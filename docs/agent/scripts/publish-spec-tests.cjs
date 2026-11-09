const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const script = path.join(__dirname, 'publish-spec.cjs');
const SHA = '0123456789abcdef0123456789abcdef01234567';
const BANNER = 'CHỈ ĐỌC — sinh từ ArenaFlow-be bởi workflow publish-spec; mọi thay đổi làm ở ArenaFlow-be';

function write(root, relative, content = `# ${relative}\n`) {
  const file = path.join(root, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content);
}

// Junction (Windows) / symlink thư mục (Linux): tạo được không cần quyền admin.
function link(target, at) {
  fs.mkdirSync(path.dirname(at), { recursive: true });
  fs.symlinkSync(target, at, 'junction');
}

function apiSpec(...headerRows) {
  return [
    '# API Specification',
    '',
    '| Mục | Giá trị |',
    '|---|---|',
    ...headerRows,
    '',
    '## 1. Tổng quan',
    '| Phiên bản | v9.9 |',
    '| Spec nghiệp vụ | `docs/agent/business-spec/BBB-002/` |',
    '',
  ].join('\n');
}

function makeBackend(root) {
  const docs = 'docs/agent';
  write(root, `${docs}/api-convention.md`);
  write(root, `${docs}/business-spec/README.md`);
  write(root, `${docs}/business-spec/_template/Overview.md`);
  write(root, `${docs}/business-spec/AAA-001/Overview.md`);
  write(root, `${docs}/business-spec/AAA-001/sub/Note.md`);
  write(root, `${docs}/business-spec/BBB-002/Overview.md`);
  write(root, `${docs}/design/README.md`);
  write(root, `${docs}/design/_template/API_Spec.md`);
  write(root, `${docs}/design/AAA-001/API_Spec.md`, apiSpec('| Tác giả / Ngày / Phiên bản | sa · 2026-11-02 · v1.0 (baseline) · hiện tại **v3.6** (dev · 2026-11-09) |'));
  write(root, `${docs}/design/AAA-001/CHANGELOG.md`);
  write(root, `${docs}/design/AAA-001/Component_Class_Design.md`);
  write(root, `${docs}/design/AAA-001/Processing_Flow.md`);
  write(root, `${docs}/design/CCC-003/API_Spec.md`, apiSpec(
    '| Spec nghiệp vụ | `docs/agent/business-spec/AAA-001/` (v1.2) — UC-1 |',
    '| Phiên bản | v1.0 (baseline) |',
  ));
  write(root, `${docs}/design/DDD-004/Processing_Flow.md`);
  write(root, `${docs}/design/EEE-005/API_Spec.md`, apiSpec('| Tác giả | sa |', '| Spec nghiệp vụ | `docs/agent/business-spec/ZZZ-999/` |'));
  write(root, `${docs}/design/FFF-006/API_Spec.md`, apiSpec('| Phiên bản | (chưa chốt) |'));
  write(root, `${docs}/design/GGG-007/CHANGELOG.md`);
  write(root, `${docs}/design/III-009/API_Spec.md`, apiSpec('| Phiên bản | v1.0 (baseline) · hiện tại v3.7 (dev) |'));
  write(root, `${docs}/decisions/README.md`);
  write(root, `${docs}/decisions/_template.md`);
  write(root, `${docs}/decisions/0001-x.md`);
}

function listFiles(root) {
  const result = [];
  const walk = directory => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const full = path.join(directory, entry.name);
      if (entry.isDirectory()) walk(full);
      else result.push(path.relative(root, full).split(path.sep).join('/'));
    }
  };
  walk(root);
  return result.sort();
}

function snapshot(root) {
  return Object.fromEntries(listFiles(root).map(file => [file, fs.readFileSync(path.join(root, file), 'utf8')]));
}

// Bỏ biến GIT_* kế thừa (vd GIT_DIR khi chạy trong git hook) để lệnh git của fixture không chạm repo thật.
const CLEAN_ENV = Object.fromEntries(Object.entries(process.env).filter(([key]) => !/^GIT_/i.test(key)));

function run(...args) {
  return runWithEnv(CLEAN_ENV, ...args);
}

function runWithEnv(env, ...args) {
  return spawnSync(process.execPath, [script, ...args], { encoding: 'utf8', env });
}

function git(cwd, ...args) {
  const result = spawnSync('git', ['-c', 'user.name=fixture', '-c', 'user.email=fixture@example.invalid', ...args], { cwd, encoding: 'utf8', env: CLEAN_ENV });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'arenaflow-publish-spec-'));
