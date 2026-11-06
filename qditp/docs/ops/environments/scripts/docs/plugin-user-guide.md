# Plugin 系统使用指南

> **版本**：2.9.4
> **最后更新**：2026-11-06
> **状态**：生产就绪

---

## 一、快速开始

### 1.1 查看已注册 Plugin

```bash
# 查看所有已注册 Plugin
python3 -c "
import sys
sys.path.insert(0, 'docs/ops/environments/scripts')
from itpops.plugin_registry import list_plugins
print('已注册 Plugin:', list_plugins())
"
```

### 1.2 切换到 Plugin 模式

```bash
# 启用 Plugin 模式
export ITPOPS_PLUGIN_MODE=1

# 执行命令（走 Plugin 路径）
./itp-ops.sh cloudos apps
./itp-ops.sh flow backend face-pay-server --env test --yes
```

### 1.3 查看 Plugin 帮助

```bash
# 所有命令支持 --explain
./itp-ops.sh cloudos apps --explain
./itp-ops.sh flow backend --explain
./itp-ops.sh version next --explain 1.2.42
```

---

## 二、Plugin 架构

### 2.1 核心组件

```
itpops/
├── plugin.py              # Plugin ABC 接口
├── plugin_base.py         # BasePlugin 基类
├── plugin_registry.py     # 插件注册表
├── plugin_auto_register.py # 自动发现与注册
└── commands/
    └── *.py               # 命令层（向后兼容）
└── <特性>/
    └── plugin.py          # Plugin 实现
```

### 2.2 Plugin 接口

```python
from itpops.plugin_base import BasePlugin, PluginContext

class MyPlugin(BasePlugin):
    name = "my-domain"
    
    def execute(self, args: list[str], ctx: PluginContext) -> int:
        """执行命令的核心逻辑。"""
        return 0
    
    def pre_flight_check(self, ctx: PluginContext) -> bool:
        """前置校验。"""
        return True
    
    def post_flight_check(self, ctx: PluginContext, result: dict) -> dict:
        """后置校验。"""
        return result
    
    def get_catalog(self) -> dict:
        """获取 CATALOG。"""
        return {
            "name": self.name,
            "summary": "描述",
            "subcommands": [],
        }
```

### 2.3 工厂函数

```python
def create_my_plugin() -> MyPlugin:
    """创建 Plugin 实例。"""
    return MyPlugin()
```

---

## 三、现有 Plugin

### 3.1 P1 阶段（核心运维）

| Plugin | 用途 | 子命令 |
|---|---|---|
| `cloudos` | 云管台运维 | 31 个（apps/env/pods/scale/deploy...） |
| `buildx` | 镜像构建 | 命令行参数 |
| `web` | 前端发版 | release/mount-export |

### 3.2 P2 阶段（部署与日志）

| Plugin | 用途 | 子命令 |
|---|---|---|
| `k8s` | K8s 部署 | deploy/deploy-all/services |
| `log` | 日志查看 | tail/watch/follow |
| `diag` | 网络诊断 | reach/routes |

### 3.3 P3 阶段（工作流与工具）

| Plugin | 用途 | 子命令 |
|---|---|---|
| `flow` | 一键工作流 | health/backend/frontend/jvm/rollback/integration/batch-jvm/batch-bump/cleanup/health-report |
| `version` | 版本管理 | next/debug/bump |
| `analysis` | 代码分析 | endpoints |
| `lint` | 仓库门禁 | yaml |

### 3.4 P4 阶段（元数据）

| Plugin | 用途 | 子命令 |
|---|---|---|
| `catalog` | 命令注册表 | （无子命令） |
| `completion` | Shell 补全 | bash/zsh |
| `help` | 帮助系统 | （无子命令） |

---

## 四、环境变量

### 4.1 运行时配置

| 变量 | 说明 | 示例 |
|---|---|---|
| `ITPOPS_PLUGIN_MODE` | 启用 Plugin 模式 | `export ITPOPS_PLUGIN_MODE=1` |
| `ITP_OPS_ENV` | 默认环境 | `export ITP_OPS_ENV=test` |
| `ITP_ENV_<FIELD>` | 覆盖环境配置 | `export ITP_ENV_k8s_ns=my-ns` |
| `NO_COLOR` | 关闭颜色 | `export NO_COLOR=1` |
| `CLICOLOR_FORCE` | 强制颜色 | `export CLICOLOR_FORCE=1` |

