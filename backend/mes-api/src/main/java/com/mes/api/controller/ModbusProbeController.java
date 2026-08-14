package com.mes.api.controller;

import com.mes.acquisition.probe.ModbusProbeModels.ConnectRequest;
import com.mes.acquisition.probe.ModbusProbeModels.ConnectResult;
import com.mes.acquisition.probe.ModbusProbeModels.ReadRequest;
import com.mes.acquisition.probe.ModbusProbeModels.ReadResult;
import com.mes.acquisition.probe.ModbusProbeModels.SessionRequest;
import com.mes.acquisition.probe.ModbusProbeModels.SessionView;
import com.mes.acquisition.probe.ModbusProbeService;
import com.mes.acquisition.probe.ProbeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** 受限的 Modbus 现场诊断接口。 */
@RestController
@RequestMapping("/api/modbus-probe")
@Tag(name = "Modbus 现场探针", description = "临时连接、读取与短时轮询诊断")
@PreAuthorize("hasAuthority('MODBUS_PROBE')")
@ConditionalOnProperty(name = "mes.modbus-probe.enabled", havingValue = "true")
public class ModbusProbeController {

    private final ModbusProbeService service;

    public ModbusProbeController(ModbusProbeService service) {
        this.service = service;
    }

    @PostMapping("/connect-test")
    @Operation(summary = "测试 TCP 连接")
    public ConnectResult connectTest(@RequestBody ConnectRequest request) {
        try {
            return service.connectTest(request);
        } catch (ProbeException ex) {
            throw status(ex);
        }
    }

    @PostMapping("/read")
    @Operation(summary = "单次读取并解码")
    public ReadResult read(@RequestBody ReadRequest request) {
        try {
            return service.read(request);
        } catch (ProbeException ex) {
            throw status(ex);
        }
    }

    @PostMapping("/sessions")
    @Operation(summary = "启动最长 60 秒的轮询会话")
    public SessionView start(@RequestBody SessionRequest request) {
        try {
            return service.startSession(request);
        } catch (ProbeException ex) {
            throw status(ex);
        }
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "查询轮询会话")
    public SessionView get(@PathVariable UUID id) {
        try {
            return service.getSession(id);
        } catch (ProbeException ex) {
            throw status(ex);
        }
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "停止轮询会话")
    public SessionView stop(@PathVariable UUID id) {
        try {
            return service.stopSession(id);
        } catch (ProbeException ex) {
            throw status(ex);
        }
    }

    private static ResponseStatusException status(ProbeException ex) {
        HttpStatus status = switch (ex.category()) {
            case SECURITY -> HttpStatus.FORBIDDEN;
            case BUSY -> HttpStatus.TOO_MANY_REQUESTS;
            case DNS -> HttpStatus.BAD_GATEWAY;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return new ResponseStatusException(status, ex.getMessage(), ex);
    }
}
