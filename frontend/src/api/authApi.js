const API_BASE =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

let accessToken = null
let onUnauthorized = null

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

export function setAccessToken(token) {
  accessToken = token
}

export function clearAccessToken() {
  accessToken = null
}

export async function apiRequest(path, options = {}) {
  const headers = new Headers(options.headers ?? {})

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  if (!response.ok) {
    let message = `La solicitud falló (${response.status})`

    try {
      const body = await response.json()
      message = body.message ?? body.detail ?? message
    } catch {
    }

    if (response.status === 401) {
      clearAccessToken()
      onUnauthorized?.()
    }
    throw new Error(message)
  }

  if (response.status === 204) {
    return null
  }

  return response.json()
}

export function registerUser({ name, email, password }) {
  return apiRequest('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name, email, password }),
  })
}

export function loginUser({ email, password }) {
  return apiRequest('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function getCurrentUser() {
  return apiRequest('/users/me')
}

export function getAdminSummary() {
  return apiRequest('/admin/summary')
}
