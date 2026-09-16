# 03 · STT（ITP_Payment_018~020）

## 结论先行

⚠️ **三个用例全部阻塞，STT 设备在代码中没有任何接入实现。**

## 取证

全仓库检索 `STT`（区分与不区分大小写都试过）、「自助终端」、「自助设备」，**只有 3 处命中，全是枚举定义或注释文字，无任何业务代码**：

- `model/src/main/java/com/chinasofti/huateng/model/enums/DeviceTypeEnum.java:48-49`
  `/** 14 - STT (0x0E) */ STT("14", "STT")` —— 仅常量定义。该常量**全仓库无任何引用点**（`DeviceTypeEnum` 的全部命中只有 `fromCode()` 通用转换与 `BomOrderServiceImpl` 的 import/注释）
- `collect-pay-server/.../model/request/BaseRequestDTO.java:9`
  注释 `01:青岛地铁APP, 02:TVM, 03:BOM, 04:AGM, 05:ACC, 06:ITP, 07:STT, 其他预留。`
- `.trae/documents/统一使用DeviceTypeEnum计划.md:16` —— 文档，非代码

> ⚠️ **两套设备编码并存且冲突**：`DeviceTypeEnum` 里 STT 是 `14`，`BaseRequestDTO` 注释里 STT 是 `07`。接 STT 前必须先定编码口径，否则设备传 `providerId=07` 而服务端按 `14` 判断（或反之）会直接分流失败。

`collect-pay-server` 里真正参与分支判断的 `providerId` 只有 `"03"`（BOM），三处：`TvmOrderController.java:78`、`:169`、`:188`。设备类型主要靠 URL 前缀区分，而**没有 `/itpstt/**` 之类的 STT 入口**。

「自助终端」「自助设备」字面量零命中。唯一相关的「自助补站」是 **APP 侧手机自助补站**（`ExcessFareHandler`，见 [[02-BOM]] 的 017 一节），与 STT 设备无关；`DeviceTypeEnum.java:65-66` 的 `SELF_SERVICE_GATE("36", "自助补站手机")` 也是被 `ExcessFareHandler` 以硬编码字符串 `"36"` 拼进 deviceId（`:98`），不是通过枚举引用。

## 三个用例的处置

### ITP_Payment_018 STT 非现金收款
⚠️ 阻塞 — 无 STT 接入实现。若业务上 STT 复用 BOM 报文（`providerId` 区分），则等价于 [[02-BOM]] 的 015，需产品确认。

### ITP_Payment_019 STT 扫码充值
⚠️ 阻塞 — 同上，若复用 BOM 则等价于 016。

### ITP_Payment_020 STT 补票-超程
⚠️ 双重阻塞 — 既无 STT 接入，ITP 侧也无补票金额计算（见 [[02-BOM]] 017）。

## 需要先确认的问题

按代码现状，STT 有三种可能的落地方式，成本差别很大，**必须先定方向再写用例**：

1. **复用 BOM 报文与接口**，仅靠 `providerId` 区分 —— 改动最小，但 `collect-pay-server` 现在只对 `"03"` 做分支，需要确认 STT 走 BOM 分支时业务逻辑是否完全一致（凭证打印、操作员权限、业务类型集合）
2. **复用 TVM 报文** —— STT 若是乘客自助操作（无操作员），形态更接近 TVM 的 `scene="qrcode"`（乘客扫机器），而不是 BOM 的 `scene="scan"`（操作员扫乘客）
3. **新增独立入口** `/itpstt/ci/stt/**` —— 工作量最大

**方向 1 和 2 的支付方向是相反的**（谁扫谁），这决定了 `scene` 取值与报文字段，不是实现细节而是需求问题。请先向甲方或产品确认 STT 的实际交互形态。

同时要定编码口径：`07` 还是 `14`。

结果：☐ 阻塞（原因：STT 无接入实现，落地方式未定）

## 交叉引用

[[00-链路事实与配置字典]] · [[01-TVM]] · [[02-BOM]] · [[04-阻塞项与缺陷候选]]

## 参考文献

- `model/src/main/java/com/chinasofti/huateng/model/enums/DeviceTypeEnum.java`
- `docs/business/tvm-bom-pay.md`
- `AGENTS.md` §2.2.2（尚未落地的需求）
