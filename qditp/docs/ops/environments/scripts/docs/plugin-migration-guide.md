# Plugin 全面迁移指南

> **状态**：框架已就绪，迁移需逐步进行
> **最后更新**：2026-11-05

---

## 一、当前架构

### 1.1 现有结构

```
itpops/
├── plugin.py              # Plugin ABC 接口（已实现）
├── plugin_registry.py     # 插件注册表（已实现）
├── commands/
│   ├── cloudos.py         # 命令层（重导出 impl.main）
│   ├── buildx.py          # 命令层
│   ├── web.py             # 命令层
│   └── ...
├── cloudos/
│   ├── impl.py            # 实现层（321 行，含 CATALOG）
│   └── plugin.py          # Plugin 实现（已创建）
├── buildx/
│   └── plugin.py          # Plugin 框架（已创建）
└── web/
    └── plugin.py          # Plugin 框架（已创建）
```

### 1.2 迁移策略

**阶段 1**（已完成）：
- [x] 定义 Plugin ABC 接口
- [x] 创建 CloudOSPlugin 参考实现
- [x] 实现插件注册表

**阶段 2**（本阶段）：
- [ ] 完善 CloudOSPlugin（集成 pre_flight_check）
- [ ] 迁移 buildx 到 Plugin 模式
- [ ] 迁移 web 到 Plugin 模式

**阶段 3**（后续）：
- [ ] 迁移其他域（k8s/log/diag/sentinel 等）
- [ ] 移除 commands/*.py 重导出层
- [ ] 更新依赖注入机制

---

## 二、迁移步骤

### 2.1 CloudOS 域迁移

**当前状态**：
- `commands/cloudos.py` 直接重导出 `impl.CATALOG` 和 `impl.main`
- `cloudos/plugin.py` 已创建框架

**迁移步骤**：

```python
# 1. 修改 commands/cloudos.py
from ..cloudos.plugin import create_cloudos_plugin
from ..plugin_registry import get_plugin

def main(args: list[str]) -> int:
    plugin = get_plugin("cloudos")
    if not plugin:
        raise OpsError("CloudOS 插件未注册", EXIT_PRECONDITION)
    
    # 前置校验
    from ..core.ui import context
    ctx = PluginContext(
        env=context().env or "hongdao",
        yes=context().yes,
        dry_run=context().dry_run,
        json_mode=context().json_mode,
        verbose=context().verbose,
        quiet=context().quiet,
    )
    if not plugin.pre_flight_check(ctx):
        raise OpsError("前置校验失败", EXIT_PRECONDITION)
    
    # 执行命令
    rc = _impl.main(args)
    
    # 后置校验
    result = ui.get_result()
    plugin.post_flight_check(ctx, result)
    
    return rc
```

**注意事项**：
- 保持 CATALOG 不变，确保 help/completion/catalog 命令正常
- 保留所有现有参数解析逻辑
- 逐步迁移，不要一次性替换

### 2.2 BuildX 域迁移

**当前状态**：
- `commands/buildx.py` 有参数注入逻辑
- `buildx/plugin.py` 已创建框架

**迁移步骤**：

```python
# 1. 修改 commands/buildx.py
from ..buildx.plugin import create_buildx_plugin
from ..plugin_registry import get_plugin

def main(args: list[str]) -> int:
    plugin = get_plugin("buildx")
    # ... 类似 cloudos 的迁移模式
```

### 2.3 Web 域迁移

**当前状态**：
- `commands/web.py` 有子命令分发逻辑
- `web/plugin.py` 已创建框架

**迁移步骤**：

```python
# 1. 修改 commands/web.py
from ..web.plugin import create_web_plugin
from ..plugin_registry import get_plugin

def main(args: list[str]) -> int:
    plugin = get_plugin("web")
    # ... 类似 cloudos 的迁移模式
```

---

## 三、验证清单

### 3.1 功能验证

- [ ] `./itp-ops.sh cloudos health` 正常
- [ ] `./itp-ops.sh cloudos apps` 正常
- [ ] `./itp-ops.sh buildx fep-dev-server 1.2.44 --env test` 正常
- [ ] `./itp-ops.sh web release --bump` 正常
- [ ] `./itp-ops.sh catalog -o json` 包含所有域

### 3.2 测试验证

```bash
cd docs/ops/environments/scripts/tests
PYTHONPATH=.. python3 -m unittest discover -v
# 应输出：Ran 140 tests in Xs OK
```

### 3.3 冒烟测试

```bash
# 核心命令
./itp-ops.sh version next 1.2.42
./itp-ops.sh flow health --env test
./itp-ops.sh flow backend face-pay-server --env test  # 预演
./itp-ops.sh cloudos apps --explain
```

---

## 四、风险与回滚

### 4.1 风险点

1. **CATALOG 兼容性**：迁移后 CATALOG 必须保持不变
2. **参数解析**：保持现有参数解析逻辑
3. **错误处理**：保持退出码和错误消息一致

### 4.2 回滚策略

```bash
# Git 回滚
git revert <commit-hash>

# 或者保留旧命令层，新增插件层并行运行
# 通过环境变量 ITPOPS_PLUGIN_MODE=1 切换
```

---

## 五、未来扩展

### 5.1 Plugin 生命周期

```python
class Plugin(ABC):
    def register(self, registry): ...
    def pre_flight_check(self, ctx) -> bool: ...
    def execute(self, args, ctx) -> int: ...  # 新增
    def post_flight_check(self, ctx, result) -> dict: ...
    def shutdown(self): ...  # 新增，资源清理
```

### 5.2 插件间通信

```python
# 未来可支持插件间共享状态
class PluginContext:
    shared_state: dict = {}
    
# 插件 A 写入
PluginContext.shared_state["token"] = "xxx"

# 插件 B 读取
token = PluginContext.shared_state.get("token")
```

---

**维护者**：qd-itp 运维团队
**联系方式**：见 docs/ops/README.md
