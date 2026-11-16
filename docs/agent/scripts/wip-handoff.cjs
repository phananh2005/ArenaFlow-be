// Mang việc làm dở giữa nhiều máy qua GitHub bằng "ảnh chụp" lưu ở ref ẩn refs/wip/<nhánh> (không phải nhánh,
// không tạo được PR, không hiện trong danh sách nhánh). Nhánh thật, staging và working tree KHÔNG bị đổi khi push.
//
// Dùng (chạy tại gốc repo):
//   node wip-handoff.cjs push [--dry-run] [--force-overwrite]  Ảnh chụp mọi nhánh local chưa đồng bộ → push refs/wip/*
//   node wip-handoff.cjs list [--json]                         Fetch refs/wip/* và liệt kê ảnh chụp + trạng thái
//   node wip-handoff.cjs apply <nhánh> [--stash]               Đưa nhánh local tới base của ảnh chụp, áp file dở
// Exit: 0 thành công · 1 lỗi/từ chối vì an toàn · 2 sai tham số.
//
// Mỗi máy có mã riêng (git config --local wip-handoff.machine) ghi vào trailer WIP-Machine. Ảnh chụp trên remote
// do máy khác đẩy mà máy này chưa lấy về (refs/wip-seen/<nhánh>) thì push từ chối ghi đè, trừ khi --force-overwrite.
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const REMOTE = 'origin';
const WIP_PREFIX = 'refs/wip/';
const SEEN_PREFIX = 'refs/wip-seen/';
const HEADS_PREFIX = 'refs/heads/';
const HOOKS = path.join(__dirname, '..', 'hooks', 'agent-hooks.cjs');

class Refusal extends Error {}

