# MinIO Hierarchical Folder Structure

## Overview

The MinIO object storage now uses a hierarchical folder structure organized by project, page, and component. All folder and file names are automatically sanitized (lowercase, no special characters).

## Complete Folder Structure

```
{projectName}/                           # Project root folder
├── design.json                           # Complete project design with all pages/components metadata
├── pages/                                # Pages container folder
    ├── {pageName1}/                      # Page folder (sanitized page name)
    │   ├── page.json                     # Page metadata (name, id, componentCount, etc.)
    │   └── figma-components/             # Components container for this page
    │       ├── {componentName1}.json     # Individual component JSON (sanitized name)
    │       ├── {componentName2}.json
    │       └── {componentNameN}.json
    │
    ├── {pageName2}/
    │   ├── page.json
    │   └── figma-components/
    │       ├── {componentName1}.json
    │       └── {componentNameN}.json
    │
    └── {pageNameN}/
        ├── page.json
        └── figma-components/
            └── {componentName}.json
```

## Example Structure

For a project called "Mobile App" with pages "Dashboard" and "Settings":

```
mobile-app/                              # Sanitized project name
├── design.json                           # Root design file
├── pages/
    ├── dashboard/                        # Sanitized page name
    │   ├── page.json                     # Dashboard page metadata
    │   └── figma-components/             # Dashboard components
    │       ├── header-bar.json
    │       ├── user-card.json
    │       └── stats-widget.json
    │
    └── settings/                         # Settings page
        ├── page.json                     # Settings page metadata
        └── figma-components/             # Settings components
            ├── profile-form.json
            ├── privacy-toggle.json
            └── logout-button.json
```

## File Contents

### design.json (Project Root)
```json
{
  "projectName": "Mobile App",
  "pages": [
    {
      "id": 1,
      "name": "Dashboard",
      "pageName": "dashboard",
      "componentCount": 3,
      "fileLink": "mobile-app/pages/dashboard/page.json",
      "createdAt": "2026-04-29T10:00:00Z"
    },
    {
      "id": 2,
      "name": "Settings",
      "pageName": "settings",
      "componentCount": 3,
      "fileLink": "mobile-app/pages/settings/page.json",
      "createdAt": "2026-04-29T10:00:00Z"
    }
  ],
  "totalPages": 2,
  "totalComponents": 6,
  "createdAt": "2026-04-29T10:00:00Z"
}
```

### page.json (Page Metadata)
```json
{
  "pageId": 1,
  "pageName": "Dashboard",
  "projectName": "Mobile App",
  "componentCount": 3,
  "components": [
    {
      "id": 1,
      "figmaNodeId": "1:10",
      "figmaNodeName": "Header Bar",
      "type": "COMPONENT_SET",
      "position": { "x": 0, "y": 0 },
      "size": { "width": 320, "height": 64 },
      "fileLink": "mobile-app/pages/dashboard/figma-components/header-bar.json"
    },
    {
      "id": 2,
      "figmaNodeId": "1:11",
      "figmaNodeName": "User Card",
      "type": "COMPONENT",
      "position": { "x": 0, "y": 64 },
      "size": { "width": 320, "height": 100 },
      "fileLink": "mobile-app/pages/dashboard/figma-components/user-card.json"
    }
  ],
  "createdAt": "2026-04-29T10:00:00Z"
}
```

### {componentName}.json (Individual Component)
```json
{
  "componentId": 1,
  "figmaNodeId": "1:10",
  "figmaNodeName": "Header Bar",
  "type": "COMPONENT_SET",
  "pageId": 1,
  "pageName": "Dashboard",
  "projectName": "Mobile App",
  "position": {
    "x": 0,
    "y": 0
  },
  "size": {
    "width": 320,
    "height": 64
  },
  "properties": {
    "backgroundColor": "#FFFFFF",
    "borderRadius": 0
  },
  "children": [
    {
      "nodeId": "1:20",
      "name": "Title",
      "type": "TEXT",
      "properties": {
        "fontSize": 18,
        "fontWeight": "bold"
      }
    },
    {
      "nodeId": "1:21",
      "name": "Menu Button",
      "type": "BUTTON",
      "properties": {
        "onClick": "openMenu"
      }
    }
  ],
  "createdAt": "2026-04-29T10:00:00Z"
}
```

## API Endpoints

