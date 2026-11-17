package com.payflow.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically loads .env variables into Spring Boot Environment on startup
 * and normalizes cloud PostgreSQL URLs (e.g., Render, Railway, Neon).
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 1. Load from .env files if present (local dev)
        loadDotenvFiles(environment);

        // 2. Normalize Cloud Database URLs (Render, Railway, Heroku)
        normalizeDatabaseConfiguration(environment);
    }

    private void loadDotenvFiles(ConfigurableEnvironment environment) {
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

    private void normalizeDatabaseConfiguration(ConfigurableEnvironment environment) {
        String dbUrl = environment.getProperty("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = environment.getProperty("POSTGRES_URL");
        }

        if (dbUrl != null && !dbUrl.isBlank()) {
            Map<String, Object> normalized = new HashMap<>();

            // If Render or cloud provides postgres:// or postgresql:// (URI format)
            if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
                try {
                    URI uri = new URI(dbUrl);
                    String host = uri.getHost();
                    int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                    String path = uri.getPath();
                    String query = uri.getQuery();

                    String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + (path != null ? path : "/payflow")
                            + (query != null ? "?" + query : "");

                    normalized.put("spring.datasource.url", jdbcUrl);
                    normalized.put("spring.datasource.driver-class-name", "org.postgresql.Driver");

                    if (uri.getUserInfo() != null) {
                        String[] userPass = uri.getUserInfo().split(":", 2);
                        normalized.put("spring.datasource.username", userPass[0]);
                        if (userPass.length > 1) {
                            normalized.put("spring.datasource.password", userPass[1]);
                        }
                    }

                    log.info("Transformed Cloud URI Database URL into JDBC format: {}", jdbcUrl);
                } catch (Exception e) {
                    log.error("Failed to parse Cloud DATABASE_URL URI: {}", e.getMessage());
                }
            } else if (dbUrl.startsWith("jdbc:postgresql:")) {
                normalized.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
            } else if (dbUrl.startsWith("jdbc:h2:")) {
                normalized.put("spring.datasource.driver-class-name", "org.h2.Driver");
            }

            if (!normalized.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource("normalizedDatabaseProps", normalized));
            }
        }
    }
}
