import { request } from './request.js'

export const dutyService = {
  status: date => request(`/api/admin/duty?date=${encodeURIComponent(date)}`),
  schedule: (adminId, date, slotId) => request(`/api/admin/schedules?adminId=${encodeURIComponent(adminId)}&slotDate=${encodeURIComponent(date)}&slotId=${encodeURIComponent(slotId)}`, { method: 'POST' }),
  remove: id => request(`/api/admin/schedules/${id}`, { method: 'DELETE' }),
  arrive: id => request(`/api/admin/schedules/${id}/arrive`, { method: 'POST' }),
  depart: () => request('/api/admin/duty/depart', { method: 'POST' })
}
