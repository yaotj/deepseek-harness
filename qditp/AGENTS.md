# 青岛地铁 ITP 票务平台（qd-itp）

## 1. Core Objective (核心目标)
协助本平台的**开发、联调、文档编写与运维**。一切产出（代码 / 文档 / 配置 / SQL / 联调改动）MUST 绝对符合现有架构与既有约定、高内聚低耦合、具备金融级安全性，且 NEVER 引入任何冗余（依赖 / 文档 / 流程 / 上下文占用）。

## 2. Project Context (项目上下文)
本项目是城市轨道交通自动售检票系统（AFC）的互联网业务层，连接地铁自有系统（APP、闸机 AGM、TVM、BOM、ACC）及支付渠道（含支付宝）等第三方，支撑乘客互联网票务全生命周期。

### 2.1 核心技术栈
- **后端**: Java + Spring Boot + Maven 多模块；**数据库**: Oracle（ojdbc）
- **ORM/持久层**: MyBatis + PageHelper + **自研 sql-datasource / mybatis-adaptor**
- **序列化**: Fastjson2，**NEVER** Jackson / Gson
- **安全**: Spring Security（仅 web-server 启用）、JJWT、SHA256WithRSA
- **连接池**: Druid

## 3. Architecture & Modules (架构与模块)

### 3.1 代码分层约定（Spring Boot 模块内部）
- **controller** 仅参数校验与路由、**NEVER** 写业务逻辑；**service** 承载业务逻辑与事务；`entity`/`model` 为 DB 映射、`dto`/`vo` 为传输/视图对象。

## 4. STRICT RULES (严格开发准则 - AI 必读)

### 4.1 代码生成与修改 (Do's and Don'ts)
- **MUST** 使用 `jakarta.*` 包，**NEVER** 新增 `javax.servlet` / `javax.validation` / `javax.persistence` / `javax.annotation` 等 Jakarta EE 旧包（Spring Boot 3.x 强制要求）；JDK 自带的 `javax.crypto` / `javax.net` / `javax.sql` 不在此列。
- **NEVER** 直接写原生 JDBC 或标准 MyBatis 注解，**MUST** 统一走自研 `mybatis-adaptor` / `sql-datasource`；唯一遗留是 `collect-pay-server` 的 `OrderSeqMapper`（`@Select` 取序列），**NEVER** 再新增同类写法。
- **NEVER** 主动创建新的工具类（如 DateUtils, StringUtils），**MUST** 优先在 `model`、`rpc`、`resource/micro`（`web` / `sql-datasource` / `mybatis-adaptor`）中查找并复用现有组件。
- **MUST** 使用 Fastjson2 进行 JSON 序列化/反序列化，**NEVER** 引入 Jackson 或 Gson 处理核心业务报文。
- **NEVER** 引入 Redis / RabbitMQ / Kafka 等中间件：本项目不使用缓存与消息队列。需要异步或重试时 **MUST** 采用「落库状态 + `@Scheduled` 扫表重试」；需要并发控制时 **MUST** 采用数据库唯一索引 + `DuplicateKeyException` 兜底。`resource/micro/rabbitmq-adaptor` 是历史遗留基础构件，**NEVER** 在业务模块中引用。

### 4.2 业务与架构约束
- **幂等与一致性**: 涉及支付、订单、扣款的关键业务表，**MUST** 考虑 幂等设计（如唯一索引/防重表）及分布式事务一致性。
- **状态机校验用白名单、不用黑名单**：只允许明确的前置状态继续处理，其余一律拒绝；**NEVER** 写成「非终态即可处理」，**NEVER** 散落 `if` 里比状态字符串。范式：枚举 + 迁移表 + `canTransitTo` / `isTerminal` + 配 `*WhitelistTest` / `*StatusMachineTest` 单测。
- **服务间调用 MUST 通过 `rpc` 模块 + HTTP 直连**（读 `application.properties` 的 `service.*.url`），**NEVER** 引入 Feign / Dubbo；新增内部接口 DTO MUST 放 `model`、在对应 Client 补方法，**NEVER** 只在本模块定义。
- **安全红线**: 涉及支付、密钥、签名的代码改动，**NEVER** 擅自修改现有加密/签名逻辑，**MUST** 提示人工复核安全合规性。

## 5. AI Workflow (AI 执行工作流)

