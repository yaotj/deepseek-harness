/**
 * Orchestrator Plugin - Phase 1 Skeleton
 *
 * 当前状态：代码已就绪，等待 cordis_define 修复后注册
 * 使用方式：将 src/index.js 作为 cordis_define 的 code.host 传入
 */

// ==================== 配置加载 ====================

async function loadProjectConfig(ctx) {
  // 策略1: 命令行参数
  // const configPath = ctx.settings.get('orchestrator.configPath');
  
  // 策略2: Git 仓库内 .orchestrator/roles.yaml（推荐）
  const repoRoot = await ctx.shell.run('git rev-parse --show-toplevel');
  const configPath = repoRoot.stdout.trim() + '/.orchestrator/roles.yaml';
  
  try {
    const content = await ctx.fs.readText(configPath);
    return parseRolesYaml(content);
  } catch (e) {
    // 策略3: 全局 fallback
    const globalPath = require('os').homedir() + '/.dsh/orchestrator/roles.yaml';
    const globalContent = await ctx.fs.readText(globalPath);
    return parseRolesYaml(globalContent);
  }
}

function parseRolesYaml(content) {
  // 简化版 YAML 解析，实际应使用 js-yaml
  // 这里用 JSON.parse 演示，生产环境需要完整 YAML 解析器
  try {
    return JSON.parse(content);
  } catch (e) {
    // fallback: 简单正则提取
    return {
      version: "1.0",
      roles: [],
      templates: []
    };
  }
}

// ==================== 状态管理 ====================

class TaskState {
  constructor(taskId, template, title) {
    this.taskId = taskId;
    this.template = template;
    this.title = title;
    this.status = 'pending'; // pending | running | paused | completed | failed
    this.currentStageIndex = 0;
    this.stages = [];
    this.createdAt = Date.now();
    this.updatedAt = Date.now();
  }

  updateStage(roleId, sessionId, status) {
    const stage = this.stages.find(s => s.role === roleId);
    if (stage) {
      stage.status = status;
      stage.sessionId = sessionId;
      stage.updatedAt = Date.now();
    } else {
      this.stages.push({
        role: roleId,
        sessionId,
        status,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        retryCount: 0
      });
    }
    this.updatedAt = Date.now();
  }

  nextStage() {
    this.currentStageIndex++;
  }

  getCurrentStage() {
    return this.stages[this.currentStageIndex];
  }
}

// ==================== Provider 注册 ====================

async function registerProviders(ctx, config) {
  const providers = {};

  for (const role of config.roles) {
    const provider = {
      name: role.id,
      async start(request) {
        const roleConfig = config.roles.find(r => r.id === role.id);
        
        // 创建子 Agent
        const agentOptions = {
          preset: role.id,
          systemPrompt: roleConfig.systemPrompt,
          tools: roleConfig.tools || []
        };

        const handle = await ctx.agents.create({
          sessionId: generateSessionId(),
          options: agentOptions
        });

        // 记录到 Task State
        const task = getCurrentTask(ctx);
        if (task) {
          task.updateStage(role.id, handle.sessionId, 'running');
        }

        return {
          sessionId: handle.sessionId,
          messages: [
            { role: 'system', content: roleConfig.systemPrompt },
            { role: 'user', content: request.task }
          ]
        };
      },

      async followup(parent, childId, content, options) {
        return await ctx.subagents.followup(parent, childId, content, options);
      },

      async listChildren(parentSessionId) {
        return await ctx.subagents.listChildren(parentSessionId);
      }
    };

    ctx.subagents.registerProvider(provider);
    providers[role.id] = provider;
  }

  return providers;
}

// ==================== 工具注册 ====================

