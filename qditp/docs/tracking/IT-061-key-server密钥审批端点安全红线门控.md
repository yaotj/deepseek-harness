# IT-061 key-server 密钥审批端点安全红线门控

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-061` 一行（重档：涉及密钥的安全红线改动，AGENTS.md §4.2）。

## 1. 背景 / 现象
- 发现时间：2026-11-04
- 发现渠道：对标旧综管台补建缺页（`docs/testing/新旧ITP综管台对标-2026-11-03.md`）实施过程中的自查
- 影响范围：key-server 新增操作型端点 `POST /page/key/agm-version/approval`，写 `METRO_AGM_KEY_VERSION.PUBLIC_KEY_VERSION_STATUS`（20010 初始化 → 20020 审批通过 / 20030 拒绝）
- 现象描述：本批 7 个后端模块中，collect-pay / face-pay / gate-txn-pay / recon / alipay-pay-sign / blacklist 均为只读查询 + 灰名单最小写，**只有 key-server 触碰密钥域写路径**。按 §4.2 安全红线「涉及支付、密钥、签名的代码改动，NEVER 擅自修改现有加密/签名逻辑，MUST 提示人工复核安全合规性」，该端点必须过人工复核后才能随 key-server 发版。

## 2. 根因分析
- 排查过程：本仓此前 `METRO_AGM_KEY_VERSION` 只有只读查询（`KeyVersionQueryService`），没有任何审批状态写路径；本轮对标补建为旧综管台「密钥操作」页补齐了审批入口，属**新增**写路径，未改动既有加密/签名逻辑（密钥材料字段一个未碰）。
- 根因结论：改动本身合规（白名单状态机 + DB CAS，`AgmKeyApprovalStatusWhitelistTest` 4/4 通过），但属安全红线动作，**风险在审批语义而非代码实现**——「谁有资格把某批次密钥版本标记为审批通过」是业务/安全决策，必须由业主与安全合规确认口径后才能上线。

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A | 7 模块一起发，key-server 审批端点同时上线 | 一次滚更完成 | 未经安全复核的密钥域写路径先到线上 | 违反 §4.2 红线 |
| 方案B | 门控：key-server 审批端点代码保留在仓，待人工安全合规复核通过后，key-server 才随本批发测试环境；其余 6 模块先发 | 红线不破，进度不阻塞 | key-server 本批只上只读查询增量，审批按钮需前端同步门控（菜单 SQL 不含审批权限行，复核通过后再执行） | 无 |

## 4. 决策与结论
- 选定方案：方案B（门控）。
- 决策理由：§4.2 安全红线为硬约束；审批语义（20010→20020 的放行口径、MANAGER_ID 取值）须业主/安全合规复核。
- 影响范围 / 关联模块：key-server（`AgmKeyApprovalStatus` / `MetroAgmKeyVersionMapper.transitionApprovalStatus` / `AgmKeyApprovalServiceImpl` / `KeyPageController#approveAgmVersion`）＋ web 前端 `views/trans/key/version/index.vue` 审批对话框＋ `api/trans/keyVersion.js#approveAgmKeyVersion`。

## 5. 验证与回滚
- 验证方式 / 结果：`AgmKeyApprovalStatusWhitelistTest` 4/4 通过（白名单出边锁定：INIT→APPROVED/REJECTED 放行，终态零出边，INIT 不可自环）；service 层 CAS 0 行回查区分幂等命中与并发冲突（已代码评审，未上线实测）。
- 回滚预案：端点未发版前无回滚；发版后如需停用，回滚 = 撤下该 jar 镜像（版本回退），`METRO_AGM_KEY_VERSION` 已有行不新增、只可能改过 20010→20020/20030 的 `PUBLIC_KEY_VERSION_STATUS`，发版前 MUST 存原值并提供还原 SQL（测试环境破坏性写库约定）。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-061`）
- [x] 已建本处理文档并记录处理过程与决策
- [ ] 处理后已回填索引「状态 / 下一步 / 更新」（复核通过并发版后回填）
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字已标注来源或「MUST 现查」（状态码 20010/20020/20030 来自 `AgmKeyApprovalStatus` 常量；jar 版本 MUST 现查 K8s Deployment）
- 偏差说明（如有）：无