> **前置判据（MUST 在执行任何步骤前确认，顺序不可颠倒）**：
> 1. **任务类型**：开发新功能 / 排障 / 发布 / 联调测试 / 写文档 / 综管台（web-server + web）—— 先判任务类型分流到 §5.A~§5.E（**5.E 按对象分流：凡涉及综管台一律走 5.E**）；进入具体 workflow 后，再按「技术关注点」查 §5.1 路由表决定先读哪篇（不在 §5.1 高频 6 条内则查全表 `docs/提示词/任务路由表.md`，两层路由不冲突）。
> 2. **执行环境**：本任务在哪个环境操作？——`开发环境（本机）` / `测试环境（k8s-master / 172.20.211.23）` / `生产环境`。**发布任务 MUST 显式确认是测试还是生产**（操作命令和回滚路径完全不同）。
> 3. **生产环境特殊约束**：涉及生产环境的所有操作 **MUST 二次确认**，**NEVER** 自动执行。具体红线见 `docs/ops/生产环境清单.md` §六。

### 5.0 问题跟踪（跨工作流通用）
任何工作流中发现需跟踪的新问题（故障 / bug / 待办 / 架构债），**MUST** 先在 `docs/tracking/问题清单.md` 加索引行，**NEVER** 只修不记录。登记粒度按严重度分两档，**MUST 按下表判据选档，MUST NOT 升级凑流程**：

| 档位 | 判据 | 产出 |
|---|---|---|
| **重档** | 阻塞 / 生产事故 / 架构债 / 跨模块契约变更 / 数据一致性或安全风险 | 索引行 + `docs/tracking/IT-<编号>-<简述>.md` 处理文档（模板 `docs/tracking/IT-模板.md`，固定 6 节：背景 / 根因 / 方案对比 / 决策 / 验证与回滚 / 流程符合度自评） |
| **轻档** | 一次性小缺陷：文案 / 样式 / 拼写 / 单点 UI / 单行配置，且**处置方案唯一、无架构或一致性影响** | **只加索引行**（编号 + 一句话现象 + 关联提交 / PR），**NEVER** 建 IT 文档 |

**索引行 MUST 一眼读完**（一句话现象 + 状态 + 下一步 + 关联文档链接）；处理过程只写在 IT 文档里，索引与文档 **NEVER** 重复同一段叙述。

### 5.1 任务 → 必读路由（高频 6 条；全量真源在 `docs/提示词/任务路由表.md`）

**§5.2 通用骨架第 1 步「定界与检索」的「按技术关注点」查表入口（与前置判据「按任务类型」分流到 5.A~5.E 是两层路由，不冲突）：命中任一行 MUST 先读「先读」列再动手。**

**本表是 `docs/提示词/任务路由表.md` 高频 6 条的受机检投影**（`lint-prompts.py` 校验两表「先读」路径集合完全一致，漂移即 ERROR）；**全量路由以路由表为唯一真源**，命中非高频任务时 MUST 先读该表。路由表漏了本类任务 MUST 补进路由表（属高频 6 条时同步本表），NEVER 让下一个 AI 重新摸索。

| 任务类型 | 先读 | 再读 |
|---|---|---|
| 改业务代码 | `docs/business/<域>.md`（索引 `docs/business/README.md`） | 核心类源码 |
| 查架构总览 / 模块归属 / 公共构件 | `docs/architecture/overview.md`；`docs/architecture/common-components.md` | 按任务类型再路由 |
| 写 / 改 MyBatis `mapper` XML、SQL、Oracle 持久层 | `docs/architecture/mybatis-oracle-runtime-traps.md` | `docs/architecture/common-components.md` |
| 改支付 / 订单 / 扣款 / 签约 / 开户 / 卡池 / 对账链路；写 `@Transactional` / Spring 事件 / RPC 包装方法 | `docs/architecture/transaction-and-consistency.md` | `docs/business/<域>.md` + `docs/domain/decisions.md` |
| 排查「同一份代码在 A 模块好、B 模块坏」、tracing 断链 / `%X{traceId}` 恒空、虚拟线程 pin / 慢 SQL 拖垮 JVM | `docs/architecture/runtime-observability-traps.md` | `docs/architecture/common-components.md` §附.5.1 |
| 综管台 / 管理后台（web-server + web）功能开发 | `docs/architecture/综管台开发工作流.md` | `docs/architecture/web-server.md` §二/§三/§六；业务查询接口后端在对应模块 `/page/**`；红线与验证/打包/发版见工作流 §3 / §5 |

**技能不在当前上下文时，读 `~/.workbuddy/skills/qditp-<名>/SKILL.md`。**

