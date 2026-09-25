package com.pios.config;

import com.pios.domain.LlmProvider;
import com.pios.domain.ProviderConnection;
import com.pios.repository.LlmProviderRepository;
import com.pios.repository.ProviderConnectionRepository;
import com.pios.security.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Startup migration for LLM API keys and Garmin passwords:
 * encrypts plaintext rows and re-encrypts rows still encrypted with the legacy key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecretMigrationRunner implements ApplicationRunner {

    private final LlmProviderRepository llmRepo;
    private final ProviderConnectionRepository providerRepo;
    private final SecretCryptoService secretCrypto;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int llmMigrated = 0;
        for (LlmProvider provider : llmRepo.findAll()) {
            String key = provider.getApiKeyEncrypted();
            if (key == null || key.isBlank()) {
                continue;
            }
            String migrated = migrate(key, "llmProvider id=" + provider.getId());
            if (migrated != null) {
                provider.setApiKeyEncrypted(migrated);
                llmRepo.save(provider);
                llmMigrated++;
            }
        }

        int garminMigrated = 0;
        for (ProviderConnection conn : providerRepo.findAll()) {
            Map<String, Object> auth = conn.getAuthPayload();
            if (auth == null || auth.get("password") == null) {
                continue;
            }
            String password = auth.get("password").toString();
            String migrated = migrate(password, "providerConnection id=" + conn.getId());
            if (migrated != null) {
                Map<String, Object> updated = new HashMap<>(auth);
                updated.put("password", migrated);
                conn.setAuthPayload(updated);
                providerRepo.save(conn);
                garminMigrated++;
            }
        }

        if (llmMigrated > 0 || garminMigrated > 0) {
            log.info("Migrated secrets on startup: llmProviders={}, garminConnections={}",
                    llmMigrated, garminMigrated);
        }
    }

    /**
     * Returns the value encrypted with the current key, or null when no change is needed / possible.
     */
    private String migrate(String value, String label) {
        if (!secretCrypto.isEncrypted(value)) {
            return secretCrypto.encrypt(value);
        }
        if (!secretCrypto.needsReencryption(value)) {
            return null;
        }
        try {
            return secretCrypto.reencrypt(value);
        } catch (Exception e) {
            log.error("Cannot decrypt secret for {} with current or legacy key. "
                    + "Set PIOS_ENCRYPTION_KEY_LEGACY to the previous key.", label);
            return null;
        }
    }
}
