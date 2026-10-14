# 基于 Spring Cloud Gateway 构建网关：能实现什么功能 + 最佳实践

> 本文基于官方文档 **Spring Cloud Gateway 5.0.3** 整理，结合 qd-itp 项目实际架构（Java 21 / Spring Boot 3.2.6 / 无注册中心 / K8s+Istio）给出可落地的功能清单与最佳实践。
> 官方文档离线版见同目录 `index.html`；设计文档「档 3：SCG 透明网关」见 `docs/design/用户分流渐进迁移设计.md` §17。
>
> **选型结论（2026-10-13 定稿）**：本项目的"SCG 透明网关"采用 **Web MVC 版（`spring-cloud-starter-gateway-server-webmvc`）**，理由：
> 1. 理论峰值 **< 5k QPS**，Web MVC 版线程池在中等流量下足够（配合 `FormFilter` 原生支持 form 报文）；
> 2. qd-itp 报文为 `application/x-www-form-urlencoded`，Web MVC 版内置 `FormFilter` 直接解决 form 参数合并坑；
> 3. 同步编程模型 + Tomcat/Jetty 运行时，团队理解成本低、交付快、调试友好；
> 4. 金融场景的长尾 RT（冷启动 2785ms）用 **线程池调优 + 下游超时兜底** 控制，比 WebFlux 版的非阻塞模型更直观可维护。
>
> WebFlux 版仍作为后续大流量（>5k QPS）升级路径保留（见 §七）。
>
> **🔴 红线（金融级，MUST 守住）**：网关连库**只读不写**——查 `USER_ITP_REG_INFO`（账号注册表）`third_user_id` **是否存在任意记录**（不看 `DEL_YN`）判断流量分发到旧/新系统（存在→新 ITP，无记录→旧 ITP），**绝不执行 INSERT/UPDATE/DELETE**。账号用专用只读账号；写库会把网关可用性押在 DB 上，且网关作为流量入口，任何写操作失败都会放大成网关雪崩。落码时 SQL 只允许 `SELECT`，连接池/账号/网络策略三处 MUST 校验无写权限。

---

## 〇、WebFlux 版 vs Web MVC 版选型（本项目）

| 维度 | WebFlux 版（经典） | Web MVC 版（本项目选用） |
|---|---|---|
| starter | `spring-cloud-starter-gateway-server-webflux` | `spring-cloud-starter-gateway-server-webmvc` |
| 运行时 | Netty（仅 NIO 事件循环） | Tomcat/Jetty（线程池） |
| 客户端 | Reactor Netty `HttpClient`（异步非阻塞） | Spring `RestClient`（同步阻塞） |
| 编程模型 | `Mono`/`Flux` 响应式 | 同步（`ServerRequest`/`ServerResponse`） |
| form 报文 | 需 `AdaptCachedBodyGlobalFilter` 兜底 | **`FormFilter` 内置，原生支持** |
| 线程占用 | 少量事件循环线程（不随 QPS 增） | 每请求 1 线程（`tomcat.threads.max` 默认 200） |
| 单实例 QPS 上限 | 1~3w（Netty 连接池） | `200 / RT`（RT=100ms 时 ~2000） |
| 长尾慢调用 | 不占线程，稳 | 占线程，RT 越长线程池越容易满 |
| 调试 | 异步 trace 易断链 | 同步 stack trace 完整 |
| 学习成本 | 高（Reactor 模型） | **低（Servlet 日常）** |
| 未来趋势 | 官方主线，长期维护 | 实验性（5.0.x），可能弃 |

**结论**：理论 < 5k QPS + form 报文 + 团队 Servlet 日常 ⇒ **Web MVC 版交付快、理解成本低**。代价是慢调用 RT 下线程池风险（见 §六 上线前 4 条 MUST）。

---

## 一、SCG 能实现什么功能

### 1.1 路由（Route）—— 核心能力

SCG 的本质是 **WebFlux 上的路由网关**：`Gateway Handler Mapping` 判断请求是否匹配某个 Route，命中后交给 `Gateway Web Handler` 走过滤器链。

一个路由由三要素组成：

| 要素 | 作用 | 配置示例 |
|---|---|---|
| `id` | 路由唯一标识 | `my_route` |
| `predicates`（断言） | 匹配条件，全部满足才命中 | `Path=/api/**` |
| `filters`（过滤器） | 请求/响应增强 | `StripPrefix=1` |
| `uri` | 转发目标 | `http://backend:8080` |

### 1.2 路由断言工厂（Predicate Factories）—— 路由匹配规则

| 断言 | 用途 | 示例 |
|---|---|---|
| `Path` | 路径匹配（Ant 风格，支持 `{segment}` 模板变量） | `Path=/red/{segment},/blue/**` |
| `Method` | HTTP 方法匹配 | `Method=GET,POST` |
| `Header` | 请求头匹配（正则） | `Header=X-Request-Id, \d+` |
| `Cookie` | Cookie 匹配（正则） | `Cookie=chocolate, ch.p` |
| `Query` | 查询参数匹配（正则） | `Query=green` |
| `Host` | Host 头匹配（Ant 风格） | `Host=**.somehost.org` |
| `RemoteAddr` | 客户端 IP（CIDR） | `RemoteAddr=192.168.1.1/24` |
| `XForwardedRemoteAddr` | 基于 X-Forwarded-For 的 IP 白名单 | `XForwardedRemoteAddr=192.168.1.1/24` |
| `After/Before/Between` | 时间窗（ZonedDateTime） | `Between=...,...`（维护窗口） |
| `Weight` | 按权重分流（灰度） | `Weight=group1, 8` |
| `Version` | API 版本匹配（`spring.webflux.apiversion.*` 解析版本） | `Version=1.1+`（基线版本） |
| `ReadBody` | 读请求体并做自定义判断（仅 Java DSL） | `.readBody(String.class, body -> body.contains("yes"))` |

> **关键特性**：多个断言之间是 **AND** 关系；`Path`/`Host` 断言支持提取 URI 模板变量（如 `{segment}`），供后续过滤器使用（`ServerWebExchangeUtils.getUriTemplateVariables`）。

### 1.3 过滤器工厂（GatewayFilter Factories）—— 请求/响应加工

**请求增强类**：
- `AddRequestHeader` / `AddRequestHeadersIfNotPresent` / `SetRequestHeader`：加/改写请求头
- `AddRequestParameter` / `RewriteRequestParameter`：加/改写查询参数
- `ModifyRequestBody`：改写请求体（如加签）
- `PreserveHostHeader`：透传原始 Host
- `SetRequestHostHeader`：改写转发 Host

**响应增强类**：
- `AddResponseHeader` / `SetResponseHeader`：加/改写响应头
- `ModifyResponseBody`：改写响应体
- `DedupeResponseHeader`：去重响应头
- `RemoveJsonAttributesResponseBody`：移除响应体 JSON 字段
- `LocalResponseCache`：本地响应缓存（Caffeine，GET + 200/206/301 + 允许缓存时生效）

**路由重写类**：
- `RewritePath`：正则重写路径（`/consumingServiceEndpoint` → `/backingServiceEndpoint`）
- `PrefixPath`：加前缀
- `StripPrefix`：去前缀
- `SetPath`：直接替换路径
- `RedirectTo`：重定向
- `SetStatus`：设置响应状态码

