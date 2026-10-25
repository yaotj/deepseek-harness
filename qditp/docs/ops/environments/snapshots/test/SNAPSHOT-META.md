# 测试环境快照

> 目录原名 `scripts/migration/k8s-current-config/`，2026-10-26 分环境重构时移到此地
> （它记录的是**测试环境集群实况**，与数据库迁移无关，放在 migration/ 下是错位的）。

## 归属证据（确属测试环境，NEVER 当红岛用）

- 镜像前缀 `os-harbor-svc.default.svc.cloudos:443/**itp**/`（红岛是 `itpb`）— `deployments.md`
- NodePort 集中在 `30011~30035`，Service port 出现 `9098/9100/9101/9102` — `services.md`
  （这些端口在红岛**一律不存在**，红岛是 `31919~31943`）
- `victoria-logs-pbi6a-svc ... 30032` — `services.md`（测试专用地址）
- 含 `collect-pay`（红岛未部署）与 istio bookinfo 样例 — `deployments.md`
- 32 个 Deployment ≠ 红岛实跑 24 个

## 抓取时间

- 推定 **2026-10-14**：`configmaps.txt` 里 `istio-ca-root-cert 215d`、`kube-root-ca.crt 215d`，
  与 `docs/ops/environments/scripts/migration/ingress-config-cleaned/README.md` 声明的导出日期吻合
  （2026-03-13 + 215d ≈ 2026-10-14）。
- 抓取命令（供重抓复用）：
  ```bash
  ssh k8s-master
  kubectl get deploy -n itp -o wide | awk '{print $1, $NF}'
  kubectl get svc -n itp -o wide
  kubectl get cm -n itp
  kubectl get deploy -n itp -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{range .spec.template.spec.containers[*].env[*]}  {.name}{"\n"}{end}{end}'
  ```

## ⚠️ 已知过期 / 注意项

- **快照会过期**：集群会滚更，本目录只在抓取时点准确。用它做「迁移替换矩阵」前 MUST 重抓。
- `env-keys.txt`（967 行）是本目录**最有长期价值**的一个：32 个 Deployment 的环境变量**键名全集
  （不含值）**，核对「红岛 env 是否注入齐全」（尤其 `SERVICE_*_URL` / `VLOGS_URL` 那个坑）时的唯一完整基线。
  详见 `docs/ops/environments/红岛.md`。
- `secrets.txt` 只有 **3 行、仅 Secret 名称**，**不含任何值**（2026-10-26 复核：曾误判为敏感快照，实际无害）。
- 原「生产迁移配置清单报告生成器」已删，本目录失去唯一消费者，现按**集群实况快照**独立保留。
