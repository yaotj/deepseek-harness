#!/usr/bin/env node
/**
 * 一次性盘点探针：码值泄漏 + 动作按钮风格漂移（不是常驻门禁，先量出真实违规面）。
 * 用法：cd web && node ui-consistency-audit.mjs
 */
import fs from 'fs'
import path from 'path'

const VIEWS = path.resolve('src/views')

function walk(dir, acc = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name)
    if (e.isDirectory()) walk(full, acc)
    else if (e.name.endsWith('.vue')) acc.push(full)
  }
  return acc
}

/** 状态类：必须翻成中文。 */
const STATUS_PROP = /(status|state)$/i
/** 类型/渠道/商户类：码值，属灰色地带（有现成码表就该翻）。 */
const CATEGORY_PROP = /(type|flag|channel|vendor|source|kind|mode|period)$/i
/** 标识符类：本来就是原值，翻了反而错。 */
const IDENTIFIER = /Code$|No$|Number$|Id$/i
/** 把码值翻成中文的常见写法：*Label( / format*( / dict / translate / RuoYi dict-tag / 三元映射。 */
const TRANSLATED = /(Label\(|format[A-Z]|toLabel|dict\.|translate|dict-tag|CODES?\[|\?\s*'[\u4e00-\u9fa5]|:[\s]*'[\u4e00-\u9fa5])/

const codeLeaks = []
const iconOnly = []
const buttons = new Map()

for (const file of walk(VIEWS)) {
  const rel = path.relative(VIEWS, file)
  const src = fs.readFileSync(file, 'utf-8')

  // A) 表格列：按「状态类 / 类型渠道类 / 标识符类」分桶，只看该列自己区域内有没有中文翻译
  const colStarts = [...src.matchAll(/<el-table-column\b/g)].map((m) => m.index)
  for (const m of src.matchAll(/<el-table-column\b[^>]*?>/g)) {
    const tag = m[0]
    const prop = (tag.match(/\bprop="([^"]+)"/) || [])[1]
    const label = (tag.match(/\blabel="([^"]+)"/) || [])[1] || '(无label)'
    if (!prop) continue
    const bucket = IDENTIFIER.test(prop) ? 'C'
      : STATUS_PROP.test(prop) ? 'A'
      : CATEGORY_PROP.test(prop) ? 'B' : null
    if (!bucket) continue
    const next = colStarts.find((i) => i > m.index) ?? src.length
    const region = src.slice(m.index, Math.min(next, m.index + 800))
    if (TRANSLATED.test(region)) continue
    codeLeaks.push({ bucket, rel, label, prop, line: src.slice(0, m.index).split('\n').length })
  }

  // B) 操作按钮：icon 有、文字有没有
  for (const m of src.matchAll(/<el-button\b[\s\S]{0,400}?(<\/el-button>|\/>)/g)) {
    const tag = m[0]
    const inner = m[1] === '/>' ? '' : tag.replace(/^<el-button\b[^>]*>/, '').replace(/<\/el-button>$/, '')
    // 保留 {{ ... }}：里面常是三元中文（如「解约中 : 解约」），剥掉会把有文字按钮误判成纯图标。
    const text = inner.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
    const icon = (tag.match(/\bicon="([^"]+)"/) || [])[1] || ''
    const type = (tag.match(/\btype="([^"]+)"/) || [])[1] || 'default'
    const circle = /\bcircle\b/.test(tag)
    if (!icon && !text) continue
    const key = `${text ? '有文字' : '纯图标'}|${circle ? 'circle' : '非circle'}|${type}|${icon || '(无icon)'}`
    if (!buttons.has(key)) buttons.set(key, { count: 0, files: new Set() })
    const b = buttons.get(key)
    b.count++
    b.files.add(rel)
    if (!text) iconOnly.push(`${rel}:L${src.slice(0, m.index).split('\n').length} type=${type} icon=${icon}`)
  }
}

const titles = { A: 'A 状态类（必须翻中文）', B: 'B 类型/渠道类（有码表就该翻）', C: 'C 标识符类（原值即正确，不算违规）' }
console.log('===== A/B/C. 未做中文翻译的表格列 =====')
for (const bucket of ['A', 'B', 'C']) {
  const rows = codeLeaks.filter((c) => c.bucket === bucket)
  console.log(`\n--- ${titles[bucket]}：${rows.length} 列 / ${new Set(rows.map((c) => c.rel)).size} 个页面`)
  if (bucket === 'C') continue
  const byFile = {}
  for (const c of rows) (byFile[c.rel] ||= []).push(`L${c.line} ${c.label} prop=${c.prop}`)
  for (const [f, cols] of Object.entries(byFile)) console.log(`${f}\n    ${cols.join('\n    ')}`)
}

console.log('\n===== D. 纯图标按钮（无文字）逐条落点 =====')
for (const line of iconOnly) console.log(`  ${line}`)
console.log(`共 ${iconOnly.length} 个`)

console.log('\n===== B. 按钮风格组合（文字/circle/type/icon）=====')
for (const [k, v] of [...buttons.entries()].sort((a, b) => b[1].count - a[1].count)) {
  console.log(`${String(v.count).padStart(4)}  ${k}   [${v.files.size} 页]`)
}
