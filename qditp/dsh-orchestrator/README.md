# Orchestrator Plugin - Phase 1 Skeleton

## 当前状态

⚠️ **阻塞**：`cordis_define` 的 `plugin` 参数验证 bug 仍未修复，无法直接注册 Plugin。

✅ **已完成**：
- 项目级 `roles.yaml` 已创建：`.orchestrator/roles.yaml`
- Plugin 骨架代码已准备：`src/index.js`
- 配置解析、Provider 注册、工具注册、事件监听、Host API 均已实现

## 文件结构

```
dsh-orchestrator/
├── README.md                 # 本文件
├── src/
│   └── index.js              # Phase 1 骨架代码（约 300 行）
└── .orchestrator/
    └── roles.yaml            # 项目级角色配置（已创建在项目根目录）
```

## 启用方式

### 方式 1：等 `cordis_define` 修复后（推荐）

```javascript
const fs = require('fs');

cordis_define({
  plugin: { kind: "new", idPrefix: "orch" },
  name: "phase1-skeleton",
  purpose: "Phase 1 Orchestrator skeleton",
  code: {
    host: fs.readFileSync('./dsh-orchestrator/src/index.js', 'utf8')
  }
}).then(result => {
  console.log('Plugin registered:', result.pluginId, result.packageId);
  return cordis_run({
    pluginId: result.pluginId,
    packageId: result.packageId,
    mode: 'run'
  });
});
```

### 方式 2：通过 DSH preset/composition 注册

如果 `cordis_define` 长期不可用，可以考虑：
1. 将 `src/index.js` 代码复制到 DSH preset 目录
2. 通过 `editing-cordis-compositions` skill 编辑 composition
3. 在 `cordis.yml` 中添加 plugin 行

## Phase 1 功能清单

### ✅ 已完成

- [x] `roles.yaml` 解析器（支持 Git 仓库自动发现）
- [x] 动态 Subagent Provider 注册（从配置读取）
- [x] `/orchestrate:task` 工具（start/status/cancel/retry/takeOver）
- [x] 事件监听（subagent/start, subagent/end, agent/error）
- [x] Host API（`orchestrator.getSubagentTree`, `orchestrator.takeOver`）
- [x] 状态机骨架（TaskState 类）
- [x] 线性流水线（serial 模式）

### ⏳ Phase 2 待实现

- [ ] 并行执行引擎（Promise.all + 依赖图）
- [ ] 条件分支模式
- [ ] 自动重试机制（指数退避）
- [ ] 人工接管流程（pause_for_human → human_taken_over）
- [ ] DSH Session 持久化（替代内存 Map）
- [ ] Client Slot UI（侧边栏子 Agent 面板）

## 测试

### 验证代码语法

```bash
node -c dsh-orchestrator/src/index.js
```

### 模拟运行

```bash
node -e "
const fs = require('fs');
const code = fs.readFileSync('./dsh-orchestrator/src/index.js', 'utf8');
console.log('Code length:', code.length, 'chars');
console.log('Lines:', code.split('\n').length);
"
```

## 已知限制

1. **YAML 解析**：当前使用 `JSON.parse` 模拟，生产环境需要 `js-yaml`
2. **状态持久化**：当前使用内存 `Map`，重启丢失，需要接入 `ctx.sessionPersistence`
3. **并行模式**：Phase 1 仅支持串行，并行在 Phase 2 实现
4. **工具权限隔离**：配置已支持，硬隔离 `ctx.tools.restrict()` 待验证
5. **Worktree 管理**：Phase 1 未实现，Phase 4 集成

## 下一步

1. 等待 `cordis_define` bug 修复
2. 注册 Plugin 并运行测试
3. 实现 Phase 2 并行与重试
4. 开发 Client Slot UI
