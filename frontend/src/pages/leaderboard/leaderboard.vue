<template>
  <view class="page">
    <NavBar title="训练排行榜" subtitle="A LITTLE BETTER, EVERY DAY"><template #action><button class="text-button" aria-label="查看排名规则" @tap="showRules">规则 ↗</button></template></NavBar>
    <view class="page-content">
      <text class="eyebrow">THE PURSUIT OF PRECISION</text><view class="page-heading">以专注，见进步。</view><text class="muted">与伙伴切磋，也与昨天的自己较量。</text>
      <view class="weapon-tabs"><button v-for="weapon in weapons" :key="weapon.value" :class="{ active: activeWeapon === weapon.value }" @tap="activeWeapon = weapon.value"><text>{{ weapon.label }}</text><text>{{ weapon.en }}</text></button></view>
      <view class="segmented"><button v-for="event in events" :key="event.value" :class="{ active: activeEvent === event.value }" @tap="activeEvent = event.value">{{ event.label }}</button></view>
      <view class="metric-tabs"><button v-for="metric in metrics" :key="metric.value" :class="{ active: activeMetric === metric.value }" @tap="activeMetric = metric.value">{{ metric.label }}</button></view>
      <view class="ranking-heading"><text class="section-title">{{ eventLabel }} · {{ metricLabel }}</text><picker :range="genders" range-key="label" :value="genderIndex" @change="genderIndex = Number($event.detail.value)"><view class="gender-select">{{ genders[genderIndex].label }}⌄</view></picker></view>
      <view class="table-head"><text>排名 / 学员</text><text>{{ activeMetric === 'IMPROVE' ? '提升环数' : '成绩 / 环' }}</text></view>
      <view v-if="loading" class="rank-state"><view class="target-mark"/><text class="state-title">正在更新排行榜</text><text class="muted">同步训练成绩，请稍候</text></view>
      <view v-else-if="error" class="rank-state"><view class="target-mark"/><text class="state-title">{{ needsLogin ? '登录后查看训练榜单' : '榜单暂时无法加载' }}</text><text class="muted">{{ error }}</text><button class="secondary-button" @tap="needsLogin ? login() : fetchRows()">{{ needsLogin ? '前往登录' : '重新加载' }}</button></view>
      <view v-else-if="!rows.length" class="rank-state"><view class="target-mark"/><text class="state-title">这一页，等你来书写</text><text class="muted">当前筛选下暂无已发布的训练成绩。</text><button class="text-button" @tap="goBooking">预约训练，积累进步 ↗</button></view>
      <view v-else><view v-for="(row, index) in rows" :key="row.userIdHash" class="rank-row"><text class="rank-number" :class="{ podium: index < 3 }">{{ String(row.rank || index + 1).padStart(2, '0') }}</text><view class="rank-person"><text class="rank-name">{{ row.displayName || '训练学员' }}</text><text class="rank-detail">{{ row.recordedAt ? row.recordedAt.slice(0, 10).replace(/-/g, '.') : '已发布成绩' }}{{ row.sampleCount ? ` · ${row.sampleCount} 次训练` : '' }}</text></view><view class="rank-score"><text>{{ formatScore(row.score) }}</text><text>环</text></view></view></view>
      <view class="ranking-footer"><text>{{ metricHint }}</text><text v-if="asOf && !error">更新于 {{ asOf.slice(0, 16).replace('T', ' ') }}</text></view>
      <view class="rank-note"><text>进步，从来不止一个名次。</text><text>保持热爱，也保持自己的节奏。</text></view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import NavBar from '@/components/NavBar.vue'
