#!/usr/bin/env node
/**
 * 一次性探针：核对「前端提示用正则」与「后端裁判正则」对同一批用例的判定是否一致。
 * 两个模式都从源码里读出来再构造，不在本文件重抄一遍（重抄就等于没测）。
 * 用法：cd web && node recon-name-probe.mjs
 */
import fs from 'fs'

const JS_FILE = 'src/utils/reconFileType.js'
const JAVA_FILE = '../web-server/web-admin/src/main/java/com/chinasofti/huateng/web/controller/recon/ReconUploadFileController.java'

const js = fs.readFileSync(JS_FILE, 'utf-8')
const java = fs.readFileSync(JAVA_FILE, 'utf-8')

function fromJs(name) {
  const m = new RegExp(`const ${name} = /(.+)/`).exec(js)
  if (!m) throw new Error(`前端没找到 ${name}`)
  return new RegExp(m[1])
}
function fromJava(name) {
  const m = new RegExp(`${name} =\\s*\\n?\\s*Pattern\\.compile\\("(.+?)"\\)`).exec(java)
  if (!m) throw new Error(`后端没找到 ${name}`)
  return new RegExp(m[1].replace(/\\\\/g, '\\'))
}

const jsItp = fromJs('ITP_NAME')
const jsBill = fromJs('BILL_NAME')
const jvItp = fromJava('ITP_NAME')
const jvBill = fromJava('BILL_NAME')

const cases = [
  ['ITP.PAY.20261105', true, 'PAY'],
  ['ITP.EXP.20261105', true, 'EXP'],
  ['ITP.BUS.20260910', true, 'BUS'],
  ['ITP.DETAIL.20261105', true, 'DETAIL'],
  ['ITP.TXNDIFF.20261105', true, 'TXNDIFF'],
  ['ITP.PAY.20261105.csv', true, 'PAY'],
  ['ITP.PAY.20261105.xlsx', true, 'PAY'],
  ['ITP.PAY.202611', false, null],
  ['ITP.PAY.20261399', true, 'PAY'],
  ['ITP.FOO.20261105', false, null],
  ['itp.pay.20261105', false, null],
  ['ITP.PAY.20261105.csv.gz', false, null],
  ['reconciliation-bill-20261105.csv', true, 'BILL'],
  ['reconciliation-bill-20261105', true, 'BILL'],
  ['reconciliation-bill-date.csv', false, null],
  ['reconciliation-bill-20261105.zip', true, 'BILL'],
  ['对账.xls', false, null],
  ['ITP.PAY.20261105/../evil', false, null],
  ['../../ITP.PAY.20261105', false, null],
  ['ITP.PAY.20261105.CSV', true, 'PAY'],
  ['ITP.PAY.20261105.aaaaaaaaaaa', false, null]
]

let bad = 0
console.log('用例'.padEnd(34), '前端ITP', '前端BILL', '后端ITP', '后端BILL', '判定类别', '一致?')
for (const [name, expectMatch, expectCat] of cases) {
  const a = jsItp.test(name), b = jsBill.test(name)
  const c = jvItp.test(name), d = jvBill.test(name)
  const agree = a === c && b === d
  const matched = (a || b) === expectMatch
  const cat = a ? jsItp.exec(name)[1] : (b ? 'BILL' : null)
  const catOk = cat === expectCat
  if (!agree || !matched || !catOk) bad++
  console.log(
    name.padEnd(34),
    String(a).padEnd(7), String(b).padEnd(8), String(c).padEnd(7), String(d).padEnd(8),
    String(cat).padEnd(8),
    agree && matched && catOk ? 'OK' : `✗ 前后端一致=${agree} 命中预期=${matched} 类别=${catOk}`
  )
}
console.log(`\n共 ${cases.length} 例，不符合预期 ${bad} 例`)
console.log(bad === 0 ? 'PROBE OK' : 'PROBE FAILED')
process.exit(bad === 0 ? 0 : 1)
