# Spring Cloud Gateway 5.0.3 完整说明书（离线版）

> 官方参考文档完整离线副本，由官方 GitHub 仓库 `v5.0.3` tag 的 AsciiDoc 源构建。
> 构建日期：2026-10-13

## 目录结构

```
docs/spring-cloud-gateway/
├── index.html          # 离线导航首页（带侧边栏目录，推荐从这里开始）
├── html/              # 完整 HTML 页面（119 个章节页）
│   ├── index.html
│   ├── intro.html
│   ├── spring-cloud-gateway-server-webflux.html        # WebFlux 版网关
│   ├── spring-cloud-gateway-server-webflux/            # WebFlux 子章节
│   ├── spring-cloud-gateway-server-webmvc.html         # Web MVC 版网关
│   ├── spring-cloud-gateway-server-webmvc/             # Web MVC 子章节
│   ├── spring-cloud-gateway-proxy-exchange.html        # Proxy Exchange
│   └── appendix.html                                   # 附录：全部配置属性
└── 源文件/            # 官方 AsciiDoc 原文（129 个文件）
    ├── *.adoc
    ├── spring-cloud-gateway-server-webflux/
    ├── spring-cloud-gateway-server-webmvc/
    ├── assets/images/
    └── antora.yml / nav.adoc
```

## 使用方式

**推荐**：用浏览器打开 `index.html`，左侧为全站目录，点击任意章节即可阅读。

也可直接打开 `html/` 下对应页面。

## 文档范围

- **Spring Cloud Gateway Server WebFlux**（基于 WebFlux 的响应式网关，即经典 Spring Cloud Gateway）
  - 如何引入、术语表、工作原理
  - 路由断言工厂（Route Predicate Factories）
  - 网关过滤器工厂（GatewayFilter Factories，30+ 种）
  - 全局过滤器、HttpHeadersFilters、TLS/SSL、Spring Security 集成
  - HttpClientCustomizer、Configuration、路由元数据、超时配置
  - Fluent Java Routes API、DiscoveryClient 路由定位
  - Reactor Netty Access Logs、CORS、Actuator API
  - 多实例路由共享、故障排查、开发者指南、AOT/原生镜像
  - 全部配置属性（Configuration properties）
- **Spring Cloud Gateway Server Web MVC**（基于 Servlet 的网关）
  - Java Routes API、Gateway Request Predicates、Gateway Handler Filter Functions
  - 自定义断言与过滤器、Servlet/Filter 协作、TLS/SSL、Spring Security
- **Proxy Exchange Gateway**（基于 Web MVC 或 WebFlux 的代理交换网关）
- **附录：Common application properties**（常用配置属性全表）

## 版本信息

- 文档版本：**5.0.3**（stable）
- 在线原文：<https://docs.spring.io/spring-cloud-gateway/reference/>
- GitHub tag：`v5.0.3`，路径 `docs/modules/ROOT/`
- 对应 Spring Cloud 版本：2026.0.x（Spring Boot 4.x 时代）

## 重新构建说明

源文件使用 AsciiDoc 编写，其中 `appendix.adoc` / `configprops.adoc` 引用了
Antora 专有的 `include::partial$_xxx.adoc[]` 语法。重新构建时需先将
`partial$_xxx` 替换为 `_xxx` 相对路径，再用 asciidoctor 转换：

```bash
# 安装 asciidoctor（Node 版）
npm install @asciidoctor/core
# 转换脚本见本次构建过程（docs/spring-cloud-gateway/构建记录.md 可参考）
```

## 版权

文档版权归 Broadcom（原 VMware / Pivotal / Spring）所有，本副本仅用于个人学习研究。