**容错与弹力**：
- `CircuitBreaker`：熔断（Resilience4J），支持 `fallbackUri`（`forward:`）、按状态码触发、`resumeWithoutError`
- `Retry`：重试（支持指数退避 `backoff`、抖动 `jitter`、超时 `timeout`）
- `RequestRateLimiter`：限流（Redis Token Bucket 或 Bucket4j）
- `FallbackHeaders`：熔断时把异常信息写入响应头

**安全类**：
- `SecureHeaders`：自动添加安全响应头（X-Content-Type-Options 等）
- `TokenRelay`：OAuth2 token 中继（配合 Spring Security）

**其他**：
- `SaveSession`：保存会话状态
- `RequestHeaderSize`：请求头大小限制
- `RequestSize`：请求体大小限制
- `JsonToGrpc`：JSON 转 gRPC

### 1.4 全局过滤器（Global Filters）—— 跨路由横切

全局过滤器对所有路由生效，与路由过滤器**合并后按 `Ordered` 排序**（pre 阶段顺序正排，post 阶段逆排）。

内置关键全局过滤器：

| 过滤器 | 作用 |
|---|---|
| `RouteToRequestUrlFilter` | 根据 Route 的 URI 构造请求 URL（order 10000） |
| `ReactiveLoadBalancerClientFilter` | 解析 `lb://` 服务名（order 10100） |
| `NettyRoutingFilter` | 用 Netty HttpClient 转发请求（order 2147483647） |
| `NettyWriteResponseFilter` | 把下游响应写回客户端（order -1） |
| `ForwardRoutingFilter` | 处理 `forward://` 到本地 DispatcherHandler |
| `WebsocketRoutingFilter` | WebSocket 转发（`ws://` / `wss://`） |
| `GatewayMetricsFilter` | 指标埋点（`/actuator/metrics/spring.cloud.gateway.requests`） |
| `LocalResponseCache` | 本地响应缓存（Caffeine） |

**`lb://` 负载均衡**：通过 `ReactiveLoadBalancerClientFilter`，URI 写 `lb://service` 即可按服务名从注册中心（Eureka/Nacos/Consul）解析实例。**本项目无注册中心，用不上 `lb://`，直接写 HTTP 地址即可。**

### 1.5 其他重要能力

| 能力 | 说明 |
|---|---|
| **CORS** | 全局（`spring.cloud.gateway.server.webflux.globalcors.cors-configurations`）或按路由（metadata.cors）配置 |
| **超时控制** | 全局（`spring.cloud.gateway.httpclient.connect-timeout` / `response-timeout`）或按路由（metadata 里的 `response-timeout` / `connect-timeout`） |
| **Actuator 管理** | `/actuator/gateway/routes`、`/actuator/gateway/refresh`、`/actuator/gateway/globalfilters` 等，可动态增删路由（需设 `management.endpoint.gateway.access=read-only` 或 `unrestricted`） |
| **TLS/SSL** | 网关入口 HTTPS（`server.ssl.*`）；到后端 HTTPS 可配信任证书或 `useInsecureTrustManager`（生产禁用） |
| **配置来源** | `PropertiesRouteDefinitionLocator`（yaml/properties）+ Java DSL（`RouteLocatorBuilder`）+ Actuator 动态路由 |
| **AOT/原生镜像** | 支持 GraalVM Native Image（有约束，见官方文档） |

---

## 二、基于 SCG 能做什么（典型场景清单）

1. **统一入口 / 反向代理**：所有微服务请求先到网关，按路径转发到不同后端。
2. **灰度发布（暂不做，留作升级路径）**：`Weight` 断言可按比例灰度；`Header`/`Cookie`/`RemoteAddr` 可按用户特征灰度。**本项目当前档 3 保持"名单二选一"最简形态（命中 LEGACY 走旧、不命中走新），不引入 `Weight` 比例路由**；将来要"先 1% 再逐步放大"时再加 `Weight=group1, N` 断言（零新依赖，SCG 内置）。
3. **按用户分流**：`ReadBody` 断言 + 自定义 `GlobalFilter` 查名单，改写转发目标（**qd-itp 档 3 方案正落这里**）。
4. **API 版本管理**：`Version` 断言按 `X-API-Version` 头 / query 参数 / path 段路由到不同版本后端。
5. **鉴权/认证**：`GlobalFilter` 校验 token/sign，`TokenRelay` 透传 OAuth2 token。
6. **限流**：`RequestRateLimiter`（Redis/Bucket4j）按用户/接口限流，返回 429。
7. **熔断降级**：`CircuitBreaker` + fallback 控制器/外部 fallback 服务。
8. **重试**：`Retry` 对 5XX/异常自动重试（GET 默认），指数退避。
9. **请求/响应改写**：加头、改路径、加前缀、去前缀、改写 body（加解密/加签）。
10. **缓存**：`LocalResponseCache` 对只读接口做本地缓存。
11. **CORS 统一处理**：集中配置跨域策略。
12. **可观测性**：Actuator 指标 + Micrometer 集成 Prometheus/Grafana。
13. **动态路由**：`/actuator/gateway/routes` + `refresh` 事件，不重启更新路由。

---

## 三、最佳实践（含本项目落点）

### 3.1 本项目（qd-itp）引入 SCG 的最佳实践

项目已确认的档 3 方案（`用户分流渐进迁移设计.md` §17）是 SCG 的正确打开方式：

**适用场景**：用户分流渐进迁移（旧/新用户灰度），fep-app 完全不动。

**架构**：
```
APP 请求 → k8s Service scg-gateway:8080 → SCG（GlobalFilter order=-1 读 body 取 thirdUserId → 查名单）
                                          ├── LEGACY → 旧系统
                                          └── NEW    → fep-app-svc:9101
```

**4 条硬约束（踩坑预警）**：
- **C1 响应式 API**：名单查询 MUST 用 `WebClient` / `ReactiveRedisTemplate`，`GlobalFilter` 里**严禁阻塞调用**（阻塞 = QPS 直降）。
- **C2 body 缓存**：`ReadBody` 会把 body 缓存到 exchange 属性，**大 body 会吃内存**，bizData 建议设上限（≤10KB）。
- **C3 动态路由刷新**：改 Nacos 配置后 MUST 发 `RefreshRoutesEvent` 并清 `CachingRouteLocator`，否则改了不生效。
- **C4 线程安全**：`GlobalFilter` 是单例，内部缓存用 Caffeine / ConcurrentHashMap。

### 3.2 通用最佳实践清单

**配置与路由**
- 路由配置优先用 **Java DSL**（`RouteLocatorBuilder`），复杂逻辑（ReadBody、自定义 predicate）必须用代码。
- 简单转发用 yaml 即可；但**路由是配置的一部分，建议纳入版本管理**。
- 使用 `Path` 断言时注意 `matchTrailingSlash` 默认 true，会影响尾斜杠匹配。
- 多个路由注意 `order` 优先级，`order` 越小越先匹配（默认 0）。

**过滤器链顺序**
- 自定义 `GlobalFilter` 用 `@Order` 或实现 `Ordered`；鉴权/名单类 filter **放最前（order 越小越先执行）**。
- pre 逻辑在 `chain.filter(exchange)` 之前，post 逻辑在之后（用 `Mono.defer` 或 `then`）。

**弹性设计**
- 外部依赖（名单查询/Redis）**必须 fail-safe**：查询失败走默认分支（本项目 = 走 LEGACY/旧 ITP 承接），并打告警。
- 熔断用 `CircuitBreaker` + `fallbackUri`，fallback 页/接口要单独路由。
- 重试只对**幂等请求**（GET）开启，带 body 的 POST 重试会缓存 body 增加内存压力。
- 限流 `KeyResolver` 自定义（按用户/按 IP），生产不要用 query 参数做 key（文档明示 not recommended）。

