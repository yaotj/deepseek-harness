# 应用日志落共享盘（红岛 itpb）

把 itpb 各 Java 服务容器的 `/home/javaapp/app/logs` 换成共享 PVC，使日志落在
`172.120.0.1:/ITP-PV`，跳板机 `hd-linux` 挂载该卷即可直接读全部服务日志。

**状态（2026-10-28 复核）**：21 个 Java 应用已挂载并稳定运行 48h，平台未回收；
跳板机 `hd-linux` 已持久化挂载（fstab）+ 装好 `applogs` 快捷命令，可直接使用。
4 个应用按设计排除。**但盘上目前几乎只有 JVM `gctrace.txt`，业务日志只有 3 个服务有**
——原因见 §6，与挂载无关。

仓库内文件：`applogs-pvc.yaml`（PVC 清单）、`mount-applogs.py`（批量挂载）、
`applogs.sh`（跳板机命令，改动后 `scp` 到 `/usr/local/bin/applogs` 覆盖）。

---

## 1. 为什么是共享盘，不是 otelcol sidecar

qd-itp 自带日志外发：`resource/micro/web` 里的 `VictoriaLogsAppender`（Log4j2 插件）+
`log4j2-linux.xml`，靠 env `VLOGS_URL` 指向集群内 VictoriaLogs（`victoria-be7vr-internal-svc.itpb.svc:9428`）。
所以「日志上服务器」早就有了，不需要再塞 otelcol sidecar（最初的参考方案已废弃）。

共享盘解决的是另一件事：**跳板机/运维要能直接 grep、tail、留档**，而不只是靠 VictoriaLogs 查询。

## 2. 存储

| 项 | 值 |
|---|---|
| PVC | `itpb/app-logs`，400Gi，`ReadWriteMany` |
| StorageClass | `itpb-yw`（→ NFS `172.120.0.1:/ITP-PV`，总量 1.1T） |
| 实际落地目录 | `/ITP-PV/itpb-app-logs-pvc-96643eed-d93a-4f12-9229-6f15bbbf939e/` |
| 用量（2026-10-28） | 3.2G / 400G；卷整体 31G / 1.1T（3%） |
| NFS 协议 | **必须 `vers=3`**（`vers=4.1` 报 `Protocol not supported`） |

`itpb-nfs`（`/ItpApiGate`，550G，已用 91%）**不用于日志**。

清单见 `applogs-pvc.yaml`。

## 3. 挂载方式：只能走云平台 `advanced-config`

`kubectl patch` 改出来的东西会被平台数分钟内按自身存储的 metadata 回收（IT-020 实测）。
唯一可持续写路径：

```
PATCH /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/advanced-config
```

脚本：`mount-applogs.py`（预演默认，`--yes` 才下发）。

```bash
# 看清单与当前挂载态
python3 docs/ops/environments/pvc-logship/mount-applogs.py --list
# 预演（生成 payload 到临时目录，不下发）
python3 docs/ops/environments/pvc-logship/mount-applogs.py para-server
# 真下发（逐台滚动 + 轮询复验）
python3 docs/ops/environments/pvc-logship/mount-applogs.py para-server --yes
```

### 3.1 载荷五条硬约束（踩过的坑，改脚本前必读）

1. **`env` 是全量替换，不是合并。** 少给一项 = 平台上该项被永久删除 = Pod 少一个环境变量。
   - 唯一来源 MUST 是 `metadata.env`（平台存储的完整配置项），**NEVER** 用 kubectl 读回来的容器 env。
   - 2026-10-26 试点即因此丢掉 `VLOGS_URL`，导致该服务停止向 VictoriaLogs 外发；
     因 `VictoriaLogsAppender` 在 url 为空时静默自禁用，**没有任何报错**。
     补救用 `--restore-vlogs`（itpb 21 个应用该值统一，无二值）。
2. **`resources[1]` 必须是完整 Deployment**（`containers[]` / `volumes[]` / `volumeMounts[]`）。
   平台无视自己存的旧模板，照抄会把副本数、资源回退（IT-020）。脚本按 kubectl 现网对象重建，
   去掉 server-side 字段（`status` / `managedFields` / `resourceVersion` / `nodeName` 等）。
3. **`version` 第 4 段 +1**，`version_id` 用应用当前 `upgrade_id`，`container_image` MUST 传
   （缺则 `application.5000010`），且本次不改镜像 → 版本号变、镜像不变。
4. **`pvcResource` 传 `{"pv":[],"pvc":["app-logs"]}`**（用已有 PVC，不动态建）。
5. 平台会自动归一化 `affinity` / `automountServiceAccountToken`，载荷里不必带。

### 3.2 挂载点怎么写

- volume：`{"name":"app-logs","persistentVolumeClaim":{"claimName":"app-logs"}}`
- volumeMount：`{"name":"app-logs","mountPath":"/home/javaapp/app/logs"}`

沿用原路径 `/home/javaapp/app/logs`，**不改任何代码/配置**：`web.properties` 里
`other.out.path=/home/javaapp/app/logs`、`other.logging.logPath=${other.out.path}` 原样生效，
日志落到 `${logPath}/${HOSTNAME}/${appName}.log` —— 按 Pod 名分目录，天然避免多副本互相覆盖。

## 4. 覆盖清单

已挂载（21，均有 `app-logs` volume + 挂载点 + `VLOGS_URL`）：

acc-es-server / acc-security-server / account-server / alipay-account-server /
alipay-pay-sign-server / blacklist-server / card-pool-server / daily-ticket-server /
face-pay-server / fep-acc-server / fep-alipay-server / fep-app / fep-dev-server /
gate-txn-pay-server / industry-data-server / key-server / para-server / pay-sign-server /
recon-server / ticket-server / trans-query-server

