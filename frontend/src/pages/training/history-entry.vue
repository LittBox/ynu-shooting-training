<template>
  <view class="page">
    <NavBar title="补录历史训练" subtitle="TRAINING HISTORY"><template #action><button class="text-button" :disabled="saving" @tap="back">返回 ↗</button></template></NavBar>
    <view class="page-content history-content">
      <text class="eyebrow">KEEP EVERY ROUND</text><view class="page-heading">把过去的训练，记下来</view>
      <text class="hint intro">填写实际训练时间和各轮成绩，按训练开始日期归档，每个项目展示当天最高成绩。</text>
      <view class="card">
        <view class="section-title">选择学员</view>
        <view v-if="member" class="selected-member"><view><text>{{ member.name }}</text><text class="hint">学号 {{ member.studentNo }}</text></view><button class="text-button" :disabled="locked" @tap="member = null">更换</button></view>
        <template v-else>
          <view class="search"><input v-model="search" :disabled="locked" :maxlength="100" placeholder="输入姓名或学号" confirm-type="search" @confirm="findMembers(false)"/><button class="text-button" :disabled="locked || searching || !search.trim()" @tap="findMembers(false)">{{ searching ? '查询中…' : '查询' }}</button></view>
          <button v-for="item in members" :key="item.id" class="member-option" :disabled="locked" @tap="member = item"><text>{{ item.name }}</text><text class="hint">学号 {{ item.studentNo }}</text></button>
          <text v-if="searched && !members.length && !searching" class="hint">未找到学员，请核对姓名或学号；学员需先登录并完善资料。</text>
          <button v-if="hasMore" class="text-button" :disabled="searching || locked" @tap="findMembers(true)">更多匹配学员</button>
        </template>
      </view>
      <view class="card">
        <view class="section-title">训练信息</view>
        <view class="choices"><button v-for="item in weapons" :key="item.value" :class="{ active: weapon === item.value }" :disabled="locked" @tap="weapon = item.value">{{ item.label }}</button></view>
        <text class="field-label">开始时间 · 北京时间</text>
        <view class="time-row"><picker mode="date" :value="startDate" start="1900-01-01" :end="today" :disabled="locked" @change="startDate = $event.detail.value"><view class="picker-value">{{ startDate }} ▾</view></picker><picker mode="time" :value="startTime" :disabled="locked" @change="startTime = $event.detail.value"><view class="picker-value">{{ startTime }} ▾</view></picker></view>
        <text class="field-label">结束时间 · 北京时间</text>
        <view class="time-row"><picker mode="date" :value="endDate" start="1900-01-01" :end="today" :disabled="locked" @change="endDate = $event.detail.value"><view class="picker-value">{{ endDate }} ▾</view></picker><picker mode="time" :value="endTime" :disabled="locked" @change="endTime = $event.detail.value"><view class="picker-value">{{ endTime }} ▾</view></picker></view>
      </view>
      <view class="section-heading"><text class="section-title">实际训练 {{ rounds.length }} 轮</text><text class="hint">逐轮填写分组总环数</text></view>
      <view v-for="(round, index) in rounds" :key="round.key" class="card">
        <view class="round-heading"><text class="section-title">第 {{ index + 1 }} 轮</text><button v-if="rounds.length > 1" class="text-button" :disabled="locked" @tap="removeRound(index)">移除此轮</button></view>
        <view class="choices"><button v-for="item in modes" :key="item.value" :class="{ active: round.mode === item.value }" :disabled="locked" @tap="changeMode(index, item.value)">{{ item.label }}</button></view>
        <view v-for="(count, i) in groupSpec(round.mode)" :key="i" class="score-row"><view><text>第 {{ i + 1 }} 组</text><text class="hint">{{ count }} 发 · 0–{{ count * 10 }} 环</text></view><input class="score-input" v-model="round.groupTotals[i]" type="digit" :maxlength="5" :disabled="locked" placeholder="总环数" :aria-label="`第 ${index + 1} 轮第 ${i + 1} 组总环数`"/><text>环</text></view>
        <view class="round-total"><text>本轮合计</text><text>{{ sumGroups(round.groupTotals).toFixed(1) }} 环</text></view>
      </view>
      <button class="secondary-button" :disabled="locked || rounds.length >= 100" @tap="addRound">＋ 添加一轮</button>
      <text class="hint">同次训练可交替登记决赛和资格赛；保存后可在训练详情中更正成绩。</text>
      <view v-if="error" class="error-message">{{ error }}</view>
      <text v-if="pending && !saving" class="hint">上次提交结果待确认。重试会核对同一条补录，不会重复新增。</text>
      <button class="primary-button submit" :disabled="saving || searching" :loading="saving" @tap="confirmSave">{{ saved ? '查看已保存的训练' : pending ? '重试并确认保存' : `保存 ${rounds.length} 轮历史训练` }}</button>
    </view>
  </view>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import NavBar from '@/components/NavBar.vue'
