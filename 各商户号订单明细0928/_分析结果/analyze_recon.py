# -*- coding: utf-8 -*-
import os, re, glob, json, warnings, html
import zipfile, xml.etree.ElementTree as ET
import pandas as pd
warnings.filterwarnings("ignore")

DATA = "/Users/tuanjie/workspace/company/chinasofti/qd/各商户号订单明细0928"
OUT  = "/Users/tuanjie/workspace/company/chinasofti/qd/各商户号订单明细0928/_分析结果"
os.makedirs(OUT, exist_ok=True)

def fnum(s):
    if s is None: return 0.0
    s = str(s).strip().replace(",", "")
    if s in ("", "None", "nan"): return 0.0
    try: return float(s)
    except: return 0.0

# ---------- 通用 xlsx 原始解析（处理 inlineStr + sharedStrings + 单元格坐标） ----------
def col_letter_to_idx(ref):
    # "AB12" -> 27 (1-based)
    s = re.match(r"([A-Za-z]+)", ref)
    if not s: return 0
    L = s.group(1).upper(); n = 0
    for ch in L: n = n*26 + (ord(ch)-64)
    return n-1

def read_xlsx_raw(path):
    z = zipfile.ZipFile(path)
    ss = []
    try:
        sr = ET.fromstring(z.read("xl/sharedStrings.xml"))
        for si in sr:
            ss.append("".join(x.text or "" for x in si.iter() if x.tag.endswith("}t")))
    except Exception:
        pass
    xml = z.read("xl/worksheets/sheet1.xml").decode("utf-8", "ignore")
    row_re = re.compile(r"<row\b[^>]*>(.*?)</row>", re.S)
    cell_re = re.compile(r"<c\b([^>]*)>(.*?)</c>", re.S)
    t_re = re.compile(r"<t[^>]*>(.*?)</t>", re.S)
    v_re = re.compile(r"<v>(.*?)</v>", re.S)
    rows = []
    for rm in row_re.finditer(xml):
        cells = {}
        maxc = 0
        for cm in cell_re.finditer(rm.group(1)):
            attr, body = cm.group(1), cm.group(2)
            ref = re.search(r'\br="([^"]+)"', attr)
            ci = col_letter_to_idx(ref.group(1)) if ref else maxc
            if "<is>" in body:
                mm = t_re.search(body); val = html.unescape(mm.group(1)) if mm else ""
            elif 't="s"' in attr or "t='s'" in attr:
                mm = v_re.search(body); val = ss[int(mm.group(1))] if (mm and ss) else ""
            else:
                mm = v_re.search(body); val = mm.group(1) if mm else ""
            cells[ci] = val
            maxc = max(maxc, ci+1)
        row = [cells.get(i, "") for i in range(maxc)]
        rows.append(row)
    return rows

# ---------- SpreadsheetML 2003 XML (.xls 实为 XML) ----------
def read_ss2003(path):
    rows = []
    context = ET.iterparse(path, events=("end",))
    for ev, el in context:
        if ev == "end" and el.tag.endswith("}Row"):
            cells = []
            for c in el:
                if c.tag.endswith("}Cell"):
                    txt = ""
                    for d in c:
                        if d.tag.endswith("}Data"):
                            txt = d.text or ""
                    cells.append(txt)
            rows.append(cells)
            el.clear()
    return rows

# ===================== 处理各文件 =====================
report = {"files": [], "channel_summary": {}, "merchant_detail": [], "flags": []}
def add_file(**kw): report["files"].append(kw)

# ---------- 微信 CSV ----------
def clean_backtick(v):
    if isinstance(v, str) and v.startswith("`"): return v[1:]
    return v

