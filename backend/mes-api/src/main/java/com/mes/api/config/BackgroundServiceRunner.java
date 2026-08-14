package com.mes.api.config;

import com.mes.core.hosting.BackgroundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 后台服务的统一启停器(相当于 .NET 的 AddHostedService 宿主)。
 * <p>
 * 组合根在这里持有全部 {@link BackgroundService},按 {@code @Order} 升序启动、降序停止:
 * <pre>
 *   启动:处理管道(10)→ 数据清理(20)→ 采集调度(30)→ 扫码接入(40)
 *   停止:扫码接入 → 采集调度 → 数据清理 → 处理管道
 * </pre>
 * 顺序是刻意安排的:先启动消费者(处理管道)再启动生产者(采集),启动瞬间的数据不会无人接收;
 * 停机时反过来,先停生产者、最后停处理管道,管道才能把队列与缓冲区里剩下的数据全部落库。
 */
@Component
public class BackgroundServiceRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BackgroundServiceRunner.class);

    /** 单个服务的停机等待上限(毫秒)。 */
    private static final long STOP_TIMEOUT_MS = 10_000;

    private final List<BackgroundService> services;
    private volatile boolean running;

    public BackgroundServiceRunner(List<BackgroundService> services) {
        this.services = services; // Spring 按 @Order 注入有序列表
    }

    @Override
    public void start() {
        for (BackgroundService service : services) {
            log.info("启动后台服务:{}", service.serviceName());
            service.start();
        }
        running = true;
    }

    @Override
    public void stop() {
        // 反序停止:先让上游停下,再让下游把缓冲数据处理完
        List<BackgroundService> reversed = new ArrayList<>(services);
        Collections.reverse(reversed);
        for (BackgroundService service : reversed) {
            log.info("停止后台服务:{}", service.serviceName());
            service.stop(STOP_TIMEOUT_MS);
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
