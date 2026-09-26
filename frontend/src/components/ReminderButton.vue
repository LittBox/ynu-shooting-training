<template>
  <view class="reminder" @tap.stop>
    <text v-if="loading && !state" class="reminder-hint">正在查看提醒状态…</text>
    <template v-if="state">
      <text class="reminder-hint">{{ state.message }}</text>
      <text v-if="state.canSubscribe" class="reminder-hint">仅提醒本次{{ kind === 'DUTY' ? '值班' : '训练' }}，请在微信弹窗中选择允许。</text>
      <view class="reminder-actions">
        <button v-if="state.canSubscribe || acceptedPending" class="reminder-button" :disabled="busy || loading || !active" :loading="busy" @tap.stop="subscribe">{{ acceptedPending ? '保存已同意的提醒' : '订阅本次微信提醒' }}</button>
        <button v-if="state.status === 'PENDING' && !state.canSubscribe" class="reminder-link" :disabled="busy || loading || !active" @tap.stop="cancel">取消本次提醒</button>
      </view>
    </template>
    <text v-if="error" class="reminder-error">{{ error }}</text>
    <button v-if="error && !acceptedPending" class="reminder-link" :disabled="busy || loading || !active" @tap.stop="load">刷新提醒状态 ↻</button>
  </view>
</template>
<script setup>
import { ref, watch, onUnmounted } from 'vue'
import { reminderService, requestReminderSubscription } from '../services/reminders.js'
const props = defineProps({ kind: { type: String, required: true }, targetId: { type: Number, required: true }, active: { type: Boolean, default: true }, refreshKey: { default: null } })
const state = ref(null), loading = ref(false), busy = ref(false), error = ref(''), acceptedPending = ref(null)
let generation = 0, disposed = false
async function load() {
  if (!props.active || busy.value || disposed) return
  const id = ++generation, token = uni.getStorageSync('token')
  loading.value = true; error.value = ''
  try {
    const result = await reminderService.status(props.kind, props.targetId)
    if (id !== generation || token !== uni.getStorageSync('token') || disposed) return
    state.value = result
    if (!result.canSubscribe) acceptedPending.value = null
  } catch (e) { if (id === generation && !disposed) { state.value = null; error.value = e.message } }
  finally { if (id === generation && !disposed) loading.value = false }
}
async function subscribe() {
  if (busy.value || loading.value || !props.active || (!state.value?.canSubscribe && !acceptedPending.value)) return
  const token = uni.getStorageSync('token'), kind = props.kind, targetId = props.targetId
  const templateId = acceptedPending.value?.templateId || state.value.templateId
  busy.value = true; error.value = ''
  try {
    if (!acceptedPending.value) {
      const accepted = await requestReminderSubscription(typeof wx === 'undefined' ? null : wx, templateId)
      if (token !== uni.getStorageSync('token') || disposed) return
      if (!accepted) { error.value = '未订阅提醒，预约或值班安排不受影响'; return }
      acceptedPending.value = { templateId, token }
    }
    if (acceptedPending.value.token !== token) { acceptedPending.value = null; throw new Error('登录已变化，请重新订阅') }
    const result = await reminderService.subscribe(kind, targetId, templateId)
    if (token !== uni.getStorageSync('token') || disposed) return
    state.value = result; acceptedPending.value = null
    uni.showToast({ title: '本次提醒已安排', icon: 'success' })
  } catch (e) {
    if (!disposed && token === uni.getStorageSync('token')) { error.value = e.message; if (e.code >= 400 && e.code < 500) acceptedPending.value = null }
  } finally { busy.value = false }
}
async function cancel() {
  if (busy.value || loading.value || !props.active) return
  busy.value = true; error.value = ''; const token = uni.getStorageSync('token')
  try {
    const result = await reminderService.cancel(props.kind, props.targetId)
    if (!disposed && token === uni.getStorageSync('token')) { state.value = result; acceptedPending.value = null }
  } catch (e) { if (!disposed && token === uni.getStorageSync('token')) error.value = e.message }
  finally { busy.value = false }
}
watch(() => [props.kind, props.targetId], () => { generation++; state.value = null; acceptedPending.value = null; load() }, { immediate: true })
watch(() => [props.active, props.refreshKey], () => { if (props.active) load(); else { generation++; loading.value = false } })
onUnmounted(() => { disposed = true; generation++ })
</script>
<style scoped>
.reminder { margin-top: 20rpx; padding-top: 16rpx; border-top: 1rpx dashed var(--color-border); text-align: left; }
.reminder-hint, .reminder-error { display: block; font-size: 21rpx; line-height: 1.7; color: var(--color-text-soft); }
.reminder-error { color: var(--color-danger); margin-top: 8rpx; }
.reminder-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 12rpx; }
.reminder-button, .reminder-link { margin: 12rpx 0 0; padding: 10rpx 18rpx; font-size: 22rpx; line-height: 1.8; border-radius: 8rpx; color: var(--color-primary-deep); background: var(--color-bg); }
.reminder-link { background: transparent; font-size: 21rpx; }
.reminder-button::after, .reminder-link::after { border: 0; }
</style>
