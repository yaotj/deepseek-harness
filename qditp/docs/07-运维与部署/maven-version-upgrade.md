# Maven 模块版本号升级操作手册

## 升级规则

- **根 pom**：保持原版本号不变，不参与升级
- **子模块 project 版本号**：末位数字 +1
  - 例：`1.0` → `1.1`，`1.9` → `1.10`，`2.0` → `2.1`
- **公共模块属性版本号**：若 `model`、`rpc` 等公共模块升级，所有引用它的子模块中对应的固定版本号属性也要同步升级

## 升级步骤

### 1. 确认当前版本号

遍历项目下所有 `pom.xml`，提取每个模块的 `<version>` 标签值，记录当前版本号。

```bash
# 可用的项目内 pom 文件列表
find . -name pom.xml
```

重点确认以下公共模块当前版本：
- `model/pom.xml`
- `rpc/pom.xml`

### 2. 升级子模块 project 版本号

对所有子模块 `pom.xml` 的 `<version>` 执行末位 +1：

| 原版本 | 升级后 |
|--------|--------|
| 1.0 | 1.1 |
| 1.1 | 1.2 |
| 1.9 | 1.10 |
| 2.0 | 2.1 |

根 `pom.xml` 的 `<version>` **不做修改**。

### 3. 同步升级公共模块属性版本号

若 `model` 或 `rpc` 的 project 版本号已升级，需同步修改所有子模块 `pom.xml` 中引用它们的固定版本号属性：

- `<model.version>`：从旧值改为新值
- `<rpc.version>`：从旧值改为新值

修改范围为所有子模块的 `<properties>` 节点内。

### 4. 校验

确认以下内容已正确更新：
- 根 `pom.xml` 版本号未变
- 所有子模块 project 版本号末位 +1
- 所有子模块中 `model.version`、`rpc.version` 属性值与公共模块实际版本一致

## 项目模块清单

当前项目包含以下 19 个模块（根 pom 除外）：

- model
- rpc
- account-server
- wallet-server
- acc-secure-server
- acc-security-server
- acc-es-server
- collect-ticket-server
- collect-pay-server
- pay-sign-server
- online-server
- ticket-server
- industry-data-server
- fep-app-server
- fep-dev-server
- gate-txn-pay-server
- blacklist-server
- key-server

## 注意事项

- 仅修改本项目内的 `pom.xml`，不涉及外部依赖
- `web.version` 等其他属性仅在对应公共模块本身升级时才需要同步修改
- 修改前建议先备份或确认当前版本号，避免重复升级
