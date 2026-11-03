# IT-050 设备端联调测试方案

> 用途：设备侧（TVM / BOM / APP）与 route-sentinel 分流网关的端到端联调。
> 执行人：业务/设备侧测试人员。
> 配合项：如需在 `USER_ITP_REG_INFO` 临时增删测试用户，请联系 QDITP 后端。

---

## 1. 联调目标

验证 5 条设备域 URL 在新旧用户分流场景下，**请求能正确到达旧 ITP 或 `face-pay-server`**，并返回预期业务响应。

---

## 2. 环境与入口

| 项 | 值 | 说明 |
|---|---|---|
| 测试环境入口 | `http://58.56.166.170:48000` | 公网 Istio Gateway（设备侧通常走此入口） |
| 集群内网关 | `http://10.100.192.240:50908` | K8s 内部 itp-gateway，排查时可从跳板机直接访问 |
| route-sentinel 版本 | `1.0.5` | 镜像 `os-harbor-svc.default.svc.cloudos:443/itp/route-sentinel:1.0.5` |
| 旧 ITP TVM | `http://172.20.202.10:8180` | TVM legacy 下游 |
| 旧 ITP BOM | `http://172.20.202.10:8580` | BOM legacy 下游 |
| face-pay-server | `http://face-pay-server-internal:8080` | 新用户下游 |
| 用户名单表 | `QDITP.USER_ITP_REG_INFO` | 有记录 = 新用户，走 `face-pay-server` |

---

## 3. 联调范围（5 条 URL）

```text
TVM 3 条：
  /itptvm/ci/tvm/notiTakeTicketResult
  /itptvm/ci/tvm/notiTakeTicketFailResult
  /itptvm/ci/tvm/requestTakeTicketAuth

BOM 2 条：
  /itpbom/ci/bom/requestCardDataAnalyse
  /itpbom/ci/bom/requestUpdateCardData
```

---

## 4. 测试场景总览

| 场景编号 | 设备域 | 用户类型 | 判定依据 | 期望路由 | 期望下游 |
|---|---|---|---|---|---|
| TVM-01 | TVM | 无用户 | `bizData` 缺失或 `thirdUserId` 为空 | `legacy-route-tvm` | 旧 ITP `172.20.202.10:8180` |
| TVM-02 | TVM | 老用户 | `thirdUserId` 不在 `USER_ITP_REG_INFO` | `legacy-route-tvm` | 旧 ITP `172.20.202.10:8180` |
| TVM-03 | TVM | 新用户 | `thirdUserId` 在 `USER_ITP_REG_INFO` | `face-pay-split-route-tvm` | `face-pay-server-internal:8080` |
| BOM-01 | BOM | 无用户 | `bizData` 缺失或 `thirdUserId` 为空 | `legacy-route-bom` | 旧 ITP `172.20.202.10:8580` |
| BOM-02 | BOM | 老用户 | `thirdUserId` 不在 `USER_ITP_REG_INFO` | `legacy-route-bom` | 旧 ITP `172.20.202.10:8580` |
| BOM-03 | BOM | 新用户 | `thirdUserId` 在 `USER_ITP_REG_INFO` | `face-pay-split-route-bom` | `face-pay-server-internal:8080` |

**合计：5 条 URL × 3 场景 = 15 个测试用例。**

---

## 5. 测试数据准备

### 5.1 新用户（必须在 `USER_ITP_REG_INFO` 中存在）

测试环境已有样本：

```text
thirdUserId = 00522967
```

如需要新增/删除测试用户，执行：

```sql
-- 新增测试新用户
INSERT INTO QDITP.USER_ITP_REG_INFO (third_user_id, del_yn, reg_tms)
VALUES ('TEST_NEW_USER_001', 0, SYSTIMESTAMP);

-- 删除临时测试用户
DELETE FROM QDITP.USER_ITP_REG_INFO WHERE third_user_id = 'TEST_NEW_USER_001';
```

> 注意：`USER_ITP_REG_INFO` 有 Caffeine 缓存（TTL 5 分钟），增删后建议等待 5 分钟或重启 route-sentinel Pod 使缓存失效。

### 5.2 老用户

任选一个**确认不在** `USER_ITP_REG_INFO` 的 `thirdUserId`，例如：

```text
thirdUserId = legacy_test_20261103
```

### 5.3 bizData 格式

设备侧请求时，把 `thirdUserId` 放到 URL query 参数 `bizData` 中：

```text
bizData={"thirdUserId":"00522967"}
```

URL 编码后：

```text
bizData=%7B%22thirdUserId%22%3A%2200522967%22%7D
```

---

## 6. 设备侧执行步骤

### 6.1 通用执行模板

对每条 URL、每个场景，按以下模板执行：

