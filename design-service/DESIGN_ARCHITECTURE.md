# Design Implementation Architecture with Abstract Factory Pattern

## Overview

This document describes the design-to-web-page implementation workflow using the Abstract Factory design pattern in the figma-to-design microservice.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Design Implementation Flow                    │
└─────────────────────────────────────────────────────────────────┘

1. CREATE PROJECT
   ├─ Project Name
   ├─ Project URL
   ├─ Figma Project Name
   └─ Figma Project Key

    ↓

2. FETCH FIGMA PAGES
   ├─ Query by Project Name
   ├─ Retrieve Page List
   └─ Extract Page Metadata

    ↓

3. PARSE DESIGN STRUCTURE
   ├─ Extract Frames
   ├─ Extract Components
   └─ Map Component Properties

    ↓

4. ABSTRACT FACTORY LAYER
   │
   ├─→ FigmaComponentFactory
   │   ├─ createPage() → FigmaPageProduct
   │   ├─ createComponent() → FigmaComponent
   │   └─ createFrame() → FigmaFrameProduct
   │
   └─→ WebComponentFactory
       ├─ createPage() → WebPageProduct
       ├─ createComponent() → WebComponent
       └─ createFrame() → WebFrameProduct

    ↓

5. PAGE & URL TAGGING
   ├─ Generate URL: /pages/{project}/{page-name}
   ├─ Create Tag: {project}-page-{name}
   └─ Store Metadata

    ↓

6. WEB PAGE GENERATION
   ├─ Create TSX/HTML files
   ├─ Add to routes
   └─ Update navigation

    ↓

7. COMPONENT SYNCHRONIZATION
   ├─ Extract Figma Components
   ├─ Map to Web Components
   └─ Generate Component Files

    ↓

8. DESIGN SYNC COMPLETION
   └─ All Pages Tagged ✓
      All Components Synced ✓
      Design URL-mapped ✓
```

## Component Architecture

### Factory Pattern Implementation

#### 1. **ComponentFactory Interface** (Abstract Factory)
```java
interface ComponentFactory {
    Object createPage(PageCreationDto pageDto);
    Object createComponent(ComponentCreationDto componentDto);
    Object createFrame(FrameCreationDto frameDto);
    PlatformType getPlatformType();
}
```

#### 2. **FigmaComponentFactory** (Concrete Factory)
Creates Figma-specific domain objects:
- `FigmaPageProduct`: Represents a Figma page
- `FigmaFrameProduct`: Represents a Figma frame
- `FigmaComponent`: Entity for database persistence

```java
@Component
public class FigmaComponentFactory implements ComponentFactory {
    @Override
    public Object createPage(PageCreationDto pageDto) {
        return FigmaPageProduct.builder()
            .id("figma_" + pageDto.getName())
            .name(pageDto.getName())
            .figmaPageId(pageDto.getFigmaPageId())
            .figmaUrl(pageDto.getFigmaUrl())
            .build();
    }
    // ... other methods
}
```

#### 3. **WebComponentFactory** (Concrete Factory)
Creates Web-specific domain objects:
- `WebPageProduct`: Represents a web page with URL and tag
- `WebFrameProduct`: Represents a CSS container
- `WebComponent`: Entity for web components

```java
@Component
public class WebComponentFactory implements ComponentFactory {
    @Override
    public Object createPage(PageCreationDto pageDto) {
        String url = pageDto.getUrl() != null ? 
            pageDto.getUrl() : 
            "/pages/" + pageDto.getProjectName() + "/" + 
            pageDto.getName().toLowerCase().replace(" ", "-");
        
        return WebPageProduct.builder()
            .id("web_" + pageDto.getName())
            .name(pageDto.getName())
            .url(url)
            .tag(pageDto.getTag())
            .build();
    }
    // ... other methods
}
```

### Orchestrator Service

**DesignImplementationOrchestrator** coordinates the entire workflow:

```java
@Service
@Transactional
public class DesignImplementationOrchestrator {
    public ProjectDesignImplementationResult processProjectDesign(
            DesignImplementationRequest request) throws Exception {
        
        // Step 1: Create project
        Project project = createOrGetProject(request);
        
        // Step 2: Fetch Figma pages
        List<FigmaPageData> figmaPages = fetchFigmaPagesFromAPI(
            request.getFigmaProjectName()
        );
        
        // Step 3: Parse and tag pages
        List<PageWithMetadata> pagesWithMetadata = parseAndTagPages(
            figmaPages, 
            project.getName()
        );
        
        // Step 4: Create web pages using factory
        List<Page> webPages = pagesWithMetadata.stream()
            .map(pageData -> {
                WebComponentFactory.WebPageProduct webPageProduct = 
                    (WebComponentFactory.WebPageProduct) webFactory.createPage(
                        PageCreationDto.builder()
                            .name(pageData.getPageName())
                            .projectName(project.getName())
                            .url(pageData.getUrl())
                            .tag(pageData.getTag())
                            .build()
                    );
                // Save to database
            })
            .collect(Collectors.toList());
        
        // Step 5: Sync designs to implementation
        List<ComponentSyncResult> syncResults = syncDesignsToImplementation(
            figmaPages, 
            webPages, 
            project
        );
        
        return ProjectDesignImplementationResult.builder()
            .projectId(project.getId())
            .totalPages(webPages.size())
            .totalComponents(syncResults.size())
            .pages(webPages)
            .syncResults(syncResults)
            .status("SUCCESS")
            .build();
    }
}
```

## REST API Endpoint

### POST /api/v1/design/implementation/process

**Request Body:**
```json
{
  "projectName": "MyDesignProject",
  "projectUrl": "/my-design-project",
  "figmaProjectKey": 12345,
  "figmaProjectName": "MyDesignProject",
  "figmaApiKey": "figd_xxxxx"
}
```

**Response:**
```json
{
  "projectId": 1,
  "projectName": "MyDesignProject",
  "totalPages": 5,
  "totalComponents": 42,
  "pages": [
    {
      "id": 1,
      "name": "HomePage",
      "url": "/pages/mydesignproject/homepage",
      "project": { "id": 1, "name": "MyDesignProject" }
    },
    ...
  ],
  "syncResults": [
    {
      "pageId": 1,
      "pageName": "HomePage",
      "componentCount": 8,
      "status": "SYNCED"
    },
    ...
  ],
  "status": "SUCCESS"
}
```

## Database Schema

### Projects Table (Extended)
```sql
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL UNIQUE,
    figma_key INTEGER NOT NULL UNIQUE,
    link_minio VARCHAR(500),
    figma_project_name VARCHAR(255),
    figma_design_file_id VARCHAR(255),
    design_implementation_status VARCHAR(50),
    last_design_sync_at TIMESTAMP,
    is_design_synced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Pages Table
