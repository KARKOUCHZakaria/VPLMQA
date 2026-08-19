import { api } from "./api";

export interface TicketReportRequest {
  featureName?: string;
  featureId?: string;
  scenarioName?: string;
  scenarioId?: string;
  executionId?: string;
  action: "validated_failure" | "refused_positive";
  status?: string;
  steps: Array<{
    id?: string;
    keyword?: string;
    text?: string;
    status?: string;
    error?: string;
  }>;
  failedStep?: Record<string, unknown>;
  logs?: string;
  screenshots?: string[];
  errorMessage?: string;
  severity?: string;
}

export interface TicketReportResponse {
  title: string;
  description: string;
  severity?: string;
  expectedResult?: string;
  actualResult?: string;
  rootCauseHint?: string;
  provider?: string;
  model?: string;
  error?: string;
}

export const agentApi = {
  generateTicketReport: (data: TicketReportRequest) =>
    api.post<TicketReportResponse>("/api/v1/agents/part2/ticket-report", data),
};