**安全**
- Actuator 的 `/gateway` 端点**生产 MUST 设 `access=read-only`**（避免动态删改路由），且要鉴权保护。
- HTTPS 入口按 `server.ssl.*` 配置；到后端 HTTPS 生产用受信证书，**禁用 `useInsecureTrustManager`**。
- 部署在代理后时，真实客户端 IP 用 `XForwardedRemoteAddressResolver.maxTrustedIndex(N)`（N=信任代理跳数），不要信任裸 X-Forwarded-For。

**性能（Web MVC 版适用）**
- Web MVC 版用同步 `RestClient`，**调用阻塞是正常行为**，不需要"全改响应式"（那是 WebFlux 版的铁律）。
- 真正要管的是**线程池 + 下游超时**：
  - 调 `server.tomcat.threads.max`（默认 200）按 `峰值 QPS × 下游 P99 RT` 估算；
  - 给 `RestClient` 设 `connect-timeout` + `response-timeout`（或 `RestClient` 的 timeout builder），避免慢调用占线程；
  - 名单查询等外部调用必须设**硬超时**，超时走 fail-safe 默认分支（走旧 ITP）。
- 大 body 转发的路由要设 body 上限（`RequestSize` 过滤器），避免内存堆积。
- 网关层只做**轻量横切**，重逻辑下沉到业务服务。

**form 报文（Web MVC 版专属要点）**
- qd-itp 是 `application/x-www-form-urlencoded` 报文。Web MVC 版内置 **`FormFilter`** 专门解决"Servlet 容器把 form 参数合并到 query 参数、重建 form body 给下游"的坑。
- **任何自定义 `jakarta.servlet.Filter` 若在读请求参数，MUST 排在 `FormFilter` 之前**（`getOrder() = FormFilter.FORM_FILTER_ORDER - 1`），否则 form body 已被消费无法重建。
- 读 form body 做名单解析：用 `@RequestParam` / `request.getParameter("bizData")` 即可，不需要 WebFlux 版的 `AdaptCachedBody` 兜底。

**可观测性**
- 加 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`，用 `spring.cloud.gateway.requests` 指标（routeId/status/httpMethod 标签）做监控告警。
- 开启 `spring.cloud.gateway.metrics.enabled=true`，配合 Grafana 仪表盘。
- **Web MVC 版线程池监控**：暴露 Tomcat `executor` 指标（`activeCount`/`queueSize`），线程池打满 = QPS 上限到了，是比"网关 p99 变慢"更早的预警信号。

**与项目现状的冲突点（NEVER 踩）**
- qd-itp **无注册中心**，SCG 路由 URI **直接写 HTTP 地址**（如 `http://fep-app-svc:9101`），不要用 `lb://`。
- qd-itp 服务间调用是 `rpc` 模块 `ProxyWebClient` HTTP 直连 + `service.*.url` 硬编码。SCG 只做**入口网关**，不要试图接管所有服务间调用（改动面爆炸）。
- 入向已有 Istio VS（`fep-app-vr`）做流量调度。SCG 应接在 VS 之后作为**透明代理层**，不要和 VS 抢路由职责。

---

## 四、关键配置速查（可直接抄）

### 4.1 基础路由（yaml）
```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: fep-app-route
              uri: http://fep-app-svc:9101
              predicates:
                - Path=/itptvm/**,/itpbom/**
              filters:
                - StripPrefix=0
```

### 4.2 按用户分流（本项目档 3 核心，Java DSL）
```java
@Bean
public RouteLocator userRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("user-route", r -> r
            .path("/itptvm/**", "/itpbom/**")
            .filters(f -> f
                .rewritePath("/itptvm/(?<seg>.*)", "/$0"))  // 按需改写
            .uri("http://fep-app-svc:9101"))
        .build();
}
```
> 名单查改写逻辑放在 `GlobalFilter`（order=-1）里，用 `ServerWebExchangeUtils` 改请求 URL 属性，而不是在 RouteLocator 里写死。

### 4.3 全局超时
```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 1000        # 毫秒
        response-timeout: 5s           # Duration
```

### 4.4 按路由超时（metadata）
```yaml
- id: per_route_timeouts
  uri: http://fep-app-svc:9101
  predicates:
    - Path=/delay/**
  metadata:
    response-timeout: 200
    connect-timeout: 200
```

### 4.5 限流（Redis）
```yaml
filters:
  - name: RequestRateLimiter
    args:
      key-resolver: "#{@userKeyResolver}"       # 自定义 Bean
      redis-rate-limiter.replenishRate: 10
      redis-rate-limiter.burstCapacity: 20
      redis-rate-limiter.requestedTokens: 1
```

### 4.6 熔断 + fallback
```yaml
filters:
  - name: CircuitBreaker
    args:
      name: myCircuitBreaker
      fallbackUri: forward:/inCaseOfFailureUseThis
      statusCodes:
        - 500
        - "NOT_FOUND"
```

### 4.7 Actuator 动态路由（生产只读）
```properties
management.endpoint.gateway.access=read-only
management.endpoints.web.exposure.include=gateway,health,info,metrics
```

---

## 五、常见坑（Troubleshooting）

| 坑 | 原因 | 解决 |
|---|---|---|
| 路由不生效 | 路由配置在 yaml 但被 `spring.cloud.gateway.server.webmvc.enabled=false` 关闭 | 检查开关（注意 Web MVC 版属性键是 `webmvc` 不是 `webflux`） |
| 改配置不生效 | 路由缓存未清 | POST `/actuator/gateway/refresh` 或发 `RefreshRoutesEvent` |
| **form 参数丢失** | Servlet 容器把 form 参数合并到 query，下游只收到 query 没有 body | **必须保留 `FormFilter`**；自定义 `Filter` 若读参要排在 `FormFilter` 之前 |
| **线程池打满（503/排队）** | 下游 RT 长 + Tomcat `threads.max` 太小 | 调 `server.tomcat.threads.max`；给 `RestClient` 设硬超时；名单查询超时 fail-safe 走旧 |
| 502/504 | 后端超时/连接失败 | 调 `RestClient` 的 `connect-timeout`/`response-timeout`；检查后端可达性 |
| 下游收到错误 Host | 未设 `PreserveHostHeader` | 按需加该过滤器 |
| 响应头重复 | 下游和网关都加 | `DedupeResponseHeader` 去重 |
| 大请求体 OOM | 未限 body 大小 | `RequestSize` 过滤器 |
| 真实 IP 错误 | 代理层后未解析 XFF | `XForwardedRemoteAddressResolver.maxTrustedIndex(N)` |
| `@LoadBalanced` RestClient 冲突 | 自定义 `@LoadBalanced RestClient.Builder` 拦截了网关内部 proxy 调用 | 网关路由只走 `lb()` 过滤器 / `lb://`，自定义 RestClient 不要加 `@LoadBalanced` |

---

## 六、结论

1. **SCG 能做的**：统一入口、路由转发、灰度/分流、API 版本、鉴权、限流、熔断、重试、请求响应改写、缓存、CORS、可观测性、动态路由——一个成熟 API 网关该有的能力两版都有。
2. **在 qd-itp 的正确用法**：在入口（Istio VS 之后）加一层透明 SCG，做「按用户分流」这类需要业务感知的横切；服务间调用维持现状。
3. **选型**：**Web MVC 版**（理论 < 5k QPS + form 报文 + 团队 Servlet 日常 ⇒ 理解成本低、交付快）。
4. **落地路径**：按 §七 的 Web MVC 版落地清单执行，4 条上线前 MUST（线程池/超时/FormFilter/Actuator）是 P0。

