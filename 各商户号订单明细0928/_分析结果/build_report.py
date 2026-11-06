# -*- coding: utf-8 -*-
import json, os
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side

D="/Users/tuanjie/workspace/company/chinasofti/qd/各商户号订单明细0928"
OUT=os.path.join(D,"_分析结果")
r=json.load(open(os.path.join(OUT,"analysis.json"),encoding="utf-8"))

files=r["files"]; ch=r["channel_summary"]; md=r["merchant_detail"]; flags=r["flags"]

# 取关键子项
def fobj(name): return next((f for f in files if f["name"]==name), {})
ali_detail=fobj("支付宝出行对账明细表20260928.xlsx").get("by_business_type",{})
ali_sell=fobj("9.28-卖出交易-支付宝.xls").get("by_status",{})
ali_refund=fobj("9.28-退款交易-支付宝.xls").get("by_status",{})
ccb=fobj("9.28收-建行.xlsx").get("totals",{})
ccb_refund=fobj("9.28退-建行.csv").get("totals",{})
union_pos=fobj("9.28银联POS.xls").get("summary",{})
union_qr=fobj("9.28银联收款码.xls").get("summary",{})

# ---------- Excel ----------
wb=Workbook()
hdr_fill=PatternFill("solid",fgColor="1F4E78"); hdr_font=Font(color="FFFFFF",bold=True)
title_font=Font(bold=True,size=13,color="1F4E78")
thin=Side(style="thin",color="BFBFBF"); border=Border(left=thin,right=thin,top=thin,bottom=thin)
def style_header(ws,row,ncol):
    for c in range(1,ncol+1):
        cell=ws.cell(row=row,column=c); cell.fill=hdr_fill; cell.font=hdr_font; cell.border=border
        cell.alignment=Alignment(horizontal="center",vertical="center",wrap_text=True)
def put_table(ws,start,headers,rows):
    for j,h in enumerate(headers,1): ws.cell(row=start,column=j,value=h)
    style_header(ws,start,len(headers))
    for i,row in enumerate(rows,start+1):
        for j,v in enumerate(row,1):
            c=ws.cell(row=i,column=j,value=v); c.border=border
            if isinstance(v,(int,float)): c.alignment=Alignment(horizontal="right")
    return start+1+len(rows)

# Sheet1 文件清单
ws=wb.active; ws.title="文件清单"
ws.cell(1,1,"各商户号订单明细0928 — 文件清单（2026-09-28 多渠道对账包）").font=title_font
rows=[[f["name"],f["channel"],f["role"],f.get("rows"),f.get("size_mb"),
       (f.get("merchant") or (f.get("totals") and "—") or "—")] for f in files]
put_table(ws,3,["文件名","渠道","角色(收/退/汇总)","数据行数","大小(MB)","商户/账号"],rows)
ws.column_dimensions["A"].width=40; ws.column_dimensions["B"].width=12; ws.column_dimensions["C"].width=16
for col in "DEF": ws.column_dimensions[col].width=12
ws.column_dimensions["F"].width=34

# Sheet2 渠道汇总
ws=wb.create_sheet("渠道汇总")
ws.cell(1,1,"各渠道 收款净额 / 退款 / 手续费 汇总（单位：元）").font=title_font
rows=[[c,v["collection_net"],v["refund"],v["fee"],round(v["collection_net"]-v["refund"]-v["fee"],2)] for c,v in ch.items()]
tot=[sum(x[1] for x in rows),sum(x[2] for x in rows),sum(x[3] for x in rows),sum(x[4] for x in rows)]
rows.append(["合计"]+tot)
put_table(ws,3,["渠道","收款净额","退款","手续费","净额(收-退-费)"],rows)
for col in "ABCDE": ws.column_dimensions[col].width=16

# Sheet3 商户维度
ws=wb.create_sheet("商户汇总")
ws.cell(1,1,"各商户号订单明细汇总").font=title_font
rows=[]
for m in md:
    if m.get("collection_net") not in ("见by_status","见totals","见summary",None):
        rows.append([m.get("merchant"),m.get("channel"),m.get("collection_net"),m.get("refund"),m.get("rows")])
    else:
        rows.append([m.get("merchant"),m.get("channel"),m.get("note",""),"",m.get("rows")])
