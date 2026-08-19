package com.vplmqa.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vplmqa.common.ApiException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page.WaitForLoadStateOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.LoadState;
import com.vplmqa.project.dto.ExtractionArtifactResponse;
import com.vplmqa.project.dto.MlDatasetResponse;
import com.vplmqa.project.entity.Component;
import com.vplmqa.project.entity.Page;
import com.vplmqa.project.entity.Project;
import com.vplmqa.project.enumtype.ComponentSourceEnum;
import com.vplmqa.project.enumtype.ComponentStatusEnum;
import com.vplmqa.project.repository.ComponentRepository;
import com.vplmqa.project.repository.PageRepository;
import com.vplmqa.project.repository.ProjectRepository;
import java.time.Instant;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectExtractionService {

    private static final List<String> WEB_E2E_TAGS = List.of("button", "input", "select", "textarea", "form", "a", "label");
    private static final Pattern FIGMA_FILE_PATTERN = Pattern.compile("/(?:file|design)/([^/?#]+)");
    private static final String FIGMA_TOKEN_MESSAGE = "Figma access token is invalid, missing, or does not have permission for this file. Save a valid Figma personal access token on the project.";
    private static final int MAX_DB_NAME_LENGTH = 120;

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final ComponentRepository componentRepository;
    private final ExtractionStorageService storageService;
    private final ExtractionPathService pathService;
    private final ObjectMapper objectMapper;

    public ProjectExtractionService(ProjectRepository projectRepository,
                                    PageRepository pageRepository,
                                    ComponentRepository componentRepository,
                                    ExtractionStorageService storageService,
                                    ExtractionPathService pathService,
                                    ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.componentRepository = componentRepository;
        this.storageService = storageService;
        this.pathService = pathService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExtractionArtifactResponse extractFigmaDesign(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        JsonNode figmaFile = fetchFigmaFile(project);
        return storeFigmaDesign(projectId, figmaFile);
    }

    @Transactional
    public ExtractionArtifactResponse extractFigmaComponents(UUID projectId, UUID pageId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new RuntimeException("Page not found: " + pageId));
        if (page.getFigmaObjectPath() == null || page.getFigmaObjectPath().isBlank()) {
            throw new RuntimeException("Extract Figma pages before extracting components for " + page.getName());
        }
        try {
            JsonNode pageJson = objectMapper.readTree(storageService.downloadText(page.getFigmaObjectPath()));
            return storeFigmaPage(project.getId(), page.getId(), pageJson);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to read stored Figma page JSON: " + exception.getMessage(), exception);
        }
    }

    @Transactional
    public ExtractionArtifactResponse storeFigmaPage(UUID projectId, UUID pageId, JsonNode pageJson) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new RuntimeException("Page not found: " + pageId));

        JsonNode refinedPageJson = refinePageForComponentExtraction(pageJson, page.getName(), ComponentSourceEnum.FIGMA);
        String pagePath = pathService.figmaPagePath(project.getName(), page.getName());
        storageService.uploadJson(pagePath, toPrettyJson(refinedPageJson));
        page.setFigmaObjectPath(pagePath);
        page.setLastScannedAt(Instant.now());
        page.setScanStatus("FIGMA_EXTRACTED");

        List<JsonNode> componentNodes = new ArrayList<>();
        collectFigmaComponents(refinedPageJson, componentNodes);
        List<String> componentPaths = storeComponents(project, page, componentNodes, ComponentSourceEnum.FIGMA);

        pageRepository.save(page);
        return new ExtractionArtifactResponse(projectId, pageId, page.getName(), "FIGMA", pagePath, componentPaths.size(), componentPaths);
    }

    @Transactional
    public ExtractionArtifactResponse storeFigmaDesign(UUID projectId, JsonNode figmaJson) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        String designPath = pathService.designPath(project.getName());
        storageService.uploadJson(designPath, toPrettyJson(figmaJson));

        List<JsonNode> figmaPages = extractFigmaPages(figmaJson);
        if (figmaPages.isEmpty()) {
            throw new RuntimeException("No Figma pages found in design JSON");
        }

        List<String> pagePaths = new ArrayList<>();
        Page firstPage = null;
        List<Page> existingPages = pageRepository.findByProjectId(projectId);
        Map<String, byte[]> pageImages = fetchFigmaPageImages(project, figmaPages);
        for (JsonNode figmaPage : figmaPages) {
            String originalPageName = firstNonBlank(figmaPage.path("name").asText(null), "page");
            String pageName = compactName(originalPageName, MAX_DB_NAME_LENGTH);
            List<Page> matchingPages = existingPages.stream()
                    .filter(this::isFigmaPage)
                    .filter(existing -> existing.getName().equalsIgnoreCase(pageName))
                    .toList();
            Page page = matchingPages.stream()
                    .max(Comparator
                            .comparing((Page existing) -> !componentRepository.findByPageId(existing.getId()).isEmpty())
                            .thenComparing(existing -> existing.getLastScannedAt() == null
                                    ? Instant.EPOCH : existing.getLastScannedAt()))
                    .orElseGet(Page::new);
            for (Page duplicate : matchingPages) {
                if (!duplicate.getId().equals(page.getId())
                        && componentRepository.findByPageId(duplicate.getId()).isEmpty()) {
                    pageRepository.delete(duplicate);
                    existingPages.remove(duplicate);
                }
            }
            if (page.getId() == null) {
                page.setProject(project);
            }
            page.setName(pageName);
            page.setPath(pagePath(pageName));
            page.setUrl(null);
            page.setLastScannedAt(Instant.now());
            page.setScanStatus("FIGMA_PAGE_EXTRACTED");

            String pagePath = pathService.figmaPagePath(project.getName(), pageName);
            storageService.uploadJson(pagePath, toPrettyJson(figmaPage));
            String renderNodeId = largestRenderableChild(figmaPage).path("id").asText("");
            byte[] pageImage = pageImages.getOrDefault(renderNodeId, new byte[0]);
            if (pageImage.length > 0) {
                storageService.uploadPng(pathService.figmaPageImagePath(project.getName(), pageName), pageImage);
            }
            page.setFigmaObjectPath(pagePath);
            pageRepository.save(page);
            firstPage = firstPage == null ? page : firstPage;
            pagePaths.add(pagePath);
        }

        return new ExtractionArtifactResponse(
                projectId,
                firstPage == null ? null : firstPage.getId(),
                project.getName(),
                "FIGMA_DESIGN",
                designPath,
                pagePaths.size(),
                pagePaths
        );
    }

    private boolean isFigmaPage(Page page) {
        return page.getFigmaObjectPath() != null && !page.getFigmaObjectPath().isBlank()
                || page.getUrl() == null
                || page.getScanStatus() != null && page.getScanStatus().startsWith("FIGMA");
    }

    @Transactional
    public ExtractionArtifactResponse extractWebPage(UUID projectId, UUID pageId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new RuntimeException("Page not found: " + pageId));

        WebExtraction webExtraction = extractWebJson(page.getUrl(), page.getName());
        JsonNode webPageJson = refinePageForComponentExtraction(webExtraction.pageJson(), page.getName(), ComponentSourceEnum.WEB);
        String pagePath = pathService.webPagePath(project.getName(), page.getName());
        storageService.uploadJson(pagePath, toPrettyJson(webPageJson));
        storageService.uploadPng(pathService.webPageImagePath(project.getName(), page.getName()), webExtraction.screenshotPng());
        page.setWebObjectPath(pagePath);
        page.setLastScannedAt(Instant.now());
        page.setScanStatus("WEB_EXTRACTED");

        List<JsonNode> componentNodes = new ArrayList<>();
        collectWebComponents(webPageJson.path("dom"), componentNodes);
        List<String> componentPaths = storeComponents(project, page, componentNodes, ComponentSourceEnum.WEB);

        pageRepository.save(page);
        return new ExtractionArtifactResponse(projectId, pageId, page.getName(), "WEB", pagePath, componentPaths.size(), componentPaths);
    }

    @Transactional
    public MlDatasetResponse generateMlDataset(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        List<Component> projectComponents = componentRepository.findByPage_Project_Id(projectId);
        List<Component> figmaComponents = projectComponents.stream()
                .filter(component -> component.getSource() == ComponentSourceEnum.FIGMA)
                .sorted(Comparator.comparing(component -> safe(component.getPage().getName()) + safe(component.getCanonicalName())))
                .toList();
        List<Component> webComponents = projectComponents.stream()
                .filter(component -> component.getSource() == ComponentSourceEnum.WEB)
                .toList();

        if (figmaComponents.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Extract Figma components before generating the ML dataset.");
        }

        Set<UUID> matchedWebComponentIds = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Component figmaComponent : figmaComponents) {
            Component webComponent = bestWebMatch(figmaComponent, webComponents, matchedWebComponentIds);
            if (webComponent != null) {
                matchedWebComponentIds.add(webComponent.getId());
            }
            rows.add(mlRow(figmaComponent, webComponent));
        }

        String jsonPath = pathService.projectMlDatasetJsonPath(project.getName());
        String csvPath = pathService.projectMlDatasetCsvPath(project.getName());
        storageService.uploadJson(jsonPath, toPrettyJson(objectMapper.valueToTree(rows)));
        storageService.uploadCsv(csvPath, toCsv(rows));

        return new MlDatasetResponse(projectId, rows.size(), jsonPath, csvPath, rows);
    }

    private WebExtraction extractWebJson(String url, String pageName) {
        String browserUrl = resolveBrowserUrl(url);
        try (Playwright playwright = Playwright.create();
             Browser browser = launchBrowser(playwright);
             BrowserContext context = browser.newContext(
                     new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
            com.microsoft.playwright.Page browserPage = context.newPage();
            browserPage.navigate(browserUrl);
            browserPage.waitForLoadState(LoadState.NETWORKIDLE, new WaitForLoadStateOptions().setTimeout(30000));
            Object dom = browserPage.evaluate(WEB_EXTRACTION_SCRIPT);
            byte[] screenshot = browserPage.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setType(ScreenshotType.PNG));
            ObjectNode root = objectMapper.createObjectNode();
            root.put("url", url);
            root.put("browserUrl", browserUrl);
            root.put("pageName", pageName);
            root.put("extractedAt", Instant.now().toString());
            root.set("dom", objectMapper.valueToTree(dom));
            return new WebExtraction(root, screenshot);
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Web page is not reachable from Docker at " + browserUrl + ". Saved URL was " + url + ". " + exception.getMessage()
            );
        }
    }

    private Browser launchBrowser(Playwright playwright) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of("--ignore-certificate-errors"));
        browserExecutablePath().ifPresent(options::setExecutablePath);
        return playwright.chromium().launch(options);
    }

    private java.util.Optional<Path> browserExecutablePath() {
        List<String> candidates = new ArrayList<>();
        addIfPresent(candidates, System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH"));
        addIfPresent(candidates, System.getenv("CHROME_PATH"));
        addIfPresent(candidates, System.getenv("CHROME_EXECUTABLE_PATH"));
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String localAppData = System.getenv("LOCALAPPDATA");
        addIfPresent(candidates, join(programFiles, "Google\\Chrome\\Application\\chrome.exe"));
        addIfPresent(candidates, join(programFilesX86, "Google\\Chrome\\Application\\chrome.exe"));
        addIfPresent(candidates, join(localAppData, "Google\\Chrome\\Application\\chrome.exe"));
        addIfPresent(candidates, "/usr/bin/chromium");
        addIfPresent(candidates, "/usr/bin/chromium-browser");
        return candidates.stream()
                .map(Paths::get)
                .filter(Files::isRegularFile)
                .findFirst();
    }

    private void addIfPresent(List<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value.trim());
        }
    }

    private String join(String root, String child) {
        return root == null || root.isBlank() ? null : Paths.get(root, child).toString();
    }

    private String resolveBrowserUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Web page URL is empty.");
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            if (isLocalHost(host) && port == 3000) {
                if (isPortReachable("127.0.0.1", 3000)) {
                    return url.trim();
                }
                return "http://front-end" + path + query;
            }
            if (isLocalHost(host) && port == 8080) {
                if (isPortReachable("127.0.0.1", 8080)) {
                    return url.trim();
                }
                return "http://gateway:8080" + path + query;
            }
            return url.trim();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Web page URL is invalid: " + url);
        }
    }

    private boolean isLocalHost(String host) {
        return host != null && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
    }

    private boolean isPortReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private JsonNode fetchFigmaFile(Project project) {
        String fileKey = normalizeFigmaFileKey(project.getFigmaFileUrl());
        if (fileKey.isBlank()) {
            throw new RuntimeException("Project does not have a Figma file key.");
        }
        String token = resolveFigmaToken(project);
        if (token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FIGMA_TOKEN_MESSAGE);
        }

        try {
            String encodedKey = URLEncoder.encode(fileKey, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.figma.com/v1/files/" + encodedKey))
                    .GET();
            applyFigmaAuthHeader(requestBuilder, token);
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, FIGMA_TOKEN_MESSAGE);
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Figma API returned HTTP " + response.statusCode() + " while extracting the design.");
            }
            return objectMapper.readTree(response.body());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to fetch real Figma design: " + exception.getMessage(), exception);
        }
    }

    private Map<String, byte[]> fetchFigmaPageImages(Project project, List<JsonNode> figmaPages) {
        String fileKey = normalizeFigmaFileKey(project.getFigmaFileUrl());
        String token = resolveFigmaToken(project);
        List<String> nodeIds = figmaPages.stream()
                .map(this::largestRenderableChild)
                .map(node -> node.path("id").asText(""))
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        if (fileKey.isBlank() || token.isBlank() || nodeIds.isEmpty()) {
            return Map.of();
        }
        try {
            String encodedKey = URLEncoder.encode(fileKey, StandardCharsets.UTF_8).replace("+", "%20");
            String encodedNodeIds = nodeIds.stream()
                    .map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20"))
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            HttpRequest.Builder imageRequestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.figma.com/v1/images/" + encodedKey + "?ids=" + encodedNodeIds + "&format=png&scale=1"))
                    .GET();
            applyFigmaAuthHeader(imageRequestBuilder, token);
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
            HttpResponse<String> imageResponse = client.send(
                    imageRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (imageResponse.statusCode() < 200 || imageResponse.statusCode() >= 300) {
                return Map.of();
            }
            JsonNode images = objectMapper.readTree(imageResponse.body()).path("images");
            Map<String, CompletableFuture<byte[]>> downloads = new LinkedHashMap<>();
            for (String nodeId : nodeIds) {
                String imageUrl = images.path(nodeId).asText("");
                if (imageUrl.isBlank()) {
                    continue;
                }
                downloads.put(nodeId, client.sendAsync(
                                HttpRequest.newBuilder(URI.create(imageUrl)).timeout(java.time.Duration.ofSeconds(30)).GET().build(),
                                HttpResponse.BodyHandlers.ofByteArray())
                        .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300
                                ? response.body() : new byte[0])
                        .exceptionally(ignored -> new byte[0]));
            }
            CompletableFuture.allOf(downloads.values().toArray(CompletableFuture[]::new)).join();
            Map<String, byte[]> result = new LinkedHashMap<>();
            downloads.forEach((nodeId, future) -> result.put(nodeId, future.join()));
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private JsonNode largestRenderableChild(JsonNode page) {
        JsonNode best = page;
        double bestArea = 0;
        for (JsonNode child : page.path("children")) {
            String type = child.path("type").asText("");
            if (!List.of("FRAME", "COMPONENT", "INSTANCE", "SECTION").contains(type)) {
                continue;
            }
            double area = pathDouble(child, "absoluteBoundingBox", "width")
                    * pathDouble(child, "absoluteBoundingBox", "height");
            if (area > bestArea) {
                best = child;
                bestArea = area;
            }
        }
        return best;
    }

    private String normalizeFigmaFileKey(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        Matcher matcher = FIGMA_FILE_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return trimmed;
    }

    private String resolveFigmaToken(Project project) {
        String projectToken = normalizeToken(project.getFigmaTokenEncrypted());
        if (looksLikeFigmaToken(projectToken)) {
            return projectToken;
        }
        return "";
    }

    private void applyFigmaAuthHeader(HttpRequest.Builder requestBuilder, String token) {
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            requestBuilder.header("Authorization", token);
            return;
        }
        requestBuilder.header("X-Figma-Token", token);
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return "";
        }
        return token.trim();
    }

    private boolean looksLikeFigmaToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String trimmed = token.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                || trimmed.startsWith("figd_")
                || trimmed.startsWith("figpat-")
                || (trimmed.length() >= 30 && !trimmed.contains("/") && !trimmed.contains(" "));
    }

    private List<JsonNode> extractFigmaPages(JsonNode root) {
        List<JsonNode> pages = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectFigmaPages(root, pages, seen);
        if (pages.isEmpty() && root != null) {
            String type = root.path("type").asText("").toUpperCase();
            if ("FRAME".equals(type) || "PAGE".equals(type) || "CANVAS".equals(type)) {
                pages.add(root);
            }
        }
        return pages;
    }

    private void collectFigmaPages(JsonNode node, List<JsonNode> pages, Set<String> seen) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            String type = node.path("type").asText("").toUpperCase();
            String id = node.path("id").asText(node.path("name").asText(""));
            if ("CANVAS".equals(type) && seen.add(id)) {
                pages.add(node);
                return;
            }
            JsonNode children = node.path("children");
            if (children.isArray()) {
                collectFigmaPages(children, pages, seen);
            }
            node.fields().forEachRemaining(entry -> {
                if (!"children".equals(entry.getKey())) {
                    collectFigmaPages(entry.getValue(), pages, seen);
                }
            });
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectFigmaPages(child, pages, seen);
            }
        }
    }

    private String pagePath(String pageName) {
        String sanitized = pageName == null ? "page" : pageName.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return "/" + (sanitized.isBlank() ? "page" : sanitized);
    }

    private String compactName(String value, int maxLength) {
        String normalized = firstNonBlank(value, "page").trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        String hash = Integer.toHexString(normalized.hashCode());
        int prefixLength = maxLength - hash.length() - 3;
        return normalized.substring(0, Math.max(1, prefixLength)).trim() + "-" + hash;
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String normalizedPath = path == null || path.isBlank() ? "/" : path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return base + normalizedPath;
    }

    private Component bestWebMatch(Component figmaComponent, List<Component> webComponents, Set<UUID> alreadyMatched) {
        String figmaPageKey = matchKey(figmaComponent.getPage().getName());
        String figmaNameKey = matchKey(figmaComponent.getCanonicalName());
        Component best = null;
        int bestScore = -1;
        for (Component webComponent : webComponents) {
            if (webComponent.getId() != null && alreadyMatched.contains(webComponent.getId())) {
                continue;
            }
            int score = 0;
            String webPageKey = matchKey(webComponent.getPage().getName());
            if (!figmaPageKey.isBlank() && !webPageKey.isBlank() && !figmaPageKey.equals(webPageKey)) {
                continue;
            }
            String webNameKey = matchKey(webComponent.getCanonicalName());
            if (!figmaPageKey.isBlank() && figmaPageKey.equals(webPageKey)) {
                score += 100;
            }
            if (!figmaNameKey.isBlank() && figmaNameKey.equals(webNameKey)) {
                score += 80;
            } else if (!figmaNameKey.isBlank() && (figmaNameKey.contains(webNameKey) || webNameKey.contains(figmaNameKey))) {
                score += 45;
            }
            String webLocatorKey = matchKey(firstNonBlank(webComponent.getTestIdentifier(), webComponent.getHtmlId(), webComponent.getCssSelector()));
            if (!figmaNameKey.isBlank() && !webLocatorKey.isBlank() && (figmaNameKey.contains(webLocatorKey) || webLocatorKey.contains(figmaNameKey))) {
                score += 30;
            }
            if (score > bestScore) {
                bestScore = score;
                best = webComponent;
            }
        }
        return bestScore >= 45 ? best : null;
    }

    private Map<String, Object> mlRow(Component figmaComponent, Component webComponent) {
        Map<String, Object> row = new LinkedHashMap<>();
        String uniqueName = semanticName(figmaComponent, webComponent);
        row.put("component", uniqueName);
        row.put("figma_color", normalizeColor(firstToken(figmaComponent, "color", "backgroundColor", "strokeColor")));
        row.put("code_color", normalizeColor(firstToken(webComponent, "color", "backgroundColor", "borderColor")));
        row.put("figma_spacing", spacingToken(figmaComponent));
        row.put("code_spacing", maxPositive(
                numberToken(token(webComponent, "spacing")),
                numberToken(token(webComponent, "padding")),
                numberToken(token(webComponent, "paddingTop")),
                numberToken(token(webComponent, "paddingRight")),
                numberToken(token(webComponent, "paddingBottom")),
                numberToken(token(webComponent, "paddingLeft")),
                numberToken(token(webComponent, "gap"))
        ));
        row.put("figma_font_size", numberToken(token(figmaComponent, "fontSize")));
        row.put("code_font_size", numberToken(token(webComponent, "fontSize")));
        row.put("figma_font_weight", normalizeFontWeight(token(figmaComponent, "fontWeight")));
        row.put("code_font_weight", normalizeFontWeight(token(webComponent, "fontWeight")));
        row.put("figma_border_radius", numberToken(token(figmaComponent, "borderRadius")));
        row.put("code_border_radius", numberToken(token(webComponent, "borderRadius")));
        row.put("figma_width", numberToken(box(figmaComponent, "width")));
        row.put("code_width", numberToken(box(webComponent, "width")));
        row.put("figma_height", numberToken(box(figmaComponent, "height")));
        row.put("code_height", numberToken(box(webComponent, "height")));
        row.put("uniqueName", uniqueName);
        row.put("usage", usageFor(uniqueName, figmaComponent, webComponent));
        row.put("figma_page", figmaComponent.getPage().getName());
        row.put("web_page", webComponent == null ? "" : webComponent.getPage().getName());
        row.put("web_locator", webComponent == null ? "" : webLocator(webComponent));
        return row;
    }

    private String toCsv(List<Map<String, Object>> rows) {
        List<String> columns = List.of(
                "component",
                "figma_color",
                "code_color",
                "figma_spacing",
                "code_spacing",
                "figma_font_size",
                "code_font_size",
                "figma_font_weight",
                "code_font_weight",
                "figma_border_radius",
                "code_border_radius",
                "figma_width",
                "code_width",
                "figma_height",
                "code_height"
        );
        StringBuilder csv = new StringBuilder(String.join(",", columns)).append("\n");
        for (Map<String, Object> row : rows) {
            for (int index = 0; index < columns.size(); index++) {
                if (index > 0) {
                    csv.append(",");
                }
                csv.append(csvCell(row.get(columns.get(index))));
            }
            csv.append("\n");
        }
        return csv.toString();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String semanticName(Component figmaComponent, Component webComponent) {
        String base = firstNonBlank(
                figmaComponent.getFunctionalMeaning(),
                figmaComponent.getSemanticRole(),
                figmaComponent.getCanonicalName(),
                webComponent == null ? null : webComponent.getFunctionalMeaning(),
                webComponent == null ? null : webComponent.getSemanticRole(),
                webComponent == null ? null : webComponent.getCanonicalName(),
                "component"
        );
        return compactName(base, 80);
    }

    private String usageFor(String uniqueName, Component figmaComponent, Component webComponent) {
        String role = firstNonBlank(figmaComponent.getSemanticRole(), webComponent == null ? null : webComponent.getSemanticRole(), uniqueName);
        String locator = webComponent == null ? "" : webLocator(webComponent);
        if (locator.isBlank()) {
            return "Use " + role + " as a design component to validate visual token parity against the implemented interface.";
        }
        return "Use " + role + " through locator " + locator + " during end-to-end execution and compare its implemented visual tokens with the Figma design.";
    }

    private String token(Component component, String key) {
        if (component == null || component.getCssProperties() == null) {
            return "";
        }
        Object value = component.getCssProperties().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstToken(Component component, String... keys) {
        if (component == null) {
            return "";
        }
        for (String key : keys) {
            String value = token(component, key);
            if (!value.isBlank() && !"transparent".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    private int spacingToken(Component component) {
        return maxPositive(
                numberToken(token(component, "spacing")),
                numberToken(token(component, "padding")),
                numberToken(token(component, "paddingTop")),
                numberToken(token(component, "paddingRight")),
                numberToken(token(component, "paddingBottom")),
                numberToken(token(component, "paddingLeft")),
                numberToken(token(component, "gap"))
        );
    }

    private Object box(Component component, String key) {
        if (component == null || component.getBoundingBox() == null) {
            return 0;
        }
        return component.getBoundingBox().getOrDefault(key, 0);
    }

    private int numberToken(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        try {
            String text = String.valueOf(value).trim();
            Matcher matcher = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(text);
            if (!matcher.find()) {
                return 0;
            }
            text = matcher.group();
            return (int) Math.round(Double.parseDouble(text));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.trim();
        if ("transparent".equalsIgnoreCase(text)) {
            return "transparent";
        }
        if (text.startsWith("#")) {
            String hex = text.substring(1).trim();
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.length() >= 6) {
                return "#" + hex.substring(0, 6).toUpperCase();
            }
            return text.toUpperCase();
        }
        Matcher matcher = Pattern.compile("rgba?\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            String[] parts = matcher.group(1).split(",");
            if (parts.length >= 3) {
                double alpha = parts.length >= 4 ? alphaValue(parts[3]) : 1.0;
                if (alpha <= 0.0) {
                    return "transparent";
                }
                return String.format("#%02X%02X%02X",
                        colorChannel(parts[0]), colorChannel(parts[1]), colorChannel(parts[2]));
            }
        }
        return text;
    }

    private int colorChannel(String value) {
        String text = value == null ? "" : value.trim();
        try {
            double parsed = text.endsWith("%")
                    ? Double.parseDouble(text.substring(0, text.length() - 1).trim()) * 2.55
                    : Double.parseDouble(text);
            return Math.max(0, Math.min(255, (int) Math.round(parsed)));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double alphaValue(String value) {
        String text = value == null ? "" : value.trim();
        try {
            if (text.endsWith("%")) {
                return Double.parseDouble(text.substring(0, text.length() - 1).trim()) / 100.0;
            }
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return 1.0;
        }
    }

    private String normalizeFontWeight(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.trim().toLowerCase();
        return switch (text) {
            case "normal", "regular" -> "400";
            case "medium" -> "500";
            case "semibold", "semi-bold", "demibold", "demi-bold" -> "600";
            case "bold" -> "700";
            default -> {
                int numeric = numberToken(text);
                yield numeric > 0 ? String.valueOf(numeric) : value.trim();
            }
        };
    }

    private int firstPositive(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private int maxPositive(int... values) {
        int max = 0;
        for (int value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private String webLocator(Component component) {
        return firstNonBlank(component.getHtmlId(), component.getTestIdentifier(), component.getCssSelector(), component.getXpath());
    }

    private String matchKey(String value) {
        return safe(value).toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private JsonNode refinePageForComponentExtraction(JsonNode pageJson, String pageName, ComponentSourceEnum source) {
        JsonNode refined = pageJson.deepCopy();
        Map<String, Integer> names = new HashMap<>();
        if (source == ComponentSourceEnum.WEB) {
            refineWebNode((ObjectNode) refined.path("dom"), pageName, names);
        } else if (refined instanceof ObjectNode objectNode) {
            refineFigmaNode(objectNode, pageName, names);
        }
        if (refined instanceof ObjectNode objectNode) {
            objectNode.put("refinementVersion", "component-semantic-v1");
            objectNode.put("refinementProvider", "project-extraction-deterministic");
            objectNode.put("refinedBeforeComponentExtraction", true);
        }
        return refined;
    }

    private void refineFigmaNode(ObjectNode node, String pageName, Map<String, Integer> names) {
        String uniqueName = uniqueComponentName(pageName, roleForFigma(node), figmaLabel(node), names);
        node.put("uniqueName", uniqueName);
        node.put("semanticRole", roleForFigma(node));
        node.put("functionalMeaning", purposeFor(uniqueName, node.path("name").asText(""), ComponentSourceEnum.FIGMA));
        node.put("usage", usageForRole(pageName, roleForFigma(node), ""));
        node.put("e2eImportance", looksE2eRelevant(_textForRole(node)) ? "important" : "supporting");
        node.set("cssProperties", objectMapper.valueToTree(figmaTokens(node)));
        node.set("boundingBox", objectMapper.valueToTree(mapOf(
                "x", pathDouble(node, "absoluteBoundingBox", "x"),
                "y", pathDouble(node, "absoluteBoundingBox", "y"),
                "width", pathDouble(node, "absoluteBoundingBox", "width"),
                "height", pathDouble(node, "absoluteBoundingBox", "height")
        )));
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                if (child instanceof ObjectNode childObject) {
                    refineFigmaNode(childObject, pageName, names);
                }
            }
        }
    }

    private void refineWebNode(ObjectNode node, String pageName, Map<String, Integer> names) {
        String role = roleForWeb(node);
        String locator = firstNonBlank(
                node.path("attributes").path("data-testid").asText(null),
                node.path("attributes").path("data-test").asText(null),
                node.path("id").asText(null),
                node.path("cssSelector").asText(null),
                node.path("xpath").asText(null)
        );
        String uniqueName = uniqueComponentName(pageName, role, webLabel(node), names);
        node.put("uniqueName", uniqueName);
        node.put("semanticRole", role);
        node.put("functionalMeaning", purposeFor(uniqueName, node.path("name").asText(""), ComponentSourceEnum.WEB));
        node.put("usage", usageForRole(pageName, role, locator));
        node.put("e2eImportance", looksE2eRelevant(_textForRole(node)) || WEB_E2E_TAGS.contains(node.path("tag").asText("").toLowerCase()) ? "important" : "supporting");
        node.put("preferredLocator", locator);
        node.set("cssProperties", normalizeWebTokens(node));
        node.set("boundingBox", objectMapper.valueToTree(mapOf(
                "x", pathDouble(node, "position", "x"),
                "y", pathDouble(node, "position", "y"),
                "width", pathDouble(node, "size", "width"),
                "height", pathDouble(node, "size", "height")
        )));
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                if (child instanceof ObjectNode childObject) {
                    refineWebNode(childObject, pageName, names);
                }
            }
        }
    }

    private ObjectNode normalizeWebTokens(ObjectNode node) {
        ObjectNode tokens = objectMapper.createObjectNode();
        JsonNode style = node.path("computedStyle");
        tokens.put("color", normalizeColor(style.path("color").asText("")));
        tokens.put("backgroundColor", normalizeColor(style.path("backgroundColor").asText("")));
        tokens.put("borderColor", normalizeColor(style.path("borderColor").asText("")));
        tokens.put("spacing", maxPositive(
                numberToken(style.path("padding").asText("")),
                numberToken(style.path("paddingTop").asText("")),
                numberToken(style.path("paddingRight").asText("")),
                numberToken(style.path("paddingBottom").asText("")),
                numberToken(style.path("paddingLeft").asText("")),
                numberToken(style.path("gap").asText(""))
        ));
        tokens.put("padding", numberToken(style.path("padding").asText("")));
        tokens.put("paddingTop", numberToken(style.path("paddingTop").asText("")));
        tokens.put("paddingRight", numberToken(style.path("paddingRight").asText("")));
        tokens.put("paddingBottom", numberToken(style.path("paddingBottom").asText("")));
        tokens.put("paddingLeft", numberToken(style.path("paddingLeft").asText("")));
        tokens.put("gap", numberToken(style.path("gap").asText("")));
        tokens.put("fontSize", numberToken(style.path("fontSize").asText("")));
        tokens.put("fontWeight", normalizeFontWeight(style.path("fontWeight").asText("")));
        tokens.put("borderRadius", numberToken(style.path("borderRadius").asText("")));
        tokens.put("width", pathDouble(node, "size", "width"));
        tokens.put("height", pathDouble(node, "size", "height"));
        tokens.put("type", node.path("tag").asText(""));
        tokens.put("text", node.path("text").asText(node.path("name").asText("")));
        return tokens;
    }

    private String uniqueComponentName(String pageName, String role, String label, Map<String, Integer> names) {
        String page = slug(firstNonBlank(pageName, "page"));
        String base = page + "." + slug(firstNonBlank(role, "component")) + "_" + slug(firstNonBlank(label, role, "component"));
        int occurrence = names.getOrDefault(base, 0) + 1;
        names.put(base, occurrence);
        return occurrence == 1 ? base : base + "_" + occurrence;
    }

    private String roleForFigma(JsonNode node) {
        String text = _textForRole(node);
        String directName = node.path("name").asText("").toLowerCase();
        String type = node.path("type").asText("");
        if ("TEXT".equalsIgnoreCase(type)) {
            if (text.contains("title") || text.contains("heading") || pathInt(node, "style", "fontSize") >= 20) return "heading";
            if (text.contains("email")) return "email_label";
            if (text.contains("password")) return "password_label";
            return "text";
        }
        if (directName.contains("form") || directName.contains("card") || directName.contains("glass")) return "container";
        if (text.contains("password")) return "password_field";
        if (text.contains("email") || text.contains("mail")) return "email_field";
        if (text.contains("search")) return "search_field";
        if (text.contains("checkbox")) return "checkbox";
        if (text.contains("button") || text.contains("submit") || text.contains("sign in")) return "button";
        if (text.contains("link") || text.contains("nav") || text.contains("menu")) return "navigation";
        if (text.contains("title") || text.contains("heading")) return "heading";
        return "container";
    }

    private String roleForWeb(JsonNode node) {
        String text = _textForRole(node);
        String tag = node.path("tag").asText("").toLowerCase();
        String type = node.path("attributes").path("type").asText("").toLowerCase();
        if ("form".equals(tag)) return "form";
        if ("a".equals(tag) || text.contains("forgot password") || text.contains("link") || text.contains("nav")) return "navigation";
        if ("label".equals(tag)) {
            if (text.contains("email") || text.contains("mail")) return "email_label";
            if (text.contains("password")) return "password_label";
            return "label";
        }
        if ("password".equals(type) || text.contains("password")) return "password_field";
        if ("email".equals(type) || text.contains("email") || text.contains("mail")) return "email_field";
        if ("search".equals(type) || text.contains("search")) return "search_field";
        if ("checkbox".equals(type) || text.contains("checkbox") || text.contains("remember")) return "checkbox";
        if ("button".equals(tag) || "submit".equals(type) || text.contains("sign in")) return "button";
        if (List.of("h1", "h2", "h3", "h4").contains(tag)) return "heading";
        if ("input".equals(tag) || "textarea".equals(tag) || "select".equals(tag)) return "input_field";
        return "container";
    }

    private String usageForRole(String pageName, String role, String locator) {
        String suffix = locator == null || locator.isBlank() ? "" : " Preferred locator: " + locator + ".";
        if (role.contains("password")) return "On " + pageName + ", fill this after the user identifier before submitting credentials." + suffix;
        if (role.contains("email")) return "On " + pageName + ", fill this before the password or submit action." + suffix;
        if (role.contains("search")) return "On " + pageName + ", fill the query here before triggering search." + suffix;
        if (role.equals("button")) return "On " + pageName + ", click after the required preceding fields are complete." + suffix;
        if (role.equals("checkbox")) return "On " + pageName + ", toggle this option when the scenario requires that preference." + suffix;
        if (role.equals("navigation")) return "On " + pageName + ", use this to navigate to the related destination." + suffix;
        return "On " + pageName + ", use this component as part of the visual comparison or visible-state assertion." + suffix;
    }

    private String purposeFor(String uniqueName, String label, ComponentSourceEnum source) {
        return "Refined " + source.name().toLowerCase() + " component " + uniqueName + " derived from " + firstNonBlank(label, "the page structure") + ".";
    }

    private String figmaLabel(JsonNode node) {
        return firstNonBlank(node.path("name").asText(null), node.path("characters").asText(null), node.path("type").asText(null), "component");
    }

    private String webLabel(JsonNode node) {
        return firstNonBlank(
                node.path("attributes").path("data-testid").asText(null),
                node.path("attributes").path("placeholder").asText(null),
                node.path("ariaLabel").asText(null),
                node.path("text").asText(null),
                node.path("name").asText(null),
                node.path("id").asText(null),
                node.path("tag").asText(null),
                "component"
        );
    }

    private String _textForRole(JsonNode node) {
        return String.join(" ",
                node.path("uniqueName").asText(""),
                node.path("name").asText(""),
                node.path("characters").asText(""),
                node.path("text").asText(""),
                node.path("tag").asText(""),
                node.path("ariaLabel").asText(""),
                node.path("attributes").path("type").asText(""),
                node.path("attributes").path("placeholder").asText(""),
                node.path("attributes").path("data-testid").asText(""),
                node.path("id").asText(""),
                descendantText(node, "characters"),
                descendantText(node, "name")
        ).toLowerCase();
    }

    private String slug(String value) {
        String result = firstNonBlank(value, "component").toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return result.isBlank() ? "component" : result;
    }

    private List<String> storeComponents(Project project, Page page, List<JsonNode> nodes, ComponentSourceEnum source) {
        List<Component> oldComponents = componentRepository.findByPageId(page.getId()).stream()
                .filter(component -> component.getSource() == source)
                .toList();
        componentRepository.deleteAll(oldComponents);
        componentRepository.flush();

        List<String> paths = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (JsonNode node : nodes) {
            String name = componentName(node, source);
            int occurrence = occurrences.getOrDefault(name, 0) + 1;
            occurrences.put(name, occurrence);
            String objectPath = source == ComponentSourceEnum.FIGMA
                    ? pathService.figmaComponentPath(project.getName(), page.getName(), name, occurrence)
                    : pathService.webComponentPath(project.getName(), page.getName(), name, occurrence);
            storageService.uploadJson(objectPath, toPrettyJson(node));
            upsertComponent(page, node, source, occurrence == 1 ? name : name + "_" + occurrence, objectPath);
            paths.add(objectPath);
        }
        return paths;
    }

    private JsonNode buildFigmaComponentJson(JsonNode node, String uniqueName, int occurrence) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("figmaNodeId", node.path("id").asText(""));
        out.put("uniqueName", uniqueName + (occurrence > 1 ? "_" + occurrence : ""));
        out.put("role", node.path("type").asText(""));
        out.put("semanticRole", node.path("name").asText(""));
        out.put("usage", "");
        out.put("type", node.path("type").asText(""));
        out.put("text", node.path("characters").asText(""));
        // color from fills
        out.put("color", figmaColor(node));
        out.put("backgroundColor", figmaColor(node));
        out.put("strokeColor", figmaStrokeColor(node));
        // spacing
        int spacing = maxPositive(
                pathInt(node, "itemSpacing"),
                pathInt(node, "paddingTop"),
                pathInt(node, "paddingRight"),
                pathInt(node, "paddingBottom"),
                pathInt(node, "paddingLeft")
        );
        out.put("spacing", spacing);
        out.put("paddingTop", pathInt(node, "paddingTop"));
        out.put("paddingRight", pathInt(node, "paddingRight"));
        out.put("paddingBottom", pathInt(node, "paddingBottom"));
        out.put("paddingLeft", pathInt(node, "paddingLeft"));
        // typography
        out.put("fontSize", firstPositive(pathInt(node, "style", "fontSize"), descendantInt(node, "style", "fontSize")));
        out.put("fontWeight", firstNonBlank(node.path("style").path("fontWeight").asText(""), descendantText(node, "style", "fontWeight")));
        // geometry
        int borderRadius = firstInt(pathInt(node, "cornerRadius"), pathInt(node, "rectangleCornerRadii", "0"));
        out.put("borderRadius", borderRadius);
        double width = pathDouble(node, "absoluteBoundingBox", "width");
        double height = pathDouble(node, "absoluteBoundingBox", "height");
        out.put("width", width);
        out.put("height", height);
        // bounding box for reference
        ObjectNode bbox = objectMapper.createObjectNode();
        bbox.put("x", pathDouble(node, "absoluteBoundingBox", "x"));
        bbox.put("y", pathDouble(node, "absoluteBoundingBox", "y"));
        bbox.put("width", width);
        bbox.put("height", height);
        out.set("absoluteBoundingBox", bbox);
        return out;
    }

    private void upsertComponent(Page page, JsonNode node, ComponentSourceEnum source, String canonicalName, String objectPath) {
        Component component = componentRepository.findByCanonicalNameAndPage_Id(canonicalName, page.getId())
                .orElse(new Component());
        component.setPage(page);
        component.setCanonicalName(canonicalName);
        component.setSource(source);
        component.setStatus(ComponentStatusEnum.PENDING);
        component.setObjectPath(objectPath);
        component.setRawJson(toPrettyJson(node));
        component.setFunctionalMeaning(firstNonBlank(node.path("usage").asText(null), node.path("functionalMeaning").asText(null)));

        if (source == ComponentSourceEnum.FIGMA) {
            component.setFigmaNodeId(node.path("id").asText(null));
            component.setSemanticRole(firstNonBlank(node.path("semanticRole").asText(null), node.path("name").asText(null)));
            component.setBoundingBox(node.has("boundingBox") && node.path("boundingBox").isObject()
                    ? objectMapper.convertValue(node.path("boundingBox"), Map.class)
                    : mapOf(
                    "x", pathDouble(node, "absoluteBoundingBox", "x"),
                    "y", pathDouble(node, "absoluteBoundingBox", "y"),
                    "width", pathDouble(node, "absoluteBoundingBox", "width"),
                    "height", pathDouble(node, "absoluteBoundingBox", "height")
            ));
            component.setCssProperties(node.has("cssProperties") && node.path("cssProperties").isObject()
                    ? objectMapper.convertValue(node.path("cssProperties"), Map.class)
                    : figmaTokens(node));
        } else {
            String htmlId = node.path("id").asText(null);
            component.setHtmlId(htmlId);
            component.setCssSelector(firstNonBlank(htmlId, node.path("cssSelector").asText(null)));
            component.setXpath(node.path("xpath").asText(null));
            component.setTestIdentifier(firstNonBlank(
                    htmlId,
                    node.path("attributes").path("data-testid").asText(null),
                    node.path("attributes").path("data-test").asText(null)
            ));
            component.setSemanticRole(firstNonBlank(node.path("semanticRole").asText(null), node.path("ariaRole").asText(null)));
            component.setBoundingBox(node.has("boundingBox") && node.path("boundingBox").isObject()
                    ? objectMapper.convertValue(node.path("boundingBox"), Map.class)
                    : mapOf(
                    "x", pathDouble(node, "position", "x"),
                    "y", pathDouble(node, "position", "y"),
                    "width", pathDouble(node, "size", "width"),
                    "height", pathDouble(node, "size", "height")
            ));
            component.setCssProperties(node.has("cssProperties") && node.path("cssProperties").isObject()
                    ? objectMapper.convertValue(node.path("cssProperties"), Map.class)
                    : objectMapper.convertValue(node.path("computedStyle"), Map.class));
        }
        componentRepository.save(component);
    }

    private Map<String, Object> figmaTokens(JsonNode node) {
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("color", figmaColor(node));
        tokens.put("backgroundColor", figmaColor(node));
        tokens.put("strokeColor", figmaStrokeColor(node));
        tokens.put("spacing", maxPositive(
                pathInt(node, "itemSpacing"),
                pathInt(node, "paddingTop"),
                pathInt(node, "paddingRight"),
                pathInt(node, "paddingBottom"),
                pathInt(node, "paddingLeft")
        ));
        tokens.put("paddingTop", pathInt(node, "paddingTop"));
        tokens.put("paddingRight", pathInt(node, "paddingRight"));
        tokens.put("paddingBottom", pathInt(node, "paddingBottom"));
        tokens.put("paddingLeft", pathInt(node, "paddingLeft"));
        tokens.put("fontSize", firstPositive(pathInt(node, "style", "fontSize"), descendantInt(node, "style", "fontSize")));
        tokens.put("fontWeight", firstNonBlank(node.path("style").path("fontWeight").asText(""), descendantText(node, "style", "fontWeight")));
        tokens.put("borderRadius", firstInt(pathInt(node, "cornerRadius"), pathInt(node, "rectangleCornerRadii", "0")));
        tokens.put("width", pathDouble(node, "absoluteBoundingBox", "width"));
        tokens.put("height", pathDouble(node, "absoluteBoundingBox", "height"));
        tokens.put("type", node.path("type").asText(""));
        tokens.put("text", node.path("characters").asText(""));
        return tokens;
    }

    private void collectFigmaComponents(JsonNode node, List<JsonNode> components) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        String type = node.path("type").asText("");
        String name = node.path("name").asText("").toLowerCase();
        if ("COMPONENT".equals(type)
                || "COMPONENT_SET".equals(type)
                || "INSTANCE".equals(type)
                || ("FRAME".equals(type) && looksE2eRelevant(name))
                || ("TEXT".equals(type) && looksE2eRelevant(name))
                || looksE2eRelevant(name)) {
            components.add(node);
        }
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                collectFigmaComponents(child, components);
            }
        }
    }

    private void collectWebComponents(JsonNode node, List<JsonNode> components) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        String tag = node.path("tag").asText("").toLowerCase();
        boolean visible = node.path("visible").asBoolean(false);
        if (visible && WEB_E2E_TAGS.contains(tag)) {
            components.add(node);
        }
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                collectWebComponents(child, components);
            }
        }
    }

    private boolean looksE2eRelevant(String name) {
        return name.contains("button") || name.contains("input") || name.contains("field")
                || name.contains("form") || name.contains("link") || name.contains("select")
                || name.contains("checkbox") || name.contains("radio") || name.contains("modal")
                || name.contains("dialog") || name.contains("card");
    }

    private String componentName(JsonNode node, ComponentSourceEnum source) {
        String refinedName = node.path("uniqueName").asText("");
        if (!refinedName.isBlank()) {
            return refinedName;
        }
        if (source == ComponentSourceEnum.WEB) {
            return firstNonBlank(
                    node.path("attributes").path("data-testid").asText(null),
                    node.path("ariaLabel").asText(null),
                    node.path("name").asText(null),
                    node.path("tag").asText("component")
            );
        }
        return firstNonBlank(node.path("name").asText(null), node.path("id").asText(null), "component");
    }

    private String toPrettyJson(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to serialize extraction JSON", exception);
        }
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    private double pathDouble(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            current = current.path(part);
        }
        return current.isNumber() ? current.asDouble() : 0.0;
    }

    private int pathInt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            current = current.path(part);
        }
        return current.isNumber() ? current.asInt() : 0;
    }

    private int firstInt(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String figmaColor(JsonNode node) {
        JsonNode fills = node.path("fills");
        if (fills.isArray()) {
            for (JsonNode fill : fills) {
                if ("SOLID".equalsIgnoreCase(fill.path("type").asText()) && fill.path("visible").asBoolean(true)) {
                    JsonNode color = fill.path("color");
                    int r = (int) Math.round(color.path("r").asDouble(0) * 255);
                    int g = (int) Math.round(color.path("g").asDouble(0) * 255);
                    int b = (int) Math.round(color.path("b").asDouble(0) * 255);
                    return String.format("#%02X%02X%02X", Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
                }
            }
        }
        for (JsonNode child : node.path("children")) {
            String descendant = figmaColor(child);
            if (!descendant.isBlank()) {
                return descendant;
            }
        }
        return "";
    }

    private String figmaStrokeColor(JsonNode node) {
        JsonNode strokes = node.path("strokes");
        if (strokes.isArray()) {
            for (JsonNode stroke : strokes) {
                if ("SOLID".equalsIgnoreCase(stroke.path("type").asText()) && stroke.path("visible").asBoolean(true)) {
                    JsonNode color = stroke.path("color");
                    int r = (int) Math.round(color.path("r").asDouble(0) * 255);
                    int g = (int) Math.round(color.path("g").asDouble(0) * 255);
                    int b = (int) Math.round(color.path("b").asDouble(0) * 255);
                    return String.format("#%02X%02X%02X", Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
                }
            }
        }
        for (JsonNode child : node.path("children")) {
            String descendant = figmaStrokeColor(child);
            if (!descendant.isBlank()) {
                return descendant;
            }
        }
        return "";
    }

    private int descendantInt(JsonNode node, String... path) {
        JsonNode children = node.path("children");
        if (!children.isArray()) {
            return 0;
        }
        for (JsonNode child : children) {
            int direct = pathInt(child, path);
            if (direct > 0) {
                return direct;
            }
            int nested = descendantInt(child, path);
            if (nested > 0) {
                return nested;
            }
        }
        return 0;
    }

    private String descendantText(JsonNode node, String... path) {
        JsonNode children = node.path("children");
        if (!children.isArray()) {
            return "";
        }
        for (JsonNode child : children) {
            JsonNode current = child;
            for (String part : path) {
                current = current.path(part);
            }
            if (!current.asText("").isBlank()) {
                return current.asText("");
            }
            String nested = descendantText(child, path);
            if (!nested.isBlank()) {
                return nested;
            }
        }
        return "";
    }

    private record WebExtraction(JsonNode pageJson, byte[] screenshotPng) {}

    private static final String WEB_EXTRACTION_SCRIPT = """
            () => {
                function cssPath(element) {
                    if (element.id) return `#${CSS.escape(element.id)}`;
                    const parts = [];
                    let current = element;
                    while (current && current.nodeType === Node.ELEMENT_NODE && current !== document.documentElement) {
                        let selector = current.tagName.toLowerCase();
                        if (current.classList.length > 0) {
                            selector += "." + Array.from(current.classList).slice(0, 2).map(CSS.escape).join(".");
                        }
                        const parent = current.parentElement;
                        if (parent) {
                            const siblings = Array.from(parent.children).filter(child => child.tagName === current.tagName);
                            if (siblings.length > 1) selector += `:nth-of-type(${siblings.indexOf(current) + 1})`;
                        }
                        parts.unshift(selector);
                        current = parent;
                    }
                    return parts.join(" > ");
                }

                function xpath(element) {
                    if (element.id) return `//*[@id="${element.id}"]`;
                    const parts = [];
                    let current = element;
                    while (current && current.nodeType === Node.ELEMENT_NODE) {
                        let index = 1;
                        let sibling = current.previousElementSibling;
                        while (sibling) {
                            if (sibling.tagName === current.tagName) index++;
                            sibling = sibling.previousElementSibling;
                        }
                        parts.unshift(`${current.tagName.toLowerCase()}[${index}]`);
                        current = current.parentElement;
                    }
                    return "/" + parts.join("/");
                }

                function styleTokens(style) {
                    return {
                        color: style.color,
                        backgroundColor: style.backgroundColor,
                        borderColor: style.borderColor,
                        fontSize: style.fontSize,
                        fontWeight: style.fontWeight,
                        borderRadius: style.borderRadius,
                        padding: style.padding,
                        paddingTop: style.paddingTop,
                        paddingRight: style.paddingRight,
                        paddingBottom: style.paddingBottom,
                        paddingLeft: style.paddingLeft,
                        margin: style.margin,
                        marginTop: style.marginTop,
                        marginRight: style.marginRight,
                        marginBottom: style.marginBottom,
                        marginLeft: style.marginLeft,
                        gap: style.gap,
                        display: style.display,
                        opacity: style.opacity
                    };
                }

                function build(element, depth = 0) {
                    if (depth > 50) return null;
                    const rect = element.getBoundingClientRect();
                    const style = window.getComputedStyle(element);
                    const data = {
                        id: element.id || null,
                        tag: element.tagName.toLowerCase(),
                        name: element.getAttribute('aria-label') || element.textContent?.trim().substring(0, 80) || element.tagName.toLowerCase(),
                        class: element.className || null,
                        attributes: {},
                        text: element.childNodes.length === 1 && element.childNodes[0].nodeType === 3 ? element.textContent?.trim().substring(0, 200) : null,
                        position: { x: Math.round(rect.left), y: Math.round(rect.top) },
                        size: { width: Math.round(rect.width), height: Math.round(rect.height) },
                        cssSelector: element.id || cssPath(element),
                        locator: element.id || element.getAttribute('data-testid') || element.getAttribute('data-test') || cssPath(element),
                        xpath: xpath(element),
                        computedStyle: styleTokens(style),
                        visible: rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden',
                        ariaRole: element.getAttribute('role') || null,
                        ariaLabel: element.getAttribute('aria-label') || null,
                        children: []
                    };
                    ['type', 'name', 'placeholder', 'value', 'href', 'src', 'alt', 'title', 'data-testid', 'data-test'].forEach(attr => {
                        const value = element.getAttribute(attr);
                        if (value) data.attributes[attr] = value;
                    });
                    for (const child of element.children) {
                        const childData = build(child, depth + 1);
                        if (childData) data.children.push(childData);
                    }
                    return data;
                }
                return build(document.documentElement);
            }
            """;
}
