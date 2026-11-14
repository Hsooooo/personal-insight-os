package com.pios.oauth;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class OAuthScopes {

    public static final String READ = "mcp:read";
    public static final String WRITE = "mcp:write";
    public static final List<String> SUPPORTED = List.of(READ, WRITE);
    public static final String DEFAULT = READ + " " + WRITE;

    private OAuthScopes() {
    }

    /** Parses a space-delimited scope string. Blank → default. Unknown scope → invalid_scope. */
    public static String normalize(String scope) {
        if (scope == null || scope.isBlank()) {
            return DEFAULT;
        }
        Set<String> requested = parse(scope);
        for (String s : requested) {
            if (!SUPPORTED.contains(s)) {
                throw new OAuthException("invalid_scope", "Unsupported scope: " + s);
            }
        }
        // canonical order
        return SUPPORTED.stream().filter(requested::contains).collect(Collectors.joining(" "));
    }

    public static Set<String> parse(String scope) {
        if (scope == null || scope.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(scope.trim().split("\\s+"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static List<String> toList(String scope) {
        return List.copyOf(parse(scope));
    }

    public static boolean isSubset(String requested, String granted) {
        return parse(granted).containsAll(parse(requested));
    }
}
