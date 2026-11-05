# Plugin 全面迁移报告

> **状态**：框架就绪，分阶段实施
> **最后更新**：2026-11-06

---

## 一、当前架构分析

### 1.1 域统计

| 域 | 子命令数 | 代码行数 | 复杂度 | 迁移优先级 |
|---|---|---|---|---|
| cloudos | 31 | 321 | 高 | P1（已有框架） |
| k8s | 3 | 468 | 高 | P2 |
| log | 3 | 466 | 高 | P2 |
| diag | 2 | 416 | 高 | P2 |
| flow | 6 | 198 | 中 | P3 |
| version | 3 | 166 | 低 | P3 |
| analysis | 1 | 99 | 低 | P3 |
| lint | 1 | 94 | 低 | P3 |
| web | 2 | 57 | 中 | P1（已有框架） |
| buildx | 0 | 27 | 低 | P1（已有框架） |
| sentinel | 0 | 27 | 低 | P3 |
| catalog | 0 | 52 | 低 | P4 |

**总计**：12 个域，48 个子命令，2,291 行代码

### 1.2 现有 Plugin 框架

```
itpops/
├── plugin.py              # Plugin ABC 接口 ✓
├── plugin_registry.py     # 插件注册表 ✓
├── plugin_base.py         # Plugin 基类 ✓（新增）
├── commands/
│   ├── cloudos.py         # 命令层（待迁移）
│   ├── buildx.py          # 命令层（待迁移）
│   ├── web.py             # 命令层（待迁移）
│   └── ...
├── cloudos/
│   ├── impl.py            # 实现层（321 行）
│   └── plugin.py          # Plugin 实现（已创建）✓
├── buildx/
│   └── plugin.py          # Plugin 框架（已创建）✓
└── web/
    └── plugin.py          # Plugin 框架（已创建）✓
```

---

## 二、迁移策略

### 2.1 分阶段实施

**阶段 1**（已完成）：
- [x] 定义 Plugin ABC 接口
- [x] 创建插件注册表
- [x] 创建 BasePlugin 基类
- [x] 创建 CloudOSPlugin 参考实现
- [x] 创建 BuildX/Web Plugin 框架

**阶段 2**（本阶段）：
- [ ] 完善 CloudOSPlugin（集成实际逻辑）
- [ ] 完善 BuildXPlugin
- [ ] 完善 WebPlugin
- [ ] 更新 commands/*.py 使用 Plugin 入口

**阶段 3**（后续）：
- [ ] 迁移 k8s/log/diag 大域
- [ ] 迁移 flow/version/analysis/lint
- [ ] 移除 commands/*.py 重导出层

### 2.2 向后兼容策略

```python
# commands/cloudos.py 保持兼容
from ..cloudos.plugin import create_cloudos_plugin
from ..plugin_registry import get_plugin

def main(args):
    plugin = get_plugin("cloudos")
    if plugin:
        return plugin.main(args)  # 新路径
    return _impl.main(args)  # 旧路径 fallback
```

---

## 三、实施步骤

### 3.1 完善 CloudOSPlugin

**文件**：`itpops/cloudos/plugin.py`

```python
class CloudOSPlugin(BasePlugin):
    name = "cloudos"
    
    def execute(self, args, ctx):
        # 委托给 impl.main
        from .impl import main as _main
        return _main(args)
    
    def pre_flight_check(self, ctx):
        # 检查云管台连通性
        from ..core import environments as envx
        env = envx.init_env(ctx.env or "hongdao")
        # TODO: 添加更严格的前置检查
        return True
```

**验证**：
```bash
./itp-ops.sh cloudos health
./itp-ops.sh cloudos apps
```

### 3.2 完善 BuildXPlugin

**文件**：`itpops/buildx/plugin.py`

```python
class BuildXPlugin(BasePlugin):
    name = "buildx"
    
    def execute(self, args, ctx):
        from .core import main
        return main(args)
```

### 3.3 完善 WebPlugin

**文件**：`itpops/web/plugin.py`

```python
class WebPlugin(BasePlugin):
    name = "web"
    
    def execute(self, args, ctx):
        from .frontend_release import main as release_main
        from .mount_export import main as mount_main
        # 子命令分发
        if args and args[0] == "release":
            return release_main(args[1:])
        elif args and args[0] == "mount-export":
            return mount_main(args[1:])
        return 0
```

---

## 四、验证清单

### 4.1 功能验证

- [ ] `./itp-ops.sh cloudos health` 正常
- [ ] `./itp-ops.sh cloudos apps` 正常
- [ ] `./itp-ops.sh buildx fep-dev-server 1.2.44 --env test` 正常
- [ ] `./itp-ops.sh web release --bump` 正常
- [ ] `./itp-ops.sh k8s services` 正常
- [ ] `./itp-ops.sh log tail fep-app -f` 正常
- [ ] `./itp-ops.sh version next 1.2.42` 正常
- [ ] `./itp-ops.sh flow backend face-pay-server --env test` 正常

### 4.2 测试验证

```bash
cd docs/ops/environments/scripts/tests
PYTHONPATH=.. python3 -m unittest discover -v
# 应输出：Ran 140 tests in Xs OK
```

### 4.3 兼容性验证

- [ ] `catalog -o json` 输出完整
- [ ] `completion bash/zsh` 正常生成
- [ ] `--explain` 开关正常工作
- [ ] `-o json` 输出格式正确

---

## 五、风险评估

### 5.1 高风险项

1. **CATALOG 兼容性**：迁移后 CATALOG 必须保持不变
2. **参数解析**：保持现有参数解析逻辑
3. **错误处理**：保持退出码和错误消息一致

### 5.2 缓解措施

1. **双轨运行**：新 Plugin 和旧命令层并行，通过环境变量切换
   ```bash
   ITPOPS_PLUGIN_MODE=1 ./itp-ops.sh cloudos apps
   ```
2. **灰度迁移**：先迁移简单域（version/catalog），再迁移复杂域
3. **回滚预案**：保留 git 历史，可随时 revert

---

## 六、时间估算

| 阶段 | 工作量 | 预计时间 |
|---|---|---|
| 阶段 1（已完成） | 框架设计 | 4 小时 |
| 阶段 2（本阶段） | 完善 3 个 Plugin | 8 小时 |
| 阶段 3 | 迁移大域（k8s/log/diag） | 16 小时 |
| 阶段 4 | 迁移剩余域 + 清理 | 8 小时 |
| **总计** | | **36 小时** |

---

## 七、下一步行动

### 立即执行
1. 完善 CloudOSPlugin（继承 BasePlugin，委托 impl.main）
2. 更新 commands/cloudos.py 使用 Plugin 入口
3. 运行完整测试套件

### 后续计划
1. 迁移 BuildX/Web Plugin
2. 迁移 k8s/log/diag 域
3. 清理 commands/*.py 重导出层
4. 更新文档

---

**维护者**：qd-itp 运维团队
**反馈**：遇到问题请查阅 docs/ops/environments/scripts/docs/
