// Venue time is always Asia/Shanghai, independent of the device's timezone.
export const BOOKING_RULES = Object.freeze({ advanceDays: 7, dailyLimit: 3, weeklyLimit: 10 })
export const SLOT_DEFINITIONS = Object.freeze([
  { id: 'S1', label: '上午一场', start: '08:30', end: '10:10', period: '上午' },
  { id: 'S2', label: '上午二场', start: '10:30', end: '12:10', period: '上午' },
  { id: 'S3', label: '下午一场', start: '14:00', end: '15:40', period: '下午' },
  { id: 'S4', label: '下午二场', start: '16:00', end: '17:40', period: '下午' },
  { id: 'S5', label: '晚间一场', start: '19:00', end: '20:40', period: '晚间' },
  { id: 'S6', label: '晚间二场', start: '21:30', end: '22:30', period: '晚间' }
])
export const DEVICE_TYPES = Object.freeze([
  { id: '', label: '全部设备' }, { id: 'pistol', label: '气手枪' }, { id: 'rifle', label: '气步枪' }
])
export const ACTIVE_STATUSES = ['BOOKED', 'CHECKED_IN', 'IN_USE']
export const COUNTED_STATUSES = [...ACTIVE_STATUSES, 'COMPLETED']
export const STATUS_LABELS = { BOOKED: '待签到', CHECKED_IN: '已签到', IN_USE: '训练中', COMPLETED: '已完成', CANCELLED: '已取消', NO_SHOW: '已爽约' }
export const venueToday = (now = Date.now()) => new Date(Number(now) + 8 * 3600000).toISOString().slice(0, 10)
export const addDays = (date, days) => new Date(Date.parse(`${date}T00:00:00Z`) + days * 86400000).toISOString().slice(0, 10)
export const slotTimestamp = (date, time) => Date.parse(`${date}T${time.slice(0, 5)}:00+08:00`)
export const shortDate = date => `${Number(date.slice(5, 7))}月${Number(date.slice(8))}日`
export const weekday = date => ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date(`${date}T00:00:00Z`).getUTCDay()]
export function dateWindow(today = venueToday()) {
  return Array.from({ length: BOOKING_RULES.advanceDays + 1 }, (_, index) => {
    const date = addDays(today, index)
    return { date, day: date.slice(8), label: index === 0 ? '今天' : index === 1 ? '明天' : weekday(date) }
  })
}
export function weekBounds(date) {
  const day = new Date(`${date}T00:00:00Z`).getUTCDay()
  const start = addDays(date, -(day === 0 ? 6 : day - 1))
  return { start, end: addDays(start, 6) }
}
export function bookingCounts(bookings, date) {
  const { start, end } = weekBounds(date)
  const counted = bookings.filter(item => COUNTED_STATUSES.includes(item.status))
  return {
    daily: counted.filter(item => item.slotDate === date).length,
    weekly: counted.filter(item => item.slotDate >= start && item.slotDate <= end).length
  }
}
export function bookingBlockReason(bookings, date, slotId, now = Date.now()) {
  const slot = SLOT_DEFINITIONS.find(item => item.id === slotId)
  if (!slot || date < venueToday(now) || date > addDays(venueToday(now), BOOKING_RULES.advanceDays)) return '不在可预约日期内'
  if (slotTimestamp(date, slot.start) <= now) return '该时段已开始，请选择其他时段'
  if (bookings.some(item => item.slotDate === date && item.slotId === slotId && COUNTED_STATUSES.includes(item.status))) return '你已预约该时段'
  const counts = bookingCounts(bookings, date)
  if (counts.daily >= BOOKING_RULES.dailyLimit) return '当日预约已达 3 场上限'
  if (counts.weekly >= BOOKING_RULES.weeklyLimit) return '该自然周预约已达 10 场上限'
  return ''
}
export function normalizeSlots(rows) {
  if (!Array.isArray(rows)) throw new Error('时段数据格式异常，请稍后重试')
  // Explicit projection prevents other students' names from entering UI state.
  return rows.map(row => ({
    id: row.id, start: row.start?.slice(0, 5), end: row.end?.slice(0, 5),
    deviceId: row.deviceId, deviceName: row.deviceName, deviceType: row.deviceType,
    bookedCount: Number(row.bookedCount || 0), deviceStatus: row.deviceStatus, staffed: row.staffed, coachPresent: row.coachPresent,
    walkInAvailable: row.walkInAvailable === true && !['MAINTENANCE', 'DISABLED', 'IN_USE'].includes(row.deviceStatus),
    available: Number(row.available) > 0 && !['MAINTENANCE', 'DISABLED'].includes(row.deviceStatus)
  }))
}
export function calendarCell(rows, bookings, date, slot, deviceType, now = Date.now()) {
  const devices = rows.filter(row => row.id === slot.id && (!deviceType || row.deviceType === deviceType))
  const mine = bookings.find(item => item.slotDate === date && item.slotId === slot.id && ACTIVE_STATUSES.includes(item.status))
  const remaining = devices.filter(row => row.available).length
  const serviceable = devices.some(row => row.staffed !== false && !['MAINTENANCE', 'DISABLED'].includes(row.deviceStatus))
  const walkInCount = devices.filter(row => row.walkInAvailable).length
  const state = now >= slotTimestamp(date, slot.end) ? 'past' : now >= slotTimestamp(date, slot.start) ? 'ongoing' : mine ? 'mine' : !serviceable ? 'closed' : remaining ? 'available' : 'full'
  return { date, slot, devices, remaining, walkInCount, state, mine, label: { mine: '我的预约', past: '已结束', ongoing: walkInCount ? `${walkInCount} 台可立即训练` : '进行中', closed: '未开放', available: `${remaining} 台可约`, full: '已约满' }[state] }
}
// Position within the rendered slot row, so the marker follows responsive row heights.
// Breaks are compressed in this calendar; keep the marker at the preceding row's end.
export function calendarTimeMarker(dates, slots = SLOT_DEFINITIONS, now = Date.now()) {
  const today = venueToday(now)
  if (!dates.some(day => day.date === today) || !slots.length) return null
  if (now < slotTimestamp(today, slots[0].start) || now >= slotTimestamp(today, slots[slots.length - 1].end)) return null
  for (let index = 0; index < slots.length; index++) {
    const slot = slots[index]
    const start = slotTimestamp(today, slot.start), end = slotTimestamp(today, slot.end)
    if (now < end) return { slotId: slot.id, progress: (now - start) / (end - start) }
    if (index + 1 < slots.length && now < slotTimestamp(today, slots[index + 1].start)) return { slotId: slot.id, progress: 1 }
  }
  return null
}
export function cancellationHint(booking, now = Date.now()) {
  const hours = (slotTimestamp(booking.slotDate, booking.slotStart) - now) / 3600000
  return hours >= 12 ? '距开始超过 12 小时，本次为免费取消。' : hours >= 1 ? '距开始不足 12 小时，本次将记录为晚取消。' : '距开始不足 1 小时，本次将记录为临近取消。'
}
export function canCheckIn(booking, now = Date.now()) {
  const minutes = (slotTimestamp(booking.slotDate, booking.slotStart) - now) / 60000
  return booking.status === 'BOOKED' && minutes <= 30 && minutes >= -10
}

export function walkInBlockReason(bookings, date, slotId, now = Date.now()) {
  const slot = SLOT_DEFINITIONS.find(item => item.id === slotId)
  if (!slot || date !== venueToday(now) || now < slotTimestamp(date, slot.start) || now >= slotTimestamp(date, slot.end)) return '仅当前正在进行的时段可立即训练'
  if (bookings.some(item => item.status === 'IN_USE')) return '请先结束正在进行的训练'
  if (bookings.some(item => item.slotDate === date && item.slotId === slotId && COUNTED_STATUSES.includes(item.status))) return '你已有该时段的预约或训练，请在我的预约中查看'
  const counts = bookingCounts(bookings, date)
  if (counts.daily >= BOOKING_RULES.dailyLimit) return '当日预约及训练已达 3 场上限'
  if (counts.weekly >= BOOKING_RULES.weeklyLimit) return '该自然周预约及训练已达 10 场上限'
  return ''
}

// Keep all records for quota/calendar rules; split only the displayed lists.
export function bookingRecordsForView(bookings, view) {
  return bookings.filter(row => view === 'history' ? row.status === 'COMPLETED' : row.status !== 'COMPLETED')
}
