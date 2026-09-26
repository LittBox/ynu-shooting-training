import test from 'node:test'
import assert from 'node:assert/strict'
import { useSlotDetails } from '../src/composables/useSlotDetails.js'

const cell = { date: '2026-09-25', slot: { id: 'S3' }, state: 'full' }
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
let token = 'student'
globalThis.uni = { getStorageSync: () => token }

test('full and past cells expose details without attempting a booking', async () => {
  const calls = []
  const state = useSlotDetails({ details: async (...args) => { calls.push(args); return { devices: [{ deviceId: 8 }] } } })
  try {
    await state.open(cell, 'rifle')
    assert.deepEqual(calls[0], ['2026-09-25', 'S3'])
    assert.equal(state.detail.value.devices[0].deviceId, 8)
    await state.open({ ...cell, state: 'past' })
    assert.equal(calls.length, 2)
  } finally { state.close() }
})
test('closing or switching slots discards late responses', async () => {
  const old = deferred()
  const state = useSlotDetails({ details: (date, slot) => slot === 'S3' ? old.promise : Promise.resolve({ slotId: slot }) })
  try {
    const opening = state.open(cell)
    await state.open({ ...cell, slot: { id: 'S4' } })
    old.resolve({ slotId: 'S3' }); await opening
    assert.equal(state.detail.value.slotId, 'S4')
    const pending = state.refresh()
    state.close(); await pending
    assert.equal(state.detail.value, null)
    assert.equal(state.inspected.value, null)
  } finally { state.close() }
})
test('switching accounts never displays the previous viewer roster', async () => {
  const pending = deferred()
  const state = useSlotDetails({ details: () => pending.promise })
  try {
    token = 'coach'
    const opening = state.open(cell)
    token = 'student'
    pending.resolve({ viewerScope: 'STAFF', devices: [] }); await opening
    assert.equal(state.detail.value, null)
    assert.equal(state.inspected.value, null)
  } finally { state.close() }
})
test('failed refresh clears stale roster and available device actions', async () => {
  let fail = false
  const state = useSlotDetails({ details: async () => { if (fail) throw new Error('连接失败'); return { devices: [{ available: true }] } } })
  try {
    await state.open(cell)
    fail = true; await state.refresh()
    assert.equal(state.detail.value, null)
    assert.equal(state.error.value, '连接失败')
  } finally { state.close() }
})