import { request } from '@/services/request.js'
const weapons = [{ value: 'PISTOL', label: '气手枪', en: 'AIR PISTOL' }, { value: 'RIFLE', label: '气步枪', en: 'AIR RIFLE' }]
const events = [{ value: 'QUALIFICATION', label: '资格赛' }, { value: 'FINAL', label: '决赛' }]
const metrics = [{ value: 'BEST', label: '最佳成绩', hint: '取单场最高总成绩' }, { value: 'AVG5', label: '近五次平均', hint: '最近 5 次有效训练均值，不足 5 次按实际次数计算' }, { value: 'IMPROVE', label: '进步之星', hint: '近 30 天与前 30 天最佳成绩的差值' }]
const genders = [{ value: 'ALL', label: '全部' }, { value: 'M', label: '男生' }, { value: 'F', label: '女生' }]
const activeWeapon = ref('PISTOL'), activeEvent = ref('QUALIFICATION'), activeMetric = ref('BEST'), genderIndex = ref(0)
const rows = ref([]), asOf = ref(''), loading = ref(false), error = ref(''), needsLogin = ref(false)
const eventLabel = computed(() => events.find(e => e.value === activeEvent.value).label)
const metricLabel = computed(() => metrics.find(m => m.value === activeMetric.value).label)
const metricHint = computed(() => metrics.find(m => m.value === activeMetric.value).hint)
let generation = 0, active = false
async function fetchRows() {
  const id = ++generation
  rows.value = []; asOf.value = ''; error.value = ''; needsLogin.value = false
  if (!uni.getStorageSync('token')) { needsLogin.value = true; error.value = '登录后可按项目与赛事查看已发布成绩。'; loading.value = false; return }
  loading.value = true
  try {
    const query = `weapon=${activeWeapon.value}&event=${activeEvent.value}&metric=${activeMetric.value}&gender=${genders[genderIndex.value].value}&limit=20`
    const result = await request(`/api/leaderboard?${query}`)
    if (id !== generation) return
    if (!Array.isArray(result?.rows)) throw new Error('成绩数据暂不可用，请稍后重试')
    rows.value = result.rows.map(row => ({ userIdHash: row.userIdHash, displayName: row.displayName, rank: row.rank, score: row.score, recordedAt: row.recordedAt, sampleCount: row.sampleCount }))
    asOf.value = result.asOf || ''
  } catch (err) { if (id === generation) { error.value = err.code === 404 ? '榜单服务尚未开放，成绩发布后可在这里查看。' : err.message; needsLogin.value = err.code === 401 } }
  finally { if (id === generation) loading.value = false }
}
function formatScore(value) { if (value == null || !Number.isFinite(Number(value))) return '—'; return `${activeMetric.value === 'IMPROVE' && Number(value) > 0 ? '+' : ''}${Number(value).toFixed(1)}` }
function showRules() { uni.showModal({ title: '排名规则', content: '气手枪、气步枪及资格赛、决赛分别排名。最佳成绩取单场最高总成绩；近五次平均取最近 5 次有效成绩均值；进步之星比较相邻两个 30 天窗口的最佳成绩。榜单使用登记的真实姓名展示，不展示学号、手机号。', showCancel: false, confirmText: '我知道了' }) }
function login() { uni.navigateTo({ url: '/pages/login/login' }) }
function goBooking() { uni.setStorageSync('booking-view', 'browse'); uni.switchTab({ url: '/pages/booking/booking' }) }
watch([activeWeapon, activeEvent, activeMetric, genderIndex], () => { if (active) fetchRows() })
onShow(() => { active = true; fetchRows() })
onHide(() => { active = false; generation++; loading.value = false })
</script>
<style scoped>
.weapon-tabs { display: flex; border-top: 1rpx solid var(--color-border); border-bottom: 1rpx solid var(--color-border); margin: 36rpx 0 28rpx; }
.weapon-tabs button { width: 50%; border-radius: 0; background: transparent; margin: 0; padding: 24rpx 0; display: flex; flex-direction: column; align-items: center; color: var(--color-text-soft); line-height: 1.8; border-bottom: 3rpx solid transparent; }
.weapon-tabs button text:first-child { font-size: 32rpx; }
.weapon-tabs button text:last-child { font-size: 16rpx; letter-spacing: 3rpx; }
.weapon-tabs .active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.metric-tabs { display: flex; gap: 32rpx; margin-top: 24rpx; }
.metric-tabs button { margin: 0; padding: 12rpx 0; line-height: 1.8; background: transparent; font-size: 23rpx; color: var(--color-text-soft); border-radius: 0; border-bottom: 2rpx solid transparent; }
.metric-tabs .active { color: var(--color-text); border-bottom-color: var(--color-text); }
.ranking-heading { display: flex; align-items: center; justify-content: space-between; margin: 38rpx 0 24rpx; }
.ranking-heading .section-title { font-size: 28rpx; }
.gender-select { border: 1rpx solid var(--color-border); padding: 10rpx 18rpx; border-radius: 8rpx; font-size: 22rpx; color: var(--color-text-soft); }
.table-head { display: flex; justify-content: space-between; padding: 18rpx 0; border-top: 1rpx solid var(--color-border); border-bottom: 1rpx solid var(--color-border); font-size: 20rpx; color: var(--color-text-soft); }
.rank-state { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 16rpx; padding: 64rpx 22rpx; border-bottom: 1rpx solid var(--color-border); }
.rank-state .muted { font-size: 22rpx; }
.rank-state button { padding: 0 32rpx; margin-top: 10rpx; }
.rank-row { display: flex; align-items: center; gap: 22rpx; padding: 28rpx 0; border-bottom: 1rpx solid var(--color-border); }
.rank-number { width: 54rpx; font-size: 30rpx; font-weight: 500; color: var(--color-text-soft); font-variant-numeric: tabular-nums; }
.podium { color: var(--color-primary); }
.rank-person { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.rank-name { font-size: 27rpx; white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
.rank-detail { font-size: 20rpx; color: var(--color-text-soft); }
.rank-score { display: flex; align-items: baseline; gap: 8rpx; font-variant-numeric: tabular-nums; }
.rank-score text:first-child { font-size: 35rpx; font-weight: 500; }
.rank-score text:last-child { font-size: 18rpx; color: var(--color-text-soft); }
.ranking-footer { display: flex; flex-direction: column; gap: 6rpx; color: var(--color-text-soft); font-size: 20rpx; padding: 20rpx 0; }
.rank-note { display: flex; flex-direction: column; align-items: center; gap: 10rpx; margin: 38rpx 0 16rpx; font-size: 24rpx; color: var(--color-text-soft); }
.rank-note text:last-child { font-size: 21rpx; color: var(--color-text-mute); }
</style>
