# IT-032 face-pay-server `application.yml` 拍平只做一半，YAML 非法致启动即崩

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-032` 一行（重档）。
> 本文所有数字均为 2026-10-29 红岛现查，二次引用前 MUST 重跑取证命令。

## 1. 背景 / 现象

- 发现时间：2026-10-29 11:20 前后（红岛「日志落共享盘 + log4j2 AsyncAppender」批量发版第 3 批）。
- 发现渠道：批量发版脚本对 `face-pay-server` 报「超时未就绪（新 tag 就绪 0/2）」，人工介入查 Pod 拿到启动栈。
- 影响范围：`face-pay-server` 单一应用（红岛 itpb，2 副本）。
  **生产影响窗口 ≈ 13 分钟 0 就绪**（11:14 PATCH 触发滚动 → 老副本被缩到 0 → 11:27
  回滚到 `1.0.83` 双副本 Ready）。窗口内 F2F 当面付（APP/TVM/BOM）**全部不可用**。
- 现象描述：新 Pod `1/2 Error`、`restartCount` 在 6 分钟内涨到 6，应用容器 `lastState.terminated`
  为 `Error` / `exitCode=1`；`kubectl logs` 首行就是异常头部：

  ```
  ERROR org.springframework.boot.SpringApplication.reportFailure  Application run failed
  org.yaml.snakeyaml.scanner.ScannerException: mapping values are not allowed here
   in 'reader', line 4, column 9:
          page:
              ^
  ```

  异常在 `SpringApplicationRunListeners.environmentPrepared` 阶段抛出，即**配置解析期**，
  与业务代码、数据库、中间件全部无关；容器起不来，因此也没有任何应用日志文件
  （`${logPath}/face-pay-server.log` 不生成，见 [IT-027](IT-027-启动期日志不进VictoriaLogs与滚动文件.md)）。

## 2. 根因分析

### 2.1 直接原因

`face-pay-server/src/main/resources/application.yml` **不是合法 YAML**。

文件前 3 行被写成了「点号扁平键」形式，第 4 行却仍是旧的分层缩进：

```yaml
mybatis.mapper-locations: classpath:mapper/*Mapper.xml
other.sql.type: oracle
other.sql.double-datasource: false
    page:                       # ← 4 空格缩进，上面没有任何未闭合的父键
      type: oracle
    host: ${DB_HOST:172.120.110.25:1521}
    ...
```

第 4 行出现「有缩进的 mapping key 但上层是标量」→ snakeyaml 在 `line 4, column 9` 报
`mapping values are not allowed here`。`page` 之后到 `audit.heartbeat` 的整段
（`page` / `host` / `database` / `username` / `password` / `audit`）全部失去父键 `other.sql`。

### 2.2 引入提交

`ebf27098e0`（2026-10-20，`chore: 批量更新文档、K8s配置与多模块代码`）对该文件做了四件事：

| 改动 | 评价 |
| --- | --- |
| 把 `mybatis:` / `other.sql.type` / `other.sql.double-datasource` 拍平成点号键 | **只拍了 3 个，`other.sql` 其余子键原样留着** → 文件变非法 |
| 补齐真实默认值（`DB_HOST` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`） | 意图正确，保留 |
| 新增 `spring.threads.virtual.enabled: false` | 意图正确，保留 |
| 删除 6 条护栏注释（`unSafeUrlChars` 必须带引号、OTLP 排除、`SERVICE_RECON_URL` 端口为 NodePort、补款回调、`recon.internal-token`、traceId 三行成组） | **不该删**，恢复 |

同一次提交还改了大量 `application.properties`（多为补默认值，`collect-pay-server` 的
`application.yml` 为 `+5` 行纯新增）。已对全仓 `*/src/main/resources/application*.yml`
跑 `yaml.safe_load` 全量校验：**只有 face-pay-server 一个失败**，其余全部可解析。

### 2.3 为什么潜伏了 9 天没被发现

1. 该提交只改了配置文本，**没有任何模块启动验证**（`mvn test-compile` 也发现不了
   —— 资源文件语法错误不参与编译）。
2. `1.0.84`、`1.0.85` 两个 jar 本地确实构建出来了（`target/` 里在），
   但**从未部署**：红岛生产一直跑 `1.0.83`（该 tag 的 jar 产自 `3fe3daca09` 之前，
   yml 是好的）。旧 ReplicaSet `face-pay-server-9ccc58ff9` 的镜像 tag 实证为 `1.0.83`。
3. 因此缺陷直到 2026-10-29 首次「用当前工作区重打 face-pay-server」才被引爆。

### 2.4 排除项（避免误判扩大）

- **不是** CloudOS `advanced-config` 丢 env：旧 ReplicaSet 模板与当前 Deployment 的
  容器 env 逐项比对 `65 项 = 65 项`，`comm` 双向差集为空。
- **不是** 本轮 log4j2 `AsyncAppender` 改动：`1.0.85` 与 `1.0.86` 两个 jar 内的
  `application.yml` 字节完全一致（`diff` 无输出），坏文件早于本轮改动存在。
- **不是** 镜像/基础镜像/`/startapp.sh` 问题：命令行与其余 20 个应用同构。

## 3. 方案对比

| 方案 | 做法 | 结论 |
| --- | --- | --- |
| A. 就地补父键 | 在 `page:` 前插回 `other:` + `  sql:`，保留点号扁平的前 3 行 | 能用但同文件两种风格并存，且删掉的护栏注释仍缺 |
| B. 回到最后可用版本 + 重放意图改动 | `git checkout 3fe3daca09 -- <file>`，再把「真实默认值」「`spring.threads.virtual.enabled`」加回去 | **采用**：结构与其它 20 个模块一致、护栏注释齐全、默认值不回退 |
| C. 改回 `application.properties` | 整文件改写为 properties | 不采用：与模块既有形态不符，改动面过大 |

## 4. 决策与结论

采用方案 B。落地内容：

1. `git checkout 3fe3daca09 -- qditp/face-pay-server/src/main/resources/application.yml`
   —— 取回最后一个可解析版本（该版本正是 `1.0.83` 生产镜像的配置，已被生产验证）。
2. 重新加回两处**有意义的**改动：
   - `other.sql.host/database/username/password` 的真实默认值；
     其中 `database` 的占位符由旧的 `${DB_DATABASE:}` **改为 `${DB_NAME:hditpdb}`**，
     与 Deployment 注入的 `other.sql.database=${DB_NAME:hditpdb}` 对齐
     （旧占位符 `DB_DATABASE` 在红岛从未被注入，属死默认值）。
   - `spring.threads.virtual.enabled: false`。
3. 6 条护栏注释随版本一并恢复；`unSafeUrlChars` 保持带引号的 `"null"`。
4. 版本号 `1.0.86 → 1.0.87` 重打（`1.0.86` 这个 tag 已指向坏产物，MUST NOT 覆盖复用）。

## 5. 验证与回滚

### 5.1 验证（发版前，本机）

```bash
# 1) YAML 可解析 + 关键节点位置正确
python3 -c "import yaml;d=yaml.safe_load(open('face-pay-server/src/main/resources/application.yml',encoding='utf-8'));print(list(d.keys()));print(d['other']['sql']['page']);print(d['spring'].get('threads'))"
# 期望：顶层键 mybatis/other/server/spring/app/f2f/pay/service/recon/management
#       other.sql.page = {'type': 'oracle'}；spring.threads = {'virtual': {'enabled': False}}

