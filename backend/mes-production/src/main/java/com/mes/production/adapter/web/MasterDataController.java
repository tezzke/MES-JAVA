package com.mes.production.adapter.web;

import com.mes.core.contract.PagedResult;
import com.mes.production.application.MasterDataService;
import com.mes.production.domain.MasterData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

/** 物料、产品、BOM、组织设备、工序和工艺路线主数据接口。 */
@RestController
@RequestMapping("/api/master")
public class MasterDataController {
    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    /** 分页查询指定类型主数据。 */
    @GetMapping("/{type}")
    @PreAuthorize("hasAuthority('MASTER_READ')")
    public PagedResult<MasterData.Item> list(@PathVariable MasterData.Type type,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(type, page, size);
    }

    /** 查询主数据详情。 */
    @GetMapping("/detail/{id}")
    @PreAuthorize("hasAuthority('MASTER_READ')")
    public MasterData.Item detail(@PathVariable long id) {
        return service.detail(id);
    }

    /** 新增主数据。 */
    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_WRITE')")
    public Map<String, Long> create(@PathVariable MasterData.Type type,
            @Valid @RequestBody MasterRequest body, Principal principal, HttpServletRequest request) {
        return Map.of("id", service.save(body.toItem(null, type), principal.getName(),
                correlationId(request)));
    }

    /** 按版本更新主数据。 */
    @PutMapping("/{type}/{id}")
    @PreAuthorize("hasAuthority('MASTER_WRITE')")
    public Map<String, Long> update(@PathVariable MasterData.Type type, @PathVariable long id,
            @Valid @RequestBody MasterRequest body, Principal principal, HttpServletRequest request) {
        service.save(body.toItem(id, type), principal.getName(), correlationId(request));
        return Map.of("id", id);
    }

    /** 主数据保存 DTO。 */
    public record MasterRequest(@NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 120) String name, Long referenceId, Long secondaryReferenceId,
            @Min(1) Integer sequence, BigDecimal quantity,
            @NotNull Map<@Size(max = 64) String, @Size(max = 255) String> attributes,
            boolean enabled, @Min(0) long version) {
        MasterData.Item toItem(Long id, MasterData.Type type) {
            return new MasterData.Item(id, type, code, name, referenceId, secondaryReferenceId,
                    sequence, quantity, attributes, enabled, version);
        }
    }

    static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute("mes.correlationId");
        return value == null ? request.getHeader("X-Correlation-ID") : value.toString();
    }
}

