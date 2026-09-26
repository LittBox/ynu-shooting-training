<template>
  <view v-if="visible" class="bottom-sheet-mask" @tap="onClose">
    <view class="bottom-sheet" @tap.stop>
      <!-- 拖拽指示条 -->
      <view class="bottom-sheet__handle"></view>

      <!-- 标题区 -->
      <view class="bottom-sheet__header">
        <text class="bottom-sheet__title">快速预约</text>
        <text class="bottom-sheet__subtitle">{{ device.typeLabel }}｜{{ device.no }}号</text>
      </view>

      <!-- 时段选择 -->
      <view class="bottom-sheet__section">
        <view class="bottom-sheet__section-title">
          <text>今天 · {{ todayLabel }}</text>
          <text class="bottom-sheet__section-meta">剩余 {{ availableCount }} 个时段</text>
        </view>
        <view class="slot-grid">
          <view
            v-for="slot in slots"
            :key="slot.id"
            class="slot-chip"
            :class="{
              'slot-chip--selected': selectedSlotId === slot.id,
              'slot-chip--disabled': !slot.available
            }"
            @tap="selectSlot(slot)"
          >
            <text class="slot-chip__time">{{ slot.start }}</text>
            <text v-if="!slot.available" class="slot-chip__tag">满</text>
          </view>
        </view>
      </view>

      <!-- 确认按钮 -->
      <view
        class="bottom-sheet__confirm"
        :class="{ 'bottom-sheet__confirm--active': selectedSlotId }"
        @tap="onConfirm"
      >
        <text>{{ selectedSlotId ? `确认预约 ${selectedSlotTime}` : '请选择时段' }}</text>
      </view>

      <!-- 提示 -->
      <view class="bottom-sheet__tip">
        <text>提示：快速预约仅选择时间，设备已选好</text>
      </view>
    </view>
  </view>
</template>

<script>
/**
 * 快速预约底部弹窗
 * 设备已选，仅需选择时段
 */
export default {
  name: 'QuickBookSheet',
  props: {
    visible: { type: Boolean, default: false },
    device: {
      type: Object,
      default: () => ({ typeLabel: '', no: '' })
    },
    slots: {
      type: Array,
      default: () => []
      // shape: [{ id, start, end, available }]
    }
  },
  data() {
    return {
      selectedSlotId: null
    }
  },
  computed: {
    todayLabel() {
      const d = new Date()
      const mm = String(d.getMonth() + 1).padStart(2, '0')
      const dd = String(d.getDate()).padStart(2, '0')
      return `${mm}-${dd}`
    },
    availableCount() {
      return this.slots.filter(s => s.available).length
    },
    selectedSlotTime() {
      const s = this.slots.find(x => x.id === this.selectedSlotId)
      return s ? `${s.start} – ${s.end}` : ''
    }
  },
  watch: {
    visible(v) {
      if (!v) this.selectedSlotId = null
    }
  },
  methods: {
    selectSlot(slot) {
      if (!slot.available) return
      this.selectedSlotId = slot.id
    },
    onConfirm() {
      if (!this.selectedSlotId) {
        uni.showToast({ title: '请先选择时段', icon: 'none' })
        return
      }
      this.$emit('confirm', {
        deviceId: this.device.id,
        slotId: this.selectedSlotId
      })
    },
    onClose() {
      this.$emit('close')
    }
  }
}
</script>

<style lang="scss" scoped>
.bottom-sheet-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(30, 27, 75, 0.55);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  animation: fadeIn 200ms ease;
}

.bottom-sheet {
  background-color: #FFFFFF;
  border-radius: 32rpx 32rpx 0 0;
  padding: 16rpx 40rpx 60rpx;
  box-shadow: var(--shadow-bottom-sheet);
  animation: slideUp 280ms cubic-bezier(0.16, 1, 0.3, 1);

  &__handle {
    width: 64rpx;
    height: 8rpx;
    background-color: #E5E7EB;
    border-radius: 999rpx;
    margin: 0 auto 32rpx;
  }

  &__header {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 32rpx;
  }
  &__title {
    font-size: 32rpx;
    font-weight: 700;
    color: var(--color-text);
  }
  &__subtitle {
    font-size: 24rpx;
    color: var(--color-text-soft);
    margin-top: 6rpx;
  }

  &__section {
    margin-bottom: 32rpx;
  }
  &__section-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20rpx;
    font-size: 26rpx;
    font-weight: 600;
    color: var(--color-text);
  }
  &__section-meta {
    font-size: 22rpx;
    font-weight: 400;
    color: var(--color-text-soft);
  }

  &__confirm {
    height: 96rpx;
    line-height: 96rpx;
    text-align: center;
    border-radius: 999rpx;
    background-color: #E5E7EB;
    color: var(--color-text-mute);
    font-size: 30rpx;
    font-weight: 600;
    transition: all 200ms ease;
    &--active {
      background: linear-gradient(135deg, #EA580C 0%, #C2410C 100%);
      color: #FFFFFF;
      box-shadow: 0 8rpx 20rpx rgba(234, 88, 12, 0.32);
    }
  }

  &__tip {
    text-align: center;
    font-size: 22rpx;
    color: var(--color-text-mute);
    margin-top: 20rpx;
  }
}

/* 时段网格 */
.slot-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}
.slot-chip {
  height: 96rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #F7F8FB;
  border: 2rpx solid transparent;
  border-radius: 16rpx;
  transition: all 180ms ease;

  &__time {
    font-size: 28rpx;
    font-weight: 600;
    color: var(--color-text);
  }
  &__tag {
    font-size: 20rpx;
    color: var(--color-text-mute);
    margin-top: 2rpx;
  }

  &--selected {
    background-color: var(--color-primary-soft);
    border-color: var(--color-primary);
    .slot-chip__time { color: var(--color-primary); }
  }
  &--disabled {
    opacity: 0.4;
    pointer-events: none;
  }
}

/* 动画 */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
</style>
