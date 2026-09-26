export function filterDayGroups(day, weapon = '', mode = '', search = '') {
  const query = search.trim().toLocaleLowerCase()
  return (day?.groups || []).filter(group => (!weapon || group.weapon === weapon) && (!mode || group.mode === mode))
    .map(group => ({ ...group, members: group.members.filter(row => !query ||
      `${row.member.name} ${row.member.studentNo || ''}`.toLocaleLowerCase().includes(query)) }))
    .filter(group => group.members.length)
}

export function roundSeries(attempts = [], mode) {
  return attempts.map((attempt, index) => ({ ...attempt, round: index + 1 }))
    .filter(attempt => attempt.mode === mode && Number.isFinite(attempt.totalScore))
}

export function roundStats(rounds) {
  if (!rounds.length) return null
  const values = rounds.map(row => row.totalScore)
  const min = Math.min(...values), max = Math.max(...values)
  return { count: values.length, mean: values.reduce((a, b) => a + b, 0) / values.length, min, max, range: max - min }
}

// Geometry uses rpx so the same native views render on WeChat and H5, without canvas dependencies.
export function roundChart(rounds, mode) {
  if (!rounds.length) return null
  const { min, max } = roundStats(rounds)
  const padding = Math.max(5, (max - min) * 0.2)
  const lower = Math.max(0, Math.floor((min - padding) / 5) * 5)
  const upper = Math.min(mode === 'final_' ? 240 : 600, Math.ceil((max + padding) / 5) * 5)
  const width = Math.max(520, rounds.length * 96), left = 64, right = width - 40
  const points = rounds.map((row, i) => ({ ...row,
    x: rounds.length === 1 ? (left + right) / 2 : left + i * (right - left) / (rounds.length - 1),
    y: 36 + (upper - row.totalScore) / (upper - lower) * 190
  }))
  const lines = points.slice(1).map((point, i) => {
    const previous = points[i], dx = point.x - previous.x, dy = point.y - previous.y
    return { x: previous.x, y: previous.y, width: Math.hypot(dx, dy), angle: Math.atan2(dy, dx) * 180 / Math.PI }
  })
  const ticks = [0, 0.5, 1].map(part => ({ y: 36 + part * 190, value: upper - part * (upper - lower) }))
  return { width, points, lines, ticks, lower, upper }
}
