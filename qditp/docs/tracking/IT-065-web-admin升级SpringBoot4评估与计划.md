# IT-065 web-admin 升级 Spring Boot 4 评估与计划

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-065` 一行（重档：框架分支 EOL 的安全 / 合规风险 + 架构债，按问题跟踪纪律的「阻塞 / 架构债 / 跨模块契约变更」判据立档）。
> **仓内只做评估与计划**：主工作区未改任何 pom / 代码 / 配置、未发版；阶段 1 PoC 仅在 `/tmp` 隔离副本内改动（结果见第 5 节）。

## 1. 背景 / 现象
- 发现时间：2026-11-07。
- 发现渠道：自查（业主侧提出「web-admin 管理控制台评估升级到 Spring Boot 4」）。
- 影响范围：`web-server` 的 6 个子模块（web-admin 为唯一可执行模块）、综管台前端 `web/`、以及两个按 Boot 3.2.6 编译的二进制依赖 `rpc:2.0.1` 与 `com.chinasofti.huateng.micro:web:2.0`；**其余 26 个业务模块不在本次范围**（裁定见第 4 节）。
- 现象描述：
  - 全仓后端现跑 Spring Boot 3.2.6（Java 21）。该分支 2024-12-31 已 EOL，3.5 分支 2026-06-30 亦已 EOL；截至本轮只有 4.0.x（2026-12-31 EOL）与 4.1.x（2027-07-31 EOL）在开源支持内。运行已停止安全补丁的框架属合规风险，升级须排期而非可选。
  - 这不是现存故障，而是「技术基线到期 + 一次大版本跳跃」的架构债：Boot 4 基于 Spring Framework 7 / Security 7、Jakarta EE 11（Servlet 6.1 / Tomcat 11）、默认 Jackson 3，并把 `spring-boot-autoconfigure` 拆成按技术划分的模块、改了一批自动配置类的包名与 starter 名称。
  - 现状事实：根 `pom.xml` 只有 `<modules>` 聚合与编译插件管理，**无 `dependencyManagement`**；`web-server/pom.xml` 无 parent，用 `spring-boot-dependencies` BOM import 自管 3.2.6 并手工覆盖 Spring Framework 6.1.8 / Security 6.2.4 / Logback 1.4.14 / Tomcat 10.1.42；`account-server/pom.xml`、`rpc/pom.xml`、`resource/micro/web/pom.xml` 各自 `parent = spring-boot-starter-parent 3.2.6`。各模块打各自 fat jar，运行时互不影响。
  - `web-server` 无 `javax.*` 残留、无 `spring.factories`、无 `@MockBean` / `RestTemplate`，Jakarta 迁移层没有历史债。基础镜像 `itp/jdk:21` 满足 Boot 4.1.x 的 Java 17–26 区间，换 JDK 不是升级前置条件。

## 2. 根因分析
- 排查过程：逐项核对 `web-server/pom.xml`、`web-admin/pom.xml`、`account-server/pom.xml`、`rpc/pom.xml`、`resource/micro/web/pom.xml`，以及 `ServerApplication`、`SecurityConfig`、`ApplicationConfig`、`SensitiveJsonSerializer`、`application.properties` 的配置键与 `spring.autoconfigure.exclude`；再核对 Spring Boot 4.0 迁移指南、Spring Framework 7 / Security 7 升级说明、MyBatis / PageHelper / Druid / springdoc / JJWT 的兼容矩阵与版本生命周期（2026-11-07 公开资料检索）。
- 根因结论：
  1. **风险根因是框架分支 EOL**，不是代码缺陷；但升级成本集中在 `web-server` 内部，属于可隔离的局部改造。
  2. **硬阻塞清单**（均在 web-server 内，编译期或启动期即暴露）：
     - `ServerApplication` 的 `@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class })` 是**编译期 Class 引用**，Boot 4 下两个类都改包（Boot → `org.springframework.boot.jdbc.autoconfigure.*`；Druid → `com.alibaba.druid.spring.boot4.autoconfigure.*`）；`application.properties` 的 `spring.autoconfigure.exclude` 是同一排除的重复配置，两处都要改。
     - `SecurityConfig` 直接 `new AntPathRequestMatcher(...)`：Security 7 已移除该类，改 `PathPatternRequestMatcher`；且 PathPattern 语义变化会改变 `/**/*.html`、`/profile/**`、`/swagger-ui/**` 等放行结果，**属鉴权变更，MUST 人工复核安全合规性**。
     - `ApplicationConfig` 的 `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`；`SensitiveJsonSerializer` 与 `jjwt-jackson` 是 Jackson 2 实现，需迁 `tools.jackson.*` 或过渡保留 `spring-boot-jackson2`。
     - `web-server/pom.xml` 的 4 个手工版本覆盖必须删除，否则与 Boot 4 的 7.x / 11.x / 1.5.x 直接冲突；starter 更名（web → webmvc、aop → aspectj，旧名仍可用但已废弃）可用 `spring-boot-starter-classic` 桥接。
     - 第三方大版本：MyBatis starter 3.0.3 → 4.0.x、PageHelper 2.1.0 → 4.1.1、Druid `druid-spring-boot-3-starter` 1.2.23 → `druid-spring-boot-4-starter` 1.2.28+、springdoc 2.5.0 → 3.1+/3.2、JJWT 0.12.6 → 0.13.0（均有 GA 版本可用）。
  3. **范围误判已更正**：初版结论把 `rpc 2.0.1` / `micro:web 2.0` 当作「共享源码必须与 web-admin 一起重编译」，据此判为「不能单点升级、范围天然是全仓」。复核后**撤回**：二者只是二进制 jar 依赖，`rpc` 对 `micro:web` 还是 `<optional>true</optional>`；`rpc` 源码只用 Spring 7 保留的稳定 API（`@Value` / `@Service` / `@ComponentScan` / `WebClient` / `ResponseEntity` / Fastjson2），`micro:web` 引用的 Boot 内部注解（`AutoConfigureBefore`、`ConditionalOn*`、`ConfigurationProperties`）在 4.x 仍保留原包，模块化改的主要是**自动配置实现类**包名。各模块独立 parent + 独立 fat jar，web-admin 单独升级不改变其余模块运行时。
  4. 二者对 web-admin 仍是**运行时必需**：`ServerApplication` 上有 9 个 `@EnableRpcXxx`，Quartz 的 `sys_job` 靠 Client 调下游；`micro:web` 提供 `ProxyWebClient`（rpc 里是 optional，不传递，web-admin 必须显式给）。不可从 pom 删除，但也不需要随之改源码 / 重编译 / 发新版。
  5. 静默失败风险（比编译错更危险）：Jackson 3 默认值变化影响 `AjaxResult`、前端与 `service/export/executor/*` 两套渲染；模块化后自动配置类未加载只会「功能没生效」；Boot 4 换了 fat jar loader，可能重现 `CustomLoggingConfiguration` 重载时 VictoriaLogs appender 不实例化（历史见 `docs/architecture/web-server.md` 附F）；Quartz 内存 JobStore 与 Druid 监控台须逐项验。

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A | 全仓 27 模块统一升 4.1.x | 一次解决全仓 EOL，基线单一 | 27 模块回归 + `acc-security-server` 的 Undertow 被 Boot 4 移除需换容器；发布窗口最长 | 高：一次改动面过大，回滚粒度粗 |
| B（推荐） | web-admin / web-server 单点升 4.1.x，`rpc 2.0.1` 与 `micro:web 2.0` 保持不动，仅做二进制兼容性验证与最小适配 | 改动面最小，只动 `web-server/pom.xml` 与 web-server 内源码；其余模块零改动 | 留下一个「web-admin 与其它模块 Boot 大版本不一致」的窗口期 | 中：集中在两个 jar 的兼容性与日志链路，可 PoC 前置排除 |
| C | 独立分支 PoC 先行，验证通过再决定 B | 风险最低，不进发版链 | 多一轮工作量 | 低；作为 B 的阶段 1 |
| D | 暂缓，只登记 EOL 风险 | 零成本 | 继续跑已 EOL 且停止安全补丁的框架 | 合规不可接受，不选 |

## 4. 决策与结论
- 选定方案：**B，且以 C 作为其阶段 1**（先 PoC 再决定推进）。目标版本 **Spring Boot 4.1.x**；不选 4.0.x（2026-12-31 即 EOL）。具体补丁版本与目标镜像版本在发版前 MUST 现查中央仓库 / 线上实际版本，NEVER 照本文档抄。
- 决策理由：① 各模块版本独立、无强制统一机制，单点升级技术可行；② `rpc` / `micro:web` 是运行时必需依赖，但不是必须一起重编译的耦合项；③ 全仓 3.2.6 EOL 治理应**另立专项**，把一次可控升级扩成 27 模块回归不划算。
- 影响范围 / 关联模块：`web-server` 6 子模块；`rpc` / `micro:web` 仅验证、不改造；`web/` 前端仅在 JSON 契约变化时联动（届时走验证四件套）；其余 26 模块 pom 与 fat jar 不动。
- 最小适配预案（仅 PoC 失败时启用）：`web-admin` 用 `<exclusions>` 排除 `micro:web` 传递的 knife4j 4.4.0（Boot 3 专用且与 springdoc 3.x 冲突）、覆盖 `micrometer-registry-prometheus` 显式旧版；必要时出 `micro:web 2.1` 供 web-admin 使用、其余模块继续 2.0（Maven 坐标不同，双版本并存可行）。
- 明确不做：不改 `token.secret`（更换会使全部已签发 JWT 失效，须单独排期、低峰执行）；不扩散 `/page/**` 无鉴权缺口；不把 JDK 25（JEP 491 虚拟线程 pinning 收益）与本次升级捆绑。

## 5. 验证与回滚
- 验证方式 / 结果：第 1 轮仅做计划；**阶段 1 PoC 实测结果见本节末尾**，其余运行态判据仍为计划门禁（本机不可执行）。
  - 阶段 0 立项：拍板范围与目标版本、登记本档、路由表补录「框架版本升级评估 / 计划」行。
  - 阶段 1 PoC（独立分支、不进发版链，只改 web-server）：`mvn -o clean test-compile`；`mvn dependency:tree` 核对传递依赖版本；用 `--debug` conditions report 验 `WebAutoConfig` 自动配置确实加载；`ProxyWebClient` 实发一次请求；验 `app:"web-server"` 入 VictoriaLogs；knife4j 排除后验 Swagger 端点；`spring.jackson.use-jackson2-defaults=true` 过渡并做 `AjaxResult` / 异步导出契约回归。
  - 阶段 2 兼容性最小适配：仅 PoC 失败时按第 4 节预案处置，其余模块一律不动。
  - 阶段 3 web-admin 专项：处置第 2 节硬阻塞清单；鉴权规则等价性 MUST 人工复核（项目安全红线）；改文档 ⇒ `python3 docs/提示词/lint-prompts.py` ERROR 0；改前端 ⇒ 全量语法自检 + 同基线回归 + 产物内锚点 + 后端 test-compile 四件套；改构建 ⇒ `mvn -o clean test-compile`。
  - 阶段 4 发版：测试环境云管台链首选；版本末位 +1、NEVER 同 tag 覆盖；就绪判据看 Pod 级（image tag 一致且 Ready、Pod 数 = 副本数）；NodePort 探活 + 日志确认；红岛生产 MUST 二次确认 + 灰度 / 回滚预案。
- 回滚预案：回滚点 = **线上实际版本**（MUST 现查 K8s Deployment / CloudOS 应用详情）；因 Boot 4 改了 fat jar loader，回滚必须整体回退镜像，NEVER 只回退配置；旧 tag 不覆盖，直接切回即可。

- **阶段 1 PoC 实测结果（2026-11-07，隔离副本，主工作区零改动）**
  - 环境：本机 macOS，`mise` JDK 21.0.11 / Maven 3.9.10，阿里云镜像 HTTP 200；副本 `/tmp/qditp-poc-webadmin-fi3MV3/web-server`（由仓内 `web-server` 复制），在线构建（新依赖需下载，未用 `-o`）。日志 `deps.log` / `build.log` / `build2.log` / `build3.log` / `build4.log` 留在副本目录。
  - 逐项判据：

    | 清单项 | 结果 | 证据 |
    |---|---|---|
    | `mvn clean test-compile`（6 个子模块） | **PASS** | `BUILD SUCCESS`：web-common / web-system / web-framework / web-quartz / web-generator / web-admin 全 SUCCESS |
    | `mvn dependency:tree` 传递依赖版本核对 | **PASS（发现 2 处必排项）** | Boot 4.1.1 统一了 `micro:web` 传递的 webflux / validation / starter / log4j2 / actuator 为 4.1.1；但 `spring-boot-starter-aop:3.2.6`（4.1.1 BOM 已改名 aspectj，无条目可覆盖）与 `knife4j-openapi3-jakarta-spring-boot-starter:4.4.0`（Boot 3 专用，与 springdoc 3.2.0 并存）仍是旧版 |
    | 最小适配验证（阶段 2 前置） | **PASS** | 在 `web-admin` 对 `micro:web` 加上述两条 `<exclusions>` 后重编译仍 `BUILD SUCCESS`，依赖树中两项计数归零 |
    | `--debug` conditions report 验 `WebAutoConfig` / `ProxyWebClient` 实发请求 / `app:"web-server"` 入 VL / Swagger 端点 / Jackson 契约回归 | **BLOCKED（本机）** | 需启动应用并连 Oracle、下游服务、VictoriaLogs；本机 Oracle 不可达，移交测试环境执行 |

  - 编译实证的硬阻塞（比第 2 节静态清单更准）：轮 1 只换 pom 时真实报错为 `DruidConfig` 的 `com.alibaba.druid.spring.boot3.*` 包不存在、`org.springframework.boot.autoconfigure.jackson` 包不存在、`AntPathRequestMatcher` 类不存在、**`DaoAuthenticationProvider()` 无参构造器与 `setUserDetailsService` 被移除**；另 `spring-boot-starter-aop` 在 4.1.1 BOM 无版本条目、`web-generator/pom.xml` 也引用了 druid-3 starter。
  - 最小修复清单（4 轮编译后通过；`rpc 2.0.1` / `micro:web 2.0` 与其余 26 模块均**未改动**）：① `web-server/pom.xml` BOM 3.2.6→4.1.1、删 4 个手工覆盖、第三方换版（druid-4 1.2.28 / pagehelper 4.1.1 / mybatis starter 4.0.1 / springdoc 3.2.0 / jjwt 0.13.0）；② `web-framework` 与 `web-generator` 的 druid artifact 改 boot4；③ `web-framework` 的 starter-aop→starter-aspectj；④ `DruidConfig` import boot3→boot4；⑤ `ApplicationConfig` 移除 `Jackson2ObjectMapperBuilderCustomizer` Bean（正确接口实测在 `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`，Jackson 3 下时区定制需重新设计，非简单改名）；⑥ `SecurityConfig` 换 `org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern()`，并把 `DaoAuthenticationProvider` 改为构造传入 `userDetailsService`；⑦ `ServerApplication` 两个 exclude 类改包（`org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` / `com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure`）。
  - **新增高危静默项**：依赖树同时存在 Jackson 2 `com.fasterxml.jackson.core:jackson-databind:2.21.5` 与 Jackson 3 `tools.jackson.core:jackson-databind:3.1.5`；`SensitiveJsonSerializer` 与 `@JsonSerialize` 属 Jackson 2 体系，Boot 4 自动配置的 `JsonMapper` 不识别 → **敏感字段脱敏可能静默失效**，编译不报错。属安全红线，MUST 迁 `tools.jackson.*` 并人工复核 + 接口 / 异步导出契约回归。另 `application.properties` 的 `spring.autoconfigure.exclude` 仍写旧类名（编译不检查），启动前 MUST 与 `ServerApplication` 同步改。
  - 结论：阶段 1 的「编译 / 依赖解析」判据通过，进一步证实单点升级可行且改动面收敛在 web-server 内部；但全部「运行态」判据未验证，且新增脱敏失效风险，**仍不得据此直接立项发版**。PoC 副本在 `/tmp`，主工作区 pom / 代码 / 配置零改动，无需回滚。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-065`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」（已随阶段 1 PoC 结果回填；立项 / 发版后再更新）
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口等数字已标注来源或「MUST 现查」（3.2.6 / 1.1.60 / 2.0.1 / 2.0 及依赖版本来自本仓 pom；4.0.x / 4.1.x 生命周期与第三方 GA 版本来自 2026-11-07 公开资料检索，发版前 MUST 现查中央仓库；线上版本 MUST 现查）
- 偏差说明：按业主要求「先做计划、不实际执行升级」，仓内未产生任何代码 / 配置改动；阶段 1 PoC 仅在 `/tmp` 隔离副本执行，实测结果已回填第 5 节，运行态判据仍待测试环境。初版「范围天然是全仓」的结论已在第 2 节记录更正过程，避免后续会话沿用旧判断。
