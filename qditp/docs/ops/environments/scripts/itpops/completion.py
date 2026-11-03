"""shell 补全生成 —— 从 CATALOG 派生，域/子命令增删后自动同步。

用法（与 kubectl completion 同形）：
  itp-ops.sh completion bash      → 标准输出补全脚本
  itp-ops.sh completion zsh
安装示例：
  bash: itp-ops.sh completion bash > /usr/local/etc/bash_completion.d/itp-ops
  zsh : itp-ops.sh completion zsh  > "${fpath[1]}/_itp-ops"
"""
from __future__ import annotations

from .catalog import _load_domains

_GLOBAL_FLAGS = (
    "-e --env -y --yes -n --dry-run -o --output -q --quiet "
    "-v --verbose --no-color --trace -h --help -V --version"
)

_BUILTINS = ("catalog", "completion", "help")


def _domain_map() -> dict[str, list[str]]:
    """域/顶层命令 → 子命令词表（多词命令如 `image build` 拆成单词，便于补全）。"""
    m: dict[str, list[str]] = {}
    for d in _load_domains():
        words: list[str] = []
        for s in d.get("subcommands", []):
            for w in (s.get("name", "") or "").split():
                if w and w not in words:
                    words.append(w)
        m[d.get("name", "")] = words + ["help"]
    for b in _BUILTINS:
        m.setdefault(b, [])
    return m


def _top_level() -> list[str]:
    names = [d.get("name", "") for d in _load_domains() if d.get("name")]
    for b in _BUILTINS:
        if b not in names:
            names.append(b)
    return names


def bash_script() -> str:
    m = _domain_map()
    cases = "\n".join(
        '    {n}) COMPREPLY=( $(compgen -W "{w} $GLOBAL_FLAGS" -- "$cur") ) ;;'.format(
            n=name, w=" ".join(subs)
        )
        for name, subs in m.items()
    )
    top = " ".join(_top_level())
    return f"""# bash completion for itp-ops.sh —— 由 `itp-ops.sh completion bash` 生成
_itp_ops_complete() {{
  local cur domain="" i w
  cur="${{COMP_WORDS[COMP_CWORD]}}"
  local GLOBAL_FLAGS="{_GLOBAL_FLAGS}"
  for ((i=1; i<COMP_CWORD; i++)); do
    w="${{COMP_WORDS[i]}}"
    case "$w" in
      -e|--env|-o|--output) ((i++)) ;;
      -*) ;;
      *) domain="$w"; break ;;
    esac
  done
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


def zsh_script() -> str:
    m = _domain_map()
    cases = "\n".join(
        "      {n}) _values '{n} 命令' {w} ;;".format(n=name, w=" ".join(subs))
        for name, subs in m.items()
    )
    top = " ".join(_top_level())
    return f"""#compdef itp-ops.sh itp-ops
# zsh completion —— 由 `itp-ops.sh completion zsh` 生成
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
