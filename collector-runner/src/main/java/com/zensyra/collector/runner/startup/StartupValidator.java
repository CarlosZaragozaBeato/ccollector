package com.zensyra.collector.runner.startup;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Validates that all required environment variables are present at startup.
 * Fails fast with a clear error message instead of crashing mid-execution.
 */
@ApplicationScoped
public class StartupValidator {

    private static final Logger LOG = Logger.getLogger(StartupValidator.class);

    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "quarkus.datasource.username",
            "quarkus.datasource.password",
            "quarkus.datasource.jdbc.url",
            "admin.token",
            "collector.encryption.key",
            "collector.api.key"
    );

    void onStart(@Observes StartupEvent event) {
        List<String> missing = new ArrayList<>();

        for (String property : REQUIRED_PROPERTIES) {
            try {
                String value = ConfigProvider.getConfig().getValue(property, String.class);
                if (value == null || value.isBlank()) {
                    missing.add(property);
                }
            } catch (NoSuchElementException e) {
                missing.add(property);
            }
        }

        validateEncryptionKeyLength(missing);

        if (!missing.isEmpty()) {
            String message = "Startup validation failed — missing or empty required properties: " + missing
                    + ". Check your environment variables and .env configuration.";
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        LOG.info("Startup validation passed.");
    }

    private void validateEncryptionKeyLength(List<String> missing) {
        if (missing.contains("collector.encryption.key")) {
            return;
        }
        try {
            String keyBase64 = ConfigProvider.getConfig().getValue("collector.encryption.key", String.class);
            byte[] keyBytes = java.util.Base64.getDecoder().decode(keyBase64);
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                        "collector.encryption.key (COLLECTOR_ENCRYPTION_KEY) must be exactly 32 bytes "
                        + "(256-bit) base64-encoded. Got " + keyBytes.length + " bytes. "
                        + "Generate with: openssl rand -base64 32");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "collector.encryption.key (COLLECTOR_ENCRYPTION_KEY) is not valid base64.", e);
        }
    }
}
