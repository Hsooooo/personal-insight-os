package com.pios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 프로세스를 띄워 타임아웃/스트림 처리를 검증한다 (가짜 스크립트는 sh로 실행).
 */
class GarminPythonClientTest {

    @TempDir
    Path tmp;

    private GarminPythonClient client;
    private final LocalDate day = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void setUp() {
        client = new GarminPythonClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "executable", "sh");
        ReflectionTestUtils.setField(client, "tokenDir", tmp.resolve("tokens").toString());
    }

    private void script(String body) throws Exception {
        Path script = tmp.resolve("fake.sh");
        Files.writeString(script, body);
        ReflectionTestUtils.setField(client, "scriptPath", script.toString());
    }

    private void timeout(long baseSeconds) {
        ReflectionTestUtils.setField(client, "timeoutBaseSeconds", baseSeconds);
        ReflectionTestUtils.setField(client, "timeoutPerDaySeconds", 0L);
    }

    @Test
    void hangingScriptIsKilledAfterTimeout() throws Exception {
        script("sleep 30\necho '{}'\n");
        timeout(2);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.fetch(1L, "a@b.c", "pw", day, day, GarminPythonClient.DataType.ALL))
                .isInstanceOf(GarminFetchException.class)
                .satisfies(e -> assertThat(((GarminFetchException) e).getKind())
                        .isEqualTo(GarminFetchException.Kind.TIMEOUT));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    void largeStderrDoesNotDeadlock() throws Exception {
        // 파이프 버퍼(수십 KB)를 훨씬 넘는 stderr를 먼저 쏟아낸 뒤 stdout으로 결과를 낸다
        script("""
                i=0
                while [ $i -lt 4000 ]; do echo "warning line $i xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" >&2; i=$((i+1)); done
                echo '{"activities":[],"health":[{"metric_date":"2026-01-01","resting_heart_rate":null}],"errors":[{"endpoint":"stats","date":"2026-01-01","kind":"server","message":"500"}],"fatal_error":null}'
                """);
        timeout(30);

        GarminPythonClient.SyncResult result =
                client.fetch(1L, "a@b.c", "pw", day, day, GarminPythonClient.DataType.ALL);

        assertThat(result.data().get("health")).hasSize(1);
        assertThat(result.data().get("health").get(0).get("resting_heart_rate").isNull()).isTrue();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).endpoint()).isEqualTo("stats");
        assertThat(result.fatalError()).isNull();
    }

    @Test
    void largeStdoutIsReadCompletely() throws Exception {
        script("""
                printf '{"activities":['
                i=0
                while [ $i -lt 3000 ]; do printf '{"garmin_activity_id":"%d","activity_name":"run padding padding padding padding"},' $i; i=$((i+1)); done
                printf '{"garmin_activity_id":"last"}],"errors":[]}'
                """);
        timeout(30);

        GarminPythonClient.SyncResult result =
                client.fetch(1L, "a@b.c", "pw", day, day, GarminPythonClient.DataType.ALL);
        assertThat(result.data().get("activities")).hasSize(3001);
    }

    @Test
    void loginFailureIsReportedAsAuth() throws Exception {
        script("echo 'Traceback...' >&2\necho '{\"error\": \"login failed: 401\", \"kind\": \"auth\"}' >&2\nexit 1\n");
        timeout(10);

        assertThatThrownBy(() -> client.fetch(1L, "a@b.c", "pw", day, day, GarminPythonClient.DataType.ALL))
                .isInstanceOf(GarminFetchException.class)
                .hasMessageContaining("login failed")
                .satisfies(e -> assertThat(((GarminFetchException) e).getKind().isFatal()).isTrue());
    }

    @Test
    void fatalErrorInOutputIsParsed() throws Exception {
        script("echo '{\"activities\":[],\"errors\":[],\"fatal_error\":{\"kind\":\"rate_limit\",\"message\":\"429\",\"endpoint\":\"stats\"}}'\n");
        timeout(10);

        GarminPythonClient.SyncResult result =
                client.fetch(1L, "a@b.c", "pw", day, day, GarminPythonClient.DataType.ALL);
        assertThat(result.fatalError()).isNotNull();
        assertThat(result.fatalError().isAuthOrRateLimit()).isTrue();
    }

    @Test
    void timeoutScalesWithChunkDays() {
        ReflectionTestUtils.setField(client, "timeoutBaseSeconds", 100L);
        ReflectionTestUtils.setField(client, "timeoutPerDaySeconds", 10L);
        ReflectionTestUtils.setField(client, "timeoutMaxSeconds", 150L);
        assertThat(client.timeoutFor(day, day)).isEqualTo(Duration.ofSeconds(110));
        assertThat(client.timeoutFor(day, day.plusDays(2))).isEqualTo(Duration.ofSeconds(130));
        assertThat(client.timeoutFor(day, day.plusDays(29))).isEqualTo(Duration.ofSeconds(150));
    }
}
