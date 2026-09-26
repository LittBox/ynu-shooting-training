import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createSSRApp, h } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { House, Calendar, Trophy, User } from '@element-plus/icons-vue'
import { Resvg } from '@resvg/resvg-js'

// 原生 TabBar 接收图片路径：从 Element Plus 官方 Vue 组件生成透明 PNG。
// 修改 pages.json 的底栏颜色后，运行 npm run icons:tabbar 同步两种状态。
const sourceRoot = new URL('../src/', import.meta.url)
const { tabBar } = JSON.parse(await readFile(new URL('pages.json', sourceRoot), 'utf8'))
const icons = {
  'pages/index/index': House,
  'pages/booking/booking': Calendar,
  'pages/leaderboard/leaderboard': Trophy,
  'pages/my/my': User,
}

for (const item of tabBar.list) {
  const component = icons[item.pagePath]
  if (!component) throw new Error(`未配置图标组件：${item.pagePath}`)

  for (const [path, color] of [
    [item.iconPath, tabBar.color],
    [item.selectedIconPath, tabBar.selectedColor],
  ]) {
    const svg = (await renderToString(createSSRApp({
      render: () => h(component, { width: 81, height: 81 }),
    }))).replaceAll('currentColor', color)
    const png = new Resvg(svg, { font: { loadSystemFonts: false } }).render().asPng()
    if (png.length > 40 * 1024) throw new Error(`图标超过 40 KB：${path}`)
    const target = new URL(path, sourceRoot)
    await writeFile(target, png)
    console.log(`${component.name}: ${fileURLToPath(target)}`)
  }
}