### 5.2 通用步骤骨架（每个 workflow 共用）
> 5.A~5.C 按任务类型分流、5.E 按对象（综管台）分流、5.D 兜底；五者共用本节 5 步。第 1 步「定界与检索」按 §5.1 路由表命中行先读必读文档。**动手前 MUST 已确认前置判据（任务类型 / 执行环境 / 生产约束）**。

固定 5 步：
1. **定界与检索**：先判任务落到哪一类 / 哪个模块（层判错后面全白做）；按 §5.1 路由表命中行先读「先读」文档。
2. **方案与确认**：提出根因 / 改法；生产环境 MUST 二次确认 + 等人工复核；增量 / 需求类 MUST 先问询定稿（先问「要不要做」再问「怎么做」）。
3. **实施**：落地改动，严格遵循 §4 STRICT RULES。
4. **验证门（通用，缺一不可）**：改构建相关 ⇒ `mvn -o clean test-compile`（§6）；改提示词 / 文档 ⇒ `python3 docs/提示词/lint-prompts.py` ERROR 0；改前端（综管台）⇒ 走 `docs/architecture/综管台开发工作流.md` §5.1 验证四件套；线上改动 ⇒ NodePort 探活 + 日志确认。
5. **回写义务（通用）**：类名 / 方法名 / URL / 端口 / 状态取值 / 数据表 owner / 菜单 变动 MUST 同步回写对应架构 / 文档；路由表（`docs/提示词/任务路由表.md`）漏了本类任务时 MUST 补进该表（属高频 6 条时同步 §5.1）。细则见 `docs/提示词/工程与文档规范.md` §六。

### 5.A 后端/接口开发工作流（微服务 + 写代码任务）
> **范围**：仅服务端 / 接口开发（Java/Spring 微服务、接口、SQL、配置改动）。前端（综管台）见 §5.E；纯文档、联调 / 测试见 §5.D。

遵循 §5.2 通用步骤骨架（定界/检索 → 方案/确认 → 实施 → 验证门 → 回写）；**本工作流专属**：

- **检索必读**：按 §5.1 路由表命中行先读「先读」文档；同时在 `model` / `rpc` / `resource/micro` 搜索可复用组件（§4.1 禁止新建工具类）。
- **业务逻辑任务前置**：命中路由表「改业务代码」行时，**MUST** 先完成 `docs/business/README.md` 要求的「业务提示词 → 原始规格 → 核心类源码」三层阅读，再进入实施。

### 5.B 后端/微服务排障工作流（测试/生产环境 + 排查任务）
> **范围**：仅后端微服务排障（基于 traceId / VictoriaLogs / `docs/ops/入口服务链路排查规范.md`）。综管台 / web 前端排障（RuoYi web-server、Vue 前端、菜单 SQL、nginx 反代、Spring Security/JWT）不在此列，走 §5.E，细则见 `docs/architecture/综管台开发工作流.md`。

遵循 §5.2 通用步骤骨架；**本工作流专属**：

- **定位三连**：确认现象（现象 + traceId + 报错原文）→ 按 `docs/ops/入口服务链路排查规范.md` 追 traceId → 打开 `docs/ops/排障手册.md` 找对应症状；再按 §5.1 路由表命中行读「先读 / 再读」文档。
- **生产环境**：提方案 MUST 等人工复核；操作完毕 MUST 立即验证（NodePort 探活 + 日志确认）。

### 5.C 发布工作流（测试/生产环境 + 上线任务）
> **范围**：所有模块的镜像构建 / 推送 / 滚动更新 / 版本切换。纯前端（综管台 `web/`）发版特殊点见 §5.E。

**发布 / 滚更 / 版本切换**：读技能 `qditp-deploy-release`，按六步流程执行（bump → 出 jar → scp → build-push → 滚更 → digest 比对）。

**生产红线（MUST，缺一不可）**：
- **二次确认**：发布任务 MUST 显式确认是测试还是生产（前置判据第 2 步）；生产发布 MUST 二次确认 + 灰度 / 回滚预案（单副本模块按服务逐个滚更，不做金丝雀分流）。
- **升版本末位 +0.0.1，NEVER 同 tag 覆盖**（§6 版本与回滚）；推镜像看 `digest:` / `Pushed` 不看退出码。
- **digest 比对**：rollout 后 MUST 比对 Pod imageID 与推送 digest 一致（判据见技能 `qditp-deploy-release` 与 `docs/ops/构建与部署规范.md`）。
- **前端**：本仓只出 zip，发版 MUST 走云平台（§5.E）；回滚点＝线上当前版本，NEVER 照文档抄版本号。

