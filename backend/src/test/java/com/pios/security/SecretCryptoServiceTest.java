package com.pios.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCryptoServiceTest {

    private static final String KEY_A = "a".repeat(40);
    private static final String KEY_B = "b".repeat(40);

    private SecretCryptoService create(String key, String legacyKey) {
        SecretCryptoService service = new SecretCryptoService();
        ReflectionTestUtils.setField(service, "encryptionKeyMaterial", key);
        ReflectionTestUtils.setField(service, "legacyKeyMaterial", legacyKey);
        service.init();
        return service;
    }

    @Test
    void encryptThenDecryptRoundTrips() {
        SecretCryptoService crypto = create(KEY_A, "");

        String encrypted = crypto.encrypt("garmin-password");

        assertThat(encrypted).startsWith("ENC:").doesNotContain("garmin-password");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("garmin-password");
        assertThat(crypto.needsReencryption(encrypted)).isFalse();
    }

    @Test
    void plaintextIsReturnedAsIsAndNotReencrypted() {
        SecretCryptoService crypto = create(KEY_A, "");

        assertThat(crypto.decrypt("legacy-plain")).isEqualTo("legacy-plain");
        assertThat(crypto.needsReencryption("legacy-plain")).isFalse();
    }

    @Test
    void legacyKeyCiphertextIsDecryptedAndReencryptedWithCurrentKey() {
        String oldCiphertext = create(KEY_A, "").encrypt("sk-secret");
        SecretCryptoService rotated = create(KEY_B, KEY_A);

        assertThat(rotated.needsReencryption(oldCiphertext)).isTrue();
        assertThat(rotated.decrypt(oldCiphertext)).isEqualTo("sk-secret");

        String reencrypted = rotated.reencrypt(oldCiphertext);
        assertThat(rotated.needsReencryption(reencrypted)).isFalse();
        // 새 키만으로도 복호화 가능해야 한다 (legacy 키 제거 후)
        assertThat(create(KEY_B, "").decrypt(reencrypted)).isEqualTo("sk-secret");
    }

    @Test
    void decryptFailsWithoutMatchingKey() {
        String ciphertext = create(KEY_A, "").encrypt("sk-secret");

        assertThatThrownBy(() -> create(KEY_B, "").decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortKeyIsRejected() {
        assertThatThrownBy(() -> create("too-short", ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