### 4.2 模块级 Hook

```bash
# 在模块目录下创建 itp-hook.sh
cat > face-pay-server/itp-hook.sh << 'HOOK'
#!/bin/bash
# 在 build 前执行
on_pre_build() {
    echo "Running pre-build checks..."
    # 你的自定义逻辑
}

# 在 build 后执行
on_post_build() {
    echo "Build completed, running post-build tasks..."
}

# 在 deploy 前执行
on_pre_deploy() {
    echo "Running pre-deploy validation..."
}
HOOK

chmod +x face-pay-server/itp-hook.sh
```

---

## 五、最佳实践

### 5.1 开发新 Plugin

1. **创建目录结构**
   ```bash
   mkdir -p itpops/<domain>
   touch itpops/<domain>/__init__.py
   cat > itpops/<domain>/plugin.py << 'EOF'
   # Plugin 实现
   EOF
   ```

2. **实现接口**
   - 继承 `BasePlugin`
   - 实现 `execute()` 方法
   - 提供工厂函数 `create_<domain>_plugin()`

3. **注册 CATALOG**
   ```python
   CATALOG = {
       "name": "<domain>",
       "summary": "描述",
       "default_env": "test",
       "subcommands": [...],
   }
   ```

4. **测试验证**
   ```bash
   python3 -m unittest tests.test_<domain>
   ./itp-ops.sh <domain> --explain
   ```

### 5.2 双轨运行

默认模式下，命令走旧路径（commands/*.py），不影响现有功能。
启用 Plugin 模式后，命令走新路径（plugin/*.py），可通过环境变量切换。

```bash
# 兼容模式（默认）
./itp-ops.sh cloudos apps

# Plugin 模式
ITPOPS_PLUGIN_MODE=1 ./itp-ops.sh cloudos apps
```

### 5.3 错误处理

Plugin 模式下的错误处理与兼容模式一致：
- 用法错误：exit 2
- 前置不满足：exit 3
- 传输失败：exit 4
- 执行失败：exit 5
- 缺 --yes：exit 6

---

## 六、故障排查

### 6.1 常见问题

**Q: Plugin 未注册？**
```bash
# 检查注册状态
python3 -c "
from itpops.plugin_registry import list_plugins
print(list_plugins())
"
```

**Q: 前置校验失败？**
```bash
# 查看详细错误
./itp-ops.sh <domain> <command> --trace
```

**Q: 切换回兼容模式？**
```bash
# 确保 ITPOPS_PLUGIN_MODE 未设置
unset ITPOPS_PLUGIN_MODE
./itp-ops.sh <domain> <command>
```

### 6.2 调试技巧

```bash
# 查看详细日志
./itp-ops.sh <domain> <command> -vvv

# 打印完整堆栈
./itp-ops.sh <domain> <command> --trace

# JSON 输出便于排查
./itp-ops.sh <domain> <command> -o json
```

---

## 七、迁移指南

### 7.1 从 commands/ 迁移到 Plugin

1. **创建 Plugin 文件**
   ```bash
   mkdir -p itpops/<domain>
   cat > itpops/<domain>/plugin.py
   ```

2. **实现 BasePlugin**
   ```python
   from ..plugin_base import BasePlugin
   
   class DomainPlugin(BasePlugin):
       name = "<domain>"
       
       def execute(self, args, ctx):
           # 委托给原 commands/<domain>.py 的 main()
           from ..commands.<domain> import main
           return main(args)
   ```

3. **添加工厂函数**
   ```python
   def create_<domain>_plugin():
       return DomainPlugin()
   ```

4. **验证兼容性**
   ```bash
   ./itp-ops.sh <domain> --explain
   python3 -m unittest tests.test_<domain>
   ```

---

**维护者**：qd-itp 运维团队
**反馈**：遇到问题请查阅 docs/ops/environments/scripts/docs/ 或联系运维负责人
