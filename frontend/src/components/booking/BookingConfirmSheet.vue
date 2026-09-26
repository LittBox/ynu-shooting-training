<template>
  <view v-if="selection" class="mask" @tap="close" @touchmove.stop.prevent>
    <view class="sheet" role="dialog" :aria-label="walkIn ? '确认立即训练' : '确认训练预约'" @tap.stop>
      <view class="handle"/>
      <view class="heading"><view><text class="eyebrow">YOUR NEXT SESSION</text><view class="title">{{ walkIn ? '利用空档，现在开始训练' : '为下一次专注，留个位置' }}</view></view><button class="close" :disabled="busy" aria-label="关闭预约确认" @tap="close">×</button></view>
      <view class="session"><view><text class="date">{{ shortDate(selection.date) }} · {{ weekday(selection.date) }}</text><view class="time">{{ selection.slot.start }}<text> — </text>{{ selection.slot.end }}</view></view><view class="duration">{{ duration }}<text>{{ walkIn ? '分钟剩余' : '分钟' }}</text></view></view>
      <view class="label">选择训练设备<text>每次使用 1 台</text></view>
      <scroll-view scroll-y class="device-scroll">
        <button v-for="device in devices" :key="device.deviceId" class="device" :class="{ selected: selectedId === device.deviceId }" :disabled="!available(device) || busy" @tap="selectedId = device.deviceId">
          <view class="symbol">{{ device.deviceType === 'rifle' ? 'R' : 'P' }}</view><view class="info"><text class="name">{{ device.deviceName }}</text><text class="type">10 米{{ device.deviceType === 'rifle' ? '气步枪' : '气手枪' }} · {{ available(device) ? (walkIn ? '可立即训练' : '可预约') : '暂不可用' }}</text></view><view class="radio">{{ selectedId === device.deviceId ? '✓' : '' }}</view>
        </button>
        <view v-if="!devices.length" class="empty">暂时无法获取设备，请关闭后刷新重试。</view>
      </scroll-view>
      <view v-if="walkIn" class="notice"><text class="notice-title">请确认本人已到场，听从值班教练安排</text><text>确认后直接开始训练，无需提前预约或另行签到。训练限当前时段剩余时间，结束后请及时释放设备。</text><picker :range="modes" range-key="label" :value="modeIndex" :disabled="busy" @change="modeIndex = Number($event.detail.value)"><view class="mode-picker">训练模式：{{ modes[modeIndex].label }} ▾</view></picker></view>
      <view v-else class="notice"><text class="notice-title">请按时到场，做好训练准备</text><text>开始前 30 分钟开放签到，开始后 10 分钟截止。</text><text>提前 12 小时可免费取消；不足 12 小时记录晚取消，不足 1 小时记录临近取消。</text></view>
      <button class="submit" :disabled="!selectedDevice || busy" :loading="busy" @tap="$emit('confirm', selectedId, modes[modeIndex].value)">{{ busy ? '正在确认…' : !loggedIn ? '登录并继续' : walkIn ? '我已到场，立即训练' : '确认预约' }}<text v-if="!busy"> ↗</text></button>
      <text class="footnote">{{ walkIn ? '与预约合计每天最多 3 场、每周最多 10 场' : loggedIn ? '确认预约即确认本人该时段可到场，并占用设备' : '可先浏览时段，登录并完成身份登记后提交预约' }}</text>
    </view>
  </view>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { shortDate, weekday, slotTimestamp } from '../../domain/booking.js'
