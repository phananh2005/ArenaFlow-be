// Test tích hợp cho wip-handoff.cjs: dựng remote bare + 2 bản clone (laptop A, laptop B) trong thư mục tạm.
// Chạy: node docs/agent/scripts/wip-handoff-tests.cjs
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const script = path.join(__dirname, 'wip-handoff.cjs');
const sandbox = fs.mkdtempSync(path.join(os.tmpdir(), 'wip-handoff-tests-'));
const emptyGlobalConfig = path.join(sandbox, 'gitconfig-empty');
fs.writeFileSync(emptyGlobalConfig, '');

// Cô lập khỏi cấu hình git của máy (hooksPath, gpgsign, autocrlf…) để test tất định.
const env = {
  ...process.env,
  GIT_CONFIG_GLOBAL: emptyGlobalConfig,
  GIT_CONFIG_NOSYSTEM: '1',
  GIT_AUTHOR_NAME: 'Test',
  GIT_AUTHOR_EMAIL: 'test@example.invalid',
  GIT_COMMITTER_NAME: 'Test',
  GIT_COMMITTER_EMAIL: 'test@example.invalid',
};
delete env.CLAUDE_PROJECT_DIR;

function git(cwd, ...args) {
  const result = spawnSync('git', args, { cwd, env, encoding: 'utf8' });
  if (result.status !== 0) throw new Error(`git ${args.join(' ')} (${cwd}) lỗi: ${result.stderr}`);
  return result.stdout.trim();
}

function tryGit(cwd, ...args) {
  return spawnSync('git', args, { cwd, env, encoding: 'utf8' });
}

function run(cwd, ...args) {
  return spawnSync('node', [script, ...args], { cwd, env, encoding: 'utf8', input: '' });
}

function write(root, relative, content) {
  const file = path.join(root, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content);
}

function read(root, relative) {
  return fs.readFileSync(path.join(root, relative), 'utf8');
}

function trailers(cwd, ref) {
  const body = git(cwd, 'log', '-1', '--format=%B', ref);
  return Object.fromEntries(body.split(/\r?\n/).map(line => line.match(/^(WIP-[A-Za-z]+):\s*(.*)$/)).filter(Boolean).map(m => [m[1], m[2]]));
}

let counter = 0;
function setup() {
  const dir = path.join(sandbox, `case-${++counter}`);
  const origin = path.join(dir, 'origin.git');
  const a = path.join(dir, 'a');
  const b = path.join(dir, 'b');
  fs.mkdirSync(dir, { recursive: true });
  git(dir, 'init', '-q', '--bare', '-b', 'main', origin);
  git(dir, 'clone', '-q', origin, a);
  write(a, 'README.md', 'base\n');
  write(a, '.gitignore', '.env.dev\n');
  git(a, 'add', '-A');
  git(a, 'commit', '-q', '-m', 'init');
  git(a, 'push', '-q', 'origin', 'HEAD:main');
  git(a, 'switch', '-q', '-c', 'feature/X-001-demo');
  write(a, 'src/App.java', 'class App {}\n');
  git(a, 'add', '-A');
  git(a, 'commit', '-q', '-m', 'feat: app');
  git(a, 'push', '-q', 'origin', 'feature/X-001-demo');
  git(dir, 'clone', '-q', origin, b);
  return { dir, origin, a, b };
}

function remoteRefs(cwd) {
  const out = git(cwd, 'ls-remote', 'origin');
  return Object.fromEntries(out.split(/\r?\n/).filter(Boolean).map(line => line.split('\t').reverse()));
}

const tests = [];
function test(name, fn) { tests.push([name, fn]); }

