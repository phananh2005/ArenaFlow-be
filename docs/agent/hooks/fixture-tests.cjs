const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const path = require('node:path');
const os = require('node:os');
const fs = require('node:fs');
const { mkdtempSync, rmSync } = fs;

const repo = path.resolve(__dirname, '../../..');
const hook = path.join(repo, 'docs/agent/hooks/agent-hooks.cjs');
const cases = [
  [{ tool_name: 'Bash', tool_input: { command: 'git status' } }, 0],
  [{ tool_name: 'Bash', tool_input: { command: 'git push --force origin main' } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: 'git push origin master -f' } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: 'git push --force origin feature/test' } }, 0],
  [{ tool_name: 'Bash', tool_input: { command: 'rm -rf build' } }, 0],
  [{ tool_name: 'Bash', tool_input: { command: 'rm -rf /var/data' } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: 'rm -rf D:/workspace/data' } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: 'rm -rf \\\\server\\share' } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: 'rm -r -f ~' } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: 'rm -rf .' } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: 'rm -rf ./*' } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: "echo 'TRUNCATE TABLE t'" } }, 2],
  [{ tool_name: 'Bash', tool_input: { command: "echo 'DROP TABLE IF EXISTS t'" } }, 0],
  [{ tool_name: 'Bash', tool_input: { command: "echo 'DROP TABLE t'" } }, 2],
  [{ tool_name: 'Write', tool_input: {} }, 0],
];
for (const [payload, expected] of cases) {
  const result = spawnSync(process.execPath, [hook, 'guard'], { input: JSON.stringify(payload), encoding: 'utf8' });
  assert.equal(result.status, expected, `${payload.tool_name}: ${payload.tool_input?.command || ''}`);
}
const malformed = spawnSync(process.execPath, [hook, 'guard'], { input: '{', encoding: 'utf8' });
assert.equal(malformed.status, 0, 'invalid input must fail open');
const loggerRoot = mkdtempSync(path.join(os.tmpdir(), 'arenaflow-hook-fixture-'));
try {
  fs.mkdirSync(path.join(loggerRoot, '.git'));
  const logger = spawnSync(process.execPath, [hook, 'logger'], {
    input: JSON.stringify({ cwd: loggerRoot, tool_name: 'Bash', tool_input: { command: 'SENSITIVE_TEST_SENTINEL' } }),
    encoding: 'utf8',
  });
  assert.equal(logger.status, 0);
  const log = fs.readFileSync(path.join(loggerRoot, '.claude/logs/tool-use.jsonl'), 'utf8');
  assert.match(log, /"tool":"Bash"/);
  assert.doesNotMatch(log, /SENSITIVE_TEST_SENTINEL/);
} finally {
  rmSync(loggerRoot, { recursive: true, force: true });
}
// secret-scan-diff <from> <to>: quét file thêm/sửa giữa hai commit/tree (dùng cho ảnh chụp /af-handoff-push).
const scanRoot = mkdtempSync(path.join(os.tmpdir(), 'arenaflow-hook-scan-diff-'));
try {
  const emptyConfig = path.join(scanRoot, 'gitconfig-empty');
  fs.writeFileSync(emptyConfig, '');
  const gitEnv = { ...process.env, GIT_CONFIG_GLOBAL: emptyConfig, GIT_CONFIG_NOSYSTEM: '1', GIT_AUTHOR_NAME: 'T', GIT_AUTHOR_EMAIL: 't@example.invalid', GIT_COMMITTER_NAME: 'T', GIT_COMMITTER_EMAIL: 't@example.invalid' };
  const repoDir = path.join(scanRoot, 'repo');
  const git = (...args) => {
    const r = spawnSync('git', args, { cwd: repoDir, env: gitEnv, encoding: 'utf8' });
    assert.equal(r.status, 0, r.stderr);
    return r.stdout.trim();
  };
  fs.mkdirSync(repoDir);
  git('init', '-q');
  fs.writeFileSync(path.join(repoDir, 'a.md'), 'sạch\n');
  git('add', '-A');
  git('commit', '-q', '-m', 'clean');
  const clean = git('rev-parse', 'HEAD');
  fs.writeFileSync(path.join(repoDir, 'config.yml'), 'api_key: "abcd1234efgh5678"\n'); // allow-secret: fixture giả
  git('add', '-A');
  git('commit', '-q', '-m', 'secret');
  const dirty = git('rev-parse', 'HEAD');
  const scanDiff = (...args) => spawnSync(process.execPath, [hook, 'secret-scan-diff', ...args], { cwd: repoDir, env: { ...gitEnv, CLAUDE_PROJECT_DIR: repoDir }, input: '', encoding: 'utf8' });
  assert.equal(scanDiff(clean, dirty).status, 1, 'secret trong khoảng clean..dirty phải bị chặn');
  assert.match(scanDiff(clean, dirty).stderr, /config\.yml:1/);
  assert.equal(scanDiff(dirty, clean).status, 0, 'file bị xoá không cần quét');
  assert.equal(scanDiff(`${clean}^{tree}`, `${dirty}^{tree}`).status, 1, 'nhận cả tree');
  assert.equal(scanDiff('khong-ton-tai', dirty).status, 3, 'rev sai → lỗi môi trường (3), không phải 0');
  assert.equal(scanDiff(clean).status, 3, 'thiếu tham số → 3');
} finally {
  rmSync(scanRoot, { recursive: true, force: true });
}
process.stdout.write(`PASS: ${cases.length + 3} hook fixtures\n`);
