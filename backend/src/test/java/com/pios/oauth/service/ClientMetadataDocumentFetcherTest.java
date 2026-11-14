package com.pios.oauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientMetadataDocumentFetcherTest {

    private static InetAddress ip(String literal) throws java.net.UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @Test
    void validClientIdUrl() {
        assertThat(ClientMetadataDocumentFetcher.validateClientIdUrl("https://app.example.com/oauth/client.json").getHost())
                .isEqualTo("app.example.com");
    }

    @Test
    void rejectsInvalidClientIdUrls() {
        for (String bad : new String[]{
                "http://app.example.com/client.json",       // not https
                "https://app.example.com",                  // no path
                "https://app.example.com/",                 // root path
                "https://app.example.com/a/../b",           // dot segment
                "https://user:pw@app.example.com/c.json",   // credentials
                "https://app.example.com/c.json#x",         // fragment
                "https://127.0.0.1/c.json",                 // IP literal
                "https://[::1]/c.json",
                "https://169.254.169.254/latest/meta-data", // metadata IP literal
                "https://localhost/c.json",
                "https://intranet/c.json",                  // single label
                "https://app.example.com:8443/c.json"       // non-default port
        }) {
            assertThatThrownBy(() -> ClientMetadataDocumentFetcher.validateClientIdUrl(bad))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void privateAndSpecialAddressesAreNotPublic() throws Exception {
        for (String addr : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1",
                "169.254.169.254", "100.64.0.1", "0.0.0.0", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd12::1",
                "::ffff:10.0.0.1", "::ffff:127.0.0.1", "64:ff9b::a00:1",
                "::10.0.0.1", "::a9fe:a9fe", "2002:a00:1::1", "2002:c0a8:101::1", "2001:0:4136:e378:8000:63bf:3fff:fdd2",
                "2001::1"}) {
            assertThat(ClientMetadataDocumentFetcher.isPublicAddress(ip(addr))).as(addr).isFalse();
        }
        assertThat(ClientMetadataDocumentFetcher.isPublicAddress(ip("93.184.216.34"))).isTrue();
        assertThat(ClientMetadataDocumentFetcher.isPublicAddress(ip("2606:4700::1111"))).isTrue();
    }

    @Test
    void hostResolvingToPrivateIpIsRejected() throws Exception {
        ClientMetadataDocumentFetcher fetcher = new ClientMetadataDocumentFetcher(new ObjectMapper(),
                host -> new InetAddress[]{ip("93.184.216.34"), ip("10.0.0.5")});
        assertThatThrownBy(() -> fetcher.fetch("https://rebind.example.com/client.json"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void metadataIpViaDnsIsRejected() throws Exception {
        ClientMetadataDocumentFetcher fetcher = new ClientMetadataDocumentFetcher(new ObjectMapper(),
                host -> new InetAddress[]{ip("169.254.169.254")});
        assertThatThrownBy(() -> fetcher.fetch("https://metadata.example.com/client.json"))
                .isInstanceOf(IOException.class);
    }

    private static byte[] read(String raw) throws IOException {
        return ClientMetadataDocumentFetcher.readResponse(
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)), null, System.currentTimeMillis() + 5000);
    }

    @Test
    void parsesContentLengthAndChunkedResponses() throws Exception {
        assertThat(new String(read("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}"),
                StandardCharsets.UTF_8)).isEqualTo("{}");
        assertThat(new String(read("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "1\r\n{\r\n1\r\n}\r\n0\r\n\r\n"), StandardCharsets.UTF_8)).isEqualTo("{}");
    }

    @Test
    void rejectsRedirectsOversizeAndNonJson() {
        assertThatThrownBy(() -> read("HTTP/1.1 302 Found\r\nLocation: http://10.0.0.1/\r\n\r\n"))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> read("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 70000\r\n\r\n"))
                .isInstanceOf(IOException.class).hasMessageContaining("too large");
        assertThatThrownBy(() -> read("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 2\r\n\r\n{}"))
                .isInstanceOf(IOException.class);
    }
}
