
// Base backend URL fallback for local development: http://localhost:8080
// In cloud environments (e.g. Vercel), set VITE_API_URL or VITE_API_BASE_URL to your Render backend URL.
const rawUrl = (
  import.meta.env.VITE_API_URL ||
  import.meta.env.VITE_API_BASE_URL ||
  'http://localhost:8080'
).toString().trim().replace(/\/+$/, '');

export const API_BASE = rawUrl.endsWith('/api/v1') ? rawUrl : `${rawUrl}/api/v1`;

export function getStoredToken(): string | null {
  return (
    localStorage.getItem('fluxpay_token') ||
    localStorage.getItem('payflow_token')
  );
}

export function setStoredSession(token: string, sessionData: any) {
  localStorage.setItem('fluxpay_token', token);
  localStorage.setItem('fluxpay_session', JSON.stringify(sessionData));
}

export function clearStoredSession() {
  localStorage.removeItem('fluxpay_token');
  localStorage.removeItem('fluxpay_session');
  localStorage.removeItem('payflow_token');
  localStorage.removeItem('payflow_session');
}

export function getStoredSession(): any | null {
  const data =
    localStorage.getItem('fluxpay_session') ||
    localStorage.getItem('payflow_session');

  return data ? JSON.parse(data) : null;
}

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getStoredToken();
  const headers = new Headers(options.headers || {});
  const method = (options.method || 'GET').toUpperCase();

  // Only set Content-Type if there is a request body and method is not GET/HEAD
  if (method !== 'GET' && method !== 'HEAD' && !headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  // Only attach auth if token exists and endpoint is not public health or login
  if (token && !headers.has('Authorization') && !endpoint.includes('/health') && !endpoint.includes('/auth/login')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  if (!headers.has('X-Correlation-ID') && !endpoint.includes('/health')) {
    headers.set('X-Correlation-ID', crypto.randomUUID());
  }

  const normalizedEndpoint = endpoint.startsWith('/')
    ? endpoint
    : `/${endpoint}`;

  const response = await fetch(`${API_BASE}${normalizedEndpoint}`, {
    ...options,
    headers,
  });

  const json = await response.json().catch(() => null);

  if (!response.ok) {
    if (
      (response.status === 401 || response.status === 403) &&
      !endpoint.includes('/auth/login')
    ) {
      clearStoredSession();

      if (
        typeof window !== 'undefined' &&
        window.location.pathname !== '/login' &&
        window.location.pathname !== '/'
      ) {
        window.location.href = '/login?expired=true';
      }
    }

    const errorMsg =
      json?.error?.message ||
      json?.message ||
      `Request failed with status ${response.status}`;

    const err = new Error(errorMsg) as any;
    err.code = json?.error?.code || 'UNKNOWN_ERROR';
    err.status = response.status;
    err.correlationId = json?.meta?.correlationId;

    throw err;
  }

  return (json?.data !== undefined ? json.data : json) as T;
}
