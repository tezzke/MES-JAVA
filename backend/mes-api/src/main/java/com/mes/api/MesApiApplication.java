package com.mes.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Mes.Api 组合根:装配 采集层 / 处理存储层 / 接口层,启动整个 MES 后端。
 * <p>
 * 三层放在同一个进程内托管:15 台设备的规模下单进程绰绰有余,部署只需拷一个目录;
 * 三层代码物理隔离在不同 Maven 模块里,将来要拆成独立采集服务时只需换宿主。
 * <p>
 * 整体架构与数据流见 docs/架构设计.md。
 */
@SpringBootApplication(scanBasePackages = "com.mes")
public class MesApiApplication {

    private static final Logger log = LoggerFactory.getLogger(MesApiApplication.class);

    public static void main(String[] args) {
        // SQLite 数据文件固定放在 工作目录/data/ 下,随目录整体拷贝即可迁移。
        // 必须在容器启动(建立数据库连接)之前创建好目录。
        prepareDataDirectory();
        SpringApplication.run(MesApiApplication.class, args);
    }

    private static void prepareDataDirectory() {
        Path dataDir = Paths.get(System.getProperty("mes.data-dir", "data")).toAbsolutePath();
        try {
            Files.createDirectories(dataDir);
            log.info("数据目录:{}", dataDir);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建数据目录:" + dataDir, ex);
        }
    }
}
