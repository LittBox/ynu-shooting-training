<template>
  <view class="booking-page">
    <NavBar title="训练预约" subtitle="云大射击训练中心" />
    <view class="booking-content">
      <view class="hero">
        <view class="hero__copy"><text class="eyebrow">MAKE TIME. TAKE AIM.</text><view class="hero__title">把时间，留给专注。</view><text class="hero__subtitle">提前预约，或选择当前空闲设备立即训练。</text></view>
        <view class="target-art" aria-hidden="true"><view class="target-art__ring"><view class="target-art__inner"><view class="target-art__center" /></view></view><view class="target-art__cross target-art__cross--x"/><view class="target-art__cross target-art__cross--y"/></view>
      </view>
      <view class="view-tabs"><button :class="{ 'is-active': tab === 'schedule' }" @tap="tab = 'schedule'">预约日程<text class="tab-dot" v-if="tab === 'schedule'" /></button><button :class="{ 'is-active': tab === 'mine' }" @tap="tab = 'mine'">我的预约<text v-if="activeCount" class="tab-count">{{ activeCount }}</text></button><button :class="{ 'is-active': tab === 'history' }" @tap="tab = 'history'">过往训练</button></view>
      <button class="text-button booking-rules" @tap="showRules">预约须知 ↗</button>
      <template v-if="tab === 'schedule'">
        <view class="date-panel">
          <view class="date-panel__header"><view class="month-title"><text class="month-title__number">{{ monthLabel }}</text><text class="month-title__year">{{ selectedDate.slice(0, 4) }}</text></view><button class="today-button" @tap="chooseDate(today)">回到今天 ↗</button></view>
          <BookingDateStrip :dates="dates" :selected="selectedDate" :today="today" @select="chooseDate" />
        </view>
        <view class="filters"><view class="device-filters"><button v-for="type in deviceTypes" :key="type.id" :class="{ 'is-active': deviceType === type.id }" @tap="deviceType = type.id">{{ type.label }}</button></view><view class="range-controls"><button aria-label="前面三个日期" :disabled="offset === 0 || loading" @tap="movePage(-1)">‹</button><button aria-label="后面三个日期" :disabled="offset >= dates.length - 3 || loading" @tap="movePage(1)">›</button></view></view>
        <view class="schedule-meta"><view class="legend"><view class="legend__item"><view class="legend__mark"/>可预约</view><view class="legend__item"><view class="legend__mark legend__mark--mine"/>我的预约</view><view class="legend__item"><view class="legend__mark legend__mark--full"/>已约满</view></view><button class="refresh-link" :disabled="loading" @tap="refresh()">{{ loading ? '更新中…' : '刷新 ↻' }}</button></view>
        <view v-if="loading" class="state-card"><view class="loading-ring"/><text class="state-card__title">正在查看可约时段</text><text class="state-card__hint">为你同步训练中心的设备安排</text></view>
        <view v-else-if="error" class="state-card"><text class="state-card__symbol">↻</text><text class="state-card__title">暂时没能加载日程</text><text class="state-card__hint">{{ error }}</text><button class="secondary-button" @tap="refresh()">重新加载</button></view>
        <BookingCalendar v-else :dates="visibleDates" :rows="rows" :now="now" :selected-date="selectedDate" @inspect="inspectCell" @select-date="chooseDate" />
        <view class="booking-note"><view class="booking-note__icon">i</view><view><text class="booking-note__title">有计划的训练，让进步发生。</text><text class="booking-note__text">最多提前 7 天预约 · 每天 3 场 · 每自然周 10 场</text><text class="booking-note__text">{{ updatedAt && !error ? `${updatedAt} 更新 · 页面每 30 秒自动刷新` : '设备余量以提交时的最新结果为准' }}</text></view></view>
      </template>
      <template v-else>
        <view v-if="!loggedIn" class="state-card"><text class="state-card__symbol">⌑</text><text class="state-card__title">你的训练计划，在这里</text><text class="state-card__hint">登录后查看预约、签到和过往训练记录。</text><button class="primary-button" @tap="goLogin">登录并查看</button></view>
        <view v-else-if="loading" class="state-card"><view class="loading-ring"/><text class="state-card__title">正在同步训练记录</text></view>
        <view v-else-if="mineError" class="state-card"><text class="state-card__title">训练记录加载失败</text><text class="state-card__hint">{{ mineError }}</text><button class="secondary-button" @tap="refresh()">重新加载</button></view>
        <MyBookingList v-else :bookings="displayBookings" :active="pageActive" :history="tab === 'history'" :now="now" :busy="actionBusy" @browse="tab = 'schedule'" @cancel="cancelBooking" @checkin="checkIn" @start="startTraining" @score="openResults" />
      </template>
      <view class="page-signature"><view/><text>YNU SHOOTING · 专注每一发</text><view/></view>
    </view>
    <SlotDetailSheet :now="now" :selection="inspected" :detail="slotDetail" :loading="detailLoading || loading" :error="detailError" @close="closeDetails" @refresh="refreshDetails" @book="bookDevice" @walkin="walkInDevice" @manage="manageFromDetails" @login="goLogin" />
    <BookingConfirmSheet :now="now" :selection="selection" :devices="selectedDevices" :busy="submitting" :logged-in="loggedIn" @close="selection = null" @confirm="submit" />
    <view v-if="successBooking" class="success-mask" @tap="successBooking = null"><view class="success-card" @tap.stop><view class="success-card__check">✓</view><text class="eyebrow">SEE YOU AT THE RANGE</text><view class="success-card__title">{{ successBooking.status === 'IN_USE' ? '训练已开始' : '下一场训练，已为你留好' }}</view><text class="success-card__detail">{{ shortDate(successBooking.slotDate) }} · {{ successBooking.slotStart }}–{{ successBooking.slotEnd }}</text><text class="success-card__device">{{ successBooking.deviceName }}</text><view class="success-card__hint">{{ successBooking.status === 'IN_USE' ? '请听从值班教练安排，在当前时段结束前完成训练，并在我的预约中点击结束训练。' : '请在开始前 30 分钟至开始后 10 分钟内到场并完成签到。' }}</view><ReminderButton v-if="successBooking.status === 'BOOKED'" kind="TRAINING" :target-id="successBooking.id" :active="pageActive" /><button class="primary-button" @tap="viewMyBooking">查看我的预约 ↗</button><button class="text-button" @tap="successBooking = null">继续浏览日程</button></view></view>
  </view>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import NavBar from '@/components/NavBar.vue'
