# Orchestrator 架构设计（基于 DSH 原生能力）

> 版本：v0.1  
> 日期：2025-08-14  
> 状态：设计草案

---

## 1. 设计目标

在 DSH 中构建一个**事件驱动、可配置、可追踪**的多 Agent 编排器，用于协调用户自定义的 Agent 角色完成复杂任务。

**核心特性**：
- **动态角色配置**：角色不由 Orchestrator 硬编码，由用户通过配置文件定义
- **可复用模板**：支持定义流程模板，复用常见工作流
- **事件驱动**：基于 DSH 原生事件实现异步协调
- **Git 作为主黑板**：所有结构化产物通过 Git 传递和审计

**核心约束**：
- Orchestrator 本身是一个 **DSH Cordis Plugin**
- 仅使用 **DSH 原生 Host Services/Events**，不引入外部消息队列
- 通过 **Git 作为主黑板（Blackboard）** 传递结构化产物
- 所有 Agent 输出必须可追踪、可回滚、可审计

---

## 2. DSH 原生能力映射

| 需求 | DSH 原生能力 | 说明 |
|------|-------------|------|
| 多 Agent 派发 | `ctx.subagents` | `registerProvider`, `start`, `followup`, `listChildren` |
| 多阶段流程 | `ctx.workflowEngine` | `start(request)` 启动阶段化工作流 |
| 对外入口 | `ctx.tools` / `ctx.commands` | 注册 `/orchestrate:task` 等工具/命令 |
| 生命周期监听 | `ctx.on('subagent/*')` | `subagent/start`, `subagent/end`, `subagent/provider-added` |
| Agent 错误处理 | `ctx.on('agent/error')` | 捕获执行异常并重试或升级 |
| 状态查询 | `ctx.sessionQuery` | `searchSessions`, `readSession`, `listEvents` |
| 异步任务 | `ctx.jobs` | `start`, `list`, `kill`, `wait` |
| 文件操作 | `ctx.fs` | 读写 Git 工作区产物 |
| Shell 执行 | `ctx.shell` | 调用 `git`, `wt` 等 CLI |
| 持久化 | `ctx.sessionPersistence` | 会话级状态持久化 |
| 审计追踪 | `ctx.sessionProjections` | 投影缓存，快速检索历史 |

---

## 3. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     DSH Host Process                         │
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌───────────────┐   │
│  │   Tools /    │    │  Commands   │    │  Web UI Slot  │   │
│  │  Commands    │    │  /orchestrate│    │  (可选 Client)│   │
│  └──────┬──────┘    └──────┬──────┘    └───────────────┘   │
│         │                  │                                  │
│         ▼                  ▼                                  │
│  ┌──────────────────────────────────────────────┐            │
│  │          Orchestrator Plugin                 │            │
│  │  ┌────────────────────────────────────────┐  │            │
│  │  │  状态机 (State Machine)                 │  │            │
│  │  │  IDLE → DISPATCHING → WAITING →        │  │            │
│  │  │  COLLECTING → ROUTING → ...            │  │            │
│  │  └────────────────────────────────────────┘  │            │
│  │  ┌────────────────────────────────────────┐  │            │
│  │  │  任务队列 (Task Queue)                  │  │            │
│  │  │  - 优先级                              │  │            │
│  │  │  - 依赖关系                            │  │            │
│  │  │  - 重试策略                            │  │            │
│  │  └────────────────────────────────────────┘  │            │
│  │  ┌────────────────────────────────────────┐  │            │
│  │  │  Router (路由决策)                      │  │            │
│  │  │  根据 taskType → role mapping           │  │            │
│  │  └────────────────────────────────────────┘  │            │
│  └──────────────────┬───────────────────────────┘            │
│                     │                                        │
│  ┌──────────────────▼───────────────────────────┐            │
│  │         Subagent Provider Registry            │            │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────┐│            │
│  │  │ provider │ │ provider │ │ provider │ │ ... ││            │
│  │  │ : pm     │ │ : arch   │ │ : coder │ │safe ││            │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──┬──┘│            │
│  │       │            │            │           │   │            │
│  │  ctx.subagents.start(role, task)              │   │            │
│  └──────────────────┬───────────────────────────┘            │
│                     │                                        │
│  ┌──────────────────▼───────────────────────────┐            │
│  │          DSH Agent Runtime                    │            │
│  │  每个角色一个独立 Agent 实例                   │            │
│  │  - 独立 session                               │            │
│  │  - 独立 context                               │            │
│  │  - 共享 Git 工作区                            │            │
│  └──────────────────────────────────────────────┘            │
│                                                             │
│  ┌──────────────────────────────────────────────┐            │
│  │              Git Repository                   │            │
│  │  (主黑板 / Blackboard)                        │            │
│  │  - docs/prd/                                  │            │
│  │  - docs/architecture/                         │            │
│  │  - interfaces/                                │            │
│  │  - src/                                       │            │
│  │  - audit/                                     │            │
│  └──────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. 核心设计

### 4.1 Orchestrator Plugin 结构

```
Orchestrator Plugin
├── Host Half
│   ├── Subagent Providers (从 roles.yaml 动态加载)
│   │   ├── provider: orchestrator   # 可配置的编排角色
│   │   ├── provider: pm
│   │   ├── provider: architect
│   │   └── provider: coder
│   ├── Tools (编排入口)
│   ├── Event Listeners (生命周期)
│   └── State Machine (状态管理)
└── Client Half (可选)
    └── Slot UI (工作空间下方子 agent 面板)
```

**关键设计**：Orchestrator 本身也是一个可配置角色。这意味着：
- 用户可以在 `roles.yaml` 中定义 `orchestrator` 角色
- Orchestrator 可以嵌套：Orchestrator A → 子 Orchestrator B → 子角色
- 支持自定义编排策略（串行/并行/条件分支）

### 4.2 动态角色配置

角色不由 Orchestrator 硬编码，而是由用户通过配置文件定义。

**核心设计**：**每个项目拥有独立的 `roles.yaml`**，Orchestrator 根据当前 Git 仓库自动加载对应配置。这意味着：

- ITP 项目可以有 PM / Architect / Coder / Safety
- 另一个项目可以有 Analyst / Designer / Developer / Reviewer
- 甚至同一个项目在不同分支也可以有不同的配置

**配置文件位置**：
```
<project-root>/
├── .orchestrator/
│   └── roles.yaml          # 项目级角色配置
├── docs/
├── src/
└── ...
```

**配置文件示例**：`.orchestrator/roles.yaml`

