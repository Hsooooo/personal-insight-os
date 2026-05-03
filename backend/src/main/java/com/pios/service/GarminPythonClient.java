package com.pios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GarminPythonClient {

    private final ObjectMapper objectMapper;

    @Value("${garmin.python.script-path:scripts/garmin_sync.py}")
    private String scriptPath;

    @Value("${garmin.python.timeout-seconds:120}")
    private long timeoutSeconds;

    public SyncResult fetch(String email, String password, LocalDate fromDate, LocalDate toDate, DataType dataType) {
        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(scriptPath);
        command.add(email);
        command.add(password);
        command.add(fromDate.toString());
        command.add(toDate.toString());
        command.add(dataType.name().toLowerCase());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        try {
            log.info("Starting Garmin sync: {} from {} to {}", dataType, fromDate, toDate);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = outReader.readLine()) != null) {
                    stdout.append(line);
                }
                while ((line = errReader.readLine()) != null) {
                    stderr.append(line);
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Garmin sync timed out after " + timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String err = stderr.toString().trim();
                if (err.isEmpty()) err = stdout.toString().trim();
                throw new RuntimeException("Garmin sync failed: " + err);
            }

            JsonNode root = objectMapper.readTree(stdout.toString());
            if (root.has("error")) {
                throw new RuntimeException("Garmin sync error: " + root.get("error").asText());
            }

            return new SyncResult(root);
        } catch (Exception e) {
            log.error("Garmin sync error", e);
            throw new RuntimeException("Garmin sync failed: " + e.getMessage(), e);
        }
    }

    public enum DataType {
        ACTIVITIES, HEALTH, SLEEP, ALL
    }

    public record SyncResult(JsonNode data) {
    }
}