let checks = 0;
const failures = [];
function check(name, fn) {
  try {
    fn();
  } catch (error) {
    failures.push(`FAIL ${name}: ${error.message.split('\n')[0]}`);
  }
  checks += 1;
}

function assertRefusedUntouched(result, marker) {
  assert.equal(result.status, 1, result.stderr);
  assert.ok(result.stderr.length > 0, 'phải báo lỗi ra stderr');
  assert.ok(fs.existsSync(marker), `không được xoá ${marker}`);
}

try {
  const be = path.join(tmp, 'be');
  makeBackend(be);
  const beMarker = path.join(be, 'docs/agent/business-spec/AAA-001/Overview.md');

  check('chép đúng phạm vi, xoá bản cũ, giữ file ngoài phạm vi', () => {
    const out = path.join(tmp, 'out');
    write(out, '.git/config', 'keep\n');
    write(out, 'LICENSE', 'keep\n');
    write(out, 'business-spec/OLD-001/Overview.md');
    write(out, 'api/OLD-001/API_Spec.md');
    write(out, 'decisions/old.md');
    const result = run(be, out, '--sha', SHA);
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(listFiles(out), [
      '.git/config',
      'LICENSE',
      'README.md',
      'SOURCE',
      'api/AAA-001/API_Spec.md',
      'api/AAA-001/CHANGELOG.md',
      'api/CCC-003/API_Spec.md',
      'api/EEE-005/API_Spec.md',
      'api/FFF-006/API_Spec.md',
      'api/III-009/API_Spec.md',
      'api/api-convention.md',
      'business-spec/AAA-001/Overview.md',
      'business-spec/AAA-001/sub/Note.md',
      'business-spec/BBB-002/Overview.md',
      'decisions/0001-x.md',
      'decisions/README.md',
    ]);
    assert.equal(fs.readFileSync(path.join(out, 'LICENSE'), 'utf8'), 'keep\n');
    assert.equal(
      fs.readFileSync(path.join(out, 'api/AAA-001/API_Spec.md'), 'utf8'),
      fs.readFileSync(path.join(be, 'docs/agent/design/AAA-001/API_Spec.md'), 'utf8'),
    );
  });

  check('SOURCE và README', () => {
    const out = path.join(tmp, 'out');
    assert.equal(fs.readFileSync(path.join(out, 'SOURCE'), 'utf8'), `${SHA}\n`);
    const readme = fs.readFileSync(path.join(out, 'README.md'), 'utf8');
    assert.ok(readme.includes(BANNER), 'thiếu banner chỉ-đọc');
    for (const row of [
      '| AAA-001 | [SRS](business-spec/AAA-001/) | [API_Spec](api/AAA-001/API_Spec.md) | v3.6 | [CHANGELOG](api/AAA-001/CHANGELOG.md) |',
      '| BBB-002 | [SRS](business-spec/BBB-002/) | — | — | — |',
      '| CCC-003 | [SRS trong AAA-001](business-spec/AAA-001/) | [API_Spec](api/CCC-003/API_Spec.md) | v1.0 | — |',
      '| EEE-005 | — | [API_Spec](api/EEE-005/API_Spec.md) | — | — |',
      '| FFF-006 | — | [API_Spec](api/FFF-006/API_Spec.md) | — | — |',
      '| III-009 | — | [API_Spec](api/III-009/API_Spec.md) | v3.7 | — |',
    ]) {
      assert.ok(readme.includes(`${row}\n`), `thiếu dòng mục lục: ${row}`);
    }
    assert.ok(!readme.includes('DDD-004'), 'mã không có SRS lẫn API_Spec không được vào mục lục');
    assert.ok(!readme.includes('GGG-007'), 'mã chỉ có CHANGELOG không được vào mục lục');
    assert.ok(readme.includes('(api/api-convention.md)'), 'thiếu link api-convention');
    assert.ok(readme.includes('(decisions/)'), 'thiếu link decisions');
  });

  check('chạy lại cho kết quả giống hệt', () => {
    const out = path.join(tmp, 'out');
    const before = snapshot(out);
    assert.equal(run(be, out, '--sha', SHA).status, 0);
    assert.deepEqual(snapshot(out), before);
  });

  check('đích chưa tồn tại thì tạo mới', () => {
    const out = path.join(tmp, 'fresh', 'nested');
    assert.equal(run(be, out, '--sha', SHA).status, 0);
    assert.ok(fs.existsSync(path.join(out, 'SOURCE')));
  });

  check('không truyền --sha: lấy HEAD của git BE; không phải repo git thì dừng', () => {
    const repo = path.join(tmp, 'be-git');
    makeBackend(repo);
    git(repo, 'init', '-q');
    git(repo, 'add', '-A');
    git(repo, 'commit', '-qm', 'fixture');
    const out = path.join(tmp, 'git-out');
    const result = run(repo, out);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.readFileSync(path.join(out, 'SOURCE'), 'utf8'), `${git(repo, 'rev-parse', 'HEAD')}\n`);
    assert.equal(run(be, path.join(tmp, 'nogit-out')).status, 1);
    assert.ok(!fs.existsSync(path.join(tmp, 'nogit-out')));

    // BE không phải repo git nhưng nằm trong một repo git khác: không được lấy SHA của repo bên ngoài.
    const parent = path.join(tmp, 'parent-git');
    write(parent, 'x.md');
    git(parent, 'init', '-q');
    git(parent, 'add', '-A');
    git(parent, 'commit', '-qm', 'parent');
    const inner = path.join(parent, 'be-inner');
    makeBackend(inner);
    const innerOut = path.join(tmp, 'inner-out');
    assert.equal(run(inner, innerOut).status, 1);
    assert.ok(!fs.existsSync(innerOut));

    // GIT_DIR trỏ sang repo khác: không được lấy SHA của repo đó.
    const gitDirOut = path.join(tmp, 'gitdir-out');
    const result2 = runWithEnv({ ...CLEAN_ENV, GIT_DIR: path.join(repo, '.git') }, be, gitDirOut);
    assert.equal(result2.status, 1, `GIT_DIR lừa được script: ${result2.stdout}`);
    assert.ok(!fs.existsSync(gitDirOut));
  });

  check('thiếu hoặc sai tham số', () => {
    assert.equal(run().status, 2);
    assert.equal(run(be).status, 2);
    assert.equal(run(be, path.join(tmp, 'x'), '--sha').status, 2);
    assert.equal(run(be, path.join(tmp, 'x'), '--unknown', 'v').status, 2);
  });

  check('từ chối đích trùng, nằm trong, hoặc chứa thư mục BE', () => {
    for (const out of [be, path.join(be, 'out'), path.join(be, '..foo'), tmp]) {
      assertRefusedUntouched(run(be, out, '--sha', SHA), beMarker);
    }
    assert.ok(!fs.existsSync(path.join(be, 'out')), 'không được tạo thư mục trong BE');
    assert.ok(!fs.existsSync(path.join(be, '..foo')), 'không được tạo thư mục trong BE');
  });

  check('từ chối đích đi qua liên kết trỏ vào BE', () => {
    const victim = path.join(tmp, 'be-victim');
    makeBackend(victim);
    const victimMarker = path.join(victim, 'docs/agent/business-spec/AAA-001/Overview.md');
    const intoDocs = path.join(tmp, 'link-docs');
    link(path.join(victim, 'docs/agent'), intoDocs);
    assertRefusedUntouched(run(victim, intoDocs, '--sha', SHA), victimMarker);
    assert.ok(fs.existsSync(path.join(victim, 'docs/agent/decisions/0001-x.md')));

    const intoRoot = path.join(tmp, 'link-be');
    link(victim, intoRoot);
    assertRefusedUntouched(run(victim, path.join(intoRoot, 'new-out'), '--sha', SHA), victimMarker);
    assert.ok(!fs.existsSync(path.join(victim, 'new-out')), 'không được tạo thư mục trong BE qua liên kết');
  });

  check('fixture BE dùng chung còn nguyên', () => {
    assert.deepEqual(snapshot(path.join(be, 'docs')), (() => {
      const fresh = path.join(tmp, 'be-fresh');
      makeBackend(fresh);
      return snapshot(path.join(fresh, 'docs'));
    })());
  });

  check('SHA sai thì dừng, không đụng đích', () => {
    const out = path.join(tmp, 'sha-out');
    write(out, 'api/OLD-001/API_Spec.md');
    assertRefusedUntouched(run(be, out, '--sha', 'abc123'), path.join(out, 'api/OLD-001/API_Spec.md'));
  });

  check('thư mục mã lạ trong business-spec hoặc design thì dừng, không đụng đích', () => {
    for (const area of ['business-spec', 'design']) {
      const odd = path.join(tmp, `be-odd-${area}`);
      makeBackend(odd);
      write(odd, `docs/agent/${area}/booking/AAA-001/Overview.md`);
      const out = path.join(tmp, `odd-out-${area}`);
      write(out, 'business-spec/OLD-001/Overview.md');
      const result = run(odd, out, '--sha', SHA);
      assertRefusedUntouched(result, path.join(out, 'business-spec/OLD-001/Overview.md'));
      assert.match(result.stderr, /booking/);
    }
  });

  check('liên kết trong nguồn thì dừng, không đụng đích', () => {
    const outside = path.join(tmp, 'outside');
    write(outside, 'secret.md', 'không được publish\n');
    const outsideBackend = path.join(tmp, 'outside-be');
    makeBackend(outsideBackend);
    const cases = [
      ['thư mục docs', 'docs', path.join(outsideBackend, 'docs')],
      ['thư mục docs/agent', 'docs/agent', path.join(outsideBackend, 'docs/agent')],
      ['thư mục <MÃ> của SRS', 'docs/agent/business-spec/HHH-008', outside],
      ['thư mục con trong SRS', 'docs/agent/business-spec/AAA-001/linked', outside],
      ['file API_Spec', 'docs/agent/design/CCC-003/API_Spec.md', outside],
      ['file trong decisions', 'docs/agent/decisions/0002-y.md', outside],
      ['file api-convention', 'docs/agent/api-convention.md', outside],
    ];
    cases.forEach(([name, at, target], index) => {
      const linked = path.join(tmp, `be-link-${index}`);
      makeBackend(linked);
      fs.rmSync(path.join(linked, at), { recursive: true, force: true });
      link(target, path.join(linked, at));
      const out = path.join(tmp, `${path.basename(linked)}-out`);
      write(out, 'decisions/old.md');
      const result = run(linked, out, '--sha', SHA);
      assertRefusedUntouched(result, path.join(out, 'decisions/old.md'));
      assert.match(result.stderr, /liên kết/, name);
    });
  });

  check('README.md hoặc SOURCE ở đích là liên kết thì dừng, không đụng đích', () => {
    for (const name of ['README.md', 'SOURCE']) {
      const out = path.join(tmp, `dest-link-${name}`);
      write(out, 'decisions/old.md');
      link(path.join(tmp, 'outside'), path.join(out, name));
      assertRefusedUntouched(run(be, out, '--sha', SHA), path.join(out, 'decisions/old.md'));
    }
  });

  // Kiểm tài liệu cho FE: API_Spec + entry CHANGELOG mới nhất không được lộ nội bộ BE hay có link gãy ở đích.
  let lintIndex = 0;
  function lintRun(files) {
    lintIndex += 1;
    const root = path.join(tmp, `be-lint-${lintIndex}`);
    makeBackend(root);
    for (const [relative, content] of Object.entries(files)) write(root, `docs/agent/${relative}`, content);
    const out = path.join(tmp, `be-lint-${lintIndex}-out`);
    write(out, 'decisions/old.md');
    return { result: run(root, out, '--sha', SHA), marker: path.join(out, 'decisions/old.md') };
  }
  const withLines = (...lines) => `${apiSpec('| Phiên bản | v1.0 |')}${lines.join('\n')}\n`;

  check('API_Spec nhắc nội bộ BE thì dừng, báo file:dòng, không đụng đích', () => {
    for (const line of [
      'Chi tiết: `Processing_Flow.md` §3',
      'Xem `Component_Class_Design.md`',
      'Schema ở `docs/database/schema.dbml`',
      'Theo design-standards §11',
      'Chạy trong @Transactional',
      'Gọi TeamServiceImpl',
      'Đọc qua MatchRepository',
      'Hết lượt ConcurrencyConflictRetrier',
      'Migration V6__match_flow_a_lifecycle.sql',
    ]) {
      const { result, marker } = lintRun({ 'design/CCC-003/API_Spec.md': withLines('', line) });
      assertRefusedUntouched(result, marker);
      assert.match(result.stderr, /design\/CCC-003\/API_Spec\.md:11:/, line);
    }
  });

  check('nội dung trong comment HTML không bị chặn', () => {
    const { result } = lintRun({
      'design/CCC-003/API_Spec.md': withLines('<!-- Viết theo design-standards §11;', 'chi tiết ở Processing_Flow.md -->', 'Nội dung cho FE'),
    });
    assert.equal(result.status, 0, result.stderr);
  });

  check('CHANGELOG chỉ kiểm entry mới nhất', () => {
    const clean = lintRun({
      'design/AAA-001/CHANGELOG.md': '# CHANGELOG\n\n## [v2.0] — 2026-11-10\nThêm ví dụ JSON cho FE\n\n## [v1.0] — 2026-11-01\nSửa TeamServiceImpl\n',
    });
    assert.equal(clean.result.status, 0, clean.result.stderr);
    const dirty = lintRun({
      'design/AAA-001/CHANGELOG.md': '# CHANGELOG\n\n## [v2.0] — 2026-11-10\nSửa `Processing_Flow.md` §1\n\n## [v1.0] — 2026-11-01\nBản đầu\n',
    });
    assertRefusedUntouched(dirty.result, dirty.marker);
    assert.match(dirty.result.stderr, /design\/AAA-001\/CHANGELOG\.md:4:/);
    const crlf = lintRun({
      'design/AAA-001/CHANGELOG.md': '# CHANGELOG\r\n\r\n## [v2.0] — 2026-11-10\r\nSửa `Processing_Flow.md` §1\r\n',
    });
    assertRefusedUntouched(crlf.result, crlf.marker);
    assert.match(crlf.result.stderr, /design\/AAA-001\/CHANGELOG\.md:4:/);
  });

  check('CHANGELOG sai định dạng vẫn bị kiểm (phần mở đầu, heading lệch chuẩn)', () => {
    const preamble = lintRun({
      'design/AAA-001/CHANGELOG.md': '# CHANGELOG\nXem Processing_Flow.md\n\n## [v2.0] — 2026-11-10\nThêm ví dụ\n',
    });
    assertRefusedUntouched(preamble.result, preamble.marker);
    assert.match(preamble.result.stderr, /design\/AAA-001\/CHANGELOG\.md:2:/);
    const noEntry = lintRun({
      'design/AAA-001/CHANGELOG.md': '# CHANGELOG\n\n## v2 — 2026-11-10\nSửa TeamServiceImpl\n',
    });
    assertRefusedUntouched(noEntry.result, noEntry.marker);
    assert.match(noEntry.result.stderr, /design\/AAA-001\/CHANGELOG\.md:4:/);
  });

  check('link tương đối phải trỏ tới tài liệu được publish và còn đúng ở đích', () => {
    const ok = lintRun({
      'design/CCC-003/API_Spec.md': withLines(
        '[ADR](../../decisions/0001-x.md) · [API khác](../AAA-001/API_Spec.md#2-endpoints)',
        '[SRS](../../business-spec/AAA-001/) · [ngoài](https://example.com/a.md) · [mục](#1-tong-quan)',
        '[SRS không gạch cuối](../../business-spec/AAA-001) · [có title](../../decisions/0001-x.md "ADR 1")',
        '[ref]: ../AAA-001/API_Spec.md',
      ),
    });
    assert.equal(ok.result.status, 0, ok.result.stderr);
    for (const line of [
      '[x](../../api-convention.md)',
      '[x](../README.md)',
      '[x](../../decisions/_template.md)',
      '[x](../../decisions/9999-missing.md)',
      '[x](../DDD-004/)',
      '[x](/docs/agent/decisions/0001-x.md)',
      '[x](C:/docs/agent/decisions/0001-x.md)',
      '[x](../aaa-001/API_Spec.md)',
      '[x](../../api-convention.md "quy ước")',
      '[x](<../../api-convention.md>)',
      '[quy ước]: ../../api-convention.md',
    ]) {
      const { result, marker } = lintRun({ 'design/CCC-003/API_Spec.md': withLines(line) });
      assertRefusedUntouched(result, marker);
      assert.match(result.stderr, /CCC-003\/API_Spec\.md:10: link/, line);
    }
  });

  check('báo đủ mọi vi phạm trong một lần chạy', () => {
    const { result, marker } = lintRun({
      'design/CCC-003/API_Spec.md': withLines('Gọi TeamServiceImpl'),
      'design/AAA-001/API_Spec.md': withLines('[x](../../api-convention.md)'),
    });
    assertRefusedUntouched(result, marker);
    assert.match(result.stderr, /CCC-003\/API_Spec\.md/);
    assert.match(result.stderr, /AAA-001\/API_Spec\.md/);
  });

  check('thiếu thư mục nguồn thì dừng', () => {
    const partial = path.join(tmp, 'be-partial');
    makeBackend(partial);
    fs.rmSync(path.join(partial, 'docs/agent/decisions'), { recursive: true, force: true });
    assert.equal(run(partial, path.join(tmp, 'partial-out'), '--sha', SHA).status, 1);
    assert.ok(!fs.existsSync(path.join(tmp, 'partial-out')), 'không được tạo đích khi nguồn lỗi');
  });
} finally {
  fs.rmSync(tmp, { recursive: true, force: true });
}

if (failures.length) {
  process.stderr.write(`${failures.join('\n')}\n${failures.length}/${checks} publish-spec fixtures FAIL\n`);
  process.exitCode = 1;
} else {
  process.stdout.write(`PASS: ${checks} publish-spec fixtures\n`);
}
