<template>
  <view class="duty-panel">
    <view class="heading"><view><text class="eyebrow">教练员服务</text><view class="title">我的值班时间</view></view><button class="duty-button" :disabled="loading || busy" @tap="load">刷新 ↻</button></view>
    <text class="description">排班覆盖教室内全部设备。请到场后确认到岗，离开前确认离场。</text>
    <view v-if="state" class="summary"><text>当前时段已到岗 {{ state.onDutyCount }} 人</text><text>训练中 {{ state.activeTrainingCount }} 场</text></view>
    <button class="duty-button departure" v-if="state?.hasOpenAttendance" :disabled="busy || loading" @tap="confirmDeparture">确认本人离场</button>
    <text v-if="state?.activeTrainingCount" class="notice">仍有训练进行时，请先由接班教员到岗，或等待全部训练结束后离场。</text>
    <view class="controls">
      <picker mode="date" :value="date" :start="today" :end="lastDay" :disabled="busy" @change="date = $event.detail.value"><view class="picker">{{ shortDate(date) }} · 选择日期 ▾</view></picker>
      <text class="description">选择值班时段</text>
      <view class="slot-grid"><button v-for="(slot, index) in slots" :key="slot.id" class="duty-button slot-choice" :class="{ selected: slotIndex === index }" :disabled="busy || slotEnded(slot)" @tap="slotIndex = index"><text class="slot-name">{{ slot.label }}</text><text class="slot-hours">{{ slot.start }}—{{ slot.end }}</text><text v-if="slotEnded(slot)" class="slot-hours">已结束</text></button></view>
    </view>
    <button class="duty-button add" :disabled="busy || loading || slotEnded(slots[slotIndex])" @tap="act(() => dutyService.schedule(user.id, date, slots[slotIndex].id), '已添加场地排班')">添加我的值班</button>
    <view v-if="error" class="error">{{ error }}</view>
    <view v-if="loading && !state" class="empty">正在同步值班安排…</view>
    <view v-else-if="state && !state.schedules.length" class="empty">当天尚无教员排班，预约暂未开放。</view>
    <view v-for="group in shiftGroups" :key="group.id" class="shift">
      <view class="shift-heading"><text>{{ slotLabel(group.id) }}</text><text class="badge">{{ group.members.length }} 人值班</text></view>
      <scroll-view scroll-x class="members-scroll"><view class="shift-members">
        <view v-for="shift in group.members" :key="shift.id" class="shift-member">
          <text class="member-name">{{ shift.adminName || '值班教员' }}{{ shift.adminId === user.id ? '（我）' : '' }}</text>
          <text class="badge member-status">{{ statusLabel(shift) }}</text>
          <text v-if="shift.arrivedAt" class="attendance">到岗 {{ timeLabel(shift.arrivedAt) }}<text v-if="shift.departedAt"> · 离场 {{ timeLabel(shift.departedAt) }}</text></text>
          <ReminderButton v-if="shift.adminId === user.id && shift.state === 'PLANNED'" kind="DUTY" :target-id="shift.id" :active="active" :refresh-key="state" />
          <view v-if="shift.adminId === user.id" class="actions">
            <button class="duty-button" v-if="shift.state === 'PLANNED'" :disabled="busy || loading" @tap="confirmRemove(shift)">取消我的排班</button>
            <button class="duty-button arrival" v-if="shift.canArrive" :disabled="busy || loading" @tap="confirmArrival(shift)">{{ shift.state === 'LEFT' ? '重新到岗' : '我已到岗' }}</button>
          </view>
        </view>
      </view></scroll-view>
    </view>
    <text class="footnote">到岗确认：当天值班开始前 30 分钟至结束前。跨时段继续值班时，请为下一时段再次确认到岗。</text>
  </view>