put_table(ws,3,["商户号/账号","渠道","收款净额","退款","数据行数"],rows)
ws.column_dimensions["A"].width=24; ws.column_dimensions["B"].width=12; ws.column_dimensions["C"].width=16
ws.column_dimensions["D"].width=14; ws.column_dimensions["E"].width=12

# Sheet4 微信明细
ws=wb.create_sheet("微信商户状态")
ws.cell(1,1,"微信支付：按文件 + 交易状态").font=title_font
wx=[f for f in files if f["channel"]=="微信支付"]
rows=[]
for f in wx:
    for st,d in f.get("by_status",{}).items():
        rows.append([f["name"],st,d["count"],d["应结订单金额"],d["退款金额"],d["订单金额"]])
end=put_table(ws,3,["文件","交易状态","笔数","应结订单金额","退款金额","订单金额"],rows)
# 商户聚合
rows=[]
for m in md:
    if m.get("channel")=="微信支付" and isinstance(m.get("collection_net"),(int,float)):
        rows.append([m["merchant"],m["collection_net"],m["refund"],m["rows"]])
put_table(ws,end+2,["微信商户号","收款净额","退款","行数"],rows)
for col in "ABCDEF": ws.column_dimensions[col].width=20

# Sheet5 支付宝明细
ws=wb.create_sheet("支付宝明细")
ws.cell(1,1,"支付宝：卖出交易(全量) / 出行明细 / 退款交易").font=title_font
rows=[]
for st,d in ali_sell.items():
    rows.append(["卖出交易·"+str(st),d["count"],d["订单金额"],d["退款金额"],d["商家实收"],d["服务费"]])
end=put_table(ws,3,["卖出交易-状态","笔数","订单金额","退款金额","商家实收","服务费"],rows)
rows=[]
for bt,d in ali_detail.items():
    rows.append(["出行明细·"+str(bt),d["count"],d["订单金额"],"-",d["商家实收"],"-"])
end=put_table(ws,end+2,["出行明细-业务类型","笔数","订单金额","退款金额","商家实收","服务费"],rows)
rows=[]
for st,d in ali_refund.items():
    rows.append(["退款交易·"+str(st),d["count"],d["订单金额"],d["退款金额"],d.get("商家实收","-"),d.get("服务费","-")])
put_table(ws,end+2,["退款交易-状态","笔数","订单金额","退款金额","商家实收","服务费"],rows)
for col in "ABCDEF": ws.column_dimensions[col].width=18

# Sheet6 建行/银联/退款
ws=wb.create_sheet("建行银联退款")
ws.cell(1,1,"建设银行 / 银联 / 退款 汇总").font=title_font
rows=[
 ["建设银行-收(单商户)",ccb.get("count"),ccb.get("订单金额"),ccb.get("交易金额"),ccb.get("结算金额"),ccb.get("手续费"),ccb.get("成功笔数")],
 ["建设银行-退(csv)",ccb_refund.get("交易笔数"),"-","-",ccb_refund.get("退款金额"),"-","-"],
 ["银联-POS",union_pos.get("总交易笔数:"),"","",union_pos.get("总交易金额(元):"),union_pos.get("总手续费(元):"),union_pos.get("总结算金额(元):")],
 ["银联-收款码",union_qr.get("总交易笔数:"),"","",union_qr.get("总交易金额(元):"),union_qr.get("总手续费(元):"),union_qr.get("总结算金额(元):")],
]
put_table(ws,3,["项目","笔数/笔","订单金额","交易金额","金额/结算","手续费","成功笔数"],rows)
ws.column_dimensions["A"].width=20
for col in "BCDEFG": ws.column_dimensions[col].width=14

