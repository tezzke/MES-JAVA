package com.mes.api.config;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mes.core.options.AcquisitionOptions;
import com.mes.core.options.ScannerOptions;
import com.mes.core.options.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

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
 */
@Configuration
public class DeviceCatalogConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DeviceCatalogConfiguration.class);

    private static final String DEFAULT_FILE_NAME = "devices.json";

    /**
     * 专用于读取配置文件的 ObjectMapper:
     * 允许注释与尾随逗号(配置文件里的注释是重要的现场说明),属性名按 PascalCase 匹配。
     */
    private static final ObjectMapper CONFIG_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final String configuredPath;

    public DeviceCatalogConfiguration(@Value("${mes.devices-config:}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    /** devices.json 的完整解析结果,供下面两个 Bean 取用。 */
    @Bean
    public JsonNode deviceCatalogRoot() throws IOException {
        Resource resource = locate();
        try (InputStream input = resource.getInputStream()) {
            log.info("加载设备档案:{}", resource.getDescription());
            return CONFIG_MAPPER.readTree(input);
        }
    }

    @Bean
    public AcquisitionOptions acquisitionOptions(JsonNode deviceCatalogRoot) {
        AcquisitionOptions options = read(deviceCatalogRoot, AcquisitionOptions.SECTION_NAME,
                AcquisitionOptions.class, AcquisitionOptions::new);
        log.info("采集配置:模式={},设备 {} 台(启用 {} 台),点位模板 {} 套",
                options.getMode(), options.getDevices().size(),
                options.enabledDevices().size(), options.getPointTemplates().size());
        return options;
    }

    @Bean
    public ScannerOptions scannerOptions(JsonNode deviceCatalogRoot) {
        ScannerOptions options = read(deviceCatalogRoot, ScannerOptions.SECTION_NAME,
                ScannerOptions.class, ScannerOptions::new);
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

    private <T> T read(JsonNode root, String section, Class<T> type,
                       java.util.function.Supplier<T> fallback) {
        JsonNode node = root.get(section);
        if (node == null || node.isNull()) {
            log.warn("devices.json 缺少 \"{}\" 节点,使用默认值", section);
            return fallback.get();
        }
        try {
            return CONFIG_MAPPER.treeToValue(node, type);
        } catch (Exception ex) {
            throw new IllegalStateException("devices.json 的 \"" + section + "\" 节点格式错误:" + ex.getMessage(), ex);
        }
    }

    /** 按"外部指定 → 工作目录 → jar 内置"的顺序定位配置文件。 */
    private Resource locate() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            FileSystemResource specified = new FileSystemResource(configuredPath);
            if (!specified.exists()) {
                throw new IllegalStateException("mes.devices-config 指定的配置文件不存在:" + configuredPath);
            }
            return specified;
        }

        FileSystemResource external = new FileSystemResource(DEFAULT_FILE_NAME);
        if (external.exists()) {
            return external;
        }
        return new ClassPathResource(DEFAULT_FILE_NAME);
    }
}
