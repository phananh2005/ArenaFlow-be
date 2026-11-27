package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuỗi tìm người để mời đã phân loại. Lớp thuần, không truy vấn: nhánh tên chỉ chuẩn hoá (bỏ dấu) bằng hàm SQL
 * {@code user_name_fold} ở DB rồi mới gọi {@link #ofFolded(String)} — Java không giữ bảng ký tự có dấu.
 */
public sealed interface InvitationSearchQuery {

    /** Số điện thoại đã chuẩn hoá về dạng lưu trong DB: "0" + 9 chữ số. */
    record Phone(String phoneNumber) implements InvitationSearchQuery {
    }

    /** Chuỗi tìm theo tên đã trim, chưa bỏ dấu. */
    record Name(String raw) implements InvitationSearchQuery {
    }

    /**
     * Chuỗi tìm theo tên đã bỏ dấu: {@code tsQuery} dạng {@code w1:* & w2:*} (mỗi từ khớp đầu một từ trong tên),
     * {@code prefix} là các từ nối bằng một dấu cách (dùng ưu tiên tên bắt đầu bằng chuỗi nhập).
     */
    record FoldedName(String tsQuery, String prefix) implements InvitationSearchQuery {
    }

    /**
     * Phân loại {@code q} (đã qua {@code @NotBlank}): chỉ gồm chữ số và {@code + . -} khoảng trắng → số điện thoại,
     * bỏ khoảng trắng/chấm/gạch, đầu {@code +84}/{@code 84} đổi thành {@code 0}, phải còn đúng 10 chữ số bắt đầu bằng
     * 0, nếu không → USER_005. Còn lại → tìm theo tên.
     */
    static InvitationSearchQuery parse(String q) {
        String trimmed = q.strip();
        if (!InvitationSearchPatterns.PHONE_LIKE.matcher(trimmed).matches()) {
            return new Name(trimmed);
        }
        String digits = InvitationSearchPatterns.PHONE_SEPARATORS.matcher(trimmed).replaceAll("");
        Matcher phone = InvitationSearchPatterns.VIETNAM_PHONE.matcher(digits);
        if (!phone.matches()) {
            throw new BusinessException(ErrorCode.USER_005);
        }
        return new Phone("0" + phone.group(2));
    }

    /**
     * {@code folded} là kết quả {@code user_name_fold(q)}: các từ {@code [a-z0-9]+} cách nhau một dấu cách. Chỉ giữ
     * token khớp {@code [a-z0-9]+} (phòng khi hàm SQL bị sửa sai, ký tự cú pháp tsquery không lọt vào chuỗi bind);
     * tổng số ký tự còn lại dưới 2 → USER_006.
     */
    static FoldedName ofFolded(String folded) {
        List<String> tokens = folded == null ? List.of() : Arrays.stream(folded.split(" "))
                .filter(token -> InvitationSearchPatterns.FOLDED_TOKEN.matcher(token).matches())
                .toList();
        if (tokens.stream().mapToInt(String::length).sum() < InvitationSearchPatterns.MIN_NAME_CHARS) {
            throw new BusinessException(ErrorCode.USER_006);
        }
        String tsQuery = String.join(" & ", tokens.stream().map(token -> token + ":*").toList());
        return new FoldedName(tsQuery, String.join(" ", tokens));
    }
}

/** Mẫu dùng nội bộ của {@link InvitationSearchQuery} — tách ra để không thành hằng public của interface. */
final class InvitationSearchPatterns {

    static final Pattern PHONE_LIKE = Pattern.compile("[0-9+.\\-\\s]+");
    static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s.\\-]");
    static final Pattern VIETNAM_PHONE = Pattern.compile("^(\\+84|84|0)(\\d{9})$");
    static final Pattern FOLDED_TOKEN = Pattern.compile("^[a-z0-9]+$");
    static final int MIN_NAME_CHARS = 2;

    private InvitationSearchPatterns() {
    }
}
