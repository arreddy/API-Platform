import axios from 'axios';

export const api = axios.create({
  baseURL: '/api/v1',
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  },
);

// ── APIs ─────────────────────────────────────────────────────────────────────
export const apisApi = {
  list: (params?: Record<string, unknown>) => api.get('/apis', { params }),
  get: (id: string) => api.get(`/apis/${id}`),
  getOas: (id: string, format?: 'json' | 'yaml') =>
    api.get(`/apis/${id}/oas`, { params: { format } }),
  create: (formData: FormData) => api.post('/apis', formData),
  update: (id: string, data: unknown) => api.put(`/apis/${id}`, data),
  delete: (id: string) => api.delete(`/apis/${id}`),
  validate: (formData: FormData) => api.post('/apis/validate', formData),
  getInsights: (id: string) => api.get(`/apis/${id}/insights`),
  analyze: (id: string) => api.post(`/apis/${id}/analyze`),
};

// ── Proxies ───────────────────────────────────────────────────────────────────
export const proxiesApi = {
  list: (params?: Record<string, unknown>) => api.get('/proxies', { params }),
  get: (id: string) => api.get(`/proxies/${id}`),
  create: (data: unknown) => api.post('/proxies', data),
  update: (id: string, data: unknown) => api.put(`/proxies/${id}`, data),
  delete: (id: string) => api.delete(`/proxies/${id}`),
  versions: (id: string) => api.get(`/proxies/${id}/versions`),
  rollback: (id: string, version: number) => api.post(`/proxies/${id}/rollback/${version}`),
};

// ── API Keys ──────────────────────────────────────────────────────────────────
export const keysApi = {
  list: (params?: Record<string, unknown>) => api.get('/keys', { params }),
  get: (id: string) => api.get(`/keys/${id}`),
  create: (data: unknown) => api.post('/keys', data),
  revoke: (id: string) => api.delete(`/keys/${id}`),
};

// ── Analytics ─────────────────────────────────────────────────────────────────
export const analyticsApi = {
  summary: (params?: Record<string, unknown>) => api.get('/analytics/summary', { params }),
  requests: (params?: Record<string, unknown>) => api.get('/analytics/requests', { params }),
};

// ── Auth ──────────────────────────────────────────────────────────────────────
export const authApi = {
  login: (email: string, password: string) => api.post('/auth/login', { email, password }),
  register: (data: unknown) => api.post('/auth/register', data),
  me: () => api.get('/auth/me'),
};