import ReminderButton from '@/components/ReminderButton.vue'
import BookingDateStrip from '@/components/booking/BookingDateStrip.vue'
import BookingCalendar from '@/components/booking/BookingCalendar.vue'
import BookingConfirmSheet from '@/components/booking/BookingConfirmSheet.vue'
import MyBookingList from '@/components/booking/MyBookingList.vue'
import SlotDetailSheet from '@/components/booking/SlotDetailSheet.vue'
import { useSlotDetails } from '@/composables/useSlotDetails.js'
import { useBooking } from '@/composables/useBooking.js'
import { DEVICE_TYPES, ACTIVE_STATUSES, shortDate, cancellationHint, bookingRecordsForView } from '@/domain/booking.js'
import { bookingService } from '@/services/booking.js'
const { now, today, dates, selectedDate, offset, deviceType, visibleDates, bookings, loading, error, mineError, loggedIn, submitting, selection, successBooking, updatedAt, rows, selectedDevices, refresh, chooseDate, movePage, openCell, submit, start, stop } = useBooking()
const { inspected, detail: slotDetail, loading: detailLoading, error: detailError, open: openDetails, close: closeDetails, refresh: refreshDetails } = useSlotDetails()
const deviceTypes = DEVICE_TYPES
const tab = ref('schedule'), pageActive = ref(false)
onLoad(options => { if (['mine','history'].includes(options?.view)) tab.value = options.view })
const displayBookings = computed(() => bookingRecordsForView(bookings.value, tab.value))
const actionBusy = ref(false)
const monthLabel = computed(() => `${Number(selectedDate.value.slice(5, 7))}月`)
const activeCount = computed(() => bookings.value.filter(item => ACTIVE_STATUSES.includes(item.status)).length)
onShow(() => {
  pageActive.value = true
  const incomingView = uni.getStorageSync('booking-view')
  if (incomingView) {
    tab.value = ['mine', 'history'].includes(incomingView) ? incomingView : 'schedule'
    const incomingType = uni.getStorageSync('booking-device-filter')
    if (['', 'pistol', 'rifle'].includes(incomingType)) deviceType.value = incomingType
  }
  uni.removeStorageSync('booking-view')
  uni.removeStorageSync('booking-device-filter')
  start()
  syncNativeTabBar(!!selection.value || !!successBooking.value || !!inspected.value)
})
onHide(() => { pageActive.value = false; stop(); closeDetails(); syncNativeTabBar(false) })
onUnload(() => { pageActive.value = false; stop(); closeDetails(); syncNativeTabBar(false) })
watch(() => !!selection.value || !!successBooking.value || !!inspected.value, syncNativeTabBar)
function syncNativeTabBar(hidden) {
  // Native WeChat TabBar is above the page's stacking context.
  // #ifdef MP-WEIXIN
  if (hidden) uni.hideTabBar({ animation: false })
  else uni.showTabBar({ animation: false })
  // #endif
}
onPullDownRefresh(async () => { try { await refresh() } finally { uni.stopPullDownRefresh() } })
function inspectCell(cell) { chooseDate(cell.date); openDetails(cell, deviceType.value) }
function manageFromDetails() { closeDetails(); tab.value = 'mine' }
function walkInDevice(device) { return bookDevice(device, true) }
async function bookDevice(device, walkIn = false) {
  if (!inspected.value || detailLoading.value || loading.value) return
  const chosen = inspected.value
  await refresh()
  if (inspected.value !== chosen) return
  if (error.value) { uni.showToast({ title: error.value, icon: 'none' }); return }
  openCell({ ...chosen, state: walkIn ? 'ongoing' : 'available' }, device.deviceId, walkIn)
  if (selection.value) closeDetails()
}
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
function viewMyBooking() { successBooking.value = null; tab.value = 'mine' }
function showRules() {
  uni.showModal({ title: '预约须知', content: '可预约今天至 7 天后的固定训练时段。每天最多 3 场，每自然周最多 10 场，同一时段不可重复预约。当前时段有空闲设备且教练已到岗时，可立即训练，计入相同次数上限。\n\n开始前 30 分钟至开始后 10 分钟可签到，超时未签到记为爽约。\n\n提前 12 小时免费取消；不足 12 小时记录晚取消，不足 1 小时记录临近取消。最终以服务端校验为准。', showCancel: false, confirmText: '我知道了', confirmColor: '#3d4a5d' })
}
async function performAction(action, booking, message) {
  if (actionBusy.value) return
  actionBusy.value = true
  try { await action(booking.id); uni.showToast({ title: message, icon: 'success' }) }
  catch (err) { uni.showToast({ title: err.message, icon: 'none', duration: 3500 }); if (err.code === 401) goLogin() }
  finally { await refresh({ quiet: true }); actionBusy.value = false }
}
function cancelBooking(booking) {
  if (actionBusy.value) return
  uni.showModal({ title: '取消这场训练？', content: `${shortDate(booking.slotDate)} ${booking.slotStart}–${booking.slotEnd}\n${cancellationHint(booking)}\n取消后，设备会释放给其他学员。`, confirmText: '确认取消', cancelText: '保留预约', confirmColor: '#3d4a5d', success: result => { if (result.confirm) performAction(bookingService.cancel, booking, '预约已取消') } })
}
function checkIn(booking) {
  uni.showModal({ title: '确认已到达训练场馆？', content: '到场后再签到，训练安排请听从值班管理员指导。', confirmText: '到场签到', confirmColor: '#3d4a5d', success: result => { if (result.confirm) performAction(bookingService.checkIn, booking, '签到成功') } })
}
function startTraining(booking) {
  if (actionBusy.value) return
  uni.showActionSheet({ itemList: ['决赛 · 24 发（10＋10＋4）', '资格赛 · 60 发（6×10）'], success: result => {
    const mode = result.tapIndex === 0 ? 'final_' : 'qualifying'
    performAction(id => bookingService.startTraining(id, mode), booking, '训练已开始')
  } })
}
function openResults(booking) { if (booking.sessionId) uni.navigateTo({ url: `/pages/training/results?sessionId=${booking.sessionId}` }) }

