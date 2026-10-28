# IT-031 红岛镜像自写 ENTRYPOINT 绕过 `/startapp.sh`，JVM 与日志契约整体失效

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-031` 一行（重档）。
> 本文所有数字均为 2026-10-28 红岛现查，二次引用前 MUST 重跑取证命令。

## 1. 背景 / 现象
- 发现时间：2026-10-28（本轮「用应用仓库的 jar 在镜像仓库批量出镜像」动手前，
  先核红岛运行期 JVM 契约时暴露）。
- 发现渠道：自查——为新增的共享构建模板
  [hongdao-buildx.Dockerfile](../ops/environments/scripts/hongdao-buildx.Dockerfile)
  定「写不写 `ENTRYPOINT`」这条，需要先在集群里证伪/证实基础镜像 `/startapp.sh` 的作用。
- 影响范围：红岛 itpb 在跑的 **4 个镜像 / 2 个应用**——
  `itpb/fep-dev-server:2.0.71`、`:2.0.72`（现跑 `2.0.72`，见
  [IT-028](IT-028-红岛fep-dev-server缺DB配置卡启动.md)）与
  `itpb/route-sentinel:1.0.1`、`:1.0.2`（现跑 `1.0.1`，见 [IT-021](IT-021-红岛网关路由表缺失.md)）。
  其余 21 个 Java 服务的平台镜像不受影响（构建时未自写 `ENTRYPOINT`）。
- 现象描述：这两个 Pod 的 1 号进程是 `java -jar /app.jar`，而正常服务是 `sh /startapp.sh`；
  前者的 JVM **一个调优参数都没有**。

## 2. 根因分析

红岛基础镜像 `itpb/jdk21:1.0.0` 把「运行期契约」全部实现在镜像自带的 `/startapp.sh` 里
（容器 `ENTRYPOINT` 指向它）：

```
堆 = max_memory - reserved_memory - max_direct_memory   → -Xmx5900m / -Xms5900m
-XX:MaxDirectMemorySize=100m
-XX:+HeapDumpOnOutOfMemoryError  -XX:HeapDumpPath=/home/javaapp/app/logs/${HOSTNAME}/java.dump
-Xlog:gc*=trace:file=/home/javaapp/app/logs/${HOSTNAME}/gctrace.txt:uptimemillis,pid:filecount=5,filesize=20m
-Djava.io.tmpdir=/home/javaapp/app/logs
GC!=G1 时切 ZGC；可选 --spring.config.location=/config/application.properties
```

一旦某模块的 `Dockerfile` 末尾自己写 `ENTRYPOINT ["java", "-jar", "/app.jar"]`，
Docker 语义下**子镜像的 `ENTRYPOINT` 直接覆盖基础镜像的 `ENTRYPOINT`**，`/startapp.sh` 根本不被执行，
上面整段随之旁路。仓库里恰好有两个这样的文件（`find` 全仓，共 3 个 Dockerfile）：

| Dockerfile | FROM | 结尾 |
|---|---|---|
| `fep-dev-server/Dockerfile` | `itpb/jdk21:1.0.0` | `ENTRYPOINT ["java", "-jar", "/app.jar"]` ← 旁路 |
| `route-sentinel/Dockerfile`（兄弟工程 `qd/route-sentinel`） | `itpb/jdk21:1.0.0` | `ENTRYPOINT ["java", "-jar", "/app.jar"]` ← 旁路 |
| `docker/Dockerfile`（仓库基线，测试环境用） | `itp/jdk:21` | 无 `ENTRYPOINT` / `CMD` ✅ |

**实测对照（2026-10-28，`kubectl -n itpb exec` + `jcmd`）**：

| Pod | PID1 | 关键 JVM 参数 | 实测堆上限 | 容器 memory limit |
|---|---|---|---|---|
| `acc-es-server-6b449d945f-62ls7`（正常） | `sh /startapp.sh` | `-Xmx5900m -Xms5900m -XX:MaxDirectMemorySize=100m -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc*=trace:...`（`/proc/<javaPid>/cmdline`） | 5900 m（契约值，来自命令行） | 8 Gi |
| `fep-dev-server-7cc7c85d49-cg9pc` | `java -jar /app.jar` | 无 | `jcmd 1 VM.flags` → `-XX:MaxHeapSize=2147483648` = **2 GiB**（8 Gi 的 1/4，JVM 默认） | 8 Gi |
| `route-sentinel-5c6b4cfb67-8lbdv` | `java -jar /app.jar` | 无 | `jcmd 1 VM.flags` → `-XX:MaxHeapSize=2147483648` = **2 GiB** | 8 Gi |

即这两个服务的可用堆只有契约值的 **约 1/3（2 GiB vs 5900 m）**，且：

1. **无 GC 日志**——`fep-dev-server` 自己在 `/home/javaapp/app/logs/${HOSTNAME}/` 下**没有 `gctrace.txt`**，
   而同集群 startapp.sh 形态的 `acc-es-server` 同位置有 3 个（`16.8 MB` + 两个 `20.9 MB` 滚动件）；
   排障时拿不到 GC 轨迹。
2. **无 OOM heapdump**——`-XX:+HeapDumpOnOutOfMemoryError` / `HeapDumpPath` 一并丢失，
   OOM 时不留现场。
3. **无 `-XX:MaxDirectMemorySize=100m`、无 `-Djava.io.tmpdir=/home/javaapp/app/logs`**
   ——直接内存不受约、临时目录退回容器 `/tmp`（不在共享日志卷上）。
4. **`max_memory` / `reserved_memory` / `max_direct_memory` 三个平台 env 变成死配置**——
   `fep-dev-server` Deployment 里这三个 env 存在但无人读取（`route-sentinel` 干脆没配）。
   这条同时解释了 [IT-020](IT-020-JVM参数被平台回滚.md) 时期「改了 env 却看不到效果」的另一半成因：
   即便 env 持久化成功，对自写 `ENTRYPOINT` 的镜像也一律无效。

**必须分清的一件事（本单初稿曾写错，已更正）**：应用 `<app>.log` **不受影响**——
`fep-dev.log`（171 MB）就在该 Pod 目录下正常落盘，目录是 log4j2 自己按 `${HOSTNAME}` 建的，
`pvc-logship` 的 `applogs.sh` 照样采得到；旁路只打掉 **JVM 自己那三项**（GC 日志 / heapdump / 堆与直接内存约）。
`route-sentinel` 目录下连 `.log` 都没有是**另一件事**：该模块用
`logback-spring.xml`（不是 `log4j2-linux.xml`），本来就没有文件 appender，与 `ENTRYPOINT` 无关。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A（选定） | 构建镜像统一用**不带 `ENTRYPOINT`/`CMD`** 的共享模板，让基础镜像的 `/startapp.sh` 生效；两个模块自带的 `Dockerfile` 逐步退役 | 与在跑 21 个服务形态一致；一份模板，参数由平台 env 单点决定 | 需要下次发版才在运行期见效 | 低：不改运行中对象 |
| B | 保留自写 `ENTRYPOINT`，把 JVM 参数显式写进 `java` 命令行 | 一次改动即见效 | 在应用侧复制一遍 `startapp.sh` 的算法，与平台 env 口径分叉、GC 切换/`spring.config.location` 还得再抄 | 高：仓库与平台两份堆参数必然漂移 |
| C | 保留 `ENTRYPOINT` 但内容改成 `sh /startapp.sh` | 等价于 A 的效果 | 多一层无意义覆盖，读代码的人仍会以为镜像自己决定启动方式 | 中：可维护性差 |

## 4. 决策与结论
- 选定方案：**A**。
- 决策理由：AGENTS.md §7 已定「线上真实运行参数 MUST 查 Deployment env 与镜像」，
  红岛 JVM 契约的**唯一真源是基础镜像 `/startapp.sh` + 平台 env**；任何在模块 `Dockerfile` 里
  重写启动命令的做法都是把契约复制成第二份，正是 IT-020 反复踩的形态。
- 落地动作（本轮已完成）：
  1. 新增共享构建模板 [docs/ops/environments/scripts/hongdao-buildx.Dockerfile](../ops/environments/scripts/hongdao-buildx.Dockerfile)
     （`FROM itpb/jdk21:1.0.0` + `ENV JAVA_TOOL_OPTIONS` + `COPY app.jar /app.jar`，
     **刻意不写 `ENTRYPOINT`/`CMD`**，文件内注释写明理由）；
  2. 本轮 23 个新镜像**全部**用该模板构建（见 [IT-024](IT-024-红岛流水线构建缺口.md) §10 结果表），
     从此 `fep-dev-server:2.0.74`、`route-sentinel:1.0.3` 两个 tag 已是无旁路形态；
  3. `fep-dev-server/Dockerfile`、`route-sentinel/Dockerfile` **本轮刻意不改**：它们是
     IT-028 / IT-021 在跑版本的构建记录，改文件等于改动已交付事实，且 A 路线下构建入口
     已不再引用它们。
- 影响范围 / 关联模块：`fep-dev-server`、`route-sentinel`（红岛镜像形态的运行期 JVM）；
  连带影响 IT-020 的 JVM 调优口径。**不影响日志采集**（应用 `<app>.log` 照常落盘，见 §2 末更正块）。

## 5. 验证与回滚
- 验证方式 / 结果：
  - **已证**：两 Pod PID1 与 `-XX:MaxHeapSize` 实测差异（§2 表）；`acc-es` Pod 目录下有
    `gctrace.txt`×3 而 `fep-dev` Pod 目录下没有（同路径同卷，GC 日志缺失的直接对照）；
    `fep-dev.log` 仍在 → 应用日志采集不受影响（§2 末更正块）；全仓 `Dockerfile` 枚举确认
    只有这两个带 `ENTRYPOINT`；新镜像构建日志显示 `FROM itpb/jdk21:1.0.0@sha256:0e46cb35e5…`
    且层步骤为 `[1/2] FROM` + `[2/2]`（无 `ENTRYPOINT`/`CMD` 层）、
    `imgSize` 与平台在跑旧镜像同量级（`recon-server:1.0.28` 408.91 MiB vs `:1.0.26` 408.9 MiB）。
  - **未证（诚实标注）**：本轮只出镜像、**未做任何部署/upgrade**，故「用 `fep-dev-server:2.0.74`
    重启后 PID1 回到 `sh /startapp.sh`、堆回到 5900m」这条运行期复验**尚未做**。
- **待复验清单（已列入计划，未排期；触发即执行）**
  - 触发条件：`fep-dev-server` 或 `route-sentinel` **任一次发版改用共享模板出的新镜像**
    （现成候选 tag：`itpb/fep-dev-server:2.0.74`、`itpb/route-sentinel:1.0.3`），
    或这两个服务被平台重新部署到任一 buildx 出的新 tag。
  - 前置：**动在跑应用 MUST 先经用户二次确认**（AGENTS.md §5.C 生产红线），本单不自带执行授权。
  - 命令与判据（两条都是二值信号，不需要解读）：

    ```bash
    # ① 启动形态：期望输出 sh /startapp.sh；打出 java -jar /app.jar 即为仍被旁路
    kubectl -n itpb exec <pod> -- tr '\0' ' ' < /proc/1/cmdline

    # ② 堆上限：期望 -XX:MaxHeapSize=6186598400（5900m），仍是 2147483648 即未修复
    #    （javaPid 在修复后是 startapp.sh 的子进程，故先按 comm 找，别写死 1）
    kubectl -n itpb exec <pod> -- sh -c '
      jp=$(for x in $(ls /proc | grep -E "^[0-9]+$"); do
             grep -qa java /proc/$x/comm 2>/dev/null && echo $x; done | head -1)
      /home/javaapp/soft/jdk-21.0.1/bin/jcmd $jp VM.flags | tr " " "\n" | grep MaxHeapSize'

    # ③ 顺带核 GC 日志真的在写（hostname MUST 在 Pod 内取，别在本地 shell 展开）
    kubectl -n itpb exec <pod> -- sh -c 'ls -l /home/javaapp/app/logs/$(hostname)/'
    ```
  - 三条全绿 → 本单置「已解决」；①/② 任一不符 → 说明落到的镜像仍带自写 `ENTRYPOINT`，
    回 §4 检查构建时是否误用了 `fep-dev-server/Dockerfile` 或 `route-sentinel/Dockerfile`。
  - **命令取证状态（别把没跑过的当已验）**：①②③ 的**单体形式都已在 2026-10-28 现查里跑过**
    （§2 取证用的就是同一套 `/proc/1/cmdline` + 绝对路径 `jcmd` + `ls /home/javaapp/app/logs/$(hostname)/`），
    上面写的是**合并版**（带 javaPid 自发现、`grep -c gctrace`），合并版尚未跑过 ⇒
    首次复验 MUST 先拿一个健康服务当**正控**（现成选择：`acc-es-server`，预期
    `sh /startapp.sh` / `6186598400` / gctrace 文件数 ≥ 1），再拿目标 Pod 当**负控**对照，
    否则「命令本身写错」会被读成「镜像仍有问题」。
    （本条写就时 USB 网卡 `en9` 掉 IP、`diag reach` 报未拿到 IP，故合并版没能当场跑一遍。）
- 回滚预案：新 tag 与旧 tag 在 Harbor 并存、旧 tag 未删，应用对象一律未动 →
  回滚 = 把应用镜像指回原 tag（`fep-dev-server:2.0.72` / `route-sentinel:1.0.1`）；
  共享模板是新文件，不改任何既有构建记录。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-031`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」（状态＝处理中：镜像侧已自愈，运行期复验待发版）
- [x] 索引行未复制本文档的叙述（索引只写现象 + 状态 + 下一步 + 链接）
- [x] 本文档内版本号 / digest / 字节数均标 2026-10-28 现查，二次引用前 MUST 重跑
- 偏差说明：
  1. 发现于「出镜像」任务而非排障任务，属 AGENTS.md §5.C「镜像构建位置 MUST 远端」
     落地过程中顺带核契约所得；本轮未触碰任何在跑应用，符合生产红线（未二次确认前不部署）。
  2. 编号避让：登记时 `问题清单.md` 里 **`IT-030` 已被并发会话占用两条**
     （一条 `fep-app-vr` 7 条 exact 误指 `route-sentinel-svc`、一条云管台控制台「日志」恒空，
     后者当时只有索引行无文档），故本单顺延为 `IT-031`。
     顺带暴露一条纪律缺口：**并发会话按「最大编号 +1」分配编号会撞号**
     （`IT-028` 在表里也有两行同因），登记前 MUST 现查表内已占编号而不是只看文件名。
