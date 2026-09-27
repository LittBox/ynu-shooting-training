import test from 'node:test'
import assert from 'node:assert/strict'
import { dutyCalendarCell, dutyStatusLabel, canArriveForDuty, isCoach, validDutyDate } from '../src/domain/duty.js'
import { SLOT_DEFINITIONS } from '../src/domain/booking.js'
const slot = SLOT_DEFINITIONS[0], date = '2026-09-27', now = Date.parse('2026-09-27T09:00:00+08:00')
const own = { id: 1, adminId: 7, slotDate: date, slotId: slot.id, state: 'PLANNED' }
test('only verified coach roles expose duty access', () => {
  for (const role of ['admin', 'superadmin']) assert.equal(isCoach({ role }), true)
  for (const value of [null, {}, { role: 'student' }, { role: 'other' }]) assert.equal(isCoach(value), false)
})
test('duty calendar distinguishes my booking from other coaches and permits shared shifts', () => {
  const other = { ...own, id: 2, adminId: 8 }
  const cell = dutyCalendarCell([own, other], 7, date, slot, now)
  assert.equal(cell.state, 'mine'); assert.equal(cell.subtitle, '我的值班'); assert.equal(cell.members.length, 2)
  const join = dutyCalendarCell([other], 7, date, slot, now)
  assert.equal(join.state, 'available'); assert.equal(join.title, '可预约值班'); assert.equal(join.subtitle, '1 人排班')
  assert.equal(dutyCalendarCell([own], 7, '2026-09-28', slot, now).mine, undefined)
})
test('ended shifts keep my booked identity and distinguish attendance from just reserving', () => {
  const end = Date.parse('2026-09-27T10:10:00+08:00')
  assert.equal(dutyCalendarCell([own], 7, date, slot, end).title, '未到岗')
  assert.equal(dutyStatusLabel(own, date, slot, end), '未到岗 · 已结束')
  assert.equal(dutyCalendarCell([own], 7, date, slot, end).state, 'mine')
  assert.equal(dutyCalendarCell([], 7, date, slot, end).state, 'past')
  assert.equal(dutyStatusLabel({ ...own, state: 'PRESENT', coveringNow: true }, date, slot, now), '已到岗')
  assert.equal(dutyStatusLabel({ ...own, state: 'PRESENT', coveringNow: true }, date, slot, end), '待确认离场')
  assert.equal(dutyStatusLabel({ ...own, state: 'LEFT' }, date, slot, end), '已离场')
})
test('arrival respects server permission and exact Beijing window boundaries', () => {
  const shift = { ...own, canArrive: true }
  assert.equal(canArriveForDuty(shift, date, slot, Date.parse(`${date}T08:00:00+08:00`)), true)
  assert.equal(canArriveForDuty(shift, date, slot, Date.parse(`${date}T07:59:59+08:00`)), false)
  assert.equal(canArriveForDuty(shift, date, slot, Date.parse(`${date}T10:10:00+08:00`)), false)
  assert.equal(canArriveForDuty({ ...shift, canArrive: false }, date, slot, now), false)
  assert.equal(validDutyDate('2026-02-30'), false); assert.equal(validDutyDate(date), true)
})
