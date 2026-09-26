<template>
  <view class="trend">
    <view class="trend-heading"><text>逐轮成绩走势</text><text class="unit">环 / 轮</text></view>
    <template v-if="chart">
      <scroll-view scroll-x class="chart-scroll" :aria-label="`${label}各轮成绩折线图`">
        <view class="plot" :style="{ width: chart.width + 'rpx' }">
          <view v-for="(tick, i) in chart.ticks" :key="i" class="grid" :style="{ top: tick.y + 'rpx' }"><text>{{ tick.value.toFixed(1) }}</text></view>
          <view v-for="(line, i) in chart.lines" :key="'l' + i" class="segment" :style="{ left: line.x + 'rpx', top: line.y + 'rpx', width: line.width + 'rpx', transform: `rotate(${line.angle}deg)` }" />
          <view v-for="point in chart.points" :key="point.id" class="point" :style="{ left: point.x + 'rpx', top: point.y + 'rpx' }"><text class="point-value">{{ point.totalScore.toFixed(1) }}</text></view>
          <text v-for="point in chart.points" :key="'x' + point.id" class="x-label" :style="{ left: point.x + 'rpx' }">第{{ point.round }}轮</text>
        </view>
      </scroll-view>
      <view class="stats"><view><text>平均成绩</text><text>{{ stats.mean.toFixed(1) }}<text class="unit"> 环</text></text></view><view><text>最高与最低差</text><text>{{ stats.range.toFixed(1) }}<text class="unit"> 环</text></text></view><view><text>登记轮数</text><text>{{ stats.count }}<text class="unit"> 轮</text></text></view></view>
      <text class="note">{{ stats.count === 1 ? '目前只有一轮，暂无法比较轮间波动。' : '差值越小，已登记各轮的总成绩越接近；不代表单发散布或长期水平。' }}</text>
      <text class="note">纵轴按本次成绩范围缩放 · 横轴为登记顺序{{ rounds.length > 5 ? ' · 左右滑动查看全部轮次' : '' }}</text>
    </template>
    <text v-else class="note">本赛制暂无逐轮记录。旧版仅有总成绩时，不生成轮次曲线。</text>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { roundSeries, roundStats, roundChart } from '@/domain/coach-results.js'
import { modeLabel } from '@/domain/training.js'
const props = defineProps({ attempts: { type: Array, default: () => [] }, mode: String })
const rounds = computed(() => roundSeries(props.attempts, props.mode))
const stats = computed(() => roundStats(rounds.value))
const chart = computed(() => roundChart(rounds.value, props.mode))
const label = computed(() => modeLabel(props.mode))
</script>
<style scoped>
.trend-heading { display: flex; align-items: center; justify-content: space-between; font-size: 27rpx; font-weight: 600; margin-top: 28rpx; }
.chart-scroll { width: 100%; margin: 22rpx 0 12rpx; }
.plot { height: 284rpx; position: relative; }
.grid { position: absolute; left: 64rpx; right: 40rpx; height: 1rpx; background: var(--color-border); }
.grid > text { position: absolute; right: 100%; width: 62rpx; text-align: left; transform: translateY(-50%); font-size: 18rpx; color: var(--color-text-soft); }
.segment { position: absolute; height: 3rpx; background: var(--color-primary); transform-origin: left center; }
.point { position: absolute; width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--color-primary); transform: translate(-50%, -50%); }
.point-value { position: absolute; bottom: 18rpx; left: 50%; transform: translateX(-50%); font-size: 20rpx; color: var(--color-text); white-space: nowrap; }
.x-label { position: absolute; top: 248rpx; width: 72rpx; text-align: center; white-space: nowrap; transform: translateX(-50%); font-size: 19rpx; color: var(--color-text-soft); }
.stats { display: flex; justify-content: space-between; gap: 12rpx; padding: 20rpx 0; border-top: 1rpx solid var(--color-border); }
.stats > view { display: flex; flex-direction: column; gap: 10rpx; }
.stats > view > text:first-child, .unit { font-size: 20rpx; color: var(--color-text-soft); font-weight: 400; }
.stats > view > text:last-child { font-size: 30rpx; font-variant-numeric: tabular-nums; }
.note { display: block; font-size: 21rpx; line-height: 1.7; color: var(--color-text-soft); margin-top: 8rpx; }
</style>
