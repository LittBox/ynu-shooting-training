import assert from 'node:assert/strict'
import { request } from '../src/services/request.js'
import { bookingService } from '../src/services/booking.js'

// Run only against the ephemeral integration profile. Never run on production.
const base = process.env.TEST_API_BASE_URL
if (!base || !['127.0.0.1', 'localhost'].includes(new URL(base).hostname)) throw new Error('TEST_API_BASE_URL must point to a local integration server')
const storage = new Map()
globalThis.uni = {
  getStorageSync: key => storage.get(key),
  removeStorageSync: key => storage.delete(key),
  request(options) {
    const url = new URL(options.url)
    fetch(`${base}${url.pathname}${url.search}`, {
      method: options.method, headers: options.header,
      body: options.data === undefined ? undefined : JSON.stringify(options.data)
    }).then(async response => options.success({ statusCode: response.status, data: await response.json() })).catch(options.fail)
  }
}
const date = process.env.TEST_SLOT_DATE || '2026-09-25'
const suffix = Date.now().toString(36)
const login = async code => request('/api/auth/login', { auth: false, method: 'POST', data: { code, mode: 'mock', nickname: '联调学员' } })
const student = await login(`http-student-${suffix}`)
storage.set('token', student.token)
await request('/api/auth/profile', { method: 'POST', data: { realName: '联调测试', studentNo: `HTTP-${suffix}`, phone: '13800000000', gender: 'F' } })
assert.equal((await request('/api/auth/me')).profileStatus, 'completed')
const slots = await bookingService.slots(date)
const choice = slots.find(slot => slot.id === 'S1' && slot.deviceType === 'pistol' && slot.available)
assert.ok(choice, 'Expected a staffed S1 pistol slot at the fixed integration clock')
assert.ok(!('bookedBy' in choice), 'Frontend must not keep occupant identities')
const socket = new WebSocket(`${base.replace(/^http/, 'ws')}/ws/slots?date=${date}`)
const messages = []
socket.addEventListener('message', event => messages.push(JSON.parse(event.data)))
async function waitForMessage(predicate) {
  const deadline = Date.now() + 5000
  while (!messages.some(predicate)) {
    if (Date.now() > deadline) throw new Error('WebSocket state update timed out')
    await new Promise(resolve => setTimeout(resolve, 20))
  }
}
await waitForMessage(message => message.type === 'SLOTS_UPDATE')
messages.length = 0
const created = await bookingService.create({ deviceId: choice.deviceId, slotDate: date, slotId: choice.id })
assert.equal(created.status, 'BOOKED')
await waitForMessage(message => message.slots.some(s => s.id === choice.id && s.deviceId === choice.deviceId && s.available === 0))
socket.close()
assert.equal((await bookingService.slots(date)).find(s => s.deviceId === choice.deviceId && s.id === choice.id).available, false)
await assert.rejects(bookingService.create({ deviceId: choice.deviceId, slotDate: date, slotId: choice.id }), err => err.code === 409)
await bookingService.checkIn(created.id)
await assert.rejects(bookingService.startTraining(created.id, 'final_'), err => err.code === 409)
const admin = await login('integration-admin')
storage.set('token', admin.token)
const duty = await request(`/api/admin/duty?date=${date}`)
const shift = duty.schedules.find(s => s.slotId === 'S1')
await request(`/api/admin/schedules/${shift.id}/arrive`, { method: 'POST' })
storage.set('token', student.token)
const session = await bookingService.startTraining(created.id, 'final_')
assert.equal((await bookingService.mine()).find(b => b.id === created.id).sessionId, session.id)
storage.set('token', admin.token)
await assert.rejects(request('/api/admin/duty/depart', { method: 'POST' }), err => err.code === 409)
storage.set('token', student.token)
assert.equal((await bookingService.finishTraining(session.id)).actualDurationMin, 1)
storage.set('token', admin.token)
await request('/api/admin/duty/depart', { method: 'POST' })
storage.set('token', student.token)
await assert.rejects(bookingService.finishTraining(session.id), err => err.code === 409)
const score = { sessionId: session.id, mode: 'final_', groupSpec: [10, 10, 4], shotScores: Array(24).fill(9) }
await assert.rejects(request('/api/training/score', { method: 'POST', data: score }), err => err.code === 403)
storage.set('token', admin.token)
const recorded = await request('/api/training/score', { method: 'POST', data: score })
assert.equal(recorded.totalScore, 216)
await assert.rejects(request('/api/training/score', { method: 'POST', data: score }), err => err.code === 409)
storage.set('token', student.token)
const board = await request('/api/leaderboard?weapon=PISTOL&event=FINAL&metric=BEST&gender=F&limit=20')
assert.ok(board.rows.some(row => row.score === 216))
assert.ok(!JSON.stringify(board).includes('studentNo'))
const next = slots.find(slot => slot.id === 'S2' && slot.deviceType === 'pistol')
const cancelled = await bookingService.create({ deviceId: next.deviceId, slotDate: date, slotId: next.id })
await bookingService.cancel(cancelled.id)
assert.equal((await bookingService.mine()).find(b => b.id === cancelled.id).status, 'CANCELLED')
assert.equal((await bookingService.slots(date)).find(s => s.deviceId === next.deviceId && s.id === next.id).available, true)
const preflight = await fetch(`${base}/api/bookings`, { method: 'OPTIONS', headers: { Origin: 'http://127.0.0.1:5173', 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'authorization,content-type' } })
assert.ok(preflight.ok)
assert.equal(preflight.headers.get('access-control-allow-origin'), 'http://127.0.0.1:5173')
console.log('PASS: real frontend services → HTTP → Spring → database: login, profile, availability, booking, conflict, check-in, start/end, score permissions, leaderboard, cancellation and CORS.')
