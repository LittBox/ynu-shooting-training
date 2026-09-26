export const modeLabel = mode => mode === 'final_' ? '决赛 · 24 发' : '资格赛 · 60 发'
export const weaponLabel = weapon => weapon === 'pistol' ? '气手枪' : '气步枪'
export const groupSpec = mode => mode === 'final_' ? [10, 10, 4] : [10, 10, 10, 10, 10, 10]
export function groupError(values, mode) {
  const spec = groupSpec(mode)
  if (values.length !== spec.length) return '请完整填写当前模式的每一组成绩'
  for (let i = 0; i < spec.length; i++) {
    const text = String(values[i] ?? '').trim(), max = spec[i] * 10
    if (!text) return `请填写第 ${i + 1} 组（${spec[i]} 发）的总成绩`
    if (!/^\d+(\.\d)?$/.test(text) || Number(text) > max) return `第 ${i + 1} 组须为 0–${max} 环，最多一位小数`
  }
  return ''
}
export const sumGroups = values => Math.round(values.reduce((sum, value) => sum + Number(value || 0), 0) * 10) / 10
export function resultModes(detail) {
  if (!detail) return []
  if (Array.isArray(detail.modeResults)) return detail.modeResults
  return detail.bestTotal != null || detail.finalTotal != null ? [{ mode: detail.session.mode, bestTotal: detail.bestTotal, finalTotal: detail.finalTotal }] : []
}
export function isAttemptBest(attempt, detail) {
  const mode = attempt.mode || detail.session.mode
  return resultModes(detail).some(row => row.mode === mode && row.bestTotal === attempt.totalScore)
}
export function finishConfirmation(detail) {
  const rows = resultModes(detail)
  if (!rows.length) return '尚未登记成绩。可先结束训练，之后从“我的成绩”补登。'
  return `已登记 ${detail.attempts.length} 轮，将分别确定各模式最高成绩：${rows.map(row => `${modeLabel(row.mode)} ${row.bestTotal} 环`).join('；')}，并同步各自历史记录。`
}
export function finishMessage(result) {
  if (result.finalScores?.length) return result.finalScores.map(row => `${modeLabel(row.mode)}：${row.finalScore} 环${row.personalBest ? '（个人历史最佳）' : ''}`).join('；') + '。已同步至个人历史成绩。'

  if (result.finalScore === null || result.finalScore === undefined) return '训练已结束，未登记成绩，本次仅保留训练时长。'
  return `本次最终成绩 ${result.finalScore} 环。${result.personalBest ? '已同步为该项目个人历史最佳！' : '已同步至个人历史成绩。'}`
}
