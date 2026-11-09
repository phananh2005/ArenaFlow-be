// Chép tài liệu FE cần từ ArenaFlow-be sang bản phản chiếu chỉ-đọc.
// Dùng: node publish-spec.cjs <thư-mục-BE> <thư-mục-đích> [--sha <40 hex>]
// Exit: 0 thành công · 2 sai tham số · 1 lỗi dữ liệu/an toàn. Mọi kiểm tra (đường dẫn thật, mẫu <MÃ>, SHA,
// liên kết, kiểu file) chạy xong trước khi xoá gì ở đích; chỉ lỗi I/O khi đang chép mới có thể để đích dở dang.
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const MANAGED_DIRS = ['business-spec', 'api', 'decisions'];
const GENERATED_FILES = ['README.md', 'SOURCE'];
const CODE_PATTERN = /^[A-Z][A-Z0-9]*-\d+$/;
const SHA_PATTERN = /^[0-9a-f]{40}$/;
const NONE = '—';
const BANNER = 'CHỈ ĐỌC — sinh từ ArenaFlow-be bởi workflow publish-spec; mọi thay đổi làm ở ArenaFlow-be';

class UsageError extends Error {}

function parseArgs(argv) {
  const positional = [];
  let sha = null;
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--sha') {
      if (i + 1 >= argv.length) throw new UsageError('--sha cần giá trị');
      sha = argv[i + 1];
      i += 1;
    } else if (arg.startsWith('-')) {
      throw new UsageError(`tham số không hỗ trợ: ${arg}`);
    } else {
      positional.push(arg);
    }
  }
  if (positional.length !== 2) throw new UsageError('cần đúng 2 tham số: <thư-mục-BE> <thư-mục-đích>');
  return { backend: path.resolve(positional[0]), dest: path.resolve(positional[1]), sha };
}

// Đường dẫn thật (đã giải liên kết) kể cả khi phần cuối chưa tồn tại: giải phần cha gần nhất đang có rồi nối phần còn lại.
function realPath(target) {
  const missing = [];
  let current = target;
  while (!fs.existsSync(current)) {
    const parent = path.dirname(current);
    if (parent === current) break;
    missing.unshift(path.basename(current));
    current = parent;
  }
  return path.join(fs.realpathSync.native(current), ...missing);
}

function isInside(child, parent) {
  const relative = path.relative(parent, child);
  if (relative === '') return true;
  if (path.isAbsolute(relative)) return false;
  return relative !== '..' && !relative.startsWith(`..${path.sep}`);
}

function assertSeparate(backend, dest) {
  const realBackend = realPath(backend);
  const realDest = realPath(dest);
  if (isInside(realDest, realBackend)) throw new Error('thư mục đích không được trùng hoặc nằm trong thư mục BE (kể cả qua liên kết)');
  if (isInside(realBackend, realDest)) throw new Error('thư mục đích không được chứa thư mục BE (kể cả qua liên kết)');
}

// Chỉ nhận SHA khi chính thư mục BE là gốc repo git; git tự dò lên thư mục cha nên phải so gốc repo.
// Bỏ biến GIT_* (vd GIT_DIR khi chạy trong git hook) vì chúng khiến git trả lời theo repo khác.
function headSha(backend) {
  const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !/^GIT_/i.test(key)));
  const result = spawnSync('git', ['-C', backend, 'rev-parse', '--show-toplevel', 'HEAD'], { encoding: 'utf8', env });
  const [toplevel, sha] = result.status === 0 ? result.stdout.trim().split(/\r?\n/) : [];
  if (!toplevel || path.relative(realPath(toplevel), realPath(backend)) !== '') {
    throw new Error('thư mục BE không phải gốc repo git; truyền --sha');
  }
  return sha;
}

function requireKind(file, kind) {
  const stat = fs.lstatSync(file, { throwIfNoEntry: false });
  if (!stat) throw new Error(`thiếu nguồn: ${file}`);
  if (stat.isSymbolicLink()) throw new Error(`không publish liên kết (symlink/junction): ${file}`);
  const ok = kind === 'dir' ? stat.isDirectory() : stat.isFile();
  if (!ok) throw new Error(`nguồn phải là ${kind === 'dir' ? 'thư mục' : 'file'}: ${file}`);
}

