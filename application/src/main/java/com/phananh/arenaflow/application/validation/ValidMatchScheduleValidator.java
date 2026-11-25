package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.CreateMatchRequest;
import com.phananh.arenaflow.application.dto.request.TimeOptionRequest;
import com.phananh.arenaflow.application.support.match.MatchRules;
import com.phananh.arenaflow.common.time.AppTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quy tắc field theo luồng và thời gian của kèo.
 * <ul>
 *   <li>Luồng A: bắt buộc {@code venueId}, {@code startsAt}, {@code endsAt}; không nhận field của luồng B; bắt đầu ≥
 *       bây giờ + 1 giờ; kết thúc sau bắt đầu và ≤ bắt đầu + 12 giờ; hạn nhận đối sau bây giờ và trong
 *       [bắt đầu − 7 ngày, bắt đầu].</li>
 *   <li>Luồng B: không nhận field của luồng A; 1..5 lựa chọn địa điểm khác nhau; 1..5 khung giờ không trùng giờ bắt
 *       đầu, mỗi khung theo cùng quy tắc bắt đầu / kết thúc của luồng A (lỗi gắn {@code timeOptions[i].startsAt},
 *       {@code timeOptions[i].endsAt}); hạn nhận đối sau bây giờ và trong [S − 7 ngày, S] với S là giờ bắt đầu sớm
 *       nhất.</li>
 * </ul>
 * Kiểm thời gian chỉ khi field liên quan khác null (để {@code @NotNull} báo lỗi thiếu).
 */
public class ValidMatchScheduleValidator implements ConstraintValidator<ValidMatchSchedule, CreateMatchRequest> {

    static final String EXPIRES_WINDOW_MESSAGE =
            "Thời hạn phải nằm trong 7 ngày trước giờ bắt đầu trận";
    static final String EXPIRES_AFTER_EARLIEST_MESSAGE = "Thời hạn không được sau khung giờ sớm nhất";
    static final String EXPIRES_BEFORE_EARLIEST_WINDOW_MESSAGE =
            "Thời hạn phải nằm trong 7 ngày trước khung giờ sớm nhất";

    /**
     * Kiểm request theo {@code type}; gom mọi lỗi (không dừng ở lỗi đầu) và gắn vào tên field của request.
     *
     * @return {@code true} khi không có lỗi
     */
    @Override
    public boolean isValid(CreateMatchRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        Violations violations = new Violations(context);
        LocalDateTime now = AppTime.now();
        if (CreateMatchRequest.NEED_VENUE.equals(request.type())) {
            validateFlowB(request, now, violations);
        } else {
            validateFlowA(request, now, violations);
        }
        return violations.none();
    }

    /** Luồng A: field bắt buộc / không được gửi, khoảng giờ trận và hạn nhận đối theo giờ bắt đầu. */
    private static void validateFlowA(CreateMatchRequest request, LocalDateTime now, Violations violations) {
        violations.requireNotNull(request.venueId(), "venueId");
        violations.requireNotNull(request.startsAt(), "startsAt");
        violations.requireNotNull(request.endsAt(), "endsAt");
        if (request.venueOptionIds() != null) {
            violations.add("venueOptionIds", null);
        }
        if (request.timeOptions() != null) {
            violations.add("timeOptions", null);
        }

        LocalDateTime startsAt = request.startsAt();
        if (startsAt != null && startsAt.isBefore(now.plus(MatchRules.MIN_LEAD_TIME))) {
            violations.add("startsAt", null);
        }
        if (startsAt != null && request.endsAt() != null && !isValidEnd(startsAt, request.endsAt())) {
            violations.add("endsAt", null);
        }
        LocalDateTime expiresAt = request.expiresAt();
        if (expiresAt != null) {
            if (!expiresAt.isAfter(now)) {
                violations.add("expiresAt", null);
            } else if (startsAt != null && (expiresAt.isAfter(startsAt)
                    || expiresAt.isBefore(startsAt.minus(MatchRules.MAX_POSTING_WINDOW)))) {
                violations.add("expiresAt", EXPIRES_WINDOW_MESSAGE);
            }
        }
    }

