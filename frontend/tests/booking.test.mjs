import test from 'node:test'
import assert from 'node:assert/strict'
import { venueToday, dateWindow, addDays, weekBounds, bookingCounts, bookingBlockReason, SLOT_DEFINITIONS, calendarCell, normalizeSlots, canCheckIn, cancellationHint, slotTimestamp, calendarTimeMarker } from '../src/domain/booking.js'

const now = Date.parse('2026-09-24T09:00:00+08:00')
const booking = (overrides = {}) => ({ id: 1, slotDate: '2026-09-25', slotId: 'S1', slotStart: '08:30', slotEnd: '10:10', status: 'BOOKED', ...overrides })

test('venue dates use Shanghai midnight and include the inclusive +7 day boundary', () => {
  assert.equal(venueToday(Date.parse('2026-09-24T16:01:00Z')), '2026-09-25')
  assert.equal(dateWindow('2026-09-24').length, 8)
  assert.equal(dateWindow('2026-09-24').at(-1).date, '2026-10-01')
  assert.equal(addDays('2026-12-31', 1), '2027-01-01')
})
test('natural weeks start Monday, including year boundaries', () => {
  assert.deepEqual(weekBounds('2027-01-03'), { start: '2026-12-28', end: '2027-01-03' })
})
test('completed sessions count toward quotas; cancellations and no-shows do not', () => {
  const bookings = [booking(), booking({ id: 2, status: 'COMPLETED' }), booking({ id: 3, status: 'CANCELLED' }), booking({ id: 4, status: 'NO_SHOW' }), booking({ id: 5, slotDate: '2026-09-28' })]
  assert.deepEqual(bookingCounts(bookings, '2026-09-25'), { daily: 2, weekly: 2 })
})
test('reject elapsed, out-of-window and duplicate slots; accept day +7', () => {
  assert.match(bookingBlockReason([], '2026-09-24', 'S1', now), /已开始/)
  assert.match(bookingBlockReason([], '2026-10-02', 'S1', now), /日期/)
  assert.equal(bookingBlockReason([], '2026-10-01', 'S1', now), '')
  assert.match(bookingBlockReason([booking()], '2026-09-25', 'S1', now), /已预约/)
})
test('enforce daily and natural-week caps independently', () => {
  const daily = ['S1', 'S2', 'S3'].map(slotId => booking({ slotId, status: 'COMPLETED' }))
  assert.match(bookingBlockReason(daily, '2026-09-25', 'S4', now), /3 场/)
  const weekly = Array.from({ length: 10 }, (_, i) => booking({ id: i, slotDate: '2026-09-23', status: 'COMPLETED' }))
  assert.match(bookingBlockReason(weekly, '2026-09-25', 'S4', now), /10 场/)
  assert.equal(bookingBlockReason(weekly, '2026-09-28', 'S4', now), '')
})
test('availability normalizes zero and drops private fields', () => {
  const [row] = normalizeSlots([{ id: 'S1', deviceId: 1, available: '0', bookedBy: ['private'], realName: 'private' }])
  assert.equal(row.available, false)
  assert.equal('bookedBy' in row, false)
  assert.equal('realName' in row, false)
  assert.equal(normalizeSlots([{ available: 1, deviceStatus: 'MAINTENANCE' }])[0].available, false)
})
test('busy physical device remains bookable in free future slots without enabling walk-in', () => {
  const date = '2026-09-25', now = Date.parse(`${date}T09:00:00+08:00`)
  const rows = normalizeSlots([
    { id: 'S1', deviceId: 1, deviceStatus: 'IN_USE', staffed: true, available: 0, walkInAvailable: true },
    { id: 'S2', deviceId: 1, deviceStatus: 'IN_USE', staffed: true, available: 1 }
  ])
  assert.equal(rows[0].walkInAvailable, false)
  assert.equal(rows[0].available, false)
  assert.equal(rows[1].available, true)
  assert.equal(rows[1].deviceStatus, 'IN_USE')
  assert.equal(calendarCell(rows, [], date, SLOT_DEFINITIONS[1], '', now).label, '1 台可约')
  assert.equal(calendarCell(rows, [], date, SLOT_DEFINITIONS[0], '', now).label, '进行中')
  const occupied = normalizeSlots([{ ...rows[1], available: 0, bookedCount: 1 }])
  assert.equal(calendarCell(occupied, [], date, SLOT_DEFINITIONS[1], '', now).state, 'full')
  assert.equal(bookingBlockReason([{ slotDate: date, slotId: 'S1', status: 'IN_USE' }], date, 'S2', now), '')
})
test('future availability still closes maintenance, disabled and unstaffed devices', () => {
  for (const deviceStatus of ['MAINTENANCE', 'DISABLED']) {
    const rows = normalizeSlots([{ id: 'S1', deviceStatus, staffed: true, available: 1, walkInAvailable: true }])
    assert.equal(rows[0].available, false)
    assert.equal(rows[0].walkInAvailable, false)
    assert.equal(calendarCell(rows, [], '2026-09-25', SLOT_DEFINITIONS[0], '', now).state, 'closed')
  }
  const rows = normalizeSlots([{ id: 'S1', deviceStatus: 'IN_USE', staffed: false, available: 0 }])
  assert.equal(calendarCell(rows, [], '2026-09-25', SLOT_DEFINITIONS[0], '', now).state, 'closed')
})
test('calendar covers available, full, closed, elapsed and own-booking states', () => {
  const slot = SLOT_DEFINITIONS[0]
  const rows = [{ id: 'S1', deviceType: 'pistol', available: true }]
  assert.equal(calendarCell(rows, [], '2026-09-25', slot, '', now).state, 'available')
  assert.equal(calendarCell([{ ...rows[0], available: false }], [], '2026-09-25', slot, '', now).state, 'full')
  assert.equal(calendarCell(rows, [], '2026-09-25', slot, 'rifle', now).state, 'closed')
  assert.equal(calendarCell(rows, [], '2026-09-24', slot, '', now).state, 'ongoing')
  assert.equal(calendarCell(rows, [booking()], '2026-09-25', slot, '', now).state, 'mine')
})
test('check-in includes both boundaries and excludes one millisecond outside', () => {
  const b = booking(), start = slotTimestamp(b.slotDate, b.slotStart)
  assert.equal(canCheckIn(b, start - 30 * 60000), true)
  assert.equal(canCheckIn(b, start - 30 * 60000 - 1), false)
  assert.equal(canCheckIn(b, start + 10 * 60000), true)
  assert.equal(canCheckIn(b, start + 10 * 60000 + 1), false)
  assert.equal(canCheckIn({ ...b, status: 'CHECKED_IN' }, start), false)
})
test('cancellation classification preserves exact 12h and 1h boundaries', () => {
  const b = booking(), start = slotTimestamp(b.slotDate, b.slotStart)
  assert.match(cancellationHint(b, start - 12 * 3600000), /免费/)
  assert.match(cancellationHint(b, start - 12 * 3600000 + 1), /晚取消/)
  assert.match(cancellationHint(b, start - 3600000), /晚取消/)
  assert.match(cancellationHint(b, start - 3600000 + 1), /临近取消/)
})


