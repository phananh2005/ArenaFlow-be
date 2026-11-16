// Mang việc làm dở giữa nhiều máy qua GitHub bằng "ảnh chụp" lưu ở ref ẩn refs/wip/<nhánh> (không phải nhánh,
// không tạo được PR, không hiện trong danh sách nhánh). Nhánh thật, staging và working tree KHÔNG bị đổi khi push.
//
// Dùng (chạy tại gốc repo):
//   node wip-handoff.cjs push [--dry-run]          Ảnh chụp mọi nhánh local chưa đồng bộ → push refs/wip/*
//   node wip-handoff.cjs list [--json]             Fetch refs/wip/* và liệt kê ảnh chụp + trạng thái nhánh local
//   node wip-handoff.cjs apply <nhánh> [--stash]   Đưa nhánh local tới base của ảnh chụp, áp file dở vào working tree
// Exit: 0 thành công · 1 lỗi/từ chối vì an toàn · 2 sai tham số.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const REMOTE = 'origin';
const WIP_PREFIX = 'refs/wip/';
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

function lines(text) {
  return String(text).split(/\r?\n/).filter(Boolean);
}

function repoRoot() {
  return git(process.cwd(), ['rev-parse', '--show-toplevel']);
}

function nowLocal() {
  const d = new Date();
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// Map nhánh → đường dẫn worktree đang checkout nhánh đó; kèm danh sách worktree HEAD tách rời.
function worktrees(root) {
  const byBranch = new Map();
  const detached = [];
  let current = null;
  for (const line of git(root, ['worktree', 'list', '--porcelain']).split(/\r?\n/)) {
    if (line.startsWith('worktree ')) current = { path: line.slice(9) };
    else if (line.startsWith('branch refs/heads/') && current) byBranch.set(line.slice(18), current.path);
    else if (line === 'detached' && current) detached.push(current.path);
  }
  return { byBranch, detached };
}

function isDirty(cwd) {
  return git(cwd, ['status', '--porcelain']).length > 0;
}

function samePath(a, b) {
  return path.resolve(a).toLowerCase() === path.resolve(b).toLowerCase();
}

// Quét secret trên nội dung index tạm (diff so với HEAD) bằng đúng bộ quét của Git pre-commit.
function secretScan(cwd, indexFile) {
  if (!fs.existsSync(HOOKS)) return { ok: true, warning: 'không thấy agent-hooks.cjs; bỏ qua secret scan' };
  const result = spawnSync('node', [HOOKS, 'secret-scan'], {
    cwd,
    encoding: 'utf8',
    input: '',
    env: { ...process.env, GIT_INDEX_FILE: indexFile, CLAUDE_PROJECT_DIR: cwd },
  });
  if (result.status === 1) return { ok: false, message: (result.stderr || '').trim() };
  if (result.status !== 0) return { ok: true, warning: 'secret scan gặp lỗi môi trường' };
  return { ok: true };
}

// Cây của working tree hiện tại (gồm file chưa track, trừ gitignore) dựng trên index tạm — không đụng index thật.
function dirtyTree(cwd) {
  const indexFile = path.join(os.tmpdir(), `wip-handoff-${process.pid}-${Date.now()}-${Math.random().toString(36).slice(2)}.index`);
  const env = { ...process.env, GIT_INDEX_FILE: indexFile };
  try {
    git(cwd, ['read-tree', 'HEAD'], { env });
    git(cwd, ['add', '-A'], { env });
    const scan = secretScan(cwd, indexFile);
    if (!scan.ok) throw new Refusal(`Dừng: có thể có secret trong file đang sửa ở ${cwd}.\n${scan.message}`);
    return { tree: git(cwd, ['write-tree'], { env }), warning: scan.warning };
  } finally {
    fs.rmSync(indexFile, { force: true });
  }
}

function snapshotMessage({ branch, base, dirty, current, saved }) {
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
  const out = git(root, ['ls-remote', REMOTE, `${WIP_PREFIX}*`]);
  return new Map(lines(out).map(line => {
    const [sha, ref] = line.split('\t');
    return [ref, sha];
  }));
}

function push(root, { dryRun }) {
  git(root, ['fetch', '--quiet', '--prune', REMOTE]);
  const { byBranch, detached } = worktrees(root);
  const mainBranch = git(root, ['symbolic-ref', '--quiet', '--short', 'HEAD'], { allowFail: true }).stdout.trim();
  const saved = nowLocal();
  const snapshots = [];
  const warnings = [];

  for (const wt of detached) {
    if (isDirty(wt)) warnings.push(`Bỏ qua worktree HEAD tách rời có thay đổi: ${wt} (không có nhánh để gắn ảnh chụp)`);
  }

  for (const branch of lines(git(root, ['for-each-ref', '--format=%(refname:short)', 'refs/heads']))) {
    const head = git(root, ['rev-parse', `refs/heads/${branch}`]);
    const wt = byBranch.get(branch);
    const dirty = Boolean(wt) && isDirty(wt);
    const unpushed = Number(git(root, ['rev-list', '--count', `refs/heads/${branch}`, '--not', `--remotes=${REMOTE}`])) > 0;
    if (!dirty && !unpushed) continue;

    let tree = git(root, ['rev-parse', `${head}^{tree}`]);
    if (dirty) {
      const result = dirtyTree(wt);
      tree = result.tree;
      if (result.warning) warnings.push(`${branch}: ${result.warning}`);
    }
    const current = Boolean(wt) && samePath(wt, root) && branch === mainBranch;
    const message = snapshotMessage({ branch, base: head, dirty, current, saved });
    const sha = git(root, ['commit-tree', tree, '-p', head, '-F', '-'], { input: message });
    snapshots.push({ branch, sha, base: head, dirty, current, unpushed });
  }

  for (const w of warnings) process.stderr.write(`Cảnh báo: ${w}\n`);
  if (!snapshots.length) {
    console.log('Không có nhánh nào chưa đồng bộ — không cần đẩy gì.');
    return 0;
  }

  for (const s of snapshots) {
    const what = [s.dirty && 'file đang sửa', s.unpushed && 'commit chưa push'].filter(Boolean).join(' + ');
    console.log(`${dryRun ? '[dry-run] ' : ''}${WIP_PREFIX}${s.branch} ← ${s.sha.slice(0, 7)} (base ${s.base.slice(0, 7)}; ${what}${s.current ? '; nhánh đang làm' : ''})`);
  }
  if (dryRun) return 0;

  const existing = remoteWipRefs(root);
  const args = ['push', '--quiet', '--atomic', REMOTE];
  for (const s of snapshots) {
    const ref = `${WIP_PREFIX}${s.branch}`;
    args.push(`--force-with-lease=${ref}:${existing.get(ref) || ''}`);
  }
  for (const s of snapshots) args.push(`${s.sha}:${WIP_PREFIX}${s.branch}`);
  git(root, args);

  for (const s of snapshots) git(root, ['update-ref', `${WIP_PREFIX}${s.branch}`, s.sha]);
  const pushed = remoteWipRefs(root);
  const missing = snapshots.filter(s => pushed.get(`${WIP_PREFIX}${s.branch}`) !== s.sha);
  if (missing.length) throw new Refusal(`Push xong nhưng remote không khớp: ${missing.map(s => s.branch).join(', ')}`);
  console.log(`Đã đẩy ${snapshots.length} ảnh chụp lên ${REMOTE}/${WIP_PREFIX}*. Nhánh thật và working tree không đổi.`);
  return 0;
}

function isAncestor(root, a, b) {
  return git(root, ['merge-base', '--is-ancestor', a, b], { allowFail: true }).status === 0;
}

function localState(root, branch, base) {
  const local = git(root, ['rev-parse', '--verify', '--quiet', `refs/heads/${branch}`], { allowFail: true }).stdout.trim();
  if (!local) return { local: 'missing', head: null };
  if (local === base) return { local: 'same', head: local };
  if (isAncestor(root, local, base)) return { local: 'behind', head: local };
  if (isAncestor(root, base, local)) return { local: 'ahead', head: local };
  return { local: 'diverged', head: local };
}

function readSnapshot(root, ref, sha) {
  const t = parseTrailers(git(root, ['log', '-1', '--format=%B', sha]));
  if (t['WIP-Handoff'] !== 'true') return null;
  const parent = git(root, ['rev-parse', `${sha}^`], { allowFail: true }).stdout.trim();
  const branch = t['WIP-Branch'] || ref.slice(WIP_PREFIX.length);
  return {
    ref,
    sha,
    branch,
    base: parent,
    baseMatches: parent === t['WIP-Base'],
    dirty: t['WIP-Dirty'] === 'true',
    current: t['WIP-Current'] === 'true',
    saved: t['WIP-Saved'] || '',
    ...localState(root, branch, parent),
  };
}

function fetchWip(root, ref = `${WIP_PREFIX}*`) {
  git(root, ['fetch', '--quiet', '--prune', REMOTE]);
  git(root, ['fetch', '--quiet', '--prune', REMOTE, `+${ref}:${ref}`]);
}

function list(root, { json }) {
  fetchWip(root);
  const items = lines(git(root, ['for-each-ref', '--format=%(refname) %(objectname)', WIP_PREFIX]))
    .map(line => line.split(' '))
    .map(([ref, sha]) => readSnapshot(root, ref, sha))
    .filter(Boolean);
  if (json) {
    console.log(JSON.stringify(items, null, 2));
    return 0;
  }
  if (!items.length) {
    console.log('Không có ảnh chụp nào trên remote.');
    return 0;
  }
  const label = { missing: 'chưa có nhánh local', same: 'local = base', behind: 'local đi sau (fast-forward được)', ahead: 'local đi TRƯỚC — ảnh chụp cũ hơn', diverged: 'LỆCH lịch sử' };
  for (const i of items) {
    console.log(`${i.branch} · lưu ${i.saved} · ${i.dirty ? 'có file dở' : 'chỉ commit chưa push'}${i.current ? ' · nhánh đang làm lúc handoff' : ''} · ${label[i.local]}`);
  }
  return 0;
}

function apply(root, branch, { stash }) {
  const ref = `${WIP_PREFIX}${branch}`;
  const exists = git(root, ['ls-remote', '--exit-code', REMOTE, ref], { allowFail: true });
  if (exists.status !== 0) throw new Refusal(`Không có ảnh chụp ${ref} trên ${REMOTE}.`);
  fetchWip(root, ref);
  const snap = readSnapshot(root, ref, git(root, ['rev-parse', ref]));
  if (!snap) throw new Refusal(`${ref} không phải ảnh chụp của /af-handoff-push (thiếu WIP-Handoff: true).`);
  if (!snap.baseMatches) throw new Refusal(`${ref}: WIP-Base không khớp commit cha — không áp.`);
  if (snap.local === 'ahead') throw new Refusal(`Nhánh local ${branch} đã đi trước ảnh chụp (lưu ${snap.saved}) — ảnh chụp cũ hơn, không áp.`);
  if (snap.local === 'diverged') throw new Refusal(`Nhánh local ${branch} lệch lịch sử với ảnh chụp — không áp; cần xử lý tay.`);

  const { byBranch } = worktrees(root);
  const otherWt = byBranch.get(branch) && !samePath(byBranch.get(branch), root) ? byBranch.get(branch) : null;
  if (otherWt) throw new Refusal(`Nhánh ${branch} đang checkout ở worktree khác (${otherWt}) — không áp.`);
  const currentBranch = git(root, ['symbolic-ref', '--quiet', '--short', 'HEAD'], { allowFail: true }).stdout.trim();
  const touchesWorkingTree = snap.dirty || currentBranch === branch;

  if (touchesWorkingTree && isDirty(root)) {
    if (!stash) throw new Refusal('Working tree đang có thay đổi chưa commit — không áp. Commit/stash trước, hoặc chạy lại với --stash.');
    git(root, ['stash', 'push', '--include-untracked', '-m', `wip-handoff: cất trước khi áp ${ref} (${nowLocal()})`]);
    console.log('Đã cất thay đổi hiện tại vào git stash (xem: git stash list).');
  }

  // Đưa nhánh local tới base: tạo mới hoặc fast-forward (update-ref có kiểm giá trị cũ).
  if (snap.local === 'missing') git(root, ['branch', branch, snap.base]);
  else if (snap.local === 'behind') {
    if (currentBranch === branch) git(root, ['merge', '--ff-only', '--quiet', snap.base]);
    else git(root, ['update-ref', `refs/heads/${branch}`, snap.base, snap.head]);
  }

  if (!snap.dirty) {
    console.log(`Nhánh ${branch} → ${snap.base.slice(0, 7)} (ảnh chụp chỉ có commit chưa push; không đổi working tree).`);
    return 0;
  }
  if (currentBranch !== branch) git(root, ['switch', '--quiet', branch]);
  git(root, ['cherry-pick', '--no-commit', snap.sha]);
  git(root, ['reset', '--quiet']);
  console.log(`Đã áp ảnh chụp ${ref} (lưu ${snap.saved}) vào working tree của ${branch}; file ở trạng thái chưa commit.`);
  return 0;
}

function main(argv) {
  const [command, ...rest] = argv;
  const flags = new Set(rest.filter(a => a.startsWith('--')));
  const positional = rest.filter(a => !a.startsWith('--'));
  const usage = 'Dùng: wip-handoff.cjs push [--dry-run] | list [--json] | apply <nhánh> [--stash]';
  const allowed = { push: ['--dry-run'], list: ['--json'], apply: ['--stash'] };
  if (!allowed[command] || [...flags].some(f => !allowed[command].includes(f))) {
    process.stderr.write(`${usage}\n`);
    return 2;
  }
  if ((command === 'apply' && positional.length !== 1) || (command !== 'apply' && positional.length)) {
    process.stderr.write(`${usage}\n`);
    return 2;
  }
  try {
    const root = repoRoot();
    if (command === 'push') return push(root, { dryRun: flags.has('--dry-run') });
    if (command === 'list') return list(root, { json: flags.has('--json') });
    return apply(root, positional[0], { stash: flags.has('--stash') });
  } catch (error) {
    process.stderr.write(`${error instanceof Refusal ? error.message : error.stack || error}\n`);
    return 1;
  }
}

process.exitCode = main(process.argv.slice(2));
