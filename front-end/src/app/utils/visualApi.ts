import { api } from './api';

export interface VisualCompareRequest {
  figmaImageBase64: string;
  webImageBase64: string;
  pageName?: string;
  pageUrl?: string;
  projectId?: string;
  figmaImageFormat?: string;
  webImageFormat?: string;
}

export const visualApi = {
  compareBase64: (payload: VisualCompareRequest) =>
    api.post("/api/v1/visual/compare/base64", payload),
};

export default visualApi;
