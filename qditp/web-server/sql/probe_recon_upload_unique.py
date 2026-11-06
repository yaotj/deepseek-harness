#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""一次性探针：实测 UK_RUF_CATEGORY_DATE 的三类行为，跑完自删。

判据（三条都必须成立，否则唯一约束是假的）：
  A 同 (类别, 账期) 第二次插入 ⇒ 必须报 ORA-00001
  B 类别为 NULL 的两行同账期 ⇒ 必须都成功（函数式索引全 NULL 键被跳过 ⇒ 历史行豁免）
  C 同类别不同账期 ⇒ 必须成功
"""
import os
import sys

import oracledb

DSN = "172.20.222.3:1521/AFCITPDB"
USER = "QDITP"
PWD = os.environ.get("QDITP_PWD", "qditp")

PROBE_DATE = "29991231"
PROBE_CAT = "PROBE"
IDS = []

fails = []


def ins(cur, cat):
    rid = cur.var(int)
    cur.execute(
        "insert into recon_upload_file (id, file_name, file_path, file_category, recon_date, status)"
        " values (seq_recon_upload_file.nextval, 'PROBE', '/tmp/PROBE', :cat, :dt, 'UPLOADED')"
        " returning id into :rid",
        {"cat": cat, "dt": PROBE_DATE, "rid": rid})
    new_id = rid.getvalue(0)
    IDS.append(new_id)
    return new_id


def main():
    with oracledb.connect(user=USER, password=PWD, dsn=DSN) as conn:
        with conn.cursor() as cur:
            # A：第一行应成功
            try:
                ins(cur, PROBE_CAT)
                print("[A1] 首行 PROBE/29991231 插入 OK")
            except oracledb.DatabaseError as e:
                fails.append(f"A1 首行插入意外失败: {e}")

            # A：第二行同 (类别, 账期) 必须被拒
            try:
                ins(cur, PROBE_CAT)
                fails.append("A2 同(类别,账期)第二行竟然成功了 ⇒ 唯一约束没生效")
                print("[A2] ✗ 未被拒绝")
            except oracledb.DatabaseError as e:
                code = e.args[0].code
                print(f"[A2] 第二行被拒 code={code} {'✓ 符合预期(ORA-00001)' if code == 1 else '✗ 不是唯一冲突'}")
                if code != 1:
                    fails.append(f"A2 拒绝原因不是 ORA-00001 而是 {code}")

            conn.rollback()

            # B：类别 NULL 的两行同账期必须都成功（历史行豁免）
            try:
                ins(cur, None)
                ins(cur, None)
                print("[B] 两行 (NULL, 29991231) 均插入 OK ✓ 历史行确实豁免")
            except oracledb.DatabaseError as e:
                fails.append(f"B 类别为 NULL 的同账期两行互相冲突 ⇒ 豁免没做成: {e}")
                print("[B] ✗ 被拒")

            # C：同类别不同账期必须成功
            try:
                cur.execute(
                    "insert into recon_upload_file (id, file_name, file_path, file_category, recon_date, status)"
                    " values (seq_recon_upload_file.nextval, 'PROBE', '/tmp/PROBE', :cat, :dt, 'UPLOADED')",
                    {"cat": PROBE_CAT, "dt": "29991230"})
                print("[C] 同类别不同账期插入 OK ✓")
            except oracledb.DatabaseError as e:
                fails.append(f"C 同类别不同账期被误拦: {e}")

            # 清理：只删本探针插的行（file_name='PROBE' 且账期是探针专用日期），
            # 两个条件都用括号显式分组 —— 之前写成 A OR B AND C 会按 A OR (B AND C) 结合，
            # 万一库里已有 file_name='PROBE' 的行就会被连带删掉。
            cur.execute(
                "delete from recon_upload_file"
                " where (file_name = 'PROBE' and file_path = '/tmp/PROBE')"
                "   and recon_date in (:a, :b)",
                {"a": PROBE_DATE, "b": "29991230"})
            print(f"[清理] 删除 {cur.rowcount} 行探针数据")
            left = cur.execute(
                "select count(*) from recon_upload_file where file_name = 'PROBE' or recon_date in ('29991231','29991230')"
            ).fetchone()[0]
            print(f"[清理] 残留探针行 = {left}")
            conn.commit()

            total = cur.execute("select count(*) from recon_upload_file").fetchone()[0]
            print(f"[终态] RECON_UPLOAD_FILE 总行数 = {total}（跑前应为你已知的行数，探针不留痕）")

    if fails:
        print("\nPROBE FAILED:")
        for f in fails:
            print("  -", f)
        sys.exit(1)
    print("\nPROBE OK：唯一约束确实拦同(类别,账期)，且 NULL 类别的历史行不受影响")


if __name__ == "__main__":
    main()
