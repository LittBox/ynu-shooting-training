import { ref, computed } from 'vue'
import { bookingService } from '../services/booking.js'
import { request } from '../services/request.js'
import { venueToday, dateWindow, calendarCell, SLOT_DEFINITIONS, bookingBlockReason, walkInBlockReason } from '../domain/booking.js'

// Owns asynchronous orchestration; components only render props and emit intent.
export function useBooking(service = bookingService) {
  const now = ref(Date.now())
  const today = ref(venueToday())
  const dates = computed(() => dateWindow(today.value))
  const selectedDate = ref(today.value)
  const offset = ref(0)
  const deviceType = ref('')
  const visibleDates = computed(() => dates.value.slice(offset.value, offset.value + 3))
  const slotsByDate = ref({})
  const bookings = ref([])
  const loading = ref(false)
  const error = ref('')
  const mineError = ref('')
  const loggedIn = ref(false)
  const submitting = ref(false)
  const selection = ref(null)
  const successBooking = ref(null)
  const updatedAt = ref('')
  let generation = 0
  let timer
  let clockTimer
  let active = false
  const rows = computed(() => SLOT_DEFINITIONS.map(slot => ({ ...slot, cells: visibleDates.value.map(day => calendarCell(slotsByDate.value[day.date] || [], bookings.value, day.date, slot, deviceType.value, now.value)) })))
  const selectedDevices = computed(() => selection.value ? (slotsByDate.value[selection.value.date] || []).filter(row => row.id === selection.value.slot.id && (!deviceType.value || row.deviceType === deviceType.value)) : [])
  function tick() {
    now.value = Date.now()
    const current = venueToday(now.value)
    if (current !== today.value) { today.value = current; selectedDate.value = current; offset.value = 0; selection.value = null }
  }
  async function refresh({ quiet = false } = {}) {
    tick()
    const id = ++generation
    const sessionToken = uni.getStorageSync('token')
    loggedIn.value = !!sessionToken
    if (!loggedIn.value) { bookings.value = []; mineError.value = '' }
    if (!quiet) loading.value = true
    const days = visibleDates.value.map(day => day.date)
    const results = await Promise.allSettled([
      Promise.all(days.map(async date => [date, await service.slots(date)])),
      loggedIn.value ? service.mine() : Promise.resolve([])
    ])
    if (id !== generation) return
    if (uni.getStorageSync('token') !== sessionToken) {
      bookings.value = []; loading.value = false; loggedIn.value = !!uni.getStorageSync('token'); return
    }
    loading.value = false
    const [availability, personal] = results
    if (availability.status === 'fulfilled') {
      slotsByDate.value = Object.fromEntries(availability.value)
      error.value = ''
      const clock = new Date(now.value + 8 * 3600000).toISOString().slice(11, 16)
      updatedAt.value = clock
    } else {
      // Stale availability is never actionable after a failed refresh.
      slotsByDate.value = {}
      error.value = availability.reason.message
    }
    if (personal.status === 'fulfilled') { bookings.value = personal.value; mineError.value = '' }
    else { bookings.value = []; mineError.value = personal.reason.message }
    loggedIn.value = !!uni.getStorageSync('token')
  }
  function chooseDate(date) {
    selectedDate.value = date
    const index = dates.value.findIndex(day => day.date === date)
    if (index < offset.value || index >= offset.value + 3) {
      offset.value = Math.min(Math.max(index, 0), dates.value.length - 3)
      slotsByDate.value = {}
      refresh()
    }
  }
  function movePage(direction) {
    offset.value = Math.max(0, Math.min(dates.value.length - 3, offset.value + direction * 3))
    selectedDate.value = dates.value[offset.value].date
    slotsByDate.value = {}
    refresh()
  }
  function openCell(cell, preferredDeviceId = null, walkIn = false) {
    if (loading.value || error.value || (walkIn ? cell.state !== 'ongoing' : cell.state !== 'available')) return
    selectedDate.value = cell.date
    const reason = (walkIn ? walkInBlockReason : bookingBlockReason)(bookings.value, cell.date, cell.slot.id)
    if (reason) { uni.showToast({ title: reason, icon: 'none' }); return }
    selection.value = { date: cell.date, slot: cell.slot, preferredDeviceId, walkIn }
  }
  async function submit(deviceId, mode = 'final_') {
    if (submitting.value || !selection.value) return
    if (!uni.getStorageSync('token')) { uni.navigateTo({ url: '/pages/login/login' }); return }
    submitting.value = true
    const chosen = { ...selection.value }
    try {
      const user = await request('/api/auth/me')
      if (user.profileStatus !== 'completed') { uni.navigateTo({ url: '/pages/login/login' }); return }
      // Re-read both availability and quotas immediately before every mutation.
      const [freshSlots, freshMine] = await Promise.all([service.slots(chosen.date), service.mine()])
      slotsByDate.value = { ...slotsByDate.value, [chosen.date]: freshSlots }
      bookings.value = freshMine
      const reason = (chosen.walkIn ? walkInBlockReason : bookingBlockReason)(freshMine, chosen.date, chosen.slot.id)
      if (reason) throw new Error(reason)
      if (!freshSlots.some(row => row.id === chosen.slot.id && row.deviceId === deviceId && (chosen.walkIn ? row.walkInAvailable : row.available))) throw new Error(chosen.walkIn ? '设备或教练状态已变化，请刷新后重试' : '该设备刚刚被预约，请重新选择')
      const payload = { deviceId, slotDate: chosen.date, slotId: chosen.slot.id }
      const created = chosen.walkIn ? await service.walkIn({ ...payload, mode }) : await service.create(payload)
      successBooking.value = { ...created, slotDate: chosen.date, slotStart: chosen.slot.start, slotEnd: chosen.slot.end }
      selection.value = null
      await refresh({ quiet: true })
    } catch (err) {
      uni.showToast({ title: err.message, icon: 'none', duration: 3500 })
      if (err.code === 401) uni.navigateTo({ url: '/pages/login/login' })
      await refresh({ quiet: true })
    } finally { submitting.value = false }
  }
  function start() {
    active = true
    refresh()
    clearInterval(timer)
    clearInterval(clockTimer)
    clockTimer = setInterval(tick, 1000)
    timer = setInterval(() => { if (active && !submitting.value && !loading.value) refresh({ quiet: true }) }, 30000)
  }
  function stop() { active = false; clearInterval(timer); clearInterval(clockTimer); generation++; loading.value = false }
  return { now, today, dates, selectedDate, offset, deviceType, visibleDates, bookings, loading, error, mineError, loggedIn, submitting, selection, successBooking, updatedAt, rows, selectedDevices, refresh, chooseDate, movePage, openCell, submit, start, stop }
}
