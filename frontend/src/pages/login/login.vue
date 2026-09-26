<template>
  <view class="page">
    <NavBar title="云大射击" subtitle="WELCOME TO THE RANGE"><template #action><button class="text-button" @tap="back">返回 ↗</button></template></NavBar>
    <view class="page-content login-content">
      <view class="login-symbol"><view class="target-mark"/></view>
      <text class="eyebrow">{{ needsProfile ? 'ONE MORE STEP' : 'YOUR FOCUS STARTS HERE' }}</text>
      <view class="page-heading">{{ needsProfile ? '认识你，然后出发。' : '你好，下一位十环选手。' }}</view>
      <text class="muted">{{ needsProfile ? '完成身份登记，即可开始预约训练。' : '登录云大射击，安排训练，记录每一次进步。' }}</text>
      <view v-if="!loadingAccount" class="role-choice">
        <text class="field-label">选择你的身份</text>
        <view class="role-options"><button class="role-option" :class="{ selected: selectedRole === 'student' }" :disabled="busy" @tap="selectedRole = 'student'; errors.inviteCode = ''; errors.role = ''">学员</button><button class="role-option" :class="{ selected: selectedRole === 'coach' }" :disabled="busy" @tap="selectedRole = 'coach'; errors.role = ''">教练员</button></view>
        <text v-if="errors.role" class="field-error">{{ errors.role }}</text>
        <template v-if="selectedRole === 'coach'">
          <text v-if="hasCoachAccess" class="form-note">当前账号已开通教练身份，可安排值班并参加训练。</text>
          <view v-else class="field" :class="{ invalid: errors.inviteCode }"><text class="field-label">教练邀请码</text><input v-model="inviteCode" :password="true" :maxlength="128" :disabled="busy" placeholder="填写负责人发放的邀请码"/><text class="form-note">首次开通需验证邀请码，已有教练权限的账号可直接登录。</text><text v-if="errors.inviteCode" class="field-error">{{ errors.inviteCode }}</text></view>
        </template>
      </view>
      <view v-if="loadingAccount" class="state-card"><text class="muted">正在确认登录状态…</text></view>
      <template v-else-if="needsProfile">
        <view class="form">
          <view v-for="field in fields" :key="field.key" class="field" :class="{ invalid: errors[field.key] }"><text class="field-label">{{ field.label }}</text><input v-model="form[field.key]" :type="field.type" :maxlength="field.maxlength" :placeholder="field.placeholder" :aria-label="field.label" :disabled="busy || (field.key === 'studentNo' && studentNoLocked)"/><text v-if="errors[field.key]" class="field-error">{{ errors[field.key] }}</text></view>
          <view class="field" :class="{ invalid: errors.gender }"><text class="field-label">性别（必填）</text><picker :range="genderOptions" range-key="label" :value="genderIndex" :disabled="busy" @change="genderIndex = Number($event.detail.value); errors.gender = ''"><view>{{ genderOptions[genderIndex].label }} ⌄</view></picker><text v-if="errors.gender" class="field-error">{{ errors.gender }}</text></view>
        </view>
        <text class="form-note">姓名用于排行榜实名展示，性别用于分组筛选；学号和手机号仅用于身份核验与预约联系。</text>
        <button class="primary-button" :loading="busy" :disabled="busy" @tap="saveProfile">{{ busy ? '正在保存…' : '保存身份信息 ↗' }}</button>
      </template>
      <template v-else>
        <view class="login-benefits"><view><text>01</text><text>随时查看训练时段</text></view><view><text>02</text><text>预约、签到，一处完成</text></view><view><text>03</text><text>保留属于你的训练记录</text></view></view>
        <!-- #ifdef MP-WEIXIN -->
        <button class="primary-button" :loading="busy" :disabled="busy || !wechatReady" @tap="wechatLogin">{{ wechatReady ? '微信登录' : '微信登录暂未开放' }}</button>
        <!-- #endif -->
        <!-- #ifdef H5 -->
        <view class="form-note">正式登录请使用微信小程序。</view>
        <!-- #endif -->
        <button v-if="isDev" class="secondary-button dev-login" :loading="busy" :disabled="busy" @tap="developmentLogin">本地开发登录</button>
        <text v-if="isDev" class="form-note">开发环境专用，使用本机独立测试身份，请填写虚构测试信息。</text>
        <button class="text-button browse" @tap="browse">先浏览可约时段 ↗</button>
      </template>
      <view v-if="error" class="error-message" role="alert">{{ error }}</view>
      <view class="login-footer">YUNNAN UNIVERSITY<br/>SHOOTING TRAINING CENTER</view>
    </view>
  </view>