# Sheet7 数据质量标记
ws=wb.create_sheet("数据质量与风险")
ws.cell(1,1,"数据质量 / 对账风险标记").font=title_font
for i,fl in enumerate(flags,3):
    c=ws.cell(row=i,column=1,value=f"{i-2}. {fl}"); c.alignment=Alignment(wrap_text=True,vertical="top")
    ws.row_dimensions[i].height=42
ws.column_dimensions["A"].width=120

wb.save(os.path.join(OUT,"对账汇总_0928.xlsx"))

# ---------- Markdown ----------
def tb(headers,rows):
    out="| "+" | ".join(headers)+" |\n| "+" | ".join("---" for _ in headers)+" |\n"
    for rrow in rows:
        out+="| "+" | ".join(str(x) for x in rrow)+" |\n"
    return out

md_lines=[]
md_lines.append("# 各商户号订单明细 0928 — 对账分析报告\n")
md_lines.append(f"> 数据日期：**2026-09-28**　分析对象：`/各商户号订单明细0928/` 目录下 **12 个文件**，覆盖 **微信支付 / 支付宝 / 建设银行 / 银联** 4 类渠道。\n")
md_lines.append("## 一、文件清单（12 个）\n")
md_lines.append(tb(["文件名","渠道","角色","数据行数","大小(MB)"],
  [[f["name"],f["channel"],f["role"],f.get("rows"),f.get("size_mb")] for f in files]))
md_lines.append("\n## 二、各渠道 收款净额 / 退款 / 手续费 汇总（元）\n")
rows=[[c,f'{v["collection_net"]:,.2f}',f'{v["refund"]:,.2f}',f'{v["fee"]:,.2f}',f'{v["collection_net"]-v["refund"]-v["fee"]:,.2f}'] for c,v in ch.items()]
rows.append(["合计",f'{sum(v["collection_net"] for v in ch.values()):,.2f}',f'{sum(v["refund"] for v in ch.values()):,.2f}',f'{sum(v["fee"] for v in ch.values()):,.2f}',f'{sum(v["collection_net"]-v["refund"]-v["fee"] for v in ch.values()):,.2f}'])
md_lines.append(tb(["渠道","收款净额","退款","手续费","净额(收-退-费)"],rows))
md_lines.append("\n## 三、各商户号订单明细汇总\n")
md_lines.append("### 微信支付（2 个商户号）\n")
rows=[[m["merchant"],m["collection_net"],m["refund"],m["rows"]] for m in md if m.get("channel")=="微信支付" and isinstance(m.get("collection_net"),(int,float))]
md_lines.append(tb(["微信商户号","收款净额(元)","退款(元)","数据行数"],rows))
md_lines.append("- `1113224475`（公众账号 wx456ba221a552b382）= **后付费**产品；\n- `1113243080`（公众账号 wx9bca0cc124f355f0）= **预付费**产品。\n")
md_lines.append("### 支付宝（单商户 20882515946761930156，邮箱 ddzhzxqfxtb@qd-metro.com）\n")
md_lines.append("- 卖出交易(全量商户账单)口径：商家实收 **237,288.17** 元（成功 50,120 笔 + 关闭 1,947 笔）；退款 **738.00** 元。\n")
md_lines.append("- 出行对账明细表口径：商家实收 **614,399.00** 元（交易 161,426 笔）。\n")
md_lines.append("> ⚠️ **两套支付宝口径严重不一致**（61.4 万 vs 23.7 万，行数 16.1 万 vs 5.2 万），出行明细表的笔数/金额反而大于“全量交易”，逻辑上不成立，需人工核实两张表各自的时间范围/产品范围后再用。\n")
md_lines.append("### 建设银行（单收单商户）\n")
md_lines.append(f"- 收款明细：{ccb.get('count')} 笔，订单金额 {ccb.get('订单金额'):,.2f}，交易金额 {ccb.get('交易金额'):,.2f}，结算金额 **{ccb.get('结算金额'):,.2f}**，手续费 {ccb.get('手续费'):,.2f}（全部成功）。\n")
md_lines.append(f"- 退款（csv）：{ccb_refund.get('交易笔数')} 笔，退款 **{ccb_refund.get('退款金额')}** 元。\n")
md_lines.append("### 银联（WPS 汇总对账单，非逐笔明细）\n")
md_lines.append(f"- POS：{union_pos.get('总交易笔数:')} 笔，金额 **{union_pos.get('总交易金额(元):')}** 元，手续费 {union_pos.get('总手续费(元):')} 元，结算 {union_pos.get('总结算金额(元):')} 元。\n")
md_lines.append(f"- 收款码：{union_qr.get('总交易笔数:')} 笔，金额 **{union_qr.get('总交易金额(元):')}** 元，手续费 {union_qr.get('总手续费(元):')} 元，结算 {union_qr.get('总结算金额(元):')} 元。\n")
md_lines.append("\n## 四、微信支付 按交易状态\n")
rows=[]
for f in [x for x in files if x["channel"]=="微信支付"]:
    for st,d in f.get("by_status",{}).items():
        rows.append([f["name"],st,d["count"],f'{d["应结订单金额"]:,.2f}',f'{d["退款金额"]:,.2f}',f'{d["订单金额"]:,.2f}'])
