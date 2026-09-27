import { slotTimestamp } from './booking.js'

export const isCoach = user => ['admin', 'superadmin'].includes(user?.role)
export function dutyStatusLabel(shift, date, slot, now = Date.now()) {
  if (shift.coveringNow && now < slotTimestamp(date, slot.end)) return '已到岗'
  if (shift.state === 'PRESENT') return '待确认离场'
  if (shift.state === 'LEFT') return '已离场'
  return now >= slotTimestamp(date, slot.end) ? '未到岗 · 已结束' : '已预约值班'
}
export function dutyCalendarCell(schedules, userId, date, slot, now = Date.now()) {
  const members = schedules.filter(s => s.slotId === slot.id && s.slotDate === date)
  const mine = members.find(s => s.adminId === userId)
  const ended = now >= slotTimestamp(date, slot.end)
  const status = mine ? dutyStatusLabel(mine, date, slot, now) : ended ? '已结束' : '可预约值班'
  const title = status === '未到岗 · 已结束' ? '未到岗' : status
  const subtitle = mine ? '我的值班' : members.length ? `${members.length} 人排班` : '暂无教员'
  return { date, slot, members, mine, ended, state: mine ? 'mine' : ended ? 'past' : 'available',
    title, subtitle, action: mine ? '查看值班 ↗' : ended ? '查看排班 ↗' : '安排值班 ↗', label: `${status}，${subtitle}` }
}
export function canArriveForDuty(shift, date, slot, now = Date.now()) {
  return !!shift?.canArrive && now >= slotTimestamp(date, slot.start) - 30 * 60000 && now < slotTimestamp(date, slot.end)
}
export function validDutyDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value || '') && Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
}
