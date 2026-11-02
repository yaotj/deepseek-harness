# 应用日志落共享盘（红岛 itpb）

把 itpb 各 Java 服务容器的 `/home/javaapp/app/logs` 换成共享 PVC，使日志落在
`172.120.0.1:/ITP-PV/home/java/itpb-app-logs`，跳板机 `hd-linux` 挂载该卷即可直接读全部服务日志。

**状态（2026-10-28 16:50）**：21 个 Java 应用已切到 PVC `app-logs-v2`（静态 PV，落地目录
`/ITP-PV/home/java/itpb-app-logs`）并全部 `2/2` 就绪；跳板机已 fstab 持久化挂载 + 装好 `applogs`
命令，可直接使用。4 个应用按设计排除。历史日志（3.3G）已从旧目录迁入。

> **路径演进**：v1 由 StorageClass `itpb-yw` 动态供给，落地在
> `/ITP-PV/itpb-app-logs-pvc-96643eed-…`（`<ns>-<pvc>-<pv>` 模板）。因 PV 的
> `spec.persistentvolumesource` 创建后不可变（API 硬约束，`--dry-run=server` 已验），
> 改路径只能重建 PV，故改用**手工静态 PV** 把目录定成 `/ITP-PV/home/java/itpb-app-logs`。
> 旧 PVC `app-logs` + 旧 PV 原样保留作回滚，详见 §8。

仓库内文件：`applogs-pv-v2.yaml`（v2 静态 PV + PVC，当前在用）、`applogs-pvc.yaml`（v1 动态 PVC，
历史）、`mount-applogs.py`（批量挂载/切 claim）、`applogs.sh`（跳板机命令，改动后 `scp` 到
`/usr/local/bin/applogs` 覆盖）。

---

## 1. 为什么是共享盘，不是 otelcol sidecar

qd-itp 自带日志外发：`resource/micro/web` 里的 `VictoriaLogsAppender`（Log4j2 插件）+
`log4j2-linux.xml`，靠 env `VLOGS_URL` 指向集群内 VictoriaLogs（`victoria-be7vr-internal-svc.itpb.svc:9428`）。
所以「日志上服务器」早就有了，不需要再塞 otelcol sidecar（最初的参考方案已废弃）。

共享盘解决的是另一件事：**跳板机/运维要能直接 grep、tail、留档**，而不只是靠 VictoriaLogs 查询。

## 2. 存储

| 项 | 值 |
|---|---|
| PVC | `itpb/app-logs-v2`，400Gi，`ReadWriteMany`，**静态绑定**（`storageClassName: ""`） |
| PV | `app-logs-v2-pv`，`Retain`，手工创建 |
| 实际落地目录 | `/ITP-PV/home/java/itpb-app-logs/` |
| 底层 | NFS `172.120.0.1:/ITP-PV`，总量 1.1T |
| 用量（2026-10-28） | 4.9G；卷整体 ~35G / 1.1T |
| NFS 协议 | 节点侧实测 `vers=3`；跳板机**必须显式 `vers=3`**（`vers=4.1` 报 `Protocol not supported`） |

清单见 `applogs-pv-v2.yaml`。注意 PV/PVC 两边 `storageClassName` 都必须是空串，否则静态绑定不成立。

`itpb-nfs`（`/ItpApiGate`，550G，已用 91%）**不用于日志**。

### 2.1 为什么不用 StorageClass 动态供给

`nfs-subdir-external-provisioner:v4.0.0` 的目录名由模板生成（`<ns>-<pvc>-<pv>`，必然带 PV uid），
且 PV 的 `spec.persistentvolumesource` **创建后不可变**：

```
The PersistentVolume "pvc-96643eed-..." is invalid:
spec.persistentvolumesource: Forbidden: spec.persistentvolumesource is immutable after creation
```