```yaml
version: "1.0"

# 角色定义
roles:
  # Orchestrator 本身也是一个可配置角色
  - id: orchestrator
    name: 编排器
    description: 协调其他角色完成复杂任务
    systemPrompt: |
      你是编排器，负责协调其他角色完成任务。
      你可以调用其他角色，监控进度，处理异常。
      约束：不要直接执行具体任务，只做编排决策
    tools:  # 编排器拥有特殊工具集
      - orchestrate:start
      - orchestrate:status
      - orchestrate:retry
      - orchestrate:cancel
    inputDirs: []
    outputDirs: [".orchestrator/"]
    
  - id: pm
    name: 产品经理
    description: 需求分析与 PRD 产出
    systemPrompt: |
      你是产品经理，负责需求分析和 PRD 撰写。
      输出目录：docs/prd/
      约束：只写 PRD，不涉及技术方案
    tools: []  # 可选：限制可用工具
    inputDirs: []  # 输入目录
    outputDirs: ["docs/prd/"]  # 输出目录（用于权限控制）
    
  - id: architect
    name: 架构师
    description: 技术方案与 ICD 定义
    systemPrompt: |
      你是架构师，负责技术方案设计。
      输入：docs/prd/*.md
      输出：docs/architecture/*.md
      约束：只写架构文档，不写代码
    inputDirs: ["docs/prd/"]
    outputDirs: ["docs/architecture/"]
    
  - id: coder
    name: 开发工程师
    description: 代码实现
    systemPrompt: |
      你是开发工程师，负责代码实现。
      输入：docs/architecture/*.md, interfaces/*.proto
      输出：src/**/*.java
      约束：只写代码，不修改 interfaces/
    inputDirs: ["docs/architecture/", "interfaces/"]
    outputDirs: ["src/"]
    
  - id: safety
    name: 安全审计员
    description: 安全审计与门禁
    systemPrompt: |
      你是安全审计员，负责代码审计。
      输入：src/**/*
      输出：audit/*.md
      约束：只读代码，只写审计报告
    inputDirs: ["src/"]
    outputDirs: ["audit/"]

# 流程模板（可选）
templates:
  - id: standard-feature
    name: 标准功能开发
    description: PM → Architect → Coder → Safety
    stages:
      - role: pm
        task: "根据需求生成 PRD"
      - role: architect
        task: "根据 PRD 生成架构设计"
      - role: coder
        task: "根据架构设计实现代码"
      - role: safety
        task: "审计代码安全性"
  
  - id: bugfix
    name: Bug 修复
    description: PM → Coder → Safety
    stages:
      - role: pm
        task: "分析 bug 影响范围"
      - role: coder
        task: "修复 bug"
      - role: safety
        task: "验证修复"
```

### 4.3 项目级配置发现

Orchestrator 需要知道"当前项目"的 `roles.yaml` 在哪里。支持多种配置发现策略：

**策略 1：Git 仓库自动发现（推荐）**

```javascript
// 1. 获取当前 Git 仓库根目录
const repoRoot = await ctx.shell.run('git rev-parse --show-toplevel');

// 2. 查找 .orchestrator/roles.yaml
const configPath = path.join(repoRoot, '.orchestrator', 'roles.yaml');

// 3. 如果存在，加载项目级配置
if (await ctx.fs.stat(configPath)) {
  return await ctx.fs.readText(configPath);
}

// 4. 否则 fallback 到全局配置
return await ctx.fs.readText(globalConfigPath);
```

**策略 2：DSH Workspace 绑定**

```javascript
// 通过 DSH Workspace 服务获取当前工作区路径
const workspace = ctx.workspaceRegistry.get(currentWorkspaceId);
const configPath = path.join(workspace.path, '.orchestrator', 'roles.yaml');
```

**策略 3：命令行参数覆盖**

```bash
/orchestrate:task action=start config=/path/to/custom-roles.yaml
```

**配置优先级**（从高到低）：
1. 命令行参数 `--config`
2. Git 仓库内的 `.orchestrator/roles.yaml`
3. DSH Workspace 级别的 `.orchestrator/roles.yaml`
4. 全局配置 `~/.dsh/orchestrator/roles.yaml`

**状态存储策略（选项 B：DSH Session 为主）**

任务状态以 DSH Session 为主，Git 仅存储产出物：

| 数据类型 | 存储位置 | 说明 |
|----------|----------|------|
| 任务状态 | DSH Session + `ctx.sessionPersistence` | 实时状态、事件时间线 |
| 产出物 | Git feature branch | PRD、架构文档、代码、审计报告 |
| 审计日志 | Git commit history | 每个产出物都有 commit 记录 |
| 会话归属 | DSH Session tree | 子会话通过 `listChildren` 关联 |

**优点**：
- DSH 原生支持事件驱动和实时查询
- 避免 Git YAML 冲突
- 产出物天然在 Git 中，可 diff/review

### 4.4 Provider 动态注册

Orchestrator 启动时读取项目级 `roles.yaml`，为每个角色动态注册 Subagent Provider：

```javascript
// 伪代码
const config = await this.loadProjectConfig(ctx);

for (const role of config.roles) {
  ctx.subagents.registerProvider({
    name: role.id,
    async start(request) {
      // 1. 读取角色配置
      const roleConfig = getRoleConfig(role.id);
      
      // 2. 创建 Agent context
      const agentOptions = {
        preset: role.id,
        systemPrompt: roleConfig.systemPrompt,
        tools: roleConfig.tools || [],  // 支持单独配置可用工具
        inputDirs: roleConfig.inputDirs,
        outputDirs: roleConfig.outputDirs
      };
      
      // 3. 调用 DSH Agent Runtime
      return await ctx.agents.create({
        sessionId: generateSessionId(),
        options: agentOptions
      });
    },
    
    async followup(parent, childId, content, options) {
      // 支持向运行中的子 Agent 追加消息
      return await ctx.subagents.followup(parent, childId, content, options);
    },
    
    async listChildren(parentSessionId) {
      // 返回该角色下的所有子会话
      return await ctx.subagents.listChildren(parentSessionId);
    }
  });
}
```

**工具权限隔离**：

每个角色可单独配置可用工具列表，Orchestrator 在创建子 Agent 时动态限制：

```yaml
roles:
  - id: coder
    tools: ["edit", "write", "bash"]  # 只有这三个工具可用
  - id: safety
    tools: ["read", "bash"]  # 只读 + 执行审计命令
```

**硬隔离实现**（可选）：
```javascript
// 为子 Agent 创建独立的 tool scope
ctx.tools.restrict({
  scope: childSessionId,
  allow: roleConfig.tools || []
});
```

### 4.5 并行执行与依赖管理

支持三种执行模式，由模板或调用参数指定：

**串行模式（serial）**：
```yaml
templates:
  - id: standard-feature
    executionMode: serial
    stages:
      - role: pm
      - role: architect
      - role: coder
      - role: safety
```

**并行模式（parallel）**：
```yaml
templates:
  - id: parallel-implementation
    executionMode: parallel
    stages:
      - role: coder-a
        parallelWith: []  # 无依赖，可并行
      - role: coder-b
        parallelWith: []  # 无依赖，可并行
      - role: merger
        dependsOn: [coder-a, coder-b]  # 等待前两个完成
```