test('push: nhánh đang checkout có file sửa → refs/wip/<nhánh>, nhánh thật và working tree không đổi', () => {
  const { a } = setup();
  write(a, 'src/App.java', 'class App { int x; }\n');
  write(a, 'docs/new.md', 'mới\n');
  write(a, '.env.dev', 'DATABASE_PASSWORD=khong-duoc-day-len\n'); // allow-secret: dữ liệu giả của test
  const headBefore = git(a, 'rev-parse', 'HEAD');
  const statusBefore = git(a, 'status', '--porcelain');

  const result = run(a, 'push');
  assert.equal(result.status, 0, result.stderr);

  const refs = remoteRefs(a);
  const snap = refs['refs/wip/feature/X-001-demo'];
  assert.ok(snap, 'phải có refs/wip/feature/X-001-demo trên remote');
  assert.equal(refs['refs/heads/wip/feature/X-001-demo'], undefined, 'không được tạo nhánh wip/*');
  assert.equal(git(a, 'rev-parse', 'HEAD'), headBefore, 'nhánh thật không được dời');
  assert.equal(refs['refs/heads/feature/X-001-demo'], headBefore, 'nhánh thật trên remote không đổi');
  assert.equal(git(a, 'status', '--porcelain'), statusBefore, 'working tree/staging không đổi');
  assert.equal(git(a, 'rev-parse', `${snap}^`), headBefore, 'cha của ảnh chụp là HEAD');
  assert.equal(git(a, 'show', `${snap}:src/App.java`), 'class App { int x; }');
  assert.equal(git(a, 'show', `${snap}:docs/new.md`), 'mới');
  assert.notEqual(tryGit(a, 'cat-file', '-e', `${snap}:.env.dev`).status, 0, 'file gitignore không được vào ảnh chụp');

  const t = trailers(a, snap);
  assert.equal(t['WIP-Handoff'], 'true');
  assert.equal(t['WIP-Branch'], 'feature/X-001-demo');
  assert.equal(t['WIP-Base'], headBefore);
  assert.equal(t['WIP-Dirty'], 'true');
  assert.equal(t['WIP-Current'], 'true');
  assert.match(git(a, 'log', '-1', '--format=%s', snap), /^wip\(handoff\): /);
});

test('push: nhánh không checkout có commit chưa push → ảnh chụp WIP-Dirty false, cây = cây nhánh', () => {
  const { a } = setup();
  git(a, 'switch', '-q', '-c', 'feature/Y-002-local', 'origin/main');
  write(a, 'y.md', 'y\n');
  git(a, 'add', '-A');
  git(a, 'commit', '-q', '-m', 'docs: y');
  const yHead = git(a, 'rev-parse', 'HEAD');
  git(a, 'switch', '-q', 'feature/X-001-demo');

  const result = run(a, 'push');
  assert.equal(result.status, 0, result.stderr);
  const refs = remoteRefs(a);
  const snap = refs['refs/wip/feature/Y-002-local'];
  assert.ok(snap, 'nhánh có commit chưa push phải được đẩy ảnh chụp');
  assert.equal(refs['refs/heads/feature/Y-002-local'], undefined, 'không push nhánh thật');
  assert.equal(git(a, 'rev-parse', `${snap}^`), yHead);
  assert.equal(git(a, 'rev-parse', `${snap}^{tree}`), git(a, 'rev-parse', `${yHead}^{tree}`));
  assert.equal(trailers(a, snap)['WIP-Dirty'], 'false');
  assert.equal(trailers(a, snap)['WIP-Current'], 'false');
  assert.equal(refs['refs/wip/feature/X-001-demo'], undefined, 'nhánh sạch đã đồng bộ thì bỏ qua');
});

test('push: không có gì chưa đồng bộ → exit 0, không tạo ref', () => {
  const { a } = setup();
  const result = run(a, 'push');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(Object.keys(remoteRefs(a)).filter(r => r.startsWith('refs/wip/')).length, 0);
});

test('push: main có file sửa → refs/wip/main, không commit lên main', () => {
  const { a } = setup();
  git(a, 'switch', '-q', 'main');
  const mainBefore = git(a, 'rev-parse', 'main');
  write(a, 'README.md', 'sửa trên main\n');
  const result = run(a, 'push');
  assert.equal(result.status, 0, result.stderr);
  const refs = remoteRefs(a);
  assert.ok(refs['refs/wip/main']);
  assert.equal(git(a, 'rev-parse', 'main'), mainBefore);
  assert.equal(refs['refs/heads/main'], mainBefore);
});

