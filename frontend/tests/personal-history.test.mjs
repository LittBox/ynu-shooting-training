import test from 'node:test'
import assert from 'node:assert/strict'
import { personalHistoryDays, sessionTimeRange } from '../src/domain/personal-history.js'

test('groups mixed results once per actual training, preserving unscored and cross-midnight sessions', () => {
  const sessions = [
    { id: 2, startedAt: '2026-09-25T14:00:00', endedAt: '2026-09-25T15:00:00' },
    { id: 1, startedAt: '2026-09-25T08:30:00', endedAt: '2026-09-25T10:00:00' },
    { id: 3, startedAt: '2026-09-24T23:30:00', endedAt: '2026-09-25T00:30:00', historical: true },
    { id: 4, startedAt: '2026-09-26T10:30:00', endedAt: null }
  ]
  const score = (sessionId, mode, totalScore, weapon = 'pistol') => ({ sessionId, mode, totalScore, weapon, recordedAt: '2026-09-27T09:00:00' })
  const days = personalHistoryDays({ trainingSessions: sessions, sessions: [score(1, 'final_', 210), score(1, 'qualifying', 540), score(2, 'final_', 220), score(2, 'qualifying', 510, 'rifle'), score(3, 'final_', 0)] })
  assert.deepEqual(days.map(d => d.date), ['2026-09-26', '2026-09-25', '2026-09-24'])
  assert.deepEqual(days[1].sessions.map(s => s.id), [1, 2])
  assert.equal(days[1].sessions[0].results.length, 2)
  assert.deepEqual(days[1].bests.map(s => s.totalScore), [220, 540, 510])
  assert.equal(days[0].sessions.length, 1); assert.equal(days[0].bests.length, 0)
  assert.equal(days[2].bests[0].totalScore, 0)
  assert.equal(sessionTimeRange(sessions[2]), '23:30–09-25 00:30')
  assert.equal(sessionTimeRange(sessions[3]), '10:30–训练中')
  assert.equal(sessionTimeRange(sessions[0]), '14:00–15:00')
})
test('empty personal history renders no invented training days', () => {
  assert.deepEqual(personalHistoryDays(null), [])
  assert.deepEqual(personalHistoryDays({ trainingSessions: [], sessions: [] }), [])
})
