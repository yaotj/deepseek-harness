# MyBatis / Oracle 运行时陷阱（Druid WallFilter 同族）

> **何时读**：改 `mapper` XML / 写 SQL / 动 Oracle 持久层前 **MUST 先读**；排查「编译单测全过、只在生产 Oracle 才炸」的静默失效时也读。
> 这些规则**不再固化进 `AGENTS.md`**（2026-10-15 抽离，遵循上下文工程「常驻上下文只放高海拔规则、细节按需 JIT 加载」原则），改由本文件承载，并经 AGENTS.md §5.1「任务 → 必读路由」按需加载。

## 共同特征（务必先建立的心智模型）

这 6 条陷阱**同族**，根因都是 Druid WallFilter / Oracle / ojdbc / MyBatis 的运行时行为：

- **编译、单测、`xmllint` 全部发现不了**——只在 **Oracle 运行时**炸；
- 多被全局异常处理器包成 **HTTP 200 + UUID `retCode`**（前台只显示「查询失败」）或 **Spring 原生 500 报文**；
- 往往**只在特定输入分支上踩**（如「筛选条件全空」「该列前端传了 null」「`order by` 不在 `<if>` 里永远拼上」），而那一支常是默认加载 / 最常见路径；
- `dm.properties` 在达梦环境关闭了 WallFilter，故 WallFilter 类缺陷**只在 Oracle 生产环境暴露**。

**排查判据总则**：判定「XX 接口报错」时 MUST 落在**服务端日志原文**，NEVER 只看 HTTP 状态码（详见末条）。

## 1. NEVER 在 SQL 正文中写注释

- **NEVER 在 SQL 正文中写注释**（`--` 行注释或 `/* */` 块注释）。全部服务共用 `resource/micro/sql-datasource/src/main/resources/sql.properties` 的 `spring.datasource.druid.filter.wall.enabled=true`，Druid WallFilter 默认 `commentAllow=false`，SQL 带注释会被判定为注入并抛 `SQLException: sql injection violation ... comment not allow`，该语句**静默失效**（编译与单测都发现不了，只在运行时炸）。注释 **MUST** 写在 mapper XML 的 `<!-- -->` 或 Java 注释里。**NEVER** 靠放宽 `wall.config.comment-allow` 绕过——那是全局降低注入防护等级。注意 `dm.properties` 在达梦环境关闭了 WallFilter，因此**该类缺陷只在 Oracle 生产环境暴露**。已发生事故：`PayTxnDetailMapper.updatePayCallback` 的 WHERE 内两行 `--` 注释导致支付成功回调的 UPDATE 全部失败，`PAY_STATUS` 长期卡在 `PROCESSING`、`PAY_TIME` 恒为空（2026-08-25 修复）。

## 2. NEVER 写 `where 1 = 1` 打头 + 全可选 `<if>`

- **同一个 Druid WallFilter 还有第二条规则：NEVER 写 `where 1 = 1` 打头 + 全部谓词都是可选 `<if>`**（2026-09-16 实测，ADR-D97 续）。所有可选条件都不传时，恒真条件成为唯一谓词，Druid 报 `sql injection violation ... select alway true condition not allow`，该查询整条失败、退化成 UUID `retCode`。**MUST 用 MyBatis `<where>` 标签**（无条件时不产出 `WHERE`）。与上面那条注释规则同源、同样只在运行时炸，而且**只在「筛选条件全空」那一支上踩** —— 而那一支往往正是前台首次进页面、不选任何条件的默认加载。已发生：`account-server` 的 `UserItpRegInfoMapper.countGroupByCardType`（综管台注册量统计，不选日期即失败，2.0.73 修复）。

## 3. mapper XML 注释里 NEVER 出现两个连续减号

- **mapper XML 的 `<!-- -->` 注释里 NEVER 出现两个连续减号**。XML 规范禁止注释文本内含连续减号，MyBatis 解析该 mapper 直接失败 ⇒ `sqlSessionFactory` 建不起来 ⇒ **整个服务启动即挂**（`Failed to parse mapping resource: '.../XxxMapper.xml'`；异常链最外层报的是某个无关 mapper 的 `sqlSessionTemplate` 注入失败，极易误判成依赖问题，**MUST 一直往下翻到 `Failed to parse mapping resource` 那一行看文件名**）。这与上一条是**孪生陷阱**：上一条要求把「禁止在 SQL 正文写行注释」的说明写进 XML 注释，而说明里若原样引用那两个符号就会踩本条。已发生（2026-09-11）：`account-server` 2.0.47 因 `AccountExceptionTicketMapper.xml` 的注释里原样写了行注释符号，部署后启动失败、Pod 虽 `2/2 Running` 但端口不监听（表现为 envoy 返 `Connection refused`）。新增或改动 mapper XML 后 **MUST** 跑 `xmllint --noout <file>` 逐个校验。

## 4. 子查询驼峰别名：外层 MUST 引用别名