---

## 七、Web MVC 版落地清单（本项目实际写码对照）

### 7.1 依赖（网关新服务，全用 2026-10 最新稳定版）

> 网关是**新容器新服务**，不跟 fep-app 存量版本（Spring Boot 3.2.6 / JDK 21）捆绑，按最新版本走。
> 版本对应关系（2026-10 实测最新稳定）：
> - Spring Cloud Gateway **5.0.3**（stable，2026-08-20）→ Spring Cloud 2025.0.x → **Spring Boot 4.0.x** → **JDK 25**（LTS）
> - Druid **1.2.28**（2026-03-10，官方首个支持 Spring Boot 4 的版本，配 `druid-spring-boot-4-starter`）
> - Caffeine 用 Spring Boot 4 BOM 管的最新稳定版（3.2.x）

```xml
<!-- 父 BOM（管 Spring Boot 4 + Spring Cloud 2025.0 系） -->
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.8</version>   <!-- Spring Boot 4.0.x 最新稳定（与 SCG 5.0.3 对齐） -->
  <relativePath/>
</parent>
<properties>
  <java.version>25</java.version>   <!-- Spring Boot 4 / SCG 5.x 要求 JDK 25 -->
  <spring-cloud.version>2025.0.x</spring-cloud.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>${spring-cloud.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- 1) SCG Web MVC 网关（5.0.3 stable） -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
  <!-- 版本由 spring-cloud-dependencies BOM 管（5.0.3），无需显式写 -->
</dependency>

<!-- 2) 名单查库：JdbcTemplate + Oracle 驱动（最新版） -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
  <groupId>com.oracle.database.jdbc</groupId>
  <artifactId>ojdbc11</artifactId>   <!-- 最新 Oracle JDBC，19.18+ 兼容，跟 BOM 管或显式 23.x -->
</dependency>

<!-- 3) HikariCP 连接池（Spring Boot 默认池，7.x 由 BOM 管，不额外加依赖） -->
<!--    网关只查一张名单表（单一读 SQL），不需要 Druid 的 SQL 监控/防火墙/WallFilter -->
<!--    不引 druid-spring-boot-4-starter（Druid 1.2.28 是 Boot 4 过渡版，维护节奏不稳） -->
<!-- 版本由 Spring Boot 4 BOM 管（HikariCP 7.x），无需显式写 -->

<!-- 4) Caffeine 本地缓存（挡名单 DB 查询；版本由 Spring Boot 4 BOM 管，3.2.x） -->
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>

<!-- 5) Actuator（Spring Boot 内置）：/actuator/gateway/routes + refresh，DBA 直接维护路由 URL -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- 6) 熔断/限流（可选，按需）：Resilience4J + Bucket4j（随 SCG 5.0.3 BOM） -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

> **版本定版说明**：
> - **SCG 5.0.3 是 5.0.x 最新 stable**（5.1.0-M1 是 preview，不稳定，不用）；对应 Spring Cloud 2025.0.x + Spring Boot 4.0.x + JDK 25。
> - **连接池用 HikariCP 7.x（Spring Boot 默认池）**，**不用 Druid**：① 网关只查一张名单表（单一读 SQL），不需要 Druid 的 SQL 监控/防火墙/WallFilter 独有能力；② HikariCP 是 Spring Boot 默认连接池，不额外加依赖、版本由 BOM 管；③ HikariCP 维护节奏稳定（brettwooldridge 长期活跃），Druid 1.2.28 是 Boot 4 过渡版、维护节奏不稳（1.2.24→1.2.23 隔 1 年）。**fep-app 业务模块继续用 Druid（要 SQL 监控），网关新服务用 HikariCP，各服务连接池独立，互不影响。**
> - **ojdbc11**（不是 ojdbc8）是 Oracle 当前推荐驱动，跟 Spring Boot 4 BOM 走；若 BOM 没管则显式写 `com.oracle.database.jdbc:ojdbc11:23.x`。
> - 所有版本优先走 **Spring Boot 4 + Spring Cloud 2025.0 BOM 管理**，能省掉的显式 version 都省（避免手填错）。

> **为什么直连 DB 可行**：SCG Web MVC 版是**同步模型**，`RequestPredicate`/`HandlerFilterFunction` 里调用 `JdbcTemplate` 查库是天然同步阻塞，不需要 WebFlux 版那种"必须响应式、不能阻塞"的纠结。代价是每请求占一个 Tomcat 线程 + 一条 DB 连接，靠 **Caffeine 本地缓存 + Druid 连接池上限** 控住（见 §7.5/§7.6）。
>
> **不加 Nacos**：出口 URL / 来源路径全由**两条路由**承载，DBA/运维直接调 Actuator API 维护（§7.8），**不扫表、不落库、不接 Nacos**。名单查询仍走 DB（`USER_ITP_REG_INFO` 存在性，Caffeine 挡；**不建独立名单表**，2026-10-14 定案），但**出口 URL 的维护完全交给 Actuator**。

### 7.2 路由 DSL（两条路由，Java）

> **动态维护说明（Web MVC 版）**：Web MVC 版路由以 Java Bean 注册，**不支持运行时动态增删改**。出口 URL / 来源路径全由**两条路由**承载（`@Value` 读 `user-route.*` 配置），DBA/运维改 `application.yml` 的 `user-route` 块后 **`kubectl rollout restart`** 生效（~30s，不扫表、不动内存）。WebFlux 版（§7.8 升级后）才支持 Actuator 动态路由 API。
>
> 路由定义在 `application.yml` 的 `user-route` 块里给默认值（启动兜底），DB 表 `GATEWAY_ROUTE_CONFIG` **不再需要**（除非要留审计/备份，那也只是 DBA 手工记录，不参与路由链路）。

```java
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.method;

@Configuration
class UserRouteConfig {

    @Autowired private UserRouteResolver legacyResolver;   // 查名单判断是否 LEGACY（§7.3）

    /**
     * 路由B（LEGACY）：来源路径 + 名单命中才进来。order 更高（先匹配）。
     * 名单不命中 → 不匹配 B → 落到 A（NEW）。
     * 旧出口 = 这条路由的 uri（DBA 通过 Actuator 改这条的 uri 即可）。
     */
    @Bean
    @Order(0)
    public RouterFunction<ServerResponse> legacyRoute() {
        return route("legacy-route")
            .route(path("/itptvm/**", "/itpbom/**")
                     .and(legacyResolver.isLegacyPredicate()),   // 自定义断言：名单命中才匹配
                     http())
            .before(uri("http://legacy-system:8080"))            // 旧出口（Actuator 可改）
            .build();
    }

