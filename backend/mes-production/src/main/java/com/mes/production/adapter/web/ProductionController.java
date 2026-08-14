package com.mes.production.adapter.web;

import com.mes.core.contract.PagedResult;
import com.mes.production.application.ProductionService;
import com.mes.production.domain.ProductionModels.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/** 工单、生产任务、扫码报工和批次条码追溯接口。 */
@RestController
@RequestMapping("/api/production")
public class ProductionController {
    private final ProductionService service;

    public ProductionController(ProductionService service) {
        this.service = service;
    }

    @GetMapping("/work-orders")
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public PagedResult<WorkOrder> orders(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.orders(page, size);
    }

    @GetMapping("/work-orders/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public WorkOrder order(@PathVariable long id) {
        return service.order(id);
    }

    @PostMapping("/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public Map<String, Long> create(@Valid @RequestBody WorkOrderRequest body,
            Principal principal, HttpServletRequest request) {
        return Map.of("id", save(null, body, principal, request));
    }

    @PutMapping("/work-orders/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public Map<String, Long> update(@PathVariable long id, @Valid @RequestBody WorkOrderRequest body,
            Principal principal, HttpServletRequest request) {
        return Map.of("id", save(id, body, principal, request));
    }

    @DeleteMapping("/work-orders/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public void delete(@PathVariable long id, @RequestParam @Min(0) long version,
            Principal principal, HttpServletRequest request) {
        service.deleteOrder(id, version, principal.getName(),
                MasterDataController.correlationId(request));
    }

    @PostMapping("/work-orders/{id}/release")
    @PreAuthorize("hasAuthority('PRODUCTION_RELEASE')")
    public Map<String, Integer> release(@PathVariable long id, @Valid @RequestBody VersionRequest body,
            Principal principal, HttpServletRequest request) {
        return Map.of("generatedTasks", service.release(id, body.version(), principal.getName(),
                MasterDataController.correlationId(request)));
    }

    @GetMapping("/work-orders/{id}/tasks")
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public List<ProductionTask> tasks(@PathVariable long id) {
        return service.tasks(id);
    }

    @GetMapping("/work-orders/{id}/progress")
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public WorkOrderProgress progress(@PathVariable long id) {
        return service.progress(id);
    }

    @PostMapping("/tasks/{id}/start")
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public Map<String, Long> start(@PathVariable long id, @Valid @RequestBody ScanRequest body,
            @RequestHeader("Idempotency-Key") @Size(max = 100) String key,
            Principal principal, HttpServletRequest request) {
        return Map.of("id", service.startTask(id, body.version(), body.barcode(), key,
                principal.getName(), MasterDataController.correlationId(request)));
    }

    @PostMapping("/tasks/{id}/report")
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public Map<String, Long> report(@PathVariable long id, @Valid @RequestBody ReportRequest body,
            @RequestHeader("Idempotency-Key") @Size(max = 100) String key,
            Principal principal, HttpServletRequest request) {
        return Map.of("id", service.report(id, body.taskVersion(), body.orderVersion(), body.good(),
                body.bad(), body.barcode(), key, principal.getName(),
                MasterDataController.correlationId(request)));
    }

    @PostMapping("/tasks/{id}/complete")
    @PreAuthorize("hasAuthority('PRODUCTION_EXECUTE')")
    public Map<String, Long> complete(@PathVariable long id, @Valid @RequestBody ScanRequest body,
            @RequestHeader("Idempotency-Key") @Size(max = 100) String key,
            Principal principal, HttpServletRequest request) {
        return Map.of("id", service.completeTask(id, body.version(), body.barcode(), key,
                principal.getName(), MasterDataController.correlationId(request)));
    }

    @GetMapping("/trace")
    @PreAuthorize("hasAuthority('TRACE_READ')")
    public PagedResult<TraceEvent> trace(@RequestParam(required = false) @Size(max = 100) String batchNo,
            @RequestParam(required = false) @Size(max = 200) String barcode,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.trace(batchNo, barcode, page, size);
    }

    private long save(Long id, WorkOrderRequest body, Principal principal, HttpServletRequest request) {
        return service.saveOrder(id, body.orderNo(), body.productId(), body.routeId(), body.batchNo(),
                body.plannedQuantity(), body.version(), principal.getName(),
                MasterDataController.correlationId(request));
    }

    /** 工单保存 DTO。 */
    public record WorkOrderRequest(@NotBlank @Size(max = 64) String orderNo,
            @Min(1) long productId, @Min(1) long routeId,
            @NotBlank @Size(max = 100) String batchNo,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal plannedQuantity,
            @Min(0) long version) { }

    /** 乐观锁版本 DTO。 */
    public record VersionRequest(@Min(0) long version) { }

    /** 扫码动作 DTO。 */
    public record ScanRequest(@Min(0) long version,
            @NotBlank @Size(max = 200) String barcode) { }

    /** 报工 DTO。 */
    public record ReportRequest(@Min(0) long taskVersion, @Min(0) long orderVersion,
            @NotNull @DecimalMin("0") BigDecimal good,
            @NotNull @DecimalMin("0") BigDecimal bad,
            @NotBlank @Size(max = 200) String barcode) { }
}

