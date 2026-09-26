<template>
  <view v-if="selection" class="mask" @tap="$emit('close')" @touchmove.stop.prevent>
    <view class="sheet" role="dialog" aria-label="时段设备与预约学员" @tap.stop>
      <view class="handle" />
      <view class="heading"><view><text class="eyebrow">ON THE FIRING LINE</text><view class="title">这一场，谁在训练</view></view><button class="close" aria-label="关闭时段详情" @tap="$emit('close')">×</button></view>
      <view class="session"><text>{{ shortDate(selection.date) }} · {{ weekday(selection.date) }}</text><view>{{ selection.slot.start }}<text> — </text>{{ selection.slot.end }}</view><text>北京时间 · {{ typeLabel }}</text></view>
      <view class="toolbar"><text>设备与预约学员</text><button :disabled="loading" @tap="$emit('refresh')">{{ loading ? '同步中…' : '刷新 ↻' }}</button></view>
      <scroll-view scroll-y class="device-scroll">
        <view v-if="error" class="empty"><text>{{ error }}</text><button @tap="$emit('refresh')">重新加载</button></view>
        <view v-else-if="!detail" class="empty">正在同步这一场的设备安排…</view>
        <template v-else>
          <view v-for="device in devices" :key="device.deviceId" class="device">
            <view class="device-head"><view class="symbol">{{ device.deviceType === 'rifle' ? 'R' : 'P' }}</view><view class="device-name"><text>{{ device.deviceName }}</text><text class="muted">10 米{{ device.deviceType === 'rifle' ? '气步枪' : '气手枪' }}</text></view><text class="device-label">{{ canWalkIn(device) ? '可立即训练' : device.available ? '可预约' : deviceLabel(device) }}</text></view>
            <view v-for="person in device.reservations" :key="person.bookingId" class="person" :class="{ training: person.status === 'IN_USE' }">
              <view class="person-copy"><view class="person-name">{{ person.displayName || '已预约学员' }}<text v-if="person.mine" class="mine">我</text></view><text v-if="person.studentNo" class="student-no">学号 {{ person.studentNo }}</text><text v-if="!person.displayName" class="student-no">登录后查看预约昵称</text></view>
              <text class="status"><text v-if="person.status === 'IN_USE'">● </text>{{ STATUS_LABELS[person.status] || '状态待同步' }}</text>
            </view>
            <view v-if="!device.reservations.length" class="unoccupied">本时段暂无预约学员</view>
            <button v-if="canWalkIn(device)" class="book" :disabled="loading" @tap="$emit('walkin', device)">立即训练 <text>↗</text></button>
            <button v-else-if="device.available" class="book" :disabled="loading" @tap="$emit('book', device)">预约这台设备 <text>↗</text></button>
            <button v-else-if="device.reservations.some(person => person.mine)" class="manage" @tap="$emit('manage')">查看我的预约 ↗</button>
          </view>
          <view v-if="!devices.length" class="empty">该类型暂无设备，可切换其他设备类型查看。</view>
        </template>
      </scroll-view>
      <view v-if="detail" class="coach-status">{{ devices.some(d => d.coachPresent) ? '本时段已有教员到岗' : devices.some(d => d.staffed) ? '本时段已排班，开始训练前须有教员到岗' : '本时段场地未排班' }}</view>
      <view v-if="detail" class="footnote"><text>{{ scopeHint }}</text><text>{{ updatedTime }} 更新 · 每 30 秒同步</text></view>
      <view class="coordination"><text class="coordination-title">让每一场训练，都好安排。</text><text>如需协调时段，请与预约同学或现场教练沟通。</text><text>预约不代表已到场，以签到及训练状态为准。</text></view>
      <button v-if="detail?.viewerScope === 'GUEST'" class="login" @tap="$emit('login')">登录查看预约学员 ↗</button>
    </view>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { shortDate, weekday, STATUS_LABELS, slotTimestamp } from '../../domain/booking.js'
