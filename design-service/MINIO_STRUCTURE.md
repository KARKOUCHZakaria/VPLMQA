# MinIO Hierarchical Storage Structure

## Overview
All design files and components are organized in MinIO following a hierarchical folder structure based on project name, page name, and components. This structure enables:
- Easy navigation and discovery
- Granular component access
- Scalable storage for complex designs
- Clean separation of concerns

## Folder Structure

```
/{projectName}/
├── design.json                           # Root design file with all pages info
└── pages/
    ├── {pageName1}/
    │   ├── page.json                     # Page-specific data
    │   └── components/
    │       ├── {componentName1}.json
    │       ├── {componentName2}.json
    │       └── {componentNameN}.json
    │
    ├── {pageName2}/
    │   ├── page.json
    │   └── components/
    │       ├── {componentName1}.json
    │       └── {componentNameN}.json
    │
    └── {pageNameN}/
        ├── page.json
        └── components/
            └── {componentName}.json
```

## File Details

### Root Design File: `/{projectName}/design.json`
- Contains complete Figma design import response
- Includes all pages and components metadata
- Used as a reference snapshot of the entire design
- Stored once per project during import

**Example Path**: `/my_project/design.json`

### Page File: `/{projectName}/pages/{pageName}/page.json`
- Contains page-specific data extracted from Figma
- Includes page ID, name, component count
- Links metadata pointing to component files in components/ subfolder

**Example Path**: `/my_project/pages/landing_page/page.json`

### Component Files: `/{projectName}/pages/{pageName}/components/{componentName}.json`
- Individual JSON file for each component on the page
- Contains complete component metadata:
  - `componentId`: Database ID
  - `nodeId`: Figma node ID
  - `name`: Component display name
  - `type`: ComponentType enum value
  - `nodeType`: Figma node type (Frame, Component, Text, etc.)
  - `position`: {x, y} coordinates
  - `size`: {width, height} dimensions
  - `sourceFileKey`: Figma file key
  - `sourceFileName`: Figma file name
  - `rawJson`: Raw Figma API response (optional)
  - `importedAt`: Timestamp of import
  - `createdAt`: File creation timestamp

**Example Path**: `/my_project/pages/landing_page/components/header_button.json`

**Example Component JSON**:
```json
{
  "componentId": 123,
  "nodeId": "456:789",
  "name": "Header Button",
  "type": "COMPONENT",
  "nodeType": "COMPONENT",
  "position": {
    "x": 10.5,
    "y": 20.3
  },
  "size": {
    "width": 100,
    "height": 40
  },
  "sourceFileKey": "abc123def456",
  "sourceFileName": "MyDesign",
  "importedAt": "2026-04-29T15:30:00",
  "createdAt": 1714423800000
}
```

## File Naming Rules

All folder and file names are sanitized using `FileNameSanitizer` utility with these rules:
- Convert to lowercase
- Replace spaces with underscores `_`
- Remove special characters (keep only: a-z, 0-9, hyphen, underscore)
- Handle unicode characters by removing them
- Collapse multiple underscores to single underscore
- Remove leading/trailing underscores
- Enforce maximum length of 200 characters
- Append `.json` extension for JSON files

### Sanitization Examples
| Input | Output |
|-------|--------|
| `"My Design File"` | `my_design_file` |
| `"Landing Page (v2)"` | `landing_page_v2` |
| `"Hero Button @ Mobile"` | `hero_button_mobile` |
| `"Button—Primary/Active"` | `button_primary_active` |
| `"组件 Component"` | `component` |
| `"Shopping Cart 🛒"` | `shopping_cart` |

## Service Architecture

### ComponentFileGenerator
**Purpose**: Convert FigmaComponent entities to JSON format for storage

**Key Methods**:
- `generateComponentJson(FigmaComponent component)` - Produces JSON string with all component metadata

**Data Classes**:
- `ComponentData` - Main component DTO with all metadata
- `PositionData` - X, Y coordinates
- `SizeData` - Width, height dimensions

### ComponentStorageService
**Purpose**: Orchestrate hierarchical storage of components in MinIO

**Key Methods**:
- `storeComponentsForPage(projectName, pageName, pageId)` - Store all page components
- `buildComponentPath(projectName, pageName, componentFileName)` - Generate MinIO path for component
- `buildPagePath(projectName, pageName)` - Generate MinIO path for page
- `buildDesignPath(projectName)` - Generate MinIO path for design root file

### PageFileGeneratorService (Enhanced)
**Purpose**: Generate page files and orchestrate entire import workflow

