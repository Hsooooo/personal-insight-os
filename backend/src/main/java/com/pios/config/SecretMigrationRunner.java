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
 * One-time lazy migration: encrypt plaintext LLM API keys and Garmin passwords on startup.
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
            if (key != null && !key.isBlank() && !secretCrypto.isEncrypted(key)) {
                provider.setApiKeyEncrypted(secretCrypto.encrypt(key));
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
            if (!secretCrypto.isEncrypted(password)) {
                Map<String, Object> updated = new HashMap<>(auth);
                updated.put("password", secretCrypto.encrypt(password));
                conn.setAuthPayload(updated);
                providerRepo.save(conn);
                garminMigrated++;
            }
        }

        if (llmMigrated > 0 || garminMigrated > 0) {
            log.info("Encrypted plaintext secrets on startup: llmProviders={}, garminConnections={}",
                    llmMigrated, garminMigrated);
        }
    }
}
