package com.mes.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.mes.core.options.AcquisitionOptions;
import com.mes.core.options.ScannerOptions;
import com.mes.core.options.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 设备档案配置加载:把 devices.json 绑定成 {@link AcquisitionOptions} 与 {@link ScannerOptions} 两个单例。
 * <p>
 * 为什么不用 application.yml 承载设备点表:
 * <ul>
 *   <li>点表是现场调试的产物,单独一个文件便于版本管理、比对与整厂复制部署;</li>
 *   <li>与 .NET 版保持同一份配置文件格式(含注释的 JSON),现场迁移无需改写配置。</li>
 * </ul>
 * 查找顺序:{@code mes.devices-config} 指定的路径 → 工作目录下的 devices.json → 打包在 jar 内的默认档案。
 * 因此生产环境把 devices.json 放在 jar 旁边即可覆盖内置档案,"现场只改一个文件"。
 * <p>
 * 厂房 3D 模型不在本文件内,见 {@link PlantLayoutConfiguration}。
 */
@Configuration
public class DeviceCatalogConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DeviceCatalogConfiguration.class);

    private static final String DEFAULT_FILE_NAME = "devices.json";

    private final String configuredPath;

    public DeviceCatalogConfiguration(@Value("${mes.devices-config:}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    /** devices.json 的完整解析结果,供下面两个 Bean 取用。 */
    @Bean
    public JsonNode deviceCatalogRoot() throws IOException {
        return JsonConfigLoader.readTree(configuredPath, DEFAULT_FILE_NAME);
    }

    @Bean
    public AcquisitionOptions acquisitionOptions(JsonNode deviceCatalogRoot) {
        AcquisitionOptions options = JsonConfigLoader.section(deviceCatalogRoot,
                AcquisitionOptions.SECTION_NAME, AcquisitionOptions.class,
                AcquisitionOptions::new, DEFAULT_FILE_NAME);
        log.info("采集配置:模式={},设备 {} 台(启用 {} 台),点位模板 {} 套",
                options.getMode(), options.getDevices().size(),
                options.enabledDevices().size(), options.getPointTemplates().size());
        return options;
    }

    @Bean
    public ScannerOptions scannerOptions(JsonNode deviceCatalogRoot) {
        ScannerOptions options = JsonConfigLoader.section(deviceCatalogRoot,
                ScannerOptions.SECTION_NAME, ScannerOptions.class,
                ScannerOptions::new, DEFAULT_FILE_NAME);
        log.info("扫码枪配置:启用={},监听端口={},绑定 {} 把",
                options.isEnabled(), options.getListenPort(), options.getScannerBindings().size());
        return options;
    }

    /** 存储策略来自 application.yml 的 storage 节点。 */
    @Bean
    @ConfigurationProperties(prefix = "storage")
    public StorageOptions storageOptions() {
        return new StorageOptions();
    }
}
