<template>
  <view class="duty-panel">
    <view class="date-panel">
      <view class="date-heading"><view class="month-title"><text>{{ Number(date.slice(5, 7)) }}月</text><text>{{ date.slice(0, 4) }}</text></view><button class="today-button" :disabled="busy" @tap="goToday">回到今天 ↗</button></view>
      <BookingDateStrip :dates="dates" :selected="date" :today="today" @select="chooseDate" />
    </view>
    <view class="schedule-controls"><view><text class="schedule-title">教室值班日程</text><text class="hint">{{ state ? `当前三天内，我有 ${myCount} 个值班时段` : '同步本人值班安排' }}</text></view><view class="range-controls"><button aria-label="前面三个值班日期" :disabled="offset === 0 || loading || busy" @tap="movePage(-1)">‹</button><button aria-label="后面三个值班日期" :disabled="offset >= dates.length - 3 || loading || busy" @tap="movePage(1)">›</button></view></view>
    <view class="schedule-meta"><view class="legend"><text><text class="mark">□</text> 可预约</text><text><text class="mark mine-mark">■</text> 我的值班</text></view><button class="text-button" :disabled="loading || busy" @tap="refresh()">{{ loading ? '更新中…' : '刷新 ↻' }}</button></view>
    <view v-if="loading && !state" class="state-card">正在同步值班日程…</view>
    <view v-else-if="error" class="state-card"><text>暂时没能加载值班安排</text><text class="hint">{{ error }}</text><button class="text-button" @tap="refresh()">重新加载 ↻</button></view>
    <BookingCalendar v-else-if="state" :dates="visibleDates" :rows="rows" :now="now" :selected-date="date" hint="点击时段安排或查看值班 ↗" @inspect="openCell" @select-date="chooseDate" />
    <view v-if="state" class="attendance-card"><view class="attendance-header"><text class="schedule-title">当前场地</text><text class="hint">已到岗 {{ state.onDutyCount }} 人 · 训练中 {{ state.activeTrainingCount }} 场</text></view><button v-if="state.hasOpenAttendance" class="primary-button" :disabled="busy || loading" @tap="confirmDeparture">确认本人离场</button><text v-if="state.activeTrainingCount" class="hint">还有训练进行时，请由接班教员先到岗，或等待全部训练结束后离场。</text></view>
    <view v-if="detail" id="duty-slot-detail" class="detail-card">
      <view class="detail-heading"><view><text class="eyebrow">{{ shortDate(detail.date) }} · {{ weekday(detail.date) }}</text><view class="detail-time">{{ detail.slot.start }}–{{ detail.slot.end }}</view></view><text class="own-tag" v-if="detail.mine">我的值班</text></view>
      <text class="hint">{{ detail.slot.label }} · 值班覆盖整间教室，可由多位教员共同值班。</text>
      <view v-if="detail.mine" class="my-shift"><text class="schedule-title">{{ dutyStatusLabel(detail.mine, detail.date, detail.slot, now) }}</text><text v-if="detail.mine.arrivedAt" class="hint">到岗 {{ timeLabel(detail.mine.arrivedAt) }}<text v-if="detail.mine.departedAt"> · 离场 {{ timeLabel(detail.mine.departedAt) }}</text></text>
        <view class="actions"><button v-if="detail.mine.state === 'PLANNED'" class="secondary-button" :disabled="busy || loading" @tap="confirmRemove(detail.mine)">取消我的值班</button><button v-if="canArriveForDuty(detail.mine, detail.date, detail.slot, now)" class="primary-button" :disabled="busy || loading" @tap="confirmArrival(detail.mine)">{{ detail.mine.state === 'LEFT' ? '重新到岗' : '我已到岗' }}</button></view>
        <ReminderButton v-if="detail.mine.state === 'PLANNED' && !detail.ended" :key="detail.mine.id" kind="DUTY" :target-id="detail.mine.id" :active="active" :refresh-key="state" />
      </view>
      <button v-else-if="!detail.ended" class="primary-button reserve" :disabled="busy || loading" :loading="busy" @tap="confirmSchedule">预约这个值班时段</button>
      <text v-else class="hint">该时段已结束，不能新增值班预约。</text>
      <view class="roster"><text class="schedule-title">本时段教员 · {{ detail.members.length }} 人</text><view v-for="shift in detail.members" :key="shift.id" class="member"><text>{{ shift.adminName || '值班教员' }}{{ shift.adminId === user.id ? '（我）' : '' }}</text><text class="hint">{{ dutyStatusLabel(shift, detail.date, detail.slot, now) }}</text></view><text v-if="!detail.members.length" class="hint">尚无教员预约，排班后将开放本时段训练预约。</text></view>
    </view>
    <view v-if="actionError" class="action-error">{{ actionError }}</view>
    <text class="footnote">粉色卡片为本人已预约的值班。到场后仍需确认到岗，确认窗口为开始前 30 分钟至结束前；跨时段继续值班需再次确认。日程每 30 秒自动刷新。</text>
  </view>
