package com.phananh.arenaflow.application.support;

/** Dựng mẫu cho {@code LIKE}/{@code ILIKE} của PostgreSQL từ từ khoá người dùng nhập. */
public final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * Mẫu "chứa từ khoá": bọc {@code %…%}, ký tự {@code %} và {@code _} trong từ khoá được so khớp nguyên văn.
     * Backslash là ký tự escape mặc định của {@code LIKE} trong PostgreSQL nên được escape trước.
     *
     * @param keyword từ khoá đã cắt khoảng trắng; {@code null} hoặc rỗng nghĩa là không lọc
     * @return mẫu {@code LIKE}, hoặc {@code null} khi không có từ khoá
     */
    public static String contains(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        return "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