# 2) 口令占位符未被当作 YAML 注释截断
#    期望 other.sql.password == '${DB_PASSWORD:itp!#txn8153}'（# 前无空格，属 plain scalar 一部分）

# 3) jar 内 yml 同样合法（防「本地改了但没进产物」）
unzip -p face-pay-server/target/face-pay-server-1.0.87.jar BOOT-INF/classes/application.yml | python3 -c "import sys,yaml;yaml.safe_load(sys.stdin.read());print('jar yml OK')"

# 4) log4j2 配置在 web-2.0.jar 内（不在 BOOT-INF/classes 根），确认是 Async 版
unzip -p face-pay-server/target/face-pay-server-1.0.87.jar BOOT-INF/lib/web-2.0.jar > /tmp/w.jar
unzip -p /tmp/w.jar log4j2-linux.xml | grep -c AsyncRollingFile   # 期望 7（1 定义 + 6 引用）
```

### 5.2 验证（发版后，红岛）

- Deployment `2/2 Ready`，Pod 内 `1 号进程 = sh /startapp.sh`
  （不可写成自写 `ENTRYPOINT`，见 [IT-031](IT-031-红岛镜像ENTRYPOINT绕过startapp致JVM日志契约失效.md)）。
- `kubectl logs` 无 `ScannerException`；8080 LISTEN（`ss -ltnp | grep :8080`）。
- `grep -c '^\s*at '` 抽查日志行仍带 `%l`（Async 改造未破坏位置信息），traceId 在 MDC 内。
- `/proc/mounts` 中日志盘挂载项含 `soft,timeo=30,retrans=2`（本轮 PV `mountOptions` 生效）。

**2026-10-29 实测结果（`1.0.87`，Pod `face-pay-server-f6bffcdcf-6pvb4`）**：

| 检查项 | 实测值 |
| --- | --- |
| Deployment | `2/2/2`（updated/ready/replicas） |
| 1 号进程 | `sh /startapp.sh` |
| 8080 | LISTEN（`ss -ltn` 命中 1 条） |
| 日志盘挂载 | `172.120.0.1:/ITP-PV/home/java/itpb-app-logs` → `vers=3,soft,timeo=30,retrans=2` |
| `ScannerException` 计数 | 0 |

### 5.3 回滚

`face-pay-server` 回滚 = 把镜像指回 `1.0.83`（已验证可启动，2026-10-29 11:27 实测）：

```bash
python3 /tmp/upgrade_app.py face-pay-server \
  os-harbor-svc.default.svc.cloudos:443/itpb/face-pay-server:1.0.83 --yes
