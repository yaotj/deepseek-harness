# ITP 公网入口 48000 路由配置

> 当前入口：`58.56.166.170:48000`
> 来源：k8s-master 当前运行配置（2026-10-14 导出并清洗）
> 范围：ns `itp-gateway` 的 ingressgateway + ns `itp` 的 Gateway / VirtualService

## 一、流量路径

```
公网 58.56.166.170:48000
  → 网关节点 172.20.211.200
    → ns itp-gateway / Service itp-gateway (ClusterIP 50908→8080)
      → Deployment itp-gateway (istio-proxy, image: auto)
        → ns itp / Gateway accountserver-gw (port 8080 HTTP)
          → VirtualService fep-app-vr
            → 按 URI 前缀路由到各业务 Service
```

`itp-gateway-tgrce-svc` 是第二个 Service（8817→8081），当前未在 VirtualService 中使用。

## 二、当前 8 条前缀路由

| # | 前缀 | rewrite | 目标 Service | 目标端口 | 业务含义 |
|---|------|---------|-------------|---------|---------|
| 1 | `/fep-app/` | `/` | `fep-app-hr32k-svc` | 9101 | APP 入口 |
| 2 | `/fep-dev/` | `/` | `fep-dev-server-748lq-svc` | 30009 | 闸机 AGM 入口 |
| 3 | `/fep-acc/` | `/` | `fep-acc-wracu-svc` | 30030 | ACC 员工卡通知入口 |
| 4 | `/fep-alipay/` | `/` | `fep-alipay-rec8g-svc` | 30020 | 支付宝渠道入口 |
| 5 | `/itptvm/` | `/itptvm/` | `face-pay-server-svc` | 30025 | TVM 当面付 |
| 6 | `/itpbom/` | `/itpbom/` | `face-pay-server-svc` | 30025 | BOM 当面付 |
| 7 | `/itpagm/` | `/` | `fep-dev-server-748lq-svc` | 30009 | 闸机 AGM 别名入口 |
| 8 | `/para-server/` | `/` | `para-server-pufrl-svc` | 30026 | 参数服务入口 |

⚠️ **注意**：`/itptvm/` 和 `/itpbom/` 的 rewrite **保留前缀**，其余 6 条 rewrite 为 `/`。

## 三、文件清单

| 文件 | 对象 | 命名空间 | 说明 |
|------|------|---------|------|
| `itp-gateway-deployment.yaml` | Deployment | `itp-gateway` | istio ingressgateway，image: auto |
| `itp-gateway-service.yaml` | Service | `itp-gateway` | 50908→8080, 45609→443 |
| `itp-gateway-tgrce-svc.yaml` | Service | `itp-gateway` | 8817→8081（备用） |
| `accountserver-gw.yaml` | Gateway | `itp` | selector: istio=ingressgateway，8080 HTTP |
| `fep-app-vr.yaml` | VirtualService | `itp` | 8 条前缀路由 |

## 四、迁移到生产时的关键注意

### 4.1 如果继续用 Istio
- 必须替换 `fep-app-vr.yaml` 中所有 Service 名里的随机后缀（`-hr32k-`、`-748lq-` 等）为稳定名
- Gateway 的 `hosts: ["*"]` 在生产建议收紧为具体域名
- `itp-gateway` 资源：limits/requests 2C2Gi

### 4.2 如果不部署 Istio
当前入向路由**完全依赖** Istio Gateway + VirtualService，必须替换为：
- **Nginx Ingress / APISIX / Kong**：重写上述 8 条前缀路由规则
- **或 H3Cloud 平台 SLB + 外部负载均衡**：直接暴露各业务 Service 的 NodePort，由外部设备做前缀路由
- **关键点**：`/itptvm/` 和 `/itpbom/` 必须保留前缀 rewrite，其余 rewrite 为 `/`

### 4.3 生产变更联动
公网入口地址变更（`58.56.166.170:48000`）后，必须同步修改以下对端配置：
- 支付中心（bestonepay）回调地址
- 支付宝开放平台回调地址
- APP 后端回调地址
- 闸机 / TVM / BOM 设备对接地址

## 五、与仓库基线对比

仓库 `deploy/k8s/30-istio-ingress.yaml` + `31-itp-gateway-ns.yaml` 已包含相同的路由规则，但本目录是从当前集群导出的实际运行版本，用于核对和作为迁移基线。

主要一致点：
- Service 端口映射完全相同

⚠️ **2026-10-26 审计修正（本目录快照已过期，NEVER 当现状引用）**：
- 本目录与 `ingress-config/` 的 `fep-app-vr.yaml` 只有 **8 条 prefix**，但同集群实测真值
  `docs/ops/environments/test-istio-ingress.yaml` 与仓库基线 `deploy/k8s/30-istio-ingress.yaml`
  都是 **7 条 SCG exact + 8 条 prefix = 15 条**——两份快照的 uid/resourceVersion 与真值**完全相同**，
  却少了那 7 条，属同一对象的**过期副本**（下文原写的「8 条前缀路由完全相同」因此是错的）。
- **影响面**：`itp-ops.sh diag routes` 用本目录做 diff 基线（见 `domains/diag.sh`），
  基线过期会把已上线的 7 条 SCG 路由误报成「漂移」。
- **MUST 重抓**：`kubectl get gateway/virtualservice -n itp -o yaml` +
  `kubectl get deploy,svc -n itp-gateway -o yaml` 覆盖 `ingress-config/`，再跑
  `clean-ingress-yaml.py` 重新生成本目录。**本机无集群网络，本次审计未能重抓**，只做标注。

生产迁移前 MUST 重新现查一次 `kubectl get gateway/virtualservice -n itp -o yaml`，确认路由无漂移。
