<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="操作员名称" prop="operatorName">
        <el-input v-model="queryParams.operatorName" placeholder="请输入操作员名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="操作员编号" prop="operatorCode">
        <el-input v-model="queryParams.operatorCode" placeholder="请输入操作员编号" clearable style="width: 240px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="操作员状态" clearable style="width: 240px">
          <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['para:operator:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['para:operator:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['para:operator:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['para:operator:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="operatorList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="操作员编号" align="center" prop="operatorId" width="120" />
      <el-table-column label="操作员名称" align="center" prop="operatorName" :show-overflow-tooltip="true" />
      <el-table-column label="操作员编号" align="center" prop="operatorCode" :show-overflow-tooltip="true" />
      <el-table-column label="联系电话" align="center" prop="phone" width="120" />
      <el-table-column label="邮箱" align="center" prop="email" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-switch
            v-model="scope.row.status"
            active-value="0"
            inactive-value="1"
            @change="handleStatusChange(scope.row)"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="修改" placement="top">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['para:operator:edit']"></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['para:operator:remove']"></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改操作员对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form :model="form" :rules="rules" ref="operatorRef" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="操作员名称" prop="operatorName">
              <el-input v-model="form.operatorName" placeholder="请输入操作员名称" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作员编号" prop="operatorCode">
              <el-input v-model="form.operatorCode" placeholder="请输入操作员编号" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Operator">
// import { listOperator, getOperator, delOperator, addOperator, updateOperator, changeOperatorStatus } from "@/api/para/operator"

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = proxy.useDict("sys_normal_disable")

// 假数据
const mockData = ref([
  {
    operatorId: 1,
    operatorName: "张三",
    operatorCode: "OP001",
    phone: "13800138001",
    email: "zhangsan@example.com",
    status: "0",
    createTime: "2024-01-15 10:30:00",
    remark: "系统管理员"
  },
  {
    operatorId: 2,
    operatorName: "李四",
    operatorCode: "OP002",
    phone: "13800138002",
    email: "lisi@example.com",
    status: "0",
    createTime: "2024-01-16 11:20:00",
    remark: "普通操作员"
  },
  {
    operatorId: 3,
    operatorName: "王五",
    operatorCode: "OP003",
    phone: "13800138003",
    email: "wangwu@example.com",
    status: "1",
    createTime: "2024-01-17 14:15:00",
    remark: "临时操作员"
  },
  {
    operatorId: 4,
    operatorName: "赵六",
    operatorCode: "OP004",
    phone: "13800138004",
    email: "zhaoliu@example.com",
    status: "0",
    createTime: "2024-01-18 09:45:00",
    remark: ""
  },
  {
    operatorId: 5,
    operatorName: "孙七",
    operatorCode: "OP005",
    phone: "13800138005",
    email: "sunqi@example.com",
    status: "0",
    createTime: "2024-01-19 16:30:00",
    remark: "高级操作员"
  },
  {
    operatorId: 6,
    operatorName: "周八",
    operatorCode: "OP006",
    phone: "13800138006",
    email: "zhouba@example.com",
    status: "1",
    createTime: "2024-01-20 08:20:00",
    remark: ""
  },
  {
    operatorId: 7,
    operatorName: "吴九",
    operatorCode: "OP007",
    phone: "13800138007",
    email: "wujiu@example.com",
    status: "0",
    createTime: "2024-01-21 13:10:00",
    remark: "测试操作员"
  },
  {
    operatorId: 8,
    operatorName: "郑十",
    operatorCode: "OP008",
    phone: "13800138008",
    email: "zhengshi@example.com",
    status: "0",
    createTime: "2024-01-22 15:50:00",
    remark: ""
  },
  {
    operatorId: 9,
    operatorName: "钱一",
    operatorCode: "OP009",
    phone: "13800138009",
    email: "qianyi@example.com",
    status: "0",
    createTime: "2024-01-23 10:00:00",
    remark: "运维操作员"
  },
  {
    operatorId: 10,
    operatorName: "陈二",
    operatorCode: "OP010",
    phone: "13800138010",
    email: "chener@example.com",
    status: "1",
    createTime: "2024-01-24 11:30:00",
    remark: ""
  },
  {
    operatorId: 11,
    operatorName: "林三",
    operatorCode: "OP011",
    phone: "13800138011",
    email: "linsan@example.com",
    status: "0",
    createTime: "2024-01-25 14:20:00",
    remark: "数据分析员"
  },
  {
    operatorId: 12,
    operatorName: "黄四",
    operatorCode: "OP012",
    phone: "13800138012",
    email: "huangsi@example.com",
    status: "0",
    createTime: "2024-01-26 09:15:00",
    remark: ""
  }
])

const operatorList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    operatorName: undefined,
    operatorCode: undefined,
    status: undefined
  },
  rules: {
    operatorName: [
      { required: true, message: "操作员名称不能为空", trigger: "blur" },
      { min: 2, max: 50, message: "操作员名称长度必须介于 2 和 50 之间", trigger: "blur" }
    ],
    operatorCode: [
      { required: true, message: "操作员编号不能为空", trigger: "blur" },
      { min: 2, max: 50, message: "操作员编号长度必须介于 2 和 50 之间", trigger: "blur" }
    ],
    phone: [
      { pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, message: "请输入正确的手机号码", trigger: "blur" }
    ],
    email: [
      { type: "email", message: "请输入正确的邮箱地址", trigger: ["blur", "change"] }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询操作员列表 */
function getList() {
  loading.value = true
  // 模拟API延迟
  setTimeout(() => {
    // 过滤数据
    let filteredData = [...mockData.value]
    
    // 按操作员名称过滤
    if (queryParams.value.operatorName) {
      filteredData = filteredData.filter(item => 
        item.operatorName.includes(queryParams.value.operatorName)
      )
    }
    
    // 按操作员编号过滤
    if (queryParams.value.operatorCode) {
      filteredData = filteredData.filter(item => 
        item.operatorCode.includes(queryParams.value.operatorCode)
      )
    }
    
    // 按状态过滤
    if (queryParams.value.status !== undefined && queryParams.value.status !== '') {
      filteredData = filteredData.filter(item => 
        item.status === queryParams.value.status
      )
    }
    
    // 分页处理
    total.value = filteredData.length
    const start = (queryParams.value.pageNum - 1) * queryParams.value.pageSize
    const end = start + queryParams.value.pageSize
    operatorList.value = filteredData.slice(start, end)
    
    loading.value = false
  }, 300)
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 删除按钮操作 */
function handleDelete(row) {
  const operatorIds = row.operatorId || ids.value
  proxy.$modal.confirm('是否确认删除操作员编号为"' + operatorIds + '"的数据项？').then(function () {
    // 从假数据中删除
    if (Array.isArray(operatorIds)) {
      operatorIds.forEach(id => {
        const index = mockData.value.findIndex(item => item.operatorId === id)
        if (index > -1) {
          mockData.value.splice(index, 1)
        }
      })
    } else {
      const index = mockData.value.findIndex(item => item.operatorId === operatorIds)
      if (index > -1) {
        mockData.value.splice(index, 1)
      }
    }
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("para/operator/export", {
    ...queryParams.value,
  }, `operator_${new Date().getTime()}.xlsx`)
}

/** 操作员状态修改  */
function handleStatusChange(row) {
  let text = row.status === "0" ? "启用" : "停用"
  proxy.$modal.confirm('确认要"' + text + '""' + row.operatorName + '"操作员吗?').then(function () {
    // 更新假数据中的状态
    const item = mockData.value.find(item => item.operatorId === row.operatorId)
    if (item) {
      item.status = row.status
    }
    proxy.$modal.msgSuccess(text + "成功")
  }).catch(function () {
    row.status = row.status === "0" ? "1" : "0"
  })
}

/** 选择条数  */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.operatorId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 重置操作表单 */
function reset() {
  form.value = {
    operatorId: undefined,
    operatorName: undefined,
    operatorCode: undefined,
    phone: undefined,
    email: undefined,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("operatorRef")
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加操作员"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const operatorId = row.operatorId || ids.value[0]
  // 从假数据中获取
  const item = mockData.value.find(item => item.operatorId === operatorId)
  if (item) {
    form.value = { ...item }
    open.value = true
    title.value = "修改操作员"
  }
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["operatorRef"].validate(valid => {
    if (valid) {
      if (form.value.operatorId != undefined) {
        // 修改假数据
        const index = mockData.value.findIndex(item => item.operatorId === form.value.operatorId)
        if (index > -1) {
          mockData.value[index] = { ...form.value }
        }
        proxy.$modal.msgSuccess("修改成功")
        open.value = false
        getList()
      } else {
        // 新增假数据
        const newId = Math.max(...mockData.value.map(item => item.operatorId)) + 1
        const now = new Date()
        const year = now.getFullYear()
        const month = String(now.getMonth() + 1).padStart(2, '0')
        const day = String(now.getDate()).padStart(2, '0')
        const hours = String(now.getHours()).padStart(2, '0')
        const minutes = String(now.getMinutes()).padStart(2, '0')
        const seconds = String(now.getSeconds()).padStart(2, '0')
        const newOperator = {
          ...form.value,
          operatorId: newId,
          createTime: `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
        }
        mockData.value.push(newOperator)
        proxy.$modal.msgSuccess("新增成功")
        open.value = false
        getList()
      }
    }
  })
}

onMounted(() => {
  getList()
})
</script>

