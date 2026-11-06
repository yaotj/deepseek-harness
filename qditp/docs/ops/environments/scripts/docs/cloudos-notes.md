# CloudOS 平台知识沉淀

> 2026-11-04 从 `README.md`「三、CloudOS 知识沉淀」拆出，内容逐字保留（仅提升标题层级）。
> 改 `itpops/cloudos/`、走云管台发版链或排查登录异常前 MUST 读；命令用法见 [../README.md](../README.md)。

## 3.1 认证模型（红岛实测正确姿势）

```
① POST /api/sys/oapi/v1/double_factor/login
   Body: {"username":"itpbadmin","rsa":true,"password":"<RSA-1024 密文 base64>","domain":"default"}
② 业务 API 请求头：X-Auth-Token: <res.token>
```

- **密码 MUST RSA 加密**（PKCS#1 v1.5 + base64），密文恒 **128 字节 / 172 字符**。
  长度不对即公钥或填充不对，`cloudos auth-debug` 会直接报出来。
- **`domain` MUST 为 `default`**。传空返回 `svcmgt.internalServerError`（不是密码错）。
- **账号真值是 `itpbadmin`**，不是 `itpbdmin`（项目文档原笔误）、也不是 `itpadmin`（那是 Harbor 推送账号）。
- ⚠️ **历史错判**：2026-10-22 曾把认证写成「base64 明文 + rsa:true」，依据是「测试环境跑通过」。
  实际是浏览器抓包证明前端走 JSEncrypt，平台按 `rsa` 字段决定是否解密。base64 明文在红岛一律返回
  `usrmgt.userOrPasswordError`——**看着像密码错，其实是没解密**，极易引向反复试密码。
  **NEVER 为「能跑」改回 base64 明文**。
- **测试环境 2026-11-02 已打通**：登录页 JS 里的 RSA 公钥与红岛**逐字符相同**（216 字符、1024 bit），
  已固化为 `itpops/cloudos/auth.py` 的共享公钥常量（原 `lib/cloudos-auth.sh` 的 `CLOUDOS_RSA_PUBKEY_SHARED`），两套 profile 共用；
  `--env test` 登录（admin）、应用列表、镜像仓库、deploy 预演均实测通过。

RSA 公钥抓法（平台升级后需重抓）：

```
https://<host>/platform/static/js/login/login.<hash>.js   ← 文件里搜 encryptRSA / setPublicKey
```

`<hash>` 从 `/platform/static/js/app.<hash>.js` 的 chunk 映射表里取（本项目实测 `login/login → 9d53907c`）。
只换平台版本才需重抓；两套环境共用同一把，抓到一次即够。

### 3.1.1 两套环境 Δ 速查（2026-11-02 实测）

| 项 | 红岛生产 `--env hongdao` | 测试 `--env test` |
|---|---|---|
| 平台 VIP | 172.120.209.151 | 172.20.217.210 |
| 平台账号 | itpbadmin | admin |
| RSA 公钥 | 共享（`CLOUDOS_RSA_PUBKEY_SHARED`） | **同一把**（逐字符相同） |
| K8s 命名空间 | itpb | itp |
| Harbor 项目 / registry | itpb / `os-harbor-svc.default.svc.cloudos:443` | itp / **同一 registry** |
| 构建基础镜像 | `itpb/jdk21:1.0.0` | `itp/jdk:25`（`itp/jdk21:1.0.0` 在测试 Harbor 不存在） |
| 基础镜像入口 | `sh /startapp.sh` | 同（`itp/jdk:25` 同样带 `/startapp.sh`） |
| imgType 默认 | Default `20e68cab-…` | 同一 uuid；另有 ITP `21d45c2e-…` |
| 平台版本 | — | E7501-V500R001B03D019-RC31 |
| 应用记录 vs 实跑镜像 | 一致（滚更走 `cloudos deploy`） | **大面积不一致**（27 个里 25 个，滚更走 `kubectl set image`，见 IT-034） |

镜像构建入口两个环境同一份实现：`itp-ops.sh buildx --env {hongdao|test}`（文件名是红岛首发时的历史名，脚本已按环境参数化）。

## 3.2 平台实测怪癖（都是坑）