test('push: có secret trong file sửa → exit 1, không đẩy gì', () => {
  const { a } = setup();
  write(a, 'config.yml', 'api_key: "abcd1234efgh5678"\n'); // allow-secret: dữ liệu giả của test
  const result = run(a, 'push');
  assert.equal(result.status, 1, `stdout=${result.stdout} stderr=${result.stderr}`);
  assert.match(result.stderr, /secret/i);
  assert.equal(Object.keys(remoteRefs(a)).filter(r => r.startsWith('refs/wip/')).length, 0);
});

test('push: chạy lần 2 → ghi đè ảnh chụp cũ', () => {
  const { a } = setup();
  write(a, 'note.md', 'lần 1\n');
  assert.equal(run(a, 'push').status, 0);
  const first = remoteRefs(a)['refs/wip/feature/X-001-demo'];
  write(a, 'note.md', 'lần 2\n');
  const second = run(a, 'push');
  assert.equal(second.status, 0, second.stderr);
  const snap = remoteRefs(a)['refs/wip/feature/X-001-demo'];
  assert.notEqual(snap, first);
  assert.equal(git(a, 'show', `${snap}:note.md`), 'lần 2');
});

test('push --dry-run: không đẩy gì', () => {
  const { a } = setup();
  write(a, 'note.md', 'x\n');
  const result = run(a, 'push', '--dry-run');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /feature\/X-001-demo/);
  assert.equal(Object.keys(remoteRefs(a)).filter(r => r.startsWith('refs/wip/')).length, 0);
});

test('list --json: laptop B thấy ảnh chụp kèm trạng thái nhánh local', () => {
  const { a, b } = setup();
  write(a, 'note.md', 'x\n');
  assert.equal(run(a, 'push').status, 0);

  const result = run(b, 'list', '--json');
  assert.equal(result.status, 0, result.stderr);
  const items = JSON.parse(result.stdout);
  assert.equal(items.length, 1);
  assert.equal(items[0].branch, 'feature/X-001-demo');
  assert.equal(items[0].dirty, true);
  assert.equal(items[0].current, true);
  assert.equal(items[0].local, 'missing');
  assert.equal(items[0].base, git(a, 'rev-parse', 'HEAD'));
});

test('apply: B chưa có nhánh → tạo nhánh tại base, chuyển sang, file trở về trạng thái chưa commit', () => {
  const { a, b } = setup();
  write(a, 'src/App.java', 'class App { int x; }\n');
  write(a, 'docs/new.md', 'mới\n');
  assert.equal(run(a, 'push').status, 0);
  const base = git(a, 'rev-parse', 'HEAD');

  const result = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(git(b, 'branch', '--show-current'), 'feature/X-001-demo');
  assert.equal(git(b, 'rev-parse', 'HEAD'), base, 'không có commit tạm trên nhánh');
  assert.equal(read(b, 'src/App.java'), 'class App { int x; }\n');
  assert.equal(read(b, 'docs/new.md'), 'mới\n');
  const status = tryGit(b, 'status', '--porcelain').stdout; // không trim: cột đầu là trạng thái staging
  assert.match(status, /^ M src\/App\.java$/m, 'file sửa ở trạng thái chưa stage');
  assert.match(status, /^\?\? docs\/$/m, 'file mới ở trạng thái chưa track');
});

test('apply: nhánh local của B đi sau base → fast-forward rồi áp', () => {
  const { a, b } = setup();
  git(b, 'switch', '-q', 'feature/X-001-demo');
  write(a, 'more.md', 'commit chưa push\n');
  git(a, 'add', '-A');
  git(a, 'commit', '-q', '-m', 'docs: more');
  write(a, 'note.md', 'dở\n');
  assert.equal(run(a, 'push').status, 0);

  const result = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(git(b, 'rev-parse', 'HEAD'), git(a, 'rev-parse', 'HEAD'));
  assert.equal(read(b, 'more.md'), 'commit chưa push\n');
  assert.equal(read(b, 'note.md'), 'dở\n');
});

