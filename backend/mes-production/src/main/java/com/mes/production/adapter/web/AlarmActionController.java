package com.mes.production.adapter.web;

import com.mes.core.contract.PagedResult;
import com.mes.production.application.AlarmActionService;
import com.mes.production.domain.ProductionModels.AlarmAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/** MySQL 报警确认、指派、处理和关闭接口。 */
@RestController
@RequestMapping("/api/alarm-actions")
public class AlarmActionController {
    private final AlarmActionService service;

    public AlarmActionController(AlarmActionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ALARM_ACTION_READ')")
    public PagedResult<AlarmAction> list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ALARM_ACTION_READ')")
    public AlarmAction detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping("/source/{sourceAlarmId}/acknowledge")
    @PreAuthorize("hasAuthority('ALARM_ACTION_WRITE')")
    public Map<String, Long> acknowledge(@PathVariable long sourceAlarmId, Principal principal,
            HttpServletRequest request) {
        return Map.of("id", service.acknowledge(sourceAlarmId, principal.getName(),
                MasterDataController.correlationId(request)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('ALARM_ACTION_WRITE')")
    public Map<String, Long> assign(@PathVariable long id, @Valid @RequestBody AssignRequest body,
            Principal principal, HttpServletRequest request) {
        service.assign(id, body.version(), body.assignee(), principal.getName(),
                MasterDataController.correlationId(request));
        return Map.of("id", id);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('ALARM_ACTION_WRITE')")
    public Map<String, Long> resolve(@PathVariable long id, @Valid @RequestBody ResolveRequest body,
            Principal principal, HttpServletRequest request) {
        service.resolve(id, body.version(), body.resolution(), principal.getName(),
                MasterDataController.correlationId(request));
        return Map.of("id", id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ALARM_ACTION_WRITE')")
    public Map<String, Long> close(@PathVariable long id, @Valid @RequestBody VersionRequest body,
            Principal principal, HttpServletRequest request) {
        service.close(id, body.version(), principal.getName(),
                MasterDataController.correlationId(request));
        return Map.of("id", id);
    }

    /** 指派 DTO。 */
    public record AssignRequest(@Min(0) long version,
            @NotBlank @Size(max = 100) String assignee) { }

    /** 处理 DTO。 */
    public record ResolveRequest(@Min(0) long version,
            @NotBlank @Size(max = 1000) String resolution) { }

    /** 乐观锁版本 DTO。 */
    public record VersionRequest(@Min(0) long version) { }
}

