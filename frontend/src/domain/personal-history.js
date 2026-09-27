// Use the actual training start date, never the date a score was entered or corrected.
export function personalHistoryDays(history) {
  if (!history) return []
  const results = new Map()
  for (const row of history.sessions || []) {
    if (!results.has(row.sessionId)) results.set(row.sessionId, [])
    results.get(row.sessionId).push(row)
  }
  for (const rows of results.values()) rows.sort((a, b) => a.mode.localeCompare(b.mode))
  const days = new Map(), seen = new Set()
  for (const session of history.trainingSessions || []) {
    if (!session.startedAt || seen.has(session.id)) continue
    seen.add(session.id)
    const date = session.startedAt.slice(0, 10)
    if (!days.has(date)) days.set(date, { date, sessions: [], bests: [] })
    days.get(date).sessions.push({ ...session, results: results.get(session.id) || [] })
  }
  for (const day of days.values()) {
    day.sessions.sort((a, b) => a.startedAt.localeCompare(b.startedAt) || a.id - b.id)
    const bests = new Map()
    for (const row of day.sessions.flatMap(session => session.results)) {
      const key = `${row.weapon}-${row.mode}`
      if (!bests.has(key) || row.totalScore > bests.get(key).totalScore) bests.set(key, row)
    }
    day.bests = [...bests.values()].sort((a, b) => `${a.weapon}-${a.mode}`.localeCompare(`${b.weapon}-${b.mode}`))
  }
  return [...days.values()].sort((a, b) => b.date.localeCompare(a.date))
}

export function sessionTimeRange(session) {
  const start = session.startedAt?.slice(11, 16) || '未记录'
  if (!session.endedAt) return `${start}–训练中`
  const end = session.endedAt.slice(0, 10) === session.startedAt?.slice(0, 10)
    ? session.endedAt.slice(11, 16) : session.endedAt.slice(5, 16).replace('T', ' ')
  return `${start}–${end}`
}
