package com.taskpilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${taskpilot.cors.allowed-origins:http://localhost:5173,https://task-pilot-ai-study-recovery-agent.vercel.app}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        Set<String> origins = new LinkedHashSet<>();
        // Always allow local development and the verified production frontend.
        origins.add("http://localhost:5173");
        origins.add("https://task-pilot-ai-study-recovery-agent.vercel.app");
        for (String part : allowedOrigins.split(",")) {
            String origin = part.trim();
            if (origin.endsWith("/")) {
                origin = origin.substring(0, origin.length() - 1);
            }
            if (!origin.isBlank()) {
                origins.add(origin);
            }
        }
        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("MCP-Session-Id", "MCP-Protocol-Version");
    }
}
