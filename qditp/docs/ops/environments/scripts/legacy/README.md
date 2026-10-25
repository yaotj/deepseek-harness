# legacy —— 历史/一次性脚本存档（⚠️ 勿复用）

本目录收容 2026-10-25 脚本重构时判定为「不再复用」的旧脚本。**保留只为留证与追溯，
NEVER 把它们当现役工具跑。** 现役入口只有一个：`../itp-ops.sh`（用法见 `../README.md`）。

| 文件 | 原位置 | 为什么进这里 |
|---|---|---|
| `_fix.ps1` / `_run_script.ps1` | `ops/` | 2026-10 早期在 Windows 上修 k8s-master 的一次性脚本（Posh-SSH）。⚠️ **内含硬编码 root 口令**（目标 172.20.211.21）——本文刻意不复述该口令；若该口令至今有效，MUST 找运维轮换。 |
| `klog.cmd` / `klog.ps1` | `ops/` | Windows 端日志查看包装器，对标当年的 Mac 版 `klog.sh`。已被 `itp-ops.sh log tail` 取代（Windows 上 ssh 直连即可，无需 PowerShell 包装）。 |
| `enable-tracing-20261014.sh` | `ops/` | 2026-10-14 把 A+B 档 12 个模块接入链路跟踪的一次性脚本，**早已执行完毕**。重跑会再次 patch Deployment；后续接入跟踪 MUST 按 `docs/architecture/common-components.md` §3.1.2「三行成组」口径做，NEVER 照抄本文件的 env 注入方式。 |
| `cloudos-api.sh` | `cloudos/test/` | 测试环境 CloudOS 封装。⚠️ **其认证结论已被抓包证伪**（2026-10-23）：它走「base64 明文 + rsa:true」，在红岛一律返回 `usrmgt.userOrPasswordError`——看着像密码错，实际是没解密，极易把人引向反复试密码。正确模型是 RSA-1024（PKCS#1 v1.5 + base64），见 `../lib/cloudos-auth.sh` 头注与 `../domains/cloudos.sh`。测试环境公钥至今未抓到，相关命令会拒绝执行并给抓取指引——**NEVER 为了「能跑」改回 base64 明文**。 |

## 已删除、内容并入现役脚本的旧文件

以下文件在重构时 `git rm`（它们从未进入 git 历史——根级 `scripts/` 于 SVN r1244
裁决不入库，本次进 git 前的唯一副本就是工作区；内容已逐条并入新脚本）：

| 旧文件 | 并入 |
|---|---|
| `cloudos/hongdao/cloudos-hongdao.sh` | `../domains/cloudos.sh`（应用运维全部命令） |
| `cloudos/hongdao/image-build.sh` | `../domains/cloudos.sh`（`image probe/plan/build`） |
| `cloudos/` 目录的 README（认证模型 + API 前缀 + 实测结论） | `../README.md` §三（CloudOS 知识沉淀） |
| `k8s/deploy-to-harbor.sh` | `../domains/k8s.sh`（`deploy`，失败继续） |
| `k8s/batch-deploy.sh` | `../domains/k8s.sh`（`deploy-all`，失败即断；ALL_SERVICES 两份手抄清单合并为单一真源） |
| `ops/klog.sh` | `../domains/log.sh`（`log tail`） |
| `ops/klog-watcher.sh` | `../domains/log.sh`（`log watch`；顺带修了状态文件查找格式不匹配导致的每 30s 全量重启监控缺陷） |
| `ops/dump-48000-routes.sh` | `../domains/diag.sh`（`routes`；修了基线目录失效被静默跳过的缺陷） |
| `ops/red-island-reachability.sh` | `../domains/diag.sh`（`reach`） |

如需考古：上面的「并入」列就是唯一真源；本目录的文件只作反面教材。
