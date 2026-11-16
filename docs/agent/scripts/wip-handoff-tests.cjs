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