function registerTools(ctx) {
  // /orchestrate:task
  ctx.tools.register({
    name: "orchestrate",
    description: "动态编排多角色工作流",
    inputSchema: {
      type: "object",
      properties: {
        action: {
          type: "string",
          enum: ["start", "status", "cancel", "retry", "takeOver"],
          description: "编排动作"
        },
        template: {
          type: "string",
          description: "流程模板名称（如 standard-feature）"
        },
        roles: {
          type: "array",
          description: "自定义角色序列（覆盖模板）",
          items: {
            type: "object",
            properties: {
              role: { type: "string" },
              task: { type: "string" }
            }
          }
        },
        title: {
          type: "string",
          description: "任务标题"
        },
        taskId: {
          type: "string",
          description: "现有任务ID"
        },
        sessionId: {
          type: "string",
          description: "子会话ID（用于 takeOver/retry）"
        }
      },
      required: ["action"]
    },
    async execute(input) {
      const { action } = input;

      switch (action) {
        case 'start':
          return await handleStart(ctx, input);
        case 'status':
          return await handleStatus(ctx, input);
        case 'cancel':
          return await handleCancel(ctx, input);
        case 'retry':
          return await handleRetry(ctx, input);
        case 'takeOver':
          return await handleTakeOver(ctx, input);
        default:
          throw new Error(`Unknown action: ${action}`);
      }
    }
  });

  // /orchestrate:status 快捷方式
  ctx.tools.register({
    name: "orchestrate:status",
    description: "查看编排任务状态",
    inputSchema: {
      type: "object",
      properties: {
        taskId: { type: "string", description: "任务ID" }
      },
      required: ["taskId"]
    },
    async execute({ taskId }) {
      return await handleStatus(ctx, { taskId });
    }
  });
}

// ==================== 事件监听 ====================

function registerEventListeners(ctx) {
  // 子 Agent 启动
  ctx.on('subagent/start', (info) => {
    const task = getCurrentTask(ctx);
    if (task && info.metadata?.role) {
      task.updateStage(info.metadata.role, info.sessionId, 'running');
    }
  });

  // 子 Agent 结束
  ctx.on('subagent/end', (info) => {
    const task = getCurrentTask(ctx);
    if (task && info.metadata?.role) {
      const stage = task.stages.find(s => s.role === info.metadata.role);
      if (stage) {
        stage.status = 'completed';
        stage.completedAt = Date.now();
      }
      
      // 推进到下一阶段
      task.nextStage();
      
      // 如果还有下一阶段，自动启动
      const nextStage = task.getCurrentStage();
      if (nextStage) {
        executeStage(ctx, task, nextStage);
      } else {
        task.status = 'completed';
      }
    }
  });

  // Agent 错误
  ctx.on('agent/error', (payload) => {
    const task = getCurrentTask(ctx);
    if (task && payload.metadata?.role) {
      const stage = task.stages.find(s => s.role === payload.metadata.role);
      if (stage) {
        stage.status = 'failed';
        stage.error = payload.error;
        stage.retryCount++;
      }
    }
  });
}

// ==================== Host API ====================

function registerHostApis(ctx) {
  // 获取子 Agent 树
  ctx.host.handle('orchestrator.getSubagentTree', async (taskId) => {
    const task = getTask(ctx, taskId);
    if (!task) return null;

    const children = [];
    for (const stage of task.stages) {
      if (stage.sessionId) {
        try {
          const session = await ctx.sessionQuery.readSession(stage.sessionId);
          children.push({
            role: stage.role,
            sessionId: stage.sessionId,
            status: stage.status,
            task: stage.task,
            lastActivity: session.updatedAt,
            canInterrupt: stage.status === 'running',
            canRestart: stage.status === 'failed',
            canTakeOver: stage.status === 'failed'
          });
        } catch (e) {
          children.push({
            role: stage.role,
            sessionId: stage.sessionId,
            status: stage.status,
            task: stage.task,
            lastActivity: stage.updatedAt
          });
        }
      }
    }

    return {
      taskId,
      title: task.title,
      status: task.status,
      currentStage: task.currentStageIndex,
      children
    };
  });

  // 人工接管
  ctx.host.handle('orchestrator.takeOver', async ({ taskId, sessionId }) => {
    await ctx.subagents.interrupt(sessionId, 'human-takeover');
    
    const task = getTask(ctx, taskId);
    if (task) {
      const stage = task.stages.find(s => s.sessionId === sessionId);
      if (stage) {
        stage.status = 'human_taken_over';
      }
    }
    
    return { success: true };
  });
}

