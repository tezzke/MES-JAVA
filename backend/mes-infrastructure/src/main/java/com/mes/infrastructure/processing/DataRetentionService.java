package com.mes.infrastructure.processing;

import com.mes.core.contract.TelemetryRepository;
import com.mes.core.hosting.BackgroundService;
import com.mes.core.options.StorageOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 数据保留清理服务(后台常驻):
 * 每 24 小时删除一次超过保留期(storage.retentionDays)的遥测历史,
 * 防止 SQLite 数据文件无限增长写满磁盘。
 * 报警与扫码记录数据量小且有追溯价值,默认永久保留。
 */
@Component
@Order(20)
public class DataRetentionService extends BackgroundService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TelemetryRepository repository;
    private final StorageOptions options;

    public DataRetentionService(TelemetryRepository repository, StorageOptions options) {
        this.repository = repository;
        this.options = options;
    }

    @Override
    protected void execute() {
        // 启动 1 分钟后先清一次(处理停机积压),之后每 24 小时一次
        long delayMs = Duration.ofMinutes(1).toMillis();

        while (!isStopping()) {
            if (!delay(delayMs)) {
                break;
            }
            delayMs = Duration.ofHours(24).toMillis();

            try {
                Instant cutoff = Instant.now().minus(Duration.ofDays(options.getRetentionDays()));
                int deleted = repository.deleteBefore(cutoff);
                if (deleted > 0) {
                    log.info("数据保留清理完成:删除 {} 条 {} 之前的遥测记录",
                            deleted, LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault()).format(DAY));
                }
            } catch (Exception ex) {
                log.error("数据保留清理失败,将在下个周期重试", ex);
            }
        }
    }
}