const props = defineProps({ selection: Object, detail: Object, loading: Boolean, error: String, now: Number })
defineEmits(['close', 'refresh', 'book', 'walkin', 'manage', 'login'])
const devices = computed(() => (props.detail?.devices || []).filter(row => !props.selection?.deviceType || row.deviceType === props.selection.deviceType))
const typeLabel = computed(() => props.selection?.deviceType === 'rifle' ? '气步枪' : props.selection?.deviceType === 'pistol' ? '气手枪' : '全部设备')
const scopeHint = computed(() => props.detail?.viewerScope === 'STAFF' ? '教练视图 · 姓名 / 学号' : props.detail?.viewerScope === 'STUDENT' ? '学员视图 · 预约昵称' : '访客视图 · 设备安排')
const updatedTime = computed(() => {
  const time = new Date(props.detail?.updatedAt).getTime()
  return Number.isFinite(time) ? new Date(time + 8 * 3600000).toISOString().slice(11, 16) : '刚刚'
})
function canWalkIn(device) {
  return device.walkInAvailable && props.now >= slotTimestamp(props.selection.date, props.selection.slot.start) && props.now < slotTimestamp(props.selection.date, props.selection.slot.end)
}
function deviceLabel(device) {
  if (device.deviceStatus === 'MAINTENANCE') return '维护中'
  if (device.deviceStatus === 'DISABLED') return '已停用'
  if (device.reservations.some(p => ['BOOKED', 'CHECKED_IN', 'IN_USE', 'COMPLETED'].includes(p.status))) return '已有安排'
  if (!device.staffed) return '未排班'
  return '不可预约'
}
</script>
<style lang="scss" scoped>
.mask { position: fixed; inset: 0; z-index: 1000; background: rgba(61,74,93,.48); display: flex; align-items: flex-end; }
.sheet { width: 100%; max-width: 1000rpx; margin: 0 auto; max-height: 92vh; overflow-y: auto; box-sizing: border-box; background: #fff; border-radius: 32rpx 32rpx 0 0; padding: 16rpx 32rpx calc(24rpx + env(safe-area-inset-bottom)); color: var(--color-text); }
.handle { width: 64rpx; height: 7rpx; background: var(--color-border); border-radius: 99rpx; margin: 0 auto 24rpx; flex-shrink: 0; }
.heading { display: flex; justify-content: space-between; align-items: center; gap: 12rpx; }
.eyebrow { color: var(--color-primary); font-size: 17rpx; letter-spacing: 3rpx; }
.title { font-size: 36rpx; font-weight: 650; margin-top: 8rpx; }
button { margin: 0; line-height: 1.5; }
button::after { border: 0; }
.close { width: 68rpx; height: 68rpx; padding: 0; line-height: 64rpx; border-radius: 50%; background: var(--color-bg); color: var(--color-text); font-size: 40rpx; flex-shrink: 0; }
.session { background: var(--color-text); color: #fff; padding: 22rpx 26rpx; border-radius: 16rpx; margin: 24rpx 0 18rpx; }
.session > text { font-size: 21rpx; color: #f0f1f7; }
.session view { font-size: 38rpx; font-weight: 600; margin: 4rpx 0; font-variant-numeric: tabular-nums; }
.session view text { color: var(--color-border); }
.toolbar { display: flex; justify-content: space-between; align-items: center; padding: 0 2rpx 16rpx; font-size: 26rpx; font-weight: 600; }
.toolbar button { background: transparent; color: var(--color-primary); font-size: 22rpx; padding: 8rpx; }
.device-scroll { height: 480rpx; }
.device { border: 1rpx solid #dfe3eb; border-radius: 16rpx; padding: 22rpx; margin-bottom: 16rpx; }
.device-head { display: flex; align-items: center; gap: 16rpx; }
.symbol { width: 62rpx; height: 62rpx; line-height: 62rpx; text-align: center; background: var(--color-bg); border-radius: 12rpx; font-size: 28rpx; font-weight: 600; flex-shrink: 0; }
.device-name { display: flex; flex-direction: column; flex: 1; min-width: 0; gap: 3rpx; font-size: 26rpx; font-weight: 600; overflow-wrap: anywhere; }
.muted, .device-label { color: var(--color-primary); font-size: 20rpx; font-weight: 400; }
.device-label { flex-shrink: 0; }
.person { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; padding: 20rpx; background: var(--color-bg); border-radius: 10rpx; margin-top: 18rpx; }
.training { border-left: 5rpx solid var(--color-primary); }
.person-copy { min-width: 0; }
.person-name { font-size: 28rpx; font-weight: 600; overflow-wrap: anywhere; }
.mine { margin-left: 12rpx; font-size: 18rpx; padding: 3rpx 9rpx; border: 1rpx solid var(--color-border); border-radius: 6rpx; }
.student-no { display: block; font-size: 21rpx; color: var(--color-primary); margin-top: 6rpx; overflow-wrap: anywhere; }
.status { font-size: 22rpx; white-space: nowrap; }
.book, .login { width: 100%; background: var(--color-primary); color: #fff; font-size: 24rpx; padding: 17rpx; border-radius: 10rpx; margin-top: 14rpx; }
.book { display: flex; justify-content: space-between; }
.manage { background: transparent; color: var(--color-primary); font-size: 22rpx; padding: 16rpx 0 0; text-align: right; }
.unoccupied { font-size: 23rpx; color: var(--color-primary); margin-top: 20rpx; }
.empty { padding: 40rpx 12rpx; color: var(--color-primary); font-size: 24rpx; text-align: center; }
.empty button { margin: 22rpx auto 0; font-size: 24rpx; color: var(--color-text); background: var(--color-bg); }
.coach-status { font-size: 22rpx; line-height: 1.7; color: var(--color-primary); margin-top: 12rpx; }
.footnote { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8rpx; color: var(--color-primary); font-size: 18rpx; margin: 12rpx 0; }
.coordination { display: flex; flex-direction: column; gap: 5rpx; font-size: 20rpx; line-height: 1.7; color: var(--color-primary); margin-top: 12rpx; }
.coordination-title { color: var(--color-text); font-size: 23rpx; font-weight: 600; }
.heading, .session, .toolbar, .footnote, .coordination, .login { flex-shrink: 0; }
</style>