**条件分支模式（conditional）**：
```yaml
templates:
  - id: smart-review
    executionMode: conditional
    stages:
      - role: coder
      - role: reviewer
        condition: "output.hasComments"  # 根据上一个角色的输出决定
        ifTrue: safety
        ifFalse: merger
```

**依赖解析算法**：
```javascript
// 拓扑排序 + 并行调度
function resolveStages(stages) {
  const graph = buildDependencyGraph(stages);
  const sorted = topologicalSort(graph);
  
  // 找出每层可并行的节点
  const layers = [];
  let currentLayer = [];
  
  for (const stage of sorted) {
    if (stage.dependsOn.every(dep => currentLayer.includes(dep))) {
      currentLayer.push(stage);
    } else {
      layers.push(currentLayer);
      currentLayer = [stage];
    }
  }
  layers.push(currentLayer);
  
  return layers;  // 每层内并行，层间串行
}
```

### 4.6 重试与人工接管策略

**重试配置**：
```yaml
retry:
  maxAttempts: 3
  backoffMs: 5000
  onFinalFailure: "pause_for_human"  # pause_for_human | abort | skip
```

**状态流转**：

```
pending → in_progress → completed
                ↘
                 failed → retrying → completed
                                    ↘
                                     failed (max retries reached)
                                            │
                                            ▼
                                    pause_for_human
                                            │
                                            ▼
                                    human_taken_over
                                            │
                                            ▼
                                    (用户操作) → resumed / skipped / aborted
```

**自动重试逻辑**：
```javascript
async function executeStageWithRetry(stage, task) {
  for (let attempt = 0; attempt < stage.maxAttempts; attempt++) {
    try {
      const result = await ctx.subagents.start(stage.role, stage.task);
      return result;
    } catch (error) {
      if (attempt < stage.maxAttempts - 1) {
        await ctx.timer.timeout(stage.backoffMs);
        continue;
      }
      // 重试耗尽
      if (stage.onFinalFailure === 'pause_for_human') {
        await triggerHumanTakeover(task, stage, error);
      } else if (stage.onFinalFailure === 'skip') {
        await markStageSkipped(task, stage);
      } else {
        await abortTask(task, error);
      }
    }
  }
}
```

**人工接管 UI 状态**：

| 状态 | 卡片显示 | 可用操作 |
|------|----------|----------|
| `failed` | 🔴 failed | [查看日志] [重试] |
| `failed (needs human)` | 🔴 failed (needs human) | [查看日志] [重试] [跳过] [接管并编辑] [终止任务] |
| `human_taken_over` | 🟡 human_taken_over | [继续] [终止任务] |
| `skipped` | ⚪ skipped | [查看日志] |

### 4.4 状态机设计

```
                    ┌───────────┐
                    │   IDLE    │
                    └─────┬─────┘
                          │ /orchestrate:task
                          ▼
                    ┌───────────┐
      ┌─────────────│ DISPATCH  │◄──────────┐
      │             └─────┬─────┘           │
      │ 成功               │ 失败            │ 重试
      │                   ▼                 │
      │             ┌───────────┐           │
      └─────────────│  WAITING  │           │
                    └─────┬─────┘           │
                          │ subagent/end     │
                          ▼                  │
                    ┌───────────┐           │
      ┌─────────────│ COLLECT   │           │
      │             └─────┬─────┘           │
      │ 完成               │ 超时/错误       │
      │                   ▼                 │
      │             ┌───────────┐           │
      └─────────────│  ROUTING  │───────────┘
                    └─────┬─────┘
                          │ 有下一步
                          ▼
                    ┌───────────┐
      ┌─────────────│  NEXT     │◄──────────┐
      │             └─────┬─────┘           │
      │ 完成               │ 终止            │ 终止条件
      │                   ▼                 │
      │             ┌───────────┐           │
      └─────────────│  DONE     │───────────┘
                    └───────────┘
```

**状态说明**：

| 状态 | 说明 | 触发条件 |
|------|------|----------|
| IDLE | 等待新任务 | 初始化完成 |
| DISPATCH | 正在分发任务 | 收到 /orchestrate:task |
| WAITING | 等待子 Agent 完成 | subagent/start |
| COLLECT | 收集子 Agent 结果 | subagent/end |
| ROUTING | 路由决策 | 结果收集完成 |
| NEXT | 推进下一阶段 | 路由决策确定 |
| DONE | 流程结束 | 全部完成或终止 |
| ERROR | 错误状态 | Agent 失败/超时 |

---

## 5. 数据流设计

### 5.1 主流程（支持串行/并行/条件分支）

```
用户输入
   │
   ▼
/orchestrate:task { template: "standard-feature", title: "xxx" }
   │
   ▼
[Orchestrator] 读取 roles.yaml + template
   │
   ▼
[Orchestrator] 创建 Task Envelope + Git feature branch
   │
   ├─► [PM] 生成 PRD → Git commit to feature branch
   │       │
   │       ▼
   │   [Orchestrator] 监听 subagent/end
   │       │
   │       ▼
   ├─► [Architect] 生成 ICD → Git commit to feature branch
   │       │
   │       ▼
   │   [Orchestrator] 监听 subagent/end
   │       │
   │       ▼
   ├─► [Coder] 实现代码 → Git commit to feature branch
   │       │
   │       ▼
   │   [Orchestrator] 监听 subagent/end
   │       │
   │       ▼
   └─► [Safety] 审计门禁 → Git tag/review
           │
           ▼
       等待人工 Review → 合并到 main/develop
```

**关键特性**：
- 角色序列由 `roles.yaml` 中的 `templates` 定义
- 用户可在调用时通过 `roles` 参数覆盖模板
- Orchestrator 本身不知道"PM"或"Architect"是什么，只认识 `role.id`
- **每个项目自动加载自己的 `.orchestrator/roles.yaml`**，不同项目拥有完全独立的角色体系

**执行模式**：

| 模式 | 说明 | 实现方式 |
|------|------|----------|
| 串行 | 严格按顺序执行，等上一个完成再开始下一个 | `await runStage(stage)` |
| 并行 | 多个角色同时运行（如 Coder-A 和 Coder-B） | `Promise.all([runStage(a), runStage(b)])` |
| 条件分支 | 根据上一个角色的输出决定下一个角色 | `if (output.hasError) runStage(safety) else runStage(coder)` |

**Worktree 管理**：

- 按任务创建 worktree：`wt switch --create feature/task-xxx`
- 每个角色在同一个 worktree 中操作，避免环境不一致
- 任务结束后归档或删除 worktree

**Git 策略**：

- 每个角色独立 commit 到 feature branch
- Commit message 格式：`[role.id] [Model] [Task-ID] [Status]`
- Safety 审核通过后，人工合并到 main/develop
- AI 禁止自动合并到 main/develop

### 5.2 Task Envelope（任务信封）

每个任务携带最小上下文，通过 DSH Session Metadata + Git Commit Message 传递：