</script>
<style lang="scss" scoped>
.booking-page { min-height: 100vh; background: var(--color-bg); color: var(--color-text); }
.booking-content { padding: 0 28rpx 34rpx; max-width: 1000rpx; margin: 0 auto; }
button { margin: 0; padding: 0; font-weight: 400; line-height: 1.5; }
button::after { border: 0; }
.hero { display: flex; align-items: center; justify-content: space-between; padding: 42rpx 4rpx 38rpx; overflow: hidden; }
.hero__copy { position: relative; z-index: 1; }
.eyebrow { color: var(--color-primary); font-size: 17rpx; letter-spacing: 3rpx; }
.hero__title { font-size: 43rpx; letter-spacing: -1rpx; font-weight: 650; margin: 10rpx 0 12rpx; }
.hero__subtitle { color: var(--color-primary); font-size: 23rpx; }
.target-art { width: 142rpx; height: 142rpx; border: 1rpx solid var(--color-border); border-radius: 50%; position: relative; display: flex; align-items: center; justify-content: center; margin-right: 12rpx; flex-shrink: 0; opacity: .8; }
.target-art__ring { width: 104rpx; height: 104rpx; border: 1rpx solid var(--color-border); border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.target-art__inner { width: 66rpx; height: 66rpx; border: 1rpx solid var(--color-primary); border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.target-art__center { width: 18rpx; height: 18rpx; background: var(--color-primary); border-radius: 50%; }
.target-art__cross { position: absolute; background: var(--color-border); }
.target-art__cross--x { left: -12rpx; right: -12rpx; height: 1rpx; }
.target-art__cross--y { top: -12rpx; bottom: -12rpx; width: 1rpx; }
.booking-rules { margin: -12rpx 0 20rpx auto; }
.view-tabs { display: flex; align-items: center; border-bottom: 1rpx solid #d9dee7; margin-bottom: 28rpx; gap: 28rpx; }
.view-tabs button { background: transparent; color: var(--color-primary); border-radius: 0; font-size: 28rpx; padding: 0 0 22rpx; min-height: 64rpx; display: flex; align-items: center; gap: 10rpx; }
.view-tabs .is-active { font-weight: 650; color: var(--color-text); box-shadow: 0 3rpx 0 var(--color-text); }
.tab-dot { width: 8rpx; height: 8rpx; background: var(--color-primary); border-radius: 50%; }
.tab-count { font-size: 19rpx; background: #dce1ea; padding: 0 10rpx; border-radius: 999rpx; }
.view-tabs .rules-link { margin-left: auto; font-size: 21rpx; }
.date-panel { padding: 24rpx 18rpx 12rpx; background: #fff; border: 1rpx solid #e1e4eb; border-radius: 22rpx; box-shadow: 0 5rpx 18rpx rgba(61,74,93,.025); }
.date-panel__header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; padding: 0 8rpx; }
.month-title { display: flex; align-items: baseline; gap: 14rpx; }
.month-title__number { font-size: 36rpx; font-weight: 650; }
.month-title__year { font-size: 22rpx; color: var(--color-primary); letter-spacing: 2rpx; }
.today-button { font-size: 21rpx; color: var(--color-primary); background: var(--color-bg); padding: 13rpx 20rpx; border-radius: 999rpx; }
.filters { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin: 28rpx 0 12rpx; }
.device-filters { display: flex; gap: 8rpx; }
.device-filters button { background: transparent; color: var(--color-primary); padding: 14rpx 20rpx; font-size: 23rpx; border-radius: 999rpx; border: 1rpx solid #d7dce6; }
.device-filters .is-active { background: var(--color-text); color: #fff; border-color: var(--color-text); }
.range-controls { display: flex; gap: 8rpx; }
.range-controls button { width: 66rpx; height: 66rpx; background: #fff; color: var(--color-text); border-radius: 50%; line-height: 58rpx; font-size: 38rpx; }
.range-controls button[disabled] { color: var(--color-border); background: transparent; }
.schedule-meta { display: flex; justify-content: space-between; align-items: center; margin: 8rpx 4rpx 18rpx; }
.legend { display: flex; gap: 20rpx; color: var(--color-primary); font-size: 19rpx; }
.legend__item { display: flex; align-items: center; gap: 8rpx; }
.legend__mark { height: 13rpx; width: 13rpx; border: 1rpx solid var(--color-primary); background: #e1e5ee; border-radius: 3rpx; }
.legend__mark--mine { background: var(--color-primary); }
.legend__mark--full { background: transparent; border: 1rpx dashed var(--color-primary); }
.refresh-link { background: transparent; color: var(--color-primary); font-size: 21rpx; padding: 10rpx 0 10rpx 20rpx; }
.booking-note { display: flex; gap: 18rpx; margin: 26rpx 6rpx 0; }
.booking-note__icon { border: 2rpx solid var(--color-primary); width: 27rpx; height: 27rpx; line-height: 27rpx; text-align: center; border-radius: 50%; font-size: 20rpx; flex-shrink: 0; margin-top: 5rpx; color: var(--color-primary); }
.booking-note__title { display: block; font-size: 24rpx; margin-bottom: 6rpx; }
.booking-note__text { display: block; font-size: 20rpx; color: var(--color-primary); line-height: 1.8; }
.page-signature { display: flex; align-items: center; justify-content: center; gap: 18rpx; margin: 40rpx 0 4rpx; color: var(--color-primary); font-size: 16rpx; letter-spacing: 2rpx; }
.page-signature view { width: 40rpx; height: 1rpx; background: var(--color-border); }
.state-card { min-height: 390rpx; padding: 60rpx 24rpx; background: #fff; border-radius: 22rpx; display: flex; align-items: center; justify-content: center; flex-direction: column; text-align: center; box-sizing: border-box; }
.state-card__symbol { font-size: 64rpx; color: var(--color-primary); }
.state-card__title { font-size: 29rpx; font-weight: 600; margin-top: 20rpx; }
.state-card__hint { color: var(--color-primary); font-size: 23rpx; margin-top: 12rpx; }
.primary-button, .secondary-button { background: var(--color-text); color: #fff; font-size: 26rpx; padding: 22rpx 36rpx; border-radius: 14rpx; margin-top: 32rpx; }
.secondary-button { color: var(--color-text); background: var(--color-bg); }
.loading-ring { width: 42rpx; height: 42rpx; border: 4rpx solid var(--color-bg); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.success-mask { position: fixed; inset: 0; background: rgba(38,48,65,.48); display: flex; align-items: center; justify-content: center; padding: 40rpx; z-index: 1001; }
.success-card { width: 100%; max-width: 650rpx; box-sizing: border-box; background: #fff; border-radius: 28rpx; padding: 48rpx 30rpx 24rpx; text-align: center; }
.success-card__check { width: 88rpx; height: 88rpx; margin: 0 auto 24rpx; border-radius: 50%; background: var(--color-bg); color: var(--color-text); line-height: 88rpx; font-size: 44rpx; }
.success-card__title { font-size: 31rpx; font-weight: 650; margin: 16rpx 0 24rpx; }
.success-card__detail { font-size: 27rpx; display: block; }
.success-card__device { display: block; margin: 10rpx 0 24rpx; color: var(--color-primary); font-size: 24rpx; }
.success-card__hint { background: var(--color-bg); padding: 24rpx; border-radius: 14rpx; font-size: 23rpx; color: var(--color-primary); }
.text-button { font-size: 24rpx; color: var(--color-primary); background: transparent; padding: 24rpx 0; }
@media (prefers-reduced-motion: reduce) { .loading-ring { animation: none; } }
</style>
