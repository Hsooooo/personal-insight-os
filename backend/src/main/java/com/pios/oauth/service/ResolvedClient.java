package com.pios.oauth.service;

import java.util.List;

/** A client known to the AS: either DCR-registered or resolved from a Client ID Metadata Document. */
public record ResolvedClient(String clientId, String clientName, List<String> redirectUris, boolean metadataDocument) {
}
