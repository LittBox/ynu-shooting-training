<template>
  <view class="calendar">
    <view class="head"><view class="axis-label">时段</view><button v-for="day in dates" :key="day.date" class="day" :class="{ selected: day.date === selectedDate }" @tap="$emit('select-date', day.date)"><text>{{ day.label }}</text><text class="day-date">{{ shortDate(day.date) }}</text></button></view>
    <view v-for="row in rows" :key="row.id" class="row" :class="{ 'period-break': row.id === 'S3' || row.id === 'S5' }">
      <view class="time"><text class="period">{{ row.period }} <text class="slot-id">{{ row.id }}</text></text><text class="start">{{ row.start }}</text><text class="end">{{ row.end }}</text></view>
      <view v-for="cell in row.cells" :key="cell.date" class="cell" :class="{ 'cell-selected': cell.date === selectedDate }">
        <button class="slot-card" :class="[`slot-card--${cell.state}`, { 'slot-card--focused': cell.focused }]" :aria-label="`${shortDate(cell.date)} ${row.start} 至 ${row.end}，${cell.label}`" @tap="$emit('inspect', cell)">
          <view class="slot-top"><view class="mark"/><text>{{ cell.title || (cell.state === 'mine' ? '已预约' : cell.state === 'available' ? '可预约' : cell.state === 'full' ? '已约满' : cell.state === 'past' ? '已结束' : cell.state === 'ongoing' ? (cell.walkInCount && !cell.mine ? '可立即训练' : '进行中') : '未开放') }}</text></view>
          <text class="count">{{ cell.subtitle || (cell.mine ? (cell.mine.deviceType === 'rifle' ? '气步枪' : '气手枪') : cell.state === 'available' ? `${cell.remaining} 台设备` : cell.state === 'ongoing' && cell.walkInCount ? `${cell.walkInCount} 台空闲` : '—') }}</text>
          <text class="action">{{ cell.action || (cell.state === 'ongoing' && cell.walkInCount && !cell.mine ? '选择设备 ↗' : '查看设备 / 学员 ↗') }}</text>
        </button>
      </view>
      <view v-if="timeMarker && timeMarker.slotId === row.id" class="time-marker" :style="{ top: `${timeMarker.progress * 100}%` }" aria-hidden="true"><view class="time-marker__dot" /></view>
    </view>
    <view class="foot"><text>所有时间均为北京时间</text><text>{{ hint }}</text></view>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { shortDate, calendarTimeMarker } from '../../domain/booking.js'
const props = defineProps({ dates: { type: Array, required: true }, rows: { type: Array, required: true }, selectedDate: String, hint: { type: String, default: '点击时段查看设备与学员 ↗' }, now: { type: Number, required: true } })
const timeMarker = computed(() => calendarTimeMarker(props.dates, props.rows, props.now))
defineEmits(['inspect', 'select-date'])
</script>
<style lang="scss" scoped>
.calendar { overflow: hidden; background: #fff; border: 1rpx solid #e1e4eb; border-radius: 12rpx; }
.head { display: flex; align-items: stretch; min-height: 108rpx; border-bottom: 1rpx solid #e7e9ef; }
.axis-label { width: 116rpx; flex-shrink: 0; display: flex; align-items: center; justify-content: center; color: var(--color-primary); font-size: 22rpx; }
.day { flex: 1; min-width: 0; margin: 0; padding: 20rpx 4rpx; display: flex; flex-direction: column; justify-content: center; align-items: center; font-size: 24rpx; line-height: 1.4; background: #fff; color: var(--color-primary); border-radius: 0; }
button::after { border: 0; }
.day-date { font-size: 20rpx; margin-top: 5rpx; }
.selected { color: var(--color-text); font-weight: 700; background: var(--color-bg); box-shadow: inset 0 -4rpx 0 var(--color-primary); }
.row { position: relative; display: flex; min-height: 158rpx; border-bottom: 1rpx solid #edf0f4; }
.period-break { border-top: 10rpx solid #f8f9fb; }
.time { width: 116rpx; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; font-variant-numeric: tabular-nums; }
.period { font-size: 19rpx; color: var(--color-primary); margin-bottom: 8rpx; }
.slot-id { font-size: 16rpx; color: var(--color-primary); margin-left: 3rpx; }
.start { font-size: 25rpx; font-weight: 600; line-height: 1.4; color: var(--color-text); }
.end { font-size: 21rpx; color: var(--color-primary); }
.cell { flex: 1; min-width: 0; border-left: 1rpx solid #edf0f4; padding: 10rpx 6rpx; display: flex; }
.cell-selected { background: #fafbfc; }
.slot-card { width: 100%; min-width: 0; margin: 0; padding: 12rpx 10rpx; display: flex; flex-direction: column; align-items: flex-start; justify-content: center; border-radius: 10rpx; border-left: 4rpx solid var(--color-border); background: var(--color-bg); text-align: left; color: var(--color-text); line-height: 1.5; }
.slot-top { display: flex; align-items: center; gap: 6rpx; font-size: 19rpx; white-space: nowrap; }
.mark { width: 7rpx; height: 7rpx; border-radius: 50%; background: currentColor; flex-shrink: 0; }
.count { font-size: 26rpx; font-weight: 650; margin-top: 5rpx; white-space: nowrap; }
.action { font-size: 18rpx; margin-top: 7rpx; white-space: nowrap; }
.slot-card--focused { box-shadow: inset 0 0 0 2rpx var(--color-text); }
.slot-card--available { border-left-color: var(--color-primary); }
.slot-card--available:active { background: #e1e5ee; }
.slot-card--mine { background: #f2baae; color: var(--color-text); border-left-color: #d99586; }
.slot-card--full, .slot-card--closed { background: #fafbfc; color: var(--color-primary); border: 1rpx dashed var(--color-border); }
.slot-card--past { background: transparent; border-left-color: transparent; color: var(--color-primary); opacity: .75; }
.slot-card--ongoing .slot-top { color: #4f856c; font-weight: 600; }
.time-marker { position: absolute; left: 18rpx; right: 0; height: 2rpx; background: #da7c71; z-index: 2; pointer-events: none; transform: translateY(-50%); transition: top 1s linear; }
.time-marker__dot { position: absolute; left: 0; top: 50%; width: 12rpx; height: 12rpx; border-radius: 50%; background: #da7c71; box-shadow: 0 0 0 5rpx rgba(218,124,113,.12); transform: translate(-50%, -50%); }
@media (prefers-reduced-motion: reduce) { .time-marker { transition: none; } }
.slot-card[disabled] { opacity: 1; }
.foot { display: flex; justify-content: space-between; gap: 12rpx; padding: 22rpx 20rpx; color: var(--color-primary); font-size: 18rpx; }
</style>
