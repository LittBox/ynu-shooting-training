<template>
  <view class="page">
    <NavBar title="个人中心" subtitle="YOUR TRAINING JOURNEY" />
    <view class="page-content">
      <view class="profile-header"><view class="avatar">{{ initial }}</view><view class="profile-copy"><text class="eyebrow">KEEP YOUR FOCUS</text><view class="profile-name">{{ displayName }}</view><text class="muted">{{ logged ? '每一次练习，都是进步的开始。' : '登录，记录你的每一次专注。' }}</text></view></view>
      <button v-if="!logged" class="primary-button" @tap="login">登录 / 完成身份登记 ↗</button>
      <view v-else class="identity"><text>{{ user?.profileStatus === 'completed' ? '✓ 已完成身份登记' : user ? '○ 请完善资料（含性别）' : '○ 正在确认身份信息' }}</text><button v-if="user" class="text-button" @tap="editProfile">{{ user.profileStatus === 'completed' ? '资料与身份 ↗' : '去完善 ↗' }}</button></view>
      <view v-if="user" class="role-card"><view class="role-line"><text class="role-label">{{ roleLabel }}</text><button class="text-button" :disabled="loading" @tap="load">刷新身份 ↻</button></view><text class="account-id">账号 ID：{{ user.id }}</text><text class="role-hint">{{ isCoach ? '可安排本人值班、确认到岗；同时保留预约与训练功能。' : '担任教练员？在“资料与身份”中选择教练员，填写负责人发放的邀请码即可开通。' }}</text></view>
      <view class="stats"><view><text>{{ metric('completed') }}</text><text>已完成训练</text></view><view><text>{{ metric('upcoming') }}</text><text>待进行预约</text></view><view><text>{{ metric('all') }}</text><text>累计预约</text></view></view>
      <view v-if="error" class="inline-error"><text>{{ error }}</text><button class="text-button" @tap="load">重新加载</button></view>
      <view class="section-heading"><text class="section-title">下一次训练</text><button v-if="logged" class="text-button" @tap="goBookings">全部预约 ↗</button></view>
      <view v-if="loading" class="state-card"><text class="muted">正在同步训练安排…</text></view>
      <view v-else-if="upcoming" class="next-session"><view class="flex-between"><text class="eyebrow">NEXT SESSION</text><text class="status">{{ statusLabels[upcoming.status] }}</text></view><view class="session-date">{{ shortDate(upcoming.slotDate) }} · {{ weekday(upcoming.slotDate) }}</view><view class="session-time">{{ upcoming.slotStart }} — {{ upcoming.slotEnd }}</view><view class="flex-between"><text class="muted">{{ upcoming.deviceName }}</text><button class="text-button" @tap="goBookings">查看详情 ↗</button></view></view>
      <view v-else class="empty-session"><view class="target-mark"/><text class="state-title">{{ error && logged ? '暂时无法查看训练安排' : '留一点时间，给热爱的事' }}</text><text class="muted">{{ error && logged ? '请重新加载，获取最新预约记录。' : logged ? '还没有待进行的训练，选个时间出发吧。' : '登录后，在这里查看你的训练安排。' }}</text><button class="text-button" @tap="logged ? goBooking() : login()">{{ logged ? '预约下一次训练 ↗' : '登录并查看 ↗' }}</button></view>
      <view class="section-heading"><text class="section-title">训练服务</text><text class="eyebrow">FOR YOU</text></view>
      <view class="menu"><button v-if="isCoach" @tap="goDuty"><text class="menu-number">班</text><text>我的值班安排</text><text class="arrow">↗</text></button><button v-if="isCoach" @tap="goCoachResults"><text class="menu-number">教</text><text>学员成绩管理</text><text class="arrow">↗</text></button><button @tap="goResults"><text class="menu-number">☆</text><text>我的成绩与历史最佳</text><text class="arrow">↗</text></button><button @tap="goBookings"><text class="menu-number">01</text><text>我的预约与签到</text><text class="arrow">↗</text></button><button @tap="goRankings"><text class="menu-number">02</text><text>查看训练排行榜</text><text class="arrow">↗</text></button><button @tap="showRules"><text class="menu-number">03</text><text>预约与场馆须知</text><text class="arrow">＋</text></button><button @tap="showPrivacy"><text class="menu-number">04</text><text>个人信息说明</text><text class="arrow">＋</text></button></view>
      <button v-if="logged" class="logout" @tap="logout">退出登录</button>
      <view class="signature"><text>专注当下，稳步向前。</text><text>YUNNAN UNIVERSITY · SHOOTING</text></view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow, onHide } from '@dcloudio/uni-app'
