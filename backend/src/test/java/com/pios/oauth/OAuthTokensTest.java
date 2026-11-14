package com.pios.oauth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTokensTest {

    private static String challengeOf(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    @Test
    void pkceS256VerifiesMatchingVerifier() throws Exception {
        String verifier = OAuthTokens.randomToken(48);
        String challenge = challengeOf(verifier);

        assertThat(OAuthTokens.isValidCodeChallenge(challenge)).isTrue();
        assertThat(OAuthTokens.verifyPkce(verifier, challenge)).isTrue();
    }

    @Test
    void pkceRejectsWrongVerifier() throws Exception {
        String challenge = challengeOf(OAuthTokens.randomToken(48));
        assertThat(OAuthTokens.verifyPkce(OAuthTokens.randomToken(48), challenge)).isFalse();
    }

    @Test
    void pkceRejectsMalformedVerifier() throws Exception {
        String shortVerifier = "abc";
        assertThat(OAuthTokens.verifyPkce(shortVerifier, challengeOf(shortVerifier))).isFalse();
        String plainChallengeAttack = "a".repeat(43);
        assertThat(OAuthTokens.verifyPkce(plainChallengeAttack, plainChallengeAttack)).isFalse();
        assertThat(OAuthTokens.verifyPkce(null, "x")).isFalse();
    }

    @Test
    void hashIsDeterministicHex() {
        assertThat(OAuthTokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
