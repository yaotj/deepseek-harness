"""Plugin 基类 —— 所有域的 Plugin 实现 MUST 继承此类。

设计目标：
  · 统一 Plugin 接口，支持 CATALOG 注册、前置/后置校验
  · 提供 default_main() 方法，自动处理 help/--explain/--yes 等全局参数
  · 子类只需实现 execute() 方法

使用示例：
  class K8sPlugin(BasePlugin):
      name = "k8s"
      
      def execute(self, args: list[str], ctx: PluginContext) -> int:
          # 实现具体逻辑
          return 0
  
  # 注册
  from .plugin_registry import register
  register(K8sPlugin())
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any

from .plugin import Plugin, PluginContext


class BasePlugin(Plugin, ABC):
    """Plugin 基类，提供通用功能和默认行为。"""

    @property
    @abstractmethod
    def name(self) -> str:
        """插件名称（对应 itp-ops.sh 的域名）。"""
        ...

    @abstractmethod
    def execute(self, args: list[str], ctx: PluginContext) -> int:
        """执行命令的核心逻辑。

        参数:
          args: 命令参数列表
          ctx: 插件执行上下文

        返回:
          退出码（0 成功，非 0 失败）
        """
        ...

    def register(self, registry: "CommandRegistry") -> None:
        """注册插件到命令注册表（从子模块加载 CATALOG）。"""
        try:
            catalog = self.get_catalog()
            if catalog:
                registry.add_domain(catalog)
        except Exception as exc:
            print(f"[WARN] 加载 {self.name} CATALOG 失败: {exc}", file=__import__('sys').stderr)

    def get_catalog(self) -> dict | None:
        """获取域的 CATALOG（子类可覆写）。

        默认从 commands/<name>.py 导入。
        """
        try:
            import importlib
            mod = importlib.import_module(f".commands.{self.name}", package="itpops")
            return getattr(mod, "CATALOG", None)
        except (ImportError, AttributeError):
            return None

    def pre_flight_check(self, ctx: PluginContext) -> bool:
        """前置校验（默认放行，子类可覆写）。"""
        return True

    def post_flight_check(self, ctx: PluginContext, result: dict) -> dict:
        """后置校验（默认返回原结果，子类可覆写）。"""
        return result

    def main(self, args: list[str]) -> int:
        """统一入口，处理全局参数并调用 execute()。

        这是 commands/<name>.py 调用的方法。
        """
        from ..core.ui import context, ui
        from ..errors import EXIT_USAGE, OpsError

        ctx = context()

        # 处理帮助请求
        if args and args[0] in ("help", "-h", "--help"):
            catalog = self.get_catalog()
            if catalog:
                from ..cli import help as help_mod
                return help_mod.render_catalog(self.name, catalog)
            return 0

        # 处理 --explain
        if "--explain" in args:
            explain_args = [a for a in args if a != "--explain"]
            from ..cli.explainer import handle_explain
            return handle_explain([self.name] + explain_args)

        # 创建插件上下文
        plugin_ctx = PluginContext(
            env=ctx.env or "test",
            yes=ctx.yes,
            dry_run=ctx.dry_run,
            json_mode=ctx.json_mode,
            verbose=ctx.verbose,
            quiet=ctx.quiet,
        )

        # 前置校验
        if not self.pre_flight_check(plugin_ctx):
            raise OpsError(f"{self.name} 前置校验失败", 3)

        # 执行命令
        rc = self.execute(args, plugin_ctx)

        # 后置校验
        result = ui.get_result()
        self.post_flight_check(plugin_ctx, result)

        return rc if isinstance(rc, int) else 0
