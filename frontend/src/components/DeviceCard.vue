<template>
  <view class="device-card">
    <view class="visual"><image :src="device.bgImage" mode="aspectFill"/><view class="scrim"/><text class="number">{{ device.type === 'rifle' ? '02 / RIFLE' : '01 / PISTOL' }}</text><view class="image-label"><text class="device-name">{{ device.typeLabel }}</text><text class="en-name">{{ device.type === 'rifle' ? 'AIR RIFLE' : 'AIR PISTOL' }}</text></view></view>
    <view class="details"><view><text class="title">{{ device.name || `10 米${device.typeLabel}训练` }}</text><view class="usage"><view class="dot" :class="device.currentStatus?.tone || 'unknown'"/><text>{{ device.currentStatus?.label || '使用状态待更新' }}</text></view><text v-if="device.currentStatus?.hint" class="status-hint">{{ device.currentStatus.hint }}</text><view class="availability"><text>{{ device.availableCount == null ? '时段状态待更新' : device.availableCount > 0 ? `今日还有 ${device.availableCount} 个可约时段` : '今日暂无可约时段' }}</text></view></view><button class="book" :disabled="!canBook" @tap="$emit('quick-book', device)" :aria-label="`查看${device.typeLabel}训练安排`">查看 <text>↗</text></button></view>
  </view>
</template>
<script setup>
defineProps({ device: { type: Object, required: true }, canBook: { type: Boolean, default: true } })
defineEmits(['quick-book'])
</script>
<style scoped>
.device-card { border: 1rpx solid var(--color-border); border-radius: 12rpx; overflow: hidden; background: #fff; }
.visual { height: 290rpx; position: relative; overflow: hidden; background: #e7e6eb; }
.visual image { width: 100%; height: 100%; }
.scrim { position: absolute; inset: 0; background: linear-gradient(180deg,rgba(20,20,35,.15),transparent 30%,rgba(20,20,35,.7)); }
.number { position: absolute; top: 24rpx; left: 26rpx; font-size: 17rpx; letter-spacing: 3rpx; color: #fff; }
.image-label { position: absolute; bottom: 24rpx; left: 26rpx; color: #fff; display: flex; align-items: baseline; gap: 18rpx; }
.device-name { font-size: 42rpx; font-weight: 650; }
.en-name { font-size: 17rpx; letter-spacing: 3rpx; opacity: .8; }
.details { display: flex; align-items: center; justify-content: space-between; padding: 24rpx; gap: 12rpx; }
.title { font-size: 26rpx; font-weight: 600; }
.usage { display: flex; align-items: center; gap: 10rpx; margin-top: 14rpx; font-size: 28rpx; font-weight: 600; }
.status-hint { display: block; font-size: 21rpx; color: var(--color-text-soft); margin-top: 6rpx; }
.dot.busy { background: #cf735d; }
.dot.reserved { background: #c39942; }
.availability { display: flex; align-items: center; gap: 9rpx; margin-top: 7rpx; font-size: 21rpx; color: var(--color-text-soft); }
.dot { width: 9rpx; height: 9rpx; background: var(--color-success); border-radius: 50%; }
.unknown { background: var(--color-text-mute); }
.book { margin: 0; background: var(--color-primary-soft); color: var(--color-primary); border-radius: 8rpx; padding: 0 24rpx; line-height: 72rpx; font-size: 23rpx; flex-shrink: 0; }
.book text { margin-left: 10rpx; }
</style>
