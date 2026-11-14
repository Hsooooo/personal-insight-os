package com.pios.oauth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Fetches OAuth Client ID Metadata Documents with SSRF protections:
 * https only on port 443, no redirects, 5s timeout, 64KB cap, and the destination IP is resolved once,
 * checked to be public, and then connected to directly (no second DNS lookup → no DNS rebinding).
 * TLS certificate/hostname verification and SNI use the original hostname.
 */
@Component
public class ClientMetadataDocumentFetcher {

    public static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_URL_LENGTH = 2048;

    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final ObjectMapper objectMapper;
    private final DnsResolver dnsResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public ClientMetadataDocumentFetcher(ObjectMapper objectMapper) {
        this(objectMapper, InetAddress::getAllByName);
    }

    public ClientMetadataDocumentFetcher(ObjectMapper objectMapper, DnsResolver dnsResolver) {
        this.objectMapper = objectMapper;
        this.dnsResolver = dnsResolver;
    }

    public static boolean isUrlClientId(String clientId) {
        return clientId != null && clientId.regionMatches(true, 0, "https://", 0, 8);
    }

    /** Static URL checks per the CIMD draft + our SSRF rules. */
    public static URI validateClientIdUrl(String clientId) {
        if (clientId == null || clientId.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("client_id URL too long");
        }
        URI uri;
        try {
            uri = new URI(clientId);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("client_id is not a valid URL");
        }
        if (!"https".equals(uri.getScheme())) {
            throw new IllegalArgumentException("client_id URL must use https");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("client_id URL must not contain credentials");
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("client_id URL must not contain a fragment");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("client_id URL must have a host");
        }
        if (isIpLiteral(host)) {
            throw new IllegalArgumentException("client_id URL must use a DNS hostname");
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".local")
                || lower.endsWith(".internal") || !lower.contains(".")) {
            throw new IllegalArgumentException("client_id URL host is not allowed");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("client_id URL must use the default https port");
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty() || path.equals("/")) {
            throw new IllegalArgumentException("client_id URL must contain a path");
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("client_id URL must not contain dot segments");
            }
        }
        return uri;
    }

    static boolean isIpLiteral(String host) {
        return host.startsWith("[") || host.matches("[0-9.]+") || host.contains(":");
    }

    /** Rejects loopback, private, link-local, CGNAT, multicast, unspecified, ULA and similar ranges. */
    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            if (b0 == 0 || b0 == 10 || b0 == 127) return false;
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;       // 100.64/10 CGNAT
            if (b0 == 169 && b1 == 254) return false;                    // link-local / metadata
            if (b0 == 172 && b1 >= 16 && b1 <= 31) return false;
            if (b0 == 192 && b1 == 168) return false;
            if (b0 == 192 && b1 == 0 && (b[2] & 0xff) == 0) return false; // 192.0.0/24
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false;       // benchmarking
            if (b0 >= 224) return false;                                 // multicast + reserved
            return true;
        }
        if (b.length == 16) {
            if ((b[0] & 0xfe) == 0xfc) return false;                     // fc00::/7 ULA
            if ((b[0] & 0xff) == 0xfe && (b[1] & 0xc0) == 0x80) return false; // fe80::/10
            boolean mapped = true;                                       // ::ffff:a.b.c.d
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) { mapped = false; break; }
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPublicAddress(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            boolean compat = true;                                       // ::/96 IPv4-compatible (deprecated)
            for (int i = 0; i < 12; i++) {
                if (b[i] != 0) { compat = false; break; }
            }
            if (compat) return false;
            if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x02) return false; // 2002::/16 6to4
            if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01 && b[2] == 0 && b[3] == 0) {
                return false;                                            // 2001:0::/32 Teredo
            }
            if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) {
                return false;                                            // 2001:db8::/32 documentation
            }
            if ((b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b) {
                return false;                                            // 64:ff9b::/96 NAT64 (could reach private v4)
            }
            return true;
        }
        return false;
    }

    /** Resolves the host and returns the first address, rejecting if ANY resolved address is non-public. */
    InetAddress resolvePublic(String host) throws IOException {
        InetAddress[] addresses = dnsResolver.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new IOException("client_id host did not resolve");
        }
        for (InetAddress a : addresses) {
            if (!isPublicAddress(a)) {
                throw new IOException("client_id host resolves to a non-public address");
            }
        }
        return addresses[0];
    }

    public JsonNode fetch(String clientId) throws IOException {
        URI uri;
        try {
            uri = validateClientIdUrl(clientId);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage());
        }
        String host = uri.getHost();
        InetAddress address = resolvePublic(host);
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        Socket raw = new Socket();
        try {
            raw.connect(new InetSocketAddress(address, 443), TIMEOUT_MS);
            raw.setSoTimeout(TIMEOUT_MS);
            SSLContext ctx;
            try {
                ctx = SSLContext.getDefault();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException("TLS unavailable", e);
            }
            try (SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(raw, host, 443, true)) {
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                params.setServerNames(List.of(new SNIHostName(host)));
                ssl.setSSLParameters(params);
                ssl.startHandshake();

                String target = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
                String request = "GET " + target + " HTTP/1.1\r\n"
                        + "Host: " + host + "\r\n"
                        + "Accept: application/json\r\n"
                        + "Accept-Encoding: identity\r\n"
                        + "User-Agent: PIOS-OAuth/1.0\r\n"
                        + "Connection: close\r\n\r\n";
                OutputStream out = ssl.getOutputStream();
                out.write(request.getBytes(StandardCharsets.US_ASCII));
                out.flush();

                byte[] body = readResponse(new java.io.BufferedInputStream(ssl.getInputStream()), ssl, deadline);
                return objectMapper.readTree(body);
            }
        } finally {
            if (!raw.isClosed()) {
                raw.close();
            }
        }
    }

    /** Minimal HTTP/1.1 response reader: status 200 only, Content-Length / chunked / EOF, size capped. */
    static byte[] readResponse(InputStream in, Socket socket, long deadline) throws IOException {
        BoundedReader reader = new BoundedReader(in, socket, deadline);
        String statusLine = reader.readLine(MAX_HEADER_BYTES);
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) {
            throw new IOException("Invalid HTTP response");
        }
        if (!"200".equals(parts[1])) {
            throw new IOException("client metadata fetch returned HTTP " + parts[1]);
        }
        long contentLength = -1;
        boolean chunked = false;
        String contentType = null;
        int headerBytes = statusLine.length();
        while (true) {
            String line = reader.readLine(MAX_HEADER_BYTES);
            headerBytes += line.length() + 2;
            if (headerBytes > MAX_HEADER_BYTES) {
                throw new IOException("Response headers too large");
            }
            if (line.isEmpty()) {
                break;
            }
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String name = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(idx + 1).trim();
            switch (name) {
                case "content-length" -> {
                    try {
                        contentLength = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid Content-Length");
                    }
                }
                case "transfer-encoding" -> chunked = value.toLowerCase(Locale.ROOT).contains("chunked");
                case "content-type" -> contentType = value.toLowerCase(Locale.ROOT);
                case "content-encoding" -> {
                    if (!value.equalsIgnoreCase("identity")) {
                        throw new IOException("Unsupported Content-Encoding");
                    }
                }
                default -> {
                }
            }
        }
        if (contentType == null || !contentType.contains("json")) {
            throw new IOException("client metadata must be served as JSON");
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (chunked) {
            while (true) {
                String sizeLine = reader.readLine(64);
                int semi = sizeLine.indexOf(';');
                String hex = (semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim();
                int size;
                try {
                    size = Integer.parseInt(hex, 16);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid chunk size");
                }
                if (size < 0 || body.size() + (long) size > MAX_BYTES) {
                    throw new IOException("client metadata document too large");
                }
                if (size == 0) {
                    break;
                }
                reader.readFully(body, size);
                reader.readLine(2); // CRLF after chunk
            }
        } else if (contentLength >= 0) {
            if (contentLength > MAX_BYTES) {
                throw new IOException("client metadata document too large");
            }
            reader.readFully(body, (int) contentLength);
        } else {
            reader.readToEof(body, MAX_BYTES);
        }
        return body.toByteArray();
    }

    private static final class BoundedReader {
        private final InputStream in;
        private final Socket socket;
        private final long deadline;

        BoundedReader(InputStream in, Socket socket, long deadline) {
            this.in = in;
            this.socket = socket;
            this.deadline = deadline;
        }

        private int read() throws IOException {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new SocketTimeoutException("client metadata fetch timed out");
            }
            if (socket != null) {
                socket.setSoTimeout((int) Math.max(1, remaining));
            }
            return in.read();
        }

        String readLine(int max) throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                int c = read();
                if (c == -1) {
                    throw new IOException("Unexpected end of response");
                }
                if (c == '\n') {
                    int len = sb.length();
                    if (len > 0 && sb.charAt(len - 1) == '\r') {
                        sb.setLength(len - 1);
                    }
                    return sb.toString();
                }
                sb.append((char) c);
                if (sb.length() > max) {
                    throw new IOException("Response line too long");
                }
            }
        }

        void readFully(ByteArrayOutputStream out, int n) throws IOException {
            for (int i = 0; i < n; i++) {
                int c = read();
                if (c == -1) {
                    throw new IOException("Unexpected end of response");
                }
                out.write(c);
            }
        }

        void readToEof(ByteArrayOutputStream out, int max) throws IOException {
            while (true) {
                int c = read();
                if (c == -1) {
                    return;
                }
                out.write(c);
                if (out.size() > max) {
                    throw new IOException("client metadata document too large");
                }
            }
        }
    }
}
