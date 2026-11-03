"""itpops —— 青岛地铁 ITP 平台运维工具集（Python 底层实现）。

命令面与历史 `itp-ops.sh` 逐字兼容（`itp-ops.sh` 现为薄壳转调本包）；
双受众契约见 `cli.py` 头注：人机输出走 stderr/着色，大模型用 `-o json`（--json 同义）
+ `itpops catalog`；全局参数与分层帮助对齐 kubectl/gh 的用法约定。
"""

ITP_OPS_VERSION = "2.1.0"
