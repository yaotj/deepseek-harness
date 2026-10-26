// 前端全量语法自检（验证四件套①）：用 @babel/parser 解析 web/src 全部 .vue 的 <script> 块与全部 .js。
// MUST 在 web/ 目录内跑（@babel/parser 不在 /tmp 模块解析路径上）。
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, extname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from '@babel/parser'

const root = join(fileURLToPath(new URL('../src', import.meta.url)))

function walk(dir, files = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    const stat = statSync(full)
    if (stat.isDirectory()) walk(full, files)
    else if (['.vue', '.js'].includes(extname(name))) files.push(full)
  }
  return files
}

const files = walk(root)
let scriptBlocks = 0
const errors = []
for (const file of files) {
  const code = readFileSync(file, 'utf8')
  const rel = file.slice(root.length + 1)
  if (file.endsWith('.js')) {
    try {
      parse(code, { sourceType: 'module', plugins: ['jsx'] })
    } catch (e) {
      errors.push(`${rel}: ${e.message}`)
    }
  } else {
    const blocks = [...code.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)]
    for (const [i, m] of blocks.entries()) {
      scriptBlocks++
      try {
        parse(m[1], { sourceType: 'module', plugins: ['jsx'] })
      } catch (e) {
        errors.push(`${rel} <script>#${i + 1}: ${e.message}`)
      }
    }
  }
}
console.log(`文件数: ${files.length}, script块数: ${scriptBlocks}, ERROR数: ${errors.length}`)
for (const e of errors) console.log('ERROR ' + e)
process.exit(errors.length ? 1 : 0)
