# Spring Boot 3.x / Jakarta EE 迁移纪律

> **来源**：从 `AGENTS.md` §4.1 第 1 条下沉（2026-10-22，P2-1）。
> **时机**：Spring Boot 3.x 迁移才触发，属窄场景规则，按 `docs/提示词/工程与文档规范.md` §4.3「上下文占用即冗余」判据下沉。
> **维护**：Spring Boot 版本升迁、Jakarta 迁移遗留清理时同步本文件；AGENTS 侧只留指针。

## 核心纪律

1. **MUST 使用 `jakarta.*` 包**（`jakarta.servlet`、`jakarta.validation`、`jakarta.persistence`、`jakarta.annotation`）。
2. **NEVER 新增 `javax.servlet` / `javax.validation` / `javax.persistence` / `javax.annotation` 等 Jakarta EE 旧包**。
3. **JDK 自带的 `javax.crypto` / `javax.net` / `javax.sql` 不在此列**——它们是 JDK 内部实现，迁移不碰。
4. 本项目已全仓跑 Spring Boot 3.2.6（见 `AGENTS.md` §2.1 核心技术栈），老包引用只会触发编译报错，但**写代码时容易无意识用回**（习惯残留），所以这条纪律靠「MUST/NEVER + 编译兜底」两层保障。

## 迁移遗留 / 已知例外

- 本仓库应无 `javax.*` 残留；如有发现（比如拷贝自老项目），**不要顺手改成 `jakarta.*`**——那是独立任务，必须单独立项 + 回归测试，避免一次性扫仓破坏其他模块的兼容性约定。
- 若发现 `jakarta.*` 引入导致编译失败，**NEVER 退回到 `javax.*`**——正确做法是查 Spring Boot 依赖版本是否真在 3.x，或者是否有传递依赖拉下旧版本。
