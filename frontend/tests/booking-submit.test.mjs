import test from 'node:test'
import assert from 'node:assert/strict'
import { useBooking } from '../src/composables/useBooking.js'
import { venueToday, addDays, SLOT_DEFINITIONS } from '../src/domain/booking.js'

function setup({ token = 'test-session', profileStatus = 'completed', available = true, mine = [], createError } = {}) {
  const date = addDays(venueToday(), 1)
  const storage = new Map(token ? [['token', token]] : [])
  const writes = [], navigation = [], messages = []
  globalThis.uni = {
    getStorageSync: key => storage.get(key),
    removeStorageSync: key => storage.delete(key),
    navigateTo: data => navigation.push(data.url),
    showToast: data => messages.push(data.title),
    request: options => options.success({ statusCode: 200, data: { code: 0, data: { profileStatus } } })
  }
  const service = {
    slots: async () => [{ id: 'S1', deviceId: 2, deviceType: 'rifle', available }],
    mine: async () => mine,
    create: async data => { writes.push(data); if (createError) throw new Error(createError); return { id: 41, deviceName: '气步枪 1 号', status: 'BOOKED' } }
  }
  const state = useBooking(service)
  state.selection.value = { date, slot: SLOT_DEFINITIONS[0] }
  return { date, state, writes, navigation, messages }
}

test('submitting a chosen slot writes the selected device and only then shows success', async () => {
  const { date, state, writes } = setup()
  await state.submit(2)
  assert.deepEqual(writes, [{ deviceId: 2, slotDate: date, slotId: 'S1' }])
  assert.equal(state.successBooking.value.id, 41)
  assert.equal(state.successBooking.value.slotStart, '08:30')
  assert.equal(state.selection.value, null)
  assert.equal(state.submitting.value, false)
})

test('failed booking never shows success and retains the selected session', async () => {
  const { state, messages } = setup({ createError: '该时段已约满' })
  await state.submit(2)
  assert.equal(state.successBooking.value, null)
  assert.equal(state.selection.value.slot.id, 'S1')
  assert.equal(state.submitting.value, false)
  assert.ok(messages.includes('该时段已约满'))
})

test('capacity changing after opening the sheet prevents a booking write', async () => {
  const { state, writes, messages } = setup({ available: false })
  await state.submit(2)
  assert.equal(writes.length, 0)
  assert.equal(state.successBooking.value, null)
  assert.ok(messages.some(message => message.includes('刚刚被预约')))
})

test('new quota usage is rechecked immediately before writing', async () => {
  const date = addDays(venueToday(), 1)
  const { state, writes, messages } = setup({ mine: ['S2', 'S3', 'S4'].map(slotId => ({ slotDate: date, slotId, status: 'BOOKED' })) })
  await state.submit(2)
  assert.equal(writes.length, 0)
  assert.ok(messages.some(message => message.includes('3 场')))
})

test('guests and incomplete profiles return to login without losing their selection', async () => {
  for (const options of [{ token: '' }, { profileStatus: 'pending' }]) {
    const { state, writes, navigation } = setup(options)
    await state.submit(2)
    assert.deepEqual(navigation, ['/pages/login/login'])
    assert.equal(writes.length, 0)
    assert.equal(state.selection.value.slot.id, 'S1')
    assert.equal(state.submitting.value, false)
  }
})
