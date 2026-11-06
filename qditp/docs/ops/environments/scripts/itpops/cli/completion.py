"""shell 补全生成 —— 从 CATALOG 派生，域/子命令/选项增删后自动同步。

用法（与 kubectl completion 同形）：
  itp-ops.sh completion bash      → 标准输出补全脚本
  itp-ops.sh completion zsh
安装示例：
  bash: source <(itp-ops.sh completion bash)                 # 当前 shell 临时
        itp-ops.sh completion bash > /usr/local/etc/bash_completion.d/itp-ops
  zsh : itp-ops.sh completion zsh > "${fpath[1]}/_itp-ops"   # 再 compinit

补全范围：顶层域 / 各域子命令 / 从 CATALOG 自动抽取的域专有 --选项 / 全局参数 /
仓库根下的模块目录（flow backend|jvm 的第一个位置参数、--module/--app/--image）；
bash 另对 --env、-o/--output、--size 做取值补全，--jar/--dockerfile 走文件路径补全。
"""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET

from ..core.runtime import repo_root
from ..errors import OpsError
from .registry import load_domains

_GLOBAL_FLAGS = (
    "-e --env -y --yes -n --dry-run -o --output -q --quiet "
    "-v --verbose --no-color --trace -h --help -V --version"
)

_BUILTINS = ("catalog", "completion", "help")

# bash：这些选项后面按 Tab 给取值，而不是再列一堆 flag
_VALUE_FLAGS = {
    "-e": "hongdao test",
    "--env": "hongdao test",
    "-o": "text json",
    "--output": "text json",
    "--size": "4Gi 8Gi",
}


def _domain_map() -> dict[str, list[str]]:
    """域/顶层命令 → 子命令词表（多词命令如 `image build` 拆成单词，便于补全）。"""
    m: dict[str, list[str]] = {}
    for d in load_domains():
        words: list[str] = []
        for s in d.get("subcommands", []):
            for w in (s.get("name", "") or "").split():
                if w and w not in words:
                    words.append(w)
        m[d.get("name", "")] = words + ["help"]
    for b in _BUILTINS:
        m.setdefault(b, [])
    return m


def _domain_flags() -> dict[str, list[str]]:
    """从 CATALOG 的 usage / args 抽取域专有 --选项。

    flow 的 --module / --app / --image、cloudos 的 --app-version 等都写在 CATALOG 里，
    自动派生可避免「加了新命令却忘了补到补全脚本」的漂移。
    """
    out: dict[str, list[str]] = {}
    for d in load_domains():
        flags: list[str] = []
        for s in d.get("subcommands", []):
            texts = [str(s.get("usage") or "")]
            for a in s.get("args") or []:
                texts.append(str(a.get("name", "")) if isinstance(a, dict) else str(a))
            for text in texts:
                # 长 --选项与短 -x 一起抽（如 guide 的 "-i / --interactive"）
                for f in re.findall(r"(?<![\w-])--?[a-z][a-z0-9-]*", text):
                    if f not in flags:
                        flags.append(f)
        out[d.get("name", "")] = flags
    return out


def _top_level() -> list[str]:
    names = [d.get("name", "") for d in load_domains() if d.get("name")]
    for b in _BUILTINS:
        if b not in names:
            names.append(b)
    return names


def module_names() -> list[str]:
    """根 pom `<modules>` 中的模块目录名 —— flow backend 位置参数 / 发布清单的补全真源。

    2026-11-06 起以 reactor 为准，NEVER 再按「目录下有没有 pom.xml」扫描：
    acc-secure-server 有自己的 pom.xml 却不在根 pom <modules> 里，
    `mvn -pl acc-secure-server -am` 必然报 "Could not find the selected project
    in the reactor"，补全出来只会把人带进一条必失败的命令。详见
    itpops/commands/k8s.py 的 ALL_SERVICES 头注（同日已从发布清单移除）。

    生成补全脚本时静态嵌入（不每次 Tab 起 Python 进程）；新增模块后重新
    source 一次补全脚本即可，见 completion -h 的说明。解析失败时宁可返回空
    词表，也不退回目录扫描给出可能不可构建的候选。
    """
    try:
        root = repo_root()
    except OpsError:
        return []
    try:
        tree = ET.parse(root / "pom.xml")
        # `{*}` 通配 namespace：pom 带不带 xmlns 都能取到 <modules>/<module>。
        found = tree.findall(".//{*}modules/{*}module") or tree.findall(".//modules/module")
        names = sorted({e.text.strip() for e in found if e.text and e.text.strip()})
        return names
    except (OSError, ET.ParseError):
        return []