const props = defineProps({ selection: Object, devices: { type: Array, default: () => [] }, busy: Boolean, loggedIn: Boolean, now: Number })
const emit = defineEmits(['close', 'confirm'])
const selectedId = ref(null), modeIndex = ref(0)
const modes = [{ label: '决赛 · 24 发', value: 'final_' }, { label: '资格赛 · 60 发', value: 'qualifying' }]
const walkIn = computed(() => !!props.selection?.walkIn)
function available(device) { return walkIn.value ? device.walkInAvailable && props.now < slotTimestamp(props.selection.date, props.selection.slot.end) : device.available }
const selectedDevice = computed(() => props.devices.find(device => device.deviceId === selectedId.value && available(device)))
const duration = computed(() => props.selection ? Math.max(0, Math.ceil((slotTimestamp(props.selection.date, props.selection.slot.end) - (walkIn.value ? props.now : slotTimestamp(props.selection.date, props.selection.slot.start))) / 60000)) : 0)
watch(() => props.selection, () => { modeIndex.value = 0; selectedId.value = props.devices.find(device => available(device) && device.deviceId === props.selection?.preferredDeviceId)?.deviceId ?? props.devices.find(device => available(device))?.deviceId ?? null })
watch(() => props.devices, () => { if (!selectedDevice.value) selectedId.value = null })
function close() { if (!props.busy) emit('close') }
</script>
<style lang="scss" scoped>
.mask { position: fixed; inset: 0; z-index: 1000; background: rgba(38,48,65,.48); display: flex; align-items: flex-end; }
.sheet { width: 100%; box-sizing: border-box; max-height: 92vh; overflow-y: auto; background: #fff; border-radius: 32rpx 32rpx 0 0; padding: 16rpx 36rpx calc(32rpx + env(safe-area-inset-bottom)); color: var(--color-text); }
.handle { width: 64rpx; height: 7rpx; border-radius: 999rpx; background: var(--color-border); margin: 0 auto 32rpx; }
.heading { display: flex; justify-content: space-between; align-items: center; gap: 8rpx; }
.eyebrow { color: var(--color-primary); letter-spacing: 3rpx; font-size: 17rpx; }
.title { font-size: 33rpx; font-weight: 650; margin-top: 8rpx; }
.close { width: 72rpx; height: 72rpx; padding: 0; margin: 0; line-height: 68rpx; color: var(--color-primary); background: var(--color-bg); border-radius: 50%; flex-shrink: 0; font-size: 40rpx; }
button::after { border: 0; }
.session { margin: 32rpx 0; padding: 28rpx; background: var(--color-bg); border-radius: 10rpx; display: flex; align-items: center; justify-content: space-between; }
.date { font-size: 24rpx; color: var(--color-primary); }
.time { font-size: 40rpx; font-weight: 650; margin-top: 8rpx; font-variant-numeric: tabular-nums; }
.time text { color: var(--color-border); }
.duration { border-left: 1rpx solid var(--color-border); padding-left: 26rpx; font-size: 36rpx; display: flex; flex-direction: column; text-align: center; }
.duration text { font-size: 20rpx; color: var(--color-primary); }
.label { display: flex; align-items: center; justify-content: space-between; font-size: 27rpx; font-weight: 600; margin-bottom: 16rpx; }
.label text { font-size: 21rpx; font-weight: 400; color: var(--color-primary); }
.device-scroll { max-height: 300rpx; }
.device { display: flex; width: 100%; align-items: center; gap: 20rpx; padding: 22rpx; margin-bottom: 14rpx; border: 2rpx solid #e1e4eb; border-radius: 10rpx; background: #fff; text-align: left; line-height: 1.5; color: var(--color-text); }
.selected { border-color: var(--color-primary); background: var(--color-bg); }
.device[disabled] { opacity: .5; }
.symbol { width: 68rpx; height: 68rpx; border-radius: 14rpx; background: #e4e7ef; display: flex; align-items: center; justify-content: center; font-size: 30rpx; font-weight: 600; }
.info { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.name { font-size: 27rpx; font-weight: 600; }
.type { color: var(--color-primary); font-size: 21rpx; margin-top: 5rpx; }
.radio { width: 34rpx; height: 34rpx; border: 2rpx solid var(--color-border); border-radius: 50%; line-height: 34rpx; text-align: center; font-size: 23rpx; }
.selected .radio { background: var(--color-text); color: #fff; border-color: var(--color-text); }
.notice { display: flex; flex-direction: column; gap: 8rpx; margin: 24rpx 0; padding: 0 4rpx; color: var(--color-primary); font-size: 22rpx; line-height: 1.65; }
.mode-picker { padding: 18rpx; background: var(--color-bg); border-radius: 8rpx; }
.notice-title { color: var(--color-text); font-weight: 600; }
.submit { margin: 0; background: var(--color-primary); color: #fff; border-radius: 10rpx; font-size: 28rpx; line-height: 92rpx; }
.submit[disabled] { background: var(--color-bg); color: var(--color-primary); }
.footnote { display: block; text-align: center; margin-top: 18rpx; color: var(--color-primary); font-size: 20rpx; }
.empty { color: var(--color-primary); padding: 30rpx 0; font-size: 24rpx; }
</style>