</template>
<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import ReminderButton from '../ReminderButton.vue'
import { dutyService } from '../../services/duty.js'
import { SLOT_DEFINITIONS, venueToday, addDays, shortDate, slotTimestamp } from '../../domain/booking.js'
const props = defineProps({ user: { type: Object, required: true }, active: Boolean, initialDate: { type: String, default: '' } })
const date = ref(props.initialDate || venueToday()), slotIndex = ref(0), state = ref(null), loading = ref(false), busy = ref(false), error = ref('')
const slots = SLOT_DEFINITIONS
const shiftGroups = computed(() => slots.map(slot => ({ id: slot.id, members: (state.value?.schedules || []).filter(shift => shift.slotId === slot.id) })).filter(group => group.members.length))
const now = ref(Date.now())
function slotEnded(slot) { return slotTimestamp(date.value, slot.end) <= now.value }
const today = computed(() => venueToday(now.value)), lastDay = computed(() => addDays(today.value, 7))
const slotLabels = slots.map(s => `${s.label} ${s.start}—${s.end}`)
let generation = 0, timer, disposed = false
function slotLabel(id) { return slotLabels[slots.findIndex(s => s.id === id)] || id }
function timeLabel(value) { return value?.slice(11, 16) || '' }
function statusLabel(shift) { return shift.coveringNow ? '已到岗' : shift.state === 'PRESENT' ? '待确认离场 / 续班' : shift.state === 'LEFT' ? '已离场' : '未到岗' }
async function load() {
  if (!props.active) return
  now.value = Date.now()
  const id = ++generation
  loading.value = true
  try {
    const value = await dutyService.status(date.value)
    if (id === generation && !disposed) { state.value = value; error.value = '' }
  } catch (e) { if (id === generation && !disposed) error.value = e.message }
  finally { if (id === generation && !disposed) loading.value = false }
}
async function act(operation, message) {
  if (busy.value || !props.active || disposed) return
  busy.value = true; error.value = ''
  try { await operation(); if (!disposed && props.active) { uni.showToast({ title: message, icon: 'none' }); await load() } }
  catch (e) { if (!disposed) error.value = e.message }
  finally { if (!disposed) busy.value = false }
}
function confirmArrival(shift) {
  uni.showModal({ title: '确认已到达射击教室？', content: '请本人到场后确认，并在值班期间提供现场指导。', confirmText: '确认到岗', success: r => { if (r.confirm) act(() => dutyService.arrive(shift.id), '已确认到岗') } })
}
function confirmDeparture() {
  uni.showModal({ title: '确认离开射击教室？', content: '有训练进行时，请先完成交接。确认离场会结束本人所有未结束的到岗记录。', confirmText: '确认离场', success: r => { if (r.confirm) act(dutyService.depart, '已确认离场') } })
}
function confirmRemove(shift) {
  uni.showModal({ title: '取消这次值班？', content: '已有预约时，场地必须保留至少一位排班教员。', confirmText: '取消排班', success: r => { if (r.confirm) act(() => dutyService.remove(shift.id), '已取消排班') } })
}
watch(() => [props.active, date.value], () => {
  generation++; loading.value = false; state.value = null; clearInterval(timer)
  now.value = Date.now()
  if (slotEnded(slots[slotIndex.value])) slotIndex.value = Math.max(0, slots.findIndex(slot => !slotEnded(slot)))
  if (props.active) { load(); timer = setInterval(() => { if (!loading.value && !busy.value) load() }, 30000) }
}, { immediate: true })
onUnmounted(() => { disposed = true; generation++; clearInterval(timer) })
</script>
<style scoped>
.duty-panel { margin: 30rpx 0; padding: 28rpx; border: 1rpx solid var(--color-border); border-radius: 16rpx; background: #fff; }
.heading, .summary, .shift-heading, .actions { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.title { font-size: 30rpx; font-weight: 600; margin-top: 6rpx; }
.description, .footnote, .notice, .attendance { display: block; font-size: 22rpx; line-height: 1.7; color: var(--color-text-soft); margin-top: 16rpx; }
.summary { flex-wrap: wrap; margin: 22rpx 0; color: var(--color-primary); font-size: 23rpx; }
.controls { display: flex; flex-direction: column; gap: 12rpx; margin-top: 24rpx; }
.picker { padding: 18rpx; background: var(--color-bg); border-radius: 8rpx; font-size: 24rpx; }
.duty-button { margin: 0; padding: 10rpx 18rpx; font-size: 22rpx; line-height: 1.8; background: var(--color-bg); color: var(--color-text); border-radius: 8rpx; }
.duty-button::after { border: 0; }
.slot-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12rpx; }
.slot-choice { border: 1rpx solid var(--color-border); padding: 18rpx 10rpx; }
.slot-choice.selected { border-color: var(--color-primary); background: #f0eefb; color: var(--color-primary); }
.slot-name, .slot-hours { display: block; }
.slot-hours { font-size: 21rpx; }
.add { margin: 16rpx 0 22rpx; width: 100%; }
.arrival, .departure { background: var(--color-text); color: #fff; }
.departure { width: 100%; }
.shift { padding: 22rpx 0; border-top: 1rpx solid var(--color-border); }
.shift-heading { font-size: 24rpx; }
.members-scroll { width: 100%; margin-top: 16rpx; }
.shift-members { display: flex; gap: 12rpx; align-items: stretch; }
.shift-member { flex: 1 0 220rpx; min-width: 0; padding: 16rpx; box-sizing: border-box; background: var(--color-bg); border-radius: 10rpx; }
.member-name, .member-status { display: block; font-size: 23rpx; }
.member-status { margin-top: 8rpx; }
.shift-member .actions { flex-direction: column; align-items: stretch; }
.shift-member .attendance { font-size: 20rpx; }
.badge { font-size: 20rpx; color: var(--color-primary); }
.shift-time { display: block; font-size: 23rpx; margin-top: 10rpx; }
.actions { justify-content: flex-end; margin-top: 16rpx; }
.empty, .error { margin: 20rpx 0; font-size: 23rpx; line-height: 1.7; }
.error { color: var(--color-danger); }
</style>
