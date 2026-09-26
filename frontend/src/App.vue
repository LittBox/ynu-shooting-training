<script>
export default {}
</script>
<style lang="scss">
page {
  --color-primary: #707f97; --color-primary-soft: #f0f1f7; --color-primary-deep: #3d4a5d;
  --color-accent: #3d4a5d; --color-accent-soft: #f0f1f7; --color-success: #24836c;
  --color-warning: #b97b20; --color-danger: #c34444;
  --color-text: #3d4a5d; --color-text-soft: #707f97; --color-text-mute: #707f97;
  --color-text-invert: #fff; --color-bg: #f0f1f7; --color-card: #fff; --color-border: #b1b7c5;
  --radius-sm: 12rpx; --radius-md: 12rpx; --radius-lg: 16rpx; --radius-pill: 999rpx;
  --shadow-card: none;
  --shadow-bottom-sheet: 0 -8rpx 32rpx rgba(38,38,61,.12);
  background: var(--color-bg); color: var(--color-text);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  font-size: 28rpx; line-height: 1.6;
}
view, button, input { box-sizing: border-box; }
button { font-family: inherit; font-weight: 500; }
/* Override the native component's late-loaded default outline. */
button::after { border: 0 !important; }
button[disabled] { opacity: .5; }
button:active, .press-feedback:active { opacity: .75; }
.page { min-height: 100vh; background: var(--color-bg); }
.page-content { padding: 32rpx 32rpx calc(48rpx + env(safe-area-inset-bottom)); max-width: 1100px; margin: 0 auto; }
.eyebrow { display: block; color: var(--color-text-soft); font-size: 18rpx; letter-spacing: 4rpx; font-weight: 600; }
.page-heading { font-size: 46rpx; line-height: 1.35; font-weight: 700; letter-spacing: -1rpx; margin: 12rpx 0; }
.muted { color: var(--color-text-soft); font-size: 24rpx; }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin: 36rpx 0 20rpx; }
.section-title { font-size: 31rpx; font-weight: 650; }
.card { background: #fff; border: 1rpx solid var(--color-border); border-radius: 12rpx; padding: 28rpx; }
.primary-button { background: var(--color-primary); color: #fff; border-radius: 10rpx; font-size: 27rpx; line-height: 90rpx; margin: 0; }
.secondary-button { background: var(--color-primary-soft); color: var(--color-primary); border-radius: 10rpx; font-size: 24rpx; line-height: 76rpx; margin: 0; }
.text-button { background: transparent; color: var(--color-primary); padding: 12rpx 0; margin: 0; font-size: 23rpx; line-height: 1.6; }
.segmented { display: flex; gap: 6rpx; padding: 7rpx; border-radius: 10rpx; background: #f0f0f4; }
.segmented button { flex: 1; padding: 14rpx 8rpx; margin: 0; font-size: 24rpx; line-height: 1.5; background: transparent; color: var(--color-text-soft); border-radius: 6rpx; }
.segmented button.active { color: var(--color-primary); background: #fff; box-shadow: none; font-weight: 600; }
.state-card { padding: 60rpx 28rpx; border-radius: 12rpx; background: #fff; border: 1rpx solid var(--color-border); display: flex; align-items: center; flex-direction: column; text-align: center; gap: 14rpx; }
.state-title { font-size: 29rpx; font-weight: 600; }
.state-card .secondary-button { margin-top: 14rpx; padding: 0 32rpx; }
.target-mark { width: 92rpx; height: 92rpx; border: 2rpx solid #d8d4f5; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-bottom: 8rpx; }
.target-mark::before { content: ''; width: 54rpx; height: 54rpx; border: 2rpx solid #b3aaeb; border-radius: 50%; }
.target-mark::after { content: ''; position: absolute; width: 14rpx; height: 14rpx; background: var(--color-primary); border-radius: 50%; }
.rule-note { margin-top: 24rpx; padding: 24rpx; border-radius: 10rpx; background: #efedf7; color: #77708f; font-size: 22rpx; line-height: 1.8; }
.flex-between { display: flex; align-items: center; justify-content: space-between; }
@media (prefers-reduced-motion: reduce) { button, .press-feedback { transition: none !important; animation: none !important; } }
/* #ifdef H5 */
@media (min-width: 700px) { uni-page-body { max-width: 620px; margin: 0 auto; box-shadow: 0 0 80px #26263d08; } uni-tabbar .uni-tabbar { max-width: 620px; margin: auto; } }
/* #endif */
</style>
