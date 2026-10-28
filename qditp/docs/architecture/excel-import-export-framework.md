# 通用 Excel 导入导出框架

## 概述

本项目提供了一套通用的 Excel 导入导出框架，基于 RuoYi 的 `ExcelUtil`（Apache POI）。
业务模块只需实现 `IImportExportHandler` 接口并注册到 `BizRouter`，即可通过统一的
REST 接口完成导入、导出和模板下载，无需每个业务重复编写样板代码。

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                    CommonImporterController                  │
│  POST /common/importer/export?biz=<bizCode>                 │
│  POST /common/importer/importData?biz=<bizCode>&file=...    │
│  GET  /common/importer/importTemplate?biz=<bizCode>         │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │    BizRouter     │
                    │  bizCode → Handler│
                    └────────┬────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │     IImportExportHandler<T>   │
              │  - getEntityClass()           │
              │  - getEntityLabel(biz)        │
              │  - getExportList(biz, query)  │
              │  - handle(biz, list, ...)     │
              └──────────────────────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │     ExcelUtil<T> (POI)        │
              │  - 解析 @Excel 注解           │
              │  - 字典转换、日期格式化        │
              │  - 流式导出（SXSSFWorkbook）  │
              └──────────────────────────────┘
              ┌──────────────────────────────┐
              │   BizImportRecord (DB)        │
              │  - 导入批次号（UUID 幂等键）   │
              │  - 成功/失败统计              │
              │  - 错误摘要                   │
              └──────────────────────────────┘
```

## 文件存储

上传的 Excel 文件保存在服务器本地目录：
```
{points.profile}/import/{bizCode}/{datePath}/{batchNo}_{seq}.{ext}
```

- 默认 `points.profile=/home`，导入目录为 `/home/import/`
- 按业务分组：`/home/import/someBiz/2026-10-27/xxx.xlsx`
- 数据库 `biz_import_record.file_url` 存储相对路径（如 `/profile/import/someBiz/...`）
- 可通过 `GET /common/importer/download?fileUrl=...` 下载原文件复查

## 快速接入

### 1. 定义实体类（带 @Excel 注解）

```java
package com.chinasofti.huateng.somebiz.model;

import com.chinasofti.huateng.common.annotation.Excel;
import com.chinasofti.huateng.common.core.domain.BaseEntity;

public class SomeBizEntity extends BaseEntity {
    @Excel(name = "编号", type = Excel.Type.IMPORT)
    private String bizNo;

    @Excel(name = "姓名", required = true)
    private String name;

    @Excel(name = "状态", dictType = "sys_normal_disable")
    private String status;

    @Excel(name = "金额", type = Excel.Type.EXPORT)  // 仅导出
    private BigDecimal amount;

    // getters/setters...
}
```

### 2. 实现 Handler

```java
package com.chinasofti.huateng.somebiz.handler;

import java.util.List;
import org.springframework.stereotype.Component;
import com.chinasofti.huateng.web.controller.importer.IImportExportHandler;
import com.chinasofti.huateng.somebiz.model.SomeBizEntity;
import com.chinasofti.huateng.somebiz.service.ISomeBizService;

@Component
public class SomeBizImportHandler implements IImportExportHandler<SomeBizEntity> {

    @Autowired
    private ISomeBizService someBizService;

    @Override
    public Class<SomeBizEntity> getEntityClass() {
        return SomeBizEntity.class;
    }

    @Override
    public String getEntityLabel(String biz) {
        return "某业务数据";
    }

    @Override
    public List<SomeBizEntity> getExportList(String biz, String queryJson) throws Exception {
        // 解析 queryJson，查询数据库，返回待导出列表
        return someBizService.selectSomeBizList(...);
    }

    @Override
    public String handle(String biz, List<SomeBizEntity> list, boolean updateSupport, String operator) {
        // 批量插入/更新业务数据
        // 涉及支付/订单链路的，此处 MUST 做幂等校验
        int success = someBizService.batchInsertOrUpdate(list, updateSupport, operator);
        return String.format("成功 %d 条", success);
    }
}
```

### 3. 注册到 BizRouter

```java
package com.chinasofti.huateng.somebiz.config;

import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.chinasofti.huateng.web.controller.importer.BizRouter;
import com.chinasofti.huateng.somebiz.handler.SomeBizImportHandler;

@Component
public class SomeBizInitializer {

    @Autowired
    private BizRouter bizRouter;

    @Autowired
    private SomeBizImportHandler someBizHandler;