### Generate Design & Pages
**POST** `/api/v1/design/implementation/import`
- Uploads Figma design JSON
- Creates hierarchical structure automatically
- Stores: `{projectName}/design.json` + `{projectName}/pages/{pageName}/page.json`

### Generate Components from Page
**POST** `/api/v1/design/implementation/generate-components-from-page`
- Generates individual component JSONs from page entity
- Stores at: `{projectName}/pages/{pageName}/figma-components/{componentName}.json`

**Request:**
```json
{
  "pageId": 1
}
```

**Response:**
```json
{
  "pageId": 1,
  "componentsGenerated": 3,
  "componentPaths": [
    "mobile-app/pages/dashboard/figma-components/header-bar.json",
    "mobile-app/pages/dashboard/figma-components/user-card.json",
    "mobile-app/pages/dashboard/figma-components/stats-widget.json"
  ],
  "message": "3 components generated from page 1"
}
```

### Populate Components to Database
**POST** `/api/v1/design/implementation/populate-components`
- Reads page JSON from MinIO
- Creates FigmaComponent database entries
- Enables component retrieval and management

**Request:**
```json
{
  "pageId": 1
}
```

**Response:**
```json
{
  "pageId": 1,
  "componentsPopulated": 3,
  "message": "3 components populated from page JSON"
}
```

## Path Building

All paths are built using the `FileNameSanitizer` utility:

```java
// Design file path
String designPath = FileNameSanitizer.generateObjectName(
    projectName,        // e.g., "mobile-app"
    "design.json"       // Result: "mobile-app/design.json"
);

// Page file path
String pagePath = FileNameSanitizer.generateObjectName(
    projectName,        // e.g., "mobile-app"
    "pages",
    pageName,           // e.g., "dashboard"
    "page.json"         // Result: "mobile-app/pages/dashboard/page.json"
);

// Component file path
String componentPath = FileNameSanitizer.generateObjectName(
    projectName,        // e.g., "mobile-app"
    "pages",
    pageName,           // e.g., "dashboard"
    "figma-components",
    componentFileName,  // e.g., "header-bar.json"
    "json"              // Result: "mobile-app/pages/dashboard/figma-components/header-bar.json"
);
```

## File Name Sanitization

All names are automatically sanitized using `FileNameSanitizer`:

- Converted to lowercase
- Special characters removed
- Unicode characters converted to ASCII equivalents
- Max 200 characters per component name
- Examples:
  - "Header Bar" → "header-bar"
  - "User@Component" → "usercomponent"
  - "Dashboard_v2.1" → "dashboard_v2.1"

## Storage Services

### ComponentStorageService
- **Purpose:** Store individual components
- **Key Method:** `buildComponentPath(projectName, pageName, componentFileName)`
- **Output:** `{projectName}/pages/{pageName}/figma-components/{componentFileName}.json`

### FigmaPageJsonParser
- **Purpose:** Parse page JSON and extract components
- **Key Method:** `buildComponentPath(projectName, pageName, componentName)`
- **Output:** `{projectName}/pages/{pageName}/figma-components/{componentName}.json`

### PageFileGeneratorService
- **Purpose:** Generate design.json and page.json files
- **Key Methods:**
  - `buildDesignPath(projectName)` → `{projectName}/design.json`
  - `buildPagePath(projectName, pageName)` → `{projectName}/pages/{pageName}/page.json`

## Migration from Old Structure

Old structure (deprecated):
```
designs/project_name.json
```

New structure:
```
{projectName}/design.json
{projectName}/pages/{pageName}/page.json
{projectName}/pages/{pageName}/figma-components/{componentName}.json
```

Use the migration endpoints to update existing pages:
- **POST** `/api/v1/design/implementation/migrate-page-paths` - Migrate single page
- **POST** `/api/v1/design/implementation/migrate-all-page-paths` - Migrate all pages
- **GET** `/api/v1/design/implementation/page-path-migration-status` - Check status

## Benefits

✅ **Organized & Scalable** - Clear hierarchy for multiple projects/pages
✅ **Independent Access** - Fetch specific components without loading entire design
✅ **Version Control** - Easy to track changes per component
✅ **Parallel Processing** - Store components concurrently
✅ **Consistent Naming** - Automatic sanitization prevents naming conflicts
✅ **Database Integration** - Components linked to database entities
✅ **Metadata Rich** - Each level (design, page, component) has metadata JSON
