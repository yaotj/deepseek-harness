"""itpops.domains —— 域实现（cloudos / k8s / log / diag / analysis / version）。

每个域模块的契约（cli.py 消费）：
  CATALOG: dict   自描述注册表（itpops catalog 汇总给大模型）
  main(args: list[str]) -> int   域入口；成功返回 0（结果经 ui.out / ui.result），
                                 失败抛 OpsError（细分退出码）
"""