### 5.D 专项工作流（兜底：联调 / 测试 / 纯文档 / 脚本 / 数据迁移）
> **范围**：不属于 5.A~5.C / 5.E 的专项任务；匹配它们的优先走对应流程，本节点兜底。

- **联调 / 集成测试**：读 `docs/提示词/任务路由表.md` 对应行 + 相关链路规格（`docs/接口规范文档/` 对应 `.docx`）。
- **写 / 改文档**：读 `docs/提示词/工程与文档规范.md`；改动涉及事实 MUST 同步回写（§5.2 第 5 步）。
- **数据迁移 / 拆库 / 按域分库**：路由见 `docs/提示词/任务路由表.md`「按域分库 / schema 拆分 / 迁移」行。**NEVER 直接在生产库执行 DDL/DML** —— MUST 先备份（`BAK_<表>_<日期>` 或 `backupTable`）+ 给还原 SQL + 测试库演练后走审批。

### 5.E 综管台工作流（web-server + web 前端）
> **范围**：综管台（`web-server` RuoYi 后台 + `web/` Vue3 前端）的问题与需求，含菜单 SQL、nginx 反代、侧边栏样式。
> **细则（唯一真相源）**：`docs/architecture/综管台开发工作流.md`（定界/检索/实施 + 验收四件套 + 打包 + 发版 + 回写）；结构事实见 `docs/architecture/web-server.md`。本节点只留闭环主干。

遵循 §5.2 通用步骤骨架；**本工作流专属**：
- **定界**：拿现象原文（报错文案 / 接口返回 / 截图）先判层——**前端渲染 / 后端接口 / 菜单 SQL / nginx 反代**。层判错，后面全白做。
- **检索**：一律用 Grep / `rg`（注意 `rg -r` 是 REPLACE 不是 recursive）。前端搜 `web/src`；后端按落点搜——业务查询接口搜对应业务模块（`/page/**`），菜单 / 用户 / 角色 / 权限 / 定时任务搜 `web-server`（`web-system` / `web-quartz` 等）。
- **判落点**：菜单在 `web-server`，**运营查询接口后端落业务模块 `/page/**`**；改样式前先确认「编译出的选择器能否命中真实 DOM」。
- **问询定稿**：**需求类**（非缺陷）MUST 先问询取得业主裁决再动手；**增量需求先问「要不要做」，再问「怎么做」**。
- **验收 / 打包 / 发版 / 回写**：MUST 走 `docs/architecture/综管台开发工作流.md` §5「验收、打包与发版闭环」（验证四件套 + 打包 + 发版 + 上线后核验 + 回写义务），**缺一不可**；前端发版 MUST 走云平台、回滚点＝线上当前版本。

## 6. Build (构建)

**本机两条硬约束**：SVN 服务器不可达 ⇒ 源码提交换有公司网络的环境；
JDK/Maven 由 mise 管（项目 `qd/mise.toml` 指定 Maven 3.8.7 / JDK 21）⇒ **MUST** `mise exec -- mvn ...`（裸 `mvn` 落到 mise 全局默认 3.9.10、裸 `java` 落到 25.0.1，均非项目指定版本）；

**构建顺序 NEVER 颠倒**：①`resource/micro`（改了自研持久层或 `micro/web` 公共构件才需要，不在根 pom 聚合列表）
→ ②`model` + `rpc` → ③目标模块。
- `model` / `rpc` **版本号恒定、NEVER 升级**，只 `mvn install` ——
  没先 install 会拿到 `~/.m2` 里的旧 jar，**后果是编译通过但打进镜像的是旧 class**，
  新增字段被 Fastjson2 静默丢弃。
- 验证改动 **MUST** `mise exec -- mvn -o clean test-compile -pl <module>`（增量被 target 缓存骗过；`-pl` 限定本模块，
  NEVER 从根跑——会带进 `employee-card-acc-simulator` 直接假失败）；
  **NEVER 靠全量 `mvn compile` 判断**（`employee-card-acc-simulator` 必然编译失败、会假报）；
  跑依赖已改 model/rpc 的模块测试（如 `ticket-server`）MUST 带 `-am`（`model` 新字段在旧 jar 里不可见；`-am` 仅测试/验证步加，`出 jar/推镜像` 那步 NEVER 加）。
- 给 `model` 的 DTO 加字段后，**MUST 重建链路上每一个经手该 DTO 的模块镜像**，NEVER 只重建终点服务。

