#!/usr/bin/env node
/**
 * 综管台「查询区间 chip + 空态引导」接线不变式自检（与 syntax-check.mjs 平级，各管一件事）。
 *
 * syntax-check.mjs 只证明「能编译」；本脚本证明「22 个默认只查今天的页面确实被接进了新组件，
 * 且接法不会让页面在接口失败时永久转圈」。这两类漂移都不会有编译错误，只能靠判据盯。
 *
 *   node query-ui-check.mjs            全量检查
 *   判据来源：docs/design/综管台UI-UE优化方案-2026-11-04.md §二·B / §五·1 / §五·4
 */
import fs from 'fs'
import path from 'path'

const SRC = path.resolve('src')
const VIEWS = path.join(SRC, 'views')

/**
 * 实测「不带时间范围就 code=400」的页面（2026-11-04 现查 NodePort），
 * 这些页面必须 :allow-all="false"，否则「全部」chip 会把用户直接送进一条 400。
 * 换接口口径时 MUST 重跑 curl 再改这张表，NEVER 凭印象增删。
 * 路径相对 src/views —— 与 rel() 去掉 views/ 前缀后的形式一致，写错前缀会让本条判据永不触发。
 */
const MUST_FORBID_ALL = [
  'trans/face-pay/order/index.vue',
  'trans/overtime-refund/index.vue',
  'trans/stats/offline-code/index.vue',
  'trans/user/debit-detail/index.vue',
  'trans/user/transaction-detail/index.vue'
]

/** 区间变量放在查询对象内部时，数组值不得被计成筛选条件（1.2.44.17 的教训）。 */
const EMPTY_STATE = path.join(SRC, 'components/EmptyState/index.vue')

function walk(dir, acc = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, acc)
    else if (entry.name.endsWith('.vue')) acc.push(full)
  }
  return acc
}

const files = walk(VIEWS)
const rel = (f) => path.relative(SRC, f)
const relViews = (f) => path.relative(VIEWS, f)
const errors = []
const fail = (file, msg) => errors.push(`${rel(file)}: ${msg}`)

/**
 * 按名字取函数体（花括号配对，跳过字符串与注释）。
 *
 * 不用「正则到行首 }」的写法：本仓库既有 3 空格缩进、也有把函数压成一行的页面
 * （para/risk/control-log、trans/card-pool），那种写法在压成一行的文件上会跨函数吞并，
 * 把无关函数的 .finally 算进来、或把别人的判据报成缺陷。
 *
 * @returns {string|null} 函数体（含首尾花括号）；找不到返回 null
 */
function extractFunction(source, name) {
  const start = source.search(new RegExp(`function\\s+${name}\\s*\\(`))
  if (start < 0) return null
  const open = source.indexOf('{', start)
  if (open < 0) return null
  let depth = 0
  let quote = null
  for (let i = open; i < source.length; i++) {
    const ch = source[i]
    if (quote) {
      if (ch === '\\') i++
      else if (ch === quote) quote = null
      continue
    }
    if (ch === '"' || ch === "'" || ch === '`') { quote = ch; continue }
    if (ch === '/' && source[i + 1] === '/') { i = source.indexOf('\n', i); if (i < 0) return null; continue }
    if (ch === '/' && source[i + 1] === '*') { i = source.indexOf('*/', i) + 1; continue }
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) return source.slice(open, i + 1)
    }
  }
  return null
}

let todayPages = 0
let chipPages = 0
let emptyPages = 0

