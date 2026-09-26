import test from 'node:test'
import assert from 'node:assert/strict'
import { useBooking } from '../src/composables/useBooking.js'
import { request } from '../src/services/request.js'
import { addDays, venueToday, SLOT_DEFINITIONS } from '../src/domain/booking.js'

const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const storage = new Map()
globalThis.uni = { getStorageSync: key => storage.get(key), removeStorageSync: key => storage.delete(key), showToast() {} }

test('slow obsolete availability responses cannot overwrite the latest date page', async () => {
  let first = true
  const old = deferred()
  const state = useBooking({ slots: date => first ? old.promise : Promise.resolve([{ id: 'S1', deviceId: 2, date }]) })
  const pending = state.refresh()
  first = false
  state.offset.value = 3
  await state.refresh()
  old.resolve([{ id: 'S1', deviceId: 1 }])
  await pending
  assert.equal(state.rows.value[0].cells[0].devices[0].deviceId, 2)
  state.stop()
})
test('failed refresh removes stale capacity rather than leaving bookable cells', async () => {
  let fail = false
  const state = useBooking({ slots: () => fail ? Promise.reject(new Error('offline')) : Promise.resolve([{ id: 'S1', deviceId: 1, available: true }]) })
  await state.refresh()
  fail = true
  await state.refresh({ quiet: true })
  assert.equal(state.error.value, 'offline')
  assert.ok(state.rows.value[0].cells.every(cell => cell.devices.length === 0))
  state.stop()
})
test('hiding the page invalidates pending requests', async () => {
  const pending = deferred()
  const state = useBooking({ slots: () => pending.promise })
  const result = state.refresh()
  state.stop()
  pending.resolve([{ id: 'S1', deviceId: 1, available: true }])
  await result
  assert.equal(state.loading.value, false)
  assert.ok(state.rows.value[0].cells.every(cell => cell.devices.length === 0))
})
test('request strips backend internals on 500 and handles network failure', async () => {
  uni.request = options => options.success({ statusCode: 500, data: { message: 'private database details' } })
  await assert.rejects(request('/test', { auth: false }), /服务暂时不可用/)
  uni.request = options => options.fail({ errMsg: 'timeout' })
  await assert.rejects(request('/test', { auth: false }), /连接训练中心/)
})
test('401 from a stale session never clears a newly issued token', async () => {
  storage.set('token', 'old-test-token')
  let response
  uni.request = options => { response = options }
  const result = request('/test')
  storage.set('token', 'new-test-token')
  response.success({ statusCode: 401, data: { code: 401 } })
  await assert.rejects(result, /登录已过期/)
  assert.equal(storage.get('token'), 'new-test-token')
  storage.clear()
})

test('rapid confirmation taps create only one reservation and refresh the result', async () => {
  storage.set('token', 'submission-test-token')
  uni.request = options => options.success({ statusCode: 200, data: { code: 0, data: { profileStatus: 'completed' } } })
  const pending = deferred()
  let createCount = 0
  const state = useBooking({
    slots: () => Promise.resolve([{ id: 'S1', deviceId: 1, available: true }]),
    mine: () => Promise.resolve([]),
    create: () => { createCount++; return pending.promise }
  })
  state.selection.value = { date: addDays(venueToday(), 1), slot: SLOT_DEFINITIONS[0] }
  const first = state.submit(1)
  await state.submit(1)
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(createCount, 1)
  pending.resolve({ id: 42, status: 'BOOKED', deviceName: '测试设备' })
  await first
  assert.equal(state.successBooking.value.id, 42)
  assert.equal(state.selection.value, null)
  assert.equal(state.submitting.value, false)
  state.stop()
  storage.clear()
})


test('public login failure does not erase an existing authenticated session', async () => {
  storage.set('token', 'existing-session')
  uni.request = options => options.success({ statusCode: 401, data: { code: 401 } })
  await assert.rejects(request('/api/auth/login', { auth: false, method: 'POST', data: {} }))
  assert.equal(storage.get('token'), 'existing-session')
  storage.clear()
})
test('account switch discards pending records from the old account', async () => {
  storage.set('token', 'account-a')
  const pending = deferred()
  const state = useBooking({ slots: () => Promise.resolve([]), mine: () => pending.promise })
  const updating = state.refresh()
  storage.set('token', 'account-b')
  pending.resolve([{ id: 77, status: 'BOOKED' }])
  await updating
  assert.deepEqual(state.bookings.value, [])
  assert.equal(state.loading.value, false)
  state.stop(); storage.clear()
})