wechat_merchants = {}
for f in ["微信后付费-收-2026-09-28.csv","微信后付费-退_2026-09-28.csv",
          "微信预付费-收-2026-09-28.csv","微信预付费-退-2026-09-28.csv"]:
    p = os.path.join(DATA, f)
    df = pd.read_csv(p, dtype=str, keep_default_na=False, encoding="utf-8")
    for c in df.columns:
        df[c] = df[c].map(clean_backtick)
    n = len(df)
    is_refund_file = "退" in f
    # 剔除末尾汇总行（交易状态为空）
    if "交易状态" in df.columns:
        df = df[df["交易状态"].astype(str).str.strip() != ""].copy()
        n = len(df)
    # 金额列
    for c in ["应结订单金额","退款金额","订单金额","手续费","代金券金额","充值券退款金额","申请退款金额"]:
        if c in df.columns: df[c] = df[c].map(fnum)
    merch_col = "商户号" if "商户号" in df.columns else None
    # 按交易状态聚合
    by_status = {}
    if "交易状态" in df.columns:
        g = df.groupby("交易状态")
        for st, sub in g:
            by_status[st] = {
                "count": int(len(sub)),
                "应结订单金额": round(float(sub["应结订单金额"].sum()) if "应结订单金额" in sub else 0,2),
                "退款金额": round(float(sub["退款金额"].sum()) if "退款金额" in sub else 0,2),
                "订单金额": round(float(sub["订单金额"].sum()) if "订单金额" in sub else 0,2),
            }
    # 按商户聚合（收文件计净额、退文件计退款，避免退款项重复计入）
    by_merch = {}
    if merch_col:
        g = df.groupby(merch_col)
        for m, sub in g:
            coll = round(float(sub["应结订单金额"].sum()) if ("应结订单金额" in sub and not is_refund_file) else 0,2)
            ref  = round(float(sub["退款金额"].sum()) if ("退款金额" in sub and is_refund_file) else 0,2)
            by_merch[m] = {"count": int(len(sub)), "collection_net": coll, "refund": ref}
            wechat_merchants.setdefault(m, {"name": "", "files": []})
            wechat_merchants[m]["files"].append((f, coll, ref, int(len(sub))))
    # 汇总数字
    total_coll = round(float(df["应结订单金额"].sum()) if "应结订单金额" in df.columns else 0,2)
    total_ref  = round(float(df["退款金额"].sum()) if "退款金额" in df.columns else 0,2)
    total_fee  = round(float(df["手续费"].sum()) if "手续费" in df.columns else 0,2)
    add_file(name=f, channel="微信支付", role=("退款" if is_refund_file else "收款"),
             rows=n, size_mb=round(os.path.getsize(p)/1024/1024,1),
             merchant_col=merch_col, by_status=by_status,
             total_collection_net=total_coll, total_refund=total_ref, total_fee=total_fee)

# ---------- 支付宝 明细 xlsx ----------
import openpyxl
p = os.path.join(DATA,"支付宝出行对账明细表20260928.xlsx")
wb = openpyxl.load_workbook(p, read_only=True, data_only=True)
ws = wb.active
rows = list(ws.iter_rows(values_only=True))
header = rows[0]
idx = {h:i for i,h in enumerate(header) if h}
ali_detail = {"业务类型": {}}
cnt = 0
for r in rows[1:]:
    if r is None or all(v is None for v in r): continue
    cnt += 1
    bt = r[idx.get("业务类型")] if "业务类型" in idx else None
    oa = fnum(r[idx.get("订单金额（元）")]) if "订单金额（元）" in idx else 0
    sr = fnum(r[idx.get("商家实收（元）")]) if "商家实收（元）" in idx else 0
    bt = bt or "未知"
    d = ali_detail["业务类型"].setdefault(bt, {"count":0,"订单金额":0,"商家实收":0})
    d["count"]+=1; d["订单金额"]+=oa; d["商家实收"]+=sr
for bt,d in ali_detail["业务类型"].items():
    d["订单金额"]=round(d["订单金额"],2); d["商家实收"]=round(d["商家实收"],2)
add_file(name="支付宝出行对账明细表20260928.xlsx", channel="支付宝", role="出行明细",
         rows=cnt, size_mb=round(os.path.getsize(p)/1024/1024,1),
         by_business_type=ali_detail["业务类型"])

# ---------- 支付宝 汇总 / 累计未支付 ----------
for f,role in [("支付宝出行对账汇总表-20260928.xlsx","出行汇总"),
               ("支付宝对账累计未支付订单明细表20260928.xlsx","累计未支付")]:
    p=os.path.join(DATA,f)
    wb=openpyxl.load_workbook(p, read_only=True, data_only=True)
    ws=wb.active
    rws=list(ws.iter_rows(values_only=True))
    add_file(name=f, channel="支付宝", role=role, rows=max(0,len(rws)-1),
             size_mb=round(os.path.getsize(p)/1024/1024,2),
             preview=[list(x) for x in rws[:4]])
    wb.close()

