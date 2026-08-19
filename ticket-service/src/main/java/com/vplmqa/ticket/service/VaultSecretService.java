package com.vplmqa.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VaultSecretService {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String address;
    private final Path tokenFile;

    public VaultSecretService(ObjectMapper objectMapper,
                              @Value("${vault.address:http://vault:8200}") String address,
                              @Value("${vault.token-file:/run/secrets/vault_token}") String tokenFile) {
        this.objectMapper = objectMapper;
        this.address = address.replaceAll("/+$", "");
        this.tokenFile = Path.of(tokenFile);
    }

    public void put(String path, String field, String value) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("data", Map.of(field, value)));
            HttpRequest request = request(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            ensureSuccess(client.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store the Azure DevOps credential in Vault.", exception);
        }
    }

    public String get(String path, String field) {
        try {
            HttpResponse<String> response = client.send(
                    request(path).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return "";
            ensureSuccess(response);
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("data").path("data").path(field).asText("");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read the Azure DevOps credential from Vault.", exception);
        }
    }

    public boolean exists(String path, String field) {
        return !get(path, field).isBlank();
    }

    private HttpRequest.Builder request(String path) throws Exception {
        String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        return HttpRequest.newBuilder(URI.create(address + "/v1/secret/data/" + path))
                .header("X-Vault-Token", token);
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Vault returned HTTP " + response.statusCode());
        }
    }
}
