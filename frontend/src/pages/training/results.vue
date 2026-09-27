<template>
  <view class="page">
    <NavBar :title="sessionId ? '训练成绩' : selectedDate ? '当日训练' : '我的成绩'" subtitle="EVERY ROUND COUNTS"><template #action><button class="text-button" @tap="back">返回 ↗</button></template></NavBar>
    <view class="page-content results-content">
      <view v-if="error" class="error-message"><text>{{ error }}</text><button class="text-button" :disabled="busy" @tap="load">重新加载 ↻</button></view>
      <view v-if="loading && !detail && !history" class="state-card">正在同步训练成绩…</view>
      <template v-if="detail">
        <view class="session-card"><text class="eyebrow">{{ detail.session.endedAt ? 'SESSION COMPLETE' : 'TRAINING IN PROGRESS' }}</text><view class="page-heading">{{ detail.session.deviceName }}</view><text class="muted">{{ dateLabel(detail.session.startedAt) }}</text><text class="hint">训练时间 {{ sessionTimeRange(detail.session) }}</text><text class="hint">共登记 {{ detail.attempts.length }} 轮 · {{ detail.session.actualDurationMin != null ? `累计训练 ${detail.session.actualDurationMin} 分钟` : detail.session.endedAt ? '训练时长未记录' : '训练中' }}</text><view v-for="result in modeResults" :key="result.mode" class="best-line"><view><text>{{ modeLabel(result.mode) }}</text><text class="round-time">{{ detail.session.endedAt ? '本次最终成绩' : '本次当前最高' }}</text></view><text class="best-value">{{ score(detail.session.endedAt ? result.finalTotal : result.bestTotal) }}<text class="unit"> 环</text></text></view><text v-if="!modeResults.length" class="hint">尚未登记成绩，各模式分别记录最高分。</text></view>
        <view class="entry-card"><view class="section-title">本次训练表现</view><view class="mode-options"><button v-for="choice in chartModes" :key="choice" class="mode-option" :class="{ selected: chartMode === choice }" @tap="chartMode = choice">{{ modeLabel(choice) }}</button></view><RoundTrend :attempts="chartAttempts" :mode="chartMode" /></view>
        <view v-if="detail.session.endedAt && !detail.session.historical" class="resume-card"><button class="resume-button" :disabled="!detail.canResume || busy || loading" :loading="resuming" @tap="confirmResume">继续本时段训练</button><text class="hint">{{ detail.canResume ? '误触结束可继续原训练，已登记成绩保留，不重复计算预约次数。' : detail.resumeUnavailableReason }}</text></view>
        <text v-if="detail.session.historical" class="hint">教员补录 · {{ detail.session.recordedByName }} · {{ dateLabel(detail.session.recordedAt) }}。如需更正，请联系教员。</text>
        <view v-if="!detail.session.historical" class="entry-card">
          <view class="section-title">{{ detail.session.endedAt ? '补登训练成绩' : '登记本轮成绩' }}</view>
          <view class="mode-options"><button v-for="choice in modes" :key="choice.value" class="mode-option" :class="{ selected: mode === choice.value }" :disabled="busy || loading" @tap="changeMode(choice.value)">{{ choice.label }}</button></view>
          <text class="hint">每轮先选择模式，再填写平板显示的分组总成绩。同一时段可交替训练两种模式。</text>
          <view v-for="(count, index) in spec" :key="index" class="score-input-row"><view class="group-label"><text>第 {{ index + 1 }} 组</text><text class="round-time">{{ count }} 发总成绩</text></view><input v-model="groups[index]" class="score-input" type="digit" :maxlength="5" :disabled="busy" :aria-label="`第 ${index + 1} 组 ${count} 发总成绩`"/><text>环</text></view>
          <view class="round-total"><text>本轮合计</text><text>{{ score(roundTotal) }} 环</text></view>
          <text v-if="inputError" class="field-error">{{ inputError }}</text>
          <button class="primary-button save-round" :loading="saving" :disabled="busy || loading" @tap="save">保存本轮成绩</button>
          <text class="hint">{{ detail.session.endedAt ? '补登后立即更新该模式的本次最高成绩与个人历史最佳，无需恢复训练。' : '结束时分别选取决赛、资格赛各自最高的一轮；所有轮次都会保留。' }}</text>
        </view>
        <view class="section-heading"><text class="section-title">本次各轮成绩</text><text class="hint">{{ detail.attempts.length }} 轮</text></view>
        <view class="table"><view class="table-row table-head"><text>轮次 / 登记时间</text><text>总成绩</text></view><view v-for="(attempt, index) in detail.attempts" :key="attempt.id" class="table-row"><view><text>第 {{ index + 1 }} 轮 · {{ modeLabel(attempt.mode || detail.session.mode) }}</text><text class="round-time">{{ attempt.recordedAt.slice(11, 16) }}</text><text class="round-time group-summary">{{ groupSummary(attempt.groupTotals) }}</text></view><view class="round-score"><text v-if="isModeBest(attempt)" class="badge">本模式最高</text><text>{{ score(attempt.totalScore) }} 环</text></view></view><view v-if="!detail.attempts.length" class="empty">{{ modeResults.some(result => result.finalTotal != null) ? '此记录由旧版直接录入最终成绩，没有逐轮记录。' : '尚未登记轮次成绩。' }}</view></view>
        <button v-if="!detail.session.endedAt" class="finish-button" :disabled="busy || loading" :loading="finishing" @tap="confirmFinish">结束训练并确定最终成绩</button>
        <button class="text-button history-link" @tap="goHistory">查看个人历史成绩 ↗</button>
      </template>
      <template v-if="history && !sessionId">
        <template v-if="!selectedDate">
          <text class="eyebrow">ONE DAY AT A TIME</text><view class="page-heading">每天的训练，都有记录</view><text class="hint intro">选择日期，查看当天各次训练与成绩走势。按实际训练开始日归属，补登仍计入原训练日。</text>
          <button v-for="day in historyDays" :key="day.date" class="day-card" @tap="openDay(day.date)">
            <view class="date-tile"><text>{{ day.date.slice(8) }}</text><text>{{ day.date.slice(0, 7).replace('-', ' / ') }}</text></view>
            <view class="day-copy"><text class="day-title">{{ weekday(day.date) }}</text><text class="hint">{{ day.sessions.length }} 次训练</text><text v-for="best in day.bests" :key="best.weapon + best.mode" class="small">{{ weaponLabel(best.weapon) }} · {{ modeLabel(best.mode) }}<text class="daily-best">当日最佳 {{ score(best.totalScore) }} 环</text></text><text v-if="!day.bests.length" class="small">{{ day.sessions.some(row => !row.endedAt) ? '含进行中的训练' : '待登记成绩' }}</text></view><text class="arrow">↗</text>
          </button>
          <view v-if="!historyDays.length" class="state-card">还没有训练记录</view>
          <view class="section-heading"><text class="section-title">个人历史最佳</text></view><text class="hint">不同器械、赛制分别记录；训练结束后自动同步。</text>
          <view class="table best-table"><view class="table-row table-head"><text>项目</text><text>历史最高</text></view><view v-for="row in history.personalBests" :key="row.weapon + row.mode" class="table-row"><view><text>{{ weaponLabel(row.weapon) }}</text><text class="round-time">{{ modeLabel(row.mode) }}</text></view><text class="best-value">{{ score(row.totalScore) }}<text class="unit"> 环</text></text></view><view v-if="!history.personalBests.length" class="empty">完成训练并登记成绩后，将在这里记录个人最佳。</view></view>
        </template>
        <template v-else>
          <text class="eyebrow">YOUR DAILY TRAINING</text><view class="page-heading">{{ selectedDate.replaceAll('-', ' / ') }}</view><text class="hint intro">{{ weekday(selectedDate) }} · {{ selectedDay?.sessions.length || 0 }} 次训练 · 按开始时间排列</text>
          <view v-if="selectedDay?.bests.length" class="session-card"><view class="section-title">当日最佳</view><view v-for="best in selectedDay.bests" :key="best.weapon + best.mode" class="daily-summary"><text>{{ weaponLabel(best.weapon) }} · {{ modeLabel(best.mode) }}</text><text>{{ score(best.totalScore) }} 环</text></view></view>
          <button v-for="row in selectedDay?.sessions || []" :key="row.id" class="session-option" @tap="openSession(row.id)"><view class="session-option-heading"><text>{{ sessionTimeRange(row) }}</text><text class="small">{{ row.endedAt ? '已结束' : '训练中' }}</text></view><text class="hint">{{ row.deviceName }}</text><view v-for="result in row.results" :key="result.mode" class="daily-summary"><text>{{ modeLabel(result.mode) }}</text><text>{{ score(result.totalScore) }} 环</text></view><text v-if="!row.results.length" class="hint">{{ row.endedAt ? '尚无最终成绩，可进入详情补登' : '进入详情查看当前轮次' }}</text><text class="session-link">训练详情与折线图 ↗</text></button>
          <view v-if="!selectedDay" class="state-card">当天暂无训练记录</view>
        </template>
      </template>
    </view>
  </view>
