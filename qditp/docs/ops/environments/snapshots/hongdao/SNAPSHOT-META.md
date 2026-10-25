# 红岛生产环境快照（当前为空）

> ⚠️ **本目录没有任何快照内容，且刻意留空。**
> 2026-10-26 审计确认：仓库里原先所有的集群快照（`snapshots/test/` 那批）**全部来自测试环境**，
> 红岛生产侧**零覆盖**。本目录是为红岛预留的位置，**NEVER 用测试环境的值填进来冒充红岛**。

## 为什么不能照抄 test/

| 维度 | 测试环境（`snapshots/test/`） | 红岛生产（待抓） |
|---|---|---|
| 命名空间 | `itp` | **`itpb`** |
| NodePort 段 | `30009~30035` | **`31919~31943`**（9098/9100/9101 在红岛不存在） |
| Service port | 有 9098/9100/9101/9102 | 统一 `targetPort 8080`，port=NodePort=319xx |
| Harbor 项目 | `itp` | **`itpb`**（推送账号 `itpadmin`） |
| 网关 Pod 命名空间 | `itp-gateway` | **`istio-ingress`** |
| 公网入向入口 | `58.56.166.170:48000` | **`172.120.2.26:48000`** |
| VictoriaLogs | `172.20.211.23:30032` | **`172.120.2.13:31920`** |
| 数据库 | `172.20.222.3:1521/AFCITPDB` | `172.120.110.25:1521/hditpdb` |

照抄后果（已发生过）：Service 名带平台随机后缀、抄过去一律 503。

## 抓取命令（抓到之前本目录保持空）

```bash
# 前提：能 ssh 到红岛跳板 hd-linux（172.120.2.13，属 K8s 控制区 172.120.2.x 网段）
ssh hd-linux

kubectl get deploy -n itpb -o wide                        # 应用清单 + 镜像 tag
kubectl get svc   -n itpb -o wide                         # Service / NodePort 矩阵
kubectl get cm    -n itpb                                 # ConfigMap
kubectl get sts   -n itpb -o wide                         # StatefulSet（VictoriaLogs）
kubectl get deploy -n itpb -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{range .spec.template.spec.containers[*].env[*]}  {.name}{"\n"}{end}{end}'
# ↑ env 键全集最有价值：红岛「SERVICE_*_URL 未注入 → 跨模块 RPC 全失败」那个坑要靠它核对
kubectl get vs -n itpb -o yaml                            # 入向路由（红岛是 15 条：7 exact SCG + 8 prefix）
kubectl get gateway -n itpb -o yaml
```

## 落地后 MUST 同步

- `docs/ops/environments/红岛.md`（环境真源文档）
- `docs/ops/environments/scripts/README.md` §一 结构树
- 抓取日期与有效期写进本文件顶部（快照会过期，NEVER 当永久事实）