// ==================== 主流程 ====================

async function executeStage(ctx, task, stage) {
  const provider = ctx.subagents.getProvider(stage.role);
  if (!provider) {
    throw new Error(`Provider not found: ${stage.role}`);
  }

  const result = await provider.start({
    task: stage.task,
    metadata: { role: stage.role, taskId: task.taskId }
  });

  stage.sessionId = result.sessionId;
  stage.status = 'running';
}

async function handleStart(ctx, input) {
  const config = await loadProjectConfig(ctx);
  const templateId = input.template || 'standard-feature';
  const template = config.templates.find(t => t.id === templateId);
  
  if (!template) {
    throw new Error(`Template not found: ${templateId}`);
  }

  const taskId = generateTaskId();
  const task = new TaskState(taskId, templateId, input.title || 'Untitled');
  
  // 初始化 stages
  for (const stageDef of template.stages) {
    task.stages.push({
      role: stageDef.role,
      task: stageDef.task,
      status: 'pending',
      retryCount: 0,
      maxAttempts: stageDef.retry?.maxAttempts || 3
    });
  }

  // 保存任务状态
  saveTask(ctx, task);

  // 启动第一个阶段
  const firstStage = task.getCurrentStage();
  if (firstStage) {
    await executeStage(ctx, task, firstStage);
    firstStage.status = 'running';
  }

  return {
    taskId,
    title: task.title,
    template: templateId,
    currentStage: task.currentStageIndex,
    totalStages: task.stages.length
  };
}

async function handleStatus(ctx, input) {
  const task = getTask(ctx, input.taskId);
  if (!task) return null;

  return {
    taskId: task.taskId,
    title: task.title,
    status: task.status,
    currentStage: task.currentStageIndex,
    totalStages: task.stages.length,
    stages: task.stages.map(s => ({
      role: s.role,
      status: s.status,
      sessionId: s.sessionId,
      retryCount: s.retryCount
    }))
  };
}

async function handleCancel(ctx, input) {
  // TODO: 实现取消逻辑
  return { success: true };
}

async function handleRetry(ctx, input) {
  const task = getTask(ctx, input.taskId);
  if (!task) return { success: false };

  const stage = task.stages.find(s => s.sessionId === input.sessionId);
  if (stage && stage.status === 'failed') {
    stage.retryCount++;
    await executeStage(ctx, task, stage);
    stage.status = 'running';
    return { success: true, retryCount: stage.retryCount };
  }

  return { success: false };
}

async function handleTakeOver(ctx, input) {
  const task = getTask(ctx, input.taskId);
  if (!task) return { success: false };

  const stage = task.stages.find(s => s.sessionId === input.sessionId);
  if (stage) {
    stage.status = 'human_taken_over';
  }

  return { success: true };
}

// ==================== 存储辅助 ====================

const taskStore = new Map();

function saveTask(ctx, task) {
  taskStore.set(task.taskId, task);
  // TODO: 持久化到 ctx.sessionPersistence
}

function getTask(ctx, taskId) {
  return taskStore.get(taskId);
}

function getCurrentTask(ctx) {
  // TODO: 从 ctx 获取当前任务
  return Array.from(taskStore.values()).pop();
}

function generateTaskId() {
  return 'task-' + Date.now() + '-' + Math.random().toString(36).substr(2, 5);
}

function generateSessionId() {
  return 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 5);
}

// ==================== Plugin 入口 ====================

module.exports = {
  apply(ctx) {
    let config = null;

    // 初始化时加载配置
    loadProjectConfig(ctx).then(c => {
      config = c;
      return registerProviders(ctx, config);
    }).then(() => {
      registerTools(ctx);
      registerEventListeners(ctx);
      registerHostApis(ctx);
      
      ctx.log('Orchestrator Plugin initialized');
    }).catch(err => {
      ctx.log('Orchestrator init failed: ' + err.message);
    });
  }
};
