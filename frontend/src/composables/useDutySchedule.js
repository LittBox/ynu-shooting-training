import { computed, ref } from 'vue'
import { dutyService } from '../services/duty.js'
import { dateWindow, venueToday, SLOT_DEFINITIONS } from '../domain/booking.js'
import { dutyCalendarCell, isCoach, validDutyDate } from '../domain/duty.js'

export function useDutySchedule(getUser, onUnauthorized = () => {}, service = dutyService) {
  const now = ref(Date.now()), today = ref(venueToday()), rangeStart = ref(today.value), date = ref(today.value), offset = ref(0)
  const states = ref({}), selected = ref(null), loading = ref(false), busy = ref(false), error = ref(''), actionError = ref('')
  const dates = computed(() => dateWindow(rangeStart.value))
  const visibleDates = computed(() => dates.value.slice(offset.value, offset.value + 3))
  const state = computed(() => states.value[date.value] || null)
  const rows = computed(() => SLOT_DEFINITIONS.map(slot => ({ ...slot, cells: visibleDates.value.map(day => ({
    ...dutyCalendarCell(states.value[day.date]?.schedules || [], getUser()?.id, day.date, slot, now.value),
    focused: selected.value?.date === day.date && selected.value?.slot.id === slot.id
  })) })))
  const detail = computed(() => selected.value && states.value[selected.value.date]
    ? dutyCalendarCell(states.value[selected.value.date].schedules, getUser()?.id, selected.value.date, selected.value.slot, now.value) : null)
  const myCount = computed(() => Object.values(states.value).flatMap(s => s.schedules).filter(s => s.adminId === getUser()?.id).length)
  let generation = 0, lifecycle = 0, active = false, timer, clockTimer
  function tick() {
    now.value = Date.now()
    const next = venueToday(now.value)
    if (next !== today.value) { today.value = next; rangeStart.value = next; date.value = next; offset.value = 0; selected.value = null; states.value = {}; refresh() }
  }
  async function refresh({ quiet = false } = {}) {
    if (!active || !isCoach(getUser())) return
    const id = ++generation, token = uni.getStorageSync('token'), userId = getUser().id
    if (!token) { stop(); onUnauthorized(); return }
    if (!quiet) loading.value = true
    try {
      const data = await Promise.all(visibleDates.value.map(async day => [day.date, await service.status(day.date)]))
      if (id !== generation) return
      if (token !== uni.getStorageSync('token') || getUser()?.id !== userId) { stop(); onUnauthorized(); return }
      states.value = Object.fromEntries(data); error.value = ''
    } catch (err) {
      if (id !== generation) return
      states.value = {}; error.value = err.message || '暂时无法加载值班安排'
      if (err.code === 401 || err.code === 403) { stop(); onUnauthorized() }
    } finally { if (id === generation) loading.value = false }
  }
  function chooseDate(value) {
    if (busy.value || !validDutyDate(value)) return
    date.value = value; selected.value = null; actionError.value = ''
    let index = dates.value.findIndex(d => d.date === value)
    if (index < 0) { rangeStart.value = value; index = 0 }
    const next = index < offset.value || index >= offset.value + 3 ? Math.min(index, dates.value.length - 3) : offset.value
    if (next !== offset.value || !states.value[value]) { offset.value = next; states.value = {}; refresh() }
  }
  function goToday() {
    if (busy.value) return
    rangeStart.value = today.value; offset.value = 0; date.value = today.value
    selected.value = null; actionError.value = ''; states.value = {}; return refresh()
  }
  function movePage(direction) {
    if (busy.value || loading.value) return
    offset.value = Math.max(0, Math.min(dates.value.length - 3, offset.value + direction * 3))
    date.value = dates.value[offset.value].date; selected.value = null; actionError.value = ''; states.value = {}; refresh()
  }
  function inspect(cell) {
    if (busy.value || loading.value || error.value || !states.value[cell.date]) return
    date.value = cell.date; selected.value = { date: cell.date, slot: cell.slot }; actionError.value = ''
  }
  async function perform(operation, message) {
    if (!active || busy.value || loading.value || !isCoach(getUser())) return
    const token = uni.getStorageSync('token'), scope = lifecycle
    if (!token) { stop(); onUnauthorized(); return }
    busy.value = true; actionError.value = ''
    let failure
    try { await operation() }
    catch (err) { failure = err }
    finally {
      if (scope === lifecycle && active && token === uni.getStorageSync('token')) {
        await refresh()
        if (scope === lifecycle && active) {
          actionError.value = failure?.message || ''
          if (!failure) uni.showToast({ title: message, icon: 'success' })
          if (failure?.code === 401 || failure?.code === 403) { stop(); onUnauthorized() }
        }
      }
      if (scope === lifecycle && active && token !== uni.getStorageSync('token')) { stop(); onUnauthorized() }
      busy.value = false
    }
  }
  function start(initialDate) {
    active = true; lifecycle++; clearInterval(timer); clearInterval(clockTimer); now.value = Date.now(); today.value = venueToday(now.value)
    if (validDutyDate(initialDate)) {
      date.value = initialDate
      rangeStart.value = initialDate < today.value || initialDate > dateWindow(today.value).at(-1).date ? initialDate : today.value
      offset.value = Math.min(Math.max(0, dates.value.findIndex(d => d.date === initialDate)), dates.value.length - 3)
    }
    const pending = refresh()
    clockTimer = setInterval(tick, 1000)
    timer = setInterval(() => { if (!loading.value && !busy.value) refresh({ quiet: true }) }, 30000)
    return pending
  }
  function stop() { active = false; lifecycle++; generation++; clearInterval(timer); clearInterval(clockTimer); states.value = {}; selected.value = null; loading.value = false; error.value = ''; actionError.value = '' }
  return { now, today, date, dates, offset, visibleDates, state, rows, detail, myCount, loading, busy, error, actionError, refresh, chooseDate, goToday, movePage, inspect, perform, start, stop }
}
