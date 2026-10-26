# IT-017 红岛 itpb 集群 kube-apiserver 三台全部退出，平台 24 个应用集体 Error

> 状态：处理中（**根因未定**，卡在无法登录控制节点）｜发现：2026-10-23 15:24｜环境：红岛生产

## 一、背景

2026-10-23 15:04 起本机 `en9`（USB 网卡）`inactive`，红岛链路中断约 20 分钟。15:24 网络恢复后，`kubectl` 立刻失败：

```
The connection to the server 172.120.2.11:6443 was refused - did you specify the right host or port?
```

云管台（`https://172.120.209.151`，**自身完全正常**，登录与业务 API 均 1s 内返回）把集群 `kubernetes.itpb` 标为 `status=exception`，24 个应用全部 `Error` / 实例 `0/0`。

## 二、根因（未定，已排除若干方向）

**已确认事实**（2026-10-23 15:24–15:40，`--noproxy` 直连实测）：

| 组件 | 端口 | `172.120.2.11` | `.12` | `.13` |
|---|---|---|---|---|
| kube-apiserver | 6443 | **refused** | **refused** | **refused** |
| etcd | 2379 | 监听 | 监听 | 监听 |
| kubelet | 10250 | 监听（HTTP 401，需认证） | 监听 | 监听 |
| controller-manager | 10259 | 监听 | **未监听** | 监听 |
| sshd | 22 | 通 | 通 | 通 |

- **refused 而非 timeout** ⇒ 不是防火墙、不是网络，是**端口上没有进程**。
- 三台控制节点 `sshd`/`etcd`/`kubelet` 都活着 ⇒ **不是节点宕机**，是 apiserver 进程（静态 Pod）集体退出。
- 可观测栈 `172.120.2.14` 的 `30001`(kiali) / `30003`(grafana) / `31118`(prometheus) 全不通，但该节点 ping 与 `:22` 正常。
- 云管台 REST 全部可用 ⇒ **CloudOS 管理面与 itpb 业务集群相互独立**，管理面没被拖垮。

**已排除**：
- 网络中断导致的误判 —— 网络恢复后仍 refused，且同网段 etcd/kubelet 可达。
- CloudOS 平台故障 —— 云管台 API 全部正常。
- etcd 数据丢失 —— 三台 etcd 端口均在监听（尚未验证 quorum 与健康状态，需 etcdctl）。

**候选根因**（按概率，需节点日志确认）：

1. **apiserver 证书过期**：三台用同一批证书、同期过期，kubelet 反复重启静态 Pod 失败 ⇒ 完全符合「三台同时挂、kubelet 活着」的形态。判据：`/etc/kubernetes/pki/apiserver.crt` 的有效期。
2. **静态 Pod manifest 被改 / 删除**：`/etc/kubernetes/manifests/kube-apiserver.yaml`。
3. **etcd 短时不可用导致 apiserver 起不来**：静态 Pod 会无限重试，正常情况下应自愈；若持续不恢复，需看 apiserver 容器日志里的 etcd 连接错误。
4. 有人做过控制平面重启 / 升级（K8s 版本 1.28.6）。

**阻塞点**：控制节点 `root` 密码认证**挂起**（`sshd` 已提示 password，发送凭据后 15s/90s 均无任何响应，既不拒绝也不接受）⇒ PAM 认证后端不可用，可能是 LDAP/AD 或 NSS 依赖的外部服务同时挂了。本机与 `hd-linux` 对该三台**均无免密**，且 `hd-linux` 未装 `sshpass`/`expect`。

## 三、方案对比

| 方案 | 做法 | 前提 / 风险 |
|---|---|---|
| A. 修证书后重启 kubelet | 换掉过期证书 → `systemctl restart kubelet` 由静态 Pod 拉起 apiserver | **首选**。前提是先确认是证书过期；换证书需 kubeadm 证书链一致 |
| B. 直接重启 kubelet | `systemctl restart kubelet`，观察 apiserver 容器是否重建 | 便宜、无副作用，但**根因未明时可能重启后又挂**，白重启一次生产控制平面 |
| C. 重建控制平面 | `kubeadm init --phase` 系列 | 最后手段，风险最高，需 etcd 备份 |

**先 A 后 B**：先取证书有效期与 kubelet 日志，确认根因再动手，避免盲重启掩盖证据。

## 四、决策

- **暂停一切写操作**：规格调整（4C/8G）、副本数调整等 REST 写操作全部搁置 —— apiserver 不可用时平台侧必然失败，且失败态会污染应用状态。
- 需现场配合项：
  1. 确认 2026-10-23 14:30–15:30 是否有人对控制节点做过重启 / 升级 / 证书操作；
  2. 提供可用的控制节点登录方式（当前密码认证挂起）；
  3. 确认集群是否接入外部认证（LDAP/AD），为何 PAM 挂起。

## 五、验证与回滚

恢复判据（缺一不可）：

```bash
curl -sk --noproxy '*' https://172.120.2.11:6443/healthz     # 期望 ok
kubectl get nodes                                            # 期望 24 台 Ready
kubectl get pods -n itpb | grep -v Running                   # 期望无输出
```

业务恢复后需复核：
- 云管台 `apps` 命令 24 个应用回到 `OK` 且 `INST=2/2`；
- 48 个 Pod 全部 Running 且**两副本跨节点**（判据见 `红岛.md`「云管台 REST」节）。

回滚：证书操作前 MUST 备份 `/etc/kubernetes/pki/` 整目录与 etcd 快照；kubelet 重启本身无需回滚（静态 Pod 自愈）。

## 六、流程符合度自评

- 生产环境：✅ 全程只读探测，未执行任何写操作；写请求（规格变更）已主动叫停。
- 文档回写：✅ 本节事实回写 `红岛.md`「云管台 REST」节（诊断命令与组件端口矩阵）。
- 问题登记：✅ 索引 IT-017。
- **未做**：根因未定位（受限于无法登录节点），故「根因」节只给候选与判据，**NEVER** 当作结论引用。
