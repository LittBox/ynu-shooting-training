import { ref } from 'vue'
import { bookingService } from '../services/booking.js'

// Isolated read model: inspecting a full or past slot never creates a booking.
export function useSlotDetails(service = bookingService) {
  const inspected = ref(null)
  const detail = ref(null)
  const loading = ref(false)
  const error = ref('')
  let generation = 0
  let timer
  let viewerToken

  function close() {
    generation++
    clearInterval(timer)
    inspected.value = null
    detail.value = null
    loading.value = false
    error.value = ''
  }
  async function refresh() {
    if (!inspected.value) return
    const id = ++generation
    const token = uni.getStorageSync('token')
    if (viewerToken !== token) detail.value = null
    viewerToken = token
    loading.value = true
    error.value = ''
    try {
      const result = await service.details(inspected.value.date, inspected.value.slot.id)
      if (id !== generation) return
      if (uni.getStorageSync('token') !== token) { close(); return }
      detail.value = result
    } catch (err) {
      if (id !== generation) return
      detail.value = null
      error.value = err.message || '暂时无法获取设备安排'
    } finally { if (id === generation) loading.value = false }
  }
  function open(cell, deviceType = '') {
    close()
    inspected.value = { date: cell.date, slot: cell.slot, deviceType }
    const pending = refresh()
    timer = setInterval(() => { if (!loading.value) refresh() }, 30000)
    return pending
  }
  return { inspected, detail, loading, error, open, close, refresh }
}
