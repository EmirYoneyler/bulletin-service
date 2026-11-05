package com.fonbulteni.bulletinservice.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Maps every exception that reaches the MVC layer to an {@link ApiError}. Spring MVC's own
 * exceptions (405, 415, malformed JSON, ...) go through {@link #handleExceptionInternal} so that
 * all errors share one shape; anything unexpected becomes a 500 without leaking internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Beklenmeyen bir hata oluştu.", request.getRequestURI(), null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldViolation> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        ApiError body = apiError(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "İstek doğrulanamadı.", pathOf(request), fields);
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String code;
        String message;
        if (ex instanceof HttpMessageNotReadableException) {
            code = "MALFORMED_REQUEST";
            message = "İstek gövdesi okunamadı. JSON biçimini ve alan değerlerini kontrol edin.";
        } else if (ex instanceof MethodArgumentTypeMismatchException e) {
            code = "INVALID_PARAMETER";
            message = "'" + e.getName() + "' parametresinin değeri geçersiz.";
        } else if (ex instanceof TypeMismatchException) {
            code = "INVALID_PARAMETER";
            message = "Parametre değeri geçersiz.";
        } else if (ex instanceof MissingServletRequestParameterException e) {
            code = "MISSING_PARAMETER";
            message = "'" + e.getParameterName() + "' parametresi zorunlu.";
        } else if (ex instanceof NoResourceFoundException) {
            code = "NOT_FOUND";
            message = "İstenen adres bulunamadı.";
        } else if (ex instanceof HttpRequestMethodNotSupportedException e) {
            code = "METHOD_NOT_ALLOWED";
            message = "Bu adres " + e.getMethod() + " yöntemini desteklemiyor.";
        } else if (ex instanceof HttpMediaTypeNotSupportedException) {
            code = "UNSUPPORTED_MEDIA_TYPE";
            message = "Desteklenmeyen içerik türü.";
        } else {
            code = codeFor(statusCode);
            message = "İstek işlenemedi.";
        }
        return new ResponseEntity<>(apiError(statusCode, code, message, pathOf(request), null), headers, statusCode);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String code, String message, String path, List<ApiError.FieldViolation> fields) {
        return ResponseEntity.status(status).body(apiError(status, code, message, path, fields));
    }

    private ApiError apiError(
            HttpStatusCode status, String code, String message, String path, List<ApiError.FieldViolation> fields) {
        return new ApiError(OffsetDateTime.now(clock), status.value(), code, message, path, fields);
    }

    private static String codeFor(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "HTTP_" + statusCode.value();
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