```

MUST 走 `advanced-config`（`PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config`），
`kubectl patch` 会被平台数分钟内回收，见 [IT-020](IT-020-JVM参数被平台回滚.md)。

## 6. 流程符合度自评

- **已做到**：先查 Pod 启动栈再动手定性（未按「日志没写」等表象猜测）；回滚前已确认
  服务真的 0 就绪（读 `status.readyReplicas` 为空，不以 `updatedReplicas` 代替）；
  修复后逐层验证「本地文件 → 仓库文件 → jar 内副本 → 运行期 Pod」四层一致。
- **未做到 / 本可更早发现**：批量发版脚本的等待判据最初用 `Deployment.status.updatedReplicas`，
  PATCH 刚提交的瞬间该值仍等于旧值，会立刻误判「已就绪」；本轮已改为 **Pod 级判据**
  （全部 Pod 的 `spec.containers[0].image` 均等于目标 tag 且 `Ready=True`）。
- **遗留缺口（建议补）**：仓库没有任何「配置文件语法」门禁。`application*.yml` /
  `*.properties` 的语法错误不参与 `mvn test-compile`，只能在拉起进程时才暴露。
  建议在发版前置检查里加一步全仓 YAML 校验（约 10 行脚本），
  接入点待定（候选：`docs/ops/构建与发布.md` §三 前置检查、或 `scripts/` 下新增独立脚本）。
  本次未擅自新增脚本，避免与「任务→文档唯一真源」的既有路由冲突，留待裁决。
- **附带发现**：`docs/tracking/问题清单.md` 存在**两行 `IT-030`**（一行「fep-app-vr 七条 exact 误指
  route-sentinel-svc」，一行「云管台控制台日志恒空」）。编号唯一性未守，建议下一轮整理时改号。