| 现象 | 结论 |
|---|---|
| `POST /apps/{uuid}/scale` 按官方文档发 `{"replicas":N}` → 500 `McpPlaintext` 反序列化错 | 实际报文是 **body `[]` + query `?replicas=N`**（2026-10-23 实测，三种写法只有它通）。报错完全不像参数位置问题，别转去用 kubectl——两者对平台是两套状态 |
| `logs` 返回 200 + 空 body | 红岛该接口恒空（换 pod、换容器均空）。日志真源是 VictoriaLogs `172.120.2.13:31920` 或 `kubectl logs` |
| `domain` 传空 → `svcmgt.internalServerError` | 不是服务故障，是缺 domain |
| `userOrPasswordError` | 先查账号拼写，再查是否真 RSA 加密，**最后**才怀疑密码 |
| `admin` / `itpadmin` 登录失败 | `itpadmin` 是 Harbor 推送账号，不是云管台账号 |
| 双实例判据 | 不是 `instance_num=2`，而是**两个副本落在不同节点**；同节点双副本等于没扩容。查法：`ssh hd-linux "kubectl get pods -n itpb -o custom-columns=APP:.metadata.labels.app,NODE:.spec.nodeName --no-headers \| sort"` |
| `PUT /apps/{uuid}/resources` 返回 `status=true`，但 `GET` 读回仍是旧值 | **异步生效**（2026-10-26 实测）：立即读回是旧值，数十秒后才刷新（`kubectl` 先看到新 ReplicaSet）。复验 MUST 轮询到目标值，**单次读回会把成功的变更误判成失败**、进而重复下发。`spec-set` 已内置 100s 轮询 |
| `PATCH /apps/{uuid}/advanced-config` 返回 `application.5000010`（msg 为空，文档无此码） | **缺 `container_image`**——文档未标必填，image 类应用缺它必失败，补上即通。改应用 env MUST 走这条接口（`kubectl set env`/patch 会被平台数分钟内回收），契约与实测见 `docs/tracking/IT-020-JVM参数被平台回滚.md` |
| 跨模块 RPC 全失败 | 平台按 `application.properties` 生成的 env 保留测试默认值，不显式注入 `SERVICE_*_URL` 就指向 `itp` 命名空间（IT-016）。`cloudos env <app>` 一次打全 38 项 env 核对 |
| `image plan/build` 误报「不是 spring-boot fat jar」 | **已修**（2026-10-26）。入口的 `set -uo pipefail` + `unzip -l \| grep -q` 组合必假：grep 命中即退出 → unzip 收 SIGPIPE（rc=9）→ pipefail 判整条管道失败。**该 bug 会拦住所有模块的 image plan/build**，现已改为先取全量清单再匹配 |
| Harbor 443 端口证书不合法 | podman 直连 `os-harbor-svc...:443` 报 `x509: certificate is not valid for any names`。**login/pull 用 `:11443`**；K8s 侧 kubelet 拉 `:443` 正常（itpb ns 内实测 `itpb/jdk21:1.0.0` 拉取成功） |
| `itpadmin` 无 catalog 权限 | Harbor `_catalog` 报 `unauthorized to list catalog`。查项目镜像清单改用 Harbor API `/api/v2.0/projects/<proj>/repositories`；查单仓 tag 用 `/v2/<proj>/<repo>/tags/list`（需 `--path-as-is`，否则 `%2F` 被解码） |

三个设计点（`scale` 族命令）：**幂等**（已是目标值不发请求）、**异步复验**（`status=true` 只代表已下发，
轮询 `instance_ok_num` 每 3s 一次最多 180s，未达标退出非 0——看 HTTP 200 就走人 = 假成功）、
**批量默认 dry-run**（`scale-all` 不带 `--yes` 只打印清单）。

## 3.3 API 分类（5.0 PaaS 文档 232 端点，7.0 路径一致）

| 分类 | 端点数 | 路径前缀 |
|---|---|---|
| 应用仓库 | 15 | `/api/cloud/deploy/v1.0/*` |
| 应用管理 | 63 | `/api/cloud/app-mgmt/v1.0/*` |
| 容器云（镜像服务） | - | **`/api/cloud/hcr/v1.0/*`**（文档基址 `http://{ip}:11000`，但红岛 app-mgmt 实测在 https 443——各服务挂哪个端口不统一，MUST 现探） |
| 服务治理 | 27 | `/api/cloud/svcgw/v1.0/*` |
| 持续交付 | - | **`/api/cicd/*`**（原文写 `/cicd/*`，实测缺 `/api` 一层会 404；不带 token 返回 401 才是路由存在的判据） |
| 其他 | 127 | 各类接口 |

文档真源：`docs/reference/cloudos/CloudOS 5.0_PaaS_API_E7501.md`（「企业应用API」章）。
控制台页面 `/enterprise-apps/#/apply/app/appDetail/<uuid>` 对应的就是 `app <uuid>` 那组接口。

## 3.4 已知缺口（首次实测前勿当结论）

1. `fileFlag` 语义未验证（历史 `image build` 曾全押在这里；该命令已于 2026-11-06 删除，
   出镜像改走 `itp-ops.sh buildx`）。此条仅留作契约记录，`data.build[]` 的 COPY 判据不再适用。
2. `totalChunks=1` 一次性上传；jar 超网关 body 限制（经验 ~100MB）需改分片，续传/去重行为未实测。
3. 上传会在软件中心留 jar 应用包记录（`POST /packages` 副作用）；`pkg_delete_version` 可清，默认不删。
4. 镜像服务网关基址未定位（2026-10-23 网络中断）。`cloudos image probe` 就是为此写的。
5. 构建日志走 `data.build[]`（docker 原始输出），平台无错误码表，失败只能读这里。
6. 镜像架构不可指定，构建后 MUST 核对 amd64。
7. 测试环境（`--env test`）RSA 公钥未抓到，命令停在 `env_init` 报错；容器云 `pod_*` 前缀亦未经实测。

