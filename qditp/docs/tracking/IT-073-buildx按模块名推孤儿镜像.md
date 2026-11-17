# IT-073 buildx 按模块名推镜像成孤儿镜像（应用名/仓库名 ≠ 模块名）

## 一、背景

2026-11-17 14:14 测试环境发版（行程查询透出支付中心优惠，commit `ec79a63d`）：模块 `trans-query-server` 对应的应用与镜像仓库名是 **trans-query**（CloudOS 应用 `trans-query`，镜像 `itp/trans-query`，同 VL 映射规律：fep-acc→fep-acc-server 等）。按惯性执行：

```bash
itp-ops.sh buildx trans-query-server 1.0.28 --env test --dockerfile ...
```

buildx 的 `imgName` 直接取第一个参数（模块名），镜像被推成 `itp/trans-query-server:1.0.28`——**没有任何应用引用该仓库**，随后 `cloudos deploy trans-query-server 1.0.28` 报「未找到应用」，才发现名字错了。重新以 `buildx trans-query 1.0.28 --jar trans-query-server/target/...` 推正确仓库后才发成。

误推的 `itp/trans-query-server:1.0.28` 成孤儿镜像，手工清理只能翻《CloudOS 5.0 API》文档调 `POST /api/cloud/hcr/v1.0/images/deleteOneImage`（版本列表响应里**没有 id 字段**，`id` 传空串即可定位——文档未写明）。

## 二、根因

1. **buildx 缺仓库名校验**：`imgName=mod` 无任何前置检查，模块名≠仓库名时静默推孤儿镜像。
2. **应用解析失败无建议**：`resolve_app_uuid` 未命中只报「未找到应用：trans-query-server」，不提示相近的 `trans-query`——「应用名≠模块名」是本仓反复出现的规律（VL 映射表 8 对、记忆 §5），报错面没有任何引导。
3. **无镜像删除命令**：itp-ops 只有构建/探测/部署， Harbor 清理无收口，只能 raw 调 API。

## 三、方案对比

| 方案 | 做法 | 评价 |
|---|---|---|
| A. buildx 硬拒（仓库名不匹配即退出） | 构建前校验 mod ∈ 应用仓库集合，不在即拒 | 误伤**独立镜像**（基础镜像 itp/jdk、工具镜像 kennethreitz-httpbin 等本就无应用引用），NO |
| B. **WARNING + 建议**（选定） | 不匹配打 WARNING（含相近仓库名与 IT-073 指引），非阻断；`--check` 同样覆盖 | 保留独立镜像构建能力，同时让惯性操作显眼；成本一次只读 API |
| C. 模块→仓库映射表硬编码 | 维护 `trans-query-server→trans-query` 等映射 | 与 VL 映射同源漂移风险，新增应用即过期，NO |

删除命令护栏对比：占用检查选**应用 container_image 尾匹配**（deploy 同款口径），不引入 Harbor「被引用」原生概念（平台 API 不暴露）；删后复核选再查版本列表而非信响应（平台删除可能异步）。

## 四、修复（itpops 脚本，3 处）

1. **`buildx` 仓库名警示**：构建前（含 `--check`）列全部应用 `container_image` 的仓库名集合，`itp/<mod>` 不匹配即 stderr WARNING（相近仓库名提示 + IT-073 指引）；列应用失败静默降级（NEVER 阻断构建）。仓库名解析纯函数 `_repo_warning(mod, repos)` 可单测。
2. **`resolve_app_uuid` 相近建议**：未命中时按 `cli/suggest.closest` 列相近应用名（如 trans-query-server → trans-query），附「应用名≠模块名」提示；成功路径 stdout/stderr 与旧版逐字兼容。
3. **`cloudos image rm <仓库名> <tag> [--project <名>] [--yes]`**（新命令）：
   - 三道护栏：**tag 存在性核验**（打错名/项目即拒，列出现存 tag）→ **应用占用即拒**（任一应用 `container_image` 尾匹配该 `repo:tag`）→ `--yes` 写操作门；
   - 删除走 `POST /api/cloud/hcr/v1.0/images/deleteOneImage`（`id` 空串定位），删后**自动复核**版本列表，仍在则告警返回 1；
   - `cloudos --help` / `catalog` 同步登记。

## 五、验证

- 单测 184 例全过（新增 16 例：`test_image_rm.py` 守删除封装/三道护栏/复核、仓库名警示纯函数、应用名建议；`test_catalog_is_complete` 基线 30→31 并断言 `image rm` 在册）。
- 真实测试环境：
  - `cloudos pods trans-query-server` → 「未找到应用」+「你是不是想找：trans-query、paraquery-service、traffic-realtime」✅
  - `cloudos image rm trans-query-server 9.9.9 --yes` → 「没有 tag 9.9.9（现存：1.0.26, 1.0.25, 1.0.24, 1.0.22）」拒删，未发写请求 ✅
  - `buildx trans-query-server 9.9.9 --check` → ⚠「与全部 53 个应用镜像仓库名都不匹配；相近仓库：trans-query…」后正常输出 check JSON ✅
  - 误推孤儿镜像 `itp/trans-query-server:1.0.28` 已用新命令的前身 API 手工删除（删除前占用检查=0 应用引用，历史 unused tag 未动）。

## 六、教训

1. **「应用名≠Deployment 名≠模块名≠仓库名」四个名字空间在本仓系统性错位**（VL 映射 8 对 + 本例），任何「按惯性拼名字」的环节 MUST 有校验或建议面。
2. 平台 API 的字段**以实测响应为准**：文档写 `id` 必填，实测 image/versions 响应无 id、传空串即定位——脚本注释已记录。
3. 孤儿镜像的「无害」是错觉：Harbor 存储持续占用、tag 语义混淆（同仓 1.0.26 与 itp/trans-query:1.0.26 是两个不同产物）。清理收口进 itp-ops 后，「推错→发现→删除」闭环 3 条命令内完成。
