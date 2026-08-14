package com.mes.production.adapter.web;

import com.mes.production.application.ProductionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 生产域冲突响应，避免将非法状态和乐观锁冲突误报为服务端错误。
 */
@RestControllerAdvice(basePackages = "com.mes.production.adapter.web")
public class ProductionExceptionHandler {

    @ExceptionHandler({IllegalStateException.class, ProductionService.OptimisticLockException.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException exception,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", exception instanceof ProductionService.OptimisticLockException
                        ? "OPTIMISTIC_LOCK_CONFLICT" : "STATE_CONFLICT",
                "message", exception.getMessage(),
                "correlationId", nullToEmpty(MasterDataController.correlationId(request))));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
