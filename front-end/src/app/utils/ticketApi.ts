import { api } from './api';

export interface Ticket {
  id: string;
  projectId: string;
  componentId?: string;
  title: string;
  description: string;
  severity: string;
  status: string;
  comparisonResultId?: string;
  testExecutionId?: string;
  componentCanonicalName?: string;
  componentHtmlId?: string;
  assignedTo?: string;
  azureWorkItemId?: number;
  azureWorkItemUrl?: string;
  azureSyncStatus?: string;
  azureSyncError?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AzureDevOpsConnection {
  projectId: string;
  organization: string;
  azureProject: string;
  workItemType: string;
  areaPath: string;
  enabled: boolean;
  credentialStored: boolean;
}

export interface AzureDevOpsMember {
  id: string;
  displayName: string;
  email: string;
  uniqueName: string;
}

export const ticketApi = {
  createTicket: (data: Partial<Ticket>) => 
    api.post<Ticket>('/api/v1/tickets', data),
    
  getTicket: (id: string) => 
    api.get<Ticket>(`/api/v1/tickets/${id}`),
    
  listProjectTickets: (projectId: string, status?: string, severity?: string) => {
    let url = `/api/v1/tickets/projects/${projectId}`;
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (severity) params.append('severity', severity);
    if (params.toString()) url += `?${params.toString()}`;
    return api.get<Ticket[]>(url);
  },

  getAzureDevOpsConnection: (projectId: string) =>
    api.get<AzureDevOpsConnection>(`/api/v1/tickets/integrations/azure-devops/projects/${projectId}`),

  saveAzureDevOpsConnection: (
    projectId: string,
    data: Partial<AzureDevOpsConnection> & { personalAccessToken?: string }
  ) => api.put<AzureDevOpsConnection>(
    `/api/v1/tickets/integrations/azure-devops/projects/${projectId}`,
    data
  ),

  getAzureDevOpsMembers: (projectId: string) =>
    api.get<AzureDevOpsMember[]>(`/api/v1/tickets/integrations/azure-devops/projects/${projectId}/members`),

  testAzureDevOpsConnection: (projectId: string) =>
    api.get<{ connected: boolean; memberCount: number }>(
      `/api/v1/tickets/integrations/azure-devops/projects/${projectId}/test`
    ),
};
