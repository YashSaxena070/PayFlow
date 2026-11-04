const API_BASE = '/api/v1';

export function getStoredToken(): string | null {
  return localStorage.getItem('payflow_token');
}

export function setStoredSession(token: string, sessionData: any) {
  localStorage.setItem('payflow_token', token);
  localStorage.setItem('payflow_session', JSON.stringify(sessionData));
}

export function clearStoredSession() {
  localStorage.removeItem('payflow_token');
  localStorage.removeItem('payflow_session');
}

export function getStoredSession(): any | null {
  const data = localStorage.getItem('payflow_session');
  return data ? JSON.parse(data) : null;
}

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getStoredToken();
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  if (!headers.has('X-Correlation-ID')) {
    headers.set('X-Correlation-ID', crypto.randomUUID());
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  const json = await response.json().catch(() => null);

  if (!response.ok) {
    const errorMsg = json?.error?.message || json?.message || `Request failed with status ${response.status}`;
    const err = new Error(errorMsg) as any;
    err.code = json?.error?.code || 'UNKNOWN_ERROR';
    err.status = response.status;
    err.correlationId = json?.meta?.correlationId;
    throw err;
  }

  return json.data !== undefined ? json.data : json;
}

