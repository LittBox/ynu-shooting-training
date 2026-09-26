import test from 'node:test'
import assert from 'node:assert/strict'
import { useBooking } from '../src/composables/useBooking.js'
import { walkInBlockReason, calendarCell, normalizeSlots, SLOT_DEFINITIONS } from '../src/domain/booking.js'
const date = '2026-09-26', slot = SLOT_DEFINITIONS[0]
const time = value => Date.parse(`${date}T${value}+08:00`)

test('walk-in is confined to the current slot and retains personal quotas', () => {
  assert.ok(walkInBlockReason([], date, 'S1', time('08:29:59')))
  assert.equal(walkInBlockReason([], date, 'S1', time('08:30:00')), '')
  assert.equal(walkInBlockReason([], date, 'S1', time('10:09:59')), '')
  assert.ok(walkInBlockReason([], date, 'S1', time('10:10:00')))
  assert.ok(walkInBlockReason([], '2026-09-27', 'S1', time('09:00:00')))
  assert.match(walkInBlockReason([{ status: 'IN_USE' }], date, 'S1', time('09:00:00')), /先结束/)
  assert.match(walkInBlockReason([{ status: 'COMPLETED', slotDate: date, slotId: 'S1' }], date, 'S1', time('09:00:00')), /已有/)
  const mine = ['S2', 'S3', 'S4'].map(slotId => ({ status: 'BOOKED', slotId, slotDate: date }))
  assert.match(walkInBlockReason(mine, date, 'S1', time('09:00:00')), /3 场/)
})

test('current calendar exposes walk-in availability independently from future booking availability', () => {
  const rows = normalizeSlots([{ id: 'S1', deviceId: 1, deviceStatus: 'IDLE', staffed: true, coachPresent: true, available: 0, walkInAvailable: true }])
  assert.equal(rows[0].available, false)
  assert.equal(calendarCell(rows, [], date, slot, '', time('09:00:00')).label, '1 台可立即训练')
  assert.equal(normalizeSlots([{ walkInAvailable: true, deviceStatus: 'MAINTENANCE' }])[0].walkInAvailable, false)
  assert.equal(normalizeSlots([{ deviceStatus: 'IDLE' }])[0].walkInAvailable, false)
})

test('walk-in rechecks availability, sends one direct start request and only then shows success', async () => {
  const originalNow = Date.now
  Date.now = () => time('09:00:00')
  try {
    const writes = [], messages = []
    globalThis.uni = {
      getStorageSync: () => 'session', showToast: data => messages.push(data.title),
      request: options => options.success({ statusCode: 200, data: { code: 0, data: { profileStatus: 'completed' } } })
    }
    let available = true, failure = false
    const service = {
      slots: async () => [{ id: 'S1', deviceId: 1, available: false, walkInAvailable: available }], mine: async () => [],
      create: async () => { throw new Error('Must not create an advance booking') },
      walkIn: async data => { writes.push(data); if (failure) throw new Error('教练已离场'); return { id: 8, sessionId: 9, status: 'IN_USE' } }
    }
    const state = useBooking(service)
    state.openCell({ date, slot, state: 'ongoing' }, 1, true)
    await state.submit(1, 'qualifying')
    assert.deepEqual(writes, [{ deviceId: 1, slotDate: date, slotId: 'S1', mode: 'qualifying' }])
    assert.equal(state.successBooking.value.status, 'IN_USE')
    assert.equal(state.successBooking.value.sessionId, 9)
    state.successBooking.value = null
    state.openCell({ date, slot, state: 'ongoing' }, 1, true)
    available = false
    await state.submit(1)
    assert.equal(writes.length, 1)
    assert.equal(state.successBooking.value, null)
    assert.match(messages.at(-1), /状态已变化/)
    available = true; failure = true
    await state.submit(1)
    assert.equal(state.successBooking.value, null)
    assert.ok(state.selection.value)
    assert.equal(state.submitting.value, false)
    assert.equal(messages.at(-1), '教练已离场')
  } finally { Date.now = originalNow }
})