# ---------- 支付宝 卖出交易 / 退款交易 XML ----------
for f,role in [("9.28-卖出交易-支付宝.xls","卖出(全量交易)"),
               ("9.28-退款交易-支付宝.xls","退款")]:
    p=os.path.join(DATA,f)
    rows=read_ss2003(p)
    # 前两行是账号/日期说明，第3行真表头
    header=rows[2]
    hidx={h:i for i,h in enumerate(header) if h}
    agg={}
    cnt=0
    for r in rows[3:]:
        if not r or all(v is None or v=="" for v in r): continue
        def gv(key):
            i=hidx.get(key)
            if i is None or i>=len(r): return ""
            return r[i]
        st=gv("交易状态") if "交易状态" in hidx else (gv("退款状态") if "退款状态" in hidx else "ALL")
        oa=fnum(gv("订单金额(元)")); rf=fnum(gv("退款金额(元)"))
        sr=fnum(gv("商家实收(元)")); fee=fnum(gv("服务费(元)"))
        d=agg.setdefault(st,{"count":0,"订单金额":0,"退款金额":0,"商家实收":0,"服务费":0})
        d["count"]+=1; d["订单金额"]+=oa; d["退款金额"]+=rf; d["商家实收"]+=sr; d["服务费"]+=fee
        cnt+=1
    for st,d in agg.items():
        for k in d: 
            if k!="count": d[k]=round(d[k],2)
    add_file(name=f, channel="支付宝", role=role, rows=cnt,
             size_mb=round(os.path.getsize(p)/1024/1024,1),
             merchant="20882515946761930156 (ddzhzxqfxtb@qd-metro.com)",
             by_status=agg)

# ---------- 建行 收 xlsx ----------
p=os.path.join(DATA,"9.28收-建行.xlsx")
rows=read_xlsx_raw(p)
# 第0行标题(含汇总括号)，第1行真表头
header=rows[1]
hidx={re.sub(r"\[.*?\]","",h).strip():i for i,h in enumerate(header) if h}
# 列名含括号汇总，需清洗键
def col(rows_header,i):
    return re.sub(r"\[.*?\]","",str(rows_header[i])).strip()
hidx={col(header,i):i for i in range(len(header)) if header[i]}
ccb={"count":0,"订单金额":0,"交易金额":0,"结算金额":0,"手续费":0,"成功笔数":0}
def cgv(r,key):
    i=hidx.get(key)
    if i is None or i>=len(r): return ""
    return r[i]
for r in rows[2:]:
    if not r or all(v is None or v=="" for v in r): continue
    ccb["count"]+=1
    oa=fnum(cgv(r,"订单金额")); ta=fnum(cgv(r,"交易金额"))
    sa=fnum(cgv(r,"结算金额")); fe=fnum(cgv(r,"手续费"))
    st=str(cgv(r,"订单状态"))
    ccb["订单金额"]+=oa; ccb["交易金额"]+=ta; ccb["结算金额"]+=sa; ccb["手续费"]+=fe
    if "成功" in st: ccb["成功笔数"]+=1
for k in ["订单金额","交易金额","结算金额","手续费"]: ccb[k]=round(ccb[k],2)
add_file(name="9.28收-建行.xlsx", channel="建设银行", role="收款明细", rows=ccb["count"],
         size_mb=round(os.path.getsize(p)/1024/1024,1),
         merchant="建设银行收单(单商户)", totals=ccb,
         note="表头行合并了汇总值：交易笔数[128712] 交易金额[405911.63] 手续费[0.09] 结算金额[405911.54]")

# ---------- 建行 退 csv ----------
p=os.path.join(DATA,"9.28退-建行.csv")
with open(p,encoding="utf-8") as fh:
    lines=[l.rstrip("\n") for l in fh if l.strip()]
# 第0行是带括号汇总的标题，第1行真表头
header_line=lines[1].split(",")
def bracket_val(line0,key):
    for part in line0.split(","):
        if key in part:
            m=re.search(r"\[(.*?)\]",part); 
            if m: return m.group(1)
    return ""
ccb_refund={"交易笔数":bracket_val(lines[0],"交易笔数"),
            "支付金额":bracket_val(lines[0],"支付金额"),
            "退款金额":bracket_val(lines[0],"退款金额")}
add_file(name="9.28退-建行.csv", channel="建设银行", role="退款", rows=len(lines)-2,
         size_mb=round(os.path.getsize(p)/1024/1024,2), totals=ccb_refund,
         note="CSV第0行内嵌汇总：交易笔数/支付金额/退款金额")

# ---------- 银联 POS / 收款码 ----------
for f in ["9.28银联POS.xls","9.28银联收款码.xls"]:
    p=os.path.join(DATA,f)
    rows=read_xlsx_raw(p)
    flat=[c for r in rows for c in r]
    summary={}
    for i,c in enumerate(flat):
        cs=str(c)
        if cs.startswith("总") and cs.endswith(":"):
            # 取其后第一个数值型单元格
            for j in range(i+1,min(i+4,len(flat))):
                v=str(flat[j]).strip()
                if v and not v.startswith("总") and re.match(r"^-?[\d.,]+$", v):
                    summary[cs]=v; break
    add_file(name=f, channel="银联", role=("POS" if "POS" in f else "收款码"),
             rows=max(0,len(rows)-1), size_mb=round(os.path.getsize(p)/1024/1024,2),
             summary=summary, note="WPS导出xlsx(扩展名.xls)；为银联汇总对账单，非逐笔明细")

