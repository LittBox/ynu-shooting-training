<template>
  <view class="page">
    <NavBar title="云大射击" subtitle="YNU SHOOTING CLUB">
      <template #action>
        <button class="text-button" @tap="goAccount">{{ logged ? '个人中心 ↗' : '登录 ↗' }}</button>
      </template>
    </NavBar>
    <!-- 首页保持两个枪种入口的简洁布局。 -->
    <view class="page-content">
      <view v-if="error" class="inline-error">
        <text>{{ error }}</text>
        <button class="text-button" @tap="refresh">重试</button>
      </view>
      <view v-for="device in devices" :key="device.type" class="device-item">
        <DeviceCard :device="device" @quick-book="goBooking" />
      </view>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import NavBar from '@/components/NavBar.vue'
import DeviceCard from '@/components/DeviceCard.vue'
import { bookingService } from '@/services/booking.js'
import { venueToday, slotTimestamp } from '@/domain/booking.js'
import { currentDeviceStatus } from '@/domain/device-status.js'
const logged = ref(false), error = ref('')
const devices = ref([
  { type: 'pistol', typeLabel: '气手枪', bgImage: '/static/images/shouqiang.jpg', availableCount: null },
  { type: 'rifle', typeLabel: '气步枪', bgImage: '/static/images/buqiang.jpg', availableCount: null }
])
let generation = 0, refreshTimer = null
async function refresh() {
  const id = ++generation
  logged.value = !!uni.getStorageSync('token')
  try {
    const date = venueToday(), rows = await bookingService.slots(date)
    if (id !== generation) return
    devices.value = devices.value.map(device => ({ ...device, currentStatus: currentDeviceStatus(rows, device.type, date), availableCount: new Set(rows.filter(row => row.deviceType === device.type && row.available && slotTimestamp(date, row.start) > Date.now()).map(row => row.id)).size }))
    error.value = ''
  } catch (err) { if (id === generation) { error.value = err.message; devices.value = devices.value.map(d => ({ ...d, currentStatus: null, availableCount: null })) } }
  finally { uni.stopPullDownRefresh() }
}
function goBooking(device) { uni.setStorageSync('booking-device-filter', device?.type || ''); uni.setStorageSync('booking-view', 'browse'); uni.switchTab({ url: '/pages/booking/booking' }) }
function goAccount() { logged.value ? uni.switchTab({ url: '/pages/my/my' }) : uni.navigateTo({ url: '/pages/login/login' }) }
function stopRefresh() { clearInterval(refreshTimer); refreshTimer = null; generation++ }
onShow(() => { stopRefresh(); refresh(); refreshTimer = setInterval(refresh, 15000) })
onHide(stopRefresh)
onUnload(stopRefresh)
onPullDownRefresh(refresh)
</script>
<style scoped>
.device-item + .device-item { margin-top: 24rpx; }
.inline-error { display: flex; justify-content: space-between; gap: 16rpx; align-items: center; padding: 18rpx 0; color: var(--color-text-soft); font-size: 22rpx; }
.inline-error button { flex-shrink: 0; }
</style>
