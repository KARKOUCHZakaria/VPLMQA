import { api } from './api';

export interface ProjectDto {
  id: string;
  name: string;
  url: string;
  figmaKey: number;
  description?: string | null;
  figmaProjectName?: string | null;
  figmaDesignFileId?: string | null;
  designImplementationStatus?: string | null;
  lastDesignSyncAt?: string | null;
  isDesignSynced?: boolean;
  linkMinIO?: string | null;
}

export interface PageDto {
  id: string;
  projectId: string;
  name: string;
  url?: string | null;
  path?: string | null;
  figmaPageId?: string | null;
  fileLink?: string | null;
  componentCount?: number | null;
  importedAt?: string | null;
  scanStatus?: string | null;
  source?: 'FIGMA' | 'WEB' | string;
  figmaObjectPath?: string | null;
  webObjectPath?: string | null;
  project?: ProjectDto;
}

export interface WebComponentDto {
  id: string;
  componentName: string;
  htmlTag: string;
  htmlID?: string | null;
  htmlClass?: string | null;
  textContent?: string | null;
  cssSelector: string;
  fileLink?: string | null;
  functionalRole?: string | null;
  testIdentifier?: string | null;
  mappingStatus?: string | null;
  importedAt?: string | null;
  source?: string;
  cssProperties?: Record<string, unknown>;
  boundingBox?: Record<string, unknown>;
  page?: PageDto;
}

export interface ExtractionArtifactDto {
  projectId: string;
  pageId: string;
  pageName: string;
  source: 'FIGMA' | 'WEB' | string;
  pageObjectPath: string;
  componentCount: number;
  componentObjectPaths: string[];
}

export interface MlDatasetDto {
  projectId: string;
  rowCount: number;
  jsonObjectPath: string;
  csvObjectPath: string;
  rows: Record<string, unknown>[];
}

export interface DesignPredictionDto {
  index: number;
  component: string;
  modelComponent: string;
  match: boolean;
  matchProbability: number;
  rawModelMatch?: boolean;
  rawModelProbability?: number;
  mappingConfidence?: number;
}

export interface VisualDifferenceDto {
  component?: string;
  category?: string;
  figma?: string;
  web?: string;
  explanation?: string;
  figmaRegion?: VisualDifferenceRegionDto | null;
  webRegion?: VisualDifferenceRegionDto | null;
}

