#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在测试环境执行 recon-upload-file-menu.sql。"""
import os
import re
import sys
from pathlib import Path

import oracledb

SQL_FILE = Path(__file__).resolve().parent / "recon-upload-file-menu.sql"
DSN = "172.20.222.3:1521/AFCITPDB"
USER = "QDITP"
PWD = os.environ.get("QDITP_PWD", "qditp")


def split_sql(text: str):
    """拆分 Oracle SQL 脚本，支持 PL/SQL 匿名块（以单独 '/' 行结束）。"""
    # 去掉行注释
    lines = []
    for raw in text.splitlines():
        line = re.sub(r"--.*", "", raw).rstrip()
        if line:
            lines.append(line)

    stmts = []
    buf = []
    in_plsql = False

    for line in lines:
        stripped = line.strip().upper()

        # 检测 PL/SQL 块开始
        if re.match(r"^BEGIN\b", stripped):
            in_plsql = True

        buf.append(line)

        if in_plsql:
            # PL/SQL 块以单独一行的 '/' 结束
            if line.strip() == "/":
                # 去掉结尾的 '/' 并执行
                body = "\n".join(buf[:-1]).strip()
                if body:
                    stmts.append(body)
                buf = []
                in_plsql = False
        else:
            if line.rstrip().endswith(";"):
                body = "\n".join(buf).rstrip(";").strip()
                if body:
                    stmts.append(body)
                buf = []

    # 尾部残留
    if buf:
        body = "\n".join(buf).rstrip(";").strip()
        if body and body != "/":
            stmts.append(body)

    return stmts


def main():
    sql = SQL_FILE.read_text(encoding="utf-8")
    statements = split_sql(sql)
    print(f"解析到 {len(statements)} 条语句，即将连接 {DSN} ...")

    with oracledb.connect(user=USER, password=PWD, dsn=DSN) as conn:
        with conn.cursor() as cursor:
            for i, stmt in enumerate(statements, 1):
                try:
                    cursor.execute(stmt)
                    print(f"[{i:02d}] OK")
                except oracledb.DatabaseError as e:
                    error, = e.args
                    code = error.code
                    # 幂等：对象已存在 / 记录已存在 / 索引已存在
                    if code in (955, 2260, 1, 1418, 1430, 1408):
                        print(f"[{i:02d}] SKIP (已存在): {error.message.split(chr(10))[0]}")
                    else:
                        print(f"[{i:02d}] FAIL: {error.message}")
                        print("--- 失败语句 ---")
                        print(stmt)
                        print("----------------")
                        raise
        conn.commit()
    print("执行完成并已提交。")


if __name__ == "__main__":
    main()