```yaml
taskId: "task-20250814-001"
type: "feature" | "bugfix" | "refactor"
title: "用户权限模块重构"
priority: "high"
createdBy: "user-xxx"
template: "standard-feature"  # 使用的流程模板
currentRole: "pm"             # 当前执行的角色（动态）
currentStageIndex: 0          # 当前在模板中的索引
executionMode: "serial"       # serial | parallel | conditional
stages:
  - role: "pm"
    task: "根据需求生成 PRD"
    status: "completed"
    output: "docs/prd/20250814-001.md"
    sessionId: "xxx"
    commit: "abc123"
    retryCount: 0
  - role: "architect"
    task: "根据 PRD 生成架构设计"
    status: "in_progress"
    sessionId: "yyy"
    retryCount: 0
dependencies: []
artifacts:
  - path: "docs/prd/20250814-001.md"
    role: "pm"
    status: "completed"
    commit: "abc123"
  - path: "docs/architecture/20250814-001.md"
    role: "architect"
    status: "in_progress"
git:
  baseBranch: "main"
  workBranch: "feature/task-20250814-001"
retry:
  maxAttempts: 3
  backoffMs: 5000
  onFinalFailure: "pause_for_human"  # pause_for_human | abort | skip
worktree:
  path: ".worktrees/task-20250814-001"
  createOnStart: true
  cleanupOnDone: false
```

**字段说明**：

| 字段 | 说明 |
|------|------|
| `executionMode` | 执行模式：串行/并行/条件分支 |
| `stages[].sessionId` | 子 Agent 的 DSH Session ID |
| `stages[].commit` | 产出物对应的 Git commit hash |
| `stages[].retryCount` | 当前已重试次数 |
| `retry.onFinalFailure` | 重试耗尽后的策略 |
| `worktree.path` | Worktree 路径（按任务创建） |

**状态流转**：

```
pending → in_progress → completed
                ↘
                 failed → retrying → completed
                                    ↘
                                     failed (max retries reached)
                                            │
                                            ▼
                                    pause_for_human
                                            │
                                            ▼
                                    human_took_over
```

### 5.3 Agent 通信协议

**原则**：Agent 之间**不直接通信**，所有协调通过 Orchestrator + Git 完成。

```
Agent A (role.id = "pm")
   │
   │ 1. 产出 PRD → Git commit
   │    commit msg: "[pm] [Claude-3.5] [task-20250814-001] Add PRD"
   │
   ▼
Git (主黑板)
   │
   │ 2. Orchestrator 通过 fs/observed 或 subagent/end 感知完成
   │
   ▼
Orchestrator
   │
   │ 3. 解析 Git diff / 读取文件
   │ 4. 更新 Task Envelope.currentRole / currentStageIndex
   │ 5. 决定下一角色（从模板或 roles 参数）
   │
   ▼
Agent B (role.id = "architect")
   │
   │ 6. 读取 docs/prd/20250814-001.md
   │ 7. 产出 ICD → Git commit
   │
   ▼
... (循环)
```

---

## 6. 接口定义

### 6.1 工具入口

```javascript
// 注册到 ctx.tools
{
  name: "orchestrate",
  description: "动态编排多角色工作流",
  inputSchema: {
    type: "object",
    properties: {
      action: {
        type: "string",
        enum: ["start", "status", "cancel", "retry"],
        description: "编排动作"
      },
      template: {
        type: "string",
        description: "流程模板名称（如 standard-feature），从 roles.yaml 读取"
      },
      roles: {
        type: "array",
        description: "自定义角色序列（覆盖模板）",
        items: {
          type: "object",
          properties: {
            role: { type: "string", description: "角色 ID" },
            task: { type: "string", description: "该角色的具体任务" }
          }
        }
      },
      title: {
        type: "string",
        description: "任务标题"
      },
      taskId: {
        type: "string",
        description: "现有任务ID（用于 status/cancel）"
      }
    },
    required: ["action"]
  }
}
```

**使用示例**：

```yaml
# 使用模板
action: start
template: standard-feature
title: "用户权限模块重构"

# 自定义角色序列（覆盖模板）
action: start
roles:
  - role: "architect"
    task: "快速设计 API 接口"
  - role: "coder"
    task: "实现 API 接口"
  - role: "safety"
    task: "安全审计"
```

### 6.2 Subagent Provider 接口（动态）

```javascript
// Orchestrator 启动时动态注册
for (const role of config.roles) {
  ctx.subagents.registerProvider({
    name: role.id,
    async start(request) {
      // 1. 读取角色配置
      const roleConfig = getRoleConfig(role.id);
      
      // 2. 创建 Agent context
      const messages = [
        {
          role: "system",
          content: roleConfig.systemPrompt
        },
        {
          role: "user",
          content: this.buildTaskMessage(role, request.task)
        }
      ];
      
      // 3. 调用 DSH Agent Runtime
      return await ctx.agents.create({
        sessionId: generateSessionId(),
        options: {
          preset: role.id,
          systemPrompt: roleConfig.systemPrompt,
          tools: roleConfig.tools || []
        }
      });
    },
    
    async followup(parent, childId, content, options) {
      // 后续追问（如产出物不合格要求修改）
      return await ctx.subagents.followup(parent, childId, content, options);
    },
    
    async listChildren(parentSessionId) {
      // 返回该角色下的所有子会话
      return await ctx.subagents.listChildren(parentSessionId);
    }
  });
}
```

---

## 7. 事件驱动流程

### 7.1 关键事件监听

```javascript
// Orchestrator Plugin apply()
return {
  apply(ctx) {
    // 1. 子 Agent 启动
    ctx.on('subagent/start', (info) => {
      this.handleSubagentStart(info);
    });
    
    // 2. 子 Agent 结束
    ctx.on('subagent/end', (info) => {
      this.handleSubagentEnd(info);
    });
    
    // 3. Agent 错误
    ctx.on('agent/error', (payload) => {
      this.handleAgentError(payload);
    });
    
    // 4. 文件变更（Git commit）
    ctx.on('fs/observed', (target, observation) => {
      this.handleFileChange(target, observation);
    });
    
    // 5. 会话事件
    ctx.on('session/event', (session, event) => {
      this.handleSessionEvent(session, event);
    });
  }
}
```

### 7.2 事件处理逻辑

| 事件 | 处理逻辑 |
|------|----------|
| `subagent/start` | 更新状态 WAITING，记录 startTime |
| `subagent/end` | 收集结果，检查产出物，更新 Task Envelope |
| `agent/error` | 重试或进入 ERROR 状态，通知用户 |
| `fs/observed` | 检测新 commit，解析 commit msg 中的 `[Agent-Role]` |
| `session/event` | 审计日志，持久化状态快照 |

---

## 8. 安全与护栏

### 8.1 Git 权限模型

权限基于 `roles.yaml` 中的 `outputDirs` 动态生成：