</template>
<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow, onHide } from '@dcloudio/uni-app'
import NavBar from '@/components/NavBar.vue'
import RoundTrend from '@/components/RoundTrend.vue'
import { personalHistoryDays, sessionTimeRange } from '@/domain/personal-history.js'
import { weekday } from '@/domain/booking.js'
import { trainingService } from '@/services/training.js'
import { groupError, groupSpec, sumGroups, modeLabel, weaponLabel, finishMessage, resultModes, isAttemptBest, finishConfirmation } from '@/domain/training.js'
const selectedDate = ref(''), chartMode = ref('final_')
const sessionId = ref(null), detail = ref(null), history = ref(null), groups = ref(['', '', '']), mode = ref('final_'), error = ref(''), inputError = ref('')
const loading = ref(false), saving = ref(false), finishing = ref(false), resuming = ref(false)
const busy = computed(() => saving.value || finishing.value || resuming.value)
let generation = 0, requestKey = '', submittedPayload = '', viewerToken = '', modeInitialized = false
const modes = [{value:'final_',label:'决赛 · 10+10+4 发'},{value:'qualifying',label:'资格赛 · 6×10 发'}]
const spec = computed(() => groupSpec(mode.value)), roundTotal = computed(() => sumGroups(groups.value))
function groupSummary(value) { try { return JSON.parse(value).map(n => `${n}环`).join(' + ') } catch { return '' } }
const drafts = { final_: ['', '', ''], qualifying: ['', '', '', '', '', ''] }
const modeResults = computed(() => resultModes(detail.value))
const historyDays = computed(() => personalHistoryDays(history.value))
const selectedDay = computed(() => historyDays.value.find(day => day.date === selectedDate.value))
const chartAttempts = computed(() => (detail.value?.attempts || []).map(row => ({ ...row, mode: row.mode || detail.value.session.mode })))
const chartModes = computed(() => [...new Set([...modeResults.value.map(row => row.mode), ...chartAttempts.value.map(row => row.mode)])])
function isModeBest(attempt) { return isAttemptBest(attempt, detail.value) }
function changeMode(value) {
  if (mode.value === value) return
  drafts[mode.value] = [...groups.value]
  mode.value = value; groups.value = [...drafts[value]]; inputError.value = ''
}
const score = value => value == null ? '—' : Number(value).toFixed(1)
const dateLabel = value => value ? value.slice(0, 16).replace('T', ' ') : ''
function openDay(date) { selectedDate.value = date; uni.pageScrollTo({ scrollTop: 0, duration: 0 }) }
function back() { if (!sessionId.value && selectedDate.value) { selectedDate.value = ''; uni.pageScrollTo({ scrollTop: 0, duration: 0 }); return } getCurrentPages().length > 1 ? uni.navigateBack() : uni.switchTab({ url: '/pages/my/my' }) }
function goHistory() { uni.navigateTo({ url: '/pages/training/results' }) }
function openSession(id) { uni.navigateTo({ url: `/pages/training/results?sessionId=${id}` }) }
async function load() {
  const currentToken = uni.getStorageSync('token')
  if (currentToken !== viewerToken) { detail.value = null; history.value = null; selectedDate.value = ''; groups.value = spec.value.map(() => ''); drafts[mode.value] = [...groups.value]; requestKey = ''; submittedPayload = ''; viewerToken = currentToken; modeInitialized = false; drafts.final_ = ['', '', '']; drafts.qualifying = ['', '', '', '', '', ''] }
  if (!currentToken) { error.value = '请先登录后查看本人成绩'; return }
  const id = ++generation, token = uni.getStorageSync('token')
  loading.value = true; error.value = ''
  try {
    const data = sessionId.value ? await trainingService.results(sessionId.value) : await trainingService.history()
    if (id !== generation || token !== uni.getStorageSync('token')) return
    if (sessionId.value) { detail.value = data; if (!modeInitialized) { changeMode(data.session.mode); modeInitialized = true } if (!chartModes.value.includes(chartMode.value)) chartMode.value = chartModes.value[0] || data.session.mode } else { if (!Array.isArray(data.trainingSessions)) throw new Error('服务端尚未更新按日训练记录，请更新后重试'); history.value = data }
  } catch (err) { if (id === generation) { error.value = err.message; detail.value = null; history.value = null } }
  finally { if (id === generation) loading.value = false }
}
async function save() {
  if (busy.value || !detail.value || loading.value) return
  inputError.value = groupError(groups.value, mode.value)
  if (inputError.value) return
  const values = groups.value.map(Number), payloadKey = JSON.stringify([mode.value, values])
  if (!requestKey || submittedPayload !== payloadKey) { requestKey = `round-${Date.now()}-${Math.random().toString(36).slice(2)}`; submittedPayload = payloadKey }
  const token = uni.getStorageSync('token')
  saving.value = true; error.value = ''
  try {
    await trainingService.register(sessionId.value, { requestKey, mode: mode.value, groupTotals: values })
    if (token !== uni.getStorageSync('token')) return
    groups.value = spec.value.map(() => ''); drafts[mode.value] = [...groups.value]; requestKey = ''; submittedPayload = ''
    uni.showToast({ title: '本轮成绩已保存', icon: 'success' }); await load()
  } catch (err) { error.value = err.message }
  finally { saving.value = false }
}
function confirmResume() {
  if (busy.value || !detail.value?.canResume) return
  uni.showModal({ title: '继续本时段训练？', content: '请确认本人已到场。系统将再次检查设备和教练到岗状态，恢复后开始累计训练时长。', confirmText: '继续训练', success: async result => {
    if (!result.confirm || busy.value) return
    resuming.value = true
    try { await trainingService.resume(sessionId.value); await load(); uni.showToast({title:'已继续训练',icon:'success'}) }
    catch (err) { await load(); error.value = err.message }
    finally { resuming.value = false }
  } })
}
function confirmFinish() {
  if (busy.value || !detail.value) return
  if ([...groups.value, ...drafts[mode.value === 'final_' ? 'qualifying' : 'final_']].some(value => String(value).trim())) { inputError.value = '还有未保存的成绩，请检查两种模式，保存或清空输入后再结束'; return }
  uni.showModal({ title: '结束本次训练？', content: finishConfirmation(detail.value), confirmText: '结束训练', success: async result => {
    if (!result.confirm || busy.value) return
    finishing.value = true
    try { const completed = await trainingService.finish(sessionId.value); await load(); uni.showModal({ title: '训练已结束', content: finishMessage(completed), showCancel: false }) }
    catch (err) { error.value = err.message; await load() }
    finally { finishing.value = false }
  } })
}
onLoad(options => { sessionId.value = options?.sessionId ? Number(options.sessionId) : null })
onShow(load)
onHide(() => { generation++; loading.value = false })
</script>
<style scoped>
.intro { margin-bottom: 30rpx; }
.day-card { display: flex; align-items: center; width: 100%; gap: 20rpx; padding: 28rpx 22rpx; margin-bottom: 20rpx; border: 1rpx solid var(--color-border); background: #fff; border-radius: 16rpx; text-align: left; line-height: 1.5; color: var(--color-text); }
.day-card::after, .session-option::after { border: 0; }
.date-tile { width: 120rpx; flex-shrink: 0; text-align: center; border-right: 1rpx solid var(--color-border); padding-right: 18rpx; }
.date-tile > text { display: block; }
.date-tile > text:first-child { font-size: 54rpx; line-height: 1.1; font-weight: 500; }
.date-tile > text:last-child { font-size: 18rpx; color: var(--color-text-soft); margin-top: 10rpx; }
.day-copy { flex: 1; min-width: 0; }
.day-title { font-size: 29rpx; }
.small { display: block; font-size: 21rpx; line-height: 1.6; color: var(--color-text-soft); margin-top: 8rpx; }
.daily-best { display: block; color: var(--color-text); }
.arrow { color: var(--color-primary); font-size: 30rpx; }
.daily-summary, .session-option-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; font-size: 24rpx; }
.session-option { width: 100%; background: #fff; border: 1rpx solid var(--color-border); padding: 24rpx; margin: 0 0 22rpx; border-radius: 14rpx; text-align: left; color: var(--color-text); line-height: 1.7; }
.session-option-heading { margin: 0; font-size: 30rpx; }
.session-option-heading .small { margin: 0; }
.session-link { display: block; margin-top: 20rpx; color: var(--color-primary); font-size: 23rpx; }

.resume-card { margin-bottom: 28rpx; }
.resume-button { background: var(--color-primary-soft); color: var(--color-primary); font-size: 28rpx; }
.results-content { padding-top: 36rpx; }
.session-card, .entry-card, .table { background: #fff; border: 1rpx solid var(--color-border); border-radius: 12rpx; }
.session-card, .entry-card { padding: 28rpx; margin-bottom: 24rpx; }
.best-line { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-top: 32rpx; font-size: 24rpx; }
.best-value { font-size: 38rpx; font-weight: 600; font-variant-numeric: tabular-nums; }
.unit { font-size: 21rpx; font-weight: 400; }
.hint { display: block; font-size: 23rpx; line-height: 1.7; color: var(--color-text-soft); margin-top: 10rpx; }
.mode-options { display: flex; gap: 12rpx; margin-top: 20rpx; }
.mode-option { flex: 1; margin: 0; padding: 14rpx 6rpx; font-size: 23rpx; line-height: 1.7; background: var(--color-bg); color: var(--color-text); border: 1rpx solid var(--color-border); }
.mode-option.selected { border-color: var(--color-primary); background: #ebe9f7; }
.mode-option.selected[disabled] { color: var(--color-primary); }
.mode-option::after { border: 0; }
.group-label { flex-shrink: 0; font-size: 25rpx; }
.round-total { display: flex; justify-content: space-between; font-size: 30rpx; font-weight: 600; margin-bottom: 24rpx; }
.group-summary { max-width: 350rpx; overflow-wrap: anywhere; }
.score-input-row { display: flex; align-items: center; gap: 16rpx; border-bottom: 1rpx solid var(--color-border); margin: 24rpx 0; padding: 16rpx 0; }
.score-input { flex: 1; text-align: right; min-width: 0; height: 76rpx; font-size: 40rpx; }
.table { overflow: hidden; }
.table-row { display: flex; justify-content: space-between; align-items: center; gap: 20rpx; padding: 24rpx; border-bottom: 1rpx solid var(--color-border); font-size: 26rpx; }
.table-row:last-child { border-bottom: 0; }
.table-head { background: #f7f7fb; color: var(--color-text-soft); font-size: 22rpx; }
.round-time { display: block; color: var(--color-text-soft); font-size: 21rpx; margin-top: 8rpx; }
.round-score { display: flex; flex-direction: column; align-items: flex-end; gap: 8rpx; flex-shrink: 0; }
.badge { font-size: 20rpx; color: #4f856c; }
.empty { padding: 36rpx 24rpx; font-size: 24rpx; line-height: 1.7; color: var(--color-text-soft); }
.finish-button { margin: 28rpx 0 12rpx; font-size: 27rpx; background: var(--color-text); color: #fff; border-radius: 10rpx; line-height: 88rpx; }
.finish-button::after, .history-row::after { border: 0; }
.history-link { margin: 20rpx auto; }
.best-table { margin-top: 28rpx; }
.history-row { background: #fff; border-radius: 0; margin: 0; text-align: left; line-height: 1.6; color: var(--color-text); }
.history-copy { min-width: 0; }
.field-error, .error-message { font-size: 24rpx; color: var(--color-danger); line-height: 1.7; }
.error-message { margin-bottom: 24rpx; }
</style>
