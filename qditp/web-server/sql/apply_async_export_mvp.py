#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在测试环境执行 async-export-mvp.sql。"""
import os
import re
import sys
from pathlib import Path

import oracledb

SQL_FILE = Path(__file__).resolve().parent / "async-export-mvp.sql"
DSN = "172.20.222.3:1521/AFCITPDB"
USER = "QDITP"
PWD = os.environ.get("QDITP_PWD", "qditp")


def split_sql(text: str):
    """按分号拆分并去掉注释与空行；返回无尾部空语句的列表。"""
    # 去掉行注释与块注释
    text = re.sub(r"--.*", "", text)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    stmts = [s.strip() for s in text.split(";")]
    return [s for s in stmts if s]


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
                    # 幂等：对象已存在/记录已存在 视为 OK
                    code = error.code
                    if code in (955, 2260, 1, 1418, 1430):  # ORA-00955 名称已由现有对象使用, ORA-02260 表已存在, ORA-00001 唯一约束, ORA-01418 指定的索引不存在, ORA-01430 表中已存在要添加的列
                        print(f"[{i:02d}] SKIP (已存在): {error.message.split(chr(10))[0]}")
                    else:
                        print(f"[{i:02d}] FAIL: {error.message}")
                        raise
        conn.commit()
    print("执行完成并已提交。")


if __name__ == "__main__":
    main()