1. **构造请求**
   - URL：`http://58.56.166.170:48000<path>?bizData=<encoded>`
   - Method：按设备侧真实调用方式（通常为 POST）
   - Headers：`Content-Type: application/json`
   - Body：按设备侧真实报文（可为空或最小 JSON `{}`）

2. **发送请求并记录**
   - 记录请求时间、设备号、thirdUserId、请求报文。

3. **检查响应**
   - HTTP 状态码
   - 响应体 `retCode` / `retMsg`
   - 响应头 `X-Route-Id`（如设备侧能抓包或日志打印）

4. **判定结果**
   - 对照第 7 节「预期结果」判定是否通过。

### 6.2 TVM 联调用例

#### TVM-01：无用户 → legacy-route-tvm

```bash
POST http://58.56.166.170:48000/itptvm/ci/tvm/notiTakeTicketResult
Content-Type: application/json
Body: {}
```

#### TVM-02：老用户 → legacy-route-tvm

```bash
POST http://58.56.166.170:48000/itptvm/ci/tvm/notiTakeTicketResult?bizData=%7B%22thirdUserId%22%3A%22legacy_test_20261103%22%7D
Content-Type: application/json
Body: {}
```

#### TVM-03：新用户 → face-pay-split-route-tvm

```bash
POST http://58.56.166.170:48000/itptvm/ci/tvm/notiTakeTicketResult?bizData=%7B%22thirdUserId%22%3A%2200522967%22%7D
Content-Type: application/json
Body: {}
```

> 三条 TVM URL 均按上述三个场景执行一次。

### 6.3 BOM 联调用例

#### BOM-01：无用户 → legacy-route-bom

```bash
POST http://58.56.166.170:48000/itpbom/ci/bom/requestCardDataAnalyse
Content-Type: application/json
Body: {}
```

#### BOM-02：老用户 → legacy-route-bom

```bash
POST http://58.56.166.170:48000/itpbom/ci/bom/requestCardDataAnalyse?bizData=%7B%22thirdUserId%22%3A%22legacy_test_20261103%22%7D
Content-Type: application/json
Body: {}
```

#### BOM-03：新用户 → face-pay-split-route-bom

```bash
POST http://58.56.166.170:48000/itpbom/ci/bom/requestCardDataAnalyse?bizData=%7B%22thirdUserId%22%3A%2200522967%22%7D
Content-Type: application/json
Body: {}
```

> 两条 BOM URL 均按上述三个场景执行一次。

---

## 7. 预期结果

### 7.1 TVM 预期

| 场景 | X-Route-Id | HTTP | 预期响应体关键字段 |
|---|---|---|---|
| 无用户 / 老用户 | `legacy-route-tvm` | 200 | `{"retCode":"2001","retMsg":"非法设备"}` |
| 新用户 | `face-pay-split-route-tvm` | 200 | `{"retCode":"2002","retMsg":"orderNo不能为空"}`（`notiTakeTicketResult` / `notiTakeTicketFailResult`）或 `{"retCode":"2002","retMsg":"非法参数"}`（`requestTakeTicketAuth`） |

### 7.2 BOM 预期

| 场景 | X-Route-Id | HTTP | 预期响应体关键字段 |
|---|---|---|---|
| 无用户 / 老用户 | `legacy-route-bom` | 200 | `requestCardDataAnalyse` → `{"retCode":"2001","retMsg":"非法设备"}`；`requestUpdateCardData` → `{"retCode":"9999","retMsg":"失败"}` |
| 新用户 | `face-pay-split-route-bom` | 200 | `{"retCode":"8003","retMsg":"cardId不能为空"}` |

---

## 8. 结果记录表

| 用例编号 | URL | 场景 | 实际 X-Route-Id | 实际 HTTP | 实际 retCode/retMsg | 是否通过 | 备注 |
|---|---|---|---|---|---|---|---|
| TVM-01 | `/itptvm/ci/tvm/notiTakeTicketResult` | 无用户 | | | | | |
| TVM-02 | `/itptvm/ci/tvm/notiTakeTicketResult` | 老用户 | | | | | |
| TVM-03 | `/itptvm/ci/tvm/notiTakeTicketResult` | 新用户 | | | | | |
| TVM-04 | `/itptvm/ci/tvm/notiTakeTicketFailResult` | 无用户 | | | | | |
| TVM-05 | `/itptvm/ci/tvm/notiTakeTicketFailResult` | 老用户 | | | | | |
| TVM-06 | `/itptvm/ci/tvm/notiTakeTicketFailResult` | 新用户 | | | | | |
| TVM-07 | `/itptvm/ci/tvm/requestTakeTicketAuth` | 无用户 | | | | | |
| TVM-08 | `/itptvm/ci/tvm/requestTakeTicketAuth` | 老用户 | | | | | |
| TVM-09 | `/itptvm/ci/tvm/requestTakeTicketAuth` | 新用户 | | | | | |
| BOM-01 | `/itpbom/ci/bom/requestCardDataAnalyse` | 无用户 | | | | | |
| BOM-02 | `/itpbom/ci/bom/requestCardDataAnalyse` | 老用户 | | | | | |
| BOM-03 | `/itpbom/ci/bom/requestCardDataAnalyse` | 新用户 | | | | | |
| BOM-04 | `/itpbom/ci/bom/requestUpdateCardData` | 无用户 | | | | | |
| BOM-05 | `/itpbom/ci/bom/requestUpdateCardData` | 老用户 | | | | | |
| BOM-06 | `/itpbom/ci/bom/requestUpdateCardData` | 新用户 | | | | | |