    /** Luồng B: field của luồng A không được gửi, lựa chọn địa điểm, khung giờ và hạn nhận đối theo khung sớm nhất. */
    private static void validateFlowB(CreateMatchRequest request, LocalDateTime now, Violations violations) {
        if (request.venueId() != null) {
            violations.add("venueId", null);
        }
        if (request.startsAt() != null) {
            violations.add("startsAt", null);
        }
        if (request.endsAt() != null) {
            violations.add("endsAt", null);
        }
        if (!isValidVenueOptions(request.venueOptionIds())) {
            violations.add("venueOptionIds", null);
        }
        LocalDateTime earliestStart = validateTimeOptions(request.timeOptions(), now, violations);

        LocalDateTime expiresAt = request.expiresAt();
        if (expiresAt != null) {
            if (!expiresAt.isAfter(now)) {
                violations.add("expiresAt", null);
            } else if (earliestStart != null && expiresAt.isAfter(earliestStart)) {
                violations.add("expiresAt", EXPIRES_AFTER_EARLIEST_MESSAGE);
            } else if (earliestStart != null && expiresAt.isBefore(earliestStart.minus(MatchRules.MAX_POSTING_WINDOW))) {
                violations.add("expiresAt", EXPIRES_BEFORE_EARLIEST_WINDOW_MESSAGE);
            }
        }
    }

    /** 1..{@code MAX_VENUE_OPTIONS} id, không null, không trùng. */
    private static boolean isValidVenueOptions(List<UUID> venueOptionIds) {
        if (venueOptionIds == null || venueOptionIds.isEmpty() || venueOptionIds.size() > MatchRules.MAX_VENUE_OPTIONS
                || venueOptionIds.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        return new HashSet<>(venueOptionIds).size() == venueOptionIds.size();
    }

    /**
     * Kiểm số khung giờ (lỗi gắn {@code timeOptions}) rồi từng khung: trùng giờ bắt đầu với khung trước, bắt đầu quá
     * sớm → {@code timeOptions[i].startsAt}; kết thúc sai → {@code timeOptions[i].endsAt}.
     *
     * @return giờ bắt đầu sớm nhất trong các khung có giờ bắt đầu; {@code null} khi không có khung hợp lệ để so
     */
    private static LocalDateTime validateTimeOptions(List<TimeOptionRequest> timeOptions, LocalDateTime now,
                                                     Violations violations) {
        if (timeOptions == null || timeOptions.isEmpty() || timeOptions.size() > MatchRules.MAX_TIME_OPTIONS
                || timeOptions.stream().anyMatch(Objects::isNull)) {
            violations.add("timeOptions", null);
            return null;
        }
        Set<LocalDateTime> seenStarts = new HashSet<>();
        for (int i = 0; i < timeOptions.size(); i++) {
            TimeOptionRequest option = timeOptions.get(i);
            LocalDateTime startsAt = option.startsAt();
            if (startsAt != null && (!seenStarts.add(startsAt) || startsAt.isBefore(now.plus(MatchRules.MIN_LEAD_TIME)))) {
                violations.addIndexed("timeOptions", i, "startsAt");
            }
            if (startsAt != null && option.endsAt() != null && !isValidEnd(startsAt, option.endsAt())) {
                violations.addIndexed("timeOptions", i, "endsAt");
            }
        }
        return timeOptions.stream().map(TimeOptionRequest::startsAt).filter(Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null);
    }

    /** Kết thúc sau bắt đầu và không quá {@code MAX_DURATION}. */
    private static boolean isValidEnd(LocalDateTime startsAt, LocalDateTime endsAt) {
        return endsAt.isAfter(startsAt) && !endsAt.isAfter(startsAt.plus(MatchRules.MAX_DURATION));
    }

    /** Gom lỗi theo tên field; {@code message} null = message mặc định của annotation. */
    private static final class Violations {
        private final ConstraintValidatorContext context;
        private boolean any;

        /** Gắn vào context của lần kiểm hiện tại. */
        Violations(ConstraintValidatorContext context) {
            this.context = context;
        }

        /** Thêm lỗi cho {@code field} khi {@code value} null. */
        void requireNotNull(Object value, String field) {
            if (value == null) {
                add(field, null);
            }
        }

        /** Thêm lỗi gắn vào field mức gốc của request. */
        void add(String field, String message) {
            any = true;
            context.buildConstraintViolationWithTemplate(message == null ? context.getDefaultConstraintMessageTemplate() : message)
                    .addPropertyNode(field)
                    .addConstraintViolation();
        }

        /** Thêm lỗi (message mặc định) gắn vào field của phần tử thứ {@code index} trong danh sách: {@code list[index].field}. */
        void addIndexed(String list, int index, String field) {
            any = true;
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(list)
                    .addPropertyNode(field).inIterable().atIndex(index)
                    .addConstraintViolation();
        }

        /** {@code true} khi chưa có lỗi nào. */
        boolean none() {
            return !any;
        }
    }
}
