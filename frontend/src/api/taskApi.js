import { apiRequest } from './authApi'

export function getTasks() {
  return apiRequest('/tasks')
}

export function getTask(id) {
  return apiRequest(`/tasks/${id}`)
}

export function createTask(task) {
  return apiRequest('/tasks', {
    method: 'POST',
    body: JSON.stringify(task),
  })
}

export function updateTask(id, task) {
  return apiRequest(`/tasks/${id}`, {
    method: 'PUT',
    body: JSON.stringify(task),
  })
}

export function deleteTask(id) {
  return apiRequest(`/tasks/${id}`, {
    method: 'DELETE',
  })
}