</template>
<script setup>
import { nextTick, onUnmounted, watch } from 'vue'
import BookingDateStrip from '../booking/BookingDateStrip.vue'
import BookingCalendar from '../booking/BookingCalendar.vue'
import ReminderButton from '../ReminderButton.vue'
import { dutyService } from '../../services/duty.js'
import { useDutySchedule } from '../../composables/useDutySchedule.js'
import { shortDate, weekday } from '../../domain/booking.js'
import { dutyStatusLabel, canArriveForDuty } from '../../domain/duty.js'
const props = defineProps({ user: { type: Object, required: true }, active: Boolean, initialDate: { type: String, default: '' } })
const emit = defineEmits(['date-change', 'unauthorized'])
const { now, today, date, dates, offset, visibleDates, state, rows, detail, myCount, loading, busy, error, actionError, refresh, chooseDate, goToday, movePage, inspect, perform, start, stop } = useDutySchedule(() => props.user, () => emit('unauthorized'))
let disposed = false
const timeLabel = value => value?.slice(11, 16) || ''
async function openCell(cell) { inspect(cell); if (detail.value) { await nextTick(); uni.pageScrollTo({ selector: '#duty-slot-detail', duration: 200 }) } }
function confirm(title, content, confirmText, operation, message) {
  if (busy.value || loading.value || !props.active) return
  const token = uni.getStorageSync('token'), userId = props.user.id
  uni.showModal({ title, content, confirmText, success: answer => {
    if (answer.confirm && !disposed && props.active && userId === props.user.id && token === uni.getStorageSync('token')) perform(operation, message)
  } })
}
function confirmSchedule() {
  const cell = detail.value, userId = props.user.id
  if (!cell || cell.mine || cell.ended) return
  confirm('预约这个值班时段？', `${shortDate(cell.date)} ${cell.slot.start}–${cell.slot.end}\n排班覆盖整间教室，请到场后再确认到岗。`, '确认预约', () => dutyService.schedule(userId, cell.date, cell.slot.id), '值班预约成功')
}
function confirmArrival(shift) { confirm('确认已到达射击教室？', '请本人到场后确认，并在值班期间提供现场指导。', '确认到岗', () => dutyService.arrive(shift.id), '已确认到岗') }
function confirmDeparture() { confirm('确认离开射击教室？', '有训练进行时，请先完成交接。确认离场会结束本人所有未结束的到岗记录。', '确认离场', dutyService.depart, '已确认离场') }
function confirmRemove(shift) { confirm('取消这次值班？', `${shortDate(shift.slotDate)}\n已有预约时，场地必须保留至少一位排班教员。`, '取消值班', () => dutyService.remove(shift.id), '已取消值班') }
watch(date, value => emit('date-change', value))
watch(() => [props.active, props.user.id], () => { stop(); if (props.active) start(props.initialDate) }, { immediate: true })
onUnmounted(() => { disposed = true; stop() })
defineExpose({ refresh })
</script>
<style scoped>
.duty-panel { padding-bottom: 8rpx; }
button { margin: 0; line-height: 1.5; }
button::after { border: 0; }
.hint, .footnote { display: block; color: var(--color-primary); font-size: 21rpx; line-height: 1.7; margin-top: 8rpx; overflow-wrap: anywhere; }
.date-panel { padding: 24rpx 18rpx 12rpx; background: #fff; border: 1rpx solid #e1e4eb; border-radius: 22rpx; }
.date-heading, .schedule-controls, .schedule-meta, .detail-heading, .attendance-header { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.date-heading { margin-bottom: 16rpx; padding: 0 8rpx; }
.month-title { display: flex; align-items: baseline; gap: 14rpx; }
.month-title text:first-child { font-size: 36rpx; font-weight: 650; }
.month-title text:last-child { font-size: 22rpx; color: var(--color-primary); letter-spacing: 2rpx; }
.today-button { background: var(--color-bg); color: var(--color-primary); padding: 13rpx 20rpx; border-radius: 999rpx; font-size: 21rpx; }
.schedule-controls { margin: 26rpx 0 8rpx; }
.schedule-title { font-size: 25rpx; font-weight: 600; }
.range-controls { display: flex; gap: 8rpx; }
.range-controls button { width: 66rpx; height: 66rpx; background: #fff; color: var(--color-text); border-radius: 50%; padding: 0; line-height: 58rpx; font-size: 38rpx; }
.range-controls button[disabled] { color: var(--color-border); background: transparent; }
.schedule-meta { margin: 4rpx 4rpx 16rpx; }
.legend { display: flex; gap: 20rpx; color: var(--color-primary); font-size: 19rpx; }
.mark { margin-right: 8rpx; }
.mine-mark { color: #f2baae; }
.attendance-card, .detail-card { margin-top: 22rpx; padding: 26rpx; background: #fff; border: 1rpx solid #e1e4eb; border-radius: 16rpx; }
.attendance-header { flex-wrap: wrap; }
.attendance-card .primary-button { margin-top: 20rpx; }
.detail-time { margin: 10rpx 0; font-size: 34rpx; font-weight: 650; }
.own-tag { background: #f2baae; color: var(--color-text); padding: 8rpx 14rpx; font-size: 21rpx; border-radius: 8rpx; flex-shrink: 0; }
.my-shift { margin-top: 24rpx; padding: 22rpx; border-radius: 12rpx; background: var(--color-bg); }
.actions { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 18rpx; }
.primary-button, .secondary-button { font-size: 24rpx; padding: 20rpx; border-radius: 10rpx; line-height: 1.5; }
.primary-button { color: #fff; background: var(--color-text); }
.secondary-button { color: var(--color-text); background: #fff; border: 1rpx solid var(--color-border); }
.actions button { flex: 1; min-width: 160rpx; }
.reserve { width: 100%; margin-top: 24rpx; }
.roster { margin-top: 24rpx; padding-top: 20rpx; border-top: 1rpx solid #e1e4eb; }
.member { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8rpx 18rpx; font-size: 23rpx; padding: 18rpx 0 0; }
.member .hint { margin: 0; }
.footnote { margin: 24rpx 6rpx 0; }
.state-card { padding: 56rpx 24rpx; text-align: center; }
.action-error { color: var(--color-danger); margin-top: 20rpx; font-size: 24rpx; line-height: 1.7; }
</style>