- **子查询起了驼峰别名时，外层 `<where>` / `order by` MUST 引用别名，NEVER 引用物理列名**（2026-09-20 实测，`daily-ticket-server` 1.0.35 修复）。Oracle 把未加引号的列别名统一折叠成**大写无下划线**（`O.CREATE_TIME as createTime` ⇒ 结果集列名是 `CREATETIME`），于是外层写 `O.CREATE_TIME` 一律 `ORA-00904: "O"."CREATE_TIME": 标识符无效`；`ORDER_NO as orderNo` / `PAYMENT_ORDER_NO as paymentOrderNo` 同理。这是本节前两条 Druid WallFilter 陷阱的**同族第三条**：编译、单测、`xmllint` 全都发现不了，只在 Oracle 运行时炸，且被全局异常处理器包成 **HTTP 200 + UUID `retCode`**，前台只显示「查询失败」。**NEVER 以为「只有带筛选条件那一支才踩」** —— `order by` 不在 `<if>` 里、每次都拼上，因此该查询**任何筛选组合都失败**、功能从上线起从未可用过。已发生：综管台「日票退款」页（`172.20.211.23:30029/trans-operation/daily-ticket-refund` → `GET /page/daily-ticket/refund/orders`）的 `DailyTicketOrderMapper.selectRefundOrders`，UNION ALL 子查询外层 4 个谓词加 `order by` 全按物理列名写，页面 100% 报错。**排查判据**：日志里 `ORA-00904` 指向的表别名若是**子查询自己的别名**（本例是 `) O`），就是本条，MUST 回头看那段 `as` 列表；修法是把外层引用换成别名（`O.createTime`），**NEVER 去动子查询里的别名** —— `resultType` 的字段映射依赖它。

## 5. mapper 写语句参数 MUST 显式写 `jdbcType`

- **mapper 写语句的参数 MUST 显式写 `jdbcType`，NEVER 依赖 MyBatis 推断**（2026-09-23 实测，上述三条的同族第四条）。Java 侧参数为 `null` 且未指定 `jdbcType` 时，MyBatis 按 `JdbcType.OTHER`（type code 1111）下发，Oracle/ojdbc 直接报 `java.sql.SQLException: 无效的列类型: 1111`，被包成 `MyBatisSystemException: null`（**注意 message 是 `null`**，见下一条）。编译、单测、`xmllint` 全都发现不了，**只在 Oracle 运行时炸、且只在「该列前端传了字面 null」那一支上炸** —— 而前端普遍把空白说明/备注 trim 成 `null` 再送。已发生：综管台「风险组管理 / 风险规则管理」（`172.20.211.23:30029/risk/risk-group` → `POST|PUT /page/risk/groups`、`/page/risk/rules`）在描述留空时新增/修改**必现 HTTP 500**，日志原文 `Could not set parameters for mapping: ParameterMapping{property='groupDesc', ... jdbcType=null ...} Cause: ... Error setting null for parameter #2 with JdbcType OTHER`；同一支 XPath 上 `groupId` / `managerCode` / `remark` 三个可空列同样中招，**已于 para-server `2.0.26` 重建镜像并生产复测通过（描述 `null` 的新增/修改 → `200`）**。修法是在 XML 里给每个参数补正确类型（字符串列 `VARCHAR`、数值列 `NUMERIC`），**NEVER 用全局 `mybatis.configuration.jdbc-type-for-null` 兜底或把 null 归一成空串**——前者改的是所有模块的公共行为，后者会改变列语义（NULL 变成空串）。**本项目已有既成约定**：`recon-server/src/main/resources/mapper/ReconPartMapper.xml` 写明「所有参数一律显式写 jdbcType（Oracle 下 null + OTHER(1111) = 无效列类型）」；`para-server` 里此前只有 `RiskGroupMapper` / `RiskRuleMapper` 两个写 mapper 漏写，其余（含 `OrderRefundCycleMapper`、`SingleTicketPurchaseLimitMapper`）均合规。**判据**：新增或改动含可空列的写语句后，MUST 逐个参数确认有 `jdbcType=`。

## 6. 排查「全局异常处理器却返 Spring 原生 500」MUST 先看异常 message 是否为 null

- **连带，排查「明明有全局异常处理器却返 Spring 原生 500 报文」MUST 先看异常自身的 `getMessage()` 是否为 `null`**：`resource/micro/web/.../global/ApiErrorResponse.java` 无条件执行 `ex.getMessage().length() > 20`，而 `MyBatisSystemException`（由 MyBatis 只带 cause 构造）的 message 恒为 `null` ⇒ `@ExceptionHandler(Exception)` 自己抛 `NullPointerException` ⇒ `@RestControllerAdvice` 失效 ⇒ 请求回落到 `BasicErrorController`，最终返 **HTTP 500 + `{"timestamp","status","error","path"}`**（不是项目的 `retCode` 形态、也没有 `msg`，前台只能显示 500）。因此**判定「XX 接口报 500」时判据 MUST 落在服务端日志原文**，NEVER 只看有无 500：**Spring 原生 500 报文 = 全局异常处理器自身失败**（HTTP 状态 500 且 body 无 `retCode`），而 **`code:"500"` 的业务报文是正常的业务拒绝**（如「风险组名称已存在」），两者形态不同、成因不同。该 NPE 是 `resource/micro` 公共构件的既有缺陷，修复需走「`mvn install` 公共构件 + 逐个重建受影响模块镜像」的完整序列（公共构件修复详见 `AGENTS.md §7`），**未做那套重建前 NEVER 认为它已生效**。