#!/bin/bash
# 红岛可达性自检：插好 AX88179B 后跑一次，自动判断「插到哪个区、哪些区通了」
# 用法：bash red-island-reachability.sh
set -u
IF=en9
echo "=== 1. 网卡 $IF 当前 IP（决定你插到哪个红岛区）==="
ip=$(ifconfig "$IF" 2>/dev/null | awk '/inet /{print $2}' | head -1)
if [ -z "$ip" ]; then
  echo "  ⚠️  $IF 没拿到 IP——USB 没插好或没插业务口。插到红岛 K8s 控制区 172.120.2.x 口后重试。"
  exit 1
fi
echo "  $IF = $ip"
case "$ip" in
  172.120.2.*)  echo "  → K8s 控制区（可直达 hd-linux:22 / VL:31920）" ;;
  172.120.110.*) echo "  → 业务区（可直达 前端:31919）" ;;
  172.120.249.*) echo "  ⚠️  出口区（隔离！到 K8s/业务无三层路由，只能 ping 本网段网关）" ;;
  *) echo "  → 未知区（$ip），逐区探测看哪些通" ;;
esac

echo ""
echo "=== 2. 逐区可达性（ping + TCP 端口）==="
# 每个区：| 标签 | 代表 host | 要探的端口
for entry in \
  "K8s控制|172.120.2.13|22|31920" \
  "K8s工作|172.120.1.11|22" \
  "云管|172.120.3.10|22" \
  "业务|172.120.110.11|31919|31920" \
  "私有|172.120.125.1|22" \
  "安全|172.120.209.101|22" \
  "工作站|172.120.211.1|22" \
  "核心|172.120.216.1|22" \
  "出口|172.120.249.254|" \
  "管理|172.120.250.1|22" \
  "环回|172.120.255.1|22" \
; do
  IFS='|' read -r label host ports <<< "$entry"
  # ping 判定
  if ping -c1 -t1 "$host" >/dev/null 2>&1; then icmp=OK; else icmp=FAIL; fi
  # 逐端口
  pout=""
  for p in $ports; do
    if [ -z "$p" ]; then continue; fi
    if nc -z -G 3 "$host" "$p" >/dev/null 2>&1; then pout="$pout $p:OK"; else pout="$pout $p:FAIL"; fi
  done
  printf "  %-8s %-18s ICMP=%-4s%s\n" "$label" "$host" "$icmp" "$pout"
done

echo ""
echo "=== 3. 关键服务探活（前端 / VL / hd-linux SSH）==="
for target in "hd-linux SSH|172.120.2.13|22" "VL|172.120.2.13|31920" "前端 web|172.120.110.11|31919"; do
  IFS='|' read -r label host port <<< "$target"
  if nc -z -G 5 "$host" "$port" >/dev/null 2>&1; then
    echo "  ✅ $label $host:$port 可达"
  else
    echo "  ❌ $label $host:$port 不可达"
  fi
done

echo ""
echo "=== 4. 结论 ==="
echo "  若 3 全 ✅：已全通，可 ssh hd-linux 拉集群 + 回写 MEMORY"
echo "  若 hd-linux/VL ✅ 但 前端 ❌：说明前端在 172.120.110.x 业务区，你的 en9 在 .2 控制区，跨区不通——需红岛侧放行 .2↔.110 三层路由"
echo "  若 3 全 ❌：你插在出口区 .249（隔离区），换插 K8s 控制区 .2 或 业务区 .110 的口"
