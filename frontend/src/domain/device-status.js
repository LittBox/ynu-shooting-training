import { slotTimestamp } from './booking.js'
// Device state describes physical usage; future booking counts do not.
export function currentDeviceStatus(rows, type, date, now = Date.now()) {
  const devices = new Map()
  for (const row of rows.filter(r => r.deviceType === type)) {
    const item = devices.get(row.deviceId) || { status: row.deviceStatus, current: null }
    if (now >= slotTimestamp(date, row.start) && now < slotTimestamp(date, row.end)) item.current = row
    devices.set(row.deviceId, item)
  }
  if (!devices.size) return { label: '使用状态待更新', tone: 'unknown' }
  const states = [...devices.values()].map(({ status, current }) => {
    if (status === 'IN_USE') return { label: '使用中', tone: 'busy' }
    if (status === 'MAINTENANCE') return { label: '维护中', tone: 'unknown' }
    if (status === 'DISABLED') return { label: '已停用', tone: 'unknown' }
    if (!['IDLE', 'BOOKED'].includes(status)) return { label: '状态待更新', tone: 'unknown' }
    if (current?.bookedCount > 0 && !current.walkInAvailable) return { label: '已预约待训练', tone: 'reserved' }
    return { label: '空闲', tone: 'idle', hint: !current ? '当前非训练时段' : current.coachPresent ? '当前时段可查看训练安排' : '待教练到岗' }
  })
  if (states.length === 1) return states[0]
  const counts = new Map()
  for (const state of states) counts.set(state.label, (counts.get(state.label) || 0) + 1)
  return { label: [...counts].map(([label, count]) => `${count} 台${label}`).join(' · '), tone: states.some(s => s.tone === 'busy') ? 'busy' : states.some(s => s.tone === 'idle') ? 'idle' : 'unknown' }
}
