---
description: >
  Review nhanh code theo 5 chiều (Security, Performance, Correctness, Style,
  Reuse & Duplication) trong phiên, TRƯỚC khi commit. Kích hoạt khi implement xong, hoặc khi nghe
  "review", "check code", "kiểm tra code". Review toàn diện trước MR →
  agent code-reviewer.
---

# Code Review (nhanh, trước commit)

> Tiêu chí cụ thể lấy từ knowledge dự án. Skill chỉ định nghĩa 5 chiều — cùng bộ chiều với
> agent `code-reviewer`, bản rút gọn để chạy trong phiên.

## MUST trigger
- Implement xong thay đổi — **trước khi commit**.

**NEVER** commit khi chưa chạy đủ 5 chiều.

## Không chạy
- Commit **chỉ** đổi file tài liệu `*.md` (không có file nào khác trong diff) — `CLAUDE.md` §8. Có file code/cấu hình/hook/migration → vẫn chạy.

## Quy trình
1. **Phạm vi:** diff nào? Người dùng đã chỉ định → bắt đầu luôn.
2. **5 chiều:**
   - **Security** — secret, log lộ dữ liệu, truy cập dữ liệu tham số hoá, phân quyền đúng Spring Security.
   - **Performance** — ngưỡng số truy vấn `docs/agent/non-functional-standards.md`, N+1, phân trang, I/O trong vòng lặp.
   - **Correctness** — null/biên, lỗi, transaction, test đã chạy PASS.
   - **Style** — convention Java / Spring Boot, ranh giới module.
   - **Reuse** — Reuse Report có chưa (khi tạo thành phần dùng chung), trùng với thứ đã có, logic lặp.
3. **Phân loại:** CRITICAL (security hole, mất dữ liệu, logic sai) · MAJOR (hiệu năng, bảo trì) · MINOR (style).
4. **Đề xuất sửa:** mỗi CRITICAL/MAJOR kèm đoạn code gợi ý + `file:line`.

## Liên kết
- Review trước MR → agent `code-reviewer`
