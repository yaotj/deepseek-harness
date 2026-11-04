import fs from 'fs'
import path from 'path'
import { parse as parseScript } from '@babel/parser'
import { parse as parseSfc, compileTemplate } from '@vue/compiler-sfc'

const SRC_DIR = path.resolve('src')
const EXTS = ['.vue', '.js']

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules') continue
      walk(full, files)
    } else if (EXTS.some((ext) => entry.name.endsWith(ext))) {
      files.push(full)
    }
  }
  return files
}

let fileCount = 0
let scriptBlockCount = 0
let templateCount = 0
let errorCount = 0

for (const file of walk(SRC_DIR)) {
  fileCount++
  const content = fs.readFileSync(file, 'utf-8')
  if (file.endsWith('.vue')) {
    const { descriptor, errors } = parseSfc(content, { filename: file })
    if (errors && errors.length) {
      errorCount++
      console.error(`[SFC] ${file}: ${errors.map((e) => e.message).join('; ')}`)
      continue
    }
    if (descriptor.script || descriptor.scriptSetup) {
      scriptBlockCount++
      const script = descriptor.scriptSetup || descriptor.script
      try {
        parseScript(script.content, {
          sourceType: 'module',
          plugins: ['jsx', 'typescript', 'decorators-legacy', 'importAssertions']
        })
      } catch (e) {
        errorCount++
        console.error(`[SCRIPT] ${file}: ${e.message}`)
      }
    }
    // 模板单独编译一次：@babel/parser 只看 script 块，模板里写错的指令/插值它看不见。
    if (descriptor.template) {
      templateCount++
      const { errors: templateErrors } = compileTemplate({
        source: descriptor.template.content,
        filename: file,
        id: path.basename(file)
      })
      if (templateErrors.length) {
        errorCount++
        console.error(`[TEMPLATE] ${file}: ${templateErrors.map((e) => e.message || String(e)).join('; ')}`)
      }
    }
  } else {
    scriptBlockCount++
    try {
      parseScript(content, {
        sourceType: 'module',
        plugins: ['jsx', 'typescript', 'decorators-legacy', 'importAssertions']
      })
    } catch (e) {
      errorCount++
      console.error(`[JS] ${file}: ${e.message}`)
    }
  }
}

console.log(`扫描 ${fileCount} 文件 / ${scriptBlockCount} script 块 / ${templateCount} 模板 / ERROR ${errorCount}`)
process.exit(errorCount > 0 ? 1 : 0)