**出 jar**：`mise exec -- mvn -o -pl <module> -am package -Dmaven.test.skip=true`（默认只出 jar、不推镜像；`-am` 把依赖的 model/rpc 一起重新编进 jar，`-Dmaven.test.skip=true` 而非 `-DskipTests`——后者仍跑 test-compile、删过 target 会报「主类找不到」假错）。

## 7. Ops & Deployment

涉及部署配置、环境变量、外部系统地址、密钥、上线核对、切流量的任务，
**MUST** 先读 [`docs/ops/README.md`](docs/ops/README.md) 的文档清单（含「何时读」列）再动手——该清单是 `docs/ops/` 的唯一索引，NEVER 在本文件复制第二份。高频率红线见下。

**环境判据（以下为示例，数字与地址 MUST 现查，NEVER 当现状引用）**：
- **线上真实地址 / 版本 / 流量落点 MUST 查 K8s Deployment env 与 image tag**，
  NEVER 只看仓库 properties 或 pom（集群 env 会覆盖 jar 内配置；`deploy/k8s/` 的 manifest 只作模板）。
- **设备域入向路由的仓库内唯一开关是 `fep-app-vr` 这条 VirtualService**：切流量 / 改前缀 / 判落点 MUST 先读 `docs/ops/流量切换.md`（唯一 Runbook，NEVER 只凭本行操作）。最致命一条常驻：**改端口只改 `targetPort`，NEVER 改 `port` / Service selector**；APP 域不走这条 VS，要切改 `fep-app` 的 `service.*.url` env。
- **Quartz 用内存 JobStore**：`QRTZ_*` 恒 0 行 ≠ 任务没跑，MUST 查 `SYS_JOB_LOG`；
  运行期 INSERT/UPDATE `sys_job` 不生效，MUST 重启 web-admin；`misfire_policy=3` 表示重启期间不补跑。
- **双跑硬约束**：recon-server / web-admin / face-pay-server / collect-pay-server / acc-security-server 的定时任务无分布式锁，MUST 单副本（名单与判据见 `docs/ops/生产环境清单.md`）。
- 从 `k8s-master` 压测 MUST 用 `172.20.211.23:<NodePort>`；看日志 MUST 走 `scripts/klog.sh`
  （本机 kubeconfig 无 ITP 集群凭据）；**klog 只看尾部窗口且 Pod 一重启日志就丢**，
  全量检索须 `ssh k8s-master` 后逐 Pod grep 容器内日志文件。
- **`172.20.211.23:300xx` 网段裁决为测试环境**；即便如此，破坏性写库仍 MUST 先存原值并给还原 SQL。

> 完整条文（`service.*.url` 三类问题、404 伪装成 HTTP 200 + UUID retCode 的巡检手法、MCP 数据库工具参数名坑、`*-schema.sql` 与 `*-migration.sql` 分界、`testngbackV2` 残留清单、外部网关地址 MUST 实测等）见 [`docs/ops/构建与部署规范.md`](docs/ops/构建与部署规范.md) §8。

## 8. 文档地图
`docs/` 按权威性分三类（不是目录穷举）：**AI 提示词文档**（可改）、**甲方原始规格**（只读、只有 `.docx`/`.doc`，NEVER 修改）、**外部系统接口规格**（按供方版本整体替换）。

**逐目录说明（是什么 / 边界 / 何时读）与记忆层（`.workbuddy/memory/`）见 [`docs/文档地图.md`](docs/文档地图.md)**——**没有平级第二份索引**：`docs/README.md` 已于 2026-08-25 删除、**NEVER 重建**，目录索引统一在 `docs/文档地图.md`。「任务 → 读哪篇」的入口是 §5.1 路由表，**全量真源在 `docs/提示词/任务路由表.md`**，两份配合、不互相替代。

**两条必须常驻的全局警告（本文件保留，勿下沉）**：
- **缺失的甲方文档**：《技术规范-第4部分-系统接口规范》（`0x2001~0x9004` 报文码定义）在仓库内**没有源文件**，需向甲方索取；涉及该编号体系的任务 **MUST** 先告知用户文档缺失。
- 甲方三个原始规格目录（`docs/接口规范文档/`、`docs/业务需求文档/`、`docs/技术规范文档/`）**只读、只有 `.docx`/`.doc`，NEVER 修改**，且 NEVER 在其中新增派生文件（脚本抽取的接口清单 `.md`/`.json` 已于 2026-08-25 全删）。