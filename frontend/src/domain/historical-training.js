import { groupError, groupSpec } from './training.js'

export const newHistoryRound = (mode = 'final_') => ({ mode, groupTotals: groupSpec(mode).map(() => '') })
export function historicalTrainingError(form, now = Date.now()) {
  if (!form.userId) return '请先选择学员'
  if (!['pistol', 'rifle'].includes(form.weapon)) return '请选择枪种'
  const parse = value => {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00$/.test(value || '')) return NaN
    const ms = Date.parse(`${value}+08:00`)
    if (!Number.isFinite(ms) || new Date(ms + 8 * 3600000).toISOString().slice(0, 19) !== value) return NaN
    return ms
  }
  const start = parse(form.startedAt), end = parse(form.endedAt)
  if (!Number.isFinite(start) || !Number.isFinite(end) || form.startedAt.slice(0, 4) < '1900') return '请填写有效的训练开始和结束时间'
  if (end <= start) return '结束时间须晚于开始时间'
  if (end > now) return '历史训练结束时间不能在未来'
  if (!form.rounds?.length || form.rounds.length > 100) return '请登记 1–100 轮实际训练成绩'
  for (let i = 0; i < form.rounds.length; i++) {
    const round = form.rounds[i]
    if (!['final_', 'qualifying'].includes(round.mode)) return `第 ${i + 1} 轮请选择赛制`
    const error = groupError(round.groupTotals, round.mode)
    if (error) return `第 ${i + 1} 轮：${error}`
  }
  return ''
}
export function historicalTrainingPayload(form, requestKey) {
  return { requestKey, userId: form.userId, weapon: form.weapon, startedAt: form.startedAt, endedAt: form.endedAt,
    rounds: form.rounds.map(row => ({ mode: row.mode, groupTotals: row.groupTotals.map(Number) })) }
}
