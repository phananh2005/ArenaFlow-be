package com.phananh.arenaflow.api.config;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.common.exception.ErrorEnvelope;
import com.phananh.arenaflow.common.exception.ErrorResponse;
import com.phananh.arenaflow.common.exception.ValidationError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Ánh xạ exception → envelope lỗi. Lỗi 4xx → WARN (mã lỗi + path), 5xx → ERROR kèm exception;
 * response không lộ stack trace. NEVER log token/secret/PII.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PENDING_OR_ACTIVE_UNIQUE_INDEX = "idx_team_member_pending_or_active";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorEnvelope> handleBusinessException(
            BusinessException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Business error {} at {}", errorCode.getCode(), request.getRequestURI());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorEnvelope(ErrorResponse.from(errorCode, exception.getDetails(), request.getRequestURI())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationResponse(request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ValidationError(fieldName(violation.getPropertyPath()), violation.getMessage()))
                .toList();
        return validationResponse(request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorEnvelope> handleHandlerMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        List<ValidationError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return validationResponse(request, errors);
    }

    /** Path của violation tham số có dạng "<method>.<param>" → chỉ trả node cuối (tên field client gửi). */
    private static String fieldName(Path propertyPath) {
        String name = null;
        for (Path.Node node : propertyPath) {
            name = node.getName();
        }
        return name != null ? name : propertyPath.toString();
    }

    /** Body không phải JSON hợp lệ / sai kiểu field → 400 (không lộ chi tiết parser). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleMessageNotReadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return validationResponse(request, List.of(new ValidationError("body", "Nội dung request không hợp lệ")));
    }

    /** Path/query sai kiểu (UUID sai định dạng, enum không hợp lệ như status=BOGUS) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return validationResponse(request, List.of(new ValidationError(exception.getName(), "Giá trị không hợp lệ")));
    }

    /** Thiếu query param bắt buộc → 400 VALIDATION_ERROR, field = tên tham số. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorEnvelope> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return validationResponse(request,
                List.of(new ValidationError(exception.getParameterName(), "Tham số bắt buộc")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        String causeMessage = exception.getMostSpecificCause().getMessage();
        if (causeMessage != null && causeMessage.contains(PENDING_OR_ACTIVE_UNIQUE_INDEX)) {
            log.warn("Business error {} at {}", ErrorCode.TEAM_003.getCode(), request.getRequestURI());
            return businessResponse(ErrorCode.TEAM_003, request);
        }
        // Message/stack của lỗi SQL chứa giá trị dòng ("Detail: Key (phone_number)=(...)") → PII.
        // Chỉ log tên constraint + loại lỗi gốc.
        log.error("Unhandled data integrity violation at {} (constraint={}, cause={})", request.getRequestURI(),
                constraintName(exception), exception.getMostSpecificCause().getClass().getName());
        return internalServerError(request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        if (exception instanceof org.springframework.web.ErrorResponse frameworkError) {
            return frameworkErrorResponse(frameworkError, exception, request);
        }
        log.error("Unhandled exception at {}", request.getRequestURI(), exception);
        return internalServerError(request);
    }

    /**
     * Exception của Spring đã mang status → mã SYS_* (giữ status + header gốc, vd {@code Allow});
     * 400 → VALIDATION_ERROR; 4xx → WARN, còn lại → ERROR; status không chuẩn → 500.
     */
    private ResponseEntity<ErrorEnvelope> frameworkErrorResponse(org.springframework.web.ErrorResponse frameworkError,
                                                                 Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(frameworkError.getStatusCode().value());
        if (status == null) {
            log.error("Unhandled exception with non-standard status at {}", request.getRequestURI(), exception);
            return internalServerError(request);
        }
        if (status.is4xxClientError()) {
            log.warn("Framework error {} at {}", status.value(), request.getRequestURI());
        } else {
            log.error("Framework error {} at {}", status.value(), request.getRequestURI(), exception);
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return validationResponse(request, List.of());
        }
        ErrorCode errorCode = switch (status) {
            case NOT_FOUND -> ErrorCode.SYS_001;
            case METHOD_NOT_ALLOWED -> ErrorCode.SYS_002;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.SYS_003;
            default -> status.is4xxClientError() ? ErrorCode.SYS_004 : ErrorCode.SYS_005;
        };
        return ResponseEntity.status(status).headers(frameworkError.getHeaders())
                .body(new ErrorEnvelope(ErrorResponse.from(errorCode, null, request.getRequestURI())));
    }

    private static String constraintName(DataIntegrityViolationException exception) {
        String name = exception.getCause() instanceof org.hibernate.exception.ConstraintViolationException violation
                ? violation.getConstraintName()
                : null;
        return Objects.requireNonNullElse(name, "unknown");
    }

    private ResponseEntity<ErrorEnvelope> validationResponse(HttpServletRequest request, List<ValidationError> errors) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        ErrorResponse error = new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null,
                OffsetDateTime.now(), request.getRequestURI(), errors);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(new ErrorEnvelope(error));
    }

    private ResponseEntity<ErrorEnvelope> businessResponse(ErrorCode errorCode, HttpServletRequest request) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorEnvelope(ErrorResponse.from(errorCode, null, request.getRequestURI())));
    }

    private ResponseEntity<ErrorEnvelope> internalServerError(HttpServletRequest request) {
        return businessResponse(ErrorCode.SYS_005, request);
    }
}
