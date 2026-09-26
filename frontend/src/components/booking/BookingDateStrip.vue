<template>
  <view class="date-strip">
    <button v-for="day in dates" :key="day.date" class="day" :class="{ selected: day.date === selected, today: day.date === today }" :aria-label="`${day.date} ${day.label}`" @tap="$emit('select', day.date)">
      <text class="weekday">{{ day.label }}</text><text class="number">{{ Number(day.day) }}</text><view class="dot" />
    </button>
  </view>
</template>
<script setup>
defineProps({ dates: { type: Array, required: true }, selected: String, today: String })
defineEmits(['select'])
</script>
<style lang="scss" scoped>
.date-strip { display: flex; justify-content: space-between; gap: 4rpx; padding: 10rpx 0; }
.day { display: flex; flex: 1; min-width: 0; flex-direction: column; align-items: center; margin: 0; padding: 14rpx 0 8rpx; border-radius: 10rpx; background: transparent; color: var(--color-primary); line-height: 1.5; border: 1rpx solid transparent; }
.day::after { border: 0; }
.weekday { font-size: 21rpx; }
.number { font-size: 32rpx; font-weight: 600; margin-top: 9rpx; font-variant-numeric: tabular-nums; }
.dot { width: 6rpx; height: 6rpx; margin-top: 8rpx; border-radius: 50%; }
.today .dot { background: var(--color-primary); }
.selected { background: var(--color-primary); color: #fff; box-shadow: none; }
.selected .dot { background: #fff; }
</style>