排除（4，理由在脚本 `EXCLUDE` 里同步维护）：

| 应用 | 理由 |
|---|---|
| `victoria` | 它本身就是日志服务器，不能自指 |
| `web-frontend` | nginx 静态站点，不写这个目录 |
| `route-sentinel` | logback 打 stdout，不落这个目录 |
| `web-admin` | RuoYi/logback，落盘路径不是这个（待定，如需纳管先确认其 logback 配置） |

## 5. 跳板机读日志（已持久化，2026-10-28）

挂载已写入 `hd-linux` 的 `/etc/fstab`，重启不丢；`remote-fs.target` 为 `enabled`。

```
172.120.0.1:/ITP-PV  /mnt/itp-pv  nfs  ro,vers=3,soft,timeo=100,retrans=3,_netdev,nofail,noatime  0 0
```

选型说明：
- `ro` —— 只读，防止误删生产日志（应用侧按 `logBakDays=30` 自然轮转，无需人工删）。
- `vers=3` —— **必须**；该 NFS 后端不支持 4.1（报 `Protocol not supported`）。
- `soft,timeo=100,retrans=3` —— 后端异常时返回 EIO 而不是让 shell/tar 永久挂死；运维机上"卡住"比"报错重试"更难受。
- `_netdev,nofail` —— 网络就绪后再挂；挂不上也不阻塞开机（不会被日志盘拖成救援模式）。
- 备份：`/etc/fstab.bak-20261028`。

### 5.1 用 `applogs` 命令（推荐）

脚本已装到跳板机 `/usr/local/bin/applogs`，同时建了软链 `/home/applogs` 指向日志根。

```bash
applogs                            # 一览：Pod 目录 / 类型 / 大小 / 最近更新
applogs path  acc-security          # 打印目录，可直接 cd
applogs tail  pay-sign 200          # 看最新日志尾部（多副本会各打一份）
applogs grep  acc-security "ERROR|Exception"
applogs list  para-server           # 列目录内文件
applogs stale                       # 列出已无对应 Pod 的陈旧目录（只列，不删）
```

- **关键字 = Pod 目录名子串**（`account` / `para-server` / `acc-security` 都行）。
- 一览里的 `类型` 列很关键：`APP` 表示有真实业务日志 `*.log`，`GC` 表示**只有 JVM `gctrace.txt`**
  （应用没走完启动，见 §6）。
- `applogs stale` 只报告不删除——滚更后旧 Pod 目录会留下，删除属人工决定。

### 5.2 手动方式

```bash
ssh hd-linux
cd /home/applogs                       # = /mnt/itp-pv/itpb-app-logs-pvc-96643eed-.../
ls | head                              # 按 Pod 名分目录
tail -f <pod名>/<app名>.log
```

- 目录名随 Pod 重建变化（ReplicaSet hash 变），找当前在跑的一批用
  `kubectl -n itpb get pods` 的 Pod 名比对，或用 `applogs`（自动按最新修改时间选）。
- 需要读写（例如人工清陈旧目录）：`mount -o remount,rw /mnt/itp-pv`，用完再
  `mount -o remount,ro /mnt/itp-pv`；**别长期留 rw**。
- 挂载掉了：`mount /mnt/itp-pv`（走 fstab，不用记参数）。


## 6. 已知缺口（挂载之外的问题，别误判成挂载的锅）

**红岛 21 个 Java 应用里只有 3 个真正在监听 8080**（`trans-query-server` / `fep-app` /
`acc-security-server`），其余 18 个进程挂在 Spring 容器 refresh 之前，只靠 istio-proxy 端口活着
（`kubectl get endpoints` 仍有地址，是**假就绪**——没有 readinessProbe 兜底）。
根因是 IT-023（红岛 DB 认证被 RST），本目录的挂载只是把后果变得可见：

- 应用没走到 `ApplicationReadyEvent` → `CustomLoggingConfiguration` 不触发 →
  `log4j2-linux.xml` 不加载 → **不产生 `*.log`**，也不往 VictoriaLogs 外发。
- 唯一还在写盘的是基础镜像 `/startapp.sh` 的 `-Xlog:gc*:file=${out_dir_bak}gctrace.txt`
  （`out_dir_bak=/home/javaapp/app/logs/${HOSTNAME}/`，`filecount=5,filesize=20m`）。
- 所以现在每个 Pod 目录 ≈ 58–120MB，**全是 gc trace**；两个在跑业务的 Pod 才有真实 `*.log`
  （`acc-security.log` 64MB / `pay-sign.log` 3MB 级别）。

容量结论：gc trace 有上限（5×20MB/Pod ≈ 100MB），不随运行时间无界增长；
真正要盯的是 IT-023 解开后业务日志按 `logBakDays=30` 增长（估算 21 应用 × 2 副本 ≈ 数十 GB 量级），
400G 够用，届时按需调 `logBakDays`。

顺带：`server.tomcat.basedir=${other.out.path}` 会让 `work/Tomcat`、`tomcat-docbase.8080.*`
也落在共享盘，占位很小（空目录为主），可接受；若要彻底分离，改 `other.logging.logPath`
而不动 `other.out.path`（属代码改动，需另行发版）。

## 7. 回滚

单个应用回滚＝用同一接口把 `pvcResource` 置空、从 `resources[1]` 里去掉 `app-logs`
volume 与挂载点，版本末位再 +1 下发。批量回滚同理遍历应用。

PVC 本身 `Retain`，删 PVC 不删数据；`itpb-yw` 的 provisioner 带 `archiveOnDelete: "true"`，
目录会留成 `archived-*`。