    @PostConstruct
    public void init() {
        bizRouter.register("someBiz", someBizHandler);
    }
}
```

## Web 端调用约定

| 操作 | 方法 | 路径 | 参数 |
|------|------|------|------|
| 导出 | POST | `/common/importer/export` | `biz`（必填）、`queryJson`（可选） |
| 导入 | POST | `/common/importer/importData` | `biz`（必填）、`file`（multipart）、`updateSupport`（可选，默认 false） |
| 模板 | GET | `/common/importer/importTemplate` | `biz`（必填） |

### 导入响应格式

```json
{
  "code": 200,
  "msg": "成功 977 条，失败 23 条",
  "data": {
    "failRows": 23,
    "errorMessages": [
      "第23行: 手机号格式错误",
      "第56行: 姓名不能为空"
    ]
  }
}
```

### Vue 前端示例

```vue
<template>
  <div>
    <!-- 导出按钮 -->
    <el-button type="primary" @click="handleExport">导出</el-button>

    <!-- 导入区域 -->
    <el-upload
      action="/common/importer/importData"
      :data="{ biz: 'someBiz', updateSupport: false }"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      accept=".xlsx,.xls"
    >
      <el-button type="success">导入</el-button>
    </el-upload>

    <!-- 下载模板 -->
    <el-button @click="handleDownloadTemplate">下载模板</el-button>
  </div>
</template>

<script setup>
import request from '@/utils/request'

const handleExport = () => {
  request({
    url: '/common/importer/export',
    method: 'post',
    params: { biz: 'someBiz', queryJson: JSON.stringify(queryParams) },
    responseType: 'blob'
  }).then(response => {
    // 触发浏览器下载
    const url = window.URL.createObjectURL(new Blob([response]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '某业务数据.xlsx')
    link.click()
  })
}

const handleUploadSuccess = (res) => {
  if (res.code === 200) {
    ElMessage.success(res.msg)
  } else {
    ElMessage.error(res.msg)
  }
}

const handleDownloadTemplate = () => {
  window.location.href = `/common/importer/importTemplate?biz=someBiz`
}
</script>
```

## 导入记录表（biz_import_record）

| 字段 | 类型 | 说明 |
|------|------|------|
| `import_id` | NUMBER(20) | 主键（SEQUENCE） |
| `biz_code` | VARCHAR2(64) | 业务标识（对应 BizRouter key） |
| `batch_no` | VARCHAR2(64) | **UUID 批次号（幂等键）** |
| `file_name` | VARCHAR2(255) | 原始文件名 |
| `file_size` | NUMBER(20) | 文件大小（字节） |
| `import_by` | VARCHAR2(64) | 操作人 |
| `client_ip` | VARCHAR2(128) | 操作人 IP |
| `total_rows` | NUMBER(10) | 总行数（不含表头） |
| `success_rows` | NUMBER(10) | 成功条数 |
| `fail_rows` | NUMBER(10) | 失败条数 |
| `status` | CHAR(1) | **0处理中 1成功 2失败** |
| `error_msg` | VARCHAR2(2000) | 错误摘要 |
| `create_time` | DATE | 创建时间 |
| `finish_time` | DATE | 完成时间 |

**索引：**
- `idx_import_batch_no`（GLOBAL）—— 幂等查询、按批次追溯
- `idx_import_biz_code` —— 按业务筛选
- `idx_import_create_time` —— 时间范围查询

## 安全红线（AGENTS.md §4.2）

涉及以下链路的 biz，handler 内部 **MUST** 做幂等兜底：
- 支付
- 订单
- 扣款
- 签约
- 开户
- 卡池
- 对账

**幂等实现方式：**
1. DB 唯一索引 + `DuplicateKeyException` 捕获处理
2. 网络层重试已由 `ProxyWebClient.isNetworkRetryEnabled()=false` 关闭

## 权限控制

本框架不在控制器层面加 `@PreAuthorize`，权限由以下方式之一控制：
1. 前端菜单 SQL（`sys_menu`）配置接口权限
2. Handler 内部业务校验
3. 子类覆盖控制器方法并添加 `@PreAuthorize`

## 现有范例

- `SysUserController`：用户管理的导入导出（直接写法，非框架）
- 本框架适用于新增业务模块，避免重复代码

## 文件位置

| 文件 | 路径 |
|------|------|
| 通用控制器 | `web-server/web-admin/src/main/java/.../controller/importer/CommonImporterController.java` |
| Handler 接口 | `web-server/web-admin/src/main/java/.../controller/importer/IImportExportHandler.java` |
| 路由注册表 | `web-server/web-admin/src/main/java/.../controller/importer/BizRouter.java` |
| 导入记录实体 | `web-server/web-admin/src/main/java/.../domain/importer/BizImportRecord.java` |
| 导入记录 Mapper | `web-server/web-admin/src/main/java/.../mapper/importer/BizImportRecordMapper.java` |
| 导入记录 Service | `web-server/web-admin/src/main/java/.../service/importer/` |
| Excel 工具类 | `web-server/web-common/src/main/java/.../utils/poi/ExcelUtil.java` |
| Excel 注解 | `web-server/web-common/src/main/java/.../annotation/Excel.java` |
| DDL SQL | `web-server/sql/biz-import-record.sql` |