test('apply: B đang có thay đổi chưa commit → exit 1, không đụng gì; --stash → cất vào stash rồi áp', () => {
  const { a, b } = setup();
  write(a, 'note.md', 'từ A\n');
  assert.equal(run(a, 'push').status, 0);
  write(b, 'README.md', 'B đang sửa dở\n');

  const refused = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(refused.status, 1);
  assert.equal(git(b, 'branch', '--show-current'), 'main');
  assert.equal(read(b, 'README.md'), 'B đang sửa dở\n');

  const stashed = run(b, 'apply', 'feature/X-001-demo', '--stash');
  assert.equal(stashed.status, 0, stashed.stderr);
  assert.match(git(b, 'stash', 'list'), /wip-handoff/);
  assert.equal(git(b, 'branch', '--show-current'), 'feature/X-001-demo');
  assert.equal(read(b, 'note.md'), 'từ A\n');
});

test('apply: nhánh local của B đã đi trước base (ảnh chụp cũ) → exit 1, không đụng gì', () => {
  const { a, b } = setup();
  write(a, 'note.md', 'cũ\n');
  assert.equal(run(a, 'push').status, 0);
  git(b, 'switch', '-q', 'feature/X-001-demo');
  write(b, 'newer.md', 'mới hơn\n');
  git(b, 'add', '-A');
  git(b, 'commit', '-q', '-m', 'docs: newer');
  const headBefore = git(b, 'rev-parse', 'HEAD');

  const result = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(result.status, 1);
  assert.equal(git(b, 'rev-parse', 'HEAD'), headBefore);
  assert.equal(fs.existsSync(path.join(b, 'note.md')), false);
});

test('apply: ảnh chụp không có file sửa (chỉ commit chưa push) → chỉ tạo nhánh local, không chuyển nhánh', () => {
  const { a, b } = setup();
  git(a, 'switch', '-q', '-c', 'feature/Y-002-local', 'origin/main');
  write(a, 'y.md', 'y\n');
  git(a, 'add', '-A');
  git(a, 'commit', '-q', '-m', 'docs: y');
  const yHead = git(a, 'rev-parse', 'HEAD');
  git(a, 'switch', '-q', 'main');
  assert.equal(run(a, 'push').status, 0);

  const result = run(b, 'apply', 'feature/Y-002-local');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(git(b, 'rev-parse', 'feature/Y-002-local'), yHead);
  assert.equal(git(b, 'branch', '--show-current'), 'main');
});

test('apply: không có ảnh chụp cho nhánh → exit 1', () => {
  const { b } = setup();
  const result = run(b, 'apply', 'feature/khong-ton-tai');
  assert.equal(result.status, 1);
});

function wipRefCount(cwd) {
  return Object.keys(remoteRefs(cwd)).filter(r => r.startsWith('refs/wip/')).length;
}

function commitFile(cwd, file, content, message) {
  write(cwd, file, content);
  git(cwd, 'add', '-A');
  git(cwd, 'commit', '-q', '-m', message);
  return git(cwd, 'rev-parse', 'HEAD');
}

test('push: phần đã staged và index thật không đổi', () => {
  const { a } = setup();
  write(a, 'staged.md', 'staged\n');
  git(a, 'add', 'staged.md');
  write(a, 'staged.md', 'staged rồi sửa tiếp\n');
  const cachedBefore = tryGit(a, 'diff', '--cached').stdout;
  assert.equal(run(a, 'push').status, 0);
  assert.equal(tryGit(a, 'diff', '--cached').stdout, cachedBefore);
});

test('M1: máy cũ không được ghi đè ảnh chụp mới hơn do máy kia đẩy; --force-overwrite thì được', () => {
  const { a, b } = setup();
  write(a, 'note.md', 'A1\n');
  assert.equal(run(a, 'push').status, 0);
  assert.equal(run(b, 'apply', 'feature/X-001-demo').status, 0);
  write(b, 'note.md', 'B-newer\n');
  const bPush = run(b, 'push');
  assert.equal(bPush.status, 0, `máy đã lấy ảnh chụp về thì được đẩy đè: ${bPush.stderr}`);
  const fromB = remoteRefs(b)['refs/wip/feature/X-001-demo'];

  write(a, 'note.md', 'A2\n');
  const refused = run(a, 'push');
  assert.equal(refused.status, 1);
  assert.match(refused.stderr, /máy khác/);
  assert.equal(remoteRefs(a)['refs/wip/feature/X-001-demo'], fromB, 'ảnh chụp của B phải còn nguyên');

  const forced = run(a, 'push', '--force-overwrite');
  assert.equal(forced.status, 0, forced.stderr);
  assert.equal(git(a, 'show', `${remoteRefs(a)['refs/wip/feature/X-001-demo']}:note.md`), 'A2');
});