function codeDirectories(directory) {
  const names = fs.readdirSync(directory, { withFileTypes: true })
    .filter(entry => !entry.name.startsWith('_') && (entry.isDirectory() || entry.isSymbolicLink()))
    .map(entry => entry.name)
    .sort();
  const invalid = names.filter(name => !CODE_PATTERN.test(name));
  if (invalid.length) {
    throw new Error(`thư mục không đúng mẫu <MÃ> trong ${directory}: ${invalid.join(', ')} — cập nhật script trước khi publish`);
  }
  for (const name of names) requireKind(path.join(directory, name), 'dir');
  return names;
}

// Liệt kê file cần chép trong cây thư mục; gặp liên kết hoặc loại file lạ thì dừng.
function treeCopies(source, target, skip = () => false) {
  const copies = [];
  for (const entry of fs.readdirSync(source, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    if (skip(entry.name)) continue;
    const from = path.join(source, entry.name);
    const to = path.join(target, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`không publish liên kết (symlink/junction): ${from}`);
    if (entry.isDirectory()) copies.push(...treeCopies(from, to, skip));
    else if (entry.isFile()) copies.push({ from, to });
    else throw new Error(`loại file không hỗ trợ: ${from}`);
  }
  return copies;
}

function headerRows(apiSpecFile) {
  const lines = fs.readFileSync(apiSpecFile, 'utf8').split(/\r?\n/);
  const end = lines.findIndex(line => line.startsWith('## '));
  return (end === -1 ? lines : lines.slice(0, end)).filter(line => line.startsWith('|'));
}

function apiVersion(rows) {
  const row = rows.find(line => line.includes('Phiên bản'));
  if (!row) return NONE;
  const current = row.match(/hiện tại\s*\**\s*(v\d+(?:\.\d+)*)/);
  if (current) return current[1];
  const first = row.match(/\b(v\d+(?:\.\d+)+)\b/);
  return first ? first[1] : NONE;
}

function referencedSrs(rows, srsCodes) {
  const row = rows.find(line => line.includes('Spec nghiệp vụ'));
  if (!row) return [];
  const codes = [...row.matchAll(/business-spec\/([A-Z][A-Z0-9]*-\d+)\//g)].map(match => match[1]);
  return [...new Set(codes)].filter(code => srsCodes.includes(code)).sort();
}

function plan({ backend, dest, sha }) {
  assertSeparate(backend, dest);
  const resolvedSha = sha ?? headSha(backend);
  if (!SHA_PATTERN.test(resolvedSha)) throw new Error('SHA phải gồm 40 ký tự hex thường');

  const docs = path.join(backend, 'docs', 'agent');
  const businessSpec = path.join(docs, 'business-spec');
  const design = path.join(docs, 'design');
  const decisions = path.join(docs, 'decisions');
  const apiConvention = path.join(docs, 'api-convention.md');
  for (const dir of [path.dirname(docs), docs, businessSpec, design, decisions]) requireKind(dir, 'dir');
  requireKind(apiConvention, 'file');

  const srsCodes = codeDirectories(businessSpec);
  const designCodes = codeDirectories(design);
  const copies = [];
  for (const code of srsCodes) {
    copies.push(...treeCopies(path.join(businessSpec, code), path.join('business-spec', code)));
  }
  const apis = [];
  for (const code of designCodes) {
    const apiSpec = path.join(design, code, 'API_Spec.md');
    if (!fs.lstatSync(apiSpec, { throwIfNoEntry: false })) continue;
    requireKind(apiSpec, 'file');
    const changelog = path.join(design, code, 'CHANGELOG.md');
    const hasChangelog = Boolean(fs.lstatSync(changelog, { throwIfNoEntry: false }));
    if (hasChangelog) requireKind(changelog, 'file');
    copies.push({ from: apiSpec, to: path.join('api', code, 'API_Spec.md') });
    if (hasChangelog) copies.push({ from: changelog, to: path.join('api', code, 'CHANGELOG.md') });
    const rows = headerRows(apiSpec);
    apis.push({ code, hasChangelog, version: apiVersion(rows), srsRefs: referencedSrs(rows, srsCodes) });
  }
  copies.push({ from: apiConvention, to: path.join('api', 'api-convention.md') });
  copies.push(...treeCopies(decisions, 'decisions', name => name.startsWith('_template')));

  for (const name of GENERATED_FILES) {
    const stat = fs.lstatSync(path.join(dest, name), { throwIfNoEntry: false });
    if (stat && !stat.isFile()) throw new Error(`${name} ở đích phải là file thường (không phải liên kết/thư mục)`);
  }
  return { backend, dest, sha: resolvedSha, srsCodes, apis, copies };
}

function srsCell(code, work) {
  if (work.srsCodes.includes(code)) return `[SRS](business-spec/${code}/)`;
  const refs = work.apis.find(api => api.code === code)?.srsRefs ?? [];
  return refs.length ? refs.map(ref => `[SRS trong ${ref}](business-spec/${ref}/)`).join(', ') : NONE;
}

function readme(work) {
  const codes = [...new Set([...work.srsCodes, ...work.apis.map(api => api.code)])].sort();
  const rows = codes.map(code => {
    const api = work.apis.find(item => item.code === code);
    return [
      code,
      srsCell(code, work),
      api ? `[API_Spec](api/${code}/API_Spec.md)` : NONE,
      api ? api.version : NONE,
      api?.hasChangelog ? `[CHANGELOG](api/${code}/CHANGELOG.md)` : NONE,
    ];
  });
  return [
    '# ArenaFlow-spec',
    '',
    `> **${BANNER}**`,
    '>',
    '> Không sửa trực tiếp repo này: mọi thay đổi bị ghi đè ở lần publish sau. Tài liệu chỉ cập nhật khi thay đổi được merge vào `main` của ArenaFlow-be.',
    '',
    '## Mục lục',
    '',
    '| Mã | SRS | API_Spec | Phiên bản API_Spec | CHANGELOG |',
    '|---|---|---|---|---|',
    ...rows.map(cells => `| ${cells.join(' | ')} |`),
    '',
    '- Quy ước API chung: [api/api-convention.md](api/api-convention.md)',
    '- Quyết định kiến trúc (ADR): [decisions/](decisions/)',
    '- "SRS trong X": SRS của mã này nằm trong spec của mã X.',
    '- Mục **MAJOR** trong CHANGELOG của API_Spec = phá vỡ hợp đồng (xoá/đổi endpoint, đổi cấu trúc request/response, đổi/bỏ mã lỗi): FE phải xem lại phần gọi API liên quan.',
    '- `SOURCE`: SHA commit ArenaFlow-be của lần publish gần nhất có thay đổi nội dung.',
    '',
  ].join('\n');
}

function publish(work) {
  const { backend, dest } = work;
  fs.mkdirSync(dest, { recursive: true });
  assertSeparate(backend, dest);
  for (const dir of MANAGED_DIRS) fs.rmSync(path.join(dest, dir), { recursive: true, force: true });
  for (const { from, to } of work.copies) {
    const target = path.join(dest, to);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(from, target);
  }
  fs.writeFileSync(path.join(dest, 'README.md'), readme(work));
  fs.writeFileSync(path.join(dest, 'SOURCE'), `${work.sha}\n`);
  const apiCodes = work.apis.map(api => api.code);
  return `PUBLISHED: SRS [${work.srsCodes.join(', ')}] · API_Spec [${apiCodes.join(', ')}] · SOURCE ${work.sha}\n`;
}

function main(argv) {
  try {
    process.stdout.write(publish(plan(parseArgs(argv))));
    return 0;
  } catch (error) {
    process.stderr.write(`publish-spec: ${error.message}\n`);
    return error instanceof UsageError ? 2 : 1;
  }
}

if (require.main === module) process.exitCode = main(process.argv.slice(2));
