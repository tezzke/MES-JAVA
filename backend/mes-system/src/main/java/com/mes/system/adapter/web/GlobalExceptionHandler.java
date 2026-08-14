package com.mes.system.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 全局安全错误响应。服务端异常只记录关联标识，不向客户端泄露 SQL、堆栈或凭据。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception,
                                               HttpServletRequest request) {
        List<String> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct().toList();
        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_FAILED", "请求参数不合法", correlationId(request), fields));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException exception,
                                           HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(
                exception.getStatusCode().value() == 401 ? "UNAUTHORIZED" : "REQUEST_FAILED",
                exception.getReason() == null ? "请求失败" : exception.getReason(),
                correlationId(request), List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException exception,
                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
                "FORBIDDEN", "权限不足", correlationId(request), List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception,
                                               HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                "BAD_REQUEST", exception.getMessage(), correlationId(request), List.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException exception,
                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "CONFLICT", exception.getMessage(), correlationId(request), List.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound(NoResourceFoundException exception,
                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(
                "NOT_FOUND", "资源不存在", correlationId(request), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        String id = correlationId(request);
        log.error("未处理请求异常(correlationId={})", id, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiError("INTERNAL_ERROR", "服务器内部错误", id, List.of()));
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

    /** 标准错误响应。 */
    public record ApiError(String code, String message, String correlationId, List<String> fields) {
    }
}