想让落地目录可读、可控，只有手工静态 PV 一条路。若后续要让**新** PVC 也落在这个层级，
应由平台侧把 `itpb-yw` provisioner 的 `NFS_PATH` 从 `/ITP-PV` 改成 `/ITP-PV/home/java`
（影响该 SC 上所有租户，属平台决策，本目录未做）。

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
python3 docs/ops/environments/pvc-logship/mount-applogs.py para-server --pvc app-logs-v2
# 真下发（逐台滚动 + 轮询复验）
python3 docs/ops/environments/pvc-logship/mount-applogs.py para-server --pvc app-logs-v2 --yes
# 批量：空格分隔多个应用名即可，建议分批（每批 5~7 个，跑完看汇总再下一批）
```

`--pvc <名字>` 指定 claim，缺省 `app-logs`（v1）。对已挂载的应用，脚本会把已有 volume 的
`claimName` 就地改指，幂等。

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
4. **`pvcResource` 传 `{"pv":[],"pvc":["<claim>"]}`**（用已有 PVC，不动态建）。
5. 平台会自动归一化 `affinity` / `automountServiceAccountToken`，载荷里不必带。

### 3.2 挂载点怎么写

- volume：`{"name":"app-logs","persistentVolumeClaim":{"claimName":"app-logs-v2"}}`
- volumeMount：`{"name":"app-logs","mountPath":"/home/javaapp/app/logs"}`

volume 名沿用 `app-logs`（只是个标识，与 claim 名解耦），只有 `claimName` 指向 v2。

沿用原路径 `/home/javaapp/app/logs`，**不改任何代码/配置**：`web.properties` 里
`other.out.path=/home/javaapp/app/logs`、`other.logging.logPath=${other.out.path}` 原样生效，
日志落到 `${logPath}/${HOSTNAME}/${appName}.log` —— 按 Pod 名分目录，天然避免多副本互相覆盖。

## 4. 覆盖清单

已挂载（21，volume 名 `app-logs` → claim `app-logs-v2` + 挂载点 + `VLOGS_URL`）：

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

入口：`ssh hd-linux`（`~/.ssh/config` 里已配 `HostName 172.120.2.71` / `User root`），
或手动 `ssh root@172.120.2.71`。日志根在跳板机上是 `/home/applogs`
（软链 → `/mnt/itp-pv/home/java/itpb-app-logs`）。

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
applogs bak   acc-security          # 列按天归档 bak/*.log.gz（验证滚动是否生效）
applogs day   acc-security          # 某一天全天日志（默认昨天，见 §5.4）
applogs stale                       # 列出已无对应 Pod 的陈旧目录（只列，不删）
```

- **关键字 = Pod 目录名子串**（`account` / `para-server` / `acc-security` 都行）。
- 一览里的 `类型` 列很关键：`APP` 表示有真实业务日志 `*.log`，`GC` 表示**只有 JVM `gctrace.txt`**
  （应用没走完启动，见 §6）。
- `applogs stale` 只报告不删除——滚更后旧 Pod 目录会留下，删除属人工决定。

### 5.2 手动方式

```bash
ssh hd-linux
cd /home/applogs                       # = /mnt/itp-pv/home/java/itpb-app-logs/
ls | head                              # 按 Pod 名分目录
tail -f <pod名>/<app名>.log
```

- 目录名随 Pod 重建变化（ReplicaSet hash 变），找当前在跑的一批用
  `kubectl -n itpb get pods` 的 Pod 名比对，或用 `applogs`（自动按最新修改时间选）。
- 需要读写（例如人工清陈旧目录）：`mount -o remount,rw /mnt/itp-pv`，用完再
  `mount -o remount,ro /mnt/itp-pv`；**别长期留 rw**。
- 挂载掉了：`mount /mnt/itp-pv`（走 fstab，不用记参数）。

### 5.3 日志滚动与保留（已经是按天，别再改）

`log4j2-linux.xml` 的策略是**按天 + 按大小**双条件：

```xml
<RollingFile name="RollingFile" fileName="${logPath}/${appName}.log"
             filePattern="${logPath}/bak/%d{yyyy-MM-dd}-${appName}-%i.log.gz">
  <Policies>
    <TimeBasedTriggeringPolicy interval="1" modulate="true"/>   <!-- 每天 00:00 -->
    <SizeBasedTriggeringPolicy size="200 MB"/>                  <!-- 单日超 200MB 再拆 -->
  </Policies>
```

