import test from 'node:test'
import assert from 'node:assert/strict'
import { groupError, groupSpec, sumGroups, finishMessage, resultModes, isAttemptBest, finishConfirmation } from '../src/domain/training.js'

test('qualification sums six ten-shot groups; final sums ten, ten and four', () => {
  assert.deepEqual(groupSpec('qualifying'), [10,10,10,10,10,10])
  assert.deepEqual(groupSpec('final_'), [10,10,4])
  assert.equal(groupError(['90','91','92','93','94','95'], 'qualifying'), '')
  assert.equal(sumGroups(['90','91','92','93','94','95']), 555)
  assert.equal(groupError(['94.1','95.2','38.3'], 'final_'), '')
  assert.equal(sumGroups(['94.1','95.2','38.3']), 227.6)
})
test('incomplete, negative and out-of-range groups never become a valid round', () => {
  assert.ok(groupError(['90','90',''], 'final_'))
  assert.ok(groupError(['90','90','41'], 'final_'))
  assert.ok(groupError(['101','90','36'], 'final_'))
  assert.ok(groupError(['90.01','90','36'], 'final_'))
  assert.ok(groupError(['-1','90','36'], 'final_'))
  assert.ok(groupError(['90','90','36'], 'qualifying'))
  assert.equal(groupError(['0','0','0'], 'final_'), '')
})
test('finish feedback distinguishes unscored sessions from real zero and personal bests', () => {
  assert.match(finishMessage({ finalScore:null }), /仅保留训练时长/)
  assert.match(finishMessage({ finalScore:0 }), /0 环/)
  assert.match(finishMessage({ finalScore:227,personalBest:true }), /历史最佳/)
})

test('mixed rounds compare only within each mode and finish reports both', () => {
  const detail = { session:{mode:'final_'}, attempts:[{mode:'qualifying',totalScore:570},{mode:'final_',totalScore:228}], modeResults:[{mode:'final_',bestTotal:228,finalTotal:228},{mode:'qualifying',bestTotal:570,finalTotal:570}] }
  assert.equal(resultModes(detail).length, 2)
  assert.equal(isAttemptBest(detail.attempts[0],detail),true)
  assert.equal(isAttemptBest(detail.attempts[1],detail),true)
  assert.equal(isAttemptBest({mode:'qualifying',totalScore:228},detail),false)
  assert.match(finishConfirmation(detail),/决赛.*228.*资格赛.*570/)
  const feedback=finishMessage({finalScores:[{mode:'final_',finalScore:228,personalBest:true},{mode:'qualifying',finalScore:570,personalBest:false}]})
  assert.match(feedback,/决赛.*228.*个人历史最佳.*资格赛.*570/)
  assert.equal((feedback.match(/个人历史最佳/g)||[]).length,1)
})
