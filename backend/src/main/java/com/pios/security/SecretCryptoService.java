package com.pios.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for secrets stored in DB (LLM API keys, Garmin passwords).
 * Ciphertext is prefixed with {@code ENC:} so legacy plaintext rows can be detected and migrated lazily.
 */
@Service
public class SecretCryptoService {

    private static final String PREFIX = "ENC:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static final int MIN_KEY_LENGTH = 32;

    @Value("${pios.encryption-key}")
    private String encryptionKeyMaterial;

    @Value("${pios.encryption-key-legacy:}")
    private String legacyKeyMaterial;

    private SecretKey secretKey;
    private SecretKey legacyKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void init() {
        if (encryptionKeyMaterial == null || encryptionKeyMaterial.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException("pios.encryption-key must be at least " + MIN_KEY_LENGTH + " characters");
        }
        this.secretKey = deriveKey(encryptionKeyMaterial);
        if (legacyKeyMaterial != null && !legacyKeyMaterial.isBlank()
                && !legacyKeyMaterial.equals(encryptionKeyMaterial)) {
            this.legacyKey = deriveKey(legacyKeyMaterial);
        }
    }

    private static SecretKey deriveKey(String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize SecretCryptoService", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * True when the value is encrypted but cannot be decrypted with the current key
     * (i.e. it was encrypted with the legacy key and needs re-encryption).
     */
    public boolean needsReencryption(String value) {
        if (!isEncrypted(value)) {
            return false;
        }
        try {
            decryptWith(value, secretKey);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Decrypts with the current key, falling back to the legacy key, and re-encrypts with the current key.
     */
    public String reencrypt(String value) {
        return encrypt(decrypt(value));
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    /**
     * Decrypts ENC: values. Returns plaintext as-is for legacy (unencrypted) rows.
     */
    public String decrypt(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            return decryptWith(value, secretKey);
        } catch (Exception e) {
            if (legacyKey != null) {
                try {
                    return decryptWith(value, legacyKey);
                } catch (Exception ignored) {
                    // fall through
                }
            }
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    private String decryptWith(String value, SecretKey key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(value.substring(PREFIX.length()));
        ByteBuffer buffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
