package com.mes.acquisition.probe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 探针 SSRF 防护规则测试。 */
class ModbusProbeNetworkPolicyTest {

    private final ModbusProbeNetworkPolicy policy =
            new ModbusProbeNetworkPolicy("192.0.2.0/24", "502,1502");

    @Test
    void acceptsWhitelistedAddressAndPort() {
        assertThat(policy.validateAndResolve("192.0.2.10", 502)).isEqualTo("192.0.2.10");
    }

    @Test
    void rejectsLoopbackEvenWhenCidrIncludesIt() {
        ModbusProbeNetworkPolicy localPolicy =
                new ModbusProbeNetworkPolicy("127.0.0.0/8", "502");

        assertThatThrownBy(() -> localPolicy.validateAndResolve("127.0.0.1", 502))
                .isInstanceOfSatisfying(ProbeException.class,
                        ex -> assertThat(ex.category()).isEqualTo(ProbeErrorCategory.SECURITY));
    }

    @Test
    void rejectsAddressOrPortOutsideWhitelist() {
        assertThatThrownBy(() -> policy.validateAndResolve("198.51.100.1", 502))
                .isInstanceOf(ProbeException.class);
        assertThatThrownBy(() -> policy.validateAndResolve("192.0.2.10", 503))
                .isInstanceOf(ProbeException.class);
    }
}