</template>
<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '@/components/NavBar.vue'
import { request } from '@/services/request.js'
import { login, saveProfile as submitProfile, activateCoach, mockLoginEnabled, wechatLoginEnabled } from '@/services/auth.js'
const isDev = mockLoginEnabled
// Enabled when the deployment is configured for WeChat authentication.
const wechatReady = wechatLoginEnabled
const busy = ref(false), needsProfile = ref(false), error = ref(''), loadingAccount = ref(false)
const form = reactive({ realName: '', studentNo: '', phone: '' }), errors = reactive({})
const genderOptions = [{ label: '请选择性别', value: '' }, { label: '男', value: 'M' }, { label: '女', value: 'F' }]
const genderIndex = ref(0)
const studentNoLocked = ref(false)
const selectedRole = ref(''), accountRole = ref('student'), inviteCode = ref('')
const hasCoachAccess = computed(() => ['admin', 'superadmin'].includes(accountRole.value))
function fillProfile(account) {
  for (const field of fields) form[field.key] = account[field.key] || ''
  genderIndex.value = Math.max(0, genderOptions.findIndex(item => item.value === account.gender))
  studentNoLocked.value = !!account.studentNo
  accountRole.value = account.role
}
const fields = [
  { key: 'realName', label: '真实姓名', placeholder: '请输入你的姓名', type: 'text', maxlength: 40 },
  { key: 'studentNo', label: '学号', placeholder: '请输入你的学号', type: 'text', maxlength: 32 },
  { key: 'phone', label: '手机号码', placeholder: '请输入 11 位手机号码', type: 'number', maxlength: 11 }
]
function back() { const pages = getCurrentPages(); pages.length > 1 ? uni.navigateBack() : uni.switchTab({ url: '/pages/my/my' }) }
function browse() { uni.setStorageSync('booking-view', 'browse'); uni.switchTab({ url: '/pages/booking/booking' }) }
async function confirmSelectedRole() {
  if (selectedRole.value !== 'coach' || hasCoachAccess.value) return
  if (!inviteCode.value.trim()) throw new Error('请输入负责人发放的教练邀请码')
  const account = await activateCoach(inviteCode.value.trim())
  accountRole.value = account.role
  inviteCode.value = ''
}
function finish() {
  if (selectedRole.value === 'coach') uni.switchTab({ url: '/pages/my/my' })
  else back()
}
async function authenticate(mode) {
  if (busy.value) return
  if (!selectedRole.value) { errors.role = '请选择学员或教练员'; return }
  busy.value = true
  error.value = ''
  try {
    const result = await login({ mode })
    fillProfile(await request('/api/auth/me'))
    if (result.profileStatus === 'completed') {
      // Keep the form available if invitation verification fails after login.
      needsProfile.value = true
      await confirmSelectedRole()
      uni.showToast({ title: '登录成功' })
      finish()
    } else {
      needsProfile.value = true
    }
  } catch (err) {
    error.value = err.message
  } finally {
    busy.value = false
  }
}
function developmentLogin() { if (isDev) authenticate('mock') }
function wechatLogin() { if (wechatReady) authenticate('wechat') }
async function saveProfile() {
  if (busy.value) return
  for (const field of fields) { form[field.key] = form[field.key].trim(); errors[field.key] = form[field.key] ? '' : `请填写${field.label}` }
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) errors.phone = '请输入有效的 11 位手机号'
  errors.gender = genderOptions[genderIndex.value]?.value ? '' : '请选择性别'
  errors.role = selectedRole.value ? '' : '请选择学员或教练员'
  errors.inviteCode = selectedRole.value === 'coach' && !hasCoachAccess.value && !inviteCode.value.trim() ? '请输入教练邀请码' : ''
  if (Object.values(errors).some(Boolean)) return
  busy.value = true; error.value = ''
  try {
    fillProfile(await submitProfile({ ...form, gender: genderOptions[genderIndex.value].value }))
    await confirmSelectedRole()
    uni.showToast({ title: selectedRole.value === 'coach' ? '教练身份已开通' : '资料已保存' })
    finish()
  }
  catch (err) { error.value = err.message; if (err.code === 401) needsProfile.value = false }
  finally { busy.value = false }
}
onLoad(async (options) => {
  if (!uni.getStorageSync('token')) return
  loadingAccount.value = true
  try { const account = await request('/api/auth/me'); if (account.profileStatus === 'completed' && options?.edit !== '1') back(); else { fillProfile(account); selectedRole.value = hasCoachAccess.value ? 'coach' : 'student'; needsProfile.value = true } }
  catch (err) { error.value = err.message }
  finally { loadingAccount.value = false }
})
</script>
<style scoped>
.login-content { padding-top: 60rpx; }
.login-symbol { margin-bottom: 44rpx; }
.login-symbol .target-mark { width: 112rpx; height: 112rpx; }
.login-content .page-heading { font-size: 42rpx; margin: 18rpx 0; }
.role-choice { margin-top: 30rpx; padding: 24rpx; border: 1rpx solid var(--color-border); border-radius: 12rpx; }
.role-options { display: flex; gap: 16rpx; }
.role-option { flex: 1; margin: 0; font-size: 26rpx; background: #fff; border: 1rpx solid var(--color-border); }
.role-option.selected { color: var(--color-primary); background: #f0eefb; border-color: var(--color-primary); }
.login-benefits { padding: 38rpx 0; margin: 26rpx 0; border-top: 1rpx solid var(--color-border); border-bottom: 1rpx solid var(--color-border); }
.login-benefits view { display: flex; gap: 28rpx; font-size: 26rpx; padding: 14rpx 0; }
.login-benefits view text:first-child { color: var(--color-text-mute); font-size: 21rpx; }
.dev-login { margin-top: 22rpx; }
.browse { margin: 26rpx auto 0; }
.form { margin-top: 36rpx; }
.field { padding: 24rpx 0; border-bottom: 1rpx solid var(--color-border); }
.field-label { display: block; font-size: 23rpx; margin-bottom: 16rpx; }
.field input { height: 58rpx; font-size: 29rpx; }
.field-error { color: var(--color-danger); font-size: 22rpx; margin-top: 8rpx; display: block; }
.invalid { border-bottom-color: var(--color-danger); }
.form-note { display: block; font-size: 22rpx; color: var(--color-text-soft); margin: 24rpx 0; line-height: 1.8; }
.error-message { margin-top: 24rpx; padding: 20rpx; border-left: 3rpx solid var(--color-danger); background: #fff3f1; font-size: 24rpx; color: var(--color-danger); }
.login-footer { font-size: 17rpx; letter-spacing: 3rpx; color: var(--color-text-mute); line-height: 1.9; margin-top: 64rpx; }
</style>