| 目录 | 写入权限 | 说明 |
|------|----------|------|
| `docs/prd/` | `pm` | 需求文档 |
| `docs/architecture/` | `architect` | 架构设计 |
| `interfaces/` | **只读** | 接口契约，只有指定角色可写入 |
| `src/` | `coder` | 代码实现 |
| `audit/` | `safety` | 审计报告 |
| `.orchestrator/` | Orchestrator | 任务状态和元数据 |
| `.worktrees/` | Orchestrator | Worktree 管理目录 |

**动态权限检查**：
```javascript
// 每个角色执行前，检查其 outputDirs 是否在允许范围内
function checkWritePermission(roleId, targetPath) {
  const role = getRoleConfig(roleId);
  return role.outputDirs.some(dir => 
    targetPath.startsWith(dir)
  );
}
```

**Feature Branch 策略**：

- 每个任务创建独立的 feature branch：`feature/task-<taskId>`
- 各角色独立 commit 到该分支
- Safety 审核通过后，人工合并到 `main` / `develop`
- AI **禁止**自动合并到 `main` / `develop`

### 8.2 Commit Message 规范

```
[role.id] [Model-Version] [Task-ID] [Status]

Role: 来自 roles.yaml 的 role.id
Model: Claude-3.5-Sonnet | GPT-4o | ...
Status: START | PROGRESS | COMPLETE | FAILED | RETRY | HUMAN_TAKEN

Example:
[pm] [Claude-3.5] [task-20250814-001] [COMPLETE]
[architect] [GPT-4o] [task-20250814-001] [COMPLETE]
[safety] [Claude-3.5] [task-20250814-001] [FAILED]  # 触发人工接管
```

### 8.3 人类接管门禁

- AI **禁止**直接合并到 `main` / `develop`
- 最终产出物（如架构设计、代码）必须经过 **Human Review** 才能合并
- 高风险操作（删除分支、强制推送）必须通过 `ctx.userQuestions` 请求人工确认
- 用户可在 `roles.yaml` 中为特定角色设置 `requireHumanReview: true`

**重试失败后的接管流程**：

```
角色执行失败
    │
    ▼
自动重试（最多 maxAttempts 次）
    │
    ▼
重试耗尽
    │
    ▼
触发人工接管（pause_for_human）
    │
    ├─► 用户选择：继续重试
    ├─► 用户选择：跳过该角色
    ├─► 用户选择：手动修复并继续
    └─► 用户选择：终止任务
```

**人工接管时的 UI 状态**：
- 子 Agent 卡片显示 🔴 `failed (needs human)`
- 提供按钮：[重试] [跳过] [接管并编辑] [终止任务]
- 用户接管后，可直接编辑文件并手动触发下一阶段

---

## 9. 可观测性与可视化

### 9.1 核心问题：用户能否看见每个 Subagent？

**答案是：可以，而且 DSH 原生支持多层级观测。**

Orchestrator 设计为**事件驱动 + 状态持久化**，所有子 Agent 的生命周期都可被观测：

| 观测维度 | DSH 原生能力 | 说明 |
|----------|-------------|------|
| 实时状态 | `subagent/start`, `subagent/end`, `agent/status` | 事件推送，无需轮询 |
| 会话查询 | `ctx.sessionQuery.searchSessions`, `readSession`, `listEvents` | 查看子会话完整日志 |
| 层级关系 | `ctx.subagents.listChildren`, `listDescendants` | 查看父/子/孙会话树 |
| 结果收集 | `subagent/end` payload | 包含子 Agent 产出结果 |
| 审计追踪 | Git commit history + `ctx.sessionPersistence` | 完整操作留痕 |

### 9.2 观测层级

```
Orchestrator (父会话)
├── [pm] Session-1 (子会话)
│   ├── 状态：running
│   ├── 产出：docs/prd/20250814-001.md
│   └── Git commit: [pm] [Claude-3.5] [task-001] [COMPLETE]
│
├── [architect] Session-2 (子会话)
│   ├── 状态：pending (等待 Session-1 完成)
│   └── 依赖：docs/prd/20250814-001.md
│
└── [coder] Session-3 (子会话)
    ├── 状态：not-started
    └── 依赖：docs/architecture/20250814-001.md
```

### 9.3 查询接口（DSH 原生）

```javascript
// 1. 查看 Orchestrator 的所有直接子会话
const children = await ctx.subagents.listChildren(orchestratorSessionId);
// 返回：[{ sessionId, role: 'pm', status: 'running' }, ...]

// 2. 查看完整后代树
const descendants = await ctx.subagents.listDescendants(orchestratorSessionId);
// 返回：多层级的会话树，包含孙子会话

// 3. 查询特定子会话的详情
const session = await ctx.sessionQuery.readSession(childSessionId);
// 返回：完整对话日志、工具调用、模型输出

// 4. 查看子会话的事件时间线
const events = await ctx.sessionQuery.listEvents(childSessionId);
// 返回：[{ type: 'subagent/start', timestamp }, ...]

// 5. 搜索包含特定角色的所有会话
const results = await ctx.sessionQuery.searchSessions({
  query: 'role:pm',
  filters: [{ field: 'metadata.role', value: 'pm' }]
});
```

### 9.4 可视化方案：在工作空间下方显示 Subagent 会话

#### 设计目标

用户希望在**工作空间（Workspace）下方**直接看到当前 Orchestrator 任务的所有子 Agent 会话，并能够进行交互操作。

**关键要求**：
- 位置：工作空间下方（侧边栏或主会话区下方）
- 内容：子 Agent 列表，包含角色、状态、产出物
- 交互：点击查看详情、发送消息、停止/重启、查看输出文件
- 直接点击进入子会话：点击子 Agent 卡片可直接跳转到该子会话的对话界面
- 并行展示：支持并行执行的角色同时显示
- 人工接管：失败角色显示接管按钮，支持用户直接介入

#### Slot 选型

| 候选 Slot | 位置 | 类型 | 推荐度 | 说明 |
|-----------|------|------|--------|------|
| `sidebar.workspaces` | 左侧边栏 | single | ⭐⭐⭐ | 工作区列表本身，可在下方插入任务树 |
| `sidebar.workspaces.directoryFlow` | 侧边栏子槽 | single | ⭐⭐⭐ | 工作区浏览区的扩展槽，适合插入子 agent 面板 |
| `conversation.composer.dock` | 输入框下方 | list | ⭐⭐ | 状态栏区域，适合显示紧凑状态 |
| `conversation.session` | 主会话区 | single | ⭐⭐ | 对话主体下方，适合插入子 agent 卡片 |
| `conversation.details.tool` | 右侧详情 | single | ⭐ | 选中子 agent 后显示详情 |

**推荐方案**：`sidebar.workspaces.directoryFlow`

