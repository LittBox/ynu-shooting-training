import test from 'node:test'
import assert from 'node:assert/strict'
import { filterDayGroups, roundSeries, roundStats, roundChart } from '../src/domain/coach-results.js'

test('day filters combine weapon, mode and member search without changing ranks', () => {
  const row = (id, name, rank) => ({ member: { id, name, studentNo: `YNU${id}` }, rank })
  const day = { groups: [
    { weapon: 'pistol', mode: 'final_', members: [row(1, '甲', 1), row(2, '乙', 2)] },
    { weapon: 'pistol', mode: 'qualifying', members: [row(2, '乙', 1)] },
    { weapon: 'rifle', mode: 'final_', members: [row(1, '甲', 1)] }
  ] }
  assert.equal(filterDayGroups(day).length, 3)
  assert.equal(filterDayGroups(day, 'pistol', 'final_', '  ynu2 ')[0].members[0].rank, 2)
  assert.equal(filterDayGroups(day, '', 'final_', '甲').length, 2)
  assert.equal(filterDayGroups(day, 'rifle', 'qualifying').length, 0)
  assert.equal(filterDayGroups(null).length, 0)
  assert.equal(day.groups[0].members.length, 2)
})
test('round trend separates modes and preserves original round order including zero scores', () => {
  const data = [{ id: 1, mode: 'final_', totalScore: 0 }, { id: 2, mode: 'qualifying', totalScore: 560 }, { id: 3, mode: 'final_', totalScore: 220 }]
  const series = roundSeries(data, 'final_')
  assert.deepEqual(series.map(row => row.round), [1, 3])
  assert.deepEqual(roundStats(series), { count: 2, mean: 110, min: 0, max: 220, range: 220 })
  assert.equal(roundStats([]), null)
  assert.equal(roundChart([], 'final_'), null)
})
test('chart geometry handles single, equal, zero, maximum and long round series', () => {
  for (const mode of ['final_', 'qualifying']) {
    const max = mode === 'final_' ? 240 : 600
    for (const values of [[0], [max], [210, 210], [0, max], Array.from({ length: 40 }, (_, i) => 100 + i)]) {
      const series = values.map((totalScore, i) => ({ id: i, round: i + 1, totalScore }))
      const chart = roundChart(series, mode)
      assert.equal(chart.lines.length, values.length - 1)
      assert.ok(chart.upper > chart.lower)
      assert.ok(chart.lower >= 0 && chart.upper <= max)
      for (const point of chart.points) assert.ok(Number.isFinite(point.y) && point.y >= 36 && point.y <= 226)
      for (const line of chart.lines) assert.ok(Number.isFinite(line.angle) && line.width > 0)
      if (values.length === 40) assert.ok(chart.width > 520)
    }
  }
})
