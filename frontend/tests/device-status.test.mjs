import test from 'node:test'
import assert from 'node:assert/strict'
import { currentDeviceStatus } from '../src/domain/device-status.js'
import { normalizeSlots } from '../src/domain/booking.js'
const date = '2026-09-26', now = Date.parse(`${date}T09:00:00+08:00`)
const row = { id:'S1', deviceId:1, deviceType:'pistol', deviceStatus:'IDLE', start:'08:30', end:'10:10', coachPresent:true, bookedCount:0 }
const state = (rows, time=now) => currentDeviceStatus(rows, 'pistol', date, time)
test('physical usage remains visible regardless of future available slots or current slot ending', () => {
  assert.equal(state([{...row, deviceStatus:'IN_USE', available:3}]).label, '使用中')
  assert.equal(state([{...row, deviceStatus:'IN_USE'}],Date.parse(`${date}T10:15:00+08:00`)).label, '使用中')
  assert.equal(state([row]).label, '空闲')
})
test('current reservations are distinguished from future bookings and overdue walk-in releases', () => {
  assert.equal(state(normalizeSlots([{...row, bookedCount:1}])).label,'已预约待训练')
  assert.equal(state(normalizeSlots([{...row, bookedCount:1,walkInAvailable:true}])).label,'空闲')
  assert.equal(state([row,{...row,id:'S2',start:'10:30',end:'12:10',bookedCount:1}]).label,'空闲')
})
test('maintenance, unavailable and off-slot states are explicit', () => {
  assert.equal(state([{...row,deviceStatus:'MAINTENANCE'}]).label,'维护中')
  assert.equal(state([{...row,deviceStatus:'DISABLED'}]).label,'已停用')
  assert.equal(state([]).tone,'unknown')
  assert.equal(state([{...row,coachPresent:false}]).hint,'待教练到岗')
  assert.equal(state([row],Date.parse(`${date}T10:15:00+08:00`)).hint,'当前非训练时段')
})
test('multiple devices are counted once rather than once per slot', () => {
  assert.equal(state([row,{...row,id:'S2',start:'10:30',end:'12:10'},{...row,deviceId:2,deviceStatus:'IN_USE'}]).label,'1 台空闲 · 1 台使用中')
})
