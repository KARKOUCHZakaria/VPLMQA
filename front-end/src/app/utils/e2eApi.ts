import { api } from './api';

// Centralized E2E API contract used by the feature editor and test execution
// screens. Secret values are deliberately never returned to the browser.
export interface Feature {
  id: string;
  name: string;
  description: string;
  gherkinContent: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  projectId?: string;
  defaultPageId?: string;
  targetMode: 'PROJECT' | 'EXTERNAL';
}

export interface Scenario {
  id: string;
  featureId: string;
  name: string;
  description: string;
  status: string;
  sequenceOrder: number;
}

export interface Step {
  id: string;
  scenarioId: string;
  type: string;
  text: string;
  status: string;
  sequenceOrder: number;
}

export interface TestExecution {
  id: string;
  featureId: string;
  scenarioId: string | null;
  status: string;
  executionTimeMs: number;
  outputLog: string;
  errorMessage: string;
  screenshots: string;
  createdAt: string;
}

export interface ScenarioWithSteps extends Scenario {
  steps: Step[];
}

export interface FeatureWithHierarchy extends Feature {
  scenarios: ScenarioWithSteps[];
}

// Feature lifecycle
export const getFeatures = async () => {
  return api.get<{ content?: Feature[] }>('/api/v1/features?size=100');
};

export const getFeatureWithHierarchy = async (id: string) => {
  return api.get<FeatureWithHierarchy>(`/api/v1/features/${id}/full`);
};

export const getFeatureExecutions = async (featureId: string) => {
  return api.get<TestExecution[]>(`/api/v1/executions?featureId=${encodeURIComponent(featureId)}`);
};

export const createFeature = async (data: Partial<Feature>) => {
  return api.post<Feature>('/api/v1/features', data);
};

export const updateFeature = async (id: string, data: Partial<Feature>) => {
  return api.put<Feature>(`/api/v1/features/${id}`, data);
};

export const runFeatureAgentPipeline = async (featureId: string) => {
  return api.post<Record<string, any>>(`/api/v1/features/${featureId}/run-agent`);
};

export const listProjectSecrets = async (projectId: string) => {
  return api.get<{ aliases: string[] }>(`/api/v1/e2e/projects/${projectId}/secrets`);
};

export const saveProjectSecret = async (projectId: string, alias: string, value: string) => {
  return api.put<{ alias: string; reference: string }>(
    `/api/v1/e2e/projects/${projectId}/secrets/${encodeURIComponent(alias)}`,
    { value }
  );
};

export const exportFeatureGherkin = async (id: string) => {
  return api.get<string>(`/api/v1/features/${id}/export-gherkin`);
};

export const deleteFeature = async (id: string) => {
  return api.delete<void>(`/api/v1/features/${id}`);
};

// Scenario lifecycle
export const createScenario = async (featureId: string, data: Partial<Scenario>) => {
  return api.post<Scenario>(`/api/v1/features/${featureId}/scenarios`, data);
};

export const updateScenario = async (id: string, data: Partial<Scenario>) => {
  return api.put<Scenario>(`/api/v1/scenarios/${id}`, data);
};

export const deleteScenario = async (id: string) => {
  return api.delete<void>(`/api/v1/scenarios/${id}`);
};

// Step lifecycle
export const createStep = async (scenarioId: string, data: Partial<Step>) => {
  return api.post<Step>(`/api/v1/scenarios/${scenarioId}/steps`, data);
};

export const updateStep = async (id: string, data: Partial<Step>) => {
  return api.put<Step>(`/api/v1/steps/${id}`, data);
};

export const deleteStep = async (id: string) => {
  return api.delete<void>(`/api/v1/steps/${id}`);
};

// Execution is delegated to the LangGraph endpoint through the gateway.
export const runTestExecution = async (featureId: string) => {
  return api.post<{ run_id: string; status: string; feature_id: string }>('/api/v1/agents/part2/run', {
    feature_id: featureId,
  });
};
