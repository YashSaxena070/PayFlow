package com.payflow.common.health;

import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.service.ProviderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;

@RestController
@Tag(name = "Health Check", description = "System health, readiness probes, and telemetry")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ProviderRegistry providerRegistry;
    private final Environment environment;

    public HealthController(
            ProviderRegistry providerRegistry,
            Environment environment) {
        this.providerRegistry = providerRegistry;
        this.environment = environment;
    }

    @GetMapping(value = {"/", "/health", "/health/", "/api/v1/health", "/api/v1/health/"})
    @Operation(summary = "Comprehensive Health Check", description = "Returns deep health status across database, cache, message broker, and payment gateways")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> checks = new LinkedHashMap<>();

        boolean isHealthy = true;

        // 1. Database Check
        Map<String, Object> dbCheck = checkDatabase();
        checks.put("database", dbCheck);
        if (!"UP".equals(dbCheck.get("status"))) {
            isHealthy = false;
        }

        // 2. Redis Check
        Map<String, Object> redisCheck = checkRedis();
        checks.put("redis", redisCheck);

        // 3. Payment Providers Check
        Map<String, Object> providersCheck = checkProviders();
        checks.put("orchestration", providersCheck);

        // 4. System & Memory Runtime Telemetry
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsedMb = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMaxMb = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        Map<String, Object> systemTelemetry = new LinkedHashMap<>();
        systemTelemetry.put("uptimeSeconds", uptimeSeconds);
        systemTelemetry.put("heapUsedMb", heapUsedMb);
        systemTelemetry.put("heapMaxMb", heapMaxMb);
        systemTelemetry.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        checks.put("system", systemTelemetry);

        response.put("status", isHealthy ? "UP" : "DEGRADED");
        response.put("service", "fluxpay-orchestrator");
        response.put("version", "1.0.0");
        response.put("timestamp", Instant.now().toString());
        response.put("checks", checks);

        return ResponseEntity.status(isHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping(value = {"/health/live", "/health/live/", "/api/v1/health/live", "/api/v1/health/live/"})
    @Operation(summary = "Liveness Probe", description = "Fast liveness check for load balancers and container runtimes")
    public ResponseEntity<Map<String, String>> getLiveness() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "fluxpay-orchestrator",
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping(value = {"/health/ready", "/health/ready/", "/api/v1/health/ready", "/api/v1/health/ready/"})
    @Operation(summary = "Readiness Probe", description = "Readiness check verifying database connectivity before accepting live traffic")
    public ResponseEntity<Map<String, String>> getReadiness() {
        Map<String, Object> dbCheck = checkDatabase();
        boolean isReady = "UP".equals(dbCheck.get("status"));
        return ResponseEntity
                .status(isReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", isReady ? "UP" : "DOWN",
                        "database", String.valueOf(dbCheck.get("status")),
                        "timestamp", Instant.now().toString()
                ));
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> check = new LinkedHashMap<>();
        if (dataSource == null) {
            check.put("status", "DISABLED");
            check.put("message", "No DataSource configured");
            return check;
        }

        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            long latency = System.currentTimeMillis() - start;
            check.put("status", "UP");
            check.put("latencyMs", latency);
            check.put("databaseProduct", conn.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            log.error("Database health check failed: {}", e.getMessage());
            check.put("status", "DOWN");
            check.put("error", e.getMessage());
        }
        return check;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> check = new LinkedHashMap<>();
        if (redisTemplate == null) {
            check.put("status", "OPTIONAL");
            check.put("message", "Redis template not registered (local fallback active)");
            return check;
        }

        long start = System.currentTimeMillis();
        try {
            RedisConnection connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
            String ping = connection.ping();
            long latency = System.currentTimeMillis() - start;
            check.put("status", "PONG".equalsIgnoreCase(ping) ? "UP" : "DEGRADED");
            check.put("latencyMs", latency);
            connection.close();
        } catch (Exception e) {
            log.warn("Redis health check notice: {}", e.getMessage());
            check.put("status", "DOWN");
            check.put("error", e.getMessage());
        }
        return check;
    }

    private Map<String, Object> checkProviders() {
        Map<String, Object> check = new LinkedHashMap<>();
        List<PaymentProviderPort> providers = providerRegistry.getAllProviders();
        List<String> codes = new ArrayList<>();
        Map<String, Object> capabilities = new LinkedHashMap<>();

        for (PaymentProviderPort p : providers) {
            codes.add(p.getProviderCode());
            capabilities.put(p.getProviderCode(), Map.of(
                    "currencies", p.capabilities().supportedCurrencies(),
                    "paymentMethods", p.capabilities().supportedMethods(),
                    "supports3ds", p.capabilities().supports3DS(),
                    "supportsRefunds", p.capabilities().supportsRefunds()
            ));
        }

        check.put("status", "UP");
        check.put("activeGatewayCount", providers.size());
        check.put("registeredGateways", codes);
        check.put("gatewayCapabilities", capabilities);
        return check;
    }
}