test('unstaffed periods are closed rather than fully booked', () => {
  const slot = SLOT_DEFINITIONS[0]
  const day = '2026-09-25'
  const rows = normalizeSlots([{ id: 'S1', deviceId: 1, deviceType: 'pistol', deviceStatus: 'IDLE', staffed: false, available: 0 }])
  assert.equal(calendarCell(rows, [], day, slot, '', Date.parse('2026-09-25T07:00:00+08:00')).state, 'closed')
})

test('a completed reservation still consumes the same scheduled slot', () => {
  const date = '2026-09-25', now = Date.parse('2026-09-25T08:00:00+08:00')
  assert.equal(bookingBlockReason([{ slotDate: date, slotId: 'S1', status: 'COMPLETED' }], date, 'S1', now), '你已预约该时段')
})


test('calendar status changes at start and end even for my own reservation', () => {
  const date = '2026-09-25', slot = SLOT_DEFINITIONS[0]
  const start = slotTimestamp(date, slot.start), end = slotTimestamp(date, slot.end)
  const rows = [{ id: 'S1', deviceType: 'pistol', available: true }]
  for (const mine of [[], [booking()]]) {
    assert.equal(calendarCell(rows, mine, date, slot, '', start).label, '进行中')
    assert.equal(calendarCell(rows, mine, date, slot, '', end - 1).state, 'ongoing')
    assert.equal(calendarCell(rows, mine, date, slot, '', end).label, '已结束')
  }
  assert.equal(calendarCell(rows, [booking()], date, slot, '', start - 1).state, 'mine')
})

test('current-time line tracks slot progress and handles breaks and date navigation', () => {
  const dates = [{ date: '2026-09-25' }]
  const at = time => slotTimestamp(dates[0].date, time)
  assert.equal(calendarTimeMarker(dates, SLOT_DEFINITIONS, at('08:29')), null)
  assert.deepEqual(calendarTimeMarker(dates, SLOT_DEFINITIONS, at('08:30')), { slotId: 'S1', progress: 0 })
  assert.deepEqual(calendarTimeMarker(dates, SLOT_DEFINITIONS, at('09:20')), { slotId: 'S1', progress: 0.5 })
  assert.deepEqual(calendarTimeMarker(dates, SLOT_DEFINITIONS, at('10:20')), { slotId: 'S1', progress: 1 })
  assert.deepEqual(calendarTimeMarker(dates, SLOT_DEFINITIONS, at('10:30')), { slotId: 'S2', progress: 0 })
  assert.deepEqual(calendarTimeMarker(dates, SLOT_DEFINITIONS, at('13:00')), { slotId: 'S2', progress: 1 })
  assert.equal(calendarTimeMarker(dates, SLOT_DEFINITIONS, at('22:30')), null)
  assert.equal(calendarTimeMarker([{ date: '2026-09-26' }], SLOT_DEFINITIONS, at('09:20')), null)
  assert.equal(calendarTimeMarker(dates, [], at('09:20')), null)
})


test('completed training moves to history without changing quotas or resumed records', async () => {
  const { bookingRecordsForView } = await import('../src/domain/booking.js')
  const records = [{ id: 1, status: 'BOOKED' }, { id: 2, status: 'COMPLETED', sessionId: 9 }, { id: 3, status: 'IN_USE' }, { id: 4, status: 'CANCELLED' }]
  assert.deepEqual(bookingRecordsForView(records, 'mine').map(r => r.id), [1, 3, 4])
  assert.deepEqual(bookingRecordsForView(records, 'history').map(r => r.id), [2])
  assert.equal(records.length, 4)
  records[1].status = 'IN_USE'
  assert.equal(bookingRecordsForView(records, 'history').length, 0)
  assert.ok(bookingRecordsForView(records, 'mine').some(r => r.id === 2))
})
