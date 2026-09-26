import { request, ApiError } from './request.js'

// Development identity requires explicit opt-in on both client and server.
export const mockLoginEnabled = import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK_LOGIN === 'true'
export const wechatLoginEnabled = import.meta.env.VITE_AUTH_MODE === 'wechat' || import.meta.env.VITE_WECHAT_AUTH_READY === 'true'
export const loginEnabled = mockLoginEnabled || wechatLoginEnabled

export async function login({ mode = mockLoginEnabled ? 'mock' : 'wechat' } = {}) {
  let code
  if (mode === 'mock' && mockLoginEnabled) {
    code = uni.getStorageSync('developmentIdentity')
    if (!code) {
      code = `dev-${Date.now()}-${Math.random().toString(36).slice(2)}`
      uni.setStorageSync('developmentIdentity', code)
    }
  } else if (mode === 'wechat' && wechatLoginEnabled) {
    code = await new Promise((resolve, reject) => {
      uni.login({ provider: 'weixin', success: result => result.code ? resolve(result.code) : reject(new ApiError('未能获取微信登录凭证')), fail: () => reject(new ApiError('微信登录未完成，请在微信小程序中重试')) })
    })
  } else throw new ApiError('登录服务尚未开通，请联系训练中心')
  const result = await request('/api/auth/login', { method: 'POST', auth: false, data: { code, mode, nickname: '射击学员' } })
  if (!result?.token) throw new ApiError('登录响应异常，请重试')
  uni.setStorageSync('token', result.token)
  return result
}

export const saveProfile = data => request('/api/auth/profile', { method: 'POST', data })
export const activateCoach = inviteCode => request('/api/auth/coach-activation', { method: 'POST', data: { inviteCode } })