function git(cwd, args, options = {}) {
  const result = spawnSync('git', args, {
    cwd,
    encoding: 'utf8',
    env: options.env || process.env,
    input: options.input,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  if (result.status !== 0 && !options.allowFail) {
    throw new Refusal(`git ${args.join(' ')} lỗi: ${(result.stderr || '').trim()}`);
  }
  return options.allowFail ? result : result.stdout.replace(/\r?\n$/, '');
}

function tryRev(cwd, rev) {
  return git(cwd, ['rev-parse', '--verify', '--quiet', rev], { allowFail: true }).stdout.trim() || null;
}

function lines(text) {
  return String(text).split(/\r?\n/).filter(Boolean);
}

function nowLocal() {
  const d = new Date();
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function realPath(p) {
  try { return fs.realpathSync.native(p); } catch { return path.resolve(p); }
}

function samePath(a, b) {
  const norm = p => realPath(p).replace(/[\\/]+$/, '');
  return process.platform === 'win32' ? norm(a).toLowerCase() === norm(b).toLowerCase() : norm(a) === norm(b);
}

function currentBranchOf(cwd) {
  return git(cwd, ['symbolic-ref', '--quiet', '--short', 'HEAD'], { allowFail: true }).stdout.trim() || null;
}

// Worktree còn thư mục: map nhánh → đường dẫn, danh sách worktree HEAD tách rời; worktree mất thư mục → cảnh báo.
function worktrees(root) {
  const entries = [];
  let current = null;
  for (const line of git(root, ['worktree', 'list', '--porcelain']).split(/\r?\n/)) {
    if (line.startsWith('worktree ')) entries.push(current = { path: line.slice(9) });
    else if (!current) continue;
    else if (line.startsWith(`branch ${HEADS_PREFIX}`)) current.branch = line.slice(7 + HEADS_PREFIX.length);
    else if (line === 'detached') current.detached = true;
    else if (line.startsWith('prunable')) current.prunable = true;
    else if (line === 'bare') current.bare = true;
  }
  const byBranch = new Map();
  const detached = [];
  const missing = [];
  for (const e of entries) {
    if (e.bare) continue;
    if (e.prunable || !fs.existsSync(e.path)) { missing.push(e.path); continue; }
    if (e.branch) byBranch.set(e.branch, e.path);
    else if (e.detached) detached.push(e.path);
  }
  return { byBranch, detached, missing };
}

function isDirty(cwd) {
  return git(cwd, ['status', '--porcelain']).length > 0;
}

// Quét secret trên file thêm/sửa giữa from..to bằng bộ quét của Git pre-commit. Không fail-open.
function secretScan(cwd, from, to) {
  if (!fs.existsSync(HOOKS)) throw new Refusal(`Dừng: không tìm thấy bộ quét secret (${HOOKS}) — không đẩy.`);
  const result = spawnSync(process.execPath, [HOOKS, 'secret-scan-diff', from, to], {
    cwd,
    encoding: 'utf8',
    input: '',
    env: { ...process.env, CLAUDE_PROJECT_DIR: cwd },
  });
  if (result.status === 0) return;
  if (result.status === 1) throw new Refusal(`Dừng: có thể có secret — không đẩy.\n${(result.stderr || '').trim()}`);
  throw new Refusal(`Dừng: secret scan lỗi (exit ${result.status}) — không đẩy.\n${(result.stderr || result.error || '').toString().trim()}`);
}

// Cây của working tree (gồm file chưa track, trừ gitignore) dựng trên index tạm — không đụng index thật.
function dirtyTree(cwd, head) {
  const indexFile = path.join(os.tmpdir(), `wip-handoff-${process.pid}-${crypto.randomUUID()}.index`);
  const env = { ...process.env, GIT_INDEX_FILE: indexFile };
  try {
    git(cwd, ['read-tree', head], { env });
    git(cwd, ['add', '-A'], { env });
    const tree = git(cwd, ['write-tree'], { env });
    secretScan(cwd, head, tree);
    return tree;
  } finally {
    fs.rmSync(indexFile, { force: true });
  }
}

function scanCommits(root, commits) {
  let emptyTree = null;
  for (const c of commits) {
    const [, parent] = git(root, ['rev-list', '--parents', '-n', '1', c]).split(' ');
    const from = parent || (emptyTree ||= git(root, ['hash-object', '-t', 'tree', '--stdin'], { input: '' }));
    secretScan(root, from, c);
  }
}

function machineId(root, create) {
  const existing = git(root, ['config', '--local', '--get', 'wip-handoff.machine'], { allowFail: true }).stdout.trim();
  if (existing || !create) return existing || null;
  const id = crypto.randomUUID();
  git(root, ['config', '--local', 'wip-handoff.machine', id]);
  return id;
}

function snapshotMessage({ branch, base, dirty, current, saved, machine }) {
  return [
    `wip(handoff): snapshot ${branch} — ảnh chụp tạm, KHÔNG merge, KHÔNG tạo PR`,
    '',
    'Tạo bởi /af-handoff-push; lấy lại bằng /af-resume-pull. Không nằm trên nhánh thật.',
    '',
    'WIP-Handoff: true',
    `WIP-Branch: ${branch}`,
    `WIP-Base: ${base}`,
    `WIP-Dirty: ${dirty}`,
    `WIP-Current: ${current}`,
    `WIP-Saved: ${saved}`,
    `WIP-Machine: ${machine}`,
    '',
  ].join('\n');
}

function parseTrailers(body) {
  const out = {};
  for (const line of String(body).split(/\r?\n/)) {
    const match = line.match(/^(WIP-[A-Za-z]+):\s*(.*)$/);
    if (match) out[match[1]] = match[2].trim();
  }
  return out;
}

function remoteWipRefs(root) {
  return new Map(lines(git(root, ['ls-remote', REMOTE, `${WIP_PREFIX}*`])).map(line => {
    const [sha, ref] = line.split('\t');
    return [ref, sha];
  }));
}

function isAncestor(root, a, b) {
  return git(root, ['merge-base', '--is-ancestor', a, b], { allowFail: true }).status === 0;
}

function localState(root, branch, base) {
  const local = tryRev(root, `${HEADS_PREFIX}${branch}`);
  if (!local) return { local: 'missing', head: null };
  if (local === base) return { local: 'same', head: local };
  if (isAncestor(root, local, base)) return { local: 'behind', head: local };
  if (isAncestor(root, base, local)) return { local: 'ahead', head: local };
  return { local: 'diverged', head: local };
}

// Ảnh chụp không còn giá trị so với nhánh trên origin: origin chứa base và
// (không có file dở, hoặc mọi file dở đã giống hệt trên origin). File dở chưa có ở đâu khác → KHÔNG cũ.
function isStale(root, originTip, base, sha, dirty) {
  if (!originTip || !isAncestor(root, base, originTip)) return false;
  if (!dirty) return true;
  if (originTip === base) return false;
  const paths = git(root, ['diff', '--name-only', '--no-renames', '-z', base, sha]).split('\0').filter(Boolean);
  return git(root, ['diff', '--quiet', sha, originTip, '--', ...paths], { allowFail: true }).status === 0;
}

// Đọc ảnh chụp ở ref; trả { invalid } nếu không phải ảnh chụp hợp lệ của lệnh này.
function readSnapshot(root, ref, sha) {
  const branch = ref.slice(WIP_PREFIX.length);
  const t = parseTrailers(git(root, ['log', '-1', '--format=%B', sha]));
  if (t['WIP-Handoff'] !== 'true') return { ref, branch, invalid: `${ref} không phải ảnh chụp của /af-handoff-push (thiếu WIP-Handoff: true).` };
  if (t['WIP-Branch'] !== branch) return { ref, branch, invalid: `${ref}: WIP-Branch "${t['WIP-Branch']}" lệch tên ref — không áp.` };
  const base = tryRev(root, `${sha}^`);
  if (!base || base !== t['WIP-Base']) return { ref, branch, invalid: `${ref}: WIP-Base không khớp commit cha — không áp.` };
  const dirty = t['WIP-Dirty'] === 'true';
  const originTip = tryRev(root, `refs/remotes/${REMOTE}/${branch}`);
  const state = localState(root, branch, base);
  const originAhead = Boolean(originTip) && originTip !== base && isAncestor(root, base, originTip);
  // onto: file dở dựa trên commit cũ hơn đầu nhánh (local đi trước, hoặc chưa có local mà origin mới hơn) → áp lên đầu nhánh.
  const onto = dirty && (state.local === 'ahead' || (state.local === 'missing' && originAhead));
  return {
    ref,
    sha,
    branch,
    base,
    dirty,
    current: t['WIP-Current'] === 'true',
    saved: t['WIP-Saved'] || '',
    machine: t['WIP-Machine'] || null,
    originTip,
    stale: isStale(root, originTip, base, sha, dirty),
    onto,
    ...state,
  };
}

function fetchWip(root, ref = `${WIP_PREFIX}*`) {
  git(root, ['fetch', '--quiet', '--prune', REMOTE]);
  git(root, ['fetch', '--quiet', '--prune', REMOTE, `+${ref}:${ref}`]);
}

function localBranches(root) {
  return lines(git(root, ['for-each-ref', '--format=%(refname)%09%(upstream:track)', HEADS_PREFIX])).map(line => {
    const [refname, track] = line.split('\t');
    return { branch: refname.slice(HEADS_PREFIX.length), refname, gone: track === '[gone]' };
  });
}

function push(root, { dryRun, forceOverwrite }) {
  fetchWip(root);
  const { byBranch, detached, missing } = worktrees(root);
  const rootBranch = currentBranchOf(root);
  const existing = remoteWipRefs(root);
  const myMachine = machineId(root, !dryRun);
  const saved = nowLocal();
  const snapshots = [];
  const conflicts = [];
  const warnings = missing.map(p => `Bỏ qua worktree đã mất thư mục: ${p} (dọn bằng git worktree prune nếu không cần)`);
  const detachedDirty = detached.filter(isDirty);

  for (const { branch, refname, gone } of localBranches(root)) {
    const head = git(root, ['rev-parse', refname]);
    const wt = byBranch.get(branch);
    const dirty = Boolean(wt) && isDirty(wt);
    const unpushed = lines(git(root, ['rev-list', refname, '--not', `--remotes=${REMOTE}`]));
    if (!dirty && !unpushed.length) continue;
    if (gone && !dirty) {
      warnings.push(`Bỏ qua ${branch}: nhánh trên remote đã bị xoá (upstream [gone]) và không có file dở.`);
      continue;
    }

    const ref = `${WIP_PREFIX}${branch}`;
    const remoteSha = existing.get(ref);
    if (remoteSha && !forceOverwrite && remoteSha !== tryRev(root, `${SEEN_PREFIX}${branch}`)) {
      const other = readSnapshot(root, ref, remoteSha);
      const otherMachine = parseTrailers(git(root, ['log', '-1', '--format=%B', remoteSha]))['WIP-Machine'];
      // Ảnh chụp đã cũ (nội dung đã lên origin) thì ghi đè không mất gì.
      if (!other.stale && (!myMachine || otherMachine !== myMachine)) {
        conflicts.push(`${branch} (ảnh chụp lưu ${other.saved || '?'})`);
        continue;
      }
    }

    scanCommits(root, unpushed);
    const tree = dirty ? dirtyTree(wt, head) : git(root, ['rev-parse', `${head}^{tree}`]);
    const current = Boolean(wt) && samePath(wt, root) && branch === rootBranch;
    const message = snapshotMessage({ branch, base: head, dirty, current, saved, machine: myMachine || '(dry-run)' });
    const sha = git(root, ['commit-tree', tree, '-p', head, '-F', '-'], { input: message });
    snapshots.push({ branch, ref, sha, base: head, tree, dirty, current, unpushed });
  }

  for (const w of warnings) process.stderr.write(`Cảnh báo: ${w}\n`);
  if (conflicts.length) {
    throw new Refusal([
      'Dừng: máy khác đã đẩy ảnh chụp mới hơn mà máy này chưa lấy về — không đẩy gì:',
      ...conflicts.map(c => `  - ${c}`),
      'Chạy /af-resume-pull trước; chắc chắn muốn ghi đè thì chạy lại với --force-overwrite.',
    ].join('\n'));
  }

  const pushedBranches = new Set(snapshots.map(s => s.branch));
  const hints = [];
  for (const [ref, sha] of existing) {
    const branch = ref.slice(WIP_PREFIX.length);
    if (pushedBranches.has(branch) || !tryRev(root, sha)) continue;
    const snap = readSnapshot(root, ref, sha);
    if (!snap.invalid && snap.stale) hints.push(`Gợi ý: ${ref} là ảnh chụp thừa (nhánh trên ${REMOTE} đã chứa nó) — xoá nếu không cần: git push ${REMOTE} --delete ${ref}`);
  }

  const detachedMessage = detachedDirty.length
    ? `Còn việc dở KHÔNG đẩy được — worktree HEAD tách rời (không có nhánh để gắn ảnh chụp):\n${detachedDirty.map(p => `  - ${p}`).join('\n')}\nCheckout một nhánh trong worktree đó (hoặc hoàn tất rebase) rồi chạy lại.`
    : null;

  if (!snapshots.length) {
    for (const h of hints) console.log(h);
    if (detachedMessage) throw new Refusal(detachedMessage);
    console.log('Không có nhánh nào chưa đồng bộ — không cần đẩy gì.');
    return 0;
  }

  for (const s of snapshots) {
    const what = [s.dirty && 'file đang sửa', s.unpushed.length && `${s.unpushed.length} commit chưa push`].filter(Boolean).join(' + ');
    console.log(`${dryRun ? '[dry-run] ' : ''}${s.ref} ← ${s.sha.slice(0, 7)} (base ${s.base.slice(0, 7)}; ${what}${s.current ? '; nhánh đang làm' : ''})`);
    if (dryRun) {
      if (s.unpushed.length) for (const l of lines(git(root, ['log', '--oneline', s.base, '--not', `--remotes=${REMOTE}`]))) console.log(`    commit ${l}`);
      if (s.dirty) for (const l of lines(git(root, ['diff', '--name-status', '--no-renames', s.base, s.tree]))) console.log(`    ${l}`);
    }
  }
  if (dryRun) {
    for (const h of hints) console.log(h);
    if (detachedMessage) process.stderr.write(`${detachedMessage}\n`);
    return detachedMessage ? 1 : 0;
  }

  const args = ['push', '--quiet', '--atomic', REMOTE];
  for (const s of snapshots) args.push(`--force-with-lease=${s.ref}:${existing.get(s.ref) || ''}`);
  for (const s of snapshots) args.push(`${s.sha}:${s.ref}`);
  git(root, args);

  const pushed = remoteWipRefs(root);
  const mismatched = snapshots.filter(s => pushed.get(s.ref) !== s.sha);
  if (mismatched.length) throw new Refusal(`Push xong nhưng remote không khớp: ${mismatched.map(s => s.branch).join(', ')}`);
  for (const s of snapshots) {
    git(root, ['update-ref', s.ref, s.sha]);
    git(root, ['update-ref', `${SEEN_PREFIX}${s.branch}`, s.sha]);
  }
  console.log(`Đã đẩy ${snapshots.length} ảnh chụp lên ${REMOTE}/${WIP_PREFIX}*. Nhánh thật và working tree không đổi.`);
  for (const h of hints) console.log(h);
  if (detachedMessage) throw new Refusal(detachedMessage);
  return 0;
}

function stateLabel(i) {
  if (i.stale) return `CŨ HƠN nhánh trên ${REMOTE} (nội dung đã có trên ${REMOTE}) — không áp, pull nhánh thật`;
  if (i.local === 'diverged') return 'LỆCH lịch sử — không áp, xử lý tay';
  if (i.onto) return 'file dở dựa trên commit cũ hơn đầu nhánh — sẽ áp lên đầu nhánh (có thể đụng độ)';
  if (i.local === 'ahead') return 'nhánh local đã chứa ảnh chụp — không cần áp';
  return { missing: 'chưa có nhánh local', same: 'local = base', behind: 'local đi sau (fast-forward được)' }[i.local];
}

function list(root, { json }) {
  fetchWip(root);
  const all = lines(git(root, ['for-each-ref', '--format=%(refname) %(objectname)', WIP_PREFIX]))
    .map(line => line.split(' '))
    .map(([ref, sha]) => readSnapshot(root, ref, sha));
  for (const bad of all.filter(s => s.invalid)) process.stderr.write(`Bỏ qua: ${bad.invalid}\n`);
  const items = all.filter(s => !s.invalid);
  if (json) {
    console.log(JSON.stringify(items, null, 2));
    return 0;
  }
  if (!items.length) {
    console.log('Không có ảnh chụp nào trên remote.');
    return 0;
  }
  for (const i of items) {
    const flags = [
      `lưu ${i.saved}`,
      i.dirty ? 'có file dở' : 'chỉ commit chưa push',
      i.current && 'nhánh đang làm lúc handoff',
      stateLabel(i),
    ].filter(Boolean);
    console.log(`${i.branch} · ${flags.join(' · ')}`);
  }
  return 0;
}

function apply(root, branch, { stash }) {
  const ref = `${WIP_PREFIX}${branch}`;
  const exists = git(root, ['ls-remote', '--exit-code', REMOTE, ref], { allowFail: true });
  if (exists.status !== 0) throw new Refusal(`Không có ảnh chụp ${ref} trên ${REMOTE}.`);
  fetchWip(root, ref);
  const snap = readSnapshot(root, ref, git(root, ['rev-parse', ref]));
  if (snap.invalid) throw new Refusal(snap.invalid);
  if (snap.stale) throw new Refusal(`Ảnh chụp ${ref} (lưu ${snap.saved}) cũ hơn nhánh ${REMOTE}/${branch} (nội dung đã có trên ${REMOTE}) — không áp; pull nhánh thật thay vào đó.`);
  if (snap.local === 'diverged') throw new Refusal(`Nhánh local ${branch} lệch lịch sử với ảnh chụp — không áp; cần xử lý tay.`);
  if (snap.local === 'ahead' && !snap.dirty) {
    git(root, ['update-ref', `${SEEN_PREFIX}${branch}`, snap.sha]);
    console.log(`Nhánh local ${branch} đã chứa ảnh chụp ${ref} — không cần áp.`);
    return 0;
  }

  const { byBranch } = worktrees(root);
  const wt = byBranch.get(branch);
  if (wt && !samePath(wt, root)) throw new Refusal(`Nhánh ${branch} đang checkout ở worktree khác (${wt}) — không áp.`);
  const currentBranch = currentBranchOf(root);
  const touchesWorkingTree = snap.dirty || (currentBranch === branch && snap.local === 'behind');

  if (touchesWorkingTree && isDirty(root)) {
    if (!stash) {
      throw new Refusal('Working tree đang có thay đổi chưa commit (có thể từ ảnh chụp vừa áp — mỗi working tree chỉ chứa được một ảnh chụp có file dở) — không áp. Commit/stash trước, hoặc chạy lại với --stash.');
    }
    git(root, ['stash', 'push', '--include-untracked', '-m', `wip-handoff: cất trước khi áp ${ref} (${nowLocal()})`]);
    console.log('Đã cất thay đổi hiện tại vào git stash (xem: git stash list).');
  }

  // Đưa nhánh local tới base (hoặc đầu origin khi áp lên đầu nhánh): tạo mới kèm upstream, hoặc fast-forward
  // (update-ref có kiểm giá trị cũ). Nhánh local đi trước base thì giữ nguyên.
  if (snap.local === 'missing') {
    git(root, ['branch', branch, snap.onto ? snap.originTip : snap.base]);
    if (snap.originTip) git(root, ['branch', '--quiet', `--set-upstream-to=${REMOTE}/${branch}`, branch]);
  } else if (snap.local === 'behind') {
    if (currentBranch === branch) git(root, ['merge', '--ff-only', '--quiet', snap.base]);
    else git(root, ['update-ref', `${HEADS_PREFIX}${branch}`, snap.base, snap.head]);
  }

  if (snap.dirty) {
    if (currentBranch !== branch) git(root, ['switch', '--quiet', branch]);
    const picked = git(root, ['cherry-pick', '--no-commit', snap.sha], { allowFail: true });
    if (picked.status !== 0) {
      // Working tree đã sạch trước khi áp → reset --merge đưa về đúng trạng thái trước đó, không đụng file chưa track.
      const conflicted = lines(git(root, ['diff', '--name-only', '--diff-filter=U'], { allowFail: true }).stdout);
      git(root, ['reset', '--merge', '--quiet'], { allowFail: true });
      git(root, ['cherry-pick', '--quit'], { allowFail: true });
      throw new Refusal([
        `Áp ${ref} lên đầu nhánh ${branch} bị đụng độ — đã huỷ, working tree như trước khi áp.`,
        conflicted.length ? `File đụng độ: ${conflicted.join(', ')}` : (picked.stderr || '').trim(),
        `Ảnh chụp vẫn còn trên ${REMOTE}; áp tay: git cherry-pick --no-commit ${ref} rồi sửa đụng độ, git reset.`,
      ].join('\n'));
    }
    git(root, ['reset', '--quiet']);
  }
  git(root, ['update-ref', `${SEEN_PREFIX}${branch}`, snap.sha]);
  console.log(snap.dirty
    ? `Đã áp ảnh chụp ${ref} (lưu ${snap.saved}) vào working tree của ${branch}${snap.onto ? ' (lên đầu nhánh)' : ''}; file ở trạng thái chưa commit.`
    : `Nhánh ${branch} → ${snap.base.slice(0, 7)} (ảnh chụp chỉ có commit chưa push; không đổi working tree).`);
  return 0;
}

function main(argv) {
  const [command, ...rest] = argv;
  const flags = new Set(rest.filter(a => a.startsWith('--')));
  const positional = rest.filter(a => !a.startsWith('--'));
  const usage = 'Dùng: wip-handoff.cjs push [--dry-run] [--force-overwrite] | list [--json] | apply <nhánh> [--stash]';
  const allowed = { push: ['--dry-run', '--force-overwrite'], list: ['--json'], apply: ['--stash'] };
  const arity = { push: 0, list: 0, apply: 1 };
  if (!allowed[command] || [...flags].some(f => !allowed[command].includes(f)) || positional.length !== arity[command]) {
    process.stderr.write(`${usage}\n`);
    return 2;
  }
  try {
    const root = git(process.cwd(), ['rev-parse', '--show-toplevel']);
    if (command === 'push') return push(root, { dryRun: flags.has('--dry-run'), forceOverwrite: flags.has('--force-overwrite') });
    if (command === 'list') return list(root, { json: flags.has('--json') });
    return apply(root, positional[0], { stash: flags.has('--stash') });
  } catch (error) {
    process.stderr.write(`${error instanceof Refusal ? error.message : error.stack || error}\n`);
    return 1;
  }
}

process.exitCode = main(process.argv.slice(2));