import { trainingService } from '@/services/training.js'
import { groupSpec, sumGroups } from '@/domain/training.js'
import { venueToday } from '@/domain/booking.js'
import { newHistoryRound, historicalTrainingError, historicalTrainingPayload } from '@/domain/historical-training.js'
const today = ref(venueToday()), startDate = ref(today.value), endDate = ref(today.value), startTime = ref('08:30'), endTime = ref('10:10')
const member = ref(null), search = ref(''), members = ref([]), hasMore = ref(false), searching = ref(false), searched = ref(false)
const weapon = ref('pistol'), rounds = ref([]), error = ref(''), saving = ref(false), pending = ref(null), saved = ref(null)
const locked = computed(() => saving.value || !!pending.value || !!saved.value)
const weapons = [{ value: 'pistol', label: '气手枪' }, { value: 'rifle', label: '气步枪' }]
const modes = [{ value: 'final_', label: '决赛 · 24 发' }, { value: 'qualifying', label: '资格赛 · 60 发' }]
let roundKey = 0, generation = 0, page = 0, viewerToken = uni.getStorageSync('token')
function addRound() { rounds.value.push({ ...newHistoryRound(rounds.value.at(-1)?.mode), key: ++roundKey }) }
addRound()
function removeRound(index) {
  if (locked.value) return
  uni.showModal({ title: '移除这一轮？', content: '本轮未保存的成绩将被移除。', success: r => { if (r.confirm && !locked.value) rounds.value.splice(index, 1) } })
}
function changeMode(index, mode) {
  const round = rounds.value[index]
  if (locked.value || round.mode === mode) return
  const apply = () => { if (!locked.value) rounds.value[index] = { ...newHistoryRound(mode), key: round.key } }
  if (round.groupTotals.some(v => v !== '')) uni.showModal({ title: '切换本轮赛制？', content: '将清空本轮已填写的分组成绩。', success: r => { if (r.confirm) apply() } })
  else apply()
}
watch(search, () => { generation++; members.value = []; searched.value = false; hasMore.value = false; searching.value = false }, { flush: 'sync' })
async function findMembers(append) {
  if (locked.value || searching.value || !search.value.trim()) return
  const id = ++generation, token = uni.getStorageSync('token'), next = append ? page + 1 : 0
  searching.value = true; error.value = ''
  try {
    const data = await trainingService.coachMembers(search.value.trim(), next)
    if (id !== generation || token !== uni.getStorageSync('token')) return
    members.value = append ? [...members.value, ...data.members] : data.members; hasMore.value = data.hasMore; page = next; searched.value = true
  } catch (err) { if (id === generation) { error.value = err.message; members.value = []; hasMore.value = false } }
  finally { if (id === generation) searching.value = false }
}
function form() { return { userId: member.value?.id, weapon: weapon.value, startedAt: `${startDate.value}T${startTime.value}:00`, endedAt: `${endDate.value}T${endTime.value}:00`, rounds: rounds.value } }
function confirmSave() {
  if (saving.value) return
  if (saved.value) { openSaved(); return }
  if (pending.value) { save(); return }
  const values = form()
  error.value = historicalTrainingError(values)
  if (error.value) return
  const token = uni.getStorageSync('token')
  const payload = historicalTrainingPayload(values, `history-${Date.now()}-${Math.random().toString(36).slice(2)}`)
  uni.showModal({ title: '确认补录历史训练？', content: `${member.value.name} · ${weapons.find(w => w.value === weapon.value).label}\n${startDate.value} ${startTime.value} 至 ${endDate.value} ${endTime.value}\n共 ${rounds.value.length} 轮，保存后同步当日最高成绩。`, confirmText: '确认保存', success: r => {
    if (!r.confirm || saving.value || token !== uni.getStorageSync('token')) return
    pending.value = payload; save()
  } })
}
async function save() {
  const token = uni.getStorageSync('token'), payload = pending.value
  saving.value = true; error.value = ''
  try {
    const detail = await trainingService.createHistory(payload)
    if (token !== uni.getStorageSync('token')) return
    saved.value = { date: payload.startedAt.slice(0, 10), id: detail.results.session.id }; pending.value = null
    uni.showToast({ title: '历史训练已保存', icon: 'success' })
    openSaved()
  } catch (err) {
    if (token !== uni.getStorageSync('token')) { clearPrivate(); error.value = '登录身份已变化，请重新登录'; return }
    error.value = err.message
    if (err.code >= 400 && err.code < 500) pending.value = null
  } finally { saving.value = false }
}
function openSaved() {
  uni.redirectTo({ url: `/pages/training/coach?date=${saved.value.date}&sessionId=${saved.value.id}`, fail: () => { error.value = '训练已保存，请点击下方按钮重新打开详情' } })
}
function clearPrivate() { member.value = null; members.value = []; search.value = ''; rounds.value = []; addRound(); pending.value = null; saved.value = null }
function back() {
  if (saving.value) return
  if (saved.value) { openSaved(); return }
  uni.showModal({ title: pending.value ? '离开前请确认保存结果' : '离开补录页面？', content: pending.value ? '提交可能已成功，建议先重试确认；离开后请先查看历史记录，避免重复补录。' : '尚未保存的填写内容将丢失。', success: r => { if (r.confirm && !saving.value) getCurrentPages().length > 1 ? uni.navigateBack() : uni.redirectTo({ url: '/pages/training/coach' }) } })
}
onShow(() => {
  today.value = venueToday()
  const token = uni.getStorageSync('token')
  if (token !== viewerToken) { clearPrivate(); viewerToken = token; error.value = '登录身份已变化，请重新选择学员' }
})
onHide(() => { generation++; searching.value = false })
</script>
<style scoped>
.history-content { padding-top: 28rpx; padding-bottom: 70rpx; }
.hint { display: block; font-size: 23rpx; line-height: 1.65; color: var(--color-text-soft); overflow-wrap: anywhere; margin-top: 8rpx; }
.intro { margin-bottom: 28rpx; }
.card { padding: 26rpx; margin: 22rpx 0; border: 1rpx solid var(--color-border); border-radius: 16rpx; background: #fff; }
.section-title { font-size: 29rpx; font-weight: 600; }
.search, .selected-member, .round-heading, .score-row, .round-total { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.search { margin-top: 18rpx; }
.search input { flex: 1; min-width: 0; font-size: 27rpx; }
.selected-member { margin-top: 20rpx; font-size: 29rpx; }
.member-option { text-align: left; padding: 20rpx 0; font-size: 27rpx; background: #fff; border-bottom: 1rpx solid var(--color-border); border-radius: 0; line-height: 1.6; }
button::after { border: 0; }
.choices { display: flex; gap: 16rpx; margin: 20rpx 0; flex-wrap: wrap; }
.choices button { margin: 0; padding: 12rpx 20rpx; font-size: 24rpx; line-height: 1.5; border: 1rpx solid transparent; background: var(--color-bg); color: var(--color-text-soft); border-radius: 9rpx; }
.choices button.active { color: var(--color-primary); border-color: #d8d2ee; background: #f0eefb; }
.field-label { display: block; margin: 24rpx 0 12rpx; font-size: 24rpx; color: var(--color-text-soft); }
.time-row { display: flex; gap: 14rpx; }
.time-row picker:first-child { flex: 1.4; }
.time-row picker:last-child { flex: 1; }
.picker-value { padding: 18rpx 12rpx; background: var(--color-bg); border-radius: 9rpx; text-align: center; font-size: 26rpx; }
.section-heading { margin: 32rpx 0 0; }
.score-row { padding: 18rpx 0; font-size: 26rpx; border-bottom: 1rpx solid var(--color-border); }
.score-row > view { flex: 1; }
.score-input { width: 160rpx; height: 76rpx; padding: 14rpx; background: var(--color-bg); text-align: right; border-radius: 8rpx; font-size: 30rpx; }
.round-total { padding-top: 22rpx; font-size: 28rpx; font-weight: 600; }
.submit { margin-top: 26rpx; }
.error-message { margin-top: 20rpx; }
</style>
