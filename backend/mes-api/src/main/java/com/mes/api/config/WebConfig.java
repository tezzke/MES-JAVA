package com.mes.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Web 层通用配置:跨域 + 前端静态资源托管。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 前端构建产物的查找位置,按顺序命中:
     *   1. jar 同级的 wwwroot/ —— 现场只换前端时,直接替换目录即可,不用重新打包;
     *   2. jar 内置的 static/ —— 一体化交付时把 frontend/dist 放进资源目录一起打包。
     */
    private static final String[] STATIC_LOCATIONS = { "file:./wwwroot/", "classpath:/static/" };

    /** 这些前缀不做 index.html 兜底,否则接口 404 会变成一个 HTML 页面 */
    private static final String[] NO_FALLBACK_PREFIXES = { "api/", "hubs/", "v3/api-docs", "swagger", "webjars/" };
    private final List<String> allowedOrigins;

    public WebConfig(@Value("${mes.security.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                     String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 开发阶段前端(Vite:5173)与后端(5100)跨端口,需要放开 CORS;
        // 生产部署时前端构建产物由本服务直接托管,同源其实无需 CORS。
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-XSRF-TOKEN", "X-Correlation-ID")
                .allowCredentials(true);
    }

    /**
     * 托管前端产物,并为前端 history 路由做兜底:
     * 请求的静态文件不存在时返回 index.html,刷新 /alarms 这类前端路由不会 404。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATIONS)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        for (String prefix : NO_FALLBACK_PREFIXES) {
                            if (resourcePath.startsWith(prefix)) {
                                return null;
                            }
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }
}
