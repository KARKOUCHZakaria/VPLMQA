package com.vplmqa.e2e.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Stores E2E credentials in Vault under project-scoped aliases.
 *
 * <p>Only aliases are listed or returned to clients. The secret value stays in
 * Vault and is resolved server-side during execution.</p>
 */
@Service
public class VaultSecretService {
    private final RestClient vaultClient;
    private final Path tokenFile;

    public VaultSecretService(
            @Value("${vault.url:http://vault:8200}") String vaultUrl,
            @Value("${vault.token-file:/run/secrets/vault_token}") String tokenFile) {
        this.vaultClient = RestClient.builder().baseUrl(vaultUrl).build();
        this.tokenFile = Path.of(tokenFile);
    }

    public String putProjectSecret(UUID projectId, String alias, String value) {
        // Normalize aliases before using them in a Vault path.
        String normalizedAlias = normalizeAlias(alias);
        vaultClient.post()
                .uri("/v1/secret/data/vplmqa/projects/{projectId}/e2e/{alias}", projectId, normalizedAlias)
                .header("X-Vault-Token", readToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("data", Map.of("value", value)))
                .retrieve()
                .toBodilessEntity();
        return "vault://vplmqa/projects/" + projectId + "/e2e/" + normalizedAlias;
    }

    @SuppressWarnings("unchecked")
    public List<String> listProjectSecretAliases(UUID projectId) {
        try {
            Map<String, Object> response = vaultClient.get()
                    .uri("/v1/secret/metadata/vplmqa/projects/{projectId}/e2e/?list=true", projectId)
                    .header("X-Vault-Token", readToken())
                    .retrieve()
                    .body(Map.class);
            Object data = response == null ? null : response.get("data");
            Object keys = data instanceof Map<?, ?> map ? map.get("keys") : null;
            if (!(keys instanceof List<?> rawKeys)) return List.of();
            List<String> aliases = new ArrayList<>();
            for (Object key : rawKeys) {
                String alias = String.valueOf(key).replaceAll("/$", "");
                if (!alias.isBlank()) aliases.add(alias);
            }
            return aliases;
        } catch (HttpClientErrorException.NotFound ignored) {
            return List.of();
        }
    }

    private String normalizeAlias(String alias) {
        String normalized = alias == null ? "" : alias.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        if (normalized.isBlank()) throw new IllegalArgumentException("Secret alias is required");
        return normalized;
    }

    private String readToken() {
        try {
            String token = Files.readString(tokenFile).trim();
            if (token.isBlank()) throw new IllegalStateException("Vault token file is empty");
            return token;
        } catch (IOException exception) {
            throw new IllegalStateException("Vault token must be mounted at " + tokenFile, exception);
        }
    }
}