**当天日志固定写在 `<app>.log`（不带日期）**，只有滚过去的才进 `bak/` 并按
`<日期>-<app>-<N>.log.gz` 命名 —— 这是 Log4j2 `RollingFile` 的标准语义（`fileName` 不支持
动态日期，只在启动时求值一次）。所以「打开目录只看到一个 xxx.log、没有日期」**是正常的**，
不代表没按天滚。

实测归档规律（acc-security，2026-10-28 现查）：

| 文件 | 生成时间 | 触发原因 |
|---|---|---|
| `2026-10-26-acc-security-1.log.gz` | 10-27 00:05 | 跨天 |
| `2026-10-27-acc-security-1.log.gz` | 10-27 11:32 | 单日超 200MB |
| `2026-10-27-acc-security-2.log.gz` | 10-27 22:57 | 单日超 200MB |
| `2026-10-27-acc-security-3.log.gz` | 10-28 00:05 | 跨天 |
| `2026-10-28-acc-security-1.log.gz` | 10-28 11:38 | 单日超 200MB |

验证：`applogs bak <关键字>`；或等跨天后看 `bak/` 里是否多出 `<昨天日期>-*.log.gz`。

可调项（**改 env 即可，无需改代码发版**，走 `advanced-config`）：

| 配置项 | 现值 | 作用 |
|---|---|---|
| `other.logging.logBakDays` | `30` | 保留天数；同时决定 `max`（= 天数 × 24 = 720 个归档上限） |
| `other.logging.level` | `INFO` | 本地文件/控制台阈值（VictoriaLogs 侧另有 DEBUG 规则） |

**真不按天的是 `gctrace.txt`** —— 那是基础镜像 `/startapp.sh` 里 JVM 的
`-Xlog:gc*:file=${out_dir_bak}gctrace.txt:…:filecount=5,filesize=20m`，按 20MB × 5 个滚，
与 log4j2 无关（JDK 的 `-Xlog` 只支持按大小，不支持按时间）。它固定约占 100MB/Pod，可接受；
嫌占地方得改镜像启动脚本，属另一件事。

### 5.4 看历史某一天（昨天的日志）

**当日活文件里只有当日内容**，昨天及更早的全部在 `bak/<日期>-<app>-<N>.log.gz`。
归档名的日期 = **内容所属那天**（`filePattern` 的 `%d` 是滚动时刻，正好标在前一段内容上），
所以 `2026-10-27-*.log.gz` 装的就是 10-27 00:00:00 ~ 23:59:59（实测首末行已验证）。

```bash
applogs day acc-security                    # 昨天全天
applogs day acc-security 2026-10-26         # 指定某天
applogs day acc-security | less             # 管道输出全量
applogs day acc-security | grep -c ERROR    # 只数错误条数
applogs day acc-security | grep -A5 "NullPointer"
```

行为约定：

- **stdout 只放日志原文**（提示走 stderr），可直接接 `less` / `grep` / `> file`。
- **终端里直接跑只显示最后 200 行**（acc-security 一天约 200 万行，全量刷屏没法看）；
  一旦接管道或重定向就自动输出**全量**。
- 会把该关键字下**所有 Pod 目录**的同日分片按 `-1→-2→-3` 顺序拼接（`ls -v` 版本序，
  不会让 `-10` 排在 `-2` 前面），每个 Pod 在 stderr 打一行 `== <pod> [日期] 分片数/大小`。

两个注意点：

1. **一个应用 2 副本 = 2 份不同内容**（不是互相重复），要看全两个都得看，脚本已合并。
2. **别用 `find /home/applogs …`**：`/home/applogs` 是软链，`find` 默认不跟随符号链接，
   会静默返回空（`exit 0` 但无输出，极易误判成"文件不存在"）。要么 `find -L`，
   要么直接用 `/mnt/itp-pv/home/java/itpb-app-logs`。

