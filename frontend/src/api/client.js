const BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const TOKEN_KEY = 'cc_token'

export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors || {}
  }
}

function readToken() {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function saveToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // storage unavailable (private mode): the session lasts until reload
  }
}

let onUnauthorized = () => {}
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

/** fetch wrapper: adds the JWT, parses JSON, and turns RFC 7807 problem responses into ApiError. */
export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = readToken()
  if (auth && token) headers.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, "Can't reach the server. Is the backend running?")
  }

  const data = res.status === 204 ? null : await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401 && auth) onUnauthorized()
    throw new ApiError(res.status, data?.detail || `Request failed (${res.status})`, data?.errors)
  }
  return data
}

export const authApi = {
  register: (email, password) => api('/api/auth/register', { method: 'POST', body: { email, password }, auth: false }),
  login: (email, password) => api('/api/auth/login', { method: 'POST', body: { email, password }, auth: false }),
}

export const courseApi = {
  list: () => api('/api/courses'),
  create: (course) => api('/api/courses', { method: 'POST', body: course }),
  items: (courseId) => api(`/api/courses/${courseId}/items`),
  addItem: (courseId, item) => api(`/api/courses/${courseId}/items`, { method: 'POST', body: item }),
  rate: (itemId, rating) => api(`/api/items/${itemId}/rating`, { method: 'POST', body: { rating } }),
  projection: (courseId) => api(`/api/courses/${courseId}/projection`),
  predict: (courseId, features) => api(`/api/courses/${courseId}/predict`, { method: 'POST', body: features }),
  /** Resolves to null when the course has no prediction yet (409) instead of throwing. */
  recommendation: async (courseId) => {
    try {
      return await api(`/api/courses/${courseId}/recommendation`)
    } catch (e) {
      if (e.status === 409) return null
      throw e
    }
  },
}

export const hasToken = () => Boolean(readToken())
