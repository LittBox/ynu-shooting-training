import { request } from './request.js'
export const trainingService = {
  coachDays: (page = 0) => request(`/api/coach/training/days?page=${page}`),
  coachDay: date => request(`/api/coach/training/days/${encodeURIComponent(date)}`),
  coachRecords: (search = '', page = 0) => request(`/api/coach/training?search=${encodeURIComponent(search)}&page=${page}`),
  coachDetail: id => request(`/api/coach/training/${id}`),
  correct: (id, row, data) => request(`/api/coach/training/${id}/${row.legacy ? 'legacy-scores' : 'attempts'}/${row.id}`, { method: 'PUT', data }),
  results: id => request(`/api/training/${id}/results`),
  register: (id, data) => request(`/api/training/${id}/attempts`, { method: 'POST', data }),
  history: () => request('/api/training/records/me'),
  resume: id => request(`/api/training/resume/${id}`, { method: 'POST' }),
  finish: id => request(`/api/training/finish/${id}`, { method: 'POST' })
}
