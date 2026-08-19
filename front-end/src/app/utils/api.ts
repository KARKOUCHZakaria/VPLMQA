const configuredGatewayUrl = import.meta.env.VITE_GATEWAY_URL || window.location.origin;
export const GATEWAY_URL = configuredGatewayUrl.replace(/\/+$/, "");

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp?: string;
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

const hasApiEnvelope = <T>(value: unknown): value is ApiResponse<T> => {
  return Boolean(
    value &&
      typeof value === 'object' &&
      'success' in value &&
      typeof (value as ApiResponse<T>).success === 'boolean' &&
      'data' in value
  );
};

const parseErrorMessage = async (response: Response) => {
  const fallback = response.statusText || `HTTP ${response.status}`;
  try {
    const data = await response.clone().json();
    return data?.message || data?.detail || data?.error || fallback;
  } catch (_) {
    try {
      const text = await response.text();
      return text || fallback;
    } catch (_) {
      return fallback;
    }
  }
};

export async function request<T>(
  path: string,
  method: HttpMethod = 'GET',
  body?: any
): Promise<T> {
  const token = localStorage.getItem('accessToken');
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  
  if (token && !path.includes('/auth/login') && !path.includes('/auth/register')) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = `${GATEWAY_URL}${normalizedPath}`;
  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (error) {
    const reason = error instanceof Error ? error.message : 'Network request failed';
    throw new Error(
      `Gateway/API is not reachable at ${GATEWAY_URL}. Wait until the launcher log says "ALL SERVICES ARE READY", then refresh the app. Details: ${reason}`
    );
  }

  if (!response.ok) {
    if (response.status === 401 && token && !path.includes('/auth/login')) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('vplmqa.currentUserEmail');
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
      throw new Error('Session expired. Please log in again.');
    }
    throw new Error(await parseErrorMessage(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    return (await response.text()) as T;
  }

  const result = await response.json();
  if (hasApiEnvelope<T>(result)) {
    if (!result.success) {
      throw new Error(result.message || 'Request failed');
    }
    return result.data;
  }

  return result as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path, 'GET'),
  post: <T>(path: string, body?: any) => request<T>(path, 'POST', body),
  put: <T>(path: string, body?: any) => request<T>(path, 'PUT', body),
  patch: <T>(path: string, body?: any) => request<T>(path, 'PATCH', body),
  delete: <T>(path: string) => request<T>(path, 'DELETE'),
};
