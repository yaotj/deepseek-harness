#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 ITP 测试→生产环境迁移配置清单报告
来源：k8s-master 当前运行配置 + 仓库 deploy/k8s/ 基线
"""

import os
import re
from pathlib import Path
from datetime import datetime

BASE_DIR = Path(__file__).parent
K8S_DIR = BASE_DIR / "k8s-current-config"
DEPLOY_K8S_DIR = Path("/Users/tuanjie/workspace/company/chinasofti/qd/qditp/deploy/k8s")
OUTPUT = BASE_DIR / "prod-migration-config-checklist.md"


def parse_deployments():
    """解析当前集群 Deployment 与镜像"""
    result = []
    text = (K8S_DIR / "deployments.md").read_text()
    for line in text.strip().split("\n")[2:]:  # 跳过标题
        parts = line.split(None, 1)
        if len(parts) == 2:
            name, image = parts[0], parts[1].strip()
            result.append((name, image))
    return result


def get_baseline_images():
    """从仓库 deploy/k8s/ 解析基线镜像"""
    baseline = {}
    for f in DEPLOY_K8S_DIR.rglob("*.yaml"):
        content = f.read_text()
        for match in re.finditer(r"image:\s*(.+)", content):
            image = match.group(1).strip()
            if image == "auto" or not image.startswith("os-harbor-svc"):
                continue
            # 从文件名或内容推断 deployment 名
            dep_name = f.stem
            baseline[dep_name] = image
    return baseline


def parse_services():
    """解析 Service 与 NodePort"""
    result = []
    text = (K8S_DIR / "services.md").read_text()
    for line in text.strip().split("\n")[2:]:
        parts = line.split(None, 5)
        if len(parts) >= 5:
            name, svc_type, cluster_ip, port, nodeport = parts[:5]
            target_port = parts[5] if len(parts) > 5 else ""
            result.append((name, svc_type, cluster_ip, port, nodeport, target_port))
    return result


def parse_env_keys():
    """解析各 Deployment 的环境变量键名"""
    result = {}
    text = (K8S_DIR / "env-keys.txt").read_text()
    current = None
    for line in text.strip().split("\n"):
        line = line.strip()
        if line.startswith("DEPLOY: "):
            current = line.replace("DEPLOY: ", "")
            result[current] = []
        elif line and current and not line.startswith("#") and line != "---":
            result[current].append(line)
    return result


def classify_service(name):
    """区分业务负载与非业务负载"""
    non_business = {
        "details-v1", "productpage-v1", "ratings-v1",
        "reviews-v1", "reviews-v2", "reviews-v3", "kennethreitz-httpbin"
    }
    if name in non_business:
        return "non-business"
    return "business"


def generate_report():
    deployments = parse_deployments()
    baseline = get_baseline_images()
    services = parse_services()
    env_keys = parse_env_keys()

    business = [(n, i) for n, i in deployments if classify_service(n) == "business"]
    non_business = [(n, i) for n, i in deployments if classify_service(n) == "non-business"]

    lines = []
    lines.append("# ITP 测试→生产环境迁移配置清单")
    lines.append(f"\n> 生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append("> 数据来源：k8s-master 当前运行配置 + 仓库 `deploy/k8s/` 基线\n")
    lines.append("> ⚠️ 本报告**不记录任何密钥明文**，只列出需要迁移、修改或确认的配置项。\n")

    # 1. 执行摘要
    lines.append("## 一、执行摘要\n")
    lines.append(f"- 当前 ns `itp` 运行 Deployment 共 **{len(deployments)}** 个")
    lines.append(f"- 其中 ITP 业务相关 **{len(business)}** 个，非业务示例/测试负载 **{len(non_business)}** 个")
    lines.append(f"- Service 共 **{len(services)}** 个")
    lines.append("- 当前集群**没有业务 Secret**，全部配置以明文 env 内联在 Deployment 中")
    lines.append("- 生产迁移时必须完成：镜像仓库地址替换、Service 名去随机后缀、密钥进 Secret、外部依赖地址变更、探针补齐\n")

    # 2. 必须迁移的业务负载
    lines.append("## 二、必须迁移的业务负载\n")
    lines.append("| # | Deployment | 当前镜像 | 仓库基线镜像 | 版本差异 |")
    lines.append("|---|-----------|---------|------------|---------|")
    for idx, (name, image) in enumerate(business, 1):
        base_image = baseline.get(name, "未在仓库基线中找到")
        diff = "一致" if image == base_image else f"当前: {image.split(':')[-1]}, 基线: {base_image.split(':')[-1]}"
        lines.append(f"| {idx} | {name} | `{image}` | `{base_image}` | {diff} |")
    lines.append("")

    # 3. 需要清理的非业务负载
    lines.append("## 三、生产环境应清理的非业务负载\n")
    lines.append("以下负载是 Istio Bookinfo 示例或 httpbin 测试镜像，与 ITP 业务无关，生产迁移时不应部署：\n")
    for name, image in non_business:
        lines.append(f"- `{name}`：`{image}`")
    lines.append("")

    # 4. Service NodePort 映射
    lines.append("## 四、Service NodePort 映射（生产需确认）\n")
    lines.append("当前 31 个 Service 全部为 NodePort（除 7 个非业务 ClusterIP）。生产迁移时：")
    lines.append("- **必须保留端口号**：大量 `service.*.url` 硬编码了这些 NodePort")
    lines.append("- **必须去掉随机后缀**：当前 Service 名带 `-n4ba6-`、`-hsa9w-` 等随机后缀，迁移后应使用稳定名如 `account-svc`")
    lines.append("- **注意 Service port ≠ targetPort ≠ NodePort**：例如 `ticket-server-bsyju-svc` Service port 是 9100，NodePort 是 30014，targetPort 才是容器真实端口 9103\n")
    lines.append("| Service | 类型 | Cluster-IP | Service Port | NodePort | TargetPort |")
    lines.append("|---------|------|-----------|-------------|---------|-----------|")
    for name, svc_type, cluster_ip, port, nodeport, target_port in services:
        if classify_service(name) == "business":
            lines.append(f"| {name} | {svc_type} | {cluster_ip} | {port} | {nodeport} | {target_port} |")
    lines.append("")

    # 5. 关键配置项分类
    lines.append("## 五、生产迁移必须修改/确认的配置项\n")

    lines.append("### 5.1 镜像仓库地址\n")
    lines.append("当前所有镜像引用：\n")
    lines.append("```")
    lines.append("os-harbor-svc.default.svc.cloudos:443")
    lines.append("```")
    lines.append("这是**当前集群内 Service DNS**，新集群不存在。生产迁移时必须全部替换为新 Harbor 地址。\n")

    lines.append("### 5.2 外部依赖地址（生产必须重新开通并换地址）\n")
    lines.append("| 依赖 | 当前测试环境地址/键 | 生产需确认 | 使用方 |")
    lines.append("|------|------------------|-----------|--------|")
    lines.append("| Oracle 业务库 | `172.20.222.3:1521/AFCITPDB` | 生产 Oracle 地址、实例、用户 | 除无 DB 模块外全部 |")
    lines.append("| 加密机 HSM | `172.20.201.4:6666/7002` | 生产 HSM 地址与端口 | acc-security-server |")
    lines.append("| ACC 清分中心 | `172.20.211.11:32605` | 生产 ACC 地址 | account-server、card-pool-server |")
    lines.append("| FTP（卡池） | `172.20.215.3` | 生产 FTP 地址 | card-pool-server |")
    lines.append("| FTP（参数文件） | `172.20.214.101:21` | 生产 FTP 地址 | para-server |")
    lines.append("| FTP（对账文件） | 见 `RECON_FTP_*` | 生产 FTP 地址 | recon-server |")
    lines.append("| 支付中心 | `dtcustomer.bestonepay.com` | 生产商户号、回调地址 | pay-sign、daily-ticket、collect-pay、alipay-pay-sign |")
    lines.append("| 支付宝 | `openapi.alipay.com` | 生产 appId、商户配置 | fep-alipay、alipay-pay-sign |")
    lines.append("| 公网回调入口 | `58.56.166.170:48000` | 新公网入口，需同步支付中心/支付宝/APP/设备侧 | daily-ticket、collect-pay、face-pay、pay-sign |")
    lines.append("")

    lines.append("### 5.3 密钥治理（必须进 K8s Secret）\n")
    lines.append("当前所有敏感信息以**明文 env** 内联在 Deployment 中。生产迁移时必须改为 `secretKeyRef`，至少包括：\n")
    sensitive_keys = [
        ("DB 口令", "`other.sql.password` / `DB_PASSWORD` / `spring.datasource.password`", "16+ 个服务"),
        ("报文签名密钥", "`itp.signKey` / `jhm.key` / `app.signKey`", "account、collect-pay、face-pay 等"),
        ("商户私钥", "`pay.center.merchant-private-key` / `pay.center.paycenter-public-key`", "alipay-pay-sign、collect-pay"),
        ("HSM/3DES 密钥", "`security.publicKey/privateKey` / `acc.3des.key` / `appserver.3des.key`", "security-server、key-server"),
        ("FTP 口令", "`CARD_POOL_FTP_PASSWORD` / `RECON_FTP_PASSWORD` / `ftp.password` / `para.ftp.password`", "card-pool、recon、es-server、para"),
        ("后台 Token", "`token.secret`", "web-admin"),
    ]
    lines.append("| 敏感项 | 键名示例 | 使用方 |")
    lines.append("|--------|---------|--------|")
    for item, keys, users in sensitive_keys:
        lines.append(f"| {item} | {keys} | {users} |")
    lines.append("")

    lines.append("### 5.4 单副本约束（生产严禁扩容）\n")
    lines.append("以下服务因进程内锁 / 内存 JobStore / 无分布式锁，**必须保持单副本**：\n")
    lines.append("- `recon-server`：进程内 `AtomicBoolean` 锁，多副本会重复生成/投递对账文件")
    lines.append("- `web-admin`：Quartz 内存 JobStore，多副本每个副本各跑一遍任务")
    lines.append("- `face-pay-server`：7 个 `@Scheduled` 任务，无分布式锁")
    lines.append("- `collect-pay`：`SingleTicketRefundTask` 4 处无分布式锁")
    lines.append("")

    lines.append("### 5.5 探针缺失（建议迁移时补齐）\n")
    lines.append("当前 32 个 Deployment **全部没有 livenessProbe / readinessProbe**。生产迁移时建议为每个服务补 `/actuator/health` 探针。\n")
    lines.append("⚠️ 注意：`web-admin` 的 `/actuator/health` 返回 200 但 body 为 401（被鉴权拦截），属正常，配置探针时需注意。\n")

    lines.append("### 5.6 `/etc/localtime` hostPath\n")
    lines.append("每个业务 Pod 都挂载了宿主 `/etc/localtime`。生产节点时区 **MUST 为 CST**，否则时间错 8 小时，影响对账账期和乘车码时效。\n")

    lines.append("### 5.7 存储\n")
    lines.append("- `victoria-logs`：StatefulSet，15Gi RWO PVC（StorageClass `apigateway2`），生产需确认是否保留历史日志")
    lines.append("- `recon-server`：当前无 PVC，对账文件落在容器本地。生产迁移时应挂载 RWX PVC 到 `RECON_STORAGE_ROOT`，否则 Pod 重启丢分片")
    lines.append("")

    # 6. 当前 web 前端配置
    lines.append("## 六、Web 前端配置\n")
    lines.append("当前 web Deployment 挂载的 ConfigMap：`websyspresetweb-version-1.29`\n")
    lines.append("生产迁移时只需要保留当前使用的这一个 ConfigMap，其余 40+ 个 `websyspresetweb-version-*` 历史版本可直接丢弃。\n")

    # 7. 需要废弃的 ConfigMap
    lines.append("## 七、需要废弃的 ConfigMap\n")
    lines.append("- `key-server-config-version-1.0`、`key-server-config1-version-1.0`、`-1.1`：当前无 Pod 挂载，疑似孤儿")
    lines.append("- `websyspresetweb-version-*`（40+ 个历史版本）：只有 `1.29` 被 web 挂载，其余是 PaaS 发布残留")
    lines.append("- `istio-ca-root-cert`、`kube-root-ca.crt`：平台自动生成，无需迁移")
    lines.append("")

    # 8. 迁移检查清单
    lines.append("## 八、生产迁移检查清单\n")
    checklist = [
        "[ ] 新集群底座：K8s + Istio（如继续用）或替代入向网关 + StorageClass + Harbor 就绪",
        "[ ] 新节点时区已置为 CST（`timedatectl` 确认）",
        "[ ] 新 Harbor 地址确认，旧镜像已迁移或可重新构建",
        "[ ] Oracle / HSM / ACC / 三个 FTP / 支付中心 / 支付宝 网络策略已开通",
        "[ ] 新公网入口地址确定，并已通知支付中心、支付宝、APP、闸机/TVM/BOM 设备侧修改回调/对接地址",
        "[ ] `deploy/k8s/` 中所有镜像地址已替换为新 Harbor",
        "[ ] Service 名已改为无随机后缀的稳定名，所有 `service.*.url` 已同步更新",
        "[ ] 全部敏感配置已从明文 env 改为 K8s Secret + `secretKeyRef`",
        "[ ] `recon-server` 已挂载 RWX PVC，`victoria-logs` 已按需要保留或清空历史日志",
        "[ ] 为每个业务 Deployment 补齐 livenessProbe / readinessProbe",
        "[ ] `recon-server`、`web-admin`、`face-pay-server`、`collect-pay` 保持单副本",
        "[ ] 非业务负载（bookinfo + httpbin）未部署到新集群",
        "[ ] 按依赖顺序部署并逐服务用 `/actuator/health` 探活",
        "[ ] 公网入口切流后，旧集群业务 Deployment 缩到 0（保留回滚位）",
    ]
    for item in checklist:
        lines.append(item)
    lines.append("")

    # 9. 关键环境变量键名汇总
    lines.append("## 九、各业务 Deployment 当前环境变量键名汇总\n")
    lines.append("以下键名在生产迁移时需要逐一确认并正确注入。完整清单见 `k8s-current-config/env-keys.txt`。\n")
    for name, keys in sorted(env_keys.items()):
        if classify_service(name) != "business":
            continue
        lines.append(f"### {name}\n")
        # 只列出与外部依赖/密钥/地址相关的键
        important = [k for k in keys if any(x in k.lower() for x in [
            "url", "host", "port", "password", "key", "secret", "token",
            "username", "ftp", "callback", "notify", "sign", "private", "public"
        ])]
        if important:
            for k in important[:20]:  # 限制数量
                lines.append(f"- `{k}`")
            if len(important) > 20:
                lines.append(f"- ... 等共 {len(important)} 个相关键")
        else:
            lines.append("- （无非默认配置键）")
        lines.append("")

    OUTPUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Report generated: {OUTPUT}")


if __name__ == "__main__":
    generate_report()
