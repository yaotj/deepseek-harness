#!/bin/sh
# applogs —— 红岛 itpb 应用日志快捷入口（共享盘 NFS，只读）
# 数据链路: PVC itpb/app-logs -> StorageClass itpb-yw -> 172.120.0.1:/ITP-PV
BASE=$(ls -d /mnt/itp-pv/itpb-app-logs-* 2>/dev/null | head -1)

if [ -z "$BASE" ] || [ ! -d "$BASE" ]; then
  echo "日志盘未挂载。执行:  mount /mnt/itp-pv" >&2
  exit 1
fi

usage() {
  cat <<EOF
用法（日志根: $BASE）
  applogs                          一览：Pod 目录 / 类型 / 大小 / 最近更新
  applogs list <关键字>            列出匹配目录里的文件
  applogs path <关键字>            打印匹配目录（可直接 cd 进去）
  applogs tail <关键字> [行数]     查看最新日志尾部（默认 200）
  applogs grep <关键字> <模式>     grep -E 检索
  applogs stale                    列出已无对应 Pod 的陈旧目录（只列，不删）
  关键字 = Pod 目录名子串，例: account / para-server / acc-security
  类型: APP=有业务日志(*.log)  GC=只有 JVM gctrace（应用未完成启动）
EOF
}

# $1=目录 -> 打印该目录下"最新"的日志文件（优先业务日志，退回 gctrace）
latest() {
  _f=$(ls -t "$1"/*.log 2>/dev/null | grep -v "/check-" | head -1)
  [ -z "$_f" ] && _f=$(ls -t "$1"/gctrace.txt 2>/dev/null | head -1)
  printf "%s" "$_f"
}

# $1=目录 -> APP / GC
kind() {
  if ls "$1"/*.log 2>/dev/null | grep -qv "/check-"; then echo APP; else echo GC; fi
}

# $1=关键字 -> 匹配的 Pod 目录（新的在前）
dirs() {
  ls -dt "$BASE"/*"$1"*/ 2>/dev/null \
    | grep -vE "/(tomcat-docbase|work)" \
    | sed "s:/$::"
}

all_dirs() {
  ls -dt "$BASE"/*/ 2>/dev/null \
    | grep -vE "/(tomcat-docbase|work)" \
    | sed "s:/$::"
}

summary() {
  usage
  echo
  printf "%-46s %-4s %8s  %s\n" "Pod 目录" "类型" "大小" "最近更新"
  for d in $(all_dirs); do
    f=$(latest "$d")
    [ -z "$f" ] && continue
    printf "%-46s %-4s %8s  %s\n" \
      "$(basename "$d")" "$(kind "$d")" \
      "$(du -h "$f" 2>/dev/null | cut -f1)" \
      "$(date -r "$f" "+%m-%d %H:%M" 2>/dev/null)"
  done
}

[ $# -eq 0 ] && { summary; exit 0; }

cmd=$1
shift

case "$cmd" in
  -h|--help|help)
    usage
    ;;
  list)
    [ -z "$1" ] && { echo "需要关键字" >&2; exit 2; }
    for d in $(dirs "$1"); do
      echo "== $d"
      ls -lh "$d"
    done
    ;;
  path)
    [ -z "$1" ] && { echo "需要关键字" >&2; exit 2; }
    dirs "$1"
    ;;
  tail)
    [ -z "$1" ] && { echo "需要关键字" >&2; exit 2; }
    n=${2:-200}
    for d in $(dirs "$1"); do
      f=$(latest "$d")
      [ -z "$f" ] && continue
      echo "== $f"
      tail -n "$n" "$f"
      echo
    done
    ;;
  grep)
    [ -z "$1" ] && { echo "需要关键字与模式" >&2; exit 2; }
    kw=$1
    shift
    [ -z "$1" ] && { echo "需要模式" >&2; exit 2; }
    pat=$*
    for d in $(dirs "$kw"); do
      f=$(latest "$d")
      [ -z "$f" ] && continue
      echo "== $f"
      _out=$(grep -E "$pat" "$f" 2>/dev/null | tail -n 50)
      if [ -z "$_out" ]; then echo "(无匹配)"; else printf "%s\n" "$_out"; fi
      echo
    done
    ;;
  stale)
    # 只列不删：删除生产日志属人工决定，脚本不代劳
    _live=$(mktemp)
    kubectl -n itpb get pods -o name 2>/dev/null | sed "s:pod/::" | sort > "$_live"
    if [ ! -s "$_live" ]; then
      echo "取不到 Pod 列表（kubectl 不可用），跳过" >&2
      rm -f "$_live"
      exit 1
    fi
    for d in $(all_dirs); do
      n=$(basename "$d")
      grep -qx "$n" "$_live" || printf "%-46s %8s\n" "$n" "$(du -sh "$d" 2>/dev/null | cut -f1)"
    done
    rm -f "$_live"
    echo
    echo "以上目录对应的 Pod 已不存在（滚更遗留）。删除需人工确认，本命令不删。"
    ;;
  *)
    usage
    exit 2
    ;;
esac
