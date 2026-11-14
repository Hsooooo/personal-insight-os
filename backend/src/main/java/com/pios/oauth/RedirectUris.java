package com.pios.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * redirect_uri rules: absolute, no fragment, no userinfo, and one of
 * <ul>
 *   <li>https (any host)</li>
 *   <li>http on loopback (localhost / 127.0.0.1 / [::1], any port)</li>
 *   <li>RFC 8252 §7.1 private-use scheme for native apps (e.g. {@code cursor://...}, {@code vscode://...}):
 *       scheme matches {@code ^[a-z][a-z0-9+.-]*$} and is not a dangerous/network scheme</li>
 * </ul>
 * Matching is exact, except http loopback URIs where the port may vary (RFC 8252 §7.3).
 */
public final class RedirectUris {

    public static final int MAX_LENGTH = 2048;

    private static final Pattern PRIVATE_SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*$");
    private static final Set<String> FORBIDDEN_SCHEMES = Set.of(
            "http", "javascript", "data", "file", "vbscript", "about", "blob", "ftp", "ws", "wss");

    private RedirectUris() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c == 0x7f) {
                return false;
            }
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!uri.isAbsolute() || uri.isOpaque() || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            return false;
        }
        // case-sensitive on purpose: private-use schemes must be lowercase; https/http compared as written
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if ("https".equals(scheme)) {
            return host != null && !host.isEmpty();
        }
        if ("http".equals(scheme)) {
            return host != null && isLoopbackHost(host);
        }
        if (!isPrivateUseScheme(scheme)) {
            return false;
        }
        String authority = uri.getRawAuthority();
        if (authority != null) {
            return !authority.isEmpty() && !authority.contains("@");
        }
        // e.g. com.example.app:/oauth/callback
        String path = uri.getRawPath();
        return path != null && path.startsWith("/");
    }

    /** RFC 8252 §7.1 private-use scheme (not https / http / dangerous schemes). */
    public static boolean isPrivateUseScheme(String scheme) {
        return scheme != null && !"https".equals(scheme)
                && PRIVATE_SCHEME.matcher(scheme).matches()
                && !FORBIDDEN_SCHEMES.contains(scheme);
    }

    /**
     * What to show the user on the consent screen: the host for https/http,
     * {@code scheme://authority} (or {@code scheme:}) for private-use schemes.
     */
    public static String displayTarget(String redirectUri) {
        try {
            URI uri = new URI(redirectUri);
            String scheme = uri.getScheme();
            if ("https".equals(scheme) || "http".equals(scheme)) {
                return uri.getHost();
            }
            return uri.getRawAuthority() != null ? scheme + "://" + uri.getRawAuthority() : scheme + ":";
        } catch (URISyntaxException e) {
            return redirectUri;
        }
    }

    static boolean isLoopbackHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]");
    }

    /** True if {@code requested} matches one of the registered URIs. */
    public static boolean matchesAny(String requested, List<String> registered) {
        if (requested == null || registered == null) {
            return false;
        }
        for (String r : registered) {
            if (matches(requested, r)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String requested, String registered) {
        if (requested == null || registered == null) {
            return false;
        }
        if (requested.equals(registered)) {
            return true;
        }
        try {
            URI req = new URI(requested);
            URI reg = new URI(registered);
            if (!"http".equalsIgnoreCase(req.getScheme()) || !"http".equalsIgnoreCase(reg.getScheme())) {
                return false;
            }
            if (req.getHost() == null || reg.getHost() == null
                    || !isLoopbackHost(req.getHost()) || !req.getHost().equalsIgnoreCase(reg.getHost())) {
                return false;
            }
            return Objects.equals(req.getRawPath(), reg.getRawPath())
                    && Objects.equals(req.getRawQuery(), reg.getRawQuery())
                    && req.getRawUserInfo() == null
                    && req.getRawFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
