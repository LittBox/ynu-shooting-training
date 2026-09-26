export const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
export class ApiError extends Error {
  constructor(message, code = 0) { super(message); this.code = code }
}
export function request(path, { method = 'GET', data, auth = true } = {}) {
  const token = uni.getStorageSync('token')
  if (auth && !token) return Promise.reject(new ApiError('请先登录后继续', 401))
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${path}`, method, data, timeout: 12000,
      header: { 'Content-Type': 'application/json', ...(auth && token ? { Authorization: `Bearer ${token}` } : {}) },
      success(response) {
        const body = response.data
        if (response.statusCode === 401 || body?.code === 401) {
          // A late response from an old session must not clear a new login.
          if (auth && token && uni.getStorageSync('token') === token) uni.removeStorageSync('token')
          reject(new ApiError('登录已过期，请重新登录', 401))
        } else if (response.statusCode >= 200 && response.statusCode < 300 && body?.code === 0) {
          resolve(body.data)
        } else {
          reject(new ApiError(response.statusCode >= 500 ? '服务暂时不可用，请稍后重试' : body?.message || '请求失败，请稍后重试', body?.code || response.statusCode))
        }
      },
      fail() { reject(new ApiError('暂时无法连接训练中心，请检查网络后重试')) }
    })
  })
}