# ===================== 汇总层 =====================
# 各渠道 收/退/净额
ch = {}
def ch_add(channel, coll, ref, fee):
    d=ch.setdefault(channel,{"collection_net":0.0,"refund":0.0,"fee":0.0,"coll_count":0,"ref_count":0})
    d["collection_net"]+=coll; d["refund"]+=ref; d["fee"]+=fee
# 微信（仅用“收”文件，退文件为同一退款的再陈述，避免重复计退）
for f in report["files"]:
    if f["channel"]=="微信支付" and f["role"]=="收款":
        ch_add("微信支付", f.get("total_collection_net",0), f.get("total_refund",0), f.get("total_fee",0))
# 支付宝 卖出(全量)
for f in report["files"]:
    if f["channel"]=="支付宝" and f["role"]=="卖出(全量交易)":
        for st,d in f["by_status"].items():
            ch_add("支付宝", d.get("商家实收",0), d.get("退款金额",0), d.get("服务费",0))
# 建行
for f in report["files"]:
    if f["channel"]=="建设银行" and f["role"]=="收款明细":
        ch_add("建设银行", f["totals"]["结算金额"], 0, f["totals"]["手续费"])
    if f["channel"]=="建设银行" and f["role"]=="退款":
        ch_add("建设银行", 0, fnum(f["totals"]["退款金额"]), 0)
# 银联
for f in report["files"]:
    if f["channel"]=="银联":
        amt=fnum(f.get("summary",{}).get("总交易金额(元):",None)) or fnum(f.get("summary",{}).get("总交易金额(元)",None))
        ch_add("银联", amt, 0, fnum(f.get("summary",{}).get("总手续费(元):",None)) or fnum(f.get("summary",{}).get("总手续费(元)",None)))
report["channel_summary"]=ch

# 商户维度
for m,info in wechat_merchants.items():
    coll=sum(x[1] for x in info["files"]); ref=sum(x[2] for x in info["files"]); cnt=sum(x[3] for x in info["files"])
    report["merchant_detail"].append({"merchant":m,"channel":"微信支付","collection_net":round(coll,2),"refund":round(ref,2),"rows":cnt})
report["merchant_detail"].append({"merchant":"20882515946761930156","channel":"支付宝","note":"ddzhzxqfxtb@qd-metro.com","collection_net":"见by_status","refund":"见by_status"})
report["merchant_detail"].append({"merchant":"建设银行收单","channel":"建设银行","collection_net":"见totals","refund":"见totals"})
report["merchant_detail"].append({"merchant":"银联(汇总)","channel":"银联","collection_net":"见summary","refund":"-"})

# 数据质量标记
report["flags"]=[
 "微信CSV所有字段带前导反引号(`) ，已清洗；金额按字符串读后转float。",
 "微信“收”文件内混杂 交易状态=REFUND 的行（应结订单金额=0，退款金额>0），与独立“退”文件存在口径重叠，统计净额以应结订单金额为准避免重复计收。",
 "支付宝“卖出交易/退款交易”为 SpreadsheetML 2003 XML（.xls扩展名伪装的XML），前两行为账号/日期说明，第3行才是表头；商户账号 20882515946761930156 全渠道唯一。",
 "支付宝“出行对账明细表”(161,426笔/商家实收614,399) 与 “卖出交易”(52,067笔/商家实收237,288) 均为2026-09-28数据，但前者笔数与金额均约为后者的3倍，彼此矛盾（出行若只是产品子集不应大于全量）；需核实卖出交易是否仅含部分交易或两套表统计口径不同，二选其一后再并入总账。",
 "支付宝“出行对账汇总表”与“累计未支付”表内为模板/历史示例数据(含2025年日期)，0928当日汇总需以明细表/卖出交易为准。",
 "建行“收”xlsx 的 dimension 元数据误标为 A1，openpyxl 仅读1列；已用原始XML解析，真实约12.8万行；表头列名内嵌全表汇总(交易金额[405911.63]等)。",
 "银联两个文件实为 WPS 导出的标准xlsx(扩展名.xls)，是银联汇总对账单(总交易金额/笔数/手续费)，非逐笔明细，无独立商户号。",
 "本环境无法直接访问业务库(172.20.222.3 不可达)，以上为“渠道侧对账单刻画 + 各商户汇总”，真正的账实核对需以平台内部订单为基准另行比对。",
]

with open(os.path.join(OUT,"analysis.json"),"w",encoding="utf-8") as fh:
    json.dump(report, fh, ensure_ascii=False, indent=2)
print("DONE. files:",len(report["files"]))
print(json.dumps(report, ensure_ascii=False, indent=2)[:6000])