test('M2: ảnh chụp cũ hơn nhánh trên origin → list đánh dấu stale, apply từ chối, push nhắc ảnh chụp thừa', () => {
  const { a, b } = setup();
  write(a, 'note.md', 'dở\n');
  assert.equal(run(a, 'push').status, 0);
  git(a, 'add', '-A');
  git(a, 'commit', '-q', '-m', 'docs: note');
  git(a, 'push', '-q', 'origin', 'feature/X-001-demo');

  const items = JSON.parse(run(b, 'list', '--json').stdout);
  assert.equal(items[0].stale, true);
  const applied = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(applied.status, 1);
  assert.match(applied.stderr, /cũ hơn/);
  assert.equal(git(b, 'branch', '--show-current'), 'main');

  const again = run(a, 'push');
  assert.equal(again.status, 0, again.stderr);
  assert.match(again.stdout, /thừa/);
  assert.match(again.stdout, /git push origin --delete refs\/wip\/feature\/X-001-demo/);
  assert.equal(wipRefCount(a), 1, 'không tự xoá ref');
});

test('M3: đã áp một ảnh chụp có file dở thì ảnh chụp có file dở thứ hai bị từ chối với thông báo rõ', () => {
  const { a, b } = setup();
  write(a, 'x.md', 'X dở\n');
  git(a, 'worktree', 'add', '-q', '-b', 'feature/W-003-other', path.join(path.dirname(a), 'wt-w'), 'origin/main');
  const wt = path.join(path.dirname(a), 'wt-w');
  write(wt, 'w.md', 'W dở\n');
  assert.equal(run(a, 'push').status, 0);
  const refs = remoteRefs(a);
  assert.ok(refs['refs/wip/feature/X-001-demo']);
  assert.ok(refs['refs/wip/feature/W-003-other'], 'nhánh dở ở worktree phụ cũng được chụp');
  assert.equal(trailers(a, refs['refs/wip/feature/W-003-other'])['WIP-Current'], 'false');

  assert.equal(run(b, 'apply', 'feature/X-001-demo').status, 0);
  const second = run(b, 'apply', 'feature/W-003-other');
  assert.equal(second.status, 1);
  assert.match(second.stderr, /một ảnh chụp có file dở/);
  assert.equal(read(b, 'x.md'), 'X dở\n');
});

test('apply: nhánh đang checkout ở worktree khác → từ chối', () => {
  const { a, b } = setup();
  write(a, 'note.md', 'x\n');
  assert.equal(run(a, 'push').status, 0);
  git(b, 'worktree', 'add', '-q', path.join(path.dirname(b), 'wt-b'), '-b', 'feature/X-001-demo', 'origin/feature/X-001-demo');
  const result = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /worktree khác/);
});

test('#4: không tìm thấy bộ quét secret → từ chối, không đẩy', () => {
  const { dir, a } = setup();
  const lonely = path.join(dir, 'lonely', 'wip-handoff.cjs');
  fs.mkdirSync(path.dirname(lonely), { recursive: true });
  fs.copyFileSync(script, lonely);
  write(a, 'note.md', 'x\n');
  const result = spawnSync('node', [lonely, 'push'], { cwd: a, env, encoding: 'utf8', input: '' });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /secret/i);
  assert.equal(wipRefCount(a), 0);
});

