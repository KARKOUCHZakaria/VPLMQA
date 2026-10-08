import { api } from "./api";

// This adapter keeps the E2E-focused frontend independent from the broader
// project-service payload, whose target URL is exposed as baseUrl.
export interface ProjectDto {
  id: string;
  name: string;
  url: string;
  description?: string | null;
}

export interface PageDto {
  id: string;
  projectId: string;
  name: string;
  url?: string | null;
  path?: string | null;
  source?: string | null;
}

interface ProjectServiceProject {
  id: string;
  name: string;
  description?: string | null;
  baseUrl?: string | null;
}

interface ProjectServicePage {
  id: string;
  projectId: string;
  name: string;
  url?: string | null;
  path?: string | null;
  source?: string | null;
}

const mapProject = (project: ProjectServiceProject): ProjectDto => ({
  id: project.id,
  name: project.name,
  url: project.baseUrl || "",
  description: project.description,
});

const mapPage = (page: ProjectServicePage): PageDto => ({
  id: page.id,
  projectId: page.projectId,
  name: page.name,
  url: page.url,
  path: page.path,
  source: page.source,
});

export const e2eProjectApi = {
  getProjects: async () => (await api.get<ProjectServiceProject[]>("/api/v1/projects")).map(mapProject),

  createProject: (payload: Pick<ProjectDto, "name" | "url" | "description">) =>
    api.post<ProjectServiceProject>("/api/v1/projects", {
      name: payload.name,
      description: payload.description || "",
      baseUrl: payload.url || "",
      organizationId: null,
    }).then(mapProject),

  updateProject: (id: string, payload: Pick<ProjectDto, "name" | "url" | "description">) =>
    api.put<ProjectServiceProject>(`/api/v1/projects/${id}`, {
      name: payload.name,
      description: payload.description || "",
      baseUrl: payload.url || "",
      organizationId: null,
    }).then(mapProject),

  deleteProject: (id: string) => api.delete<void>(`/api/v1/projects/${id}`),

  getProjectPages: async (projectId: string) =>
    (await api.get<ProjectServicePage[]>(`/api/v1/projects/${projectId}/pages`)).map(mapPage),

  createPage: (projectId: string, payload: Pick<PageDto, "name" | "url" | "path">) =>
    api.post<ProjectServicePage>(`/api/v1/projects/${projectId}/pages`, payload).then(mapPage),

  updatePage: (projectId: string, pageId: string, payload: Pick<PageDto, "name" | "url" | "path">) =>
    api.put<ProjectServicePage>(`/api/v1/projects/${projectId}/pages/${pageId}`, payload).then(mapPage),

  deletePage: (projectId: string, pageId: string) =>
    api.delete<void>(`/api/v1/projects/${projectId}/pages/${pageId}`),
};
