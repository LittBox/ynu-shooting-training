import test from 'node:test'
import assert from 'node:assert/strict'
import { requestReminderSubscription, reminderService } from '../src/services/reminders.js'

test('native subscription is invoked synchronously within the tap, before persistence', async () => {
  const calls = []
  const pending = requestReminderSubscription({ requestSubscribeMessage(options) {
    calls.push('native'); assert.deepEqual(options.tmplIds, ['template']); options.success({ template: 'accept' })
  } }, 'template')
  assert.deepEqual(calls, ['native'])
  assert.equal(await pending, true)
})
test('refusal, ban and missing result never count as authorization', async () => {
  for (const result of ['reject', 'ban', undefined]) {
    assert.equal(await requestReminderSubscription({ requestSubscribeMessage: options => options.success({ template: result }) }, 'template'), false)
  }
})
test('native failure and browsers do not silently report a subscription', async () => {
  await assert.rejects(requestReminderSubscription(null, 'template'), /微信小程序/)
  await assert.rejects(requestReminderSubscription({ requestSubscribeMessage: options => options.fail() }, 'template'), /订阅未完成/)
})
test('reminder persistence binds acceptance to one owned business record and the actual template', async () => {
  const seen = []
  globalThis.uni = { getStorageSync: () => 'session-token', request(options) { seen.push(options); options.success({ statusCode: 200, data: { code: 0, data: { status: 'PENDING' } } }) } }
  await reminderService.subscribe('TRAINING', 42, 'real-template')
  await reminderService.status('DUTY', 8)
  await reminderService.cancel('TRAINING', 42)
  assert.ok(seen[0].url.endsWith('/api/reminders/TRAINING/42'))
  assert.deepEqual(seen[0].data, { templateId: 'real-template', accepted: true })
  assert.equal(seen[0].method, 'POST')
  assert.ok(seen[1].url.endsWith('/api/reminders/DUTY/8'))
  assert.equal(seen[2].method, 'DELETE')
})
