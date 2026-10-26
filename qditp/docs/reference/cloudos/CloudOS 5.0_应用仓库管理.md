# CloudOS 5.0 应用仓库管理 API

> 本节选自 [CloudOS 5.0_PaaS_API_E7501.md](CloudOS%205.0_PaaS_API_E7501.md) §企业应用API，接口定义与原文一致；完整文档见同目录 PaaS 全文 Markdown。

**调用地址**：下表路径为**绝对路径**，完整 URL = `http://{平台IP}:11000` + 下表路径（Web 端口缺省 11000，见全文 §调用云平台 API）。请求头统一带 `Content-Type`、`Accept: application/json` 与 `X-Auth-Token`。

## 接口索引

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 查询应用包列表 | GET | `/api/cloud/deploy/v1.0/packages` | 获取应用包列表 |
| 查询应用包详情 | GET | `/api/cloud/deploy/v1.0/packages/{id}` | 查询应用包详情，id即为应用包ID |
| 上传应用包 | POST | `/api/cloud/deploy/v1.0/packages` | 上传应用包 |
| 通过远端地址上传应用包 | POST | `/api/cloud/deploy/v1.0/remotes` | 通过远端地址上传应用包 |
| 编辑应用包 | POST | `/api/cloud/deploy/v1.0/packages/{id}` | 编辑应用包，id为应用包ID |
| 删除应用包 | DELETE | `/api/cloud/deploy/v1.0/packages/{id}/batchDel` | 删除应用包，id为应用包ID |
| 获取空间清理配置 | GET | `/api/cloud/deploy/v1.0/retains` | 获取空间清理配置 |
| 空间清理配置 | POST | `/api/cloud/deploy/v1.0/retains` | 空间清理配置 |
| 发布应用包 | PUT | `/api/cloud/deploy/v1.0/packages/{id}/publish` | 私有仓库应用包发布至公有仓库，id为应用包ID |
| 编辑应用包版本 | PUT | `/api/cloud/deploy/v1.0/packages/version` | 编辑应用包版本 |
| 下载应用包 | GET | `/api/cloud/deploy/v1.0/package-files/{id}` | 根据应用包版本ID下载应用包，id即为应用包ID |
| 查询应用包标签列表 | GET | `/api/cloud/deploy/v1.0/tags` | 查询应用包标签列表 |
| 增加应用包标签 | POST | `/api/cloud/deploy/v1.0/tags` | 增加应用包标签 |
| 修改应用包标签 | PUT | `/api/cloud/deploy/v1.0/tags/{id}` | 修改应用包标签 |
| 删除分类 | DELETE | `/api/cloud/deploy/v1.0/tags/{id}` | 删除应用包分类 |

---


## 应用仓库

### 查询应用包列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages | JSON | GET | 获取应用包列表 |

##### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | integer | 当前页数 |
| size | True | integer | 每页的数量 |
| label | False | string | 应用包的显示名称 |
| tagId | False | string | 应用包ID |
| pkgType | False | string | 应用包的类型 |
| visible | False | boolean | 是否公有 |
| name | False | string | 应用包名称 |
| projectId | False | string | 组织ID |
| selectOwn | False | boolean | 是否筛选用户创建的应用包 |
| updated_at_sort | False | boolean | 是否根据创建时间的升序排序, 默认为 false |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用包列表信息 |
| total | Integer | 应用包个数 | 应用包总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"id": "975ba05a-5bd6-46da-bbae-2579f9d6ccef",<br>"name": "book-server",<br>"label": "book-server",<br>"description": "",<br>"logo": null,<br>"visible": true,<br>"tag": "springcloud",<br>"pkg_type": "jar",<br>"tag_id": "96ed073f-a205-4f5a-877f-00dd7a912256",<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2021-10-16 13:59:43",<br>"updated_at": "2021-10-16 13:59:43",<br>"user_name": "admin",<br>"project_name": "root",<br>"version_number": 1,<br>"used_space": null,<br>"versions": [<br>{<br>"id": "vJUMozMLUd",<br>"version": "v1",<br>"feature": "",<br>"source": "local",<br>"metadata": {<br>"file_url":"/opt/package-files/package/vJUMozMLUd/book-server-spring-cloud-0.0.1-SNAPSHOT.jar"<br>},<br>"visible": true,<br>"cleanTime": null,<br>"state": "used",<br>"stateDesc": null,<br>"collect": false,<br>"pkg_id": "975ba05a-5bd6-46da-bbae-2579f9d6ccef",<br>"file_name": "book-server-spring-cloud-0.0.1-SNAPSHOT.jar",<br>"file_size": "62.9 MiB",<br>"created_at": 1634363983000,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"is_collect": false,<br>"user_name": "admin",<br>"keep_time": null<br>}<br>]<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 查询应用包详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/{id\} | JSON | GET | 查询应用包详情，id即为应用包ID |

##### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用包详情信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "506e7dad-444e-48f3-95e1-fab27c1cb24c",<br>"name": "app2",<br>"label": "myapp2",<br>"pkg_type": "jar",<br>"tag_id": "3e7d4935-8c54-4b5b-a36c-516dde6384a7",<br>"tag": "jar",<br>"description": "",<br>"logo": null,<br>"visible": true,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2021-10-29 08:45:52",<br>"updated_at": null,<br>"user_name": null,<br>"project_name": "root",<br>"version_number": 0,<br>"used_space": "34.1 MiB",<br>"versions": [<br>{<br>"id": "9zaa2tbftc",<br>"version": "v3",<br>"feature": "",<br>"source": "local",<br>"metadata": {<br>"file_url": "/opt/package-files/package/9zaa2tbftc/spring-boot-web-0.0.1-SNAPSHOT-v2.jar"<br>},<br>"visible": true,<br>"cleanTime": null,<br>"state": "unused",<br>"stateDesc": null,<br>"collect": false,<br>"pkg_id": "506e7dad-444e-48f3-95e1-fab27c1cb24c",<br>"file_name": "spring-boot-web-0.0.1-SNAPSHOT-v2.jar",<br>"file_size": "34.1 MiB",<br>"created_at": 1635468971000,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"is_collect": false,<br>"user_name": "admin",<br>"keep_time": null<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 上传应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages | multipart/form-data | POST | 上传应用包 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |

##### 请求体(form-data)

| totalChunks | true | Integer | 应用包总片数 |
| --- | --- | --- | --- |
| chunkNumber | true | Integer | 当前分片序号 |
| id | ture | String | 应用包版本ID |
| pkgType | true | String | 应用包版本类型 |
| file | true | File | 应用包文件 |
| name | true | String | 应用包名称 |
| version | true | String | 应用包版本号 |
| visible | true | boolean | 是否上传至公有仓库 |
| label | False | String | 应用包的显示名称 |
| tagId | False | String | 应用包的标签ID |
| logo | False | File | 应用包logo |
| description | False | String | 应用包描述信息 |
| feature | False | String | 应用包版本特性 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用上传结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "string",<br>"code": "string",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 通过远端地址上传应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/remotes | multipart/form-data | POST | 通过远端地址上传应用包 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |

##### 请求体(form-data)

| id | ture | String | 应用包版本ID |
| --- | --- | --- | --- |
| pkgType | true | String | 应用包版本类型 |
| name | true | String | 应用包名称 |
| version | true | String | 应用包版本号 |
| visible | true | boolean | 是否上传至公有仓库 |
| label | False | String | 应用包的显示名称 |
| tagId | False | String | 应用包的标签ID |
| logo | False | File | 应用包logo |
| description | False | String | 应用包描述信息 |
| feature | False | String | 应用包版本特性 |
| remoteUri | true | String | 远端下载地址，支持http,ftp,sftp |
| usePwd | False | String | 是否使用密码 |
| username | False | String | 远端地址账号 |
| password | False | String | 远端地址密码 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用上传结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "string",<br>"code": "string",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 编辑应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/{id\} | multipart/form-data | POST | 编辑应用包，id为应用包ID |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |

##### 请求体（form-data）

| label | true | String | 应用包的显示名称 |
| --- | --- | --- | --- |
| tagId | true | String | 应用包标签 |
| description | False | String | 应用包描述信息 |
| logo | False | File | 应用包logo |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回修改结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 删除应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/{id}/batchDel | JSON | DELETE | 删除应用包，id为应用包ID |

##### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | False | String | 应用包ID |

##### 请求体

| versionId | true | String | 数组中对象字段，版本id |
| --- | --- | --- | --- |
| forced | true | Boolean | 数组中对象字段，是否强制删除 |
| 例如：<br>[{"versionId":"T96FG97cX6","forced":true}<br>,{"versionId":"3L447pD72L","forced":true},<br>{"versionId":"5uaAU8kGfc","forced":true}] |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常全部删除成功情况下，code为空串 |
| msg | String | 错误信息 | 正常全部删除成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回删除应用包的结果信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "appinfo:v1",<br>"code": "",<br>"data": {<br>"version_number": 0<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 获取空间清理配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/retains | JSON | GET | 获取空间清理配置 |

##### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 组织id |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 空间清理配置的信息 | 空间清理配置的信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"create_by": "admin",<br>"created_at": "2020-07-30 22:50:14",<br>"enabled": false,<br>"id": "4706d829-e920-4b8f-9273-d350e0b297dd",<br>"inherit": false,<br>"last_modified_at": "2020-07-30 09:16:44",<br>"last_modified_by": "admin",<br>"parent_id": "default",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"retainDays": 15<br>},<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 空间清理配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/retains | JSON | POST | 空间清理配置 |

##### 请求体

| RequestBody | 字段类型 | 含义 | 说明 |
| --- | --- | --- | --- |
| inherit | Boolean | 是否继承 | 是否继承父组织 |
| enabled | Boolean | 是否启用配置 | 是否启用配置 |
| project_id | String | 组织id | 组织id |
| retainDays | Int | 保留天数 | 保留天数 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回配置结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"create_by": "admin",<br>"created_at": "2020-07-30 22:50:14",<br>"enabled": true,<br>"id": "4706d829-e920-4b8f-9273-d350e0b297dd",<br>"inherit": false,<br>"last_modified_at": "2020-09-11 19:11:13",<br>"last_modified_by": "admin",<br>"parent_id": "default",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"retainDays": 100<br>},<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 发布应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/{id}/publish | JSON | PUT | 私有仓库应用包发布至公有仓库，id为应用包ID |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| versionIds | ture | String | 应用包版本ID |
| apply | true | boolean | 是否需要审批 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 发布成功的信息 | 发布成功 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"f1206312-09eb-449c-9d99-74c47138f86a"<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 编辑应用包版本

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/version | JSON | PUT | 编辑应用包版本 |

##### 参数说明

| Payload参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| versionId | ture | String | 应用包版本ID |
| version | true | String | 应用包版本 |
| feature | true | String | 应用包特性 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回修改结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "string",<br>"code": "string",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 下载应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/package-files/{id\} | octet-stream | GET | 根据应用包版本ID下载应用包，id即为应用包ID |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：文件流 |  |  |  |

### 查询应用包标签列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/tags | JSON | GET | 查询应用包标签列表 |

##### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | integer | 当前页数 |
| size | True | integer | 每页数量 |
| tag | False | String | 标签名称 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回查询结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"status": true,<br>"total": 1,<br>"data": [<br>{<br>"id": "124343543654657",<br>"tag": "SERVER"<br>}<br>]<br>\} |  |  |  |

### 增加应用包标签

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/tags | JSON | POST | 增加应用包标签 |

##### 参数说明

| body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| tagName | True | String | Body里直接传递增加的标签名称 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回增加结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"status": true,<br>"total": 1,<br>"data": {<br>"id": "124343543654657",<br>"tag": "SERVER"<br>}<br>\} |  |  |  |

### 修改应用包标签

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/tags/{id\} | JSON | PUT | 修改应用包标签 |

##### 参数说明

| Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| tag | True | String | Body里直接传递修改后的标签名称 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回修改结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"status": true,<br>"total": 1,<br>"data": {<br>"tag": "SERVER2"<br>}<br>\} |  |  |  |

### 删除分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/tags/{id\} | JSON | DELETE | 删除应用包分类 |

##### 参数说明

| Path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 待删除的应用包分类id |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回删除结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"328112f0-dc7a-4767-8bfd-1374cd936a5c"<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |
