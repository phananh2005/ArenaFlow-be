const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const os = require('node:os');
const { mkdtempSync, rmSync } = fs;

const MAX_LOG_BYTES = 1024 * 1024;
const SECRET_PATTERNS = [
  { name: 'provider-token', regex: /\b(?:sk-[A-Za-z0-9_-]{16,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}|AIza[A-Za-z0-9_-]{30,})\b/g },
  { name: 'private-key', regex: /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/g },
  { name: 'credential-url', regex: /\b[a-z][a-z0-9+.-]*:\/\/[^\s/:@]+:[^\s/@]+@/gi },
  { name: 'sensitive-assignment', regex: /(?:password|passwd|secret|api[_-]?key|access[_-]?token)\s*[:=]\s*(?:"[^"\r\n]{4,}"|'[^'\r\n]{4,}'|[A-Za-z0-9_+/=-]{8,})/gi },
];
const SAFE_VALUE = /^(?:\$\{?[A-Za-z_][A-Za-z0-9_]*(?::-[^}]*)?}?|\$\([^)]+\)|<[^>]+>|\{\{[^}]+}}|true|false|null|string|number)$/i;

function projectRoot(input = {}) {
  const candidates = [process.env.CLAUDE_PROJECT_DIR, input.cwd, path.resolve(__dirname, '../../..'), process.cwd()];
  for (const candidate of candidates) {
    if (candidate && fs.existsSync(path.join(candidate, '.git'))) return path.resolve(candidate);
  }
  return path.resolve(candidates.find(Boolean) || process.cwd());
}

function parseInput() {
  try {
    return JSON.parse(fs.readFileSync(0, 'utf8'));
  } catch {
    return null;
  }
}

function splitCommands(command) {
  return String(command || '').split(/[\n;|&]+/).map(part => part.trim()).filter(Boolean);
}

function shellTokens(command) {
  return String(command).match(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`[^`]*`|[^\s]+/g)?.map(token => token.replace(/^['"`]|['"`]$/g, '')) || [];
}

function isDangerousCommand(command) {
  const parts = splitCommands(command);
  for (const part of parts) {
    const tokens = shellTokens(part);
    const lower = part.toLowerCase();
    if (/\bgit\s+push\b/i.test(part) && /(?:--force\b|(?:^|\s)-[^\s]*f)/i.test(part) && /(?:^|\s)(?:main|master)(?:\s|$)/i.test(part)) {
      return 'force-push-protected-branch';
    }
    if (/\brm\b/i.test(part)) {
      const rmIndex = tokens.findIndex(token => token === 'rm');
      const args = tokens.slice(rmIndex + 1);
      const recursive = args.some(arg => /^-[^-]*[rRfF]/.test(arg) || ['--recursive', '--force'].includes(arg));
      const targets = args.filter(arg => !arg.startsWith('-'));
      const dangerousTarget = targets.some(target => /^(?:\/|[A-Za-z]:[\\/]|\\\\|~(?:\/|\\|$)|\$HOME(?:\/|\\|$)|\$\{HOME}(?:\/|\\|$)|\.{1,2}(?:\/|\\|$)|\*)/.test(target));
      if (recursive && dangerousTarget) return 'recursive-delete-dangerous-target';
    }
    if (/\btruncate\s+table\b/i.test(lower)) return 'truncate-table';
    if (/\bdrop\s+(?:table|database|schema)\b/i.test(lower) && /\bdrop\s+(?:table|database|schema)\s+(?!if\s+exists\b)/i.test(lower)) {
      return 'drop-without-if-exists';
    }
  }
  return null;
}

function runGuard(input) {
  if (!input || input.tool_name !== 'Bash') return 0;
  const rule = isDangerousCommand(input.tool_input?.command);
  if (!rule) return 0;
  process.stderr.write(`Đã chặn lệnh nguy hiểm (${rule}). Hãy dùng thao tác giới hạn vào đúng tài nguyên và xác nhận lại phạm vi.\n`);
  return 2;
}

function fileKind(file) {
  const ext = path.extname(file).toLowerCase();
  if (['.json', '.yml', '.yaml', '.properties', '.env', '.toml', '.ini', '.conf'].includes(ext) || !ext) return 'config';
  if (['.md', '.txt', '.rst'].includes(ext)) return 'docs';
  return 'code';
}

