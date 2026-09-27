<template>
  <view class="page">
    <NavBar :title="selectedId ? '单次训练详情' : selectedDate ? '当日训练成绩' : '学员成绩管理'" subtitle="DAILY TRAINING REVIEW"><template #action><button class="text-button" :disabled="saving" @tap="back">返回 ↗</button></template></NavBar>
    <view class="page-content coach-content">
      <view v-if="error" class="error-message"><text>{{ error }}</text><button class="text-button" :disabled="busy" @tap="reload">重新加载 ↻</button></view>
      <template v-if="!selectedDate && !selectedId">
        <view class="intro"><text class="eyebrow">ONE DAY AT A TIME</text><view class="page-heading">每天的训练，都有记录</view><text class="hint">选择一天，查看学员成绩与训练过程。</text></view>
        <button class="primary-button history-entry" :disabled="busy" @tap="openHistoryEntry">＋ 补录历史训练</button>
        <button v-for="day in days" :key="day.date" class="day-card" :disabled="busy" @tap="openDay(day.date)">
          <view class="date-tile"><text>{{ day.date.slice(8) }}</text><text>{{ day.date.slice(0, 7).replace('-', ' / ') }}</text></view>
          <view class="day-copy"><text class="day-title">{{ weekday(day.date) }}<text v-if="day.date === today" class="today-tag">今天</text></text><text class="hint">{{ day.members }} 位学员 · {{ day.sessions }} 次训练</text></view><text class="arrow">↗</text>
        </button>
        <view v-if="loading && !days.length" class="state-card">正在加载训练日期…</view>
        <view v-if="loaded && !days.length && !error" class="state-card">还没有训练记录</view>
        <button v-if="hasMore" class="secondary-button" :disabled="busy" @tap="loadDays(true)">{{ loading ? '加载中…' : '更早的训练日期' }}</button>
      </template>
      <template v-else-if="selectedDate && !selectedId">
        <view class="day-heading"><view><text class="eyebrow">DAILY LEADERBOARD</text><view class="page-heading">{{ selectedDate.replaceAll('-', ' / ') }}</view><text v-if="dayData" class="hint">{{ weekday(selectedDate) }} · {{ dayData.memberCount }} 位学员 · {{ dayData.sessionCount }} 次训练</text></view><button class="text-button" :disabled="busy" @tap="loadDay">刷新 ↻</button></view>
        <view class="filter-card">
          <view class="filter-row"><text class="filter-label">枪种</text><view class="choices"><button v-for="choice in weapons" :key="choice.value" :class="{ active: weapon === choice.value }" @tap="weapon = choice.value">{{ choice.label }}</button></view></view>
          <view class="filter-row"><text class="filter-label">赛制</text><view class="choices"><button v-for="choice in modes" :key="choice.value" :class="{ active: mode === choice.value }" @tap="mode = choice.value">{{ choice.label }}</button></view></view>
          <view class="search"><input v-model="search" placeholder="查找当天的姓名或学号" :maxlength="100" confirm-type="search"/><button v-if="search" class="text-button" @tap="search = ''">清除</button></view>
        </view>
        <text class="ranking-rule">仅统计这一天 · 每人取同项目最高一轮 · 同分并列</text>
        <text class="hint">不同枪种、赛制分别排名；训练中成绩随登记更新。</text>
        <view v-for="group in dayGroups" :key="group.weapon + group.mode" class="ranking-group">
          <view class="group-heading"><text>{{ weaponLabel(group.weapon) }} · {{ modeLabel(group.mode) }}</text><text>{{ group.members.length }} 人</text></view>
          <view v-for="row in group.members" :key="row.member.id" class="rank-card">
            <view class="rank-main"><text class="rank" :class="{ podium: row.rank && row.rank <= 3 }">{{ row.rank == null ? '—' : String(row.rank).padStart(2, '0') }}</text><view class="member-copy"><text class="member-name">{{ row.member.name }}</text><text class="small">{{ row.sessions.length }} 次训练 · {{ row.rounds }} 轮已登记</text></view><view class="rank-score"><text>{{ score(row.bestTotal) }}</text><text class="small">{{ row.bestTotal == null ? '未登记成绩' : '当日最高 / 环' }}</text></view></view>
            <view class="rank-footer"><text class="small">学号 {{ row.member.studentNo || '未登记' }}</text><button class="text-button" :disabled="busy" @tap="openMember(row, group)">{{ expanded === rowKey(row, group) ? '收起训练' : '训练详情 ↗' }}</button></view>
            <view v-if="expanded === rowKey(row, group)" class="session-list"><button v-for="entry in row.sessions" :key="entry.session.id" class="session-option" :disabled="busy" @tap="open(entry.session.id, group.mode)"><view><text>{{ timeLabel(entry.session.startedAt) }}–{{ entry.session.endedAt ? timeLabel(entry.session.endedAt) : '训练中' }}</text><text class="small">{{ entry.session.deviceName }} · 本赛制 {{ entry.rounds }} 轮</text></view><text>{{ score(entry.bestTotal) }} 环 ↗</text></button></view>
          </view>
        </view>
        <view v-if="loading && !dayData" class="state-card">正在汇总当日训练…</view>
        <view v-else-if="dayData && !dayGroups.length" class="state-card">当前筛选条件下没有学员记录</view>
        <text v-if="dayData" class="hint">日期按训练开始当天（北京时间）归属，补登或更正仍计入原训练日。旧版仅有总成绩的记录不计为已登记轮次。</text>
      </template>
      <template v-else-if="detail">
        <view class="card session-overview"><view class="record-header"><text class="eyebrow">{{ detail.results.session.endedAt ? 'SESSION COMPLETE' : 'TRAINING IN PROGRESS' }}</text><text class="tag">{{ detail.results.session.endedAt ? '已结束' : '训练中' }}</text></view><view class="page-heading">{{ detail.member.name }}</view><text class="hint">{{ detail.results.session.deviceName }} · 学号 {{ detail.member.studentNo || '未登记' }}</text>
          <text v-if="detail.results.session.historical" class="hint">教员补录 · {{ detail.results.session.recordedByName }} · {{ dateLabel(detail.results.session.recordedAt) }}</text>
          <view class="time-grid"><view><text class="small">开始训练</text><text>{{ dateLabel(detail.results.session.startedAt) }}</text></view><view><text class="small">结束训练</text><text>{{ detail.results.session.endedAt ? dateLabel(detail.results.session.endedAt) : '尚未结束' }}</text></view></view>
          <view class="session-facts"><text>共登记 {{ detail.results.attempts.length }} 轮</text><text>{{ detail.results.session.actualDurationMin != null ? '累计训练 ' + detail.results.session.actualDurationMin + ' 分钟' : detail.results.session.endedAt ? '训练时长未记录' : '训练中' }}</text></view>
        </view>
        <view class="card"><view class="section-title">本次训练表现</view><view class="choices detail-modes"><button v-for="choice in detailModes" :key="choice" :class="{ active: chartMode === choice }" @tap="chartMode = choice">{{ modeLabel(choice) }}</button></view><RoundTrend :attempts="detail.results.attempts" :mode="chartMode" /><view v-for="row in detail.results.modeResults" :key="row.mode" class="summary"><text>{{ modeLabel(row.mode) }}最高</text><text>{{ score(row.bestTotal) }} 环</text></view></view>
        <view class="section-heading"><text class="section-title">每一轮的训练记录</text><text class="small">全部赛制 · 按登记顺序</text></view>
        <text class="hint detail-note">登记时间可能晚于实际训练时间。更正后会更新对应训练日排名与已发布成绩。</text>
        <view v-for="(row, index) in detail.editable" :key="`${row.legacy}-${row.id}`" class="card round-card">
          <view class="record-header"><text>{{ row.legacy ? '旧版最终成绩' : `第 ${index + 1} 轮` }} · {{ modeLabel(row.mode) }}</text><text class="round-total">{{ score(row.totalScore) }} 环</text></view>
          <text v-if="!row.legacy" class="hint">登记时间 {{ dateLabel(attemptTime(row.id)) }}</text>
          <view v-if="row.groupTotals" class="group-totals"><view v-for="(value, i) in parsedGroups(row.groupTotals)" :key="i"><text class="small">第 {{ i + 1 }} 组 · {{ groupSpec(row.mode)[i] }} 发</text><text>{{ score(value) }} 环</text></view></view>
          <text v-if="row.legacy" class="hint">旧版仅保存总成绩，没有逐轮明细；不计入轮数与折线图。</text>
          <button v-if="!editing || editing.id !== row.id || editing.legacy !== row.legacy" class="text-button edit-button" :disabled="busy || !!editing" @tap="edit(row)">更正成绩 ↗</button>
          <view v-else class="editor">
            <template v-if="!row.legacy"><view v-for="(count, i) in groupSpec(row.mode)" :key="i" class="field"><text>第 {{ i + 1 }} 组 · {{ count }} 发</text><input v-model="groups[i]" type="digit" :maxlength="5" :disabled="saving"/><text>环</text></view><view class="summary"><text>更正后合计</text><text>{{ score(sumGroups(groups)) }} 环</text></view></template>
            <view v-else class="field"><text>更正后总成绩</text><input v-model="total" type="digit" :maxlength="5" :disabled="saving"/><text>环</text></view>
            <textarea v-model="reason" class="reason" placeholder="更正原因，例如：第二组多输入了 10 环" :maxlength="200" :disabled="saving"/>
            <text v-if="inputError" class="error-message">{{ inputError }}</text>
            <view class="editor-actions"><button class="secondary-button" :disabled="saving" @tap="editing = null">取消</button><button class="primary-button" :disabled="busy" :loading="saving" @tap="confirmSave">保存更正</button></view>
          </view>
        </view>
        <view v-if="!detail.editable.length" class="state-card">本次训练尚未登记成绩</view>
      </template>
      <view v-else-if="loading" class="state-card">正在同步成绩…</view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { onShow, onHide, onLoad } from '@dcloudio/uni-app'