```sql
CREATE TABLE pages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Page Metadata (for URL tagging)
Each page stored with:
- **name**: Page name from Figma
- **url**: Generated web URL (e.g., `/pages/myproject/homepage`)
- **tag**: Unique identifier (e.g., `myproject-page-homepage`)
- **figmaPageId**: Reference to original Figma page

## Data Flow Example

**Input:** Figma project "Dashboard App" with pages: [HomePage, Dashboard, Settings]

**Processing:**
1. Create Project: `Dashboard App` → ID: 1
2. Fetch Figma pages: [HomePage, Dashboard, Settings]
3. Parse pages and tag:
   - HomePage → `/pages/dashboardapp/homepage` → `dashboardapp-page-homepage`
   - Dashboard → `/pages/dashboardapp/dashboard` → `dashboardapp-page-dashboard`
   - Settings → `/pages/dashboardapp/settings` → `dashboardapp-page-settings`
4. Create web pages in database
5. Extract components from each Figma page:
   - HomePage: 5 components
   - Dashboard: 12 components
   - Settings: 8 components
6. Sync components to web implementation

**Output:** Database records + Generated component files + Route entries

## Factory Registry

The **FactoryRegistry** manages factory instances:

```java
@Component
public class FactoryRegistry {
    public ComponentFactory getFactory(PlatformType platformType);
    public FigmaComponentFactory getFigmaFactory();
    public WebComponentFactory getWebFactory();
}
```

## Benefits of Abstract Factory Pattern

1. **Separation of Concerns**: Figma and Web creation logic are isolated
2. **Extensibility**: Easy to add new platforms (Mobile, Desktop, etc.)
3. **Consistency**: Both platforms follow the same interface
4. **Testability**: Each factory can be tested independently
5. **Maintainability**: Changes to one platform don't affect others
6. **Single Responsibility**: Each factory handles one platform only

## Implementation Checklist

- [x] Abstract Factory interface
- [x] FigmaComponentFactory implementation
- [x] WebComponentFactory implementation
- [x] Factory Registry
- [x] Orchestrator service
- [x] Design sync service
- [x] Controller endpoint
- [x] Database migrations
- [x] Data models
- [ ] Figma API integration
- [ ] Component file generation
- [ ] Route generation
- [ ] Unit tests
- [ ] Integration tests

## Future Enhancements

1. **Component Code Generation**: Generate TSX files from Figma components
2. **Style Extraction**: Convert Figma styles to Tailwind/CSS
3. **Asset Management**: Handle image and icon exports
4. **Version Control**: Track design changes and sync history
5. **Team Collaboration**: Manage permissions and design ownership
6. **Real-time Sync**: WebSocket updates for live design changes

## Error Handling

The orchestrator handles:
- Missing projects
- Invalid Figma credentials
- Network failures
- Database transaction rollbacks
- Component extraction errors

Each error is logged and returned with appropriate HTTP status codes.

## Performance Considerations

- **Batch Processing**: Pages and components processed in batches
- **Lazy Loading**: Components loaded on-demand
- **Caching**: Factory instances cached in registry
- **Transaction Management**: Transactional consistency ensured
- **Async Processing**: Long-running sync can be made async

## Security Notes

- Figma API keys stored securely in environment variables
- Database transactions ensure data consistency
- Input validation on all API endpoints
- Authorization checks for project access (to be implemented)