    /**
     * 路由A（NEW，默认全量）：来源路径直接转发到新系统。order 低（后匹配）。
     * LEGACY 没命中 / 总开关关 / legacy-route 被删 → 全落到这里。
     * 新出口 = 这条路由的 uri（DBA 通过 Actuator 改这条的 uri 即可）。
     */
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> newRoute() {
        return route("new-route")
            .route(path("/itptvm/**", "/itpbom/**"), http())
            .before(uri("http://fep-app-svc:9101"))              // 新出口（Actuator 可改）
            .build();
    }
}
```

> **关键点**：出口 URL 现在**就是两条路由的 `uri(...)`**，DBA 调 Actuator 改/删/增路由即直接生效，**不再有任何内存快照/扫表逻辑**。`UserRouteResolver` 只做一件事：判断当前请求的 `thirdUserId` 是否命中 LEGACY 名单（返回一个 `RequestPredicate`），命中走 B、不命中走 A。名单查询仍走 DB（Caffeine 挡），但**出口 URL 的维护完全交给 Actuator**。

### 7.3 名单断言（查 DB 判断 LEGACY，不改 URL + DB 熔断 + 自动回切）
```java
@Component
public class UserRouteResolver {

    private final JdbcTemplate jdbc;                          // Spring Boot 自动配置（HikariCP 连接池）
    private final Cache<String, Boolean> legacyCache;          // Caffeine，key=thirdUserId, value=是否LEGACY

    // ── DB 熔断（金融级：库挂了网关还能 fail-safe 走旧 ITP）──
    // 连续 N 次 DB 查询失败/超时 → 熔断短路（不再打 DB，Caffeine 旧值继续用），DB 恢复后自动回切
    private final AtomicLong dbFailStreak = new AtomicLong(0);          // 连续失败计数（成功清零）
    private volatile long circuitOpenUntil = 0;                          // 熔断截止时刻（ms），>now 表示短路中

    @Value("${user-route.db.fail-threshold:3}")    int failThreshold;   // 连续 N 次失败触发熔断
    @Value("${user-route.db.circuit-open-ms:30000}") long circuitOpenMs; // 熔断持续 30s 后试探回切
    @Value("${user-route.db.half-open-probe:1}")  int halfOpenProbe;   // 半开试探：放行 1 个请求探测 DB 是否恢复

    public UserRouteResolver(JdbcTemplate jdbc,
                             @Value("${user-route.cache.ttl-seconds:30}") int ttl) {
        this.jdbc = jdbc;
        this.legacyCache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofSeconds(ttl))
            .build();
    }

    /**
     * 冷启动预热（M9）：启动时把注册表全量 third_user_id（在表=走新）load 进 Caffeine。
     * 避免启动后前几秒的"冷穿透"（大量 uid 同时未命中 → 瞬间打爆业务库连接池）。
     *
     * 策略占位（开发时定，当前最简实现）：
     *   - 数据源：USER_ITP_REG_INFO WHERE del_yn=1 全量拉
     *   - 分批：BATCH_SIZE=5000，循环 SELECT，防单条大查询 + 控制启动耗时
     *   - 幂等：@PostConstruct 跑一次；DB 失败 → 降级不预热（让 Caffeine 空启动，靠流量回补）
     *   - 完成后打日志 "Caffeine preloaded N entries"（N 应 ≈ DEL_YN=1 用户基数，可核对）
     */
    @PostConstruct
    public void preload() {
        int total = 0;
        try {
            int batchSize = 5000;
            long lastId = 0;   // 若表有自增主键/ROWID 可分页，无则全量单次（量小时）
            List<Map<String, Object>> rows;
            do {
                rows = jdbc.queryForList(
                    "SELECT third_user_id FROM user_itp_reg_info " +
                    "WHERE del_yn = 1 FETCH FIRST ? ROWS ONLY",
                    batchSize);
                rows.forEach(r -> legacyCache.put((String) r.get("third_user_id"), Boolean.TRUE));
                total += rows.size();
                // 无 ROWID/自增时只能单次全量拉，这里简化为一次（量级 < 100w 可接受）
            } while (rows.size() == batchSize);   // 占位：真分页需要 ORDER BY 某稳定列 + OFFSET/KEYSET
            log.info("Caffeine preloaded {} active users", total);
        } catch (Exception e) {
            log.warn("preload failed, Caffeine starts cold; rely on traffic backfill", e);
            // 不抛异常 → 网关正常启动，未命中 uid 走流量回补（fail-safe 语义不变）
        }
    }

    /**
     * 返回一个 RequestPredicate：thirdUserId 在 USER_ITP_REG_INFO 且 DEL_YN=1（有效在网）才匹配（给 legacy-route 用）。
     * Web MVC 版 RequestPredicate 是同步的（ServerRequest -> boolean），查 DB 直接阻塞即可。
     */
    public RequestPredicate isLegacyPredicate() {
        return serverRequest -> {
            // 1) 读 form 报文 bizData（Servlet 容器已合并 form 参数到 request parameters）
            String bizData = serverRequest.param("bizData").orElse("");
            String thirdUserId = JSON.parseObject(bizData).getString("thirdUserId");
            if (thirdUserId == null) return false;            // 缺失 → 不命中 → fail-safe 走旧 ITP（非 legacy）

            // 2) 熔断判断：短路中（库挂了）→ 不打 DB，用 Caffeine 旧值；无旧值 → fail-safe 走旧 ITP
            boolean circuitOpen = System.currentTimeMillis() < circuitOpenUntil;
            if (circuitOpen) {
                Boolean cached = legacyCache.getIfPresent(thirdUserId);   // 只读缓存，不 load（不查 DB）
                return Boolean.TRUE.equals(cached);                       // 无缓存 → null → false（走 NEW）
            }

            // 3) 查注册状态：Caffeine 优先；未命中查 DB（带短超时）；查询失败 → 累加熔断计数 + fail-safe 走旧 ITP
            try {
                Boolean legacy = legacyCache.get(thirdUserId, uid ->
                    jdbc.queryForObject(
                        "SELECT CASE WHEN COUNT(*)>0 THEN 1 ELSE 0 END FROM user_itp_reg_info " +
                        "WHERE third_user_id = ? AND del_yn = 1",
                        Integer.class, uid) == 1);
                dbFailStreak.set(0);                          // 成功 → 清零连续失败
                return Boolean.TRUE.equals(legacy);
            } catch (Exception e) {
                long streak = dbFailStreak.incrementAndGet();
                if (streak >= failThreshold) {
                    // 触发熔断：30s 内不再打 DB（Caffeine 旧值继续用），到期后进入半开试探回切
                    circuitOpenUntil = System.currentTimeMillis() + circuitOpenMs;
                    log.warn("DB circuit OPEN for {}ms after {} consecutive failures; fail-safe to LEGACY",
                             circuitOpenMs, streak);
                } else {
                    log.warn("legacy lookup failed for {}, fail-safe (streak={})", thirdUserId, streak, e);
                }
                return true;                                  // fail-safe：走 LEGACY（旧 ITP）
            }
        };
    }
}
```

**DB 熔断 + 自动回切机制（金融级 P0）**：
- **触发**：连续 `failThreshold`（默认 3）次 DB 查询失败/超时 → 置 `circuitOpenUntil = now + circuitOpenMs`（30s 短路）。
- **短路期**：`circuitOpen` 为真时**不打 DB**，只用 Caffeine 已有缓存（`getIfPresent`，不 load）；无缓存的 uid → fail-safe 走旧 ITP（LEGACY）。等于"库挂了，网关靠本地缓存 + 全走旧 ITP 承接"。
- **半开试探（自动回切）**：`circuitOpenUntil` 到期后，**放行 `halfOpenProbe` 个请求探测 DB**（走正常 `legacyCache.get` 的 load），一旦成功 → `dbFailStreak=0` → 熔断解除，DB 查询恢复；若再失败 → 重新开 30s 熔断。
- **回切安全性**：短路期间**全走 NEW（fep-app）**，金融上"新旧系统都在跑、旧用户临时走新"是可接受的降级（迁移期本就双跑）；DB 恢复后名单判断恢复，旧用户重新走旧系统。

> **MUST（M8，DB 熔断）**：
> - `JdbcTemplate` MUST 设短查询超时（`queryTimeout=2s`），失败/超时都算一次失败（计入 `dbFailStreak`）；
> - 熔断**只短路 DB 查询，不短路路由**——短路期间网关本身正常，只是名单判断退化成"全走 NEW + Caffeine 兜底"；
> - **半开试探（DB 恢复后自动回切）MUST 保留**，否则会"库恢复但网关永远走 NEW"（旧用户被长期错投新系统）；
> - 熔断开/关 MUST 打日志 + 告警（`circuitOpen` 持续 > 5min = 业务库故障，需人工介入）。

> **为什么这样更干净**：① 去掉 `@Scheduled` 扫表 + `GatewayRouteConfigSync` 同步器 + `GATEWAY_ROUTE_CONFIG` 表，**少一张表、少一个定时任务**；② 出口 URL 就是路由 `uri`，**Actuator 是 Spring Cloud Gateway 官方的动态路由通道**（`POST /actuator/gateway/routes/{id}` + `refresh`），DBA/运维一行 `curl` 搞定，无需自研同步逻辑；③ 名单查询（DB + Caffeine）保留，但只负责"判断 LEGACY 还是 NEW"，**不负责改 URL**——URL 维护权完全交给 Actuator，职责分离。

> **DB 查询要点**：
> - `USER_ITP_REG_INFO` 表必须带 `THIRD_USER_ID` 的 **B 树索引**（`SELECT ... WHERE third_user_id = ?`；表按 `THIRD_USER_ID_SUFFIX` LIST 分区，建议局部索引 LOCAL），否则每请求扫全分区，DB 先崩。
> - 只读查询，**绝不** 在网关里写库（金融级：网关是流量入口，DB 故障会放大成网关不可用）。
> - `JdbcTemplate` 用 **短超时**（`queryTimeout=2s`），DB 抖动时快速失败 fail-safe 走旧 ITP，避免占死 Tomcat 线程。
> - **名单断言在路由匹配期执行**（`legacy-route` 匹配时才查 DB），`new-route` 不查。Caffeine 命中时断言开销 ≈ 0。

> **Caffeine 容量 + 读放大控制（M9 预热配合）**：
> Caffeine 能存多少取决于 `maximumSize`（条数）× 单条内存。本缓存 value 是 `Boolean` 单例，单条实际 ≈ 150~250 B（key String + 节点 + 哈希桶）：
>
> | `maximumSize` | 估算堆占用 | 适配 uid 基数 |
> |---|---|---|
> | 10w（当前默认） | ~20 MB | 迁移期灰度（活跃 uid < 10w） |
> | 100w | ~200 MB | 全量切（uid 10w~100w），网关 pod 堆调大即可 |
> | 500w | ~1 GB | uid > 100w（考虑加字节加权 / L2） |
>
> **MUST（M9 配套）**：① 上线前实测目标 uid 基数（`SELECT COUNT(DISTINCT third_user_id) FROM user_itp_reg_info`），按上表配 `maximumSize`；② 全量切场景 TTL 从 30s 提到 5min（注册表存在性低频变更，穿透率再降 10 倍）；③ `@PostConstruct` 预热（§7.3 `preload()`），避免启动后"冷穿透"打爆连接池。三者配合 = 读放大可控。

### 7.4 traceId 全链路透传（金融排障 P0，采纳）

> **为什么**：网关是全量流量入口，一笔请求经网关 → 旧/新系统 → 业务库，金融排障时**没有 traceId 全链路关联 = 抓瞎**。社区网关 SLO 标准做法：网关生成/透传 `X-Request-Id`，下游日志 MDC 关联。

**落地（Web MVC 版，`before()` 接受 `Function<ServerRequest, ServerRequest>`）**：

> Web MVC 版的 `.before(...)` 接受的是 `Function<ServerRequest, ServerRequest>`（同步改写请求后返回新 `ServerRequest`），不是 WebFlux 的 `HandlerFilterFunction`。
> 改请求 header 用 `ServerRequest.from(request).header(...).build()`（`ServerRequest` 不可变，没有 `mutate()`）。

```java
// 1) 入向：网关生成/透传 X-Request-Id（缺省时补 UUID）
//    Web MVC 版 .before() 接受 Function<ServerRequest, ServerRequest>，同步改写后返回
@Component
public class TraceIdFilter {