import NavBar from '@/components/NavBar.vue'
import RoundTrend from '@/components/RoundTrend.vue'
import { trainingService } from '@/services/training.js'
import { groupError, groupSpec, sumGroups, modeLabel, weaponLabel } from '@/domain/training.js'
import { weekday, venueToday } from '@/domain/booking.js'
import { filterDayGroups } from '@/domain/coach-results.js'
const days = ref([]), selectedDate = ref(''), dayData = ref(null), search = ref(''), weapon = ref(''), mode = ref(''), expanded = ref('')
const selectedId = ref(null), detail = ref(null), editing = ref(null), chartMode = ref('final_')
const groups = ref([]), total = ref(''), reason = ref(''), error = ref(''), inputError = ref('')
const loading = ref(false), saving = ref(false), loaded = ref(false), hasMore = ref(false), today = ref(venueToday())
const busy = computed(() => loading.value || saving.value)
const weapons = [{ value: '', label: '全部' }, { value: 'pistol', label: '气手枪' }, { value: 'rifle', label: '气步枪' }]
const modes = [{ value: '', label: '全部' }, { value: 'final_', label: '决赛' }, { value: 'qualifying', label: '资格赛' }]
const dayGroups = computed(() => filterDayGroups(dayData.value, weapon.value, mode.value, search.value))
const detailModes = computed(() => [...new Set([...(detail.value?.results.modeResults || []).map(row => row.mode), ...(detail.value?.results.attempts || []).map(row => row.mode)])])
watch([groups, total, reason], () => { inputError.value = '' }, { deep: true })
let page = 0, generation = 0, viewerToken = ''
const dateLabel = value => value?.slice(0, 19).replace('T', ' ') || '未记录'
const timeLabel = value => value?.slice(11, 16) || '未记录'
const score = value => value == null ? '—' : Number(value).toFixed(1)
const rowKey = (row, group) => `${group.weapon}-${group.mode}-${row.member.id}`
function parsedGroups(value) { try { return JSON.parse(value) } catch { return [] } }
function attemptTime(id) { return detail.value?.results.attempts.find(row => row.id === id)?.recordedAt }
function scrollTop() { uni.pageScrollTo({ scrollTop: 0, duration: 0 }) }
function clearPrivateData() { days.value = []; dayData.value = null; detail.value = null; editing.value = null; hasMore.value = false; loaded.value = false }
async function fetchView(fetcher, apply, clear) {
  const id = ++generation, token = uni.getStorageSync('token')
  loading.value = true; error.value = ''
  try {
    const data = await fetcher()
    if (id === generation && token === uni.getStorageSync('token')) apply(data)
  } catch (err) {
    if (id === generation) { error.value = err.message; clear(); if (err.code === 401 || err.code === 403) clearPrivateData() }
  } finally { if (id === generation) loading.value = false }
}
function loadDays(append = false) {
  if (saving.value) return
  const next = append ? page + 1 : 0
  return fetchView(() => trainingService.coachDays(next), data => {
    days.value = append ? [...days.value, ...data.days] : data.days; hasMore.value = data.hasMore; page = next; loaded.value = true
  }, () => { if (!append) { days.value = []; hasMore.value = false } })
}
function loadDay() { return fetchView(() => trainingService.coachDay(selectedDate.value), data => { dayData.value = data }, () => { dayData.value = null }) }
function loadDetail() {
  return fetchView(() => trainingService.coachDetail(selectedId.value), data => {
    detail.value = data
    if (!detailModes.value.includes(chartMode.value)) chartMode.value = detailModes.value[0] || data.results.session.mode
  }, () => { detail.value = null })
}
function openHistoryEntry() { uni.navigateTo({ url: '/pages/training/history-entry' }) }
function openDay(date) { if (busy.value) return; selectedDate.value = date; dayData.value = null; search.value = ''; expanded.value = ''; scrollTop(); loadDay() }
function openMember(row, group) {
  if (row.sessions.length === 1) open(row.sessions[0].session.id, group.mode)
  else expanded.value = expanded.value === rowKey(row, group) ? '' : rowKey(row, group)
}
function open(id, preferredMode) { if (busy.value) return; selectedId.value = id; chartMode.value = preferredMode; detail.value = null; editing.value = null; scrollTop(); loadDetail() }
function reload() { if (saving.value) return; editing.value = null; return selectedId.value ? loadDetail() : selectedDate.value ? loadDay() : loadDays() }
function back() {
  if (saving.value) return
  if (editing.value) { uni.showModal({ title: '放弃本次未保存的更正？', success: r => { if (r.confirm) { editing.value = null; back() } } }); return }
  generation++; loading.value = false; error.value = ''; scrollTop()
  if (selectedId.value) { selectedId.value = null; detail.value = null; loadDay(); return }
  if (selectedDate.value) { selectedDate.value = ''; dayData.value = null; loadDays(); return }
  getCurrentPages().length > 1 ? uni.navigateBack() : uni.switchTab({ url: '/pages/my/my' })
}
function edit(row) { editing.value = { ...row }; groups.value = row.groupTotals ? parsedGroups(row.groupTotals).map(String) : []; total.value = String(row.totalScore); reason.value = ''; inputError.value = '' }
function confirmSave() {
  if (busy.value || !editing.value) return
  const row = { ...editing.value }, sessionId = selectedId.value, modalToken = uni.getStorageSync('token'), modalGeneration = generation
  const newTotal = row.legacy ? Number(total.value) : sumGroups(groups.value)
  inputError.value = row.legacy ? (!/^\d+(\.\d)?$/.test(total.value.trim()) || newTotal > (row.mode === 'final_' ? 240 : 600) ? '请填写范围内的总成绩，最多一位小数' : '') : groupError(groups.value, row.mode)
  if (!inputError.value && !reason.value.trim()) inputError.value = '请填写更正原因'
  if (inputError.value) return
  const data = { expectedState: row.state, reason: reason.value.trim(), ...(row.legacy ? { totalScore: newTotal } : { groupTotals: groups.value.map(Number) }) }
  uni.showModal({ title: '确认更正成绩？', content: `${detail.value.member.name} · ${modeLabel(row.mode)}\n${score(row.totalScore)} 环 → ${score(newTotal)} 环\n更正将保留操作记录，并更新对应训练日排名。`, confirmText: '确认更正', success: async answer => {
    if (!answer.confirm || busy.value || modalGeneration !== generation || modalToken !== uni.getStorageSync('token')) return
    const token = modalToken, id = ++generation
    saving.value = true; error.value = ''
    try {
      const updated = await trainingService.correct(sessionId, row, data)
      if (id !== generation || token !== uni.getStorageSync('token')) return
      detail.value = updated; editing.value = null; dayData.value = null; uni.showToast({ title: '成绩已更正', icon: 'success' })
    } catch (err) {
      if (id === generation) { error.value = err.message; if (err.code === 401 || err.code === 403) clearPrivateData(); else if (err.code === 409) { editing.value = null; await loadDetail(); error.value = err.message } }
    } finally { saving.value = false }
  } })
}
onLoad(options => {
  if (/^\d{4}-\d{2}-\d{2}$/.test(options?.date || '')) {
    selectedDate.value = options.date
    if (/^\d+$/.test(options?.sessionId || '')) selectedId.value = Number(options.sessionId)
    viewerToken = uni.getStorageSync('token')
  }
})
onShow(() => {
  today.value = venueToday()
  const token = uni.getStorageSync('token')
  if (token !== viewerToken) { clearPrivateData(); selectedId.value = null; selectedDate.value = ''; viewerToken = token; search.value = ''; weapon.value = ''; mode.value = '' }
  if (!saving.value) reload()
})
onHide(() => { generation++; loading.value = false })
</script>
<style scoped>
.coach-content { padding-top: 28rpx; padding-bottom: 64rpx; }
.hint { display: block; font-size: 23rpx; line-height: 1.7; color: var(--color-text-soft); margin-top: 10rpx; overflow-wrap: anywhere; }
.small { display: block; font-size: 20rpx; line-height: 1.6; color: var(--color-text-soft); overflow-wrap: anywhere; }
.history-entry { margin-bottom: 28rpx; }
.intro { margin: 10rpx 0 32rpx; }
.day-card { display: flex; align-items: center; width: 100%; gap: 24rpx; padding: 28rpx 24rpx; margin-bottom: 20rpx; border: 1rpx solid var(--color-border); background: #fff; border-radius: 16rpx; text-align: left; line-height: 1.5; color: var(--color-text); }
button::after { border: 0; }
.date-tile { width: 132rpx; flex-shrink: 0; text-align: center; border-right: 1rpx solid var(--color-border); padding-right: 20rpx; }
.date-tile > text { display: block; }
.date-tile > text:first-child { font-size: 54rpx; line-height: 1.1; font-weight: 500; }
.date-tile > text:last-child { font-size: 18rpx; color: var(--color-text-soft); margin-top: 10rpx; }
.day-copy { flex: 1; min-width: 0; }
.day-title { font-size: 29rpx; }
.today-tag { margin-left: 14rpx; font-size: 19rpx; color: var(--color-primary); }
.arrow { color: var(--color-primary); font-size: 32rpx; }
.day-heading { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-bottom: 24rpx; }
.day-heading .page-heading { font-size: 35rpx; }
.filter-card, .card { border: 1rpx solid var(--color-border); border-radius: 16rpx; background: #fff; padding: 24rpx; margin-bottom: 22rpx; }
.filter-row { display: flex; gap: 16rpx; align-items: center; margin: 12rpx 0; }
.filter-label { font-size: 23rpx; color: var(--color-text-soft); flex-shrink: 0; }
.choices { display: flex; gap: 12rpx; flex-wrap: wrap; }
.choices button { margin: 0; padding: 12rpx 22rpx; background: var(--color-bg); border: 1rpx solid transparent; border-radius: 9rpx; font-size: 23rpx; line-height: 1.5; color: var(--color-text-soft); }
.choices button.active { background: #f0eefb; color: var(--color-primary); border-color: #d8d2ee; }
.search { display: flex; align-items: center; gap: 12rpx; padding-top: 20rpx; margin-top: 20rpx; border-top: 1rpx solid var(--color-border); }
.search input { flex: 1; min-width: 0; font-size: 24rpx; height: 56rpx; }
.ranking-rule { display: block; font-size: 22rpx; color: var(--color-primary); line-height: 1.7; }
.group-heading { display: flex; justify-content: space-between; align-items: center; gap: 12rpx; margin: 34rpx 0 16rpx; font-size: 26rpx; font-weight: 600; }
.group-heading > text:last-child { font-size: 20rpx; font-weight: 400; color: var(--color-text-soft); }
.rank-card { padding: 24rpx; border: 1rpx solid var(--color-border); border-radius: 14rpx; background: #fff; margin-bottom: 14rpx; }
.rank-main { display: flex; align-items: center; gap: 20rpx; }
.rank { font-size: 34rpx; width: 48rpx; flex-shrink: 0; color: var(--color-text-soft); font-variant-numeric: tabular-nums; }
.podium { color: var(--color-primary); }
.member-copy { flex: 1; min-width: 0; }
.member-name { display: block; font-size: 29rpx; overflow-wrap: anywhere; margin-bottom: 5rpx; }
.rank-score { text-align: right; flex-shrink: 0; }
.rank-score > text:first-child { display: block; font-size: 38rpx; font-weight: 500; font-variant-numeric: tabular-nums; }
.rank-footer { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; margin-top: 14rpx; }
.rank-footer .small { min-width: 0; }
.rank-footer button { flex-shrink: 0; font-size: 22rpx; }
.session-list { border-top: 1rpx solid var(--color-border); margin-top: 14rpx; }
.session-option { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; width: 100%; margin: 0; padding: 20rpx 0; background: #fff; text-align: left; color: var(--color-text); line-height: 1.7; font-size: 23rpx; }
.session-option + .session-option { border-top: 1rpx solid var(--color-border); }
.session-option > text { flex-shrink: 0; }
.record-header, .summary { display: flex; justify-content: space-between; gap: 16rpx; align-items: center; flex-wrap: wrap; font-size: 24rpx; line-height: 1.7; }
.summary { margin-top: 20rpx; padding-top: 16rpx; border-top: 1rpx solid var(--color-border); }
.tag { font-size: 21rpx; color: var(--color-primary); }
.time-grid { display: flex; flex-direction: column; gap: 16rpx; margin: 24rpx 0; }
.time-grid > view { display: flex; justify-content: space-between; gap: 12rpx; font-size: 24rpx; font-variant-numeric: tabular-nums; }
.session-facts { display: flex; justify-content: space-between; gap: 16rpx; padding-top: 20rpx; border-top: 1rpx solid var(--color-border); font-size: 23rpx; color: var(--color-primary); }
.detail-modes { margin-top: 20rpx; }
.section-heading { display: flex; justify-content: space-between; gap: 12rpx; align-items: center; }
.detail-note { margin-bottom: 20rpx; }
.round-total { font-size: 29rpx; font-weight: 500; }
.group-totals { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12rpx; margin-top: 20rpx; }
.group-totals > view { background: var(--color-bg); border-radius: 8rpx; padding: 16rpx 8rpx; text-align: center; }
.group-totals > view > text:last-child { display: block; font-size: 25rpx; margin-top: 8rpx; }
.edit-button { margin: 16rpx 0 0 auto; }
.field { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 0; border-bottom: 1rpx solid var(--color-border); font-size: 24rpx; }
.field input { flex: 1; min-width: 0; text-align: right; height: 72rpx; font-size: 34rpx; }
.reason { box-sizing: border-box; width: 100%; height: 150rpx; margin-top: 28rpx; background: var(--color-bg); border-radius: 8rpx; padding: 18rpx; font-size: 24rpx; }
.editor-actions { display: flex; gap: 20rpx; margin-top: 24rpx; }
.editor-actions button { flex: 1; font-size: 25rpx; margin: 0; }
.error-message { display: block; font-size: 24rpx; color: var(--color-danger); line-height: 1.7; margin: 20rpx 0; }
</style>