function lineHasSecret(line, kind) {
  if (/\ballow-secret\b/.test(line)) return false;
  for (const pattern of SECRET_PATTERNS.slice(0, 3)) {
    pattern.regex.lastIndex = 0;
    if (pattern.regex.test(line)) return true;
  }
  if (kind === 'docs' && /`[^`]*`/.test(line)) return false;
  if (kind === 'code' && !/["'`][^"'`]+["'`]/.test(line)) return false;
  const assignment = SECRET_PATTERNS[3].regex;
  assignment.lastIndex = 0;
  for (const match of line.matchAll(assignment)) {
    const value = match[0].replace(/^[^:=]*[:=]\s*/, '').replace(/^['"]|['"]$/g, '');
    if (value.length >= 4 && !SAFE_VALUE.test(value)) return true;
  }
  return false;
}

function scanContent(content, file) {
  const kind = fileKind(file);
  return String(content).split(/\r?\n/).flatMap((line, index) => lineHasSecret(line, kind) ? [{ line: index + 1 }] : []);
}

function scanStaged(root) {
  const listed = spawnSync('git', ['diff', '--cached', '--name-only', '-z'], { cwd: root, encoding: 'buffer' });
  if (listed.status !== 0) {
    process.stderr.write('Không đọc được danh sách file staged; bỏ qua secret scan.\n');
    return 0;
  }
  const files = listed.stdout.toString('utf8').split('\0').filter(Boolean);
  const findings = [];
  for (const file of files) {
    const content = spawnSync('git', ['show', `:${file}`], { cwd: root, encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
    if (content.status !== 0) continue;
    for (const finding of scanContent(content.stdout, file)) findings.push(`${file}:${finding.line}`);
  }
  if (findings.length) {
    process.stderr.write(`Có thể có secret trong staged diff (${findings.join(', ')}). Hãy kiểm tra và thay bằng biến môi trường/placeholder.\n`);
    return 1;
  }
  return 0;
}

function logToolUse(input) {
  try {
    const root = projectRoot(input);
    const directory = path.join(root, '.claude', 'logs');
    fs.mkdirSync(directory, { recursive: true });
    const logFile = path.join(directory, 'tool-use.jsonl');
    if (fs.existsSync(logFile) && fs.statSync(logFile).size > MAX_LOG_BYTES) fs.writeFileSync(logFile, '');
    fs.appendFileSync(logFile, `${JSON.stringify({ ts: new Date().toISOString(), tool: String(input.tool_name || 'unknown').slice(0, 40) })}\n`, 'utf8');
  } catch {}
  return 0;
}

function sessionReminder(input) {
  const root = projectRoot(input);
  const configured = spawnSync('git', ['config', '--get', 'core.hooksPath'], { cwd: root, encoding: 'utf8' });
  if (configured.status === 0 && configured.stdout.trim() === '.githooks') return 0;
  process.stdout.write(JSON.stringify({ systemMessage: 'Git pre-commit chưa được cài cho bản clone này; kiểm tra docs/agent/README.md để biết hướng dẫn.' }));
  return 0;
}

function fixtureTests() {
  const guards = [
    ['git status', 0],
    ['git push --force origin main', 2],
    ['git push origin master -f', 2],
    ['git push --force origin feature/abc', 0],
    ['rm -rf build', 0],
    ['rm -rf /var/data', 2],
    ['rm -rf D:/WORKSPACE/x', 2],
    ['rm -rf \\\\server\\share', 2],
    ['rm -r -f ~', 2],
    ['rm -rf .', 2],
    ['rm -rf ./*', 2],
    ['rm /tmp/a.txt', 0],
    ["echo 'TRUNCATE TABLE t'", 2],
    ["echo 'DROP TABLE IF EXISTS t'", 0],
    ["echo 'DROP TABLE t'", 2],
  ];
  for (const [command, expected] of guards) {
    const actual = isDangerousCommand(command) ? 2 : 0;
    if (actual !== expected) throw new Error(`guard fixture mismatch: ${command} -> ${actual}, expected ${expected}`);
  }
  if (runGuard({ tool_name: 'Write', tool_input: {} }) !== 0) throw new Error('non-Bash tool was blocked');
  if (parseFixtureJson('{') !== null) throw new Error('malformed JSON did not fail open');
  const secrets = [
    ['config', 'password="fakeSecret123"', true], // allow-secret fixture giả để kiểm thử luật phát hiện.
    ['config', 'password=${DB_PASSWORD}', false],
    ['code', 'const token = response.data.token', false],
    ['code', 'String password = "fakeSecret123";', true], // allow-secret fixture giả để kiểm thử luật phát hiện.
    ['code', "const taskController = 'task-management'", false],
    ['docs', '`password: fakeSecret123`', false], // allow-secret: test fixture
    ['config', 'password=fakeSecret123 # allow-secret', false],
  ];
  for (const [kind, text, expected] of secrets) {
    const ext = kind === 'config' ? 'properties' : kind === 'docs' ? 'md' : 'java';
    const actual = scanContent(text, `fixture.${ext}`).length > 0;
    if (actual !== expected) throw new Error(`secret fixture mismatch: ${text} -> ${actual}, expected ${expected}`);
  }
  process.stdout.write(`PASS: ${guards.length + 10} fixtures\n`);
}

function parseFixtureJson(text) {
  try { return JSON.parse(text); } catch { return null; }
}

const command = process.argv[2];
const input = parseInput();
let status = 0;
if (command === 'guard') status = runGuard(input);
else if (command === 'secret-scan') status = scanStaged(projectRoot(input || {}));
else if (command === 'logger') status = logToolUse(input || {});
else if (command === 'session-start') status = sessionReminder(input || {});
else if (command === 'test') fixtureTests();
else status = 0;
process.exitCode = status;
