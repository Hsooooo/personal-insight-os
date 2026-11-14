package com.pios.oauth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectUrisTest {

    @Test
    void acceptsHttpsAndLoopback() {
        assertThat(RedirectUris.isValid("https://claude.ai/api/mcp/auth_callback")).isTrue();
        assertThat(RedirectUris.isValid("http://localhost:6274/oauth/callback")).isTrue();
        assertThat(RedirectUris.isValid("http://127.0.0.1:33418/")).isTrue();
    }

    @Test
    void rejectsUnsafeUris() {
        assertThat(RedirectUris.isValid("http://evil.example/cb")).isFalse();
        assertThat(RedirectUris.isValid("javascript:alert(1)")).isFalse();
        assertThat(RedirectUris.isValid("https://good.example/cb#frag")).isFalse();
        assertThat(RedirectUris.isValid("https://user:pw@good.example/cb")).isFalse();
        assertThat(RedirectUris.isValid("/relative")).isFalse();
        assertThat(RedirectUris.isValid("https://good.example/cb\nX")).isFalse();
        assertThat(RedirectUris.isValid("http://localhost.evil.com/cb")).isFalse();
        assertThat(RedirectUris.isValid(null)).isFalse();
    }

    @Test
    void exactMatchRequiredForHttps() {
        List<String> registered = List.of("https://app.example/cb");
        assertThat(RedirectUris.matchesAny("https://app.example/cb", registered)).isTrue();
        assertThat(RedirectUris.matchesAny("https://app.example/cb/", registered)).isFalse();
        assertThat(RedirectUris.matchesAny("https://app.example/cb?x=1", registered)).isFalse();
        assertThat(RedirectUris.matchesAny("https://APP.example/cb", registered)).isFalse();
        assertThat(RedirectUris.matchesAny("https://app.example.evil/cb", registered)).isFalse();
    }

    @Test
    void loopbackPortMayVary() {
        List<String> registered = List.of("http://127.0.0.1:1234/callback");
        assertThat(RedirectUris.matchesAny("http://127.0.0.1:5555/callback", registered)).isTrue();
        assertThat(RedirectUris.matchesAny("http://127.0.0.1:5555/other", registered)).isFalse();
        assertThat(RedirectUris.matchesAny("http://localhost:5555/callback", registered)).isFalse();
    }

    @Test
    void acceptsPrivateUseSchemesForNativeApps() {
        assertThat(RedirectUris.isValid("cursor://anysphere.cursor-retrieval/oauth/user-pios-coach/callback")).isTrue();
        assertThat(RedirectUris.isValid("vscode://vscode.github-authentication/did-authenticate")).isTrue();
        assertThat(RedirectUris.isValid("com.example.app:/oauth/callback")).isTrue();
        assertThat(RedirectUris.isValid("my-app+x.y://cb")).isTrue();
    }

    @Test
    void rejectsDangerousOrMalformedSchemes() {
        for (String bad : new String[]{
                "javascript://x/%0aalert(1)", "data://text/html,x", "file:///etc/passwd", "vbscript://x/y",
                "about://blank/x", "blob://x/y", "ftp://x/y", "ws://x/y", "wss://x/y",
                "Cursor://anysphere/cb",            // uppercase scheme
                "1app://x/cb",                       // must start with a letter
                "myapp:callback",                    // opaque
                "cursor://anysphere/cb#frag",
                "cursor://user@anysphere/cb",
                "HTTP://evil.example/cb"}) {
            assertThat(RedirectUris.isValid(bad)).as(bad).isFalse();
        }
    }

    @Test
    void privateUseSchemeRequiresExactMatch() {
        List<String> registered = List.of("cursor://anysphere.cursor-retrieval/oauth/user-pios-coach/callback");
        assertThat(RedirectUris.matchesAny("cursor://anysphere.cursor-retrieval/oauth/user-pios-coach/callback",
                registered)).isTrue();
        assertThat(RedirectUris.matchesAny("cursor://anysphere.cursor-retrieval/oauth/other/callback", registered))
                .isFalse();
        assertThat(RedirectUris.matchesAny("cursor://anysphere.cursor-retrieval:1/oauth/user-pios-coach/callback",
                registered)).isFalse();
    }

    @Test
    void displayTargetShowsSchemeAndAuthorityForAppSchemes() {
        assertThat(RedirectUris.displayTarget("cursor://anysphere.cursor-retrieval/oauth/cb"))
                .isEqualTo("cursor://anysphere.cursor-retrieval");
        assertThat(RedirectUris.displayTarget("com.example.app:/cb")).isEqualTo("com.example.app:");
        assertThat(RedirectUris.displayTarget("https://claude.ai/api/mcp/auth_callback")).isEqualTo("claude.ai");
        assertThat(RedirectUris.displayTarget("http://127.0.0.1:3000/cb")).isEqualTo("127.0.0.1");
    }
}