### 5.5 某天到底是哪个容器写的（Pod 名可追溯）

日志按 **Pod 名**分目录（`${HOSTNAME}`），滚更/重启就换新目录，所以「这一天是谁写的」得靠推导。
反推链是固定的，**Pod 名自带部署批次**：

```
acc-security-server-7d5c78847c-vx7j6
└──── Deployment ──┘ └RS hash┘ └随机┘
   去掉末段随机后缀 = ReplicaSet 名 = 同一次部署（同镜像、同配置）
```

```bash
applogs who acc-security                    # 昨天是哪些 Pod 写的
applogs who acc-security 2026-10-28         # 指定某天
```

实测输出（2026-10-28，本机切换 PVC 当天）：

```
关键字: acc-security    日期: 2026-10-28
Pod 目录                                   状态  分片  覆盖时段        压缩 镜像tag
acc-security-server-5b47f79f5d-6vmvn         在跑      15  16:42:22 ~ 18:15:40    76M 2.0.17
acc-security-server-5b47f79f5d-pr7vb         在跑      16  16:42:31 ~ 18:15:38    80M 2.0.17
acc-security-server-7d5c78847c-vx7j6         已下线      1  00:00:00 ~ 11:33:28   7.7M 2.0.17
acc-security-server-7d5c78847c-wtjnq         已下线      1  00:00:00 ~ 11:26:17   7.7M 2.0.17
```

一眼能读出三件事：**旧 RS 两个副本在 11:26~11:33 停写，新 RS 两个副本 16:42 才起**（11:26~16:42
是 IT-023 应用挂起期，中间确实没有日志）；**镜像 tag 相同说明这次是改配置滚更、没换镜像**；
**每个时段都由 2 个副本并行写**（不缺副本）。

实现口径：

- **覆盖时段** = 该日首个分片的第一个时间戳 ~ 末个分片的最后一个时间戳。异常堆栈续行没有
  时间戳前缀，所以取"带时间戳的最后一行"而不是字面末行。
- **镜像 tag** 从 RS 的 pod template 取（`kubectl get rs <rs> -o jsonpath=...containers[0].image`）。
  kubectl 不可用、或该 RS 已被 GC（`revisionHistoryLimit=10`）时显示 `?`。
- 耗时约 3~4 秒（要解压末分片找末尾时间戳、再查 RS），所以别放进循环里频繁调。

**局限**：这条链靠"旧 Pod 目录还在"来反推。目录被人手动删掉、或归档过了 `logBakDays`
就查不到了。要彻底解决得在应用启动时写一行索引（Pod 名 + 启动时间 + 镜像），
但那要改 `CustomLoggingConfiguration` 并给 21 个应用重新发版，当前没必要。

### 5.6 为什么不按日期建目录

直觉上 `/logs/2026-10-27/xxx.log` 更直观，但**做不到安全**：同一天多个 Pod 同时往**同一个文件**
追加，NFS 上多客户端并发写同一文件的偏移量协调不可靠，会互相覆盖/截断。所以必须
**一个 Pod 一个目录**（`${HOSTNAME}` 隔离），日期维度只能靠文件名（`bak/<日期>-...`）
和 `applogs who/day` 这类查询来还原。改目录结构不改这一点，只是把问题挪个地方。

### 5.7 写入可靠性：NFS 抖动不能拖垮业务

日志是旁路 IO，**不该有权力拖垮业务**。这里原本有一条真实的隐患链：

```
业务线程 → logger.info() → log4j2 RollingFile(同步) → 写 NFS → 返回
```

而 K8s 默认给的 NFS 挂载是 `hard,timeo=600,retrans=2`（实测确认）——`hard` 语义是
**无限重试、永不返回错误**。也就是说 NFS 一旦无响应，**所有写日志的业务线程会永久卡住**，
请求堆积、连接打满、服务假死。日志盘抖动演变成业务事故。

修法是两层，互补而非重复：

