package com.vplmqa.ticket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vplmqa.ticket.dto.AzureDevOpsConnectionRequest;
import com.vplmqa.ticket.dto.AzureDevOpsConnectionResponse;
import com.vplmqa.ticket.dto.AzureDevOpsMember;
import com.vplmqa.ticket.entity.AzureDevOpsConnection;
import com.vplmqa.ticket.entity.Ticket;
import com.vplmqa.ticket.repository.AzureDevOpsConnectionRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains Azure DevOps connections and publishes tickets with their captured
 * evidence. Personal access tokens are read from Vault, never stored in the
 * ticket-service database.
 */
@Service
public class AzureDevOpsService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final AzureDevOpsConnectionRepository repository;
    private final VaultSecretService vault;

    public AzureDevOpsService(ObjectMapper objectMapper,
                              AzureDevOpsConnectionRepository repository,
                              VaultSecretService vault) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.vault = vault;
    }

    @Transactional
    public AzureDevOpsConnectionResponse save(UUID projectId, AzureDevOpsConnectionRequest request) {
        require(request.organization(), "Azure DevOps organization is required.");
        require(request.azureProject(), "Azure DevOps project is required.");
        AzureDevOpsConnection connection = repository.findById(projectId).orElseGet(AzureDevOpsConnection::new);
        Instant now = Instant.now();
        if (connection.getProjectId() == null) {
            connection.setProjectId(projectId);
            connection.setCreatedAt(now);
        }
        connection.setOrganization(normalizeOrganization(request.organization()));
        connection.setAzureProject(normalizeProject(request.azureProject(), connection.getOrganization()));
        connection.setWorkItemType(blankToDefault(request.workItemType(), "Bug"));
        connection.setAreaPath(blankToNull(request.areaPath()));
        connection.setEnabled(request.enabled() == null || request.enabled());
        connection.setUpdatedAt(now);
        repository.save(connection);
        if (request.personalAccessToken() != null && !request.personalAccessToken().isBlank()) {
            String pat = request.personalAccessToken().trim();
            vault.put(secretPath(projectId), "pat", pat);
            // The shared credential lets every enabled project use the same Azure DevOps account.
            vault.put(sharedSecretPath(), "pat", pat);
        }
        return response(connection);
    }

    @Transactional(readOnly = true)
    public AzureDevOpsConnectionResponse get(UUID projectId) {
        return repository.findById(projectId)
                .map(this::response)
                .orElse(new AzureDevOpsConnectionResponse(projectId, "", "", "Bug", "", false, false));
    }

    @Transactional(readOnly = true)
    public List<AzureDevOpsMember> members(UUID projectId) {
        AzureDevOpsConnection connection = configured(projectId);
        String pat = credential(projectId);
        JsonNode teams = get(connection, pat, String.format(
                "https://dev.azure.com/%s/_apis/projects/%s/teams?$top=500&api-version=7.1",
                encode(normalizeOrganization(connection.getOrganization())),
                encode(normalizeProject(connection.getAzureProject(), connection.getOrganization()))));
        Map<String, AzureDevOpsMember> unique = new LinkedHashMap<>();
        for (JsonNode team : teams.path("value")) {
            String teamId = team.path("id").asText("");
            if (teamId.isBlank()) continue;
            JsonNode members = get(connection, pat, String.format(
                    "https://dev.azure.com/%s/_apis/projects/%s/teams/%s/members?$top=500&api-version=7.1",
                    encode(normalizeOrganization(connection.getOrganization())),
                    encode(normalizeProject(connection.getAzureProject(), connection.getOrganization())),
                    encode(teamId)));
            for (JsonNode item : members.path("value")) {
                JsonNode identity = item.has("identity") ? item.path("identity") : item;
                String id = identity.path("id").asText(identity.path("descriptor").asText(""));
                String displayName = identity.path("displayName").asText("");
                String uniqueName = identity.path("uniqueName").asText(identity.path("mailAddress").asText(""));
                String email = identity.path("mailAddress").asText(uniqueName.contains("@") ? uniqueName : "");
                if (!id.isBlank() && !displayName.isBlank()) {
                    unique.putIfAbsent(id, new AzureDevOpsMember(id, displayName, email, uniqueName));
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    public AzureWorkItem createWorkItem(Ticket ticket) {
        AzureDevOpsConnection connection = repository.findById(ticket.getProjectId()).orElse(null);
        if (connection == null || !connection.isEnabled()) return null;
        String pat = credential(ticket.getProjectId());
        List<Map<String, Object>> operations = new ArrayList<>();
        operations.add(operation("/fields/System.Title", ticket.getTitle()));
        operations.add(operation("/fields/System.Description", html(ticket.getDescription()).replace("\n", "<br/>")));
        String tags = java.util.stream.Stream.concat(java.util.stream.Stream.of("VPLMQA", "E2E", "Automated"),
                        ticket.getTags().stream())
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("; "));
        operations.add(operation("/fields/System.Tags", tags));
        if (connection.getAreaPath() != null && !connection.getAreaPath().isBlank()) {
            operations.add(operation("/fields/System.AreaPath", connection.getAreaPath()));
        }
        if (ticket.getAssignedTo() != null && !ticket.getAssignedTo().isBlank()) {
            operations.add(operation("/fields/System.AssignedTo", ticket.getAssignedTo()));
        }
        try {
            String body = objectMapper.writeValueAsString(operations);
            String url = String.format(
                    "https://dev.azure.com/%s/%s/_apis/wit/workitems/$%s?api-version=7.1",
                    encode(normalizeOrganization(connection.getOrganization())),
                    encode(normalizeProject(connection.getAzureProject(), connection.getOrganization())),
                    encode(connection.getWorkItemType()));
            HttpResponse<String> response = httpClient.send(
                    request(url, pat)
                            .header("Content-Type", "application/json-patch+json")
                            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "work-item creation");
            JsonNode result = objectMapper.readTree(response.body());
            return new AzureWorkItem(result.path("id").asInt(), result.path("_links").path("html").path("href").asText(""));
        } catch (Exception exception) {
            throw new IllegalStateException(readableMessage(exception), exception);
        }
    }


    public void attachToWorkItem(Ticket ticket, String fileName, String contentType, byte[] content) {
        AzureDevOpsConnection connection = repository.findById(ticket.getProjectId()).orElse(null);
        if (connection == null || !connection.isEnabled() || ticket.getAzureWorkItemId() == null) return;
        String pat = credential(ticket.getProjectId());
        try {
            String baseUrl = String.format("https://dev.azure.com/%s/%s/_apis/wit",
                    encode(normalizeOrganization(connection.getOrganization())),
                    encode(normalizeProject(connection.getAzureProject(), connection.getOrganization())));
            HttpResponse<String> upload = httpClient.send(
                    request(baseUrl + "/attachments?fileName=" + encode(fileName) + "&api-version=7.1", pat)
                            .header("Content-Type", "application/octet-stream")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            ensureSuccess(upload, "attachment upload");
            String attachmentUrl = objectMapper.readTree(upload.body()).path("url").asText("");
            if (attachmentUrl.isBlank()) throw new IllegalStateException("Azure DevOps did not return an attachment URL.");
            List<Map<String, Object>> operations = List.of(Map.of(
                    "op", "add",
                    "path", "/relations/-",
                    "value", Map.of("rel", "AttachedFile", "url", attachmentUrl,
                            "attributes", Map.of("comment", "Evidence captured by VPLMQA"))));
            HttpResponse<String> link = httpClient.send(
                    request(baseUrl + "/workitems/" + ticket.getAzureWorkItemId() + "?api-version=7.1", pat)
                            .header("Content-Type", "application/json-patch+json")
                            .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(operations)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            ensureSuccess(link, "attachment linking");
            // Images are also embedded in the work-item description for direct
            // review, while every artifact remains available as an attachment.
            if (isImage(contentType)) {
                embedImageInDescription(ticket, pat, baseUrl, fileName, attachmentUrl);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(readableMessage(exception), exception);
        }
    }
    private void embedImageInDescription(Ticket ticket, String pat, String baseUrl,
                                         String fileName, String attachmentUrl) throws Exception {
        String workItemUrl = baseUrl + "/workitems/" + ticket.getAzureWorkItemId()
                + "?fields=System.Description&api-version=7.1";
        HttpResponse<String> current = httpClient.send(
                request(workItemUrl, pat).GET().build(), HttpResponse.BodyHandlers.ofString());
        ensureSuccess(current, "work-item retrieval");
        String description = objectMapper.readTree(current.body())
                .path("fields").path("System.Description").asText("");
        if (description.contains(attachmentUrl)) return;

        String evidence = "<hr/><p><strong>Capture VPLMQA : " + html(fileName)
                + "</strong></p><img src=\"" + html(attachmentUrl) + "\" alt=\""
                + html(fileName) + "\" />";
        List<Map<String, Object>> operations = List.of(operation("/fields/System.Description", description + evidence));
        HttpResponse<String> update = httpClient.send(
                request(baseUrl + "/workitems/" + ticket.getAzureWorkItemId() + "?api-version=7.1", pat)
                        .header("Content-Type", "application/json-patch+json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(operations)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        ensureSuccess(update, "work-item evidence display");
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/");
    }
    private JsonNode get(AzureDevOpsConnection connection, String pat, String url) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request(url, pat).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "connection test");
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException(readableMessage(exception), exception);
        }
    }

    private HttpRequest.Builder request(String url, String pat) {
        String auth = Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Basic " + auth)
                .header("Accept", "application/json");
    }

    private AzureDevOpsConnection configured(UUID projectId) {
        AzureDevOpsConnection connection = repository.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Azure DevOps is not configured for this project."));
        if (!connection.isEnabled()) throw new IllegalStateException("Azure DevOps is disabled for this project.");
        return connection;
    }

    private String credential(UUID projectId) {
        String pat = vault.get(secretPath(projectId), "pat");
        if (pat.isBlank()) pat = vault.get(sharedSecretPath(), "pat");
        if (pat.isBlank()) throw new IllegalStateException("Azure DevOps PAT is not configured.");
        return pat;
    }

    private AzureDevOpsConnectionResponse response(AzureDevOpsConnection connection) {
        return new AzureDevOpsConnectionResponse(
                connection.getProjectId(),
                connection.getOrganization(),
                connection.getAzureProject(),
                connection.getWorkItemType(),
                connection.getAreaPath() == null ? "" : connection.getAreaPath(),
                connection.isEnabled(),
                hasCredential(connection.getProjectId()));
    }

    private Map<String, Object> operation(String path, String value) {
        return Map.of("op", "add", "path", path, "value", value == null ? "" : value);
    }

    private void ensureSuccess(HttpResponse<String> response, String operation) {
        if (response.statusCode() >= 400) {
            String detail = response.body();
            try {
                detail = objectMapper.readTree(response.body()).path("message").asText(detail);
            } catch (Exception ignored) {
                // Keep the response text when Azure does not return JSON.
            }
            throw new IllegalStateException("Azure DevOps " + operation + " failed (HTTP "
                    + response.statusCode() + "): " + detail);
        }
    }

    private String readableMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "Azure DevOps request failed." : cause.getMessage();
    }

    private String html(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String secretPath(UUID projectId) {
        return "vplmqa/projects/" + projectId + "/azure-devops";
    }

    private String sharedSecretPath() {
        return "vplmqa/platform/azure-devops";
    }

    private boolean hasCredential(UUID projectId) {
        return vault.exists(secretPath(projectId), "pat")
                || vault.exists(sharedSecretPath(), "pat");
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeOrganization(String value) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replaceFirst("(?i)^https?://", "");
        normalized = normalized.replaceFirst("(?i)^dev\\.azure\\.com/", "");
        normalized = normalized.replaceFirst("(?i)\\.visualstudio\\.com.*$", "");
        int slash = normalized.indexOf('/');
        if (slash >= 0) normalized = normalized.substring(0, slash);
        return normalized.trim();
    }

    private String normalizeProject(String value, String organization) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replaceFirst("(?i)^https?://", "");
        normalized = normalized.replaceFirst("(?i)^dev\\.azure\\.com/", "");
        String normalizedOrganization = normalizeOrganization(organization);
        if (!normalizedOrganization.isBlank()) {
            normalized = normalized.replaceFirst("(?i)^" + java.util.regex.Pattern.quote(normalizedOrganization) + "/", "");
        }
        int slash = normalized.indexOf('/');
        if (slash >= 0) normalized = normalized.substring(0, slash);
        return normalized.trim();
    }

    public record AzureWorkItem(int id, String url) {
    }
}
