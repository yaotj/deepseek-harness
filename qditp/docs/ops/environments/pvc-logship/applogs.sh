#!/bin/sh
# applogs —— 红岛 itpb 应用日志快捷入口（共享盘 NFS，只读）
# 数据链路: PVC itpb/app-logs-v2 -> 静态 PV -> 172.120.0.1:/ITP-PV/home/java/itpb-app-logs
NEW=/mnt/itp-pv/home/java/itpb-app-logs
if [ -d "$NEW" ]; then
  BASE=$NEW
else
  # 兼容旧路径（PVC app-logs -> /ITP-PV/itpb-app-logs-pvc-<uid>）
  BASE=$(ls -d /mnt/itp-pv/itpb-app-logs-* 2>/dev/null | head -1)
fi

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
  applogs bak <关键字>            列出按天归档（bak/ 下 *.log.gz），验证滚动是否生效
  applogs day <关键字> [日期]     某一天的全天日志（默认昨天；管道友好，接 less/grep）
  applogs who <关键字> [日期]     某天是哪些 Pod 写的：覆盖时段 + 是否还在运行
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

# 昨天日期：GNU date 优先，退化到 BSD（跳板机是 GNU，本机调试时是 BSD）
_yd() {
  date -d yesterday +%F 2>/dev/null || date -v-1d +%F 2>/dev/null
}

# $1=Pod 名 -> 该 Pod 所属 ReplicaSet 的镜像 tag（Pod 名去掉末段随机后缀 = RS 名）
# kubectl 不可用/RS 已被 GC 时返回空，由调用方显示 ?
_imgtag() {
  _rs=$(printf "%s" "$1" | sed 's/-[a-z0-9]*$//')
  _im=$(kubectl -n itpb get rs "$_rs" -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null)
  [ -z "$_im" ] && return
  printf "%s" "${_im##*:}"
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
  bak)
    [ -z "$1" ] && { echo "需要关键字" >&2; exit 2; }
    for d in $(dirs "$1"); do
      echo "== $(basename "$d")"
      if [ -d "$d/bak" ] && [ -n "$(ls -A "$d/bak" 2>/dev/null)" ]; then
        ls -lh "$d/bak"
      else
        echo "(尚无归档。活跃文件是固定名 <app>.log 不带日期，跨天 00:00 后才生成 <日期>-<app>-N.log.gz)"
      fi
      echo
    done
    ;;
  day)
    # 某一天的全天日志：归档名 <日期>-<app>-<N>.log.gz 的日期即内容所属那天（00:00~23:59）
    [ -z "$1" ] && { echo "用法: applogs day <关键字> [YYYY-MM-DD]" >&2; exit 2; }
    kw=$1
    dt=$2
    if [ -z "$dt" ]; then
      dt=$(_yd)
      [ -z "$dt" ] && { echo "取不到昨天日期，请显式传日期" >&2; exit 2; }
    fi
    case "$dt" in
      [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
      *) echo "日期格式须为 YYYY-MM-DD，收到: $dt" >&2; exit 2 ;;
    esac
    _found=0
    for d in $(dirs "$kw"); do
      # -v 版本序，保证 -2 排在 -10 之前
      _fs=$(ls -v "$d"/bak/"$dt"-*.log.gz 2>/dev/null)
      [ -z "$_fs" ] && continue
      _found=1
      echo "== $(basename "$d")  [$dt]" >&2
      echo "   分片: $(echo "$_fs" | wc -l | tr -d ' ') 个，压缩后 $(du -ch $_fs 2>/dev/null | tail -1 | cut -f1)" >&2
      if [ -t 1 ]; then
        # 交互终端只给尾部，避免一次刷出上百万行；管道/重定向时输出全量
        zcat $_fs 2>/dev/null | tail -n 200
        echo "   （终端模式仅显示最后 200 行；看全量: applogs day $kw $dt | less   或 | grep 模式）" >&2
      else
        zcat $_fs 2>/dev/null
      fi
      echo >&2
    done
    if [ "$_found" -eq 0 ]; then
      echo "没有 $dt 的归档。可能原因：" >&2
      echo "  - 该应用那天没有日志（应用未启动，或只在更早/更晚的 Pod 里）" >&2
      echo "  - 归档已过 logBakDays（30 天）被自动清理" >&2
      echo "  - 换过的 Pod 目录可能带旧命名，用 applogs bak $kw 逐个看" >&2
      exit 1
    fi
    ;;
  who)
    # 某天是哪些 Pod 目录写的：覆盖时段 + 是否还在跑。回答"某天运行的是哪个容器"
    [ -z "$1" ] && { echo "用法: applogs who <关键字> [YYYY-MM-DD]" >&2; exit 2; }
    kw=$1
    dt=${2:-$(_yd)}
    case "$dt" in
      [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
      *) echo "日期格式须为 YYYY-MM-DD，收到: $dt" >&2; exit 2 ;;
    esac
    _live=$(mktemp)
    kubectl -n itpb get pods -o name 2>/dev/null | sed "s:pod/::" | sort > "$_live"
    echo "关键字: $kw    日期: $dt" >&2
    printf "%-44s %-7s %6s  %-19s %6s %-8s\n" "Pod 目录" "状态" "分片" "覆盖时段" "压缩" "镜像tag" >&2
    _n=0
    for d in $(dirs "$kw"); do
      _fs=$(ls -v "$d"/bak/"$dt"-*.log.gz 2>/dev/null)
      [ -z "$_fs" ] && continue
      _n=$((_n+1))
      _f1=$(echo "$_fs" | head -1)
      _fn=$(echo "$_fs" | tail -1)
      # 异常堆栈续行没有时间戳前缀，故多取几行再挑带时间戳的
      _t1=$(zcat "$_f1" 2>/dev/null | head -50 \
            | grep -oE "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}" | head -1 | cut -c12-19)
      _t2=$(zcat "$_fn" 2>/dev/null | tail -300 \
            | grep -oE "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}" | tail -1 | cut -c12-19)
      if [ ! -s "$_live" ]; then
        _st="?"
      elif grep -qx "$(basename "$d")" "$_live"; then
        _st="在跑"
      else
        _st="已下线"
      fi
      printf "%-44s %-7s %6s  %-19s %6s %-8s\n" "$(basename "$d")" "$_st" \
        "$(echo "$_fs" | wc -l | tr -d ' ')" "${_t1:-?} ~ ${_t2:-?}" \
        "$(du -ch $_fs 2>/dev/null | tail -1 | cut -f1)" "$(_imgtag "$(basename "$d")" || echo '?')"
    done
    rm -f "$_live"
    if [ "$_n" -eq 0 ]; then
      echo "没有 $dt 的日志。用 applogs bak $kw 看各目录都有哪些日期。" >&2
      exit 1
    fi
    echo
    echo "共 $_n 个 Pod 在该日写过日志。覆盖时段为「首个分片首行 ~ 末个分片末行」，"
    echo "中途重启会有多行接力；单个 Pod 若不在 00:00:00 起，说明它是当天中途才起的。"
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
