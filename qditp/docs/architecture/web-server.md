# web-server / web 详细设计（管理后台）

> 对应 `AGENTS.md` §3.2 中 web-server、web 两行的「详情」。
> 本文档讲**技术结构**；运营页面接口该放哪个模块的约定见 [`../business/admin-web.md`](../business/admin-web.md)。

## 一、定位与关键事实

`web-server` 基于 **RuoYi-Vue3** 改造，前端在 `web/`。

**web-server 目前不调用任何业务服务**：
- 全部子模块 pom **无 `rpc` / `model` / `resource-micro` 依赖**
- 无 `RestTemplate` / `WebClient` / `@FeignClient` / 任何 `XxxClient` 注入
- `application.properties` 中**没有任何 `service.*.url`**
- 唯一 HTTP 工具 `web-common/.../utils/http/HttpUtils.java` 只被 IP 归属地查询 `AddressUtils` 使用

它与业务服务的唯一交集是**共用同一个 Oracle 实例**（`web-admin/src/main/resources/application.properties` 的 `jdbc:oracle:thin:@//...:1521/AFCITPDB`），但当前只读写 `sys_*`。

因此**它不是 BFF，也不是聚合层**，而是一个独立的权限/菜单后台。

## 二、子模块结构

子模块列表见 `web-server/pom.xml`。

| 子模块 | 职责 | 关键内容 |
|---|---|---|
| **web-admin** | 唯一可执行模块 + RuoYi 原生 controller 层 | 启动类 `web-server/web-admin/src/main/java/com/chinasofti/huateng/ServerApplication.java`；端口 **8080**，context-path `/`；**未设置 `spring.application.name`**（仅 `points.name=points`）；controller 分 `system` / `monitor` / `common` / `tool`；集成 Swagger |
| **web-system** | `sys_*` 域的 domain / mapper / service | `SysUserServiceImpl` 等；`src/main/resources/mapper/system/*.xml` |
| **web-quartz** | Quartz 调度与任务 CRUD | `quartz/controller/SysJobController.java` → `/monitor/job` |
| **web-generator** | 代码生成器 | `generator/controller/GenController.java` → `/tool/gen`，Velocity 模板 |
| **web-framework** | Spring Security / JWT、Druid 动态数据源、AOP | 日志 / 数据权限 / 限流切面，全局异常 |
| **web-common** | 基础设施 | 注解、`AjaxResult` / `BaseEntity` / 分页、工具类、XSS 与 Referer 过滤 |

### 依赖方向

```
web-admin → web-quartz / web-generator / web-system → web-framework → web-common
```

新增后台功能 **MUST** 遵循此方向：controller 放 web-admin，domain/service/mapper 放 web-system，通用切面与安全放 web-framework，**NEVER** 让 web-common 反向依赖上层。

## 三、鉴权与数据访问

- 鉴权：Spring Security + JJWT，`token.secret` + `token.expireTime` 配置在 web-admin。
  ⚠️ 现网 `token.secret` 是 26 位连续字母，强度极低；外置与轮换见 [`../ops/敏感配置外置方案.md`](../ops/敏感配置外置方案.md)。
- 数据源：Druid 主从（`spring.datasource.druid.master` / `slave`），slave 当前口令为空、未启用。
  ⚠️ 与业务模块的自研 `other.sql.*` **是两套完全不同的数据源装配**，配置写法不可互相复制。
- Druid 监控台 `statViewServlet` 已启用且口令为弱口令，建议关闭外网暴露。

## 四、数据表

- `sys_*`：用户 / 角色 / 菜单 / 部门 / 字典 / 参数 / 日志
- `gen_table`、`gen_table_column`：代码生成器元数据
- `QRTZ_*`：Quartz 调度（`web-server/sql/quartz.sql`）

⚠️ `web-server/sql/*-menu.sql` 中出现的业务表名（`TBL_TVM_ORDER_PAY`、`ALIPAY_USER_INFO`、`USER_ITP_REG_INFO`、`QRCODE_STATUS`）**只是菜单种子数据的注释**，web-server 后端没有对应实现，勿据此推断。

## 五、前端 web/

```
web/src/
  api/       login.js  menu.js  system/  monitor/  tool/  para/  trans/
  views/     页面
  layout/  components/  router/  store/(Pinia)  directive/  plugins/  utils/
  main.js  permission.js  settings.js  App.vue
```

- `api/system|monitor|tool` 对应 web-admin 的 RuoYi 原生接口；
- `api/para`、`api/trans` 是本项目新增的业务查询页面，其后端在 **para-server / ticket-server 等业务模块的 `/page/**`**，经反向代理转发（`web/vite.config.js`、`web/nginx.conf`）。
- 环境配置：`.env.development` / `.env.staging` / `.env.production`
- 规范：**MUST** 用 Vue 3.5 Composition API（`<script setup>`）+ Element Plus；新增页面 **MUST** 同时补菜单 SQL，写法参考 `web-server/sql/*-menu.sql`。

## 六、常见踩坑点

- web-server 端口 8080 与三个支付宝模块冲突，同机启动必冲突。
- 新增运营查询接口容易误放进 web-server（因为菜单在这里），但**后端约定在业务模块的 `/page/**`**，放错会导致 web-server 反向依赖 rpc / model，破坏现有边界。
- 代码生成器生成的模板是 RuoYi 风格（标准 MyBatis + `sys_*` 约定），**NEVER** 用它生成业务模块代码 —— 业务模块必须走自研 `mybatis-adaptor`。
- 多个 properties 明文含数据库与 Druid 口令，触碰 **MUST** 提示人工复核，**NEVER** 回显口令值。

## 七、相关文档

- 运营页面接口归属约定：[`../business/admin-web.md`](../business/admin-web.md)
- 架构总览与端口：[`overview.md`](overview.md)
- 公共构件：[`common-components.md`](common-components.md)
