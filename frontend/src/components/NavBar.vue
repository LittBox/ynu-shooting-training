<template>
  <view class="navbar-wrapper" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="navbar" :style="{ minHeight: navBarHeight + 'px', paddingRight: rightInset + 'px' }">
      <view class="brand-mark"><view class="brand-mark__inner" /></view>
      <view class="navbar__left"><text class="navbar__title">{{ title }}</text><text v-if="subtitle" class="navbar__subtitle">{{ subtitle }}</text></view>
      <view class="navbar__action"><slot name="action" /></view>
    </view>
  </view>
</template>
<script setup>
import { ref, onMounted } from 'vue'
defineProps({ title: String, subtitle: String })
const statusBarHeight = ref(0), navBarHeight = ref(62), rightInset = ref(16)
onMounted(() => {
  const info = uni.getSystemInfoSync()
  statusBarHeight.value = info.statusBarHeight || 0
  // #ifdef MP-WEIXIN
  const menu = uni.getMenuButtonBoundingClientRect()
  if (menu?.height) { navBarHeight.value = Math.max(54, menu.height + (menu.top - statusBarHeight.value) * 2); rightInset.value = info.windowWidth - menu.left + 12 }
  // #endif
})
</script>
<style scoped>
.navbar-wrapper { background: #fff; border-bottom: 1rpx solid var(--color-border); }
.navbar { display: flex; gap: 18rpx; align-items: center; padding: 12rpx 32rpx; }
.brand-mark { width: 52rpx; height: 52rpx; border: 2rpx solid var(--color-primary); border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.brand-mark__inner { width: 28rpx; height: 28rpx; border: 2rpx solid var(--color-primary); border-radius: 50%; position: relative; }
.brand-mark__inner::after { content: ''; position: absolute; width: 8rpx; height: 8rpx; border-radius: 50%; background: var(--color-primary); top: 8rpx; left: 8rpx; }
.navbar__left { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.navbar__title { font-size: 28rpx; font-weight: 650; }
.navbar__subtitle { font-size: 18rpx; color: var(--color-text-soft); letter-spacing: 1rpx; }
.navbar__action { flex-shrink: 0; }
</style>