md_lines.append(tb(["文件","交易状态","笔数","应结订单金额","退款金额","订单金额"],rows))
md_lines.append("\n## 五、支付宝 明细\n")
rows=[[f'卖出交易·{st}',d["count"],f'{d["订单金额"]:,.2f}',f'{d["退款金额"]:,.2f}',f'{d["商家实收"]:,.2f}',f'{d["服务费"]:,.2f}'] for st,d in ali_sell.items()]
md_lines.append("**卖出交易（全量商户账单）按状态：**\n"+tb(["状态","笔数","订单金额","退款金额","商家实收","服务费"],rows))
rows=[[f'出行明细·{bt}',d["count"],f'{d["订单金额"]:,.2f}','—',f'{d["商家实收"]:,.2f}','—'] for bt,d in ali_detail.items()]
md_lines.append("\n**出行对账明细表 按业务类型：**\n"+tb(["业务类型","笔数","订单金额","退款金额","商家实收","服务费"],rows))
rows=[[f'退款交易·{st}',d["count"],f'{d["订单金额"]:,.2f}',f'{d["退款金额"]:,.2f}',d.get("商家实收","—"),d.get("服务费","—")] for st,d in ali_refund.items()]
md_lines.append("\n**退款交易（独立退款报表）：**\n"+tb(["退款状态","笔数","订单金额","退款金额","商家实收","服务费"],rows))
md_lines.append("\n## 六、数据质量与对账风险标记\n")
for i,fl in enumerate(flags,1): md_lines.append(f"{i}. {fl}")
md_lines.append("\n## 七、下一步建议\n")
md_lines.append("1. **先消解支付宝两套口径矛盾**：确认“出行对账明细表”与“卖出交易”各自统计口径（是否含历史补单/非出行产品/不同日期），以其中一套为准后再并入总账。\n")
md_lines.append("2. **与平台内部订单库核对**：本环境业务库(172.20.222.3)不可达，以上为渠道侧对账单刻画；真正账实核对需以平台订单（QDITP 库）为基准，按 `商户订单号`/`微信订单号`/`支付宝交易号` 逐笔勾兑。\n")
md_lines.append("3. **微信退款口径统一**：微信“收”文件内已含 REFUND 行（应结=0），与独立“退”文件是同一批退款的两种呈现，汇总时只取其一避免重复计退（本报告已处理）。\n")
md_lines.append("4. **建行/银联为银行侧汇总单**，可直接作为银行到账依据；银联无逐笔明细，若需逐笔需向银联另要流水。\n")

with open(os.path.join(OUT,"对账分析_0928.md"),"w",encoding="utf-8") as fh:
    fh.write("\n".join(md_lines))
print("REPORT WRITTEN")
print(os.path.join(OUT,"对账分析_0928.md"), os.path.join(OUT,"对账汇总_0928.xlsx"))
print("flags count:",len(flags))
