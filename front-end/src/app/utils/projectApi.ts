import { api } from './api';

export interface Project {
  id: string;
  name: string;
  description: string;
  organizationId: string;
  status: string;
  figmaFileUrl?: string;
  baseUrl?: string;
  githubUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectPage {
  id: string;
  projectId: string;
  name: string;
  url?: string;
  path: string;
  source?: string;
  scanStatus?: string;
}

export const projectApi = {
  getProjects: () => 
    api.get<Project[]>('/api/v1/projects'),
    
  getProject: (id: string) => 
    api.get<Project>(`/api/v1/projects/${id}`),

  getPages: (id: string) =>
    api.get<ProjectPage[]>(`/api/v1/projects/${id}/pages`),
    
  createProject: (data: Partial<Project>) => 
    api.post<Project>('/api/v1/projects', data),
    
  updateProject: (id: string, data: Partial<Project>) => 
    api.put<Project>(`/api/v1/projects/${id}`, data),
    
  deleteProject: (id: string) => 
    api.delete<void>(`/api/v1/projects/${id}`)
};
