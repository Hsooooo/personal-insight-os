package com.pios.config;

import com.pios.common.NotFoundException;
import com.pios.dto.ApiResponse;
import com.pios.service.GarminSyncService.TooFrequentSyncException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 모든 오류를 {@link ApiResponse} 형태로 변환한다.
 * 4xx는 원인 메시지를 그대로(필드 단위) 돌려주고, 5xx는 내부 정보를 숨기고 서버 로그에만 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------- 400: 입력 검증 ----------

    /** @Valid @RequestBody / @ModelAttribute 검증 실패 (MethodArgumentNotValidException은 BindException의 하위 타입) */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleBind(BindException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return validationError(errors);
    }

    /** Spring 6.1+ 내장 메서드 검증 (@RequestParam @Min 등) */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String key = name != null ? name : "param" + result.getMethodParameter().getParameterIndex();
            String message = result.getResolvableErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            errors.putIfAbsent(key, message);
        });
        return validationError(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            String path = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "value";
            int dot = path.lastIndexOf('.');
            errors.putIfAbsent(dot >= 0 ? path.substring(dot + 1) : path, v.getMessage());
        }
        return validationError(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.debug("Malformed request body: {}", e.getMessage());
        return ApiResponse.error("Malformed or unreadable request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "valid value";
        return validationError(Map.of(e.getName(), "must be a valid " + expected));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleMissingParam(MissingServletRequestParameterException e) {
        return validationError(Map.of(e.getParameterName(), "is required"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleMissingPart(MissingServletRequestPartException e) {
        return validationError(Map.of(e.getRequestPartName(), "is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.error(e.getMessage());
    }

    // ---------- 403 / 404 / 405 / 409 / 413 / 415 / 429 ----------

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
        return ApiResponse.error(e.getMessage() != null ? e.getMessage() : "Access denied");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NotFoundException e) {
        return ApiResponse.error(e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException e) {
        return ApiResponse.error("Not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResponse.error("Method " + e.getMethod() + " not supported");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiResponse<Void> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ApiResponse.error("Unsupported content type");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ApiResponse.error("Uploaded file is too large");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ApiResponse.error("Request conflicts with existing data");
    }

    @ExceptionHandler(TooFrequentSyncException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleTooFrequentSync(TooFrequentSyncException e) {
        return ApiResponse.error(e.getMessage());
    }

    /** ResponseStatusException 등 상태 코드를 직접 들고 있는 예외는 그대로 전달한다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException e) {
        return statusResponse(e.getStatusCode(), e.getReason(), e);
    }

    // ---------- 500 ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // Spring MVC 내장 예외(ErrorResponse 구현체)는 고유 상태 코드를 유지
        if (e instanceof ErrorResponse er) {
            String detail = er.getBody().getDetail();
            return statusResponse(er.getStatusCode(), detail, e);
        }
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error"));
    }

    private ResponseEntity<ApiResponse<Void>> statusResponse(HttpStatusCode status, String message, Exception e) {
        if (status.is5xxServerError()) {
            log.error("Server error ({})", status.value(), e);
            return ResponseEntity.status(status).body(ApiResponse.error("Internal server error"));
        }
        String msg = message != null && !message.isBlank() ? message
                : (status instanceof HttpStatus hs ? hs.getReasonPhrase() : "Request failed");
        return ResponseEntity.status(status).body(ApiResponse.error(msg));
    }

    private ApiResponse<Map<String, String>> validationError(Map<String, String> errors) {
        String detail = errors.entrySet().stream()
                .map(en -> en.getKey() + ": " + en.getValue())
                .collect(Collectors.joining("; "));
        return ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message(detail.isEmpty() ? "Validation failed" : "Validation failed: " + detail)
                .data(errors)
                .build();
    }
}
