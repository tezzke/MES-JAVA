package com.mes.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.mes.core.plant.PlantLayout;
import com.mes.core.plant.PlantShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 厂房模型配置加载:把 plant.json 绑定成 {@link PlantLayout} 单例。
 * <p>
 * 与 {@link DeviceCatalogConfiguration} 并列而非合并,是因为两份配置的责任人不同 ——
 * 点表归电气调试、厂房模型归图纸,拆开后任一方改动都不会波及另一方。
 * <p>
 * 查找顺序:{@code mes.plant-config} 指定的路径 → 工作目录下的 plant.json → 打包在 jar 内的默认档案。
 */
@Configuration
public class PlantLayoutConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlantLayoutConfiguration.class);

    private static final String DEFAULT_FILE_NAME = "plant.json";

    private final String configuredPath;

    public PlantLayoutConfiguration(@Value("${mes.plant-config:}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @Bean
    public PlantLayout plantLayout() throws IOException {
        JsonNode root = JsonConfigLoader.readTree(configuredPath, DEFAULT_FILE_NAME);
        PlantLayout layout = JsonConfigLoader.whole(root, PlantLayout.class,
                PlantLayout::new, DEFAULT_FILE_NAME);

        PlantShell shell = layout.getPlant();
        log.info("厂房模型:{}(图号 {}),{}×{} m,轴网 {}×{},隔墙 {} 段,分区 {} 个,设备外观模型 {} 类",
                shell.getName(), shell.getDrawingNo(),
                String.format("%.1f", shell.getEnvelope().width()),
                String.format("%.1f", shell.getEnvelope().depth()),
                shell.getAxisGrid().getAxesX().size(), shell.getAxisGrid().getAxesZ().size(),
                shell.getPartitions().size(), shell.getZones().size(),
                layout.getDeviceModels().size());
        return layout;
    }
}
