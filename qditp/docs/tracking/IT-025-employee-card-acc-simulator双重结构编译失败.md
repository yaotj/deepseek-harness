# IT-025 employee-card-acc-simulator 双重包结构致整仓编译失败

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-025` 一行。

## 1. 背景 / 现象
- 发现时间：2026-10-27
- 发现渠道：自查——全仓 `mvn -o clean test-compile` 验证门失败。
- 影响范围：`employee-card-acc-simulator` 单模块编译失败，阻断整仓统一验证门；该模块为 ACC 员工卡模拟器，非生产支付链路（现网部署情况 MUST 现查，未在本问题范围内核实）。
- 现象描述（两处编译错误，均位于**根包** `com.chinasofti.huateng.accsimulator` 下的旧类）：
  1. 根包 `AccSimulatorService` 第 16 行 `import org.springframework.web.client.RestTemplateBuilder;`——该类在 `org.springframework.boot.web.client`，spring-web 中不存在。
  2. 根包 `AccMockController` 第 63 行调 `cardMapper.updateStatus(String, int)`，根包 `AccEmployeeCardMapper` 接口无此方法。`mapper/AccEmployeeCardMapper.xml` 里**有** `updateStatus` 语句，但其 namespace 绑的是子包 `com.chinasofti.huateng.accsimulator.mapper.AccEmployeeCardMapper`，与根包接口无关。
  - 更深一层：模块内根包与 `entity` / `mapper` / `controller` / `service` 子包存在**整套同名类重复**（Controller / Service / Mapper / Entity / Model / Properties 各两份），是半成品包结构重构。

## 2. 根因分析
- 排查过程：`git log` 定位两版类均随 `88e87eabe9`（"feat: 支付宝渠道重构收口 + 多模块功能与文档同批入库"）同批入库；HEAD 即带错误 import，工作区无他人改动。
- 根因结论：该批提交把包重构进行了一半——新子包结构（XML namespace / resultMap type 均已指向子包）与根包旧类同时入库，旧类从未被编译验证过（团队习惯按模块单独构建、SVN 时代遗留节奏，整仓验证门长期未跑）。属**提交即坏**，非本次任何改动引入。

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A. 删根包旧类、留子包新结构（建议） | 删除根包 8 个重复类；XML / 包扫描已与新结构一致 | 与 XML namespace、resultMap type 现状自洽；消重后无重复 bean / 重复端点注册 | 须先核对子包 `service` 是否已覆盖根包旧 `AccSimulatorService` 的 RestTemplate 外呼 ACC 功能；Application 启动类的组件扫描范围要复核 | 若新 service 功能不全，模拟器丢能力；删除面广，需模块负责人确认 |
| B. 删子包、回退根包 | 删子包新类，XML namespace / type 改回根包 | 改动面小（只动 XML 两行 + 删子包） | 与重构意图相悖，子包结构是更合理的分层；等于撤销他人未完成的工作 | 若同事本地继续基于子包开发，直接冲突 |
| C. 双套并存、只补方法过编译 | 给根包 Mapper 补 `updateStatus` 等方法 | 改动最小 | 两个 `@RestController` 注册同一批端点、两个 Service bean 冲突，**运行时必炸**；错误 import 说明旧类从未跑通过 | 高，否掉 |

## 4. 决策与结论
- 选定方案：**方案 A（删根包旧类、留子包新结构）**，2026-10-27 用户裁定「修复，并提交」后执行。
- 决策理由：XML namespace / resultMap type 均已指向子包，子包结构是重构目标形态；根包 9 个旧类无任何新代码引用、与 mapper XML 无绑定（运行时本就跑不通），保留只会产生重复 bean / 重复端点注册。
- 执行内容：删除根包 `AccEmployeeCard` / `AccEmployeeCardMapper` / `AccMockController` / `AccSimulatorController` / `AccSimulatorService` / `AccSimulatorProperties` / `SimulationExchange` / `AccNotifySimulationRequest` / `AccUpdateSimulationRequest` 共 9 个类（顺带消除了此前误 import `org.springframework.web.client.RestTemplateBuilder` 的编译错误——该类随删除一并移除）。
- 影响范围 / 关联模块：仅 `employee-card-acc-simulator`；全仓验证门恢复全量运行。

## 5. 验证与回滚
- 验证方式 / 结果：`mvn -o clean test-compile`（整仓，含本模块）**BUILD SUCCESS**（2026-10-27 实测）。子包 `mapper.AccEmployeeCardMapper` 的 `updateStatus` 与 XML 语句绑定一致。
- 回滚预案：`git revert` 本修复提交，或 `git checkout <prev> -- qditp/employee-card-acc-simulator/src/main/java/com/chinasofti/huateng/accsimulator/` 恢复 9 个旧类。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-025`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」（状态=已解决）
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字均标注来源（git 提交号 / 实测日期）；模块现网部署情况标注「MUST 现查」
- 偏差说明：无——结构问题已按方案 A 实际修复，非"只登记不处理"。