```
┌─────────────────────┐
│ 🔵 Orchestrator     │
│ task-20250814-001   │
├─────────────────────┤
│ 📂 Workspaces       │
│  ├── qditp          │
│  └── itp-worktree-poc│
├─────────────────────┤
│ 🤖 Subagents        │  ← sidebar.workspaces.directoryFlow
│  ├─ 🟢 pm           │    运行中
│  │   docs/prd/...   │    点击进入会话 → 发送消息 → 停止
│  │                   │
│  ├─ ⚪ architect     │    等待中
│  │   docs/arch/...  │    依赖: docs/prd/...
│  │                   │
│  └─ ⚪ coder         │    未开始
│      src/...         │    依赖: docs/architecture/...
│                      │
│  [并行模式示例]       │
│  ├─ 🟢 coder-a       │    运行中
│  ├─ 🟢 coder-b       │    运行中
│  └─ ⚪ merger        │    等待合并
└─────────────────────┘
```

#### UI 交互设计

**每个子 Agent 卡片显示**：

```
┌─ [pm] 🟢 running ──────────┐
│ 任务: 生成 PRD              │
│ 产出: docs/prd/20250814-001 │
│ 会话: Session-1             │
│                             │
│ [查看日志] [发送消息] [停止] │
└─────────────────────────────┘

┌─ [coder] 🔴 failed ─────────┐
│ 任务: 实现用户权限模块       │
│ 错误: 编译失败，重试 3 次    │
│ 会话: Session-3             │
│                             │
│ [查看日志] [重试] [跳过]     │
│ [接管并编辑] [终止任务]      │  ← 人工接管按钮
└─────────────────────────────┘
```

**交互操作列表**：

| 操作 | 触发方式 | Host 端实现 |
|------|----------|-------------|
| 进入子会话 | 点击卡片标题或「查看日志」 | 跳转到该 session 的对话界面 |
| 发送追加消息 | 在子会话中输入框发送 | `ctx.subagents.followup(parent, childId, content)` |
| 停止子 Agent | 点击「停止」 | `ctx.subagents.interrupt(sessionId)` |
| 重启子 Agent | 点击「重启」 | 重新调用 `ctx.subagents.start(role, task)` |
| 打开产出文件 | 点击文件路径 | `ctx.fs.readText(path)` 或打开编辑器 |
| 查看 Git 历史 | 点击 commit hash | `ctx.shell.run('git show ...')` |
| 查看角色配置 | 点击角色名 | 读取 `roles.yaml` 中的对应 role |
| 人工接管 | 点击「接管并编辑」 | 暂停子 Agent，用户手动编辑文件 |
| 跳过该角色 | 点击「跳过」 | 标记为 skipped，继续下一阶段 |
| 重试 | 点击「重试」 | 重新执行该角色，retryCount+1 |

#### Host-Client 通信协议

```javascript
// Host 端暴露 JSON API
ctx.host.handle('orchestrator.getSubagentTree', async (taskId) => {
  const task = this.getTask(taskId);
  const children = await ctx.subagents.listChildren(task.orchestratorSessionId);
  
  return {
    taskId,
    currentRole: task.currentRole,
    executionMode: task.executionMode,  // serial | parallel | conditional
    children: await Promise.all(children.map(async (child) => {
      const session = await ctx.sessionQuery.readSession(child.sessionId);
      const roleConfig = this.getRoleConfig(child.role);
      
      return {
        roleId: child.role,
        roleName: roleConfig.name,
        sessionId: child.sessionId,
        status: child.status,  // running | pending | completed | failed | skipped
        task: child.task,
        artifacts: this.extractArtifacts(session),
        lastActivity: session.updatedAt,
        canInterrupt: child.status === 'running',
        canRestart: child.status === 'failed' || child.status === 'completed',
        canSkip: child.status === 'failed' && task.retryCount >= task.maxRetries,
        canTakeOver: child.status === 'failed' && task.retryCount >= task.maxRetries
      };
    }))
  };
});

// Client 端调用
const tree = await host.call('orchestrator.getSubagentTree', taskId);

// 人工接管 API
ctx.host.handle('orchestrator.takeOver', async (taskId, sessionId) => {
  // 1. 中断子 Agent
  await ctx.subagents.interrupt(sessionId, 'human-takeover');
  // 2. 更新 Task Envelope 状态
  this.updateTaskStatus(taskId, sessionId, 'human_taken_over');
  // 3. 通知用户
  await ctx.userQuestions.ask({
    question: `已接管 ${sessionId}，请手动编辑文件后点击继续`,
    options: [{ label: '继续' }]
  });
});
```

**并行执行的 UI 展示**：

```
并行模式（如 Coder-A 和 Coder-B 同时实现）：
┌─ [coder-a] 🟢 running ──────┐
│ 实现方案 A                  │
│ src/user/AuthService.java    │
└─────────────────────────────┘
┌─ [coder-b] 🟢 running ──────┐
│ 实现方案 B                  │
│ src/user/AuthService.java    │
└─────────────────────────────┘
      ↓
┌─ [merger] ⚪ pending ───────┐
│ 合并两个方案                │
└─────────────────────────────┘
```

#### 实时更新机制

| 机制 | 实现方式 | 延迟 |
|------|----------|------|
| 事件推送 | Host 监听 `subagent/end` → 更新内存状态 | 毫秒级 |
| 增量推送 | `ctx.host.handle` 返回 observable（或 SSE） | 秒级 |
| UI 刷新 | Client Slot 订阅状态变更 | 实时 |
| 手动刷新 | 用户点击刷新按钮 | 即时 |

**推荐架构**：
```
Client Slot (sidebar.workspaces.directoryFlow)
    │
    │ (1) 初始化: host.call('orchestrator.getSubagentTree')
    ▼
Host Plugin
    │
    │ (2) 返回子 agent 树 JSON
    ▼
Client 渲染可折叠树形列表
    │
    │ (3) Host 监听 subagent/end 事件
    ▼
Host 更新内存状态
    │
    │ (4) (可选) 通过 SSE / polling 推送增量
    ▼
Client 局部刷新状态图标 + 按钮
```

#### 交互示例

**直接进入子会话**：
```
用户点击子 Agent 卡片 [pm] 🟢 running
    │
    ▼
Client: 跳转到该 session 的对话界面
    │
    ▼
用户可直接在该会话中发送消息
    │
    ▼
Host: ctx.subagents.followup(parentSessionId, childId, content)
    │
    ▼
子 Agent 收到追加消息，继续执行
```

**人工接管**：
```
用户点击 [接管并编辑]
    │
    ▼
Client: host.call('orchestrator.takeOver', { taskId, sessionId })
    │
    ▼
Host: ctx.subagents.interrupt(sessionId, 'human-takeover')
    │
    ▼
Host: 更新 Task Envelope 状态为 human_taken_over
    │
    ▼
Client: 子 Agent 卡片变为 🔴 human_taken_over
         Worktree 保持打开，用户可直接编辑文件
    │
    ▼
用户手动编辑 src/.../AuthService.java
    │
    ▼
用户点击 [继续]
    │
    ▼
Client: host.call('orchestrator.resume', { taskId, sessionId })
    │
    ▼
Host: 继续下一阶段或重新执行当前阶段
```