**Key Updates**:
1. Store design root file: `/{projectName}/design.json`
2. Generate hierarchical page paths: `/{projectName}/pages/{pageName}/page.json`
3. Call ComponentStorageService for each page to store components
4. Link Page entity to page.json file in MinIO
5. Include component count and import timestamp

**Workflow**:
```
1. Parse Figma design → extract pages and components
2. Save design.json at root
3. For each page:
   a. Generate page.json
   b. Save page entity
   c. Store all components for page in /components subfolder
```

## Access Patterns

### Retrieve Entire Design
```
GET /{projectName}/design.json
```
Returns: Complete FigmaDesignImportResponse with all pages and metadata

### Retrieve Specific Page Metadata
```
GET /{projectName}/pages/{pageName}/page.json
```
Returns: Page data with component references

### Retrieve Single Component
```
GET /{projectName}/pages/{pageName}/components/{componentName}.json
```
Returns: Complete component metadata and properties

### List All Components in a Page
```
LIST /{projectName}/pages/{pageName}/components/
```
Returns: All component files in the page folder

### List All Pages in a Project
```
LIST /{projectName}/pages/
```
Returns: All page folders in the project

## Database Integration

### Project Entity (`projects` table)
- `name` → Root folder name (sanitized as MinIO key)
- `linkMinIO` → Points to `/{projectName}/design.json`
- Stores project-level metadata and design source

### Page Entity (`pages` table)
- `name` → Page display name (VARCHAR 500)
- `url` → Generated page URL (VARCHAR 1000)
- `fileLink` → Exact MinIO path: `/{projectName}/pages/{pageName}/page.json`
- `figmaPageId` → Figma page identifier
- `componentCount` → Number of components extracted
- `importedAt` → Timestamp of import

### FigmaComponent Entity (`figma_components` table)
- `page_id` → Foreign key to Page entity
- `figmaNodeId` → Figma node identifier
- `figmaNodeName` → Component name (sanitized for filename)
- `positionX, positionY` → Component coordinates
- `nodeWidth, nodeHeight` → Component dimensions
- `sourceFileKey, sourceFileName` → Figma file references
- `rawJson` → Raw Figma API response (optional)

## Implementation Flow During Import

```
1. User initiates Figma design import
   ↓
2. FigmaComponentServiceImpl.importFromFigma() called
   ↓
3. PageFileGeneratorService.generatePagesFromDesign() executed
   ↓
4. Store /{projectName}/design.json (root level)
   ↓
5. For each Figma page:
   a. Sanitize page name
   b. Store /{projectName}/pages/{pageName}/page.json
   c. Save Page entity to get ID
   d. Call ComponentStorageService.storeComponentsForPage()
   ↓
6. ComponentStorageService processes components:
   a. Query all FigmaComponent entities for page
   b. For each component:
      - Generate JSON via ComponentFileGenerator
      - Sanitize component name
      - Store at /{projectName}/pages/{pageName}/components/{componentName}.json
   c. Return list of stored component paths
   ↓
7. Page entity updated with component count and fileLink
   ↓
8. Import complete - Entire hierarchy now stored in MinIO
```

## Benefits of Hierarchical Structure

✅ **Organized** - Clear hierarchy mirrors Figma design structure  
✅ **Scalable** - Efficiently handles designs with many pages and components  
✅ **Discoverable** - Easy to find, browse, or search for specific elements  
✅ **Maintainable** - Independent folders make updates and deletions straightforward  
✅ **Performance** - Components loaded on-demand without loading entire design  
✅ **Consistency** - Sanitized names prevent naming conflicts and errors  
✅ **Traceability** - Clear path structure shows design -> page -> component hierarchy  
✅ **Parallel Access** - Multiple components from same page can be fetched concurrently  

## Example Hierarchy After Import

For a project "Mobile App v3" with pages "Home", "Profile", "Settings":

```
/mobile_app_v3/
├── design.json                          # Mobile App v3 design export
└── pages/
    ├── home/
    │   ├── page.json                    # Home page metadata
    │   └── components/
    │       ├── navigation_bar.json
    │       ├── featured_banner.json
    │       ├── product_card.json
    │       ├── product_card_variant_2.json
    │       └── footer_links.json        # 5 components
    │
    ├── profile/
    │   ├── page.json
    │   └── components/
    │       ├── profile_header.json
    │       ├── user_stats.json
    │       ├── activity_feed.json
    │       └── edit_button.json         # 4 components
    │
    └── settings/
        ├── page.json
        └── components/
            ├── settings_menu.json
            ├── toggle_switch.json
            ├── save_button.json
            └── cancel_button.json       # 4 components
```

Total: 1 design file + 3 page files + 13 component files = 17 files organized hierarchically