import NavBar from '@/components/NavBar.vue'
import { request } from '@/services/request.js'
import { bookingService } from '@/services/booking.js'
import { ACTIVE_STATUSES, STATUS_LABELS, shortDate, weekday } from '@/domain/booking.js'
const user = ref(null), logged = ref(false), bookings = ref([]), error = ref(''), loading = ref(false), recordsLoaded = ref(false)
const statusLabels = STATUS_LABELS
const pageActive = ref(false), reminderDutyDate = ref('')
onLoad(options => { if (/^\d{4}-\d{2}-\d{2}$/.test(options?.dutyDate || '')) reminderDutyDate.value = options.dutyDate })
const isCoach = computed(() => logged.value && ['admin', 'superadmin'].includes(user.value?.role))
const roleLabel = computed(() => ({ student: '学员', admin: '教练员', superadmin: '管理员 / 教练员' }[user.value?.role] || '身份待确认'))
const displayName = computed(() => !logged.value ? '你好，射击爱好者' : !user.value ? '正在同步资料' : user.value.realName || (user.value.profileStatus === 'completed' ? '姓名暂未同步' : '请完善姓名'))
let generation = 0
const initial = computed(() => user.value?.realName?.slice(0, 1) || '云')
const upcoming = computed(() => [...bookings.value].filter(b => ACTIVE_STATUSES.includes(b.status)).sort((a, b) => `${a.slotDate}${a.slotStart}`.localeCompare(`${b.slotDate}${b.slotStart}`))[0])
function metric(kind) { if (!logged.value || !recordsLoaded.value) return '—'; return kind === 'all' ? bookings.value.length : bookings.value.filter(b => kind === 'completed' ? b.status === 'COMPLETED' : ACTIVE_STATUSES.includes(b.status)).length }
async function load() {
  const id = ++generation
  logged.value = !!uni.getStorageSync('token'); error.value = ''; recordsLoaded.value = false; user.value = null; bookings.value = []
  if (!logged.value) { loading.value = false; return }
  loading.value = true
  const [account, records] = await Promise.allSettled([request('/api/auth/me'), bookingService.mine()])
  if (id !== generation) return
  if (account.status === 'fulfilled') user.value = account.value
  else error.value = account.reason.message
  if (records.status === 'fulfilled') { bookings.value = records.value; recordsLoaded.value = true }
  else error.value = records.reason.message
  logged.value = !!uni.getStorageSync('token'); if (!logged.value) { user.value = null; bookings.value = []; recordsLoaded.value = false }
  loading.value = false
  if (isCoach.value && reminderDutyDate.value) { const date = reminderDutyDate.value; reminderDutyDate.value = ''; goDuty(date) }
}
function login() { uni.navigateTo({ url: '/pages/login/login' }) }
function editProfile() { uni.navigateTo({ url: '/pages/login/login?edit=1' }) }
function goBookings() { if (!logged.value) return login(); uni.setStorageSync('booking-view', 'mine'); uni.switchTab({ url: '/pages/booking/booking' }) }
function goBooking() { uni.setStorageSync('booking-view', 'browse'); uni.switchTab({ url: '/pages/booking/booking' }) }
function goDuty(date = '') { uni.setStorageSync('booking-view', 'duty'); if (typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date)) uni.setStorageSync('booking-duty-date', date); uni.switchTab({ url: '/pages/booking/booking' }) }
function goCoachResults() { uni.navigateTo({ url: '/pages/training/coach' }) }
function goResults() { if (!logged.value) return login(); uni.navigateTo({ url: '/pages/training/results' }) }
function goRankings() { uni.switchTab({ url: '/pages/leaderboard/leaderboard' }) }
function showRules() { uni.showModal({ title: '预约与场馆须知', content: '最多提前 7 天预约，每日最多 3 场，每周最多 10 场。开始前 30 分钟至开始后 10 分钟可签到。提前 12 小时可免费取消，不足 12 小时记录晚取消，不足 1 小时记录临近取消。场地有教员排班才开放预约，教员确认到岗后才可开始训练。到场后请听从教员指导。', showCancel: false, confirmText: '我知道了' }) }
function showPrivacy() { uni.showModal({ title: '个人信息说明', content: '排行榜按登记的真实姓名展示成绩，性别用于分组筛选。学号、手机号用于身份核验和预约联系，不在排行榜展示。教练身份由负责人授权，与学号无关。退出登录会清除本机登录凭证。', showCancel: false, confirmText: '我知道了' }) }
function logout() { uni.showModal({ title: '退出当前账号？', content: '已提交的预约会保留，重新登录后可继续查看。', confirmText: '退出登录', success: res => { if (res.confirm) { uni.removeStorageSync('token'); load() } } }) }
onShow(() => { pageActive.value = true; load() })
onHide(() => { pageActive.value = false; generation++; loading.value = false })
</script>
<style scoped>
.profile-header { display: flex; gap: 24rpx; align-items: center; padding: 24rpx 0 32rpx; }
.avatar { width: 112rpx; height: 112rpx; border: 1rpx solid #cec9e9; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--color-primary); font-size: 42rpx; flex-shrink: 0; }
.profile-name { font-size: 36rpx; font-weight: 600; margin: 8rpx 0; }
.profile-copy .muted { font-size: 22rpx; }
.identity { display: flex; justify-content: space-between; align-items: center; color: var(--color-text-soft); font-size: 23rpx; padding: 12rpx 0; }
.role-card { margin-top: 20rpx; padding: 24rpx; border: 1rpx solid var(--color-border); border-radius: 12rpx; }
.role-line { display: flex; align-items: center; justify-content: space-between; }
.role-label { font-size: 28rpx; font-weight: 600; color: var(--color-primary); }
.account-id, .role-hint { display: block; margin-top: 12rpx; font-size: 22rpx; line-height: 1.7; color: var(--color-text-soft); }
.stats { display: flex; padding: 32rpx 0; margin-top: 24rpx; border-top: 1rpx solid var(--color-border); border-bottom: 1rpx solid var(--color-border); }
.stats > view { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8rpx; border-right: 1rpx solid var(--color-border); }
.stats > view:last-child { border: 0; }
.stats text:first-child { font-size: 44rpx; font-variant-numeric: tabular-nums; }
.stats text:last-child { font-size: 21rpx; color: var(--color-text-soft); }
.next-session { padding: 28rpx; border: 1rpx solid #d8d4e9; border-radius: 12rpx; background: #fff; }
.status { color: var(--color-primary); font-size: 22rpx; }
.session-date { font-size: 25rpx; margin-top: 24rpx; }
.session-time { font-size: 43rpx; margin: 8rpx 0 16rpx; }
.empty-session { border: 1rpx solid var(--color-border); border-radius: 12rpx; padding: 44rpx 20rpx; display: flex; align-items: center; flex-direction: column; gap: 14rpx; text-align: center; }
.empty-session .muted { font-size: 22rpx; }
.menu button { background: transparent; border-radius: 0; border-bottom: 1rpx solid var(--color-border); display: flex; align-items: center; gap: 24rpx; font-size: 26rpx; text-align: left; padding: 28rpx 0; margin: 0; line-height: 1.6; color: var(--color-text); }
.menu-number { font-size: 20rpx; color: var(--color-text-soft); }
.arrow { margin-left: auto; color: var(--color-text-soft); font-size: 28rpx; }
.logout { margin: 36rpx 0 0; background: transparent; border: 1rpx solid var(--color-border); border-radius: 12rpx; font-size: 24rpx; color: var(--color-text-soft); line-height: 82rpx; }
.signature { text-align: center; display: flex; flex-direction: column; gap: 12rpx; margin-top: 56rpx; font-size: 23rpx; color: var(--color-text-soft); }
.signature text:last-child { font-size: 16rpx; letter-spacing: 3rpx; color: var(--color-text-mute); }
.inline-error { display: flex; justify-content: space-between; gap: 12rpx; align-items: center; margin-top: 20rpx; font-size: 22rpx; color: var(--color-danger); }
.inline-error button { flex-shrink: 0; }
</style>