def bash_script() -> str:
    m = _domain_map()
    flags = _domain_flags()
    cases = "\n".join(
        '    {n}) COMPREPLY=( $(compgen -W "{w} $GLOBAL_FLAGS" -- "$cur") ) ;;'.format(
            n=name, w=" ".join(subs + flags.get(name, []))
        )
        for name, subs in m.items()
    )
    values = "\n".join(
        '    {f}) COMPREPLY=( $(compgen -W "{v}" -- "$cur") ); return ;;'.format(f=f, v=v)
        for f, v in _VALUE_FLAGS.items()
    )
    modules = " ".join(module_names())
    flow_flags = " ".join(flags.get("flow", []))
    top = " ".join(_top_level())
    return f"""# bash completion for itp-ops.sh —— 由 `itp-ops.sh completion bash` 生成
# 启用（当前 shell 临时）：source <(./itp-ops.sh completion bash)
# 永久：./itp-ops.sh completion bash > /usr/local/etc/bash_completion.d/itp-ops
# 词表来自 CATALOG，新增域 / 子命令 / --选项后重新生成即可，无需手改本文件。
_itp_ops_complete() {{
  local cur prev="" domain="" i w
  local -a pos=()
  cur="${{COMP_WORDS[COMP_CWORD]}}"
  [[ $COMP_CWORD -gt 0 ]] && prev="${{COMP_WORDS[COMP_CWORD-1]}}"
  local GLOBAL_FLAGS="{_GLOBAL_FLAGS}"
  local FLOW_FLAGS="{flow_flags}"
  local MODULES="{modules}"
  case "$prev" in
    --module|--app|--image) COMPREPLY=( $(compgen -W "$MODULES" -- "$cur") ); return ;;
    --jar|--dockerfile) COMPREPLY=( $(compgen -f -- "$cur") ); return ;;
{values}
  esac
  for ((i=1; i<COMP_CWORD; i++)); do
    w="${{COMP_WORDS[i]}}"
    case "$w" in
      -e|--env|-o|--output) ((i++)) ;;
      -*) ;;
      *) pos+=("$w") ;;
    esac
  done
  domain="${{pos[0]:-}}"
  if [[ "$domain" == "flow" && ("${{pos[1]:-}}" == "backend" || "${{pos[1]:-}}" == "jvm") \\
        && ${{#pos[@]}} -eq 2 && "$cur" != -* ]]; then
    COMPREPLY=( $(compgen -W "$MODULES" -- "$cur") )
    return
  fi
  if [[ "$domain" == "flow" && ${{#pos[@]}} -ge 3 ]]; then
    COMPREPLY=( $(compgen -W "$FLOW_FLAGS $GLOBAL_FLAGS" -- "$cur") )
    return
  fi
  if [[ -z "$domain" ]]; then
    COMPREPLY=( $(compgen -W "{top} $GLOBAL_FLAGS" -- "$cur") )
    return
  fi
  case "$domain" in
{cases}
    *) COMPREPLY=( $(compgen -W "$GLOBAL_FLAGS" -- "$cur") ) ;;
  esac
}}
complete -F _itp_ops_complete itp-ops.sh itp-ops
"""


def _zsh_module_flags() -> str:
    """--module/--app/--image 后面的值 = 模块目录词表；--jar/--dockerfile 走文件路径。

    zsh 无法在 `case` 块内直接写 `(flag) _values ...` 这样的子句 —— 需要
    提前算出当前待补全的 token 再用普通 `if` 路由，否则语法检查会报 parse error。
    """
    modules = " ".join(module_names())
    return (
        "      local cur_flag=$words[$words[(r)--*]]\n"
        "      if [[ $cur_flag == \"--module\" || $cur_flag == \"--app\" || "
        "$cur_flag == \"--image\" ]]; then\n"
        f"        _values '模块目录' {modules}\n"
        "      elif [[ $cur_flag == \"--jar\" || $cur_flag == \"--dockerfile\" ]]; then\n"
        "        _files\n"
        "      fi\n"
    )


def zsh_script() -> str:
    m = _domain_map()
    modules = " ".join(module_names())
    flag_routed = _zsh_module_flags()
    lines: list[str] = []
    for name, subs in m.items():
        if name == "flow":
            # backend / jvm 的第一个位置参数补模块目录；其余仍补 flow 子命令
            lines.append(
                "      flow)\n"
                "        case $words[2] in\n"
                "          backend|jvm) _values '模块目录' " + modules + " ;;\n"
                "          *) _values 'flow 命令' " + " ".join(subs) + " ;;\n"
                "        esac\n"
                + flag_routed + ";;"
            )
        else:
            lines.append("      {n}) _values '{n} 命令' {w} ;;".format(n=name, w=" ".join(subs)))
    cases = "\n".join(lines)
    top = " ".join(_top_level())
    return f"""#compdef itp-ops.sh itp-ops
# zsh completion —— 由 `itp-ops.sh completion zsh` 生成
# 启用：./itp-ops.sh completion zsh > "${{fpath[1]}}/_itp-ops" && compinit
# 词表来自 CATALOG，新增域 / 子命令后重新生成即可。
_itp_ops() {{
  local -a domains
  domains=({top})
  _arguments -C \\
    '-e[环境]:env:(hongdao test)' '--env[环境]:env:(hongdao test)' \\
    '-y[确认写操作]' '--yes[确认写操作]' \\
    '-n[强制预演]' '--dry-run[强制预演]' \\
    '-o[输出格式]:format:(text json)' '--output[输出格式]:format:(text json)' \\
    '--json[等价 -o json]' \\
    '-q[抑制 INFO]' '--quiet[抑制 INFO]' \\
    '-v[DEBUG 输出]' '--verbose[DEBUG 输出]' \\
    '--no-color[关闭颜色]' '--trace[打印堆栈]' \\
    '-h[帮助]' '--help[帮助]' '-V[版本]' '--version[版本]' \\
    '1:域:($domains)' '*::参数:->args'
  case $state in
    args)
      case $words[1] in
{cases}
      esac
      ;;
  esac
}}
_itp_ops "$@"
"""
