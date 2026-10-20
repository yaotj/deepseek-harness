# IT-010 ticket-server 端口 9100 未生效致 requestIndustryData 返回 503

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-010` 一行。

## 1. 背景 / 现象

- 发现时间：2026-10-15 17:51
- 发现渠道：用户反馈（APP 端测试）
- 影响范围：ticket-server 及所有经 `fep-app-server` 调用 ticket-server 的接口（本次由 IF8A-03 `/ci/app/requestIndustryData` 暴露）
- 现象描述：
  - 卡号 `0426090949000081`、用户 `00522980` 请求行业数据时，APP 端收到响应 `{"retCode":"<UUID>","retMsg":null,"data":null}`，HTTP 200，耗时约 7.9s。
  - 该 UUID `retCode` 是 `fep-app-server` 全局异常处理器 `GlobalControllerExceptionHandler` 对未捕获异常的兜底返回。

## 2. 根因分析

- 排查过程：
  1. 复现：直接 `POST /ci/app/requestIndustryData` 返回同样 UUID `retCode`。
  2. 取 traceId（`f2e1bd1f5b6bb7385c3d2d31a0678bee`）查 VictoriaLogs，发现 `fep-app-server` 出向调用 `http://172.20.211.200:30014/internal/ticket/industry/online` 返回 **503 Service Unavailable**。
  3. 检查 ticket-server Pod：状态 `2/2 Running`；但 NodePort `30014` 探活返回 503，`Connection refused`。
  4. Pod 内检查端口：应用实际监听 **9103**，而 Service targetPort / Deployment 容器端口期望 **9100**。
  5. 检查 `ticket-server/application.properties`：`server.port=9103`。
  6. 检查 K8s Deployment env：`server.port=9100`（dot-name）。根据 Spring Boot Relaxed Binding，环境变量必须为大写下划线 `SERVER_PORT` 才能映射到 `server.port`；`server.port` 这种 dot-name 不会被识别，导致应用回退到 properties 默认值 9103。
- 根因结论：
  - 直接原因：`ticket-server/application.properties` 默认端口 9103 与 K8s 期望的 9100 不一致，且 K8s env `server.port` 使用 dot-name 无法覆盖默认值。
  - 深层原因：K8s Deployment 环境变量名违反 `AGENTS.md`「K8s env var MUST 下划线命名」约束。
  - 连带发现：**全仓 21 个 Deployment 全部使用 `server.port` dot-name**，属于同类隐患。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A | 仅把 ticket-server Deployment 的 `server.port` 改为 `SERVER_PORT=9100` | 快速恢复线上 | 其他 20 个 Deployment 仍有 dot-name 隐患 | 未来其他服务改端口时同样会炸 |
| 方案B | 同步批量把所有 Deployment 的 `server.port` → `SERVER_PORT`，并把 ticket-server 的 application.properties 默认值改为 9100 | 消除同类隐患，本机与 K8s 口径一致 | 改动文件多 | 无额外风险，符合项目规范 |

## 4. 决策与结论

- 选定方案：方案B
- 决策理由：
  - 当前故障必须立即恢复。
  - `server.port` dot-name 是全仓共性问题，必须一次性修正，避免后续同类故障。
- 影响范围 / 关联模块：
  - `ticket-server/src/main/resources/application.properties`：`server.port=9103` → `9100`
  - `deploy/k8s/10-deployments/ticket-server.yaml`：`server.port` → `SERVER_PORT`
  - 其他 20 个 Deployment yaml：`server.port` → `SERVER_PORT`

### 具体修改

1. `ticket-server/src/main/resources/application.properties`
   - `server.port=9103` → `server.port=9100`

2. 批量修改 `deploy/k8s/10-deployments/*.yaml`
   - 将 `name: server.port` 全部替换为 `name: SERVER_PORT`（共 21 个文件）

## 5. 验证与回滚

- 验证方式 / 结果：
  - 本地离线编译：`mise exec -- mvn -o clean test-compile -pl model,rpc,ticket-server -am`
  - 结果：**BUILD SUCCESS**
  - 测试环境线上修复（2026-10-15）：
    - 通过 `kubectl -n itp set env deployment/ticket-server SERVER_PORT=9100` 并删除 `server.port`
    - `rollout status` 成功
    - Pod 内 `9100` 端口 open
    - NodePort `172.20.211.23:30014/actuator/health` → **http=200 / status=UP / Oracle UP**
    - `POST /ci/app/requestIndustryData` 复测 → **retCode=0000，cardData 非空**
- 回滚预案：
  - 代码回滚：恢复上述文件修改。
  - 部署回滚：
    ```bash
    kubectl -n itp set env deployment/ticket-server server.port=9100
    kubectl -n itp set env deployment/ticket-server SERVER_PORT-
    ```

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-010`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 处理情况 / 更新」
- 偏差说明（如有）：本次线上修复通过 `kubectl set env` 直接修改 Deployment，未重新出 jar 部署；application.properties 默认值改为 9100 的改动已入库，待下次正常发版时固化到镜像。
