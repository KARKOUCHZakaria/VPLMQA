import { api } from './api';

export interface MetricsResponse {
  mismatchRate: number;
  testPassRate: number;
  tokenCoverage: number;
  healthScore: number;
}

export const analyticsApi = {
  getProjectMetrics: (projectId: string) => 
    api.get<MetricsResponse>(`/api/v1/analytics/projects/${projectId}/metrics`),
    
  getHealthScore: (projectId: string) => 
    api.get<number>(`/api/v1/analytics/projects/${projectId}/health-score`)
};