test('#5: secret trong commit chưa push → từ chối, không đẩy', () => {
  const { a } = setup();
  git(a, 'switch', '-q', '-c', 'feature/S-004-secret', 'origin/main');
  commitFile(a, 'config.yml', 'api_key: "abcd1234efgh5678"\n', 'oops'); // allow-secret: dữ liệu giả của test
  commitFile(a, 'config.yml', 'api_key: ${API_KEY}\n', 'fix');
  const result = run(a, 'push');
  assert.equal(result.status, 1, result.stdout);
  assert.match(result.stderr, /secret/i);
  assert.equal(wipRefCount(a), 0);
});

test('#6: nhánh trùng tên tag vẫn đẩy được', () => {
  const { a } = setup();
  git(a, 'tag', 'v1', 'origin/main');
  git(a, 'switch', '-q', '-c', 'v1', 'origin/main');
  commitFile(a, 'v.md', 'v\n', 'docs: v');
  const result = run(a, 'push');
  assert.equal(result.status, 0, result.stderr);
  assert.ok(remoteRefs(a)['refs/wip/v1']);
});

test('#7: worktree HEAD tách rời có file dở → exit 1, không báo "không có gì cần đẩy"', () => {
  const { a } = setup();
  const wt = path.join(path.dirname(a), 'wt-detached');
  git(a, 'worktree', 'add', '-q', '--detach', wt, 'HEAD');
  write(wt, 'lost.md', 'dở\n');
  const result = run(a, 'push');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /HEAD tách rời/);
  assert.doesNotMatch(result.stdout, /Không có nhánh nào chưa đồng bộ/);
});

test('#8: worktree đã mất thư mục → bỏ qua kèm cảnh báo, không crash', () => {
  const { a } = setup();
  const wt = path.join(path.dirname(a), 'wt-gone');
  git(a, 'worktree', 'add', '-q', '-b', 'tmp-gone', wt, 'HEAD');
  fs.rmSync(wt, { recursive: true, force: true });
  const result = run(a, 'push');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /wt-gone/);
});

test('#9: ảnh chụp không có file dở, nhánh đang checkout = base → áp được dù working tree có thay đổi, không stash', () => {
  const { a, b } = setup();
  git(a, 'switch', '-q', '-c', 'feature/Y-002-local', 'origin/main');
  commitFile(a, 'y.md', 'y\n', 'docs: y');
  git(a, 'switch', '-q', 'main');
  assert.equal(run(a, 'push').status, 0);
  assert.equal(run(b, 'apply', 'feature/Y-002-local').status, 0);
  git(b, 'switch', '-q', 'feature/Y-002-local');
  write(b, 'dang-sua.md', 'B\n');
  const result = run(b, 'apply', 'feature/Y-002-local');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(tryGit(b, 'stash', 'list').stdout, '');
  assert.equal(read(b, 'dang-sua.md'), 'B\n');
});

test('#10: nhánh tạo mới khi áp → upstream là origin/<nhánh> nếu có', () => {
  const { a, b } = setup();
  write(a, 'note.md', 'x\n');
  assert.equal(run(a, 'push').status, 0);
  assert.equal(run(b, 'apply', 'feature/X-001-demo').status, 0);
  assert.equal(git(b, 'rev-parse', '--abbrev-ref', 'feature/X-001-demo@{upstream}'), 'origin/feature/X-001-demo');
});

function pushCrafted(a, ref, message) {
  const head = git(a, 'rev-parse', 'HEAD');
  const sha = spawnSync('git', ['commit-tree', `${head}^{tree}`, '-p', head, '-F', '-'], { cwd: a, env, encoding: 'utf8', input: message }).stdout.trim();
  git(a, 'push', '-q', 'origin', `+${sha}:${ref}`);
  return head;
}

test('#11: WIP-Branch lệch tên ref, hoặc WIP-Base lệch commit cha → apply từ chối', () => {
  const { a, b } = setup();
  const head = git(a, 'rev-parse', 'HEAD');
  pushCrafted(a, 'refs/wip/feature/X-001-demo', `wip(handoff): x\n\nWIP-Handoff: true\nWIP-Branch: main\nWIP-Base: ${head}\nWIP-Dirty: false\n`);
  assert.equal(run(b, 'apply', 'feature/X-001-demo').status, 1);
  assert.equal(tryGit(b, 'rev-parse', '--verify', '--quiet', 'refs/heads/feature/X-001-demo').status === 0, false);
  pushCrafted(a, 'refs/wip/feature/X-001-demo', `wip(handoff): x\n\nWIP-Handoff: true\nWIP-Branch: feature/X-001-demo\nWIP-Base: ${'0'.repeat(40)}\nWIP-Dirty: false\n`);
  assert.equal(run(b, 'apply', 'feature/X-001-demo').status, 1);
});