    public static final String TRACE_HEADER = "X-Request-Id";

    /**
     * 返回 before 过滤器：缺省补 UUID、有则透传，写进转发请求的 X-Request-Id header。
     * Web MVC 版 ServerRequest 不可变，用 ServerRequest.from(req).header(...).build() 改写。
     */
    public Function<ServerRequest, ServerRequest> traceId() {
        return request -> {
            String inbound = request.headers().firstHeader(TRACE_HEADER);
            String traceId = (inbound != null && !inbound.isBlank())
                ? inbound
                : UUID.randomUUID().toString();
            return ServerRequest.from(request).header(TRACE_HEADER, traceId).build();
        };
    }
}
```
路由里挂上（`traceId()` 放最前，order 最小，保证名单断言/下游都拿到）：
```java
route("legacy-route")
    .route(path("/itptvm/**", "/itpbom/**").and(resolver.isLegacyPredicate()), http())
    .before(traceIdFilter.traceId())   // 最前：先补 X-Request-Id，再走名单断言/转发
    .before(uri("http://legacy-system:8080"))
    .build();
```

**2) 下游（旧系统/fep-app）必须做的对接**：
- 在入口 `Filter` 里读 `X-Request-Id` → `MDC.put("traceId", ...)`，日志 pattern 加 `%X{traceId}`；
- 下游内部调用（rpc `ProxyWebClient`）也透传 `X-Request-Id`，全链路才有意义；
- 不接 MDC 的下游 = 网关补了 traceId 也白补，**下游不接 = 透传断路**。

**3) 与 SCG 内置 `RequestHeaderToRequestUri`/`AddRequestHeader` 的关系**：
- 这里用自定义 `BeforeFilterFunctions`（而不是 `AddRequestHeader`），因为要**"有则透传、无则补 UUID"**，`AddRequestHeader` 只会无条件覆盖；
- 补 UUID 用 `java.util.UUID`（`v4`），金融场景够用；要可预测/分布式有序可换雪花 ID（暂不做）。

> **MUST（D5）**：traceId 全链路透传 MUST 三处齐：① 网关入向补/透传 `X-Request-Id`（本 §7.4）；② 下游 MDC 接 `X-Request-Id` 进日志；③ 日志采集（你们现有 stdout → 采集链路）按 `traceId` 聚合。缺一环 = 透传断链。


### 7.5 FormFilter 顺序（form 报文 MUST）
```java
@Component
public class ReadBodyFilter implements Filter, Ordered {
    @Override public int getOrder() { return FormFilter.FORM_FILTER_ORDER - 1; }
    // ...
}
```
任何读 `request.getParameter` 的 `jakarta.servlet.Filter` MUST 排在 `FormFilter` 之前，否则 form body 已被消费、下游收不到 body。

### 7.6 连接池 + 超时（Web MVC 版直连 DB 的 P0 配置）
```yaml
# 1) DB 连接池（HikariCP，Spring Boot 默认池；7.x 由 BOM 管，只读查询专用小池）
#    与 fep-app 同库（AFCITPDB），但网关的连接池独立（HikariCP 实例，不复用 fep-app 的 Druid 池）
spring:
  datasource:
    url: jdbc:oracle:thin:@172.20.222.3:1521: AFCITPDB   # 业务库（与 fep-app 同库，连接池独立）
    username: <itp_ro>                                       # MUST 用只读账号（网关只查名单，绝不写库）
    password: <from env>
    driver-class-name: oracle.jdbc.OracleDriver             # ojdbc11（§7.1 最新版）
    # type 不写 → Spring Boot 默认用 HikariCP（HikariCP 7.x 由 BOM 管，零额外依赖）
    hikari:
      minimum-idle: 2
      maximum-pool-size: 20                                 # 只读小池（Caffeine 挡大部分查询后压力有限）
      connection-timeout: 2000                              # ms，拿连接超时
      max-lifetime: 300000
      idle-timeout: 600000
      connection-test-query: "SELECT 1 FROM DUAL"           # Oracle 探活
      pool-name: gateway-readonly-pool                       # 监控/日志区分