**并行执行示例**：

```
用户调用：
/orchestrate:task template=parallel-implementation title="用户权限模块"
    │
    ▼
Orchestrator 解析模板，发现 coder-a 和 coder-b 可并行
    │
    ├─► [coder-a] Session-1 (并行启动)
    │       │
    │       ▼
    │   实现方案 A → Git commit
    │
    ├─► [coder-b] Session-2 (并行启动)
    │       │
    │       ▼
    │   实现方案 B → Git commit
    │
    └─► [merger] Session-3 (等待前两个完成)
            │
            ▼
        合并方案 A + B → Git commit
```

**重试失败后的接管流程**：

```
角色 [coder] 执行失败（编译错误）
    │
    ▼
自动重试 1/3
    │
    ▼
自动重试 2/3
    │
    ▼
自动重试 3/3 → 仍然失败
    │
    ▼
触发人工接管
    │
    ├─► 子 Agent 卡片显示 🔴 failed (needs human)
    ├─► 提供按钮：[重试] [跳过] [接管并编辑] [终止任务]
    │
    ▼
用户点击 [接管并编辑]
    │
    ▼
子 Agent 被中断，Worktree 保持打开
    │
    ▼
用户手动修复编译错误
    │
    ▼
用户点击 [重试]
    │
    ▼
Orchestrator 重新执行 [coder] 角色
    │
    ▼
成功 → 继续下一阶段
```

#### 与 Git 的可视化集成

在 Git 仓库中维护 `.orchestrator/state/` 目录：

```
.orchestrator/
├── roles.yaml
└── state/
    └── task-20250814-001.yaml
        currentRole: architect
        stages:
          - role: pm
            status: completed
            commit: abc123
            sessionId: xxx
          - role: architect
            status: in_progress
            sessionId: yyy
```

用户可直接通过 Git 查看任务状态：
```bash
git log --oneline | grep task-20250814-001
git show .orchestrator/state/task-20250814-001.yaml
```

---

## 10. 与现有 PoC 的映射关系

| PoC 组件 | DSH 实现映射 |
|----------|-------------|
| `run-poc.sh` | DSH Session + Goal |
| `orchestrator.sh` | Orchestrator Cordis Plugin |
| `worktrunk.sh` | `ctx.shell` 调用 `wt` CLI |
| `agents/pm/run.sh` | Subagent Provider: `pm` |
| `agents/architect/run.sh` | Subagent Provider: `architect` |
| `agents/coder/run.sh` | Subagent Provider: `coder` |
| `agents/safety/run.sh` | Subagent Provider: `safety` |
| Git 仓库 | 主黑板（Blackboard） |
| Git Hooks / CI | 事件触发源 |

---

## 11. 实现路径

### Phase 1: 基础骨架（当前可做）
1. 在 DSH 中创建 Orchestrator Plugin（等 `cordis_define` 修复）
2. 实现 `roles.yaml` 解析器（支持 Orchestrator 作为可配置角色）
3. 动态注册 Subagent Provider（从配置读取，支持工具权限隔离）
4. 注册 `/orchestrate:task` 工具（支持 `template` + `roles` 参数）
5. 实现串行流水线（基于模板或 roles 数组）
6. 实现 DSH Session 为主的状态管理

### Phase 2: 并行与重试
1. 实现并行执行引擎（`Promise.all` + 依赖图）
2. 实现条件分支模式（根据上游输出路由）
3. 实现自动重试机制（指数退避）
4. 实现人工接管流程（pause_for_human → human_taken_over）
5. 注册 `/orchestrate:status`、`/orchestrate:retry`、`/orchestrate:takeOver` 工具

### Phase 3: 可视化与交互
1. 实现 Client Slot UI（`sidebar.workspaces.directoryFlow`）
2. 子 Agent 树形列表（状态、产出物、操作按钮）
3. 直接点击进入子会话
4. 实时状态更新（事件推送 + polling）
5. 并行角色分组展示

### Phase 4: Worktree 与 Git 集成
1. 按任务创建 Worktree（`wt switch --create feature/task-xxx`）
2. 各角色独立 commit 到 feature branch
3. Safety 审核后人工合并到 main/develop
4. Worktree 清理策略（任务完成后归档）

### Phase 5: 生产化
1. 接入真实 LLM（替换模板渲染）
2. CI/CD 集成（GitLab CI / GitHub Actions）
3. 监控与告警
4. 性能优化（流式输出、增量构建）
5. 角色模板市场（分享和复用 roles.yaml）

---

## 12. 当前阻塞点与解决

| 阻塞点 | 影响 | 解决方案 |
|--------|------|----------|
| `cordis_define` plugin 参数验证 bug | 无法注册动态 Plugin | 等 DSH 修复，或通过 preset/composition 注册 |
| 并行执行语义 | 需要明确并行粒度和冲突解决 | 通过模板 `executionMode` + 依赖图控制 |
| 人工接管流程 | 需要 UI 支持 Worktree 保持打开 | Client Slot 提供接管按钮，Host 中断子 Agent |
| 工具权限隔离 | 子 Agent 可能越权使用工具 | 通过 `tools` 配置 + `ctx.tools.restrict()` 硬隔离 |
| Git 权限隔离 | Agent 可能越权写入 | 通过 system prompt + 文件系统权限控制 |

---

## 13. 总结

**Orchestrator 在 DSH 中的本质**：

> 一个 **Cordis Plugin**，它不直接做业务逻辑，而是：
> 1. **读取** 用户提供的 `roles.yaml` 配置
> 2. **监听** DSH 原生事件（`subagent/end`, `agent/error`）
> 3. **调度** 动态注册的 Subagent Provider（包括 Orchestrator 自身）
> 4. **持久化** 任务状态到 DSH Session（为主）+ Git（产出物）
> 5. **暴露** 工具/命令供用户触发
> 6. **提供** 可视化面板，在工作空间下方展示子 Agent 并支持交互

**已确认的设计决策**：

| 决策 | 选择 | 理由 |
|------|------|------|
| Orchestrator 是否可配置 | **是** | 支持嵌套编排、自定义策略 |
| 会话归属 | **托管模式** | 子会话出现在工作空间下方，可点击进入 |
| 状态存储 | **选项 B：DSH Session 为主** | 原生支持事件查询，避免 Git 冲突 |
| 工具权限 | **支持单独配置** | 每个角色可配置不同工具列表，支持硬隔离 |
| Worktree 策略 | **按任务创建** | 避免并行任务冲突，环境隔离 |
| 执行模式 | **允许并行** | 支持 parallel + dependsOn 依赖图 |
| 失败处理 | **自动重试 → 人工介入** | 重试耗尽后 pause_for_human |
| 产出物归属 | **commit 到 feature branch** | 等 Safety 审核后人工合并到 main/develop |
| 交互边界 | **查看日志 + 追加消息 + 人工接管** | 兼顾自动化和人工控制 |