test('apply: ref không có WIP-Handoff: true → từ chối', () => {
  const { a, b } = setup();
  pushCrafted(a, 'refs/wip/feature/X-001-demo', 'commit thường\n');
  const result = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /WIP-Handoff/);
});

test('#12: --dry-run liệt kê file và commit sẽ đẩy', () => {
  const { a } = setup();
  commitFile(a, 'c.md', 'c\n', 'docs: commit chưa push');
  write(a, 'note.md', 'x\n');
  const result = run(a, 'push', '--dry-run');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /A\tnote\.md/);
  assert.match(result.stdout, /docs: commit chưa push/);
});

test('#14: nhánh có upstream đã bị xoá trên remote, sạch → bỏ qua kèm cảnh báo', () => {
  const { a } = setup();
  git(a, 'switch', '-q', '-c', 'feature/G-005-gone', 'origin/main');
  commitFile(a, 'g.md', 'g\n', 'docs: g');
  git(a, 'push', '-q', '-u', 'origin', 'feature/G-005-gone');
  git(a, 'switch', '-q', 'main');
  git(a, 'push', '-q', 'origin', '--delete', 'feature/G-005-gone');
  const result = run(a, 'push');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(remoteRefs(a)['refs/wip/feature/G-005-gone'], undefined);
  assert.match(result.stderr, /feature\/G-005-gone/);
});

test('apply: nhánh đi sau và KHÔNG checkout → dời nhánh bằng update-ref, không đổi nhánh đang đứng', () => {
  const { a, b } = setup();
  commitFile(a, 'more.md', 'more\n', 'docs: more');
  assert.equal(run(a, 'push').status, 0);
  git(b, 'branch', 'feature/X-001-demo', 'origin/feature/X-001-demo');
  const result = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(git(b, 'rev-parse', 'feature/X-001-demo'), git(a, 'rev-parse', 'HEAD'));
  assert.equal(git(b, 'branch', '--show-current'), 'main');
});

test('apply: nhánh local lệch lịch sử với base → từ chối', () => {
  const { a, b } = setup();
  commitFile(a, 'from-a.md', 'a\n', 'docs: a');
  assert.equal(run(a, 'push').status, 0);
  git(b, 'switch', '-q', '-c', 'feature/X-001-demo', 'origin/feature/X-001-demo');
  const headBefore = commitFile(b, 'from-b.md', 'b\n', 'docs: b');
  const result = run(b, 'apply', 'feature/X-001-demo');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /lệch/);
  assert.equal(git(b, 'rev-parse', 'HEAD'), headBefore);
});

test('apply: file tracked bị xoá ở máy A → máy B thấy ở trạng thái " D"', () => {
  const { a, b } = setup();
  fs.rmSync(path.join(a, 'README.md'));
  assert.equal(run(a, 'push').status, 0);
  assert.equal(run(b, 'apply', 'feature/X-001-demo').status, 0);
  assert.match(tryGit(b, 'status', '--porcelain').stdout, /^ D README\.md$/m);
});

test('tham số sai → exit 2', () => {
  const { a } = setup();
  assert.equal(run(a).status, 2);
  assert.equal(run(a, 'lam-gi-do').status, 2);
  assert.equal(run(a, 'apply').status, 2);
});

let failed = 0;
for (const [name, fn] of tests) {
  try {
    fn();
    console.log(`PASS ${name}`);
  } catch (error) {
    failed++;
    console.log(`FAIL ${name}\n  ${String(error.stack || error).split('\n').slice(0, 4).join('\n  ')}`);
  }
}
fs.rmSync(sandbox, { recursive: true, force: true });
console.log(`\n${tests.length - failed}/${tests.length} PASS`);
process.exitCode = failed ? 1 : 0;
