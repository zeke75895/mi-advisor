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
  const isForm = body instanceof FormData
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  const token = readToken()
  if (auth && token) headers.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
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
  /** These "latest" calls resolve to null (204) when nothing has been saved yet. */
  latestRisk: (courseId) => api(`/api/courses/${courseId}/risk/latest`),
  latestCheckIn: (courseId) => api(`/api/courses/${courseId}/check-in/latest`),
  notes: (courseId) => api(`/api/courses/${courseId}/notes`),
  saveNotes: (courseId, content) => api(`/api/courses/${courseId}/notes`, { method: 'PUT', body: { content } }),
  uploadNotesPdf: (courseId, file) => {
    const form = new FormData()
    form.append('file', file)
    return api(`/api/courses/${courseId}/notes/pdf`, { method: 'POST', body: form })
  },
  /** null (204) until the course has a risk check and at least one graded or rated item. */
  recommendation: (courseId) => api(`/api/courses/${courseId}/recommendation`),
  updateItem: (itemId, item) => api(`/api/items/${itemId}`, { method: 'PUT', body: item }),
  deleteItem: (itemId) => api(`/api/items/${itemId}`, { method: 'DELETE' }),
  extractItems: (courseId) => api(`/api/courses/${courseId}/notes/extract-items`, { method: 'POST' }),
  /** Spoken summary of the recommendation: script + base64 MP3. 503 when ElevenLabs isn't configured. */
  briefing: (courseId) => api(`/api/courses/${courseId}/briefing`, { method: 'POST' }),
}

// Generation uses the course's saved notes, so the request body is empty.
export const studyApi = {
  flashcards: (courseId) => api(`/api/materials/${courseId}/generate-flashcards`, { method: 'POST', body: {} }),
  questions: (courseId) => api(`/api/materials/${courseId}/generate-questions`, { method: 'POST', body: {} }),
  latestFlashcards: (courseId) => api(`/api/materials/${courseId}/flashcards`),
  latestQuestions: (courseId) => api(`/api/materials/${courseId}/questions`),
  plan: (body) => api('/api/study-plan/generate', { method: 'POST', body }),
  latestPlan: () => api('/api/study-plan/latest'),
}

// "today" is the browser's local date so weeks match the student's calendar.
export const sessionApi = {
  log: (courseId, session, today) =>
    api(`/api/courses/${courseId}/study-sessions?today=${today}`, { method: 'POST', body: session }),
  courseLog: (courseId, today) => api(`/api/courses/${courseId}/study-sessions?today=${today}`),
  summary: (today) => api(`/api/study-sessions/summary?today=${today}`),
  remove: (sessionId) => api(`/api/study-sessions/${sessionId}`, { method: 'DELETE' }),
}

export const featuresApi = {
  get: () => api('/api/features'),
}

export const modelApi = {
  info: () => api('/api/model-info'),
}

export const hasToken = () => Boolean(readToken())
