import test from 'node:test'
import assert from 'node:assert/strict'
import { newHistoryRound, historicalTrainingError, historicalTrainingPayload } from '../src/domain/historical-training.js'
const now = Date.parse('2026-09-27T12:00:00+08:00')
const form = () => ({ userId: 7, weapon: 'pistol', startedAt: '2026-09-25T23:30:00', endedAt: '2026-09-26T00:30:00', rounds: [
  { mode: 'final_', groupTotals: ['90', '90', '30'] }, { mode: 'qualifying', groupTotals: ['90', '90', '90', '90', '90', '90'] }
] })
test('history validates Beijing times, including cross-midnight sessions, independent of local timezone', () => {
  assert.equal(historicalTrainingError(form(), now), '')
  for (const patch of [{ userId: null }, { weapon: 'other' }, { startedAt: '2026-02-30T08:30:00' }, { endedAt: '2026-09-25T23:30:00' }, { endedAt: '2026-09-27T12:01:00' }, { startedAt: '' }]) {
    assert.notEqual(historicalTrainingError({ ...form(), ...patch }, now), '')
  }
})
test('actual rounds are precisely the entered rounds, each with complete valid groups', () => {
  for (const rounds of [[], [newHistoryRound()], [{ mode: 'final_', groupTotals: ['100.1', '90', '30'] }], [{ mode: 'bad', groupTotals: ['90', '90', '30'] }], Array(101).fill(form().rounds[0])]) {
    assert.notEqual(historicalTrainingError({ ...form(), rounds }, now), '')
  }
  const zero = { mode: 'final_', groupTotals: ['0', '0', '0'] }
  assert.equal(historicalTrainingError({ ...form(), rounds: [zero] }, now), '')
})
test('submitted snapshot preserves all rounds and remains unchanged while draft edits continue', () => {
  const values = form(), payload = historicalTrainingPayload(values, 'history-retry-key')
  values.rounds[0].groupTotals[0] = '100'
  assert.equal(payload.rounds[0].groupTotals[0], 90)
  assert.equal(payload.rounds.length, 2)
  assert.equal(payload.requestKey, 'history-retry-key')
  assert.equal(newHistoryRound('qualifying').groupTotals.length, 6)
})
