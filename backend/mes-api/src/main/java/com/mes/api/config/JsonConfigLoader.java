package com.mes.api.config;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

/**
 * 现场配置文件(带注释的 JSON)的通用加载器。
 * <p>
 * 项目里有多份"现场只改这一个文件"的配置(devices.json 采集点表、plant.json 厂房模型),
 * 它们的加载规则完全一致 —— 允许注释、PascalCase 属性名、按"外部指定 → 工作目录 → jar 内置"查找,
 * 因此把这套规则收在这里,新增一份配置文件时不必再抄一遍。
 */
public final class JsonConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(JsonConfigLoader.class);

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

    private JsonConfigLoader() {
    }

    /**
     * 定位并解析配置文件。
     *
     * @param configuredPath  配置项指定的绝对/相对路径,为空则走默认查找顺序。
     * @param defaultFileName 默认文件名,如 {@code devices.json}。
     */
    public static JsonNode readTree(String configuredPath, String defaultFileName) throws IOException {
        Resource resource = locate(configuredPath, defaultFileName);
        try (InputStream input = resource.getInputStream()) {
            log.info("加载配置文件 {}:{}", defaultFileName, resource.getDescription());
            return CONFIG_MAPPER.readTree(input);
        }
    }

    /**
     * 取出根节点下的某个配置节并绑定成对象,节点缺失时返回 {@code fallback} 提供的默认值。
     *
     * @param fileName 文件名,仅用于日志与异常信息定位问题。
     */
    public static <T> T section(JsonNode root, String section, Class<T> type,
                                Supplier<T> fallback, String fileName) {
        JsonNode node = root == null ? null : root.get(section);
        if (node == null || node.isNull()) {
            log.warn("{} 缺少 \"{}\" 节点,使用默认值", fileName, section);
            return fallback.get();
        }
        try {
            return CONFIG_MAPPER.treeToValue(node, type);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    fileName + " 的 \"" + section + "\" 节点格式错误:" + ex.getMessage(), ex);
        }
    }

    /** 把整个根节点绑定成对象。 */
    public static <T> T whole(JsonNode root, Class<T> type, Supplier<T> fallback, String fileName) {
        if (root == null || root.isNull()) {
            log.warn("{} 内容为空,使用默认值", fileName);
            return fallback.get();
        }
        try {
            return CONFIG_MAPPER.treeToValue(root, type);
        } catch (Exception ex) {
            throw new IllegalStateException(fileName + " 格式错误:" + ex.getMessage(), ex);
        }
    }

    /** 按"外部指定 → 工作目录 → jar 内置"的顺序定位配置文件。 */
    private static Resource locate(String configuredPath, String defaultFileName) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            FileSystemResource specified = new FileSystemResource(configuredPath);
            if (!specified.exists()) {
                throw new IllegalStateException("指定的配置文件不存在:" + configuredPath);
            }
            return specified;
        }

        FileSystemResource external = new FileSystemResource(defaultFileName);
        if (external.exists()) {
            return external;
        }
        return new ClassPathResource(defaultFileName);
    }
}
