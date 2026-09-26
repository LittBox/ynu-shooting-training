<template>
  <view>
    <view class="intro"><text>{{ history ? '每一次专注，都有迹可循。' : '安排好下一次训练。' }}</text><text>{{ bookings.length }} 条记录</text></view>
    <view v-if="!bookings.length" class="empty"><text class="empty-title">{{ history ? '还没有已完成的训练' : '下一次训练，从这里开始' }}</text><text class="empty-hint">{{ history ? '训练结束后会自动移到这里，可查看成绩、补登或继续原时段训练。' : '还没有预约记录，去选一个适合你的时段。' }}</text><button @tap="$emit('browse')">浏览可约时段 ↗</button></view>
    <view v-for="booking in sortedBookings" :key="booking.id" class="record">
      <view class="header"><text>{{ shortDate(booking.slotDate) }} · {{ weekday(booking.slotDate) }}</text><text class="status" :class="{ active: activeStatuses.includes(booking.status) }">{{ statusLabels[booking.status] || booking.status }}</text></view>
      <view class="time">{{ booking.slotStart }} — {{ booking.slotEnd }}</view><view class="name">{{ booking.deviceName }}</view>
      <view v-if="booking.status === 'BOOKED'" class="footer"><text>{{ canCheckIn(booking, now) ? '已开放签到，请确认到达场馆' : '开始前 30 分钟开放签到' }}</text><view class="actions"><button :disabled="busy" @tap="$emit('cancel', booking)">取消预约</button><button v-if="canCheckIn(booking, now)" class="checkin" :disabled="busy" @tap="$emit('checkin', booking)">到场签到</button></view></view>
      <view v-else-if="booking.status === 'CHECKED_IN'" class="footer"><text>签到完成，请等待本时段教员到岗并确认设备就绪后开始。</text><view class="actions"><button class="checkin" :disabled="busy" @tap="$emit('start', booking)">开始训练</button></view></view>
      <view v-else-if="booking.status === 'IN_USE'" class="footer"><text>每轮可选不同模式登记，结束时分别选各模式最高分。</text><view class="actions"><button class="checkin" :disabled="busy || !booking.sessionId" @tap="$emit('score', booking)">登记成绩</button><button :disabled="busy || !booking.sessionId" @tap="$emit('score', booking)">结束训练</button></view></view>
      <ReminderButton v-if="booking.status === 'BOOKED'" kind="TRAINING" :target-id="booking.id" :active="active" :refresh-key="bookings" />
      <view v-if="booking.status === 'COMPLETED' && booking.sessionId" class="actions"><button @tap="$emit('score', booking)">查看成绩 / 补登 / 继续 ↗</button></view>
    </view>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import ReminderButton from '../ReminderButton.vue'
import { shortDate, weekday, canCheckIn, ACTIVE_STATUSES, STATUS_LABELS } from '../../domain/booking.js'
const props = defineProps({ bookings: { type: Array, default: () => [] }, now: Number, busy: Boolean, history: Boolean, active: { type: Boolean, default: true } })
defineEmits(['browse', 'cancel', 'checkin', 'start', 'score'])
const activeStatuses = ACTIVE_STATUSES
const statusLabels = STATUS_LABELS
const sortedBookings = computed(() => [...props.bookings].sort((a, b) => {
  const active = Number(ACTIVE_STATUSES.includes(b.status)) - Number(ACTIVE_STATUSES.includes(a.status))
  if (active) return active
  const order = `${a.slotDate}${a.slotStart}`.localeCompare(`${b.slotDate}${b.slotStart}`)
  return ACTIVE_STATUSES.includes(a.status) ? order : -order
}))
</script>
<style lang="scss" scoped>
.intro { display: flex; justify-content: space-between; font-size: 22rpx; color: var(--color-primary); margin: 10rpx 0 24rpx; }
.record { background: #fff; border: 1rpx solid #e1e4eb; border-radius: 12rpx; padding: 28rpx; margin-bottom: 20rpx; }
.header { display: flex; align-items: center; justify-content: space-between; font-size: 24rpx; color: var(--color-primary); }
.status { padding: 6rpx 16rpx; background: var(--color-bg); border-radius: 999rpx; font-size: 21rpx; }
.active { color: #fff; background: var(--color-primary); }
.time { color: var(--color-text); font-size: 42rpx; font-weight: 600; margin-top: 20rpx; }
.name { color: var(--color-primary); font-size: 25rpx; margin-top: 4rpx; }
.footer { margin-top: 24rpx; padding-top: 22rpx; border-top: 1rpx solid #e1e4eb; color: var(--color-primary); font-size: 21rpx; }
.actions { display: flex; justify-content: flex-end; gap: 16rpx; margin-top: 20rpx; }
button { margin: 0; padding: 0 22rpx; line-height: 72rpx; font-size: 23rpx; border-radius: 12rpx; background: var(--color-bg); color: var(--color-text); }
button::after { border: 0; }
.checkin { background: var(--color-text); color: #fff; }
.empty { display: flex; align-items: center; flex-direction: column; padding: 100rpx 20rpx; background: #fff; border-radius: 12rpx; }
.empty-title { font-size: 29rpx; font-weight: 600; margin-top: 16rpx; }
.empty-hint { font-size: 23rpx; color: var(--color-primary); margin: 16rpx 0 32rpx; }
</style>