**第一层：PV 挂载改 `soft`（防写盘线程卡死）**

```yaml
mountOptions: [nfsvers=3, soft, timeo=30, retrans=2]   # timeo 单位 0.1s → 3s×2 次 ≈ 6s 后放弃
```

`spec.mountOptions` 是**可变字段**（`persistentvolumesource` 才不可变），`kubectl patch pv` 直接生效；
但挂载参数在**Pod 创建时定型**，必须重建 Pod 才生效。验证：Pod 内 `cat /proc/mounts` 应出现 `soft,timeo=30`。

**第二层：log4j2 套 `AsyncAppender`（防业务线程等 IO）**

```xml
<Async name="AsyncRollingFile" bufferSize="16384" blocking="false"
       includeLocation="true" shutdownTimeout="3000" errorRef="Console">
    <AppenderRef ref="RollingFile" />
</Async>
```

四个属性都有明确目的，改之前先读懂：

| 属性 | 值 | 理由 |
|---|---|---|
| `blocking` | `false` | 队列满时**丢弃**新事件而不是反压调用方（默认 `true` 会阻塞业务线程，等于没异步） |
| `includeLocation` | `true` | pattern 里有 `%l`；异步后 location 只能靠入队时捕获，关掉会全变 `?` |
| `shutdownTimeout` | `3000` | SIGTERM 优雅退出时最多等 3s 排空队列，防止滚动重启丢尾部日志 |
| `errorRef` | `Console` | 队列满/异步内部异常时把事件打回 Console，不静默吞掉 |

两个容易被忽略的点：

- **性能不会变差**：`%l` 的抓栈本来就在调用线程做（同步模式同样开销），异步只是把写盘挪走。
- **`NEVER` 用 Async 包 `VictoriaLogs`**：该 appender 自带队列与刷新线程（`queueCapacity`/`flushIntervalMillis`），再包一层等于双重缓冲。
- log4j2 2.21.1 的 `AsyncAppender` **没有** `AsyncQueueFullPolicy` 属性（那是 AsyncLogger 的），别照抄网上配置。

本地验证方法（改配置后必做，`xmllint` 只能证明良构，不能证明能用）：用 log4j-core 直接加载该
xml 打几条日志，确认①落盘成功 ②`%l` 不是 `?` ③traceId 保留 ④`getAppender("AsyncRollingFile")`
返回 `AsyncAppender`（证明没被忽略退化成直连）。注意测试 classpath 要带上
`log4j-layout-template-json`，否则 VictoriaLogs appender 会报 `requires a Layout`（属测试环境缺失，非配置错误）。

**上线后判断"日志到底有没有在写"，`NEVER` 只看跳板机的 `stat`/`ls`**（2026-10-29 踩过，代价是一次
误判回滚）：NFS 客户端对**正在写入的打开文件**有属性缓存，跳板机侧 `stat` 会长时间返回 `0` 或旧大小，
而文件实际已有上百 MB。可靠判据是**在容器内看**：

```bash
kubectl -n itpb exec <pod> -c <container> -- ls -l /home/javaapp/app/logs/$HOSTNAME/<app>.log
```

或读内容（`head`/`tail`/`wc -l` 走数据路径，不受大小缓存影响）。跳板机侧缓过来通常要等属性缓存过期，
期间 `applogs tail` 对新 Pod 也会显示空，**不代表日志丢了**。


## 6. 「类型」列与启动状态的关系（排查必看）

`applogs` 一览里的 `GC` 表示该 Pod 目录**只有 JVM `gctrace.txt`、没有业务 `*.log`**。
原因是应用没走到 `ApplicationReadyEvent`：

- `CustomLoggingConfiguration` 只在 `ApplicationReadyEvent` 触发，之后才加载 `log4j2-linux.xml`；
- 没加载 → 不产 `*.log`，也不往 VictoriaLogs 外发；
- 此时唯一写盘的是基础镜像 `/startapp.sh` 的 `-Xlog:gc*:file=${out_dir_bak}gctrace.txt`
  （`out_dir_bak=/home/javaapp/app/logs/${HOSTNAME}/`，`filecount=5,filesize=20m`，上限约 100MB/Pod）。

