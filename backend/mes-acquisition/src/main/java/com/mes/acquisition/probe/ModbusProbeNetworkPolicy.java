package com.mes.acquisition.probe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 探针目标网络策略。域名只解析一次并将校验后的 IP 交给通讯通道，
 * 防止校验与连接之间发生 DNS 重绑定。
 */
@Component
@Lazy
public class ModbusProbeNetworkPolicy {

    private final List<Cidr> allowedCidrs;
    private final Set<Integer> allowedPorts;

    public ModbusProbeNetworkPolicy(
            @Value("${mes.modbus-probe.allowed-cidrs:}") String cidrs,
            @Value("${mes.modbus-probe.allowed-ports:502}") String ports) {
        this.allowedCidrs = Arrays.stream(cidrs.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(Cidr::parse).toList();
        this.allowedPorts = Arrays.stream(ports.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(Integer::parseInt).collect(Collectors.toUnmodifiableSet());
        if (allowedPorts.stream().anyMatch(port -> port < 1 || port > 65535)) {
            throw new IllegalArgumentException("Modbus 探针端口白名单包含非法端口");
        }
    }

    /** 校验目标并返回固定 IP。所有 DNS 结果都必须满足白名单。 */
    public String validateAndResolve(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new ProbeException(ProbeErrorCategory.VALIDATION, "host 不能为空");
        }
        if (!allowedPorts.contains(port)) {
            throw new ProbeException(ProbeErrorCategory.SECURITY, "目标端口不在白名单中");
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host.trim());
        } catch (UnknownHostException ex) {
            throw new ProbeException(ProbeErrorCategory.DNS, "无法解析目标主机", ex);
        }
        if (addresses.length == 0) {
            throw new ProbeException(ProbeErrorCategory.DNS, "目标主机没有可用地址");
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                throw new ProbeException(ProbeErrorCategory.SECURITY, "禁止访问 loopback/link-local 地址");
            }
            if (allowedCidrs.stream().noneMatch(cidr -> cidr.contains(address))) {
                throw new ProbeException(ProbeErrorCategory.SECURITY, "目标地址不在 CIDR 白名单中");
            }
        }
        return addresses[0].getHostAddress();
    }

    private record Cidr(byte[] network, int prefixLength) {
        private static Cidr parse(String text) {
            try {
                String[] parts = text.split("/", -1);
                InetAddress address = InetAddress.getByName(parts[0]);
                int bits = address.getAddress().length * 8;
                int prefix = parts.length == 1 ? bits : Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > bits) {
                    throw new IllegalArgumentException("CIDR 前缀越界:" + text);
                }
                byte[] network = address.getAddress().clone();
                mask(network, prefix);
                return new Cidr(network, prefix);
            } catch (Exception ex) {
                throw new IllegalArgumentException("非法 Modbus 探针 CIDR:" + text, ex);
            }
        }

        private boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress().clone();
            if (candidate.length != network.length) {
                return false;
            }
            mask(candidate, prefixLength);
            return Arrays.equals(network, candidate);
        }

        private static void mask(byte[] bytes, int prefix) {
            int fullBytes = prefix / 8;
            int remaining = prefix % 8;
            if (remaining != 0 && fullBytes < bytes.length) {
                bytes[fullBytes] &= (byte) (0xFF << (8 - remaining));
                fullBytes++;
            }
            Arrays.fill(bytes, fullBytes, bytes.length, (byte) 0);
        }
    }
}
