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
process.stdout.write(`PASS: ${cases.length + 2} hook fixtures\n`);