# 2) JdbcTemplate 查询超时（防 DB 抖动占死 Tomcat 线程）
#    用 JdbcClient（Spring 6.1）或 JdbcTemplate.setQueryTimeout(2)
user-route:
  db:
    query-timeout-seconds: 2

# 3) Tomcat 线程池（每请求 1 线程，含 DB 查询时间）
server:
  tomcat:
    threads:
      max: 400            # 按 峰值QPS × (下游P99RT + DB查询RT) 估算

# 4) 网关到下游的超时
spring:
  cloud:
    gateway:
      server:
        webmvc:
          httpclient:
            connect-timeout: 1000        # ms
            response-timeout: 3000       # 秒级硬上限
```

### 7.7 上线前 MUST（Web MVC 版 + 直连 DB）
| # | MUST | 理由 |
|---|---|---|
| M1 | **DB 只读账号 + 短查询超时**：网关连库用**专用只读账号**（禁写权限）；`JdbcTemplate` 设 `queryTimeout=2s`，超时 fail-safe 走旧 ITP | DB 抖动快速失败，不占死 Tomcat 线程；金融级不把网关可用性押在 DB 写路径上 |
| M2 | **注册表带 B 树索引**：`USER_ITP_REG_INFO` 的 `THIRD_USER_ID` MUST 有 B 树索引（表按 `THIRD_USER_ID_SUFFIX` LIST 分区，建局部索引 LOCAL；单列即可，无需 `del_yn` 复合） | 每请求一次点查，无索引会扫全分区先崩 DB 连接池 |
| M3 | **Caffeine 缓存兜底**：TTL 30s + `maximumSize` 上限；缓存命中不查 DB；DB 查询失败仍可命中旧值（fail-safe） | 挡 90%+ 的 DB 压力；DB 抖动时网关仍可用 |
| M4 | **连接池上限**：HikariCP `spring.datasource.hikari.maximum-pool-size` 设小（20 级），**不要** 给网关放一个能跑满业务库的池 | 网关是流量入口，池太大 = DB 故障放大成网关雪崩；业务库容量保护 |
| M5 | **FormFilter 顺序正确**：读参的 `Filter` MUST 排 `FormFilter` 之前 | form 报文场景，参数合并坑 |
| M6 | **Actuator 生产只读**：`management.endpoint.gateway.access=read-only` + 鉴权保护 | 防止动态删改路由 |
| M7 | **监控**：Tomcat `executor`（activeCount/queueSize）+ HikariCP（`hikaricp.active_connections`/`pending_threads`，Micrometer 自动暴露）+ DB 查询 RT 三项都要告警 | 线程池打满 / 连接池排队 / 查询变慢 是雪崩前 3 个信号 |
| M8 | **DB 熔断 + 自动回切**：连续 `failThreshold`（默认 3）次 DB 查询失败/超时 → 熔断短路 `circuitOpenMs`（默认 30s）内**不打 DB**，只用 Caffeine 旧值、无缓存 fail-safe 走旧 ITP；到期半开试探（`halfOpenProbe` 个请求探测），成功即自动回切恢复名单判断 | 库挂时网关仍可用（fail-safe）；库恢复后自动回切（不长期错投旧系统）；短超时防占死 Tomcat 线程 |
| M9 | **Caffeine 冷启动预热**：`@PostConstruct` 把 `USER_ITP_REG_INFO` 全量 `third_user_id` load 进 Caffeine（分批 5000，DB 失败降级空启动） | 避免启动后前几秒"冷穿透"瞬间打爆业务库连接池；预热完成后打日志核对条目数（≈ 注册表用户基数） |

### 7.8 WebFlux 版升级路径（未来流量 > 5k QPS 时）
1. 加 `spring-cloud-starter-gateway-server-webflux`（启用时 `spring.cloud.gateway.server.webmvc.enabled=false`）。
2. 路由 DSL 几乎 1:1 迁移：`route().GET(...)` → `.route(...)`（`RouteLocatorBuilder`）；`before(uri(...))` → `filters(f -> f...)`。
3. `JdbcTemplate`（同步）→ `ReactiveJdbcTemplate`（响应式）+ Caffeine 保留；WebFlux 版**不能阻塞**，所以 DB 查询也要切响应式驱动（`r2dbc` 或 `ReactiveJdbcTemplate`）。
4. 删 Tomcat 线程池配置，换 Netty `spring.cloud.gateway.httpclient.*`。
5. 压测对比：QPS、p99、内存（Reactor 的背压机制）。

### 7.9 动态维护 URL 的落地（Actuator 直接维护 + 落库审计）

#### 整体方案

```
┌─────────────────────────────────────────────────────────────────────┐
│ Actuator POST /actuator/gateway/routes/{id}                         │
│   ↓                                                                 │
│ ① SCG 内置 RouteDefinitionWriter 写内存（CachingRouteLocator）       │
│    POST /actuator/gateway/refresh → 立即生效（秒级）                 │
│   ↓                                                                 │
│ ② RouteSyncFilter（AfterFilter）同步写 GATEWAY_ROUTE_CONFIG 表       │
│    INSERT/UPDATE MERGE INTO gwc; INSERT INTO gwc_audit (before/after) │
│   ↓                                                                 │
│ ③ 启动时 GatewayRouteLoader 从 DB 加载兜底路由（DB 故障 → 空路由）   │
└─────────────────────────────────────────────────────────────────────┘
```

**关键原则**：
- **库是唯一真实来源**，Actuator 路由变更必须先落库再生效。流程：DBA 调 Actuator POST → RouteSyncFilter 先调 SyncService 落库 + 写审计 → 落库成功 → Actuator 返回 200，SCG 内存路由更新 → 落库失败 → 抛异常，Actuator 返回 500，内存路由不更新。
- 启动时从 `GATEWAY_ROUTE_CONFIG` 加载兜底路由（DB 故障 → 抛异常 → 应用启动失败，避免空路由上线）
- 幂等：MERGE INTO（Oracle UPSERT），重复 SYNC 不报错
- 无定时任务：只在 Actuator 写完后主动 SYNC，不扫表

#### 维护操作手册（DBA/运维直接执行）

```bash
# 0) 查当前路由（内存快照 + 落库状态）
curl 'http://scg-gateway:8080/actuator/gateway/routes'
# 查 DB 审计日志（最近 10 条）
kubectl -n itp exec -it deploy/user-route-gateway -- \
  sqlplus itp_ro/<pwd>@//172.20.222.3:1521/AFCITPDB \
  -s <<EOF
