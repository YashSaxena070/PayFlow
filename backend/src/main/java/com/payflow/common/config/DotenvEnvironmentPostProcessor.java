package com.payflow.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically loads .env variables into Spring Boot Environment on startup.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File[] candidates = new File[] {
                new File(".env"),
                new File("../.env"),
                new File("payment-orchestrator/.env")
        };

        for (File envFile : candidates) {
            if (envFile.exists() && envFile.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(envFile.toPath());
                    Map<String, Object> props = new HashMap<>();
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int idx = line.indexOf('=');
                        if (idx > 0) {
                            String key = line.substring(0, idx).trim();
                            String val = line.substring(idx + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            props.put(key, val);
                        }
                    }
                    if (!props.isEmpty()) {
                        environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", props));
                        log.info("Loaded {} environment properties from {}", props.size(), envFile.getAbsolutePath());
                    }
                    return;
                } catch (Exception e) {
                    log.warn("Failed to parse .env file at {}: {}", envFile.getAbsolutePath(), e.getMessage());
                }
            }
        }
    }
}