for (const file of files) {
  const c = fs.readFileSync(file, 'utf-8')
  const usesToday = /defaultTodayRange\(/.test(c)
  const hasChip = /<query-time-range/.test(c)
  const hasEmpty = /<empty-state/.test(c)
  if (usesToday) todayPages++
  if (hasChip) chipPages++
  if (hasEmpty) emptyPages++

  // 1) 默认只查今天的页面必须被接进 chip 组件（否则 P1 陷阱仍在）
  if (usesToday && !hasChip) fail(file, '用了 defaultTodayRange 却没有 query-time-range')

  // 2) chip 的 v-model 目标必须存在；绑的是 obj.prop 时，prop 也得真在那个对象里
  const bound = (c.match(/<query-time-range[^>]*v-model="([\w.]+)"/) || [])[1]
  if (hasChip) {
    if (!bound) fail(file, 'query-time-range 缺 v-model')
    else {
      const [root, prop] = bound.split('.')
      if (!new RegExp(`(const|let|var)\\s+${root}\\b|reactive\\s*\\(\\s*\\{[\\s\\S]{0,600}?\\b${root}\\s*=`).test(c)
        && !new RegExp(`\\b${root}\\s*=\\s*(reactive|ref)\\(`).test(c)) {
        fail(file, `query-time-range 绑到不存在的变量 ${bound}`)
      } else if (prop && !new RegExp(`\\b${prop}\\s*:`).test(c)) {
        // 只验到根对象会漏掉「写错属性名」——区间落到一个不存在的键上，chip 改了也没人读它
        fail(file, `query-time-range 绑到不存在的属性 ${bound}（对象里没有 ${prop} 这个键）`)
      }
    }
    if (!/value-format|:value-format/.test(c)) fail(file, 'query-time-range 未指定 value-format')
  }

  // 3) 空态引用的处理函数必须定义在同文件
  for (const m of c.matchAll(/(?:@relax|@clear|@retry|@change)="([A-Za-z_$][\w$]*)"/g)) {
    const handler = m[1]
    if (!new RegExp(`(function\\s+${handler}\\b|const\\s+${handler}\\s*=|let\\s+${handler}\\s*=)`).test(c)) {
      fail(file, `empty-state 指向不存在的处理函数 ${handler}`)
    }
  }

  // 4) 接了空态 = 承诺「失败会说清楚」。
  //    主列表函数**用接线本身定位**（@retry 指向谁就是谁）：整文件级判据会被别的函数里的
  //    .finally 蒙过去，而按「所有 function」扫又会把 RuoYi 弹窗加载函数（复用同一个 loading ref）
  //    误报成缺陷、并在单行压缩写法上跨函数吞并 —— 两种错都实测踩过。
  if (hasEmpty) {
    const errorBindings = [...c.matchAll(/:error="([A-Za-z_$][\w$]*)"/g)].map((m) => m[1])
    if (!errorBindings.length) fail(file, '有 empty-state 但没有 :error 绑定（失败分支永远不亮）')
    for (const ident of errorBindings) {
      if (!new RegExp(`${ident}\\.value\\s*=`).test(c)) fail(file, `:error="${ident}" 从未被赋值`)
      else if (!new RegExp(`\\.catch\\([\\s\\S]{0,400}?${ident}\\.value\\s*=`).test(c)) {
        fail(file, `${ident} 只在成功路径出现，没在任何 .catch 里赋值`)
      }
    }
    for (const m of c.matchAll(/@retry="([A-Za-z_$][\w$]*)"/g)) {
      const body = extractFunction(c, m[1])
      if (body === null) { fail(file, `empty-state 的 @retry="${m[1]}" 找不到函数体`); continue }
      if (!/\.catch\(/.test(body)) fail(file, `主列表函数 ${m[1]}() 没有 .catch —— 失败原因进不了空态`)
      if (!/\.finally\(/.test(body)) fail(file, `主列表函数 ${m[1]}() 没有 .finally —— 接口失败时表格永久转圈`)
    }
  }

  // 5) 400 接口页必须关掉「全部」（路径按 src/views 相对，见 MUST_FORBID_ALL 注释）
  if (MUST_FORBID_ALL.includes(relViews(file)) && hasChip && !/:allow-all="false"/.test(c)) {
    fail(file, '该页接口缺时间范围会返回 400，必须 :allow-all="false"')
  }

  // 6) 不许留第二个绑同一变量的日期选择器
  if (bound && new RegExp(`el-date-picker[^>]*v-model="${bound.replace(/\./g, '\\.')}"`).test(c)) {
    fail(file, `el-date-picker 与 query-time-range 绑同一个变量 ${bound}`)
  }

  // 7) 等宽数字用全局 .tabular，不许各页再复制一份
  if (/\.tabular\s*\{[^}]*font-variant-numeric/.test(c)) {
    fail(file, '复制了全局 .tabular（真源在 assets/styles/points.scss）')
  }
}

// 8) EmptyState 自身：数组值不得计入筛选条件，否则「放宽区间」引导永远不出现
if (!fs.existsSync(EMPTY_STATE)) {
  errors.push('components/EmptyState/index.vue 不存在 —— 空态引导已整体消失，本次读数无效')
} else {
  const emptyComponent = fs.readFileSync(EMPTY_STATE, 'utf-8')
  if (/Array\.isArray\(value\)\s*\)\s*return\s+!isEmptyRange/.test(emptyComponent)) {
    errors.push('components/EmptyState/index.vue: 把时间区间计成了筛选条件 —— 页面一进就报「无匹配结果」，放宽引导永远不出现')
  }
}

console.log(`扫描 ${files.length} 个页面 / defaultTodayRange ${todayPages} 页 / query-time-range ${chipPages} 页 / empty-state ${emptyPages} 页`)

/**
 * 地板判据：没有可判对象的 PASS 不算 PASS。
 * 一次脚手架事故（src 被挪走）让本脚本在空目录上输出过 ERROR 0 —— 那是假绿，比红更危险。
 */
if (files.length === 0) errors.push('扫描到 0 个 .vue —— 目录不对或 src 被挪走，本次读数无效')
if (todayPages === 0) errors.push('没有任何页面用 defaultTodayRange —— 判据对象消失，本次读数无效')
for (const f of MUST_FORBID_ALL) {
  if (!fs.existsSync(path.join(VIEWS, f))) errors.push(`判据依赖的页面不存在：${f}（接口口径可能已改，MUST 重跑 curl 再更新这张表）`)
}

if (errors.length) {
  errors.forEach((e) => console.error(`[WIRING] ${e}`))
  console.log(`ERROR ${errors.length}`)
  process.exit(1)
}
console.log('ERROR 0')
