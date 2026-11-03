package com.phananh.ArenaFlow.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ánh xạ exception → envelope lỗi. Log theo Processing_Flow §6: lỗi nghiệp vụ 4xx → WARN (mã lỗi + path),
 * 5xx → ERROR kèm exception; response không lộ stack trace. NEVER log token/secret.
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
                .map(violation -> new ValidationError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return validationResponse(request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorEnvelope> handleHandlerMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        return validationResponse(request, List.of(new ValidationError("request", "Dữ liệu không hợp lệ")));
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        String causeMessage = exception.getMostSpecificCause().getMessage();
        if (causeMessage != null && causeMessage.contains(PENDING_OR_ACTIVE_UNIQUE_INDEX)) {
            log.warn("Business error {} at {}", ErrorCode.TEAM_003.getCode(), request.getRequestURI());
            return businessResponse(ErrorCode.TEAM_003, request);
        }
        log.error("Unhandled data integrity violation at {}", request.getRequestURI(), exception);
        return internalServerError(request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), exception);
        return internalServerError(request);
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
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "Đã xảy ra lỗi hệ thống", null,
                OffsetDateTime.now(), request.getRequestURI(), List.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorEnvelope(error));
    }
}