**它依赖 DSH 原生的能力**：
- `subagents` — Agent 生命周期管理、并行调度
- `tools/commands` — 入口注册、工具权限隔离
- `events` — 异步协调
- `fs/shell` — Git 交互、Worktree 管理
- `sessionPersistence` — 状态持久化（主）
- `sessionQuery` — 子会话查询、进入子会话
- `userQuestions` — 人工接管确认
- `settings` — 存储角色配置（可选）

**它不依赖外部的**：
- ❌ 独立消息队列（RabbitMQ/Kafka）
- ❌ 独立数据库（用 DSH Session + Git）
- ❌ 独立 API 服务（用 DSH 原生 Tools）
- ❌ 硬编码角色定义（用 `roles.yaml` 配置）

## 14. 使用示例

### 14.1 最小配置

```yaml
# .orchestrator/roles.yaml
version: "1.0"
roles:
  - id: reviewer
    name: 代码审查员
    systemPrompt: "你是一位资深工程师，负责审查代码质量。"
    outputDirs: ["audit/"]
```

### 13.2 完整工作流

```yaml
# .orchestrator/roles.yaml
version: "1.0"
roles:
  - id: analyst
    name: 需求分析师
    systemPrompt: "你是需求分析师，负责将模糊需求转化为清晰文档。"
    outputDirs: ["docs/requirements/"]
    
  - id: designer
    name: 设计师
    systemPrompt: "你是设计师，负责将需求转化为设计稿。"
    inputDirs: ["docs/requirements/"]
    outputDirs: ["docs/design/"]
    
  - id: developer
    name: 开发者
    systemPrompt: "你是开发者，负责实现功能。"
    inputDirs: ["docs/design/", "interfaces/"]
    outputDirs: ["src/"]

templates:
  - id: full-cycle
    name: 完整开发周期
    stages:
      - role: analyst
        task: "分析需求并输出需求文档"
      - role: designer
        task: "根据需求文档设计解决方案"
      - role: developer
        task: "根据设计文档实现代码"
```

### 14.3 调用方式

```bash
# 使用模板
/orchestrate:task action=start template=full-cycle title="用户注册功能"

# 自定义角色序列
/orchestrate:task action=start roles='[
  {"role": "analyst", "task": "快速分析需求"},
  {"role": "developer", "task": "快速实现 MVP"}
]'

# 并行模式
/orchestrate:task action=start template=parallel-implementation title="高可用方案"

# 查看状态
/orchestrate:task action=status taskId=task-20250814-001

# 人工接管
/orchestrate:task action=takeOver taskId=task-20250814-001 sessionId=xxx
```

### 14.4 多项目配置示例

**项目 A（ITP 系统）**：`.orchestrator/roles.yaml`
```yaml
version: "1.0"
roles:
  - id: pm
    name: 产品经理
    systemPrompt: "你是 ITP 产品经理，负责支付需求分析..."
    outputDirs: ["docs/prd/"]
  - id: architect
    name: 架构师
    systemPrompt: "你是金融系统架构师，负责高可用设计..."
    outputDirs: ["docs/architecture/"]
  - id: coder
    name: 开发工程师
    systemPrompt: "你是 Java 后端开发，负责支付核心..."
    outputDirs: ["src/"]
  - id: safety
    name: 安全审计员
    systemPrompt: "你是金融安全审计员，负责 PCI-DSS 合规..."
    outputDirs: ["audit/"]

templates:
  - id: standard-feature
    stages:
      - role: pm
      - role: architect
      - role: coder
      - role: safety
```

**项目 B（数据平台）**：`.orchestrator/roles.yaml`
```yaml
version: "1.0"
roles:
  - id: data-analyst
    name: 数据分析师
    systemPrompt: "你是数据分析师，负责定义指标口径..."
    outputDirs: ["docs/metrics/"]
  - id: data-engineer
    name: 数据工程师
    systemPrompt: "你是数据工程师，负责 ETL 开发..."
    outputDirs: ["src/etl/"]
  - id: qa
    name: 质量保证
    systemPrompt: "你是 QA，负责数据质量校验..."
    outputDirs: ["tests/quality/"]

templates:
  - id: etl-pipeline
    stages:
      - role: data-analyst
      - role: data-engineer
      - role: qa
```

**同一项目不同分支可覆盖配置**：
```
feature/new-role/
└── .orchestrator/
    └── roles.yaml   # 该分支专用的角色配置
```

### 14.5 并行模板示例

```yaml
# .orchestrator/roles.yaml
version: "1.0"
roles:
  - id: coder-a
    name: 开发工程师 A
    systemPrompt: "你是开发工程师 A，负责实现方案 A..."
    outputDirs: ["src/impl-a/"]
    
  - id: coder-b
    name: 开发工程师 B
    systemPrompt: "你是开发工程师 B，负责实现方案 B..."
    outputDirs: ["src/impl-b/"]
    
  - id: merger
    name: 合并工程师
    systemPrompt: "你是合并工程师，负责合并两个方案..."
    inputDirs: ["src/impl-a/", "src/impl-b/"]
    outputDirs: ["src/"]

templates:
  - id: parallel-implementation
    name: 并行实现
    executionMode: parallel
    stages:
      - role: coder-a
        parallelWith: [coder-b]
      - role: coder-b
        parallelWith: [coder-a]
      - role: merger
        dependsOn: [coder-a, coder-b]
```

### 14.6 人工接管示例

```yaml
# .orchestrator/roles.yaml
version: "1.0"
roles:
  - id: coder
    name: 开发工程师
    systemPrompt: "你是开发工程师..."
    outputDirs: ["src/"]

templates:
  - id: standard-feature
    executionMode: serial
    stages:
      - role: pm
      - role: architect
      - role: coder
        retry:
          maxAttempts: 3
          backoffMs: 5000
          onFinalFailure: "pause_for_human"
      - role: safety
```

**用户交互流程**：
1. Coder 角色重试 3 次后仍然失败
2. 侧边栏子 Agent 卡片显示 🔴 `failed (needs human)`
3. 用户点击 [接管并编辑]
4. Worktree 保持打开，用户可直接编辑 `src/...`
5. 用户修复问题后点击 [重试]
6. Orchestrator 重新执行 Coder 角色
7. 成功后自动进入 Safety 阶段

### 14.7 直接进入子会话

用户点击工作空间下方的子 Agent 卡片后：
1. DSH 自动跳转到该子会话的对话界面
2. 用户可直接在子会话中发送追加消息
3. 消息通过 `ctx.subagents.followup()` 发送给子 Agent
4. 子 Agent 收到消息后继续执行
5. 侧边栏面板实时更新状态

---

*文档结束*