**所以「某应用日志没落盘」先查它有没有在监听、有没有走到 Ready，别误判成日志配置缺失。**
判据（跳板机可直接跑）：

```bash
kubectl -n itpb get pods                       # 2/2 不一定等于应用活着（这些 Deployment 无 readinessProbe）
/tmp/chk2.sh <app>                             # 读 /proc/net/tcp{,6} 的 LISTEN，看应用端口在不在
```

历史情况（2026-10-28 上午）：21 个应用只有 3 个在监听 8080，18 个挂在 Spring refresh 之前，
根因是 IT-023（红岛 DB 认证被 RST）。**同日 16:50 切换 PVC 触发的滚动重启后已好转：
19/21 在监听 8080，仅 `fep-dev-server`、`recon-server` 仍未起**（需另行跟进，与日志盘无关）。

容量结论：gc trace 有上限（5×20MB/Pod），不随运行时间无界增长；业务日志按 `logBakDays=30`
增长（21 应用 × 2 副本，估算数十 GB 量级），400G 够用，届时按需调 `logBakDays`。

顺带：`server.tomcat.basedir=${other.out.path}` 会让 `work/Tomcat`、`tomcat-docbase.8080.*`
也落在共享盘，占位很小（空目录为主），可接受；若要彻底分离，改 `other.logging.logPath`
而不动 `other.out.path`（属代码改动，需另行发版）。

## 7. 回滚

**只回滚路径（v2 → v1，最常用）**：21 个应用把 `claimName` 改回 `app-logs` 即可，数据不用动
（v1 目录原样保留）：

```bash
python3 docs/ops/environments/pvc-logship/mount-applogs.py <应用...> --pvc app-logs --yes
```

**回滚挂载本身**：用同一接口把 `pvcResource` 置空、从 `resources[1]` 里去掉 `app-logs`
volume 与挂载点，版本末位再 +1 下发。批量回滚同理遍历应用。

两个 PV 都是 `Retain`，删 PVC 不删数据；v1 的 provisioner 带 `archiveOnDelete: "true"`，
目录会留成 `archived-*`。

### 7.1 v1 残留怎么收

v1 的 PVC `app-logs` + PV `pvc-96643eed-…` 现在**没有任何 Pod 引用**，纯作回滚点保留。
确认 v2 跑稳（建议 ≥1 周）后可清理，顺序必须是先 PVC 后 PV，否则 PV 进 `Released` 还得手工清
`claimRef`：

```bash
kubectl -n itpb delete pvc app-logs
kubectl delete pv pvc-96643eed-d93a-4f12-9229-6f15bbbf939e
```

删完目录仍留在 `/ITP-PV/itpb-app-logs-pvc-96643eed-…`（`Retain` 语义），要回收空间再人工删目录。

## 8. 变更记录

| 日期 | 内容 |
|---|---|
| 2026-10-26 | v1：PVC `app-logs`（SC `itpb-yw` 动态供给）挂 21 个应用，落地 `/ITP-PV/itpb-app-logs-pvc-96643eed-…` |
| 2026-10-28 15:40 | 跳板机持久化挂载（fstab）+ `applogs` 命令 |
| 2026-10-28 16:50 | v2：静态 PV + PVC `app-logs-v2`，落地改 `/ITP-PV/home/java/itpb-app-logs`；3.3G 历史日志迁入；21 应用分批切 claim，全部 `2/2`；v1 保留作回滚 |
| 2026-10-28 19:00 | PV 挂载改 `soft,timeo=30,retrans=2`（原默认 `hard,timeo=600`，NFS 无响应会永久卡住写日志线程）；`log4j2-linux.xml` 主日志改走 `AsyncAppender`（`blocking=false` 队列满即丢弃、`includeLocation=true` 保住 `%l`）。挂载已生效于重建后的 Pod，**log4j2 改动待发版**。 |
