package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.CreateMatchRequest;
import com.phananh.arenaflow.application.support.match.MatchRules;
import com.phananh.arenaflow.common.time.AppTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;

/**
 * Luồng A: bắt buộc {@code venueId}, {@code startsAt}, {@code endsAt}; không nhận field của luồng B; bắt đầu ≥ bây giờ
 * + 1 giờ; kết thúc sau bắt đầu và ≤ bắt đầu + 12 giờ; hạn nhận đối sau bây giờ và trong [bắt đầu − 7 ngày, bắt đầu].
 * Luồng B chưa phát hành: bỏ qua (service trả MATCH_016). Kiểm thời gian chỉ khi field liên quan khác null.
 */
public class ValidMatchScheduleValidator implements ConstraintValidator<ValidMatchSchedule, CreateMatchRequest> {

    static final String EXPIRES_WINDOW_MESSAGE =
            "Thời hạn phải trong khoảng tối đa 7 ngày trước giờ đá đến giờ bắt đầu";

    @Override
    public boolean isValid(CreateMatchRequest request, ConstraintValidatorContext context) {
        if (request == null || CreateMatchRequest.NEED_VENUE.equals(request.type())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        Violations violations = new Violations(context);
        LocalDateTime now = AppTime.now();

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
        if (startsAt != null && request.endsAt() != null
                && (!request.endsAt().isAfter(startsAt) || request.endsAt().isAfter(startsAt.plus(MatchRules.MAX_DURATION)))) {
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
        return violations.none();
    }

    /** Gom lỗi theo tên field; {@code message} null = message mặc định của annotation. */
    private static final class Violations {
        private final ConstraintValidatorContext context;
        private boolean any;

        Violations(ConstraintValidatorContext context) {
            this.context = context;
        }

        void requireNotNull(Object value, String field) {
            if (value == null) {
                add(field, null);
            }
        }

        void add(String field, String message) {
            any = true;
            context.buildConstraintViolationWithTemplate(message == null ? context.getDefaultConstraintMessageTemplate() : message)
                    .addPropertyNode(field)
                    .addConstraintViolation();
        }

        boolean none() {
            return !any;
        }
    }
}
