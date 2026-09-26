import { request } from './request.js'
import { normalizeSlots } from '../domain/booking.js'

export const bookingService = {
  async details(date, slotId) {
    return request(`/api/bookings/slots/details?date=${encodeURIComponent(date)}&slotId=${encodeURIComponent(slotId)}`, { auth: !!uni.getStorageSync('token') })
  },
  async slots(date) { return normalizeSlots(await request(`/api/bookings/slots?date=${encodeURIComponent(date)}`, { auth: false })) },
  async mine() {
    const rows = await request('/api/bookings/me')
    if (!Array.isArray(rows)) throw new Error('预约记录格式异常，请重试')
    return rows.map(({ id, sessionId, slotDate, slotId, slotStart, slotEnd, deviceName, deviceType, status }) => ({ id, sessionId, slotDate, slotId, slotStart: slotStart?.slice(0, 5), slotEnd: slotEnd?.slice(0, 5), deviceName, deviceType, status }))
  },
  create: data => request('/api/bookings', { method: 'POST', data: { ...data, availabilityConfirmed: true } }),
  walkIn: data => request('/api/bookings/walk-in', { method: 'POST', data }),
  cancel: id => request(`/api/bookings/${id}/cancel`, { method: 'POST' }),
  checkIn: id => request(`/api/bookings/${id}/checkin`, { method: 'POST' }),
  startTraining: (id, mode) => request(`/api/training/start/${id}?mode=${encodeURIComponent(mode)}`, { method: 'POST' }),
  finishTraining: sessionId => request(`/api/training/finish/${sessionId}`, { method: 'POST' })
}