export interface VisualDifferenceRegionDto {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface PageComparisonDto {
  projectId: string;
  pageId: string;
  pageName: string;
  generatedAt: string;
  figmaImage: string;
  webImage: string;
  figmaImagePath: string;
  webImagePath: string;
  resultObjectPath: string;
  rows: Record<string, any>[];
  predictions: DesignPredictionDto[];
  explanation?: {
    summary?: string;
    severity?: string;
    provider?: string;
    warning?: string;
    visualDifferences?: VisualDifferenceDto[];
    recommendations?: string[];
  } | null;
}

interface ProjectServiceProject {
  id: string;
  name: string;
  description?: string | null;
  figmaFileUrl?: string | null;
  baseUrl?: string | null;
  status?: string | null;
  updatedAt?: string | null;
}

interface ProjectServicePage {
  id: string;
  projectId: string;
  name: string;
  url?: string | null;
  path?: string | null;
  lastScannedAt?: string | null;
  scanStatus?: string | null;
  source?: string;
  figmaObjectPath?: string | null;
  webObjectPath?: string | null;
}

interface ProjectServiceComponent {
  id: string;
  pageId: string;
  canonicalName: string;
  semanticRole?: string | null;
  functionalMeaning?: string | null;
  htmlId?: string | null;
  figmaNodeId?: string | null;
  testIdentifier?: string | null;
  cssSelector?: string | null;
  xpath?: string | null;
  source?: string;
  status?: string;
  cssProperties?: Record<string, unknown>;
  boundingBox?: Record<string, unknown>;
  screenshot?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

const mapProject = (project: ProjectServiceProject): ProjectDto => ({
  id: project.id,
  name: project.name,
  url: project.baseUrl || '',
  figmaKey: 0,
  description: project.description,
  figmaProjectName: project.name,
  figmaDesignFileId: project.figmaFileUrl,
  designImplementationStatus: project.status,
  lastDesignSyncAt: project.updatedAt,
  isDesignSynced: project.status === 'ACTIVE',
});

const mapPage = (page: ProjectServicePage, project?: ProjectDto): PageDto => ({
  id: page.id,
  projectId: page.projectId,
  name: page.name,
  url: page.url,
  path: page.path,
  fileLink: page.scanStatus,
  importedAt: page.lastScannedAt,
  scanStatus: page.scanStatus,
  source: page.source,
  figmaObjectPath: page.figmaObjectPath,
  webObjectPath: page.webObjectPath,
  project,
});

const mapComponent = (component: ProjectServiceComponent, page?: PageDto): WebComponentDto => ({
  id: component.id,
  componentName: component.canonicalName,
  htmlTag: component.source || 'component',
  htmlID: component.htmlId,
  cssSelector: component.cssSelector || (component.htmlId ? `#${component.htmlId}` : ''),
  testIdentifier: component.testIdentifier || component.htmlId || component.figmaNodeId || undefined,
  functionalRole: component.semanticRole || component.functionalMeaning,
  mappingStatus: component.status,
  importedAt: component.updatedAt || component.createdAt,
  source: component.source,
  cssProperties: component.cssProperties,
  boundingBox: component.boundingBox,
  page,
});

const getProjects = async () => {
  const projects = await api.get<ProjectServiceProject[]>('/api/v1/projects');
  return projects.map(mapProject);
};

const getPages = async () => {
  const projects = await getProjects();
  const pages = await Promise.all(
    projects.map(async (project) => {
      const projectPages = await api.get<ProjectServicePage[]>(`/api/v1/projects/${project.id}/pages`);
      return projectPages.map((page) => mapPage(page, project));
    })
  );
  return pages.flat();
};

const getProjectPages = async (project: ProjectDto) => {
  const pages = await api.get<ProjectServicePage[]>(`/api/v1/projects/${project.id}/pages`);
  return pages.map((page) => mapPage(page, project));
};

const getPageComponents = async (page: PageDto) => {
  const components = await api.get<ProjectServiceComponent[]>(
    `/api/v1/projects/${page.projectId}/components/page/${page.id}`
  );
  return components.map((component) => mapComponent(component, page));
};

const pathFromUrl = (url?: string | null) => {
  if (!url) {
    return '/login';
  }
  try {
    return new URL(url).pathname || '/';
  } catch {
    return url.startsWith('/') ? url : '/login';
  }
};

export const designApi = {
  getProjects,
  createProject: (payload: Partial<ProjectDto>) =>
    api.post<ProjectServiceProject>('/api/v1/projects', {
      name: payload.name,
      description: payload.description || '',
      figmaFileUrl: payload.figmaDesignFileId || '',
      figmaTokenEncrypted: payload.figmaProjectName || '',
      baseUrl: payload.url || '',
      organizationId: null,
    }).then(mapProject),
  updateProject: (id: string, payload: Partial<ProjectDto>) =>
    api.put<ProjectServiceProject>(`/api/v1/projects/${id}`, {
      name: payload.name,
      description: payload.description || '',
      figmaFileUrl: payload.figmaDesignFileId || '',
      figmaTokenEncrypted: payload.figmaProjectName || '',
      baseUrl: payload.url || '',
      organizationId: null,
    }).then(mapProject),
  getPages,
  getProjectPages,
  createPage: (payload: Partial<PageDto>, projectId?: string) => {
    if (!projectId) {
      return Promise.reject(new Error('Project is required to create a page.'));
    }
    return api.post<ProjectServicePage>(`/api/v1/projects/${projectId}/pages`, {
      name: payload.name,
      url: payload.url,
      path: payload.path || pathFromUrl(payload.url),
    }).then((page) => mapPage(page));
  },
  getWebComponents: async () => {
    const pages = await getPages();
    const components = await Promise.all(
      pages.map(async (page) => {
        return getPageComponents(page);
      })
    );
    return components.flat();
  },
  getPageComponents,
  storeFigmaPage: (payload: { projectId: string; pageId: string; pageJson: unknown }) =>
    api.post<ExtractionArtifactDto>(`/api/v1/projects/${payload.projectId}/extraction/figma/pages/${payload.pageId}`, {
      pageJson: payload.pageJson,
    }),
  storeFigmaDesign: (payload: { projectId: string; designJson: unknown }) =>
    api.post<ExtractionArtifactDto>(`/api/v1/projects/${payload.projectId}/extraction/figma/design`, {
      pageJson: payload.designJson,
    }),
  extractFigmaDesign: (payload: { projectId: string }) =>
    api.post<ExtractionArtifactDto>(`/api/v1/projects/${payload.projectId}/extraction/figma/design/extract`),
  extractFigmaComponents: (payload: { projectId: string; pageId: string }) =>
    api.post<ExtractionArtifactDto>(`/api/v1/projects/${payload.projectId}/extraction/figma/pages/${payload.pageId}/components`),
  extractWebPage: (payload: { projectId: string; pageId: string }) =>
    api.post<ExtractionArtifactDto>(`/api/v1/projects/${payload.projectId}/extraction/web/pages/${payload.pageId}`),
  generateMlDataset: (payload: { projectId: string; pageId?: string }) => {
    const query = payload.pageId ? `?pageId=${encodeURIComponent(payload.pageId)}` : '';
    return api.post<MlDatasetDto>(`/api/v1/design-token-comparison/projects/${payload.projectId}/dataset${query}`);
  },
  comparePage: (payload: { projectId: string; pageId: string; refresh?: boolean }) =>
    api.post<PageComparisonDto>(
      `/api/v1/design-token-comparison/projects/${payload.projectId}/compare?pageId=${encodeURIComponent(payload.pageId)}&refresh=${Boolean(payload.refresh)}`
    ),
  extractDesign: (_payload?: unknown) =>
    Promise.reject(new Error('Figma extraction now belongs to project-service and expects a page JSON payload.')),
  extractPages: (_payload?: unknown) =>
    Promise.reject(new Error('Page extraction now belongs to project-service. Create pages with the project API.')),
  extractComponents: (_payload?: unknown) =>
    Promise.reject(new Error('Figma component extraction now uses storeFigmaPage with a page JSON payload.')),
  createWebPage: (payload: { url: string; tagName: string; projectId: string }) =>
    api.post<ProjectServicePage>(`/api/v1/projects/${payload.projectId}/pages`, {
      name: payload.tagName,
      url: payload.url,
      path: pathFromUrl(payload.url),
    }).then((page) => mapPage(page)),
  extractWebDesign: (payload: { projectId: string; pageId: string }) =>
    api.post<ExtractionArtifactDto>(`/api/v1/projects/${payload.projectId}/extraction/web/pages/${payload.pageId}`),
  extractWebComponents: (payload: { projectId: string; pageId: string }) =>
    api.post<ExtractionArtifactDto>(`/api/v1/projects/${payload.projectId}/extraction/web/pages/${payload.pageId}`),
  captureWebScreenshot: (_payload?: unknown) =>
    Promise.reject(new Error('Screenshot capture was removed from design-service extraction.')),
  exportAllPagesPng: (_payload?: unknown) =>
    Promise.reject(new Error('PNG export was removed from design-service extraction.')),
  exportFigmaPagePng: (_payload?: unknown) =>
    Promise.reject(new Error('Figma PNG export was removed from design-service extraction.')),
};
