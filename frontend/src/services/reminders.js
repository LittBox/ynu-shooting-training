import { request } from './request.js'
export const reminderService = {
  status: (kind, id) => request(`/api/reminders/${kind}/${id}`),
  subscribe: (kind, id, templateId) => request(`/api/reminders/${kind}/${id}`, { method: 'POST', data: { templateId, accepted: true } }),
  cancel: (kind, id) => request(`/api/reminders/${kind}/${id}`, { method: 'DELETE' })
}

// Invoke directly inside a tap handler, before any network request or await.
// getSetting's remembered choice does not create a one-time subscription quota.
export function requestReminderSubscription(native, templateId) {
  return new Promise((resolve, reject) => {
    if (!native?.requestSubscribeMessage) return reject(new Error('请在微信小程序中订阅提醒'))
    native.requestSubscribeMessage({
      tmplIds: [templateId],
      success(result) { resolve(result[templateId] === 'accept' || result[templateId] === 'acceptWithAudio') },
      fail() { reject(new Error('微信订阅未完成，请检查小程序消息设置后重试')) }
    })
  })
}
