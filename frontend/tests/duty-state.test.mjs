import test from 'node:test'
import assert from 'node:assert/strict'
import { useDutySchedule } from '../src/composables/useDutySchedule.js'
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
let token = 'coach-token'
globalThis.uni = { getStorageSync: () => token, showToast() {} }
const coach = { id: 7, role: 'admin' }
const response = (date, schedules = []) => ({ schedules, onDutyCount: 0, activeTrainingCount: 0, hasOpenAttendance: false })
test('loads three day calendar and preserves dates arriving from existing reminder links', async () => {
  const requested = []
  const state = useDutySchedule(() => coach, undefined, { status: async date => { requested.push(date); return response(date) } })
  try {
    await state.start('2026-09-20')
    assert.equal(state.date.value, '2026-09-20'); assert.equal(state.visibleDates.value[0].date, '2026-09-20')
    assert.equal(requested.length, 3); assert.equal(state.rows.value.length, 6)
    await state.goToday()
    assert.equal(state.date.value, state.today.value); assert.equal(requested.length, 6)
  } finally { state.stop() }
})
test('calendar never loads private duty data for a student', async () => {
  let requests = 0
  const state = useDutySchedule(() => ({ id: 9, role: 'student' }), undefined, { status: async () => { requests++; return response() } })
  try { await state.start(); assert.equal(requests, 0); assert.equal(state.state.value, null) } finally { state.stop() }
})
test('late responses cannot overwrite a newer date page or a hidden page', async () => {
  const old = deferred(); let slow = true
  const state = useDutySchedule(() => coach, undefined, { status: date => slow ? old.promise : Promise.resolve(response(date)) })
  try {
    const first = state.start(); slow = false; state.chooseDate(state.dates.value[6].date); await state.refresh()
    const latest = state.date.value
    old.resolve({ ...response(), onDutyCount: 99 }); await first
    assert.equal(state.date.value, latest); assert.equal(state.state.value.onDutyCount, 0)
    const wait = deferred(); const hidden = useDutySchedule(() => coach, undefined, { status: () => wait.promise })
    const pending = hidden.start(); hidden.stop(); wait.resolve(response()); await pending
    assert.equal(hidden.state.value, null); assert.equal(hidden.loading.value, false)
  } finally { state.stop() }
})
test('failed refresh removes actionable stale data and role revocation clears calendar', async () => {
  let failure, revoked = 0
  const state = useDutySchedule(() => coach, () => { revoked++ }, { status: async date => { if (failure) throw failure; return response(date) } })
  try {
    await state.start(); assert.ok(state.state.value)
    failure = new Error('offline'); await state.refresh({ quiet: true })
    assert.equal(state.state.value, null); assert.equal(state.error.value, 'offline')
    failure = Object.assign(new Error('forbidden'), { code: 403 }); await state.refresh()
    assert.equal(revoked, 1); assert.equal(state.state.value, null)
  } finally { state.stop() }
})
test('changing accounts discards the former coach roster', async () => {
  const pending = deferred(); let revoked = 0
  const state = useDutySchedule(() => coach, () => { revoked++ }, { status: () => pending.promise })
  try {
    token = 'coach-a'; const loading = state.start(); token = 'coach-b'; pending.resolve(response()); await loading
    assert.equal(state.state.value, null); assert.equal(revoked, 1)
  } finally { state.stop(); token = 'coach-token' }
})
test('rapid action taps create one shift and refresh the marked calendar after success', async () => {
  let created = false, count = 0
  const pending = deferred()
  const state = useDutySchedule(() => coach, undefined, { status: async date => response(date, created ? [{ id: 8, adminId: 7, slotDate: date, slotId: 'S1', state: 'PLANNED' }] : []) })
  try {
    await state.start(); const operation = async () => { count++; await pending.promise; created = true }
    const first = state.perform(operation, '成功'); await state.perform(operation, '成功')
    assert.equal(count, 1); pending.resolve(); await first
    assert.equal(state.rows.value[0].cells[0].state, 'mine'); assert.equal(state.busy.value, false)
    await state.perform(async () => { throw new Error('最后一位教员不可取消') }, '取消')
    assert.equal(state.actionError.value, '最后一位教员不可取消'); assert.equal(state.rows.value[0].cells[0].state, 'mine')
  } finally { state.stop() }
})