SELECT audit_id, route_id, action, before_uri, after_uri, operator, operate_tms
FROM qditp.gateway_route_config_audit
ORDER BY audit_id DESC FETCH FIRST 10 ROWS ONLY;
EOF

# 1) 改新出口（new-route 的 uri）
curl -X POST 'http://scg-gateway:8080/actuator/gateway/routes/new-route' \
     -H 'Content-Type: application/json' \
     -H 'X-Operator: zhangsan' \
     -d '{"uri":"http://fep-app-v2-svc:9102","predicates":["Path=/itptvm/**","Path=/itpbom/**"],"remark":"fep-app v2 上线"}'
curl -X POST 'http://scg-gateway:8080/actuator/gateway/refresh'

# 2) 改旧出口（legacy-route 的 uri）
curl -X POST 'http://scg-gateway:8080/actuator/gateway/routes/legacy-route' \
     -H 'Content-Type: application/json' \
     -H 'X-Operator: zhangsan' \
     -d '{"uri":"http://face-pay-v2-svc:30025","predicates":["Path=/itptvm/**","Path=/itpbom/**"],"remark":"face-pay v2 迁移"}'
curl -X POST 'http://scg-gateway:8080/actuator/gateway/refresh'

# 3) 紧急全切新系统（删 legacy-route，名单命中也全走 new）
curl -X DELETE 'http://scg-gateway:8080/actuator/gateway/routes/legacy-route' \
     -H 'X-Operator: zhangsan'
curl -X POST 'http://scg-gateway:8080/actuator/gateway/refresh'

# 4) 恢复旧系统（重建 legacy-route）
curl -X POST 'http://scg-gateway:8080/actuator/gateway/routes/legacy-route' \
     -H 'Content-Type: application/json' \
     -d '{"uri":"http://face-pay-svc:30025","predicates":["Path=/itptvm/**","Path=/itpbom/**"]}'
curl -X POST 'http://scg-gateway:8080/actuator/gateway/refresh'
```

> **改完后 MUST 验证（防"改了没生效"）**：
> 1. `curl /actuator/gateway/routes` 确认路由 uri/predicates 已变；
> 2. 查审计日志确认 SYNC 成功（`action=UPDATE/CREATE`，无 error 日志）；
> 3. 打一笔 LEGACY 用户测试请求，确认实际落到新出口；
> 4. 看网关日志 `thirdUserId → LEGACY/NEW` 是否正确。

#### Web MVC 版 vs WebFlux 版 Actuator 差异（Q2 踩坑记录）

- Web MVC 版的 Actuator 动态路由 API（`POST /actuator/gateway/routes/{id}` + `refresh`）**可用**（底层仍是完整 SCG，与 WebFlux 共用 `RouteDefinitionWriter`/`CachingRouteLocator`）。
- 但 Web MVC 版的路由入口是 `@Bean RouterFunction<ServerResponse>`（Java DSL），**不是** `RouteDefinitionRepository` 那张 `routes` 表 —— 所以 `GET /actuator/gateway/routes` 返回的是**运行时内存快照**（不是持久化到 DB），改完靠 `refresh` 事件刷新 `CachingRouteLocator` 才生效，**不重启、不落库**。
- **启动时**从 `GATEWAY_ROUTE_CONFIG` 表加载兜底路由（DB 故障 → 空路由，后续靠 Actuator 注入）。

#### 落库表设计（DDL 见 `account-server/src/main/resources/sql/gateway-route-config-schema.sql`）

```sql
-- 路由配置表（主表，幂等 UPSERT）
CREATE TABLE GATEWAY_ROUTE_CONFIG (
    ROUTE_ID      VARCHAR2(64 CHAR) NOT NULL PRIMARY KEY,  -- legacy-route / new-route
    URI           VARCHAR2(512 CHAR) NOT NULL,
    PREDICATES    CLOB,                                    -- JSON 数组 ["Path=/itptvm/**",...]
    FILTERS       CLOB DEFAULT '[]',
    ORDER_NO      NUMBER(10) NOT NULL,
    CREATED_TMS   TIMESTAMP DEFAULT SYSTIMESTAMP,
    UPDATED_TMS   TIMESTAMP DEFAULT SYSTIMESTAMP,
    CREATED_BY    VARCHAR2(64 CHAR) DEFAULT 'system'
) PARTITION BY LIST (ROUTE_ID) (
    PARTITION P_LEGACY VALUES ('legacy-route'),
    PARTITION P_NEW      VALUES ('new-route')
);

-- 审计日志表（只插不删，按月分区）
CREATE TABLE GATEWAY_ROUTE_CONFIG_AUDIT (
    AUDIT_ID         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ROUTE_ID         VARCHAR2(64 CHAR) NOT NULL,
    ACTION           VARCHAR2(16 CHAR) NOT NULL,  -- CREATE / UPDATE / DELETE
    BEFORE_URI       VARCHAR2(512 CHAR),
    AFTER_URI        VARCHAR2(512 CHAR),
    BEFORE_PREDICATES CLOB,
    AFTER_PREDICATES  CLOB,
    OPERATOR         VARCHAR2(64 CHAR),
    OPERATE_TMS      TIMESTAMP DEFAULT SYSTIMESTAMP,
    REMARK           VARCHAR2(256 CHAR)
) PARTITION BY RANGE (OPERATE_TMS) INTERVAL (INTERVAL '1' MONTH) (
    PARTITION P_202610 VALUES LESS THAN (TO_DATE('2026-11-01','YYYY-MM-DD'))
);
```

### 7.12 明确不做的事（范围界定，防后续误加）

档 3 定位是**最简透明路由网关**：只做「按名单分流 + 路由 URL 可改 + traceId 透传」，以下加固**一律不做**（2026-10-13 决策）：

| 项 | 为什么不做 | 将来要做怎么加 |
|---|---|---|
| 出口维度 metrics / SLO 告警 | 当前量级（<5k QPS）不需要；先跑通分流再观测 | 加 `micrometer-registry-prometheus` + 按 `target=LEGACY/NEW` 打标签 |
| 限流（防恶意穿透） | 入口已有 Istio/LB 限流；避免重复 | SCG `RequestRateLimiter`（Bucket4j 本地，不引 Redis） |
| CORS 收紧 / 响应安全头 | 网关只做透传，CORS/安全头交给前端/下游；避免越权配置 | `globalcors` + `SecureHeaders` 过滤器 |
| Actuator 路由端点加固 | 端点已 read-only（M6），暂不加专门鉴权 | 加 `spring-security` 对 `/actuator/gateway/**` 限内网 |
| 灰度比例路由（`Weight`） | 当前只做「名单二选一」；比例灰度暂不需要 | 加 `Weight=group, N` 断言 + 新出口路由 |
| 网关验签 | 验签是业务语义，留在 fep-app/旧系统 | 网关不做验签（已定界，见 §七 红线） |

> **判定原则**（社区共识）：**"如果这个功能换一个服务也能做、或需要理解业务语义，就不该放网关。"** 档 3 只保留"纯路由"职责。

---

*附：本文基于官方文档 5.0.3 整理。完整官方文档见同目录 `index.html`；设计文档见 `docs/design/用户分流渐进迁移设计.md`。选型结论：Web MVC 版 + 直连 DB 查名单（§〇、§七）。*