---

## 9. 问题排查速查

### 9.1 如果新用户走到了 legacy

可能原因：
- `thirdUserId` 不在 `USER_ITP_REG_INFO` 中（查表确认）。
- route-sentinel Caffeine 缓存未刷新（等待 5 分钟或重启 Pod）。
- `bizData` 格式错误，未正确提取 `thirdUserId`（检查 URL 编码）。

### 9.2 如果老用户走到了 face-pay-split

可能原因：
- `thirdUserId` 误写入了 `USER_ITP_REG_INFO`。
- 缓存异常（极少见）。

### 9.3 如果 TVM legacy 返回 404

说明 `legacy-route-tvm` 仍带有 `RewritePath`，请确认运行态 `filters=[]`：

```bash
kubectl -n itp port-forward pod/route-sentinel-xxx 19999:9999 &
curl http://localhost:19999/actuator/gatewayroutes
```

### 9.4 如果设备侧看不到 `X-Route-Id`

可让后端配合抓包，或通过 VictoriaLogs / Pod 日志确认：

```bash
# 查看 route-sentinel 实时日志
kubectl -n itp logs -l app=route-sentinel -f
```

日志关键字：
- `X-Route-Id: legacy-route-tvm`
- `X-Route-Id: face-pay-split-route-tvm`
- `RewritePath`（如仍有重写）

---

## 10. 联调通过标准

- [ ] 15 个用例全部执行完毕。
- [ ] 每个用例的 `X-Route-Id` 与预期一致。
- [ ] TVM/BOM 无用户、老用户场景均返回旧 ITP 业务响应。
- [ ] TVM/BOM 新用户场景均返回 `face-pay-server` 业务校验响应。
- [ ] 未出现 502/503/504 等网关层错误。

---

## 11. 附录：快速 curl 验证脚本

如设备侧暂时无法直接调用，可由后端在跳板机执行以下脚本作为对照：

```bash
GATEWAY="http://10.100.192.240:50908"
NEW_USER="00522967"
OLD_USER="legacy_test_20261103"

new_biz="%7B%22thirdUserId%22%3A%22${NEW_USER}%22%7D"
old_biz="%7B%22thirdUserId%22%3A%22${OLD_USER}%22%7D"

# TVM 新用户
for p in notiTakeTicketResult notiTakeTicketFailResult requestTakeTicketAuth; do
  echo "=== TVM $p NEW_USER ==="
  curl -s -D - -X POST "${GATEWAY}/itptvm/ci/tvm/${p}?bizData=${new_biz}" \
    -H "Content-Type: application/json" -d '{}' | grep -E "HTTP/|x-route-id|retCode|retMsg"
done

# TVM 老用户
for p in notiTakeTicketResult notiTakeTicketFailResult requestTakeTicketAuth; do
  echo "=== TVM $p OLD_USER ==="
  curl -s -D - -X POST "${GATEWAY}/itptvm/ci/tvm/${p}?bizData=${old_biz}" \
    -H "Content-Type: application/json" -d '{}' | grep -E "HTTP/|x-route-id|retCode|retMsg"
done

# BOM 新用户
for p in requestCardDataAnalyse requestUpdateCardData; do
  echo "=== BOM $p NEW_USER ==="
  curl -s -D - -X POST "${GATEWAY}/itpbom/ci/bom/${p}?bizData=${new_biz}" \
    -H "Content-Type: application/json" -d '{}' | grep -E "HTTP/|x-route-id|retCode|retMsg"
done

# BOM 老用户
for p in requestCardDataAnalyse requestUpdateCardData; do
  echo "=== BOM $p OLD_USER ==="
  curl -s -D - -X POST "${GATEWAY}/itpbom/ci/bom/${p}?bizData=${old_biz}" \
    -H "Content-Type: application/json" -d '{}' | grep -E "HTTP/|x-route-id|retCode|retMsg"
done
```

---

## 12. 变更记录

| 时间 | 修订人 | 内容 |
|---|---|---|
| 2026-11-03 | QDITP | 初稿，基于 route-sentinel 1.0.5 当前运行态 |
