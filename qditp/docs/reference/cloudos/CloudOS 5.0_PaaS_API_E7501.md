## 概述

云平台API（Application Programming Interface，应用程序编程接口）是一组预先定义的功能函数，用户通过调用API即可使用相应的应用程序功能而无需理解其内部的工作细节。

云平台 API 是REST风格API，用户可通过调用API将云平台提供的各项功能与既有系统或平台进行整合。

### REST API构成

采用REST风格的API也称为REST API，一个完整的REST API由以下几部分构成：

- HTTP方法（HTTP Method）
- 统一资源标识符（Uniform Resource Identifer，URI）
- 请求消息（Request）
- 响应消息（Response）

其中请求消息和响应消息又包含消息头（Header）和消息体（Body）。客户端在调用API时会先根据API中约定的HTTP方法、URI和请求消息向云平台发送HTTP请求；云平台在收到请求后对其进行处理，然后根据API中的约定的响应消息发送HTTP响应；客户端在收到云平台返回的响应消息后解析其中的内容。常用消息格式包括XML和JSON等，云平台 API目前仅支持JSON格式的消息。

### 调用云平台 API

用户可通过标准的REST API客户端对云平台 API进行调用。标准的REST API客户端包括多种，常见的如cURL、REST Client（Firefox）、REST Client（Chrome），调用API时发送的HTTP请求形如：http://{ ip address}:{port}/{URI}，各部分的含义如下：

- {ip address}：部署云平台的指定的IP地址。
- {port}：部署云平台的指定的Web端口地址，缺省为11000。
- {URI}：API对应的URI。

## 公用部分介绍

### 2.1请求消息头

在云平台 API中，不同API的请求消息头和响应消息头大致相同，本章将对常见的请求消息头和响应消息头进行介绍。

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | 指定请求内容类型，一般为：application/json。 |
| Accept | true | String | 指定响应内容类型，一般为：application/json。对于无响应消息体的请求，请求消息头不能含有该字段。 |
| X-Auth-Token | true | String | 调用云平台各API时，请求消息头中需含有的经过认证的令牌。 |

### 2.2请求错误返回响应体

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 错误码 |
| msg | String | 错误信息 | 错误信息 |
| data | Object | null | 返回空 |
| total | Integer | 0 | 返回0个 |
| 例如：<br>{<br>"code": " ",<br>"msg": "",<br>"data": null,<br>"total": 0<br>\} |  |  |  |

//错误码对应信息见错误码说明附件。（错误码表后续提供）

## 企业应用API

### 应用仓库

#### 查询应用包列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages | JSON | GET | 获取应用包列表 |

###### 参数说明

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

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用包列表信息 |
| total | Integer | 应用包个数 | 应用包总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"id": "975ba05a-5bd6-46da-bbae-2579f9d6ccef",<br>"name": "book-server",<br>"label": "book-server",<br>"description": "",<br>"logo": null,<br>"visible": true,<br>"tag": "springcloud",<br>"pkg_type": "jar",<br>"tag_id": "96ed073f-a205-4f5a-877f-00dd7a912256",<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2021-10-16 13:59:43",<br>"updated_at": "2021-10-16 13:59:43",<br>"user_name": "admin",<br>"project_name": "root",<br>"version_number": 1,<br>"used_space": null,<br>"versions": [<br>{<br>"id": "vJUMozMLUd",<br>"version": "v1",<br>"feature": "",<br>"source": "local",<br>"metadata": {<br>"file_url":"/opt/package-files/package/vJUMozMLUd/book-server-spring-cloud-0.0.1-SNAPSHOT.jar"<br>},<br>"visible": true,<br>"cleanTime": null,<br>"state": "used",<br>"stateDesc": null,<br>"collect": false,<br>"pkg_id": "975ba05a-5bd6-46da-bbae-2579f9d6ccef",<br>"file_name": "book-server-spring-cloud-0.0.1-SNAPSHOT.jar",<br>"file_size": "62.9 MiB",<br>"created_at": 1634363983000,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"is_collect": false,<br>"user_name": "admin",<br>"keep_time": null<br>}<br>]<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询应用包详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/{id\} | JSON | GET | 查询应用包详情，id即为应用包ID |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用包详情信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "506e7dad-444e-48f3-95e1-fab27c1cb24c",<br>"name": "app2",<br>"label": "myapp2",<br>"pkg_type": "jar",<br>"tag_id": "3e7d4935-8c54-4b5b-a36c-516dde6384a7",<br>"tag": "jar",<br>"description": "",<br>"logo": null,<br>"visible": true,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2021-10-29 08:45:52",<br>"updated_at": null,<br>"user_name": null,<br>"project_name": "root",<br>"version_number": 0,<br>"used_space": "34.1 MiB",<br>"versions": [<br>{<br>"id": "9zaa2tbftc",<br>"version": "v3",<br>"feature": "",<br>"source": "local",<br>"metadata": {<br>"file_url": "/opt/package-files/package/9zaa2tbftc/spring-boot-web-0.0.1-SNAPSHOT-v2.jar"<br>},<br>"visible": true,<br>"cleanTime": null,<br>"state": "unused",<br>"stateDesc": null,<br>"collect": false,<br>"pkg_id": "506e7dad-444e-48f3-95e1-fab27c1cb24c",<br>"file_name": "spring-boot-web-0.0.1-SNAPSHOT-v2.jar",<br>"file_size": "34.1 MiB",<br>"created_at": 1635468971000,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"is_collect": false,<br>"user_name": "admin",<br>"keep_time": null<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 上传应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages | multipart/form-data | POST | 上传应用包 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |

###### 请求体(form-data)

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

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用上传结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "string",<br>"code": "string",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 通过远端地址上传应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/remotes | multipart/form-data | POST | 通过远端地址上传应用包 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |

###### 请求体(form-data)

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

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用上传结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "string",<br>"code": "string",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 编辑应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/{id\} | multipart/form-data | POST | 编辑应用包，id为应用包ID |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |

###### 请求体（form-data）

| label | true | String | 应用包的显示名称 |
| --- | --- | --- | --- |
| tagId | true | String | 应用包标签 |
| description | False | String | 应用包描述信息 |
| logo | False | File | 应用包logo |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回修改结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/{id}/batchDel | JSON | DELETE | 删除应用包，id为应用包ID |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | False | String | 应用包ID |

###### 请求体

| versionId | true | String | 数组中对象字段，版本id |
| --- | --- | --- | --- |
| forced | true | Boolean | 数组中对象字段，是否强制删除 |
| 例如：<br>[{"versionId":"T96FG97cX6","forced":true}<br>,{"versionId":"3L447pD72L","forced":true},<br>{"versionId":"5uaAU8kGfc","forced":true}] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常全部删除成功情况下，code为空串 |
| msg | String | 错误信息 | 正常全部删除成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回删除应用包的结果信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "appinfo:v1",<br>"code": "",<br>"data": {<br>"version_number": 0<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 获取空间清理配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/retains | JSON | GET | 获取空间清理配置 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 组织id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 空间清理配置的信息 | 空间清理配置的信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"create_by": "admin",<br>"created_at": "2020-07-30 22:50:14",<br>"enabled": false,<br>"id": "4706d829-e920-4b8f-9273-d350e0b297dd",<br>"inherit": false,<br>"last_modified_at": "2020-07-30 09:16:44",<br>"last_modified_by": "admin",<br>"parent_id": "default",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"retainDays": 15<br>},<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 空间清理配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/retains | JSON | POST | 空间清理配置 |

###### 请求体

| RequestBody | 字段类型 | 含义 | 说明 |
| --- | --- | --- | --- |
| inherit | Boolean | 是否继承 | 是否继承父组织 |
| enabled | Boolean | 是否启用配置 | 是否启用配置 |
| project_id | String | 组织id | 组织id |
| retainDays | Int | 保留天数 | 保留天数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回配置结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"create_by": "admin",<br>"created_at": "2020-07-30 22:50:14",<br>"enabled": true,<br>"id": "4706d829-e920-4b8f-9273-d350e0b297dd",<br>"inherit": false,<br>"last_modified_at": "2020-09-11 19:11:13",<br>"last_modified_by": "admin",<br>"parent_id": "default",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"retainDays": 100<br>},<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 发布应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/{id}/publish | JSON | PUT | 私有仓库应用包发布至公有仓库，id为应用包ID |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| versionIds | ture | String | 应用包版本ID |
| apply | true | boolean | 是否需要审批 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 发布成功的信息 | 发布成功 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"f1206312-09eb-449c-9d99-74c47138f86a"<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 编辑应用包版本

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/packages/version | JSON | PUT | 编辑应用包版本 |

###### 参数说明

| Payload参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| versionId | ture | String | 应用包版本ID |
| version | true | String | 应用包版本 |
| feature | true | String | 应用包特性 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回修改结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "string",<br>"code": "string",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 下载应用包

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/package-files/{id\} | octet-stream | GET | 根据应用包版本ID下载应用包，id即为应用包ID |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：文件流 |  |  |  |

#### 查询应用包标签列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/tags | JSON | GET | 查询应用包标签列表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | integer | 当前页数 |
| size | True | integer | 每页数量 |
| tag | False | String | 标签名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回查询结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"status": true,<br>"total": 1,<br>"data": [<br>{<br>"id": "124343543654657",<br>"tag": "SERVER"<br>}<br>]<br>\} |  |  |  |

#### 增加应用包标签

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/tags | JSON | POST | 增加应用包标签 |

###### 参数说明

| body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| tagName | True | String | Body里直接传递增加的标签名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回增加结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"status": true,<br>"total": 1,<br>"data": {<br>"id": "124343543654657",<br>"tag": "SERVER"<br>}<br>\} |  |  |  |

#### 修改应用包标签

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/tags/{id\} | JSON | PUT | 修改应用包标签 |

###### 参数说明

| Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| tag | True | String | Body里直接传递修改后的标签名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回修改结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"status": true,<br>"total": 1,<br>"data": {<br>"tag": "SERVER2"<br>}<br>\} |  |  |  |

#### 删除分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/deploy/v1.0/tags/{id\} | JSON | DELETE | 删除应用包分类 |

###### 参数说明

| Path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 待删除的应用包分类id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回删除结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"328112f0-dc7a-4767-8bfd-1374cd936a5c"<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 应用组

#### 查询应用组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appgroups | JSON | GET | 获取应用组列表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| filterByPrivilege | False | String，可选controller | 表示选择是否有使用权限以上的应用组列表 |
| projectId | False | String | 可以通过projectId筛选应用组 |
| name | False | String | 可以通过name筛选应用组 |
| page | True | integer | 当前页数 |
| pageSize | True | integer | 每页的数量 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回公有镜像信息 |
| total | Integer | 应用组个数 | 应用组总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"msg": "",<br>"data": [<br>{<br>"uuid": "d2402ddf-772a-45f4-a6c5-da29315661d2",<br>"name": "test13",<br>"namespace": "spaceahu2vdxy",<br>"status": "stopped",<br>"icon": null,<br>"type": "Spring Cloud",<br>"apps": {<br>"data": [<br>{<br>"uuid": "db9a054c1967484e914181129c26ad30",<br>"name": null,<br>"version": null,<br>"metadata": null,<br>"cluster_id": null,<br>"appgroup": null,<br>"app_group_id": "d2402ddf-772a-45f4-a6c5-da29315661d2",<br>"description": "test13",<br>"status": "Stopped",<br>"resource_type": "vm",<br>"instance_num": "1",<br>"instance_ok_num": null,<br>"package_type": null,<br>"package_url": null,<br>"external_endpoint": null,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2020-01-04 17:52:51",<br>"updated_at": "2020-01-06 15:34:20",<br>"azone_id": null,<br>"upgrading_versions": null<br>}<br>],<br>"metadata": {<br>"count": 1,<br>"ok_count": 0,<br>"stopped_count": 1,<br>"error_count": 0<br>}<br>},<br>"privileges": [<br>{<br>"id": "1",<br>"operation": "paas.app.group.owner",<br>"name": "owner"<br>}<br>],<br>"svc_engine_id": "jg5ytzn3yoi8sdb9bj5t",<br>"engine_cluster_id": "123456",<br>"engine_name": "spring-cloud-test",<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"created_at": "2020-01-04 17:49:58",<br>"updated_at": "2020-01-04 17:49:58",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"continuous_deployment": false<br>}<br>],<br>"total": 1<br>\} |  |  |  |

#### 查询应用组详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appgroups/{appgroup_uuid\} | JSON | GET | 查询应用组详情 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | true | String | 应用组uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回镜像详情信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"msg": "",<br>"status": true,<br>"data": {<br>"uuid": "d2402ddf-772a-45f4-a6c5-da29315661d2",<br>"name": "test13",<br>"namespace": "spaceahu2vdxy",<br>"status": "stopped",<br>"icon": null,<br>"type": "Spring Cloud",<br>"apps": {<br>"data": [<br>{<br>"uuid": "72bad1916cda4063ba6cefd4d1695a9e",<br>"name": null,<br>"version": "v4",<br>"metadata": null,<br>"cluster_id": "123456",<br>"appgroup": "测试应用组1",<br>"app_group_id": "5e82539d-4a01-45c4-986f-c0b156e7dd2a",<br>"description": "",<br>"status": "OK",<br>"resource_type": "container",<br>"instance_num": "1",<br>"instance_ok_num": null,<br>"package_type": "jar",<br>"external_endpoint": [],<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2020-01-04 17:52:51",<br>"updated_at": "2020-01-06 15:34:20",<br>"azone_id": null,<br>"upgrading_versions": null<br>}<br>],<br>"metadata": {<br>"count": 1,<br>"ok_count": 0,<br>"stopped_count": 1,<br>"error_count": 0<br>}<br>},<br>"privileges": [<br>{<br>"id": "1",<br>"operation": "paas.app.group.owner",<br>"name": "owner"<br>}<br>],<br>"svc_engine_id": "jg5ytzn3yoi8sdb9bj5t",<br>"engine_cluster_id": "123456",<br>"engine_name": "spring-cloud-test",<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"created_at": "2020-01-04 17:49:58",<br>"updated_at": "2020-01-04 17:49:58",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"continuous_deployment": false<br>},<br>"total": 1<br>\} |  |  |  |

#### 创建应用组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appgroups | JSON | POST | 创建应用组 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 请求体

| name | true | String | 应用组名称 |
| --- | --- | --- | --- |
| type | true | String | 应用组类型 |
| svc_engine_id | ture | String | 应用引擎ID |
| namespace | false | String | 自定义命名空间时，命名空间的值 |
| 例如：<br>{<br>"name": "group-test",<br>"type": "Spring Cloud",<br>"svc_engine_id": "dve2xoly3pumdnvfntzi",<br>"namespace": "xxxfsadf"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 一般为空 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"uuid": "52918847-dde6-4ed4-9fb7-09f2cf92b90d",<br>"name": "group-test",<br>"namespace": "space8m9elwbz",<br>"status": "empty",<br>"icon": null,<br>"projectName": null,<br>"type": "Spring Cloud",<br>"apps": null,<br>"privileges": null,<br>"svc_engine_id": "dve2xoly3pumdnvfntzi",<br>"engine_cluster_id": null,<br>"engine_name": null,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"created_at": "2021-10-29 15:32:12",<br>"updated_at": "2021-10-29 15:32:12",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"continuous_deployment": false<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除应用组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appgroups/{appgroup_uuid\} | JSON | DELETE | 删除应用组 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | true | String | 应用组ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回删除结果 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"uuid": "52918847-dde6-4ed4-9fb7-09f2cf92b90d",<br>"name": "group-test",<br>"namespace": "space8m9elwbz",<br>"status": "running",<br>"icon": null,<br>"metadata": null,<br>"type": "Spring Cloud",<br>"localPrivileges": "1",<br>"svc_engine_id": "dve2xoly3pumdnvfntzi",<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"created_at": "2021-10-29 15:32:12",<br>"updated_at": "2021-10-29 15:32:12",<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"continuous_deployment": false<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 启动/停止应用组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appgroups/{appgroup_uuid\} | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| action | ture | Enum(string) | 包括start, stop |
| appgourp_uuid | true | string | 应用组uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 一般为空 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询权限列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/privilege | JSON | GET | 查询权限列表 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 其中为每一项的权限 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"id": "2",<br>"operation": "paas.app.group.controller",<br>"name": "controller"<br>},<br>{<br>"id": "3",<br>"operation": "paas.app.group.reader",<br>"name": "reader"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询应用组权限列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/privilege/{appgroup_uuid\} | JSON | GET | 查询应用组权限 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | 是 | String | 应用组id |
| page | 是 | Int | 页数 |
| size | 是 | Int | 每页个数 |
| userName | 否 | String | 用户名称 |
| privilegeId | 否 | String | 权限Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 用户与权限列表信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"userId": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"privileges": [<br>{<br>"id": "1",<br>"operation": "paas.app.group.owner",<br>"name": "owner"<br>}<br>]<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 新增应用组用户权限

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/privilege/{appgroup_uuid\} | JSON | POST | 新增应用组用户权限 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | 是 | String | 应用组id |
| userList | 是 | List< String> | 用户id列表 |
| privileges | 是 | List< Object> | 具体权限 |
| 例如：<br>{<br>"userList": [<br>"4a0e5dabfcd74aaf85de98878d4a04bf",<br>"88b93190e9f74e4da196b728c6ff41d1"<br>],<br>"privileges": [<br>{<br>"id": "2",<br>"operation": "paas.app.group.controller",<br>"name": "controller"<br>},<br>{<br>"id": "3",<br>"operation": "paas.app.group.reader",<br>"name": "reader"<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 新增成功为列表 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"userList": [<br>"e2e36d70936d4f80887af31badb70b54"<br>],<br>"privileges": [<br>{<br>"id": "2",<br>"operation": "paas.app.group.controller",<br>"name": "controller"<br>},<br>{<br>"id": "3",<br>"operation": "paas.app.group.reader",<br>"name": "reader"<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 编辑应用组用户权限

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/privilege/{appgroup_uuid\} | JSON | PUT | 编辑应用组用户权限 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | 是 | String | 应用组id |
| userList | 是 | List< String> | 用户id列表 |
| privileges | 是 | List< Object> | 具体权限 |
| 例如：<br>{<br>"userList": [<br>"e2e36d70936d4f80887af31badb70b54"<br>],<br>"privileges": [<br>{<br>"id": "3",<br>"operation": "paas.app.group.reader",<br>"name": "reader"<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 编辑成功为列表 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"userList": [<br>"e2e36d70936d4f80887af31badb70b54"<br>],<br>"privileges": [<br>{<br>"id": "3",<br>"operation": "paas.app.group.reader",<br>"name": "reader"<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除应用组用户权限

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/privilege/{appgroup_uuid}/remove | JSON | POST | 删除应用组用户权限 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | 是 | String | 应用组id |
| userId | 是 | String | 用户id |
| userName | 是 | String | 用户名称 |
| privileges | 是 | List< Object> | 具体权限 |
| index | 是 | Integer | 序号 |
| 例如：<br>[<br>{<br>"userId": "4a0e5dabfcd74aaf85de98878d4a04bf",<br>"userName": "locker",<br>"privileges": [<br>{<br>"id": "2",<br>"operation": "paas.app.group.controller",<br>"name": "controller"<br>},<br>{<br>"id": "3",<br>"operation": "paas.app.group.reader",<br>"name": "reader"<br>}<br>],<br>"index": 1<br>}<br>] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回操作成功等的列表 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>{<br>"userId": "4a0e5dabfcd74aaf85de98878d4a04bf",<br>"userName": "locker",<br>"privileges": [<br>{<br>"id": "2",<br>"operation": "paas.app.group.controller",<br>"name": "controller"<br>},<br>{<br>"id": "3",<br>"operation": "paas.app.group.reader",<br>"name": "reader"<br>}<br>]<br>}<br>],<br>"failed": []<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询组织下某应用组所有成员权限

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/privilege/{appgroup_uuid}/users | JSON | GET | 查询组织下所有成员应用组用户 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | 是 | String | 应用组id |
| queryUserName | 否 | String | 用户名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 该应用组下本组织内还可以添加权限的用户 |
| total | Integer | 列表元素数量 | 列表元素总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"userId": "c4d0763f08b048a98eb2739e5ea10796",<br>"userName": "admin_kaas",<br>"privileges": null<br>},<br>{<br>"userId": "a5e1926b1bba4b72a2f25316f1e90252",<br>"userName": "ccp",<br>"privileges": null<br>},<br>{<br>"userId": "e03afccf843a46d6aba7450162f38edb",<br>"userName": "test824",<br>"privileges": null<br>}<br>],<br>"status": true,<br>"total": 3<br>\} |  |  |  |

### 应用

#### 创建应用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appgroups/{appgroup_uuid}/apps | JSON | POST | 创建应用 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | true | String | 选择应用所在的应用组uuid |
| name | true | String | 应用名称 |
| version | true | String | 应用版本 |
| description | true | String | 应用描述 |
| package_type | true | String | 包类型，可以为helm,jar,war,image,software,web |
| resource_type | true | String | 资源类型，可以为container/vm |
| workload_type | false | String | 当是容器部署时，必选；主机部署时，非必选。工作负载类型，有状态应用为Statefulset,无状态应用是Deployment，守护进程是DaemonSet |
| appEntranceUrl | false | String | 应用访问路径，拼装在访问地址后的路径 |
| cluster_type | False | String | 集群类型，可以为k8sCluster/KaasCluster |
| cluster_id | true | String | 集群ID(KaaS集群ID) |
| azone_id | true | String | 可用域ID（共享集群） |
| metadata | true | String | 不同的包类型对应不同的metadata |
| yaml_releases | false | String | 包类型为image,且集群类型为kaasCluster时，yaml部署时填写 |
| podNetwork | false | JSON | 当部署在KaaS集群并且类型为裸金属，支持vcfc时，可以选择自定义网络策略.<br>示例：<br>{<br>“netId”: “xxx”.<br>“subnetId”: “xxx”，<br>“staticIp”: false,<br>“enabledSpecificIp”：false<br>\} |
| 例如：<br>当资源为helm包时，body示例：<br>{<br>name: app name,<br>appEntranceUrl: "",<br>description: description,<br>package_type: helm,<br>resource_type: container,<br>cluster_type: k8sCluster,<br>cluster_id: xxxxx-yyyyy,<br>azone_id: XXXXX-NNNN,<br>version: xx,<br>metadata:{<br>version_id: string,<br>chart_config: {<br>inputA:  {<br>“value”: “xxx”,<br>“label”: “xxxx”<br>}<br>}<br>}<br>}<br>当资源为war包时，body示例：<br>{<br>"cluster_type": "k8sCluster",<br>"appEntranceUrl": "",<br>"description": "",<br>"metadata": {<br>"is_skywalking": false,<br>"jvm_args": "",<br>"setting_file_id": "",<br>"tomcat_version": "8.5",<br>“base_jdk_version”: "1.11"<br>"version_id": "gpk4X2dN0J",<br>"vm_metadata": {<br>"accessMode": "none",<br>"instances": [<br>{<br>"cidr": "10.125.41.0/24",<br>"deploy_type": "iaas_platform",<br>"instanceId": "fd5b6e18-0624-483e-896f-2476ec01385c",<br>"ip": "10.125.41.247",<br>"password": "75KZkqQBf2uv",<br>"subnetId": "363a273b-3d59-429c-a632-4ca258afbea1"<br>},<br>{<br>"deploy_type": "localhost",<br>"ip": "10.125.30.194",<br>"password": "cloudos"<br>},<br>{<br>"deploy_type": "agent",<br>"instanceId": "eacc132d-8ee8-4889-84b6-249ad420345a"<br>}<br>],<br>"port": "8080"<br>}<br>},<br>"name": "mix-test-3-2",<br>"package_type": "war",<br>"resource_type": "vm",<br>"version": "1.0"<br>}<br>通过Yaml编辑创建应用时，body示例：<br>{<br>"name": "yamltest",<br>"version": "v1",<br>"description": "",<br>"package_type": "image",<br>"resource_type": "container",<br>"cluster_type": "kaasCluster",<br>"workload_type": "Deployment",<br>"appEntranceUrl": "",<br>"cluster_id": "61B38F63-A626-48A1-A3D7-82598A23",<br>"metadata": null,<br>"yaml_releases": "apiVersion: \"apps/v1\"\nkind: \"Deployment\"\nmetadata:\n  name: \"yamltest\"\n  namespace: \"spaceufgvimw9\"\n  labels:\n    app: \"yamltest\"\nspec:\n  replicas: 1\n  selector:\n    matchLabels:\n      app: \"yamltest\"\n  template:\n    metadata:\n      labels:\n        app: \"yamltest\"\n    spec:\n      containers:\n      - image: \"os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/tomcat8:8.1\"\n        imagePullPolicy: \"IfNotPresent\"\n        name: \"yamltest\"\n        resources:\n          limits:\n            cpu: \"200m\"\n            memory: \"200Mi\"\n          requests:\n            cpu: \"100m\"\n            memory: \"100Mi\"\n"<br>}<br>当使用容器镜像时， body实例为：<br>{<br>"name": "testimage",<br>"version": "v1", // 应用版本信息<br>"description": "",<br>"package_type": "image", // 类型为image<br>"resource_type": "container",<br>"cluster_type": "k8sCluster", // 共享集群或独享集群<br>"workload_type": "Deployment", // 控制器类型<br>"appEntranceUrl": "",<br>"azone_id": "58615fe8-a", // 可用域id，若是独享集群，则key为cluster_id<br>"metadata": {<br>"version_id": "xmggctyp", // 镜像包版本id<br>"is_skywalking": true, // 支持应用诊断的情况下需要传递诊断实例相关信息<br>"skywalking_collector_backend_service": "os-techops-skywalking-oap-svc.cloudos-techops:11800",<br>"skywalking_instance_id": "default",<br>"skywalking_name": "default",<br>"skywalking_type": "public",<br>"skywalking_version": "6.6.0"<br>"image_metadata": {<br>"metadata": {<br>"affinity": { // 亲和性相关<br>"nodeAffinity": { // 节点亲和性<br>"preferred": {<br>"in": [],<br>"notIn": []<br>},<br>"required": {<br>"in": [],<br>"notIn": []<br>}<br>},<br>"podAffinity": { // 应用亲和性<br>"perferPodAffinityRules": [],<br>"requirePodAffinityRules": []<br>},<br>"podAntiAffinity": { // 应用反亲和性<br>"perferPodAntiAffinityRules": [],<br>"requirePodAntiAffinityRules": []<br>}<br>},<br>"pvcResource": { // 存储相关,动态存储与已有pvc参考jar包容器部署资源体<br>"pv": [ // 静态存储<br>{<br>"clusterId": "123456",<br>"pvcName": "testimage-tmp-test2",<br>"type": "static",<br>"pvcSize": "10Mi",<br>"accessMode": "ReadWriteMany",<br>"storageClass": ""<br>}<br>],<br>"pvc": []<br>},<br>"resources": [ // 资源相关,第一个json里面是容器权限相关，第二个里面是控制器内容相关<br>{<br>"security": {<br>"capabilities": [],<br>"hostIPC": false,<br>"hostNetwork": false,<br>"hostPID": false,<br>"privileged": false,<br>"readOnlyRootFilesystem": false,<br>"runAsUser": ""<br>}<br>},<br>{<br>"apiVersion": "apps/v1",<br>"kind": "Deployment",<br>"metadata": {<br>"labels": {// label等标签需要与应用名对齐,key为app<br>"app": "testimage"<br>},<br>"name": "testimage", // 应用名称<br>"namespace": "space07prmtak" // 应用组命名空间<br>},<br>"spec": {<br>"replicas": 1,<br>"selector": {// label等标签需要与应用名对齐<br>"matchLabels": {<br>"app": "testimage"<br>}<br>},<br>"template": {<br>"metadata": { // label等标签需要与应用名对齐<br>"labels": {<br>"app": "testimage"<br>}<br>},<br>"spec": {<br>"containers": [<br>{<br>"args": null,<br>"command": null,<br>"env": [], // 环境变量<br>"image": "os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/test1-jdk1.8:v1-1645510523179", // 镜像地址<br>"imagePullPolicy": "Always",<br>"name": "testimage",<br>"ports": [],<br>"resources": { // 镜像资源配置<br>"requests": {<br>"cpu": "0.1",<br>"memory": "125Mi"<br>},<br>"limits": {<br>"cpu": "0.5",<br>"memory": "256Mi"<br>}<br>},<br>"securityContext": {<br>"privileged": true<br>},<br>"volumeMounts": [ //控制器container内与存储资源的绑定<br>{<br>"name": "hostpath-7q60brfv0",<br>"mountPath": "/tmp/test"<br>},<br>{<br>"name": "testimage-tmp-test2",<br>"mountPath": "/tmp/test2"<br>}<br>]<br>}<br>],<br>"dnsPolicy": "ClusterFirst",<br>"initContainers": [<br>{<br>"args": null,<br>"image":"os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/zy-jar-jdk1.8:v1-1664438637965",<br>"imagePullPolicy": "IfNotPresent",<br>"name": "test",<br>"env": [<br>{<br>"name": "v1",<br>"value": "v1"<br>}<br>],<br>"command": [<br>"ls"<br>],<br>"volumeMounts": [<br>{<br>"name": "emptydir-z1tj7c4i0",<br>"mountPath": "/tmp/c"<br>}<br>]<br>}<br>],<br>"restartPolicy": "Always",<br>"schedulerName": "default-scheduler",<br>"securityContext": {},<br>"serviceAccount": "testimage",<br>"serviceAccountName": "testimage",<br>"volumes": [ // 控制器yaml内的存储信息<br>{<br>"name": "hostpath-7q60brfv0",<br>"hostPath": {<br>"path": "/tmp/test"<br>}<br>},<br>{<br>"name": "testimage-tmp-test2",<br>"persistentVolumeClaim": {<br>"claimName": "testimage-tmp-test2"<br>}<br>}<br>],<br>"nodeSelector": { // 选择可用域后添加的的nodeSelector<br>"node-zone.cloudos": "58615fe8-a"<br>}<br>}<br>}<br>}<br>}<br>]<br>},<br>"zone_id": "58615fe8-a"<br>},<br>"zone_id": "58615fe8-a"<br>}<br>}<br>当使用Jar包部署容器时， body实例为：<br>{<br>"name": "testjar",<br>"version": "v1",<br>"description": "",<br>"package_type": "jar",// 类型为jar<br>"resource_type": "container",<br>"cluster_type": "k8sCluster", // 共享集群或独享集群<br>"workload_type": "Deployment", // 控制器类型<br>"appEntranceUrl": "",<br>"azone_id": "58615fe8-a", // 可用域id，若是独享集群，则key为cluster_id<br>"metadata": {<br>"version_id": "cGjEhXIdOe", // JAR包版本id<br>"jdk_version": "1.8", // JDK版本<br>"env": { // 环境变量<br>"TEST": "test"<br>},<br>"jvm_args": "",<br>"is_skywalking": true, // 接入应用诊断时，需要传递应用诊断实例的相关信息<br>"skywalking_collector_backend_service": "os-techops-skywalking-oap-svc.cloudos-techops:11800",<br>"skywalking_instance_id": "default",<br>"skywalking_name": "default",<br>"skywalking_type": "public",<br>"skywalking_version": "6.6.0",<br>"version_id": "OSz1SFMGXg",<br>"image_metadata": {  // 详细数据信息<br>"metadata": {<br>"affinity": { // 亲和性相关<br>"nodeAffinity": {<br>"preferred": {<br>"in": [],<br>"notIn": []<br>},<br>"required": {<br>"in": [],<br>"notIn": []<br>}<br>},<br>"podAffinity": {<br>"perferPodAffinityRules": [],<br>"requirePodAffinityRules": []<br>},<br>"podAntiAffinity": {<br>"perferPodAntiAffinityRules": [],<br>"requirePodAntiAffinityRules": []<br>}<br>},<br>"pvcResource": { // 存储相关<br>"pv": [<br>{ // 动态存储形式<br>"clusterId": "123456",<br>"data": {<br>"accessMode": "ReadWriteMany",<br>"capacity": "10Mi",<br>"storageClass": "nfssc-autotest"<br>},<br>"pvcName": "testjar-tmp-test", // pvc名称<br>"type": "dynamic",<br>"volumeType": "nfs"<br>}<br>],<br>"pvc": [ // 已有PVC形式<br>"testimage-tmp-test2"<br>]<br>},<br>"resources": [ // 资源相关,第一个json里面是容器权限相关，第二个里面是控制器内容相关<br>{<br>"security": {<br>"capabilities": [],<br>"hostIPC": false,<br>"hostNetwork": false,<br>"hostPID": false,<br>"privileged": false,<br>"readOnlyRootFilesystem": false,<br>"runAsUser": ""<br>}<br>},<br>{<br>"apiVersion": "apps/v1",<br>"kind": "Deployment",<br>"metadata": {<br>"labels": {<br>"app": "testjar"<br>},<br>"name": "testjar", // 应用名称<br>"namespace": "space07prmtak" // 应用组命名空间<br>},<br>"spec": {<br>"replicas": 1,<br>"selector": {<br>"matchLabels": { // label等标签需要与应用名对齐<br>"app": "testjar"<br>}<br>},<br>"template": {<br>"metadata": {<br>"labels": {<br>"app": "testjar"<br>}<br>},<br>"spec": {<br>"containers": [<br>{<br>"args": null,<br>"command": null,<br>"env": [ // 环境变量<br>{<br>"name": "TEST",<br>"value": "test"<br>}<br>],<br>"image": "",<br>"imagePullPolicy": "Always",<br>"name": "testjar",<br>"ports": [],<br>"resources": {<br>"requests": {<br>"cpu": "0.1",<br>"memory": "125Mi"<br>},<br>"limits": {<br>"cpu": "0.5",<br>"memory": "256Mi"<br>}<br>},<br>"securityContext": {<br>"privileged": true<br>},<br>"volumeMounts": [ // 控制器container内与存储资源的绑定<br>{<br>"name": "testjar-tmp-test",<br>"mountPath": "/tmp/test"<br>},<br>{<br>"name": "pvc-3lt7k6vb1",<br>"mountPath": "/tmp/test2"<br>},<br>{ // 临时存储emptydir<br>"name": "emptydir-tprdqbov2",<br>"mountPath": "/tmp/test3" // 容器路径<br>}<br>]<br>}<br>],<br>"dnsPolicy": "ClusterFirst",<br>"restartPolicy": "Always",<br>"schedulerName": "default-scheduler",<br>"securityContext": {},<br>"serviceAccount": "testjar",<br>"serviceAccountName": "testjar",<br>"volumes": [ // 控制器yaml内的存储信息<br>{<br>"name": "testjar-tmp-test",<br>"persistentVolumeClaim": {<br>"claimName": "testjar-tmp-test"<br>}<br>},<br>{<br>"name": "pvc-3lt7k6vb1",<br>"persistentVolumeClaim": { // pvc名称<br>"claimName": "testimage-tmp-test2"<br>}<br>},<br>{<br>"name": "emptydir-tprdqbov2",<br>"emptyDir": {}<br>}<br>],<br>"nodeSelector": {  // 选择可用域后添加的的nodeSelector<br>"node-zone.cloudos": "58615fe8-a"<br>}<br>}<br>}<br>}<br>}<br>]<br>},<br>"zone_id": "58615fe8-a" // 可用域id<br>}<br>}<br>}<br>创建daemonSet应用:<br>{<br>"name": "dsapp",<br>"version": "1.0.0",<br>"description": "",<br>"package_type": "image",<br>"resource_type": "container",<br>"cluster_type": "k8sCluster",<br>"workload_type": "DaemonSet",  -- 类型指定为DaemonSet<br>"appEntranceUrl": "",<br>"azone_id": "ef981dbb-a",<br>"metadata": {<br>"version_id": "jpb4h8ri",<br>"image_metadata": {<br>"metadata": {<br>"affinity": {<br>"nodeAffinity": {<br>"preferred": {<br>"in": [],<br>"notIn": []<br>},<br>"required": {<br>"in": [],<br>"notIn": []<br>}<br>},<br>"podAffinity": {},<br>"podAntiAffinity": {}<br>},<br>"pvcResource": {<br>"pv": [],<br>"pvc": []<br>},<br>"resources": [{<br>"security": {<br>"capabilities": [],<br>"hostIPC": false,<br>"hostNetwork": false,<br>"hostPID": false,<br>"privileged": false,<br>"readOnlyRootFilesystem": false,<br>"runAsUser": ""<br>}<br>}, {<br>"apiVersion": "apps/v1",<br>"kind": "DaemonSet",  -- metadata中指定为DaemonSet<br>"metadata": {<br>"labels": {<br>"app": "dsapp"<br>},<br>"name": "dsapp",<br>"namespace": "d27028"<br>},<br>"spec": {<br>"replicas": 1,<br>"selector": {<br>"matchLabels": {<br>"app": "dsapp"<br>}<br>},<br>"template": {<br>"metadata": {<br>"labels": {<br>"app": "dsapp"<br>}<br>},<br>"spec": {<br>"containers": [{<br>"args": null,<br>"command": null,<br>"env": [],<br>"image": "os-harbor-svc.default.svc.cloudos:443/library/1df71957d770436493a84f24d9344853/springboot:1.0.0",<br>"imagePullPolicy": "Always",<br>"name": "dsapp",<br>"ports": [],<br>"resources": {<br>"requests": {<br>"cpu": "0.5",<br>"memory": "500Mi"<br>},<br>"limits": {<br>"cpu": "0.5",<br>"memory": "500Mi"<br>}<br>},<br>"securityContext": {<br>"privileged": true<br>},<br>"volumeMounts": []<br>}],<br>"dnsPolicy": "ClusterFirst",<br>"restartPolicy": "Always",<br>"schedulerName": "default-scheduler",<br>"securityContext": {},<br>"serviceAccount": "dsapp",<br>"serviceAccountName": "dsapp",<br>"volumes": [],<br>"nodeSelector": {<br>"node-zone.cloudos": "ef981dbb-a"<br>}<br>}<br>}<br>}<br>}]<br>},<br>"zone_id": "ef981dbb-a"<br>},<br>"zone_id": "ef981dbb-a"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 一般为空或操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"msg": "",<br>"data": {},<br>"total": 1,<br>"status":true<br>\} |  |  |  |

#### 查询应用列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps | JSON | GET | 查询应用列表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | False | String | 筛选出name为指定值的应用 |
| appGroupId | False | String | 筛选出应用组为指定值的应用 |
| azoneId | False | String | 筛选出可用域为指定值的应用 |
| clusterId | False | String | 筛选出集群为指定值的应用 |
| filterByPrivilege | False | String，可选值controller | 筛选出拥有使用权限以上的 |
| resourceType | False | String，可选值container或vm | 筛选出不同类型的应用 |
| Status | False | String，可选值OK/ Deploying/ Upgrading/ Gray_upgrading/ Starting/ Stopping/ Stopped/ Deleting/ Error | 筛选出不同状态的应用 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Array | 具体返回结果体 | 返回应用对象列表 |
| total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": null,<br>"msg": null,<br>"data": [<br>{<br>"app_group_id": "5ab8a73f-7b20-4736-b929-e2b2fa2dd5eb",<br>"app_group_name": "wuhan",<br>"azoneId": null,<br>"azone_id": null,<br>"cluster_id": null,<br>"cluster_type": null,<br>"controllerName": "qingdaoguoxin",<br>"controller_type": "deployment/DaemonSet",  -- 控制器类型: DaemonSet<br>"created_at": "2019-12-31 11:48:57",<br>"description": "aaaa",<br>"external_endpoint": [<br>"10.125.40.184",<br>"10.125.40.185"<br>],<br>"instance_num": 2,<br>"instance_ok_num": 0,<br>"metadata": {<br>"env": {<br>"test": "test"<br>},<br>"jdk_version": "1.8",<br>"jvm_args": "-Xmx1024m",<br>"version_id": "XyJsdk8XDO",<br>"vm_metadata": {<br>"accessMode": "iaasLB",<br>"appDN": null,<br>"iaasLB": {<br>"cookieName": "aaafddd",<br>"ip": null,<br>"lb_id": "b4ebc5c8-4f7d-4074-81a2-728d85049e32",<br>"listenerPort": 0,<br>"monitor": null,<br>"monitor_id": "43e7d923-bdb9-44d1-901c-0c886ced938b",<br>"name": null,<br>"poolId": null,<br>"poolName": null,<br>"protocol": null,<br>"sessionPersistence": "APP_COOKIE",<br>"status": null,<br>"sticky": true,<br>"strategy": "ROUND_ROBIN"<br>},<br>"instances": [<br>{<br>"cidr": "10.125.40.0/24",<br>"instanceId": "33952d09-3c51-4cd4-8830-56adc5cf6b22",<br>"ip": "10.125.40.184",<br>"password": "cloudos",<br>"subnetId": "c397dd9f-37b0-4aa7-9a35-2c13637d73f0"<br>},<br>{<br>"cidr": "10.125.40.0/24",<br>"instanceId": "7857e2ad-3da9-4b16-ab72-a73f76a228cd",<br>"ip": "10.125.40.185",<br>"password": "cloudos",<br>"subnetId": "c397dd9f-37b0-4aa7-9a35-2c13637d73f0"<br>}<br>],<br>"port": "32077"<br>}<br>},<br>"name": "qingdaoguoxin",<br>"namespace": "space0ezt8oj9",<br>"package_id": "15d8f0f1-764e-41e5-9d01-a1ee7bb3efb3",<br>"package_type": "jar",<br>"package_url": "/opt/package-files/package/XyJsdk8XDO/hystrixtest-0.0.1-SNAPSHOT.jar",<br>"package_version_id": "XyJsdk8XDO",<br>"release_id": "684d94dad8924c5391b9b8b1cfea2fad",<br>"release_name": "qingdaoguoxin",<br>"resource_type": "vm",<br>"status": "Stopped",<br>"updated_at": "2020-01-06 17:02:43",<br>"upgrading_versions": null,<br>"uuid": "1c08186e56b041848ba5de4c0793bd39",<br>"version": "v1",<br>"vip": null,<br>"privileges": [<br>{<br>"id": "1",<br>"operation": "paas.app.group.owner",<br>"name": "owner"<br>}<br>],<br>"tomcatDetailedVersion": null,<br>"tomcatUpgrade": false<br>}<br>],<br>"total": 1,<br>"status":true<br>\} |  |  |  |

#### 查询应用详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid\} | JSON | GET | 查询应用详情 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用的对象 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": null,<br>"msg": null,<br>"data": {<br>"app_group_id": "5ab8a73f-7b20-4736-b929-e2b2fa2dd5eb",<br>"app_group_name": "wuhan",<br>"azoneId": null,<br>"azone_id": null,<br>"cluster_id": null,<br>"cluster_name": null,<br>"cluster_type": null,<br>"controllerName": "qingdaoguoxin",<br>"controller_type": "deployment/DaemonSet",<br>"created_at": "2019-12-31 11:48:57",<br>"description": "aaaa",<br>"elasticandscalable ": false,<br>"external_endpoint": [<br>"10.125.40.184",<br>"10.125.40.185"<br>],<br>"instance_num": 2,<br>"instance_ok_num": 0,<br>"metadata": {<br>"env": {<br>"test": "test"<br>},<br>"jdk_version": "1.8",<br>"jvm_args": "-Xmx1024m",<br>"version_id": "XyJsdk8XDO",<br>"vm_metadata": {<br>"accessMode": "iaasLB",<br>"appDN": null,<br>"iaasLB": {<br>"cookieName": "aaafddd",<br>"ip": null,<br>"lb_id": "b4ebc5c8-4f7d-4074-81a2-728d85049e32",<br>"listenerPort": 0,<br>"monitor": null,<br>"monitor_id": "43e7d923-bdb9-44d1-901c-0c886ced938b",<br>"name": null,<br>"poolId": null,<br>"poolName": null,<br>"protocol": null,<br>"sessionPersistence": "APP_COOKIE",<br>"status": null,<br>"sticky": true,<br>"strategy": "ROUND_ROBIN"<br>},<br>"instances": [<br>{<br>"cidr": "10.125.40.0/24",<br>"instanceId": "33952d09-3c51-4cd4-8830-56adc5cf6b22",<br>"ip": "10.125.40.184",<br>"password": "cloudos",<br>"subnetId": "c397dd9f-37b0-4aa7-9a35-2c13637d73f0"<br>},<br>{<br>"cidr": "10.125.40.0/24",<br>"instanceId": "7857e2ad-3da9-4b16-ab72-a73f76a228cd",<br>"ip": "10.125.40.185",<br>"password": "cloudos",<br>"subnetId": "c397dd9f-37b0-4aa7-9a35-2c13637d73f0"<br>}<br>],<br>"port": "32077"<br>}<br>},<br>"name": "qingdaoguoxin",<br>"namespace": "space0ezt8oj9",<br>"package_id": "15d8f0f1-764e-41e5-9d01-a1ee7bb3efb3",<br>"package_type": "jar",<br>"package_url": "/opt/package-files/package/XyJsdk8XDO/hystrixtest-0.0.1-SNAPSHOT.jar",<br>"package_version_id": "XyJsdk8XDO",<br>"release_id": "684d94dad8924c5391b9b8b1cfea2fad",<br>"release_name": "qingdaoguoxin",<br>"resource_type": "vm",<br>"status": "Stopped",<br>"updated_at": "2020-01-06 17:02:43",<br>"upgrading_versions": null,<br>"uuid": "1c08186e56b041848ba5de4c0793bd39",<br>"version": "v1",<br>"vip": null,<br>"privileges": [<br>{<br>"id": "1",<br>"operation": "paas.app.group.owner",<br>"name": "owner"<br>}<br>] ,<br>"tomcatDetailedVersion": null,<br>"tomcatUpgrade": false<br>},<br>"total": 1,<br>"status":true<br>\} |  |  |  |

#### 删除应用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid\} | JSON | DELETE | 删除应用 |

###### 参数说明

| Path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | App对应UUID |
| deletePvc | False | Boolean | 是否删除对应pvc,默认false |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 启动/停止/重启应用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid\} | JSON | PUT | 启动/停止/重启应用 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | App对应的UUID |
| action | true | String | 对应操作，取值start, stop, restart |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": null,<br>"msg": null,<br>"data": true,<br>"total": 1,<br>"status":true,"<br>\} |  |  |  |

#### 升级应用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/advanced-config | JSON | PATCH | 升级应用 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用uuid |
| version | true | String | 升级的应用版本 |
| service_name | true | String | 服务名称 |
| strategy | true | String | 应用升级策略，可以为rolling，gray |
| alternate_weight | False | Integer | 灰度升级时新版本的服务权重 |
| container_name | false | String | 当应用为镜像应用时，指定K8S SPEC中的container name |
| container_image | False | String | 当应用为镜像应用时，指定新版本的容器镜像地址 |
| version_id | true | String | 版本ID |
| env | False | Object | 容器应用时，可以更新新版本中的环境变量 |
| jvm_args | False | Object | 包类型是Jar或者war时，Jvm的启动参数更新 |
| nginx_version | true | String | 包类型是Web时，Nginx版本号 |
| crt_file_id | false | String | 包类型是Web时，证书公钥 |
| key_file_id | false | String | 包类型是Web时，证书私钥 |
| enable_ssl | true | Boolean | 包类型是Web时，是否开启https |
| setting_file_id | false | String | 包类型是War时，tomcat配置文件的ID |
| inputs | false | Object | 包类型是传统软件时，输出脚本的inputs |
| affinity | true | Object | 升级的高级参数-亲和性相关 |
| pvcResource | true | Object | 升级的高级参数-存储相关 |
| resources | true | Object | 升级的高级参数-资源相关 |
| tomcatUpgrade | False | Boolean | 只有当应用是war包时，并且tomcat版本低于当前平台内最新tomcat版本时，可传该值为true，并且其他下发值同升级一致不做任何变动。此时下发的version需要以当前version+”. h3ctomcatupgrade”结尾 |
| logrotateCron | False | String | 当时虚拟机应用时，可以开启虚拟机日志轮转，crontab表达式，例如：* * */7 * * |
| 例如：<br>当资源为helm包时，body示例：<br>{<br>service_name: service name,<br>strategy: grey,<br>alternate_weight: 50,<br>container_name: wordpress,<br>container_image: https://ssx/xxxxs,<br>version_id: XXXXX-NNNN,<br>env:{<br>key1: val1<br>}<br>}<br>当资源为image包时，body示例：<br>{<br>"version_id": "ajvm8et4",<br>"version": "v3",<br>"strategy": "rolling",<br>"container_name": "imagetest",<br>"container_image": "xxx",<br>"env": {},<br>"is_skywalking": true,<br>"skywalking_type": "public",<br>"skywalking_collector_backend_service": "os-techops-skywalking-oap-svc.cloudos-techops:11800",<br>"skywalking_version": "6.6.0",<br>"skywalking_name": "default",<br>"skywalking_instance_id": "default",<br>"service_name": "imagetest-c7zax-svc",<br>"affinity": {<br>"nodeAffinity": {<br>"preferred": {<br>"in": [<br>{<br>"key": "build-worker",<br>"values": [<br>"kubernetes"<br>]<br>}<br>],<br>"notIn": []<br>},<br>"required": {<br>"in": [],<br>"notIn": []<br>}<br>},<br>"podAffinity": {<br>"perferPodAffinityRules": [],<br>"requirePodAffinityRules": []<br>},<br>"podAntiAffinity": {<br>"perferPodAntiAffinityRules": [],<br>"requirePodAntiAffinityRules": []<br>}<br>},<br>"pvcResource": {<br>"pv": [],<br>"pvc": []<br>},<br>"resources": [<br>{<br>"security": {<br>"capabilities": [<br>"DAC_OVERRIDE"<br>],<br>"hostIPC": true,<br>"hostNetwork": false,<br>"hostPID": true,<br>"privileged": false,<br>"readOnlyRootFilesystem": false,<br>"runAsUser": ""<br>}<br>},<br>{<br>"apiVersion": "apps/v1",<br>"kind": "Deployment",<br>"metadata": {<br>"labels": {<br>"app": "imagetest"<br>},<br>"name": "imagetest",<br>"namespace": "spacesjxvr7ia"<br>},<br>"spec": {<br>"replicas": 1,<br>"selector": {<br>"matchLabels": {<br>"app": "imagetest"<br>}<br>},<br>"template": {<br>"metadata": {<br>"labels": {<br>"app": "imagetest"<br>}<br>},<br>"spec": {<br>"containers": [<br>{<br>"args": [<br>"1000"<br>],<br>"command": [<br>"sleep"<br>],<br>"env": [],<br>"image": "os-harbor-svc.default.svc.cloudos:443/library/1df71957d770436493a84f24d9344853/tomcat:7.0",<br>"imagePullPolicy": "Always",<br>"name": "imagetest",<br>"ports": [],<br>"resources": {<br>"requests": {<br>"cpu": "0.2",<br>"memory": "200Mi"<br>},<br>"limits": {<br>"cpu": "0.3",<br>"memory": "300Mi"<br>}<br>},<br>"securityContext": {<br>"privileged": true<br>},<br>"volumeMounts": [],<br>"livenessProbe": null,<br>"readinessProbe": null<br>}<br>],<br>"dnsPolicy": "ClusterFirst",<br>"initContainers": [<br>{<br>"args": null,<br>"image":"os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/zy-jar-jdk1.8:v1-1664438637965",<br>"imagePullPolicy": "IfNotPresent",<br>"name": "test2",<br>"env": [<br>{<br>"name": "v1",<br>"value": "v1"<br>}<br>],<br>"command": [<br>"ls"<br>],<br>"volumeMounts": [<br>{<br>"name": "emptydir-z1tj7c4i0",<br>"mountPath": "/tmp/c"<br>}<br>]<br>}<br>],<br>"restartPolicy": "Always",<br>"schedulerName": "default-scheduler",<br>"securityContext": {},<br>"serviceAccount": "imagetest",<br>"serviceAccountName": "imagetest",<br>"volumes": [],<br>"nodeSelector": {<br>"node-zone.cloudos": "0f917b9e-b"<br>}<br>}<br>}<br>}<br>}<br>]<br>}<br>当资源为web包时，请求body<br>{<br>"version_id": "CoO7m2YLRU",<br>"version": "2",<br>"strategy": "rolling",<br>"env": {},<br>"jvm_args": "",<br>"logrotateCron": null,<br>"setting_file_id": "cf4492c2-31e5-45b7-9511-0854835cc109",<br>"tomcat_version": "8.5",<br>"is_skywalking": false,<br>"enable_ssl": true,<br>"nginx_version": "1.20",<br>"crt_file_id": "04abc484-8cfb-4e0e-849c-f0cd2f64834c",<br>"key_file_id": "b4bdbf15-6e33-4d82-ae0d-1672166108fb",<br>"service_name": "vue-test1-l2bej-svc",<br>"affinity": {<br>"nodeAffinity": {<br>"preferred": {<br>"in": [],<br>"notIn": []<br>},<br>"required": {<br>"in": [],<br>"notIn": []<br>}<br>},<br>"podAffinity": {<br>"perferPodAffinityRules": [],<br>"requirePodAffinityRules": []<br>},<br>"podAntiAffinity": {<br>"perferPodAntiAffinityRules": [],<br>"requirePodAntiAffinityRules": []<br>}<br>},<br>"pvcResource": {<br>"pv": [],<br>"pvc": []<br>},<br>"resources": [<br>{<br>"security": {<br>"capabilities": [],<br>"hostIPC": false,<br>"hostNetwork": false,<br>"hostPID": false,<br>"privileged": false,<br>"readOnlyRootFilesystem": false,<br>"runAsUser": ""<br>}<br>},<br>{<br>"apiVersion": "apps/v1",<br>"kind": "Deployment",<br>"metadata": {<br>"labels": {<br>"app": "vue-test1"<br>},<br>"name": "vue-test1",<br>"namespace": "spacek85gwc25"<br>},<br>"spec": {<br>"replicas": 1,<br>"selector": {<br>"matchLabels": {<br>"app": "vue-test1"<br>}<br>},<br>"template": {<br>"metadata": {<br>"labels": {<br>"app": "vue-test1"<br>}<br>},<br>"spec": {<br>"containers": [<br>{<br>"args": null,<br>"command": null,<br>"env": [],<br>"image": "",<br>"imagePullPolicy": "Always",<br>"name": "vue-test1",<br>"ports": [],<br>"resources": {<br>"requests": {<br>"cpu": "0.3",<br>"memory": "300Mi"<br>},<br>"limits": {<br>"cpu": "0.3",<br>"memory": "300Mi"<br>}<br>},<br>"securityContext": {<br>"privileged": true<br>},<br>"volumeMounts": [<br>{<br>"name": "cm-pgy279cm0",<br>"mountPath": "/usr/share/nginx/conf/nginx.conf",<br>"subPath": "nginx.conf"<br>}<br>],<br>"livenessProbe": null,<br>"readinessProbe": null<br>}<br>],<br>"dnsPolicy": "ClusterFirst",<br>"restartPolicy": "Always",<br>"schedulerName": "default-scheduler",<br>"securityContext": {},<br>"serviceAccount": "vue-test1",<br>"serviceAccountName": "vue-test1",<br>"volumes": [<br>{<br>"configMap": {<br>"name": "vue-test1"<br>},<br>"name": "cm-pgy279cm0"<br>}<br>],<br>"nodeSelector": {<br>"node-zone.cloudos": "358c2e61-0"<br>}<br>}<br>}<br>}<br>}<br>]<br>}<br>升级DaemonSet应用:<br>{<br>"version_id": "jpb4h8ri",<br>"version": "1.0.2",<br>"strategy": "rolling",<br>"container_name": "imageds",<br>"container_image": "os-harbor-svc.default.svc.cloudos:443/library/1df71957d770436493a84f24d9344853/springboot:1.0.0",<br>"env": {<br>"aa": "ss"<br>},<br>"enabledSpecificIp": null,<br>"service_name": "imageds-v6yxd-svc",<br>"affinity": {<br>"nodeAffinity": {<br>"preferred": {<br>"in": [],<br>"notIn": []<br>},<br>"required": {<br>"in": [],<br>"notIn": []<br>}<br>},<br>"podAffinity": {<br>"perferPodAffinityRules": [],<br>"requirePodAffinityRules": []<br>},<br>"podAntiAffinity": {<br>"perferPodAntiAffinityRules": [],<br>"requirePodAntiAffinityRules": []<br>}<br>},<br>"pvcResource": {<br>"pv": [],<br>"pvc": []<br>},<br>"resources": [{<br>"security": {<br>"capabilities": [],<br>"hostIPC": false,<br>"hostNetwork": false,<br>"hostPID": false,<br>"privileged": false,<br>"readOnlyRootFilesystem": false,<br>"runAsUser": ""<br>}<br>}, {<br>"apiVersion": "apps/v1",<br>"kind": "DaemonSet",    -- 类型为DaemonSet<br>"metadata": {<br>"labels": {<br>"app": "imageds"<br>},<br>"name": "imageds",<br>"namespace": "d27028"<br>},<br>"spec": {<br>"replicas": 1,<br>"selector": {<br>"matchLabels": {<br>"app": "imageds"<br>}<br>},<br>"template": {<br>"metadata": {<br>"labels": {<br>"app": "imageds"<br>}<br>},<br>"spec": {<br>"containers": [{<br>"args": ["--abc=10"],<br>"command": null,<br>"env": [{<br>"name": "aa",<br>"value": "ss"<br>}],<br>"image": "os-harbor-svc.default.svc.cloudos:443/library/1df71957d770436493a84f24d9344853/springboot:1.0.0",<br>"imagePullPolicy": "Always",<br>"name": "imageds",<br>"ports": [],<br>"resources": {<br>"requests": {<br>"cpu": "0.5",<br>"memory": "500Mi"<br>},<br>"limits": {<br>"cpu": "0.5",<br>"memory": "500Mi"<br>}<br>},<br>"securityContext": {<br>"privileged": true<br>},<br>"volumeMounts": [],<br>"livenessProbe": null,<br>"readinessProbe": null<br>}],<br>"dnsPolicy": "ClusterFirst",<br>"restartPolicy": "Always",<br>"schedulerName": "default-scheduler",<br>"securityContext": {},<br>"serviceAccount": "imageds",<br>"serviceAccountName": "imageds",<br>"volumes": [],<br>"nodeSelector": {<br>"node-zone.cloudos": "ef981dbb-a"<br>}<br>}<br>},<br>"updateStrategy": {<br>"rollingUpdate": {<br>"maxUnavailable": 1<br>},<br>"type": "RollingUpdate"<br>}<br>}<br>}]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"msg": "",<br>"data": true,<br>"total": 1,<br>"status":true,"<br>\} |  |  |  |

#### 查询应用配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/file | JSON | GET | 查询应用配置 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用ID |
| upgradeId | true | String | 应用升级版本ID |
| fileType | true | String | 文件类型<br>"web包配置文件":"web_conf"<br>"web包证书公钥":"web_crt"<br>"web包证书私钥":"web_key" |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回应用配置信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"uuid": "1768886cdb19493dbe88f7b71edc7466",<br>"app_id": "c4f648018c9644558ee6c2168206b52e",<br>"upgrade_id": "07cac45090f94f9e9ef0d3f7fcab6dd1",<br>"file_type": "web_conf",<br>"file_text": "worker_processes  1;\n\nevents {\n    worker_connections  1024;\n}\n\nhttp {\n    include       mime.types;\n    default_type  application/octet-stream;\n    sendfile        on;\n    keepalive_timeout  65;\n\n    server {\n        listen       80;\n        server_name  localhost;\n        location / {\n            root   /usr/share/nginx/html;\n            index  index.html index.htm;\n        }\n    }\n}"<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 灰度升级权重调整

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/route | JSON | PUT | 灰度升级权重调整 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| uuid | true | String | 数组对象属性，Route名下服务名 |
| weight | true | String | 数组对象属性，权重 |
| 例如：<br>[<br>{<br>"uuid": "001fa044053f49a7a86ba70b4ce9a647",<br>"weight": 70<br>},<br>{<br>"uuid": "72bad1916cda4063ba6cefd4d1695a9e",<br>"weight": 30<br>}<br>] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"status":true,"<br>\} |  |  |  |

#### 查询应用访问方式

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/service | JSON | GET | 查询应用访问方式 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | App对应的UUID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 包含VIP 和services列表 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": {<br>"vip": "10.125.31.80",<br>"services": [<br>{<br>"type": "NodePort",<br>"service_name": "service name",<br>"port_map_list": [<br>{<br>"name": "web",<br>"protocol": "TCP",<br>"container_port": 0,<br>"cluster_port": 0,<br>"node_port": 0<br>}<br>],<br>"session_keep": true,<br>"session_timeout": 0<br>}<br>]<br>},<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 创建应用访问方式

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/service | JSON | POST | 创建应用访问方式 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| type | true | String | 服务类型，可取NodePort， ClusterIP , Headless, LoadBalancer |
| service_name | true | String | 服务名称 |
| port_map_list | true | String | 端口映射对象列表，对象包含name, protocol, container_port, cluster_port, node_port |
| session_keep | true | boolean | 是否打开会话保持(HeadLess服务时无效) |
| session_timeout | False | Integer | 会话超时时间 |
| labels | False | List | Service本身的标签 |
| 例如：<br>{<br>"type": "NodePort",<br>"service_name": "service name",<br>"port_map_list": [<br>{<br>"name": "string",<br>"protocol": "TCP",<br>"container_port": 0,<br>"cluster_port": 0,<br>"node_port": 0<br>}<br>],<br>"session_keep": true,<br>"session_timeout": 0,<br>“labels”: {xx: "xx"}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 删除应用访问方式

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/service | JSON | DELETE | 删除应用访问方式 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | App对应的UUID |
| Name | true | String | 访问方式对应的名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 查询应用域名

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/route | JSON | GET | 查询应用域名 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | App对应的UUID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回route列表，包含绑定服务名，route名，负载算法，是否开启https等属性 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": [<br>{<br>"binded_service": "myapp_svc",<br>"route": "router.space123213.svc.cloudos.com",<br>"load_balance": "roundrobin",<br>"is_https_open": false<br>}<br>],<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 创建应用域名

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/route | JSON | POST | 创建应用域名 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| route | true | String | Route名 |
| service_name | true | String | 服务名称 |
| cluster_port | true | Integer | 集群端口 |
| load_balance | true | String | 负载算法，可取：roundrobin, leastconn, source |
| is_https_open | False | boolean | 是否开启https |
| is_sticky_session_open | False | boolean | 是否开启会话粘连 |
| 例如：<br>{<br>"route": "router.space123213.svc.cloudos.com",<br>"service_name": "xxxx",<br>"cluster_port": 8080,<br>"load_balance": "roundrobin",<br>"is_https_open": false,<br>"is_sticky_session_open": false<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 修改应用域名

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/route | JSON | PATCH | 修改应用域名 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| route | true | String | Route名 |
| service_name | true | String | 服务名称 |
| cluster_port | true | Integer | 集群端口 |
| load_balance | true | String | 负载算法，可取：roundrobin, leastconn, source |
| is_https_open | False | boolean | 是否开启https |
| is_sticky_session_open | False | boolean | 是否开启会话粘连 |
| 例如：<br>{<br>"route": "router.space123213.svc.cloudos.com",<br>"service_name": "xxxx",<br>"cluster_port": 8080,<br>"load_balance": "roundrobin",<br>"is_https_open": false,<br>"is_sticky_session_open": false<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 校验应用域名

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/checkRouterHost | JSON | PUT | 校验应用域名 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| host | True | String | 应用域名名称 |
| 例如：<br>{<br>host: “test.com”<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | isTaken表示是否被使用，false表示未被使用 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": {<br>"host": "ioljksdaf"<br>"isTaken": false<br>},<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 配置应用弹性伸缩

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/autoscale | JSON | POST | 伸缩配置 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| target_cpu_utilization | true | String | CPU使用率阈值 |
| target_mem_utilization | true | String | 内存使用率阈值 |
| target_req_per_sec | true | Integer | 每秒请求数阈值 |
| target_port | true | String | 目标端口 |
| max_replica | False | boolean | 最大副本数 |
| min_replica | False | boolean | 最小副本数 |
| 例如：<br>{<br>"target_cpu_utilization": 80,<br>"target_mem_utilization": 80,<br>"target_req_per_sec": 800,<br>"target_port": 8080,<br>"max_replica": 5,<br>"min_replica": 1<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 查询应用弹性伸缩配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/autoscale | JSON | GET | 查询伸缩配置 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 弹性伸缩配置，包括几个阈值target_cpu_utilization, target_mem_utilization,<br>target_req_per_sec,<br>以及配置max_replica, min_replica |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": {<br>"target_cpu_utilization": 80,<br>"target_mem_utilization": 80,<br>"target_req_per_sec": 800,<br>"target_port": 8080,<br>"max_replica": 5,<br>"min_replica": 1,<br>“namespace”: "spacep5fdqout"<br>},<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 删除应用弹性伸缩配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/autoscale | JSON | DELETE | 删除应用弹性伸缩配置 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 配置应用周期伸缩

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/cronscale | JSON | POST | 应用周期伸缩配置 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| schedule | true | String | 定时模式字符串 |
| targetSize | true | String | 目标replica大小 |
| 例如：<br>{<br>"schedule": "30 */1 * * * *\"",<br>"targetSize": 5<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 查询应用周期伸缩配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/autoscale | JSON | GET | 伸缩配置 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 定时伸缩配置，包含schedule， targetSize |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": {<br>"name": "job-1",<br>"schedule": "30 */1 * * * *\"",<br>"targetSize": 5<br>},<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 删除应用周期伸缩配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/cronscale | JSON | DELETE | 删除应用周期伸缩配置 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 应用手动伸缩

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/scale | JSON | POST | 应用手动伸缩 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| replicas | true | Integer | 目标副本数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 查询容器资源限制

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/resources | JSON | GET | 查询资源限制 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 列表，包含应用下每个容器的resource信息 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": {},<br>"data": [<br>{<br>"name": "nginx",<br>"replicas": 0,<br>"resources": {<br>"requests": {<br>"cpu": "500m",<br>"memory": "512Mi"<br>},<br>"limits": {<br>"cpu": "1",<br>"memory": "1Gi"<br>}<br>}<br>}<br>],<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 修改容器资源限制

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/resources | JSON | PUT | 修改容器资源限制 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| resources | true | String | 数组对象属性，容器的资源限制定义 |
| name | true | String | 数组对象属性，容器名 |
| 例如：<br>[<br>{<br>"name": "nginx",<br>"resources": {<br>"requests": {<br>"cpu": "500m",<br>"memory": "512Mi"<br>},<br>"limits": {<br>"cpu": "1",<br>"memory": "1Gi"<br>}<br>}<br>}<br>] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作的结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 修改应用访问路径

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{appId}/entrance | JSON | PUT | 修改应用访问路径 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appId | true | String | 应用UUID |
| appEntranceUrl | true | String | 应用访问入口 |
| 例如：<br>{<br>"appEntranceUrl":"/index.html"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 成功为“success” |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": "success",<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 虚拟机手动弹性伸缩

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app-mgmt/v1.0/app/{{appId}}/scaleNodes | JSON | POST | 虚拟机弹性伸缩 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appId | true | String | 应用UUID |
| addNodes | False | Array | 增加虚拟机节点 |
| removeNodes | False | Array | 删除虚拟机节点 |
| strategy | true | String | 策略 |
| model | true | String | model |
| instanceId | False | String | 虚拟机ID，云主机弹性伸缩必填 |
| ip | False | String | 虚拟机IP，云主机弹性伸缩必填 |
| password | False | String | 虚拟机密码，云主机弹性伸缩必填 |
| deploy_type | True | String | 部署类型，云主机为iaas_platform |
| subnetId | False | String | 云主机所在组网ID必填 |
| netName | False | String | 云主机所在网络名称必填 |
| cidr | False | String | 云主机CIDR值 |
| 例如：<br>{<br>"model": "instance_num",<br>"strategy": "manual",<br>"addNodes": [<br>{<br>"instanceId": "aab482f2-0c95-43f4-b829-92f660ca9e5e",<br>"password": "",<br>"ip": "",<br>"subnetId": "89e98e99-9cfc-4db1-aa6f-8377250f896d",<br>"cidr": "10.125.41.0/24",<br>"deploy_type": "iaas_platform"，<br>"netName": "xxx"，<br>"cidr": "10.125.41.0/24"<br>}<br>],<br>"removeNodes": []<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 申请应用自动弹性伸缩流程

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/autoscale/bpm | JSON | POST | 伸缩配置 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| target_cpu_utilization | true | String | CPU使用率阈值 |
| target_mem_utilization | true | String | 内存使用率阈值 |
| target_req_per_sec | true | Integer | 每秒请求数阈值 |
| target_port | true | String | 目标端口 |
| max_replica | False | boolean | 最大副本数 |
| min_replica | False | boolean | 最小副本数 |
| 例如：<br>{<br>"target_cpu_utilization": 80,<br>"target_mem_utilization": 80,<br>"target_req_per_sec": 800,<br>"target_port": 8080,<br>"max_replica": 5,<br>"min_replica": 1<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 申请修改容器资源限制流程

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/resources/bpm | JSON | POST | 修改容器资源限制 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| resources | true | String | 数组对象属性，容器的资源限制定义 |
| name | true | String | 数组对象属性，容器名 |
| 例如：<br>[<br>{<br>"name": "nginx",<br>"resources": {<br>"requests": {<br>"cpu": "500m",<br>"memory": "512Mi"<br>},<br>"limits": {<br>"cpu": "1",<br>"memory": "1Gi"<br>}<br>}<br>}<br>] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": true,<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 申请应用手动伸缩流程

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/scale/bpm | JSON | POST | 伸缩配置流程 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| replicas | true | Integer | 目标副本数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 一般为空 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": {},<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 申请虚拟机手动弹性伸缩流程

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app-mgmt/v1.0/app/{{appId}}/scaleNodes/bpm | JSON | POST | 虚拟机弹性伸缩流程 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appId | true | String | 应用UUID |
| addNodes | False | Array | 增加虚拟机节点 |
| removeNodes | False | Array | 删除虚拟机节点 |
| strategy | true | String | 策略 |
| model | true | String | model |
| instanceId | False | String | 虚拟机ID，云主机弹性伸缩必填 |
| ip | False | String | 虚拟机IP，云主机弹性伸缩必填 |
| password | False | String | 虚拟机密码，云主机弹性伸缩必填 |
| deploy_type | True | String | 部署类型，云主机为iaas_platform |
| subnetId | False | String | 云主机所在组网ID必填 |
| netName | False | String | 云主机所在网络名称必填 |
| cidr | False | String | 云主机CIDR值 |
| 例如：<br>{<br>"model": "instance_num",<br>"strategy": "manual",<br>"addNodes": [<br>{<br>"instanceId": "aab482f2-0c95-43f4-b829-92f660ca9e5e",<br>"password": "",<br>"ip": "",<br>"subnetId": "89e98e99-9cfc-4db1-aa6f-8377250f896d",<br>"cidr": "10.125.41.0/24",<br>"deploy_type": "iaas_platform",<br>"netName": "xxx"，<br>"cidr": "10.125.41.0/24"<br>}<br>],<br>"removeNodes": []<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 操作结果状态 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 申请配置应用周期伸缩

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/cronscale/bpm | JSON | POST | 伸缩配置流程 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| schedule | true | String | 定时模式字符串 |
| targetSize | true | String | 目标replica大小 |
| 例如：<br>{<br>"schedule": "30 */1 * * * *\"",<br>"targetSize": 5<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 一般为空 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": {},<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 申请虚拟机自动伸缩

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/app/{app_uuid}/ VMAutoScaling/bpm | JSON | POST | 伸缩配置流程 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| interval | true | String | 检查间隔时间 |
| waitingTime | true | String | 冷却时间 |
| visitCountThreshold | true | String | 检查次数阈值 |
| cpuUtilThreshold | true | String | 扩展配置 - CPU使用率阈值 |
| memoryUtilThreshold | true | String | 扩展配置 - 内存使用率阈值 |
| tcpConnectionUtilThreshold | true | String | 扩展配置 - TCP连接阈值 |
| max_extend_members | true | String | 最大扩展数 |
| cpuUtilReduceThreshold | true | String | 收缩配置-CPU使用率阈值 |
| memoryUtilReduceThreshold | true | String | 收缩配置-内存使用率阈值 |
| tcpConnectionUtilReduceThreshold | true | String | 收缩配置-TCP连接阈值 |
| instanceUuid | true | String | 种子节点id |
| seedName | true | String | 种子节点名称 |
| 例如：<br>{<br>"interval": "30",<br>"waitingTime": "20",<br>"visitCountThreshold": "2",<br>"cpuUtilThreshold": "70",<br>"memoryUtilThreshold": "2",<br>"tcpConnectionUtilThreshold": "3000",<br>"max_extend_members": "3",<br>"cpuUtilReduceThreshold": "30",<br>"memoryUtilReduceThreshold": "1",<br>"tcpConnectionUtilReduceThreshold": "3000",<br>"instanceUuid": "00e2519d-f3ea-48da-9c7c-5e4d5b735221",<br>"seedName": "test"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | VM应用弹性伸缩配置 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": 0,<br>"data": {},<br>"status": true,<br>"msg": "错误信息",<br>"total":1<br>\} |  |  |  |

#### 校验Yaml编辑请求体

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appgroups/{appgroup_uuid}/checkYaml | JSON | POST | 校验Yaml编辑请求体 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appgroup_uuid | true | String | 应用组uuid |

###### 请求体

| Name | true | String | 应用组名称 |
| --- | --- | --- | --- |
| yaml_str | true | String | Yaml编辑请求体 |
| 例如：<br>{<br>"yaml_str": "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  labels:\n    app: yamltest\n  name: yamltest\n  namespace: spaceufgvimw9\nspec:\n  replicas: 1\n  selector:\n    matchLabels:\n      app: yamltest\n  template:\n    metadata:\n      labels:\n        app: yamltest\n    spec:\n      containers:\n      - image: os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/tomcat8:8.1\n        imagePullPolicy: IfNotPresent\n        name: yamltest\n        resources:\n          limits:\n            cpu: 200m\n            memory: 200Mi\n          requests:\n            cpu: 100m\n            memory: 100Mi"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 校验后的Yaml请求体 | 校验后的Yaml请求体 |
| total | Integer | 无实际意义 | 无实际意义，总为1 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"msg": "",<br>"data": "", //以字符串形式，返回校验后的Yaml请求体<br>"total": 1,<br>"status": true,<br>\} |  |  |  |

#### 查询虚拟机应用的运行日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/vm_instances/{instance_id}/log | JSON | GET | 查询虚拟机应用的运行日志 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| instance_id | true | String | 虚拟机实例UUID |
| tailingLines | true | Integer | 日志行数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 虚拟机应用的运行日志详情 | 虚拟机应用的运行日志详情 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": {},<br>"data": "at com.sun.jersey.api.client.filter.GZIPContentEncodingFilter.handle", //日志详情<br>"status": true,<br>"msg": "",<br>"total": 1<br>\} |  |  |  |

#### 下载虚拟机应用的运行日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/vm_instances/{instance_id}/log/download | octet-stream | GET | 下载虚拟机应用的运行日志 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| instance_id | true | String | 虚拟机实例UUID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：文件流 |  |  |  |

#### 查询容器应用的运行日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/namespaces/{namespace}/pods/{pod_name}/log | JSON | GET | 查询容器应用的运行日志 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 应用所在的命名空间 |
| pod_name | true | String | Pod的名称 |
| container | true | String | Pod中容器名称 |
| tailingLines | true | Integer | 日志行数 |
| zoneId | false | String | 可用域ID，共享集群必填 |
| clusterId | false | String | 集群ID，KaaS集群必填 |
| sinceSeconds | true | Integer | 日志时间范围 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 容器应用的运行日志详情 | 容器应用的运行日志详情 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": {},<br>"data": "at com.sun.jersey.api.client.filter.GZIPContentEncodingFilter.handle", //日志详情<br>"status": true,<br>"msg": "",<br>"total": 1<br>\} |  |  |  |

#### 下载容器应用的运行日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/namespaces/{namespace}/pods/{pod}/log/download | octet-stream | GET | 下载容器应用的运行日志 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 应用所在的命名空间 |
| pod_name | true | String | Pod的名称 |
| container | true | String | Pod中容器名称 |
| tailingLines | true | Integer | 日志行数 |
| zoneId | false | String | 可用域ID，共享集群必填 |
| clusterId | false | String | 集群ID，KaaS集群必填 |
| sinceSeconds | true | Integer | 日志时间范围 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：文件流 |  |  |  |

#### 查询容器应用实例重启信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/pod/{pod_name}/restartMsg | JSON | GET | 查询容器应用实例重启信息 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| app_uuid | true | String | 应用UUID |
| pod_name | true | String | Pod的名称 |
| container | true | String | Pod中容器名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 容器应用的运行日志详情 | 容器应用的运行日志详情 |
| total | Integer | 无实际意义 | 无实际意义 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": {},<br>"data": {<br>"exitCode": "127",<br>"lastTime": "2020-11-30T07:11:27Z",<br>"restartReason": "ContainerCannotRun"<br>}<br>"status": true,<br>"msg": "",<br>"total": 1<br>\} |  |  |  |

#### 已部署应用容器集群列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/clusters | JSON | GET | 获取已部署应用的集群列表 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | Int | 请求页 |
| pageSize | true | Int | 请求页数量 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 容器集群的字段信息 | 容器集群的字段信息 |
| total | Integer | 列表总数 | 列表总数 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "0F2B0AF7-0241-4D6D-8E25-4910DF07", // 集群UUID<br>"name": "istio", // 集群名称<br>"status": "running", // 集群状态<br>"totalWorker": 1,<br>"memory": "31.1", // 单位G<br>"core": "16.0", // 单位核<br>"k8sVersion": "v1.19.10",<br>"avilableWorker": 1, // 可用工作节点数<br>"clusterType": "kaasCluster", // 集群类型，共享集群(k8sCluster)或KaaS集群(kaasCluster)<br>"app_groups": [ // 部署的应用信息<br>{<br>"name": "11", // 应用组名称<br>"id": "f44ad874-263b-4ee7-b45a-3fdeb0980b54", // 应用组id<br>"apps": [ // 此应用组下应用信息<br>{<br>"name": "test", // 应用名称<br>"id": "98bf7cd4496b42df9e6bd04eedeb190e" // 应用id<br>},<br>{<br>"name": "library-book-istio",<br>"id": "ba7179754a784866926b3908c2d109b5"<br>}<br>]<br>}<br>]<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 修改应用描述

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{appUuid}/description | JSON | PUT | 修改应用描述 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appUuid | true | String | Url路径中填写应用id |
| description | true | String | 描述信息 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 修改是否成功 | 修改成功为success，失败时为空 |
| total | Integer | -- | 该接口返回1 |
| 例如：<br>{"msg":"","code":"","data":"success","status":true,"total":1\} |  |  |  |

#### 删除应用升级记录

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{appUuid}/batchDel/versions | JSON | POST | 删除应用升级记录 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appUuid | true | String | Url路径中填写应用id |

###### 请求体

| -- | true | List<String> | 版本列表 |
| --- | --- | --- | --- |
| 例如：<br>["88cb5dc3bc824a7794d63fd01b12181f"] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除结果 | 删除是否成功 |
| total | Integer | -- | 该接口返回1 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 支持主机应用重启单个服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/ cloud/app-mgmt/v1.0/restart/appInst | JSON | POST | 支持主机应用重启单个服务 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| releaseId | true | String | 应用的releaseId |
| instanceId | true | String | 主机ID |
| uuid | true | String | 应用的唯一ID |
| name | true | String | 主机名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 重启结果 | 重启是否成功 |
| total | Integer | -- | 该接口返回1 |
| 例如：<br>{"msg":"","code":"","data":"success","status":true,"total":1\} |  |  |  |

#### 容器应用实例列表监控信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/{appUuid}/instances | JSON | GET | 容器应用实例列表监控信息 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appUuid | true | String | 应用Id |
| duration | true | Int | 回溯时长 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 容器应用每个实例列表监控信息 | 返回结果 |
| total | Integer | 列表总数 | 列表总数 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"name": "web-1-b6598bb5d-pf5dz",<br>"ready": true,<br>"podIP": "10.243.1.106",<br>"restartTime": 0,<br>"namespace": "spaceg21sry2k",<br>"containers": [<br>{<br>"name": "web-1",<br>"image":"os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/vue-demo-nginx1.20:3-1648201370393",<br>"ready": true,<br>"container_id": "2e6aa5843d9bd87df6253f5d3e2b175c35cd5808726cf4773f7b3a722da2a664",<br>"cpu_usage": 0.0,<br>"memory_usage": 2.17578125,<br>"running_time": 235092644,<br>"total_cpu": 100.0,<br>"total_memory": 100.0,<br>"request_cpu": 100.0,<br>"request_memory": 100.0,<br>"health_check": {<br>"livenessProbe": null,<br>"readinessProbe": null<br>},<br>"command": [],<br>"env": [<br>{<br>"name": "a",<br>"value": "1"<br>}<br>],<br>"volumn": [<br>{<br>"mountPath": "/usr/share/nginx/conf/nginx.conf",<br>"name": "web-1syspresetweb",<br>"subPath": "nginx.conf"<br>}<br>],<br>"config_map": []<br>}<br>],<br>"node_ip": "paas-dev-w31189(10.125.31.189)",<br>"created_at": "2022-03-25 17:43:59",<br>"status": "Running",<br>"up_time": 235092644,<br>"container_count": 1,<br>"cpu_info": {<br>"request": 100.0,<br>"total": 100.0,<br>"usage": 0.0<br>},<br>"mem_info": {<br>"request": 100.0,<br>"total": 100.0,<br>"usage": 2.17578125<br>},<br>"azone_id": "17d0d673-e",<br>"ready_container_count": 1<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 应用报表

#### 查询应用报表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/apps/report | JSON | POST | 查询应用报表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| tenantIdIn | False | Array | 需要查询的组织ID |
| userName | False | String | 需要查询的用户名 |
| appName | False | String | 需要查询的应用名 |
| resourceType | False | String | 需要查询的资源类型，可选值为container,vm |
| appGroupId | False | String | 需要查询的应用组ID |
| startTime | True | String | 开始时间 |
| endTime | True | String | 结束时间 |
| page | True | Integer | 查询第几页，从1开始 |
| size | True | Integer | 查询每页的条目数 |
| updatedAt | True | String | 应用排序方式，根据应用更新时间升序(ascending)或降序(descending)，默认值"descending"，可选值ascending,descending |
| 例如：<br>{<br>"tenantIdIn": ["9052ae85792143ff955c35c52e8e41bd", "626f77e39c114148aedd80f9fcef37b2"],<br>"userName": "user",<br>"appName": "trace2",<br>"resourceType": "container",<br>"appGroupId": "60cb1091-e178-4a5f-b5ed-252f5fba723e",<br>"startTime": "2022-03-21T16:00:00.000Z",<br>"endTime": "2022-03-22T16:00:00.000Z",<br>"page": 1,<br>"size": 10,<br>"updatedAt": "descending"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用报表对象 |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [{<br>"id": "0ddc9ef9046443d3b334c33e49a033ac",<br>"name": "podinfo-spaceel0bixvs",<br>"status": "OK",<br>"resourceType": "container",<br>"appGroup": "ttttt",<br>"instanceNum": 2,<br>"instanceOkNum": 2,<br>"cpu": {<br>"avgValue": "0.002",<br>"maxValue": "0.0035",<br>"avgUsage": "2.0",<br>"maxUsage": "3.5"<br>},<br>"memory": {<br>"avgValue": "8.9375",<br>"maxValue": "9.575",<br>"avgUsage": "3.4912109375",<br>"maxUsage": "3.740234375"<br>},<br>"resources": {<br>"requests": {<br>"memory": "32.0Mi",<br>"cpu": "10.0m"<br>},<br>"limits": {<br>"memory": "256.0Mi",<br>"cpu": "100.0m"<br>}<br>},<br>"scale": {}<br>},{<br>"id": "f52822be0f424987b36d7761d483cf00",<br>"name": "trace3",<br>"status": "OK",<br>"resourceType": "container",<br>"appGroup": "sw-test",<br>"instanceNum": 1,<br>"instanceOkNum": 1,<br>"cpu": {<br>"avgValue": "0.008",<br>"maxValue": "0.063",<br>"avgUsage": "1.6",<br>"maxUsage": "12.6"<br>},<br>"memory": {<br>"avgValue": "248.907",<br>"maxValue": "251.15",<br>"avgUsage": "62.22675",<br>"maxUsage": "62.7875"<br>},<br>"resources": {<br>"requests": {<br>"memory": "200.0Mi",<br>"cpu": "500.0m"<br>},<br>"limits": {<br>"memory": "400.0Mi",<br>"cpu": "500.0m"<br>}<br>},<br>"scale": {}<br>}],<br>"status": true,<br>"total": 52<br>\} |  |  |  |

#### 导出应用报表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app-mgmt/v1.0/apps/report/export | QUERY | GET | 导出应用报表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| tenantIdIn | False | Array | 需要查询的组织ID |
| userName | False | String | 需要查询的用户名 |
| appName | False | String | 需要查询的应用名 |
| resourceType | False | String | 需要查询的资源类型，可选值为container,vm |
| appGroupId | False | String | 需要查询的应用组ID |
| startTime | True | String | 需要查询的开始时间 |
| endTime | True | String | 需要查询的结束时间 |
| page | True | Integer | 查询第几页，从1开始 |
| size | False | Integer | 查询每页的条目数，不传递表示查询所有 |
| updatedAt | True | String | 应用排序方式，根据应用更新时间升序(ascending)或降序(descending)，可选值ascending,descending |
| 例如：<br>&tenantIdIn=9052ae85792143ff955c35c52e8e41bd,626f77e39c114148aedd80f9fcef37b2&userName=user<br>&appName=trace2&resourceType=vm&appGroupId=60cb1091-e178-4a5f-b5ed-252f5fba723e<br>&startTime=2022-03-21T16:00:00.000Z<br>&endTime=2022-03-22T16:00:00.000Z&page=1&size=10&updatedAt=descending |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回csv文件 |  |  |  |

### 应用环境管理

#### 创建应用环境

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs | JSON | POST | 创建应用环境 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Name | True | String | 指定要创建的应用环境名称 |
| Label | True | String | 指定要创建的应用环境的标签 |
| Description | False | String | 应用环境的描述信息 |
| Resources | True | JsonNode | 应用环境包含的资源信息，示例：<br>{<br>"type":"engine",  //资源类型，包含：engine, vm, container, database<br>"id":"pismjojim85zgvdzszqt",  //资源唯一id<br>"name": "testengine",   // 资源名称<br>"subType":"springcloud"  //资源子类型，type为engine时可选值有spring cloud, istio, dubbo, type为vm时，可选值为iaas_platform, type为container时，可选值为k8sCluster, kaasCluster<br>\} |
| 例如：<br>{<br>"name":"test app environent",<br>"label":"develop",<br>"description":"for dev and test ...",<br>"resources":[<br>{<br>"type":"engine",<br>"id":"pismjojim85zgvdzszqt",<br>"subType":"springcloud"<br>},<br>{<br>"type":"container",<br>"id":"cdb3278e-2",<br>"subType":"k8sCluster"<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含创建好的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"env_id": "33cd0ead-68b3-423b-bfde-3bf6e2f1820d"<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 更新应用环境

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs | JSON | PUT | 更新应用环境 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Name | True | String | 指定要创建的应用环境名称 |
| Label | True | String | 指定要创建的应用环境的标签 |
| Description | False | String | 应用环境的描述信息 |
| Resources | True | JsonNode | 应用环境包含的资源信息，示例：<br>{<br>"type":"engine",  //资源类型，包含：engine, vm, container, database<br>"id":"pismjojim85zgvdzszqt",  //资源唯一id<br>"name": "testengine",   // 资源名称<br>"subType":"springcloud"  //资源子类型，type为engine时可选值有spring cloud, istio, dubbo, type为vm时，可选值为iaas_platform, type为container时，可选值为k8sCluster, kaasCluster<br>\} |
| 例如：<br>{<br>"name":"test app environent",<br>"label":"develop",<br>"description":"for dev and test ...",<br>"resources":[<br>{<br>"type":"engine",<br>"id":"pismjojim85zgvdzszqt",<br>"subType":"springcloud"<br>},<br>{<br>"type":"container",<br>"id":"cdb3278e-2",<br>"subType":"k8sCluster"<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含创建好的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"env_id": "33cd0ead-68b3-423b-bfde-3bf6e2f1820d"<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除应用环境

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs/{envId\} | JSON | DELETE | 删除应用环境 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| EnvId | True | String | 指定要删除的应用环境ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含已删除的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"env_id": "33cd0ead-68b3-423b-bfde-3bf6e2f1820d"<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询应用环境列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs | JSON | GET | 查询应用环境列表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | False | String | 支持通过名称查询 |
| label | False | String | 支持通过标签查询 |
| description | False | String | 支持通过描述信息查询 |
| status | False | String | 支持通过状态查询 |
| sort | False | String | 支持结果按更新时间排序，默认按照更新时间降序排序 |
| page | False | String | 分页页码 |
| size | False | String | 分页大小 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含已删除的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"name": "可选资源",<br>"id": "9cd1dea5-f8ac-4736-b8d7-1560d3b7306b",<br>"label": "开发环境",<br>"description": "开发环境",<br>"createdBy": "admin",<br>"createTime": "2022-01-11 02:30:11",<br>"updateTime": "2022-01-11 02:30:11",<br>"status": "PartNormal",<br>"resources": [<br>{<br>"name": "default",<br>"id": "default",<br>"type": "container",<br>"subType": "k8sCluster",<br>"status": "Normal",<br>"description": "",<br>"ext": {<br>"uuid": "default",<br>"name": "default",<br>"description": "default zone",<br>"clusterId": "123456",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createTime": "2021-12-27 21:51:51",<br>"projectName": "root",<br>"clusterName": "Default",<br>"labels": [<br>"default"<br>],<br>"nodes": 3,<br>"status": 1<br>},<br>"envs": []<br>},<br>{<br>"name": null,<br>"id": "78cc2f0bfaef4abcafbe",<br>"type": "engine",<br>"subType": "dubbo",<br>"status": "ToBeDeleted",<br>"description": "",<br>"ext": null,<br>"envs": []<br>},<br>{<br>"name": "ch-engine",<br>"id": "pismjojim85zgvdzszqt",<br>"type": "engine",<br>"subType": "Spring Cloud",<br>"status": "Normal",<br>"description": "",<br>"ext": {<br>"id": "pismjojim85zgvdzszqt",<br>"name": "ch-engine",<br>"clusterId": "123456",<br>"description": "",<br>"engineType": "Spring Cloud",<br>"state": "RUNNING",<br>"zone": "PaaS"<br>},<br>"envs": [<br>{<br>"name": "REGISTRY_TYPE",<br>"value": "eureka",<br>"type": "system"<br>},<br>{<br>"name": "EUREKA_URL",<br>"value": "http://10.125.29.149:30600/eureka",<br>"type": "system"<br>},<br>{<br>"name": "CONFIG_URL",<br>"value": "http://10.125.29.150:31488",<br>"type": "system"<br>},<br>{<br>"name": "DB_MASTER_HOST",<br>"value": "10.125.29.146",<br>"type": "custom"<br>},<br>{<br>"name": "DB_MASTER_PORT",<br>"value": "3306",<br>"type": "custom"<br>},<br>{<br>"name": "DB_MASTER_DATABASE",<br>"value": "review_test",<br>"type": "custom"<br>},<br>{<br>"name": "DB_USERNAME",<br>"value": "os_admin",<br>"type": "custom"<br>},<br>{<br>"name": "DB_PASSWORD",<br>"value": "H3C_Cloud0S#PaaS@CTT2019",<br>"type": "custom"<br>},<br>{<br>"name": "DB_DRIVER_NAME",<br>"value": "com.mysql.cj.jdbc.Driver",<br>"type": "custom"<br>}<br>]<br>}<br>]<br>}<br>],<br>"status": true,<br>"total": 4<br>\} |  |  |  |

#### 查询应用环境详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs/{envId\} | JSON | GET | 查询应用环境详情 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| EnvId | True | String | 指定要查询的应用环境ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含已删除的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"name": "test app environment",<br>"id": "7b9f480d-b171-4531-a8f7-81685ba65872",<br>"label": "develop",<br>"description": "develop",<br>"createdBy": "admin",<br>"createTime": "2022-01-05 14:10:55",<br>"updateTime": "2022-01-05 14:10:55",<br>"status": "Normal",<br>"resources": [<br>{<br>"name": "ch-engine",<br>"id": "pismjojim85zgvdzszqt",<br>"type": "engine",<br>"subType": "Spring Cloud",<br>"status": "Normal",<br>"description": "",<br>"ext": {<br>"id": "pismjojim85zgvdzszqt",<br>"name": "ch-engine",<br>"clusterId": "123456",<br>"description": "",<br>"engineType": "Spring Cloud",<br>"state": "RUNNING",<br>"zone": "PaaS"<br>},<br>"envs": [<br>{<br>"key": "REGISTRY_TYPE",<br>"value": "eureka",<br>"type": "system"<br>},<br>{<br>"key": "EUREKA_URL",<br>"value": "http://10.125.29.149:30600/eureka",<br>"type": "system"<br>},<br>{<br>"key": "CONFIG_URL",<br>"value": "http://10.125.29.145:31488",<br>"type": "system"<br>},<br>{<br>"key": "DB_MASTER_HOST",<br>"value": "10.125.29.146",<br>"type": "custom"<br>},<br>{<br>"key": "DB_MASTER_PORT",<br>"value": "3306",<br>"type": "custom"<br>},<br>{<br>"key": "DB_MASTER_DATABASE",<br>"value": "review_test",<br>"type": "custom"<br>},<br>{<br>"key": "DB_USERNAME",<br>"value": "os_admin",<br>"type": "custom"<br>},<br>{<br>"key": "DB_PASSWORD",<br>"value": "H3C_Cloud0S#PaaS@CTT2019",<br>"type": "custom"<br>},<br>{<br>"key": "DB_DRIVER_NAME",<br>"value": "com.mysql.cj.jdbc.Driver",<br>"type": "custom"<br>}<br>]<br>},<br>{<br>"name": "PaaS",<br>"id": "cdb3278e-2",<br>"type": "container",<br>"subType": "k8sCluster",<br>"status": "Normal",<br>"description": "",<br>"ext": {<br>"uuid": "cdb3278e-2",<br>"name": "PaaS",<br>"description": "樊公子",<br>"clusterId": "123456",<br>"projectId": "e98918042562423eae000d5e6ea6c264",<br>"createTime": "2021-11-27 09:55:11",<br>"projectName": "PaaS_带中文",<br>"clusterName": "Default",<br>"labels": [<br>"cdb3278e-2"<br>],<br>"nodes": 1,<br>"status": 1<br>},<br>"envs": []<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询可用资源列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs/resource | JSON | GET | 查询应用环境资源 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| type | True | String | 指定需要查询的资源类型，可选值: vm, container, database, engine, mysql, redis |
| subType | False | String | 指定需要查询的资源子类型，当type为container时subType可选值为：k8sCluster, kaasCluster；当type为database时，subType可选值为mysql,redis;当type为engine时，subType可选值为” Spring Cloud”, dubbo, istio |
| page | False | Int | 指定分页页码 |
| size | False | Int | 指定分页大小 |
| name | False | String | 指定要搜索资源的名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含已删除的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"name": "dubboadmin3test",<br>"id": "f1b8ab4b43cd4469b9d0",<br>"type": "engine",<br>"subType": "dubbo",<br>"status": "Normal",<br>"description": "dubbof1bbc37b",<br>"ext": {<br>"id": "f1b8ab4b43cd4469b9d0",<br>"name": "dubboadmin3test",<br>"clusterId": "123456",<br>"description": "dubbof1bbc37b",<br>"engineType": "dubbo",<br>"state": "RUNNING",<br>"zone": "PaaS"<br>},<br>"envs": []<br>},<br>{<br>"name": "dubboadmin2test",<br>"id": "675d1248e7514c55951e",<br>"type": "engine",<br>"subType": "dubbo",<br>"status": "Normal",<br>"description": "dubbo9e2d353b",<br>"ext": {<br>"id": "675d1248e7514c55951e",<br>"name": "dubboadmin2test",<br>"clusterId": "123456",<br>"description": "dubbo9e2d353b",<br>"engineType": "dubbo",<br>"state": "RUNNING",<br>"zone": "PaaS"<br>},<br>"envs": []<br>}<br>],<br>"status": true,<br>"total": 20\} |  |  |  |

#### 查询应用环境已有资源

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs/{envId}/resource | JSON | GET | 查询应用环境资源 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| envId | True | String | 指定应用环境Id |
| type | True | String | 指定需要查询的资源类型，可选值: vm, container, database, engine, mysql, redis |
| subType | False | String | 指定需要查询的资源子类型，当type为container时subType可选值为：k8sCluster, kaasCluster；当type为database时，subType可选值为mysql,redis;当type为engine时，subType可选值为” Spring Cloud”, dubbo, istio |
| page | False | Int | 指定分页页码 |
| size | False | Int | 指定分页大小 |
| name | False | String | 指定要搜索资源的名称 |
| status | False | String | 资源状态，可选值：normal, abnormal, tobedeleted, unkonw |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含已删除的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg":"",<br>"code":"",<br>"data":[<br>{<br>"name":"主集群工作节点",<br>"id":"929f1d01-f",<br>"type":"container",<br>"subType":"k8sCluster",<br>"status":"normal",<br>"createdTime":1646226294000,<br>"description":"",<br>"ext":{<br>"uuid":"929f1d01-f",<br>"name":"主集群工作节点",<br>"description":"",<br>"clusterId":"123456",<br>"projectId":"9052ae85792143ff955c35c52e8e41bd",<br>"createTime":"2022-02-28 21:38:22",<br>"projectName":"root",<br>"clusterName":"Default",<br>"labels":[<br>"929f1d01-f"<br>],<br>"nodes":1,<br>"status":1<br>},<br>"envs":[<br>]<br>},<br>{<br>"name":"NEO-SC-F",<br>"id":"ezekaqrvscvrmti93joq",<br>"type":"engine",<br>"subType":"Spring Cloud",<br>"status":"abnormal",<br>"createdTime":1646226294000,<br>"description":"",<br>"ext":{<br>"id":"ezekaqrvscvrmti93joq",<br>"name":"NEO-SC-F",<br>"clusterId":"123456",<br>"description":"",<br>"engineType":"Spring Cloud",<br>"state":"ERROR",<br>"zone":"主集群工作节点"<br>},<br>"envs":[<br>]<br>}<br>],<br>"status":true,<br>"total":2<br>\} |  |  |  |

#### 添加资源环境变量

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs/{envId}/resource/{resId}/envs | JSON | POST | 添加资源环境变量 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| resId | True | String | 指定要添环境变量的资源ID |
| requestBody | True | Json | 需要添加的环境变量列表 |
| 示例：<br>[<br>{<br>"key": "URL",<br>"value": "http://xxx.xxx.com"<br>},<br>{<br>"key": "DATA_TYPE",<br>"value": "mysql"<br>}<br>] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含已删除的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询资源环境变量列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs/{envId}/resource/{resId}/envs | JSON | GET | 查询资源环境变量列表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| envId | True | String | 指定资源所属应用环境ID |
| resId | True | String | 指定要添环境变量的资源ID |
| type | True | String | 指定资源类型 |
| subType | True | String | 指定资源子类型 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含已删除的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"key": "REGISTRY_TYPE",<br>"value": "eureka123",<br>"type": "system"<br>},<br>{<br>"key": "EUREKA_URL",<br>"value": "http://10.125.29.148:30600/eureka",<br>"type": "system"<br>},<br>{<br>"key": "CITY",<br>"value": "XIAN",<br>"type": "customer"<br>}<br>],<br>"status": true,<br>"total": 6<br>\} |  |  |  |

#### 删除资源环境变量

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app-mgmt/v1.0/appenvs/{envId}/resource/{resId}/envs?key=${key\} | JSON | DELETE | 查询资源环境变量列表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| resId | True | String | 指定要添环境变量的资源ID |
| envId | True | String | 指定所属的应用环境ID |
| key | True | String | 指定要删除的环境变量的key |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Code | String | 错误码 | 正常成功情况下，code为空串 |
| Msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| Data | Json | 具体返回结果体 | 返回应用环境对象，包含已删除的应用环境id |
| Total | Integer | 应用列表总数 | 应用列表总数 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data":"",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### CICD应用包构建记录

#### 查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/appData | JSON | GET | 获取应用包构建记录 |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| executionId | query | 否 | long | 流水线执行记录id |
| jobExecutionId | query | 否 | long | 构建执行记录id |
| page | query | 否 | number | 分页页码 |
| size | query | 否 | number | 分页大小 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| 例如：<br>{<br>"code": "string",<br>"data": [<br>{<br>"createAt": "2020-01-02T03:34:30.185Z",<br>"executionId": 0,<br>"id": 0,<br>"indexInStage": 0,<br>"jobExecutionId": 0,<br>"packageName": "string",<br>"packageType": "PAAS_HELM",<br>"packageUuid": "string",<br>"packageVersion": "string",<br>"pipelineId": 0,<br>"stageIndex": 0<br>}<br>],<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 创建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/appData | JSON | POST | 创建应用包构建记录 |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| 请求体 | body | 是 | json |  |
| {<br>"executionId": 0,<br>"indexInStage": 0,<br>"jobExecutionId": 0,<br>"packageName": "string",<br>"packageType": "PAAS_HELM",<br>"packageUuid": "string",<br>"packageVersion": "string",<br>"pipelineId": 0,<br>"stageIndex": 0<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| 例如：<br>{<br>"code": "string",<br>"data": {<br>"createAt": "2020-01-02T03:34:30.202Z",<br>"executionId": 0,<br>"id": 0,<br>"indexInStage": 0,<br>"jobExecutionId": 0,<br>"packageName": "string",<br>"packageType": "PAAS_HELM",<br>"packageUuid": "string",<br>"packageVersion": "string",<br>"pipelineId": 0,<br>"stageIndex": 0<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/appData/{id\} | JSON | GET | 应用包构建记录详情 |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| id | path | 是 | long |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| 例如：<br>{<br>"code": "string",<br>"data": {<br>"createAt": "2020-01-02T03:34:30.202Z",<br>"executionId": 0,<br>"id": 0,<br>"indexInStage": 0,<br>"jobExecutionId": 0,<br>"packageName": "string",<br>"packageType": "PAAS_HELM",<br>"packageUuid": "string",<br>"packageVersion": "string",<br>"pipelineId": 0,<br>"stageIndex": 0<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

### CICD流水线构建

#### 查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/executions | JSON | GET | 获取应用包构建记录 |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| instanceId | query | 否 | string | 租户ID |
| pipelineId | query | 否 | long | 流水线ID |
| projectId | query | 否 | string | 项目ID |
| query | query | 否 | string | 查询关键字 |
| creatorId | query | 否 | string | 创建者 |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| page | query | 否 | number | 请求页 |
| size | query | 否 | number | 请求页大小 |
| sort | query | 否 | string | 排序 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| 例如：<br>{<br>"code": "string",<br>"data": [<br>{<br>"attributes": {},<br>"beginTime": "2020-01-02T03:34:30.224Z",<br>"buildNumber": 0,<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"cause": "string",<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2020-01-02T03:34:30.224Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2020-01-02T03:34:30.224Z"<br>}<br>],<br>"createTime": "2020-01-02T03:34:30.224Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"jobDefinitions": [<br>{<br>"externalId": 0,<br>"name": "string",<br>"parameters": {}<br>}<br>],<br>"parameters": {},<br>"stages": [<br>{<br>"index": 0,<br>"jobs": [<br>{<br>"clusterSelectors": [<br>{}<br>],<br>"externalJob": true,<br>"id": 0,<br>"indexInStage": 0,<br>"jobDefinitionName": "string",<br>"jobExecution": {<br>"attributes": {},<br>"beginTime": "2020-01-02T03:34:30.224Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2020-01-02T03:34:30.224Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2020-01-02T03:34:30.224Z"<br>}<br>],<br>"createTime": "2020-01-02T03:34:30.224Z",<br>"creatorId": "string",<br>"endTime": "2020-01-02T03:34:30.224Z",<br>"executionId": 0,<br>"finished": true,<br>"id": 0,<br>"indexInStage": 0,<br>"instanceId": "string",<br>"jobId": 0,<br>"lastScheduleTime": "2020-01-02T03:34:30.224Z",<br>"lastSyncTime": "2020-01-02T03:34:30.224Z",<br>"log": "string",<br>"name": "string",<br>"parameters": {},<br>"pipelineId": 0,<br>"projectId": "string",<br>"stageIndex": 0,<br>"standalone": true,<br>"status": "PENDING"<br>},<br>"jobId": 0,<br>"name": "string",<br>"parameterGroup": "string",<br>"startMode": "AUTO",<br>"workerSpecs": {}<br>}<br>],<br>"name": "string",<br>"stageStatus": {<br>"log": "string",<br>"status": "PENDING"<br>}<br>}<br>]<br>},<br>"elapse": 0,<br>"endTime": "2020-01-02T03:34:30.224Z",<br>"finished": true,<br>"id": 0,<br>"instanceId": "string",<br>"lastStageBeginTime": "2020-01-02T03:34:30.225Z",<br>"log": "string",<br>"note": "string",<br>"parameters": {},<br>"parentExecutionId": 0,<br>"pipelineId": 0,<br>"projectId": "string",<br>"status": "PENDING",<br>"uploadedParameters": {}<br>}<br>],<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 创建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/executions | JSON | POST | 创建流水线构建 |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| 请求体 | body | 是 | json |  |
| {<br>"attributes": {},<br>"cause": "string",<br>"instanceId": "string",<br>"note": "string",<br>"parameterValues": [<br>{<br>"location": {},<br>"name": "string",<br>"value": "string"<br>}<br>],<br>"parameters": {},<br>"parentExecutionId": 0,<br>"pipelineId": 0,<br>"systemParameters": {},<br>"triggerType": "MANUAL",<br>"triggerUserId": "string"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| 例如：<br>{<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"beginTime": "2021-10-28T07:12:10.090Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"cause": "string",<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2021-10-28T07:12:10.091Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2021-10-28T07:12:10.091Z"<br>}<br>],<br>"createTime": "2021-10-28T07:12:10.091Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"jobDefinitions": [<br>{<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>}<br>],<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"stages": [<br>{<br>"index": 0,<br>"jobs": [<br>{<br>"assignType": "string",<br>"category": "BUILD",<br>"clusterSelectors": [<br>{}<br>],<br>"externalJob": true,<br>"id": 0,<br>"indexInStage": 0,<br>"jobDefinition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"jobDefinitionName": "string",<br>"jobExecution": {<br>"attributes": {},<br>"beginTime": "2021-10-28T07:12:10.091Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2021-10-28T07:12:10.091Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2021-10-28T07:12:10.091Z"<br>}<br>],<br>"createTime": "2021-10-28T07:12:10.091Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"effectiveParameters": [<br>{<br>"defaultValue": "string",<br>"description": "string",<br>"effectiveAssignType": "DEFAULT",<br>"effectiveValue": "string",<br>"group": "USER",<br>"ignoredWhenRerun": true,<br>"lastUpdatedValue": "string",<br>"name": "string",<br>"required": true,<br>"specifiedValue": "string",<br>"type": "string"<br>}<br>],<br>"elapse": 0,<br>"endTime": "2021-10-28T07:12:10.091Z",<br>"executionId": 0,<br>"finished": true,<br>"id": 0,<br>"indexInStage": 0,<br>"instanceId": "string",<br>"jobId": 0,<br>"lastScheduleTime": "2021-10-28T07:12:10.091Z",<br>"lastSyncTime": "2021-10-28T07:12:10.091Z",<br>"name": "string",<br>"note": "string",<br>"parameters": {},<br>"pipelineId": 0,<br>"projectId": "string",<br>"stageIndex": 0,<br>"standalone": true,<br>"status": "PENDING"<br>},<br>"name": "string",<br>"parameterValues": [<br>{<br>"location": {},<br>"name": "string",<br>"value": "string"<br>}<br>],<br>"startMode": "AUTO",<br>"workerSpecs": {}<br>}<br>],<br>"name": "string",<br>"parallel": true,<br>"stageStatus": {<br>"beginTime": "2021-10-28T07:12:10.091Z",<br>"elapse": 0,<br>"endTime": "2021-10-28T07:12:10.091Z",<br>"log": "string",<br>"status": "PENDING"<br>}<br>}<br>]<br>},<br>"effectiveParameters": [<br>{<br>"defaultValue": "string",<br>"description": "string",<br>"effectiveAssignType": "DEFAULT",<br>"effectiveValue": "string",<br>"group": "USER",<br>"ignoredWhenRerun": true,<br>"lastUpdatedValue": "string",<br>"name": "string",<br>"required": true,<br>"specifiedValue": "string",<br>"type": "string"<br>}<br>],<br>"elapse": 0,<br>"endTime": "2021-10-28T07:12:10.091Z",<br>"finished": true,<br>"id": 0,<br>"instanceId": "string",<br>"lastStageBeginTime": "2021-10-28T07:12:10.091Z",<br>"log": "string",<br>"note": "string",<br>"parameters": {},<br>"parentExecutionId": 0,<br>"pipelineId": 0,<br>"projectId": "string",<br>"status": "PENDING",<br>"systemParameters": {},<br>"uploadedParameters": {}<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/executions/{executionId\} | JSON | DELETE | 删除流水线构建 |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionId | path | 是 | string | 流水线构建Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/executions/{executionId\} | JSON | GET | 流水线构建详情 |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionId | path | 是 | string | 流水线构建Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"beginTime": "2021-10-28T07:12:10.125Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"cause": "string",<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2021-10-28T07:12:10.125Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2021-10-28T07:12:10.125Z"<br>}<br>],<br>"createTime": "2021-10-28T07:12:10.125Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"jobDefinitions": [<br>{<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>}<br>],<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"stages": [<br>{<br>"index": 0,<br>"jobs": [<br>{<br>"assignType": "string",<br>"category": "BUILD",<br>"clusterSelectors": [<br>{}<br>],<br>"externalJob": true,<br>"id": 0,<br>"indexInStage": 0,<br>"jobDefinition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"jobDefinitionName": "string",<br>"jobExecution": {<br>"attributes": {},<br>"beginTime": "2021-10-28T07:12:10.125Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2021-10-28T07:12:10.125Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2021-10-28T07:12:10.125Z"<br>}<br>],<br>"createTime": "2021-10-28T07:12:10.125Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"effectiveParameters": [<br>{<br>"defaultValue": "string",<br>"description": "string",<br>"effectiveAssignType": "DEFAULT",<br>"effectiveValue": "string",<br>"group": "USER",<br>"ignoredWhenRerun": true,<br>"lastUpdatedValue": "string",<br>"name": "string",<br>"required": true,<br>"specifiedValue": "string",<br>"type": "string"<br>}<br>],<br>"elapse": 0,<br>"endTime": "2021-10-28T07:12:10.126Z",<br>"executionId": 0,<br>"finished": true,<br>"id": 0,<br>"indexInStage": 0,<br>"instanceId": "string",<br>"jobId": 0,<br>"lastScheduleTime": "2021-10-28T07:12:10.126Z",<br>"lastSyncTime": "2021-10-28T07:12:10.126Z",<br>"name": "string",<br>"note": "string",<br>"parameters": {},<br>"pipelineId": 0,<br>"projectId": "string",<br>"stageIndex": 0,<br>"standalone": true,<br>"status": "PENDING"<br>},<br>"name": "string",<br>"parameterValues": [<br>{<br>"location": {},<br>"name": "string",<br>"value": "string"<br>}<br>],<br>"startMode": "AUTO",<br>"workerSpecs": {}<br>}<br>],<br>"name": "string",<br>"parallel": true,<br>"stageStatus": {<br>"beginTime": "2021-10-28T07:12:10.126Z",<br>"elapse": 0,<br>"endTime": "2021-10-28T07:12:10.126Z",<br>"log": "string",<br>"status": "PENDING"<br>}<br>}<br>]<br>},<br>"effectiveParameters": [<br>{<br>"defaultValue": "string",<br>"description": "string",<br>"effectiveAssignType": "DEFAULT",<br>"effectiveValue": "string",<br>"group": "USER",<br>"ignoredWhenRerun": true,<br>"lastUpdatedValue": "string",<br>"name": "string",<br>"required": true,<br>"specifiedValue": "string",<br>"type": "string"<br>}<br>],<br>"elapse": 0,<br>"endTime": "2021-10-28T07:12:10.126Z",<br>"finished": true,<br>"id": 0,<br>"instanceId": "string",<br>"lastStageBeginTime": "2021-10-28T07:12:10.126Z",<br>"log": "string",<br>"note": "string",<br>"parameters": {},<br>"parentExecutionId": 0,<br>"pipelineId": 0,<br>"projectId": "string",<br>"status": "PENDING",<br>"systemParameters": {},<br>"uploadedParameters": {}<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 取消执行

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/executions/{executionId}/cancel | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionId | path | 是 | string | 流水线构建Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 从暂停恢复执行

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/executions/{executionId}/continue | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionId | path | 是 | string | 流水线构建Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 查询子流水线日志列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cicd/fluxes/v1.0/executions/queryChildExecutions | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionId | path | 是 | string | 流水线构建Id |
| pipelineId | long | 是 | string | 流水线ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| url: /api/cicd/fluxes/v1.0/executions/queryChildExecutions?<br>pipelineId=1572166604886638592&executeId=1572166623094112256&t=1663669574626<br>{<br>"msg": "",<br>"data": {<br>"id": null,<br>"jobExecutionId": null,<br>"pipelineId": null,<br>"pipelineName": null,<br>"projectId": null,<br>"instanceId": null,<br>"status": null,<br>"cause": null,<br>"creatorId": null,<br>"buildVersion": null,<br>"createTime": null,<br>"beginTime": null,<br>"endTime": null,<br>"cancelInProgress": null,<br>"creatorName": null,<br>"executeUserName": null,<br>"finished": null,<br>"authId": null,<br>"authName": null,<br>"authIp": null,<br>"authUserName": null,<br>"authPassword": null,<br>"domain": null,<br>"children": [{<br>"id": "1572166777297698816",<br>"jobExecutionId": "1572166623459016704",<br>"pipelineId": "1572166428939780096",<br>"pipelineName": "testaaaa1",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"instanceId": "9052ae85792143ff955c35c52e8e41bd",<br>"status": "RUNNING",<br>"cause": "MANUAL",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"buildVersion": "v20220920181223816",<br>"createTime": 1663668743000,<br>"beginTime": 1663668743000,<br>"endTime": null,<br>"cancelInProgress": "false",<br>"creatorName": "admin",<br>"executeUserName": "admin",<br>"finished": "false",<br>"authId": "1",<br>"authName": "P185",<br>"authIp": "10.125.31.185",<br>"authUserName": "admin",<br>"authPassword": "R3cw318r7n8bWtelDN9ZcA%3D%3D",<br>"domain": "default",<br>"children": [],<br>"plat": true<br>}],<br>"plat": false<br>},<br>"code": ""<br>\} |  |  |  |

#### 获取子流水线执行详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/executions/tree | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionId | path | 是 | string | 流水线构建Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| url: /cicd/fluxes/v1.0/executions/tree?<br>executionPath=-1@1,1@1572166777297698816&t=1663670262690<br>{<br>"msg": "",<br>"data": {<br>"attributes": {<br>"executeUserName": "admin",<br>"pipelineName": "testaaaa1",<br>"userName": "admin"<br>},<br>"id": "1572166777297698816",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"pipelineId": "1572166428939780096",<br>"instanceId": "9052ae85792143ff955c35c52e8e41bd",<br>"parameters": [],<br>"systemParameters": {<br>"TRIGGER_TYPE": "MANUAL",<br>"BUILD_VERSION": "v20220920181223816"<br>},<br>"uploadedParameters": {<br>"BUILD_VERSION": "v20220920181223816"<br>},<br>"status": "SUCCESS",<br>"buildVersion": "v20220920181223816",<br>"createTime": 1663668743000,<br>"beginTime": 1663668743000,<br>"endTime": 1663668785000,<br>"lastStageBeginTime": null,<br>"cause": "MANUAL",<br>"note": null,<br>"elapse": "0",<br>"log": null,<br>"parentExecutionId": null,<br>"definition": {<br>"jobDefinitions": [{<br>"type": "FLUXES",<br>"name": "test1-1560158054647857152",<br>"externalId": "1560158054647857152",<br>"workerSpecs": null,<br>"tasks": [{<br>"body": {<br>"script": "echo 111",<br>"type": "SHELL"<br>},<br>"type": "SHELL",<br>"properties": {},<br>"name": "执行shell脚本"<br>}],<br>"parameterDefinitions": [],<br>"parameters": {}<br>}],<br>"stages": [{<br>"name": "aa",<br>"index": 0,<br>"parallel": true,<br>"jobs": [{<br>"id": "1572166428948168704",<br>"indexInStage": 0,<br>"clusterSelectors": null,<br>"workerSpecs": null,<br>"name": "job1",<br>"jobDefinitionName": "test1-1560158054647857152",<br>"category": "BUILD",<br>"startMode": "AUTO",<br>"parameterValues": [],<br>"jobDefinition": {<br>"type": "FLUXES",<br>"name": "test1-1560158054647857152",<br>"externalId": "1560158054647857152",<br>"workerSpecs": null,<br>"tasks": [{<br>"body": {<br>"script": "echo 111",<br>"type": "SHELL"<br>},<br>"type": "SHELL",<br>"properties": {},<br>"name": "执行shell脚本"<br>}],<br>"parameterDefinitions": [],<br>"parameters": {}<br>},<br>"jobExecution": {<br>"attributes": null,<br>"id": "1572166777608077312",<br>"name": "job1",<br>"jobId": "1560158054647857152",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"pipelineId": "1572166428939780096",<br>"instanceId": "9052ae85792143ff955c35c52e8e41bd",<br>"executionId": "1572166777297698816",<br>"buildVersion": "v20220920181223816",<br>"status": "SUCCESS",<br>"stageIndex": 0,<br>"indexInStage": 0,<br>"finished": true,<br>"beginTime": 1663668743000,<br>"endTime": 1663668785000,<br>"creatorId": null,<br>"lastSyncTime": 1663668785000,<br>"lastScheduleTime": 1663668743000,<br>"createTime": 1663668743000,<br>"standalone": false,<br>"cancelInProgress": false,<br>"creatorName": null,<br>"note": "pipeline: testaaaa1 1572166428939780096, execution: 1572166777297698816 v20220920181223816",<br>"children": null,<br>"codeVersions": null,<br>"elapse": "42000",<br>"effectiveParameters": [],<br>"parameters": []<br>},<br>"jobId": "1560158054647857152",<br>"externalJobName": "test1",<br>"assignType": "STANDALONE",<br>"externalJob": true<br>}],<br>"stageStatus": {<br>"status": "SUCCESS",<br>"log": null,<br>"beginTime": 1663668743000,<br>"endTime": 1663668785000,<br>"elapse": "42000"<br>}<br>}],<br>"parameterDefinitions": [],<br>"parameters": {}<br>},<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"cancelInProgress": false,<br>"creatorName": "admin",<br>"codeVersions": [],<br>"childExecution": null,<br>"finished": true,<br>"execPipeline": false,<br>"effectiveParameters": [{<br>"group": "SYSTEM",<br>"name": "TRIGGER_TYPE",<br>"description": "",<br>"effectiveValue": "MANUAL",<br>"effectiveAssignType": "SYSTEM_SPECIFIED",<br>"defaultValue": null,<br>"specifiedValue": "MANUAL",<br>"lastUpdatedValue": null,<br>"type": "STRING",<br>"required": false,<br>"ignoredWhenRerun": true<br>}, {<br>"group": "SYSTEM",<br>"name": "BUILD_VERSION",<br>"description": "a version identifier for each build",<br>"effectiveValue": "v20220920181223816",<br>"effectiveAssignType": "SYSTEM_SPECIFIED",<br>"defaultValue": null,<br>"specifiedValue": "v20220920181223816",<br>"lastUpdatedValue": "v20220920181223816",<br>"type": "STRING",<br>"required": false,<br>"ignoredWhenRerun": false<br>}]<br>},<br>"code": ""<br>\} |  |  |  |

#### 获取子流水线执行日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/ paas_proxies/job_execution_log | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| jobExecutionId | Query | 是 | string | 任务Id |
| ExecutionPath | Query | 是 | String | 子流水线路径拼接 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| {<br>"code": "string",<br>"msg": "string",<br>"data": {<br>"log":"string",<br>"hasMore":false,<br>"totalSize":null,<br>"logSourceType":""<br>}<br>\} |  |  |  |

#### 获取子流水线制品数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/appData/tree | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionPath | query | 是 | string | 子流水线全路径参数 |
| page | query | 是 | string | 页数 |
| size | query | 是 | string | 数量 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"msg": "string",<br>"total": 0,<br>"data":[]<br>\} |  |  |  |

#### 获取子流水线质量门禁报告

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/qualitygate-reports/tree | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionId | path | 是 | string | 流水线构建Id |
| executionPath | query | 是 | string | 子流水线全路径参数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| {<br>"code": "string",<br>"msg": "string",<br>"data": {<br>"apiTest":[],<br>"unitTest":[],<br>"codeCheck":[],<br>"effective":true<br>}<br>\} |  |  |  |

#### 从暂停恢复执行

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/executions/{executionId}/continue | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| executionId | path | 是 | string | 流水线构建Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

### CICD任务构建

#### 查询任务构建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_executions | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| instanceId | query | 否 | string | 租户ID |
| pipelineId | query | 否 | long | 流水线ID |
| projectId | query | 否 | string | 项目ID |
| executionId | query | 否 | long | 流水线构建id |
| jobId | query | 否 | long | 任务id |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| page | query | 否 | number | 请求页 |
| size | query | 否 | number | 请求页大小 |
| sort | query | 否 | string | 排序 |
| order | query | 否 | string | 排序方向，DESC\|ASC |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": [<br>{<br>"attributes": {},<br>"beginTime": "2020-01-02T03:34:30.356Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2020-01-02T03:34:30.356Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2020-01-02T03:34:30.356Z"<br>}<br>],<br>"createTime": "2020-01-02T03:34:30.356Z",<br>"creatorId": "string",<br>"endTime": "2020-01-02T03:34:30.356Z",<br>"executionId": 0,<br>"finished": true,<br>"id": 0,<br>"indexInStage": 0,<br>"instanceId": "string",<br>"jobId": 0,<br>"lastScheduleTime": "2020-01-02T03:34:30.356Z",<br>"lastSyncTime": "2020-01-02T03:34:30.356Z",<br>"log": "string",<br>"name": "string",<br>"parameters": {},<br>"pipelineId": 0,<br>"projectId": "string",<br>"stageIndex": 0,<br>"standalone": true,<br>"status": "PENDING"<br>}<br>],<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 执行任务构建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_executions | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| 请求体 | body | 否 | json |  |
| {<br>"attributes": {},<br>"clusterLabel": "string",<br>"jobId": 0,<br>"note": "string",<br>"parameterValues": [<br>{<br>"location": {},<br>"name": "string",<br>"value": "string"<br>}<br>],<br>"parameters": {}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"beginTime": "2021-10-28T07:51:57.408Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2021-10-28T07:51:57.408Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2021-10-28T07:51:57.408Z"<br>}<br>],<br>"createTime": "2021-10-28T07:51:57.408Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"effectiveParameters": [<br>{<br>"defaultValue": "string",<br>"description": "string",<br>"effectiveAssignType": "DEFAULT",<br>"effectiveValue": "string",<br>"group": "USER",<br>"ignoredWhenRerun": true,<br>"lastUpdatedValue": "string",<br>"name": "string",<br>"required": true,<br>"specifiedValue": "string",<br>"type": "string"<br>}<br>],<br>"elapse": 0,<br>"endTime": "2021-10-28T07:51:57.409Z",<br>"executionId": 0,<br>"finished": true,<br>"id": 0,<br>"indexInStage": 0,<br>"instanceId": "string",<br>"jobId": 0,<br>"lastScheduleTime": "2021-10-28T07:51:57.409Z",<br>"lastSyncTime": "2021-10-28T07:51:57.409Z",<br>"name": "string",<br>"note": "string",<br>"parameters": {},<br>"pipelineId": 0,<br>"projectId": "string",<br>"stageIndex": 0,<br>"standalone": true,<br>"status": "PENDING"<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 任务构建详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_executions/{id\} | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| id | path | 是 | long | 任务构建id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"beginTime": "2021-10-28T07:51:57.442Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2021-10-28T07:51:57.442Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2021-10-28T07:51:57.442Z"<br>}<br>],<br>"createTime": "2021-10-28T07:51:57.442Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"effectiveParameters": [<br>{<br>"defaultValue": "string",<br>"description": "string",<br>"effectiveAssignType": "DEFAULT",<br>"effectiveValue": "string",<br>"group": "USER",<br>"ignoredWhenRerun": true,<br>"lastUpdatedValue": "string",<br>"name": "string",<br>"required": true,<br>"specifiedValue": "string",<br>"type": "string"<br>}<br>],<br>"elapse": 0,<br>"endTime": "2021-10-28T07:51:57.442Z",<br>"executionId": 0,<br>"finished": true,<br>"id": 0,<br>"indexInStage": 0,<br>"instanceId": "string",<br>"jobId": 0,<br>"lastScheduleTime": "2021-10-28T07:51:57.442Z",<br>"lastSyncTime": "2021-10-28T07:51:57.442Z",<br>"name": "string",<br>"note": "string",<br>"parameters": {},<br>"pipelineId": 0,<br>"projectId": "string",<br>"stageIndex": 0,<br>"standalone": true,<br>"status": "PENDING"<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 删除任务构建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_executions/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| id | path | 是 | long | 要删除的任务构建id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 取消任务构建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_executions/{id}/cancel | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| id | path | 是 | long | 要删除的任务构建id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

### CICD任务

#### 查询任务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes /v1.0/jobs | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| query | query | 否 | string | 查询关键字 |
| instanceId | query | 否 | string | 租户ID |
| projectId | query | 否 | string | 项目ID |
| creatorId | query | 否 | string | 创建者id |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| page | query | 否 | number | 请求页 |
| size | query | 否 | number | 请求页大小 |
| sort | query | 否 | string | 排序 |
| order | query | 否 | string | 排序desc\|asc |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": [<br>{<br>"attributes": {},<br>"createTime": "2021-10-28T07:51:57.456Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"description": "string",<br>"executionStats": {<br>"countByStatus": {},<br>"last10ExecutionStatues": [<br>{<br>"beginTime": "2021-10-28T07:51:57.457Z",<br>"buildVersion": "string",<br>"endTime": "2021-10-28T07:51:57.457Z",<br>"executionStatus": "PENDING",<br>"jobExecutionId": 0<br>}<br>]<br>},<br>"id": 0,<br>"instanceId": "string",<br>"name": "string",<br>"projectId": "string",<br>"projectName": "string",<br>"updateTime": "2021-10-28T07:51:57.457Z"<br>}<br>],<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 创建任务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes /v1.0/jobs | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| 请求体 | body | 是 | json |  |
| {<br>"attributes": {},<br>"definition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"description": "string",<br>"name": "string",<br>"projectId": "string"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"createTime": "2021-10-28T07:51:57.466Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"description": "string",<br>"executionStats": {<br>"countByStatus": {},<br>"last10ExecutionStatues": [<br>{<br>"beginTime": "2021-10-28T07:51:57.466Z",<br>"buildVersion": "string",<br>"endTime": "2021-10-28T07:51:57.466Z",<br>"executionStatus": "PENDING",<br>"jobExecutionId": 0<br>}<br>]<br>},<br>"id": 0,<br>"instanceId": "string",<br>"name": "string",<br>"projectId": "string",<br>"projectName": "string",<br>"updateTime": "2021-10-28T07:51:57.466Z"<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 更新任务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes /v1.0/jobs/{id\} | JSON | PUT |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| id | path | 是 | long | 任务id |
| 请求体 | body | 是 | json |  |
| {<br>"attributes": {},<br>"definition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"description": "string",<br>"name": "string"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"createTime": "2021-10-28T07:51:57.519Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"description": "string",<br>"executionStats": {<br>"countByStatus": {},<br>"last10ExecutionStatues": [<br>{<br>"beginTime": "2021-10-28T07:51:57.519Z",<br>"buildVersion": "string",<br>"endTime": "2021-10-28T07:51:57.519Z",<br>"executionStatus": "PENDING",<br>"jobExecutionId": 0<br>}<br>]<br>},<br>"id": 0,<br>"instanceId": "string",<br>"name": "string",<br>"projectId": "string",<br>"projectName": "string",<br>"updateTime": "2021-10-28T07:51:57.519Z"<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 复制任务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes /v1.0/jobs/clone | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| 请求体 | body | 是 | json |  |
| {<br>"attributes": {},<br>"jobId": 0,<br>"name": "string",<br>"targetProjectId": "string"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"createTime": "2021-10-28T07:51:57.490Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"description": "string",<br>"executionStats": {<br>"countByStatus": {},<br>"last10ExecutionStatues": [<br>{<br>"beginTime": "2021-10-28T07:51:57.490Z",<br>"buildVersion": "string",<br>"endTime": "2021-10-28T07:51:57.490Z",<br>"executionStatus": "PENDING",<br>"jobExecutionId": 0<br>}<br>]<br>},<br>"id": 0,<br>"instanceId": "string",<br>"name": "string",<br>"projectId": "string",<br>"projectName": "string",<br>"updateTime": "2021-10-28T07:51:57.490Z"<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 同名检查

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes /v1.0/jobs/names | JSON | POST |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| 请求体 | body | 是 | json |  |
| {<br>"attributes": {},<br>"excludedJobId": 0,<br>"name": "string",<br>"projectId": "string"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 删除任务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes /v1.0/jobs/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| id | path | 是 | long | 任务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 任务详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes /v1.0/jobs/{id\} | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| id | path | 是 | long | 任务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"createTime": "2021-10-28T07:51:57.513Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"externalId": 0,<br>"name": "string",<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {},<br>"workerSpecs": {}<br>},<br>"description": "string",<br>"executionStats": {<br>"countByStatus": {},<br>"last10ExecutionStatues": [<br>{<br>"beginTime": "2021-10-28T07:51:57.513Z",<br>"buildVersion": "string",<br>"endTime": "2021-10-28T07:51:57.513Z",<br>"executionStatus": "PENDING",<br>"jobExecutionId": 0<br>}<br>]<br>},<br>"id": 0,<br>"instanceId": "string",<br>"name": "string",<br>"projectId": "string",<br>"projectName": "string",<br>"updateTime": "2021-10-28T07:51:57.513Z"<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 任务参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes /v1.0/jobs/{id}/parameters | JSON | GET |  |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | header | 是 | string | 当前用户 |
| tenant_id | header | 是 | string | 当前租户 |
| id | path | 是 | long | 任务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回数据 |
| total | Integer | 记录总数 | 记录总数 |
| {<br>"code": "string",<br>"data": {<br>"id": 0,<br>"parameterDefinitions": [<br>{<br>"defaultValue": {},<br>"description": "string",<br>"displayName": "string",<br>"name": "string",<br>"required": true<br>}<br>],<br>"parameters": {}<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

### CICD服务配置相关接口

#### 新建配置接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/connectors/v1.0/config/feature/{projectId\} | JSON | POST | 新建配置接口 |

###### 参数说明

| 参数名 | 参数类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| projectId | path | 是 | long | 组织id |
| 请求体 | body | 是 | json | 配置属性 |
| {<br>"apiVersionLabel": "string",<br>"auth": {<br>"password": "string",<br>"username": "string"<br>},<br>"authTime": "string",<br>"codeId": 0,<br>"configName": "string",<br>"domainId": "string",<br>"domainName": "string",<br>"id": 0,<br>"name": "string",<br>"newSession": true,<br>"page": 0,<br>"projectId": "string",<br>"secret": {<br>"password": "string",<br>"privateKey": "string",<br>"username": "string"<br>},<br>"size": 0,<br>"thirdPartyName": "string",<br>"token": "string",<br>"url": "string",<br>"version": "string"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为200 |
| msg | String | 错误信息 | 正常成功情况下，msg为OK |
| data | Object | 具体返回结果体 | 返回成功数据 |
| {<br>"code": "string",<br>"data": {<br>"apiVersionLabel": "string",<br>"auth": {<br>"password": "string",<br>"username": "string"<br>},<br>"authTime": "string",<br>"codeId": 0,<br>"configName": "string",<br>"domainId": "string",<br>"domainName": "string",<br>"id": 0,<br>"name": "string",<br>"newSession": true,<br>"page": 0,<br>"projectId": "string",<br>"secret": {<br>"password": "string",<br>"privateKey": "string",<br>"username": "string"<br>},<br>"size": 0,<br>"thirdPartyName": "string",<br>"token": "string",<br>"url": "string",<br>"version": "string"<br>},<br>"msg": "string"<br>\} |  |  |  |

#### 查询配置接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/connectors/v1.0/config/feature/{projectId}?configName=&name=&url=& | JSON | GET | 获得服务配置 |

###### 参数说明

| params参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | Path参数 | 项目id |
| projectId | false | Query参数String | 所属项目ID  path |
| configName | false | Query参数String | 配置类型，可选,多选用逗号隔开 ，如：gits,images |
| name | false | Query参数String | 名称 |
| url | false | Query参数String | 链接 |
| page | false | Query参数interger | 分页参数,和size组合，不传默认查询全部 |
| size | false | Query参数interger | 分页参数 |
| thirdPartyName | false | Query参数String | thirdPartyName |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为200 |
| msg | String | 错误信息 | 正常成功情况下，msg为Ok |
| total | Int | 返回数据条数 |  |
| data | Object | 具体返回结果体 | 返回配置列表数据 |
| {<br>"code": "string",<br>"data": [<br>{<br>"apiVersionLabel": "string",<br>"auth": {<br>"password": "string",<br>"username": "string"<br>},<br>"authTime": "string",<br>"codeId": 0,<br>"configName": "string",<br>"domainId": "string",<br>"domainName": "string",<br>"id": 0,<br>"name": "string",<br>"newSession": true,<br>"page": 0,<br>"projectId": "string",<br>"secret": {<br>"password": "string",<br>"privateKey": "string",<br>"username": "string"<br>},<br>"size": 0,<br>"thirdPartyName": "string",<br>"token": "string",<br>"url": "string",<br>"version": "string"<br>}<br>],<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 修改配置接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/connectors/v1.0/config/feature/{id\} | JSON | PUT | 修改配置 |

###### 参数说明

| 参数 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 配置ID |
| 请求体 | True | Json | 如下 |
| {<br>"apiVersionLabel": "string",<br>"auth": {<br>"password": "string",<br>"username": "string"<br>},<br>"authTime": "string",<br>"codeId": 0,<br>"configName": "string",<br>"domainId": "string",<br>"domainName": "string",<br>"id": 0,<br>"name": "string",<br>"newSession": true,<br>"page": 0,<br>"projectId": "string",<br>"secret": {<br>"password": "string",<br>"privateKey": "string",<br>"username": "string"<br>},<br>"size": 0,<br>"thirdPartyName": "string",<br>"token": "string",<br>"url": "string",<br>"version": "string"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为200 |
| msg | String | 错误信息 | 正常成功情况下，msg为Ok |
| data | Object | 具体返回结果体 | 返回修改后的配置详情 |
| {<br>"code": "string",<br>"data": {<br>"apiVersionLabel": "string",<br>"auth": {<br>"password": "string",<br>"username": "string"<br>},<br>"authTime": "string",<br>"codeId": 0,<br>"configName": "string",<br>"domainId": "string",<br>"domainName": "string",<br>"id": 0,<br>"name": "string",<br>"newSession": true,<br>"page": 0,<br>"projectId": "string",<br>"secret": {<br>"password": "string",<br>"privateKey": "string",<br>"username": "string"<br>},<br>"size": 0,<br>"thirdPartyName": "string",<br>"token": "string",<br>"url": "string",<br>"version": "string"<br>},<br>"msg": "string"<br>\} |  |  |  |

#### 删除配置接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/connectors/v1.0/config/feature/{id\} |  | DELETE | 删除配置 |

###### 参数说明

| 参数 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | ture | Path 参数String | 配置的id |
| userInfo | true | Heade参数 string | 用户信息 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常全部删除成功情况下，code为200 |
| msg | String | 错误信息 | 正常全部删除成功情况下，msg为OK |
| data | Int | 删除数据条目 | 1 |
| 例如：<br>{<br>"data": 1,<br>"msg": "OK",<br>"code": "200"<br>\} |  |  |  |

#### 复制配置接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/connectors/v1.0/config/feature/ copyto/{id\} |  | POST | 复制配置 |

###### 参数说明

| 参数 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | ture | Path参数String | 配置的id |
| copyConfig | true | body参数 json | 第三方库信息，如下 |
| {  "thirdPartName": "string"\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常全部删除成功情况下，code为200 |
| msg | String | 错误信息 | 正常全部删除成功情况下，msg为OK |
| data | Int |  | 1 |
| {<br>"code": "string",<br>"data": 0,<br>"msg": "string"<br>\} |  |  |  |

### CICD构建环境管理

#### 查询构建环境列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv |  | GET | 查询接口列表 |

###### 参数说明

| Param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 所属项目ID |
| name | false | String | 构建环境名称 |
| type | false | List<String> | 构建环境类型 |
| system | false | List<String> | 系统 |
| tool | false | List<String> | 工具 |
| version | false | List<String> | 版本 |
| page | false | int | 页数 |
| size | false | int | 每页条数 |
| sort | false | String | 排序 |
| creatorId | false | List<String> | 创建人id |
| 例如：<br>cicd/fluxesWrapper/v1.0/buildEnv？projectId=12312313&system=linux&tool=maven,gradle&name=test |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | []Object | 返回构建环境列表 | 返回构建环境列表 |
| total | Integer | 数量 | 接口数量 |
| 例如：<br>{<br>"msg": "",<br>"data": [<br>{<br>"id": 1150674615383621632,<br>"name": "test",<br>"version": "123",<br>"tool": "123",<br>"projectId": 1148423051755388928,<br>"buildInfo": "123",<br>"envInfo": "123",<br>"description": null,<br>"system": "123",<br>"defaultBuildCommand": "123",<br>"type": {<br>"code": 0,<br>"desc": "容器镜像",<br>"name": "IMAGE"<br>},<br>"creatorId": "123123123123",<br>"creator": null,<br>"updaterId": null,<br>"updater": null,<br>"createTime": "2019-07-15T07:53:01.000+0000",<br>"updateTime": null<br>},<br>{<br>"id": 1150686454502916096,<br>"name": "test2",<br>"version": "21234",<br>"tool": "12234",<br>"projectId": 1148423051755388928,<br>"buildInfo": "14223",<br>"envInfo": "142253",<br>"description": null,<br>"system": "122243",<br>"defaultBuildCommand": "12243",<br>"type": {<br>"code": 0,<br>"desc": "容器镜像",<br>"name": "IMAGE"<br>},<br>"creatorId": "123123123123",<br>"creator": null,<br>"updaterId": null,<br>"updater": null,<br>"createTime": "2019-07-15T08:40:04.000+0000",<br>"updateTime": null<br>}<br>],<br>"code": "",<br>"total": 2<br>\} |  |  |  |

#### 查询构建环境详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv/{id\} |  | GET | 查询构建环境详情 |

###### 参数说明

| path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 查询构建环境id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回接口详细的信息 |
| 例如：<br>{<br>"msg": "",<br>"data": {<br>"id": 1150704736110903296,<br>"name": "test3",<br>"version": "21234",<br>"tool": "12234",<br>"projectId": 1148423051755388928,<br>"buildInfo": "14223",<br>"envInfo": "142253",<br>"description": "",<br>"system": "122243",<br>"defaultBuildCommand": "12243",<br>"type": {<br>"code": 0,<br>"desc": "容器镜像",<br>"name": "IMAGE"<br>},<br>"creatorId": "123123123123",<br>"creator": "Unknown User",<br>"updaterId": null,<br>"updater": "",<br>"createTime": "2019-07-15T09:52:42.000+0000",<br>"updateTime": null<br>},<br>"code": ""<br>\} |  |  |  |

#### 创建构建环境接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv | JSON | POST | 创建接口 |

###### 请求体（body）

| projectId | true | String | 所属组织ID |
| --- | --- | --- | --- |
| name | true | String | 构建环境名称，1-64位 |
| type | true | int | 构建环境类型，容器镜像，对接环境 |
| envInfo | true | String | 环境信息，1-64位 |
| defaultBuildCommand | true | String | 默认构建指令，1-256位 |
| system | true | String | 系统，可以从已有数据（filter）中选择，也可以自己输入，tool，version相同 |
| tool | true | String | 工具，1-64位 |
| version | true | String | 版本，1-64位 |
| description | false | String | 描述，0-256位 |
| unitTestCmd | false | String | 单元测试命令，1-256位 |
| 例如：<br>{<br>"name": "Maven项目构建",<br>"version": "Maven#.3.39+JDK1.8",<br>"tool": "Maven",<br>"projectId": 1148423051755388928,<br>"buildInfo": "14223",<br>"envInfo": "142253",<br>"description": "",<br>"system": "CentOS6.5",<br>"defaultBuildCommand": "echo 123",<br>"unitTestCmd": "test",<br>"type": 0<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回构建环境的详情信息 |
| 例如：<br>{<br>"msg": "",<br>"data": {<br>"id": 1150969140584906752,<br>"name": "Maven项目构建",<br>"version": "Maven#.3.39+JDK1.8",<br>"tool": "Maven",<br>"projectId": 1148423051755388928,<br>"buildInfo": "14223",<br>"envInfo": "142253",<br>"description": "",<br>"system": "CentOS6.5",<br>"defaultBuildCommand": "echo 123",<br>"type": {<br>"code": 0,<br>"desc": "容器镜像",<br>"name": "IMAGE"<br>},<br>"creatorId": "9d65271d-ffdc-4c04-b60e-ed23a73afcc9",<br>"creator": null,<br>"updaterId": null,<br>"updater": null,<br>"createTime": "2019-07-16T03:23:21.745+0000",<br>"updateTime": null<br>},<br>"code": ""<br>\} |  |  |  |

#### 删除构建环境接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv/{id\} |  | DELETE | 删除构建环境 |

###### 参数说明

| Path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 删除的构建环境ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Boolean | 成功的标记位 | true |
| 例如：<br>{<br>"msg": "",<br>"data": true,<br>"code": ""<br>\} |  |  |  |

#### 修改构建环境接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv/{id\} | JSON | PUT | 修改构建环境 |

###### 参数说明

| Path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 需要修改的构建环境ID |

###### 请求体(form-data)

| defaultBuildCommand | true | String | 默认构建指令，1-256位 |
| --- | --- | --- | --- |
| description | false | String | 描述，0-256位 |
| envInfo | true | String | 环境信息，1-64位 |
| name | true | String | 构建环境名称, 1-64位 |
| projectId | true | String | 所属组织ID |
| system | true | String | 系统，1-64位 |
| tool | true | String | 工具，1-64位 |
| type | true | int | 构建环境类型，0：容器镜像，1：构建环境 |
| version | true | String | 版本，64位 |
| 例如：<br>{<br>"name": "Gradle项目构建",<br>"version": "Gradle2.3.3+JDK1.7",<br>"tool": "Gradle",<br>"buildInfo": "14223",<br>"envInfo": "142253",<br>"description": "",<br>"system": "Ubantu12.10",<br>"defaultBuildCommand": "echo 123",<br>"type": 1<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回秘钥对的详情信息 |
| 例如：<br>{<br>"msg": "",<br>"data": {<br>"id": 1150686278434422784,<br>"name": "Gradle项目构建",<br>"version": "Gradle2.3.3+JDK1.7",<br>"tool": "Gradle",<br>"projectId": 1148423051755388928,<br>"buildInfo": "14223",<br>"envInfo": "142253",<br>"description": "",<br>"system": "Ubantu12.10",<br>"defaultBuildCommand": "echo 123",<br>"type": {<br>"code": 1,<br>"desc": "对接环境",<br>"name": "ENV"<br>},<br>"creatorId": null,<br>"creator": "Unknown User",<br>"updaterId": "9d65271d-ffdc-4c04-b60e-ed23a73afcc9",<br>"updater": "Unknown User",<br>"createTime": "2019-07-15T08:39:22.000+0000",<br>"updateTime": "2019-07-16T03:30:26.658+0000"<br>},<br>"code": ""<br>\} |  |  |  |

#### 构建环境重名校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv/nameCheck | JSON | POST | 重名校验接口 |

###### 参数说明

| Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | false | int | 当前组织Id |

###### 请求体(form-data)

| projectId | true | String | 当前组织Id |
| --- | --- | --- | --- |
| name | true | String | 当前组织名称 |
| 例如：<br>{<br>"projectId":1127894417563189248,<br>"name":"test"，<br>"id":1<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 成功返回为null |
| 例如：<br>{<br>"msg": "",<br>"data": true,<br>"code": ""<br>\} |  |  |  |

#### 构建环境列表过滤器数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv/project/{projectId}/filter | JSON | GET |  |

###### 参数说明

| Path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 所属组织ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回结果信息 |  |
| verison | List<String> | 版本 |  |
| tool | List<String> | 工具 |  |
| system | List<String> | 系统 |  |
| type | List<Object> | 类型 |  |
| 例如：<br>{<br>"msg": "",<br>"data": {<br>"system": [<br>"123",<br>"1234",<br>"12234"<br>],<br>"tool": [<br>"123",<br>"1243",<br>"122243"<br>],<br>"version": [<br>"123",<br>"1234",<br>"21234"<br>],<br>"type": [<br>{<br>"code": 0,<br>"desc": "容器镜像",<br>"name": "IMAGE"<br>},<br>{<br>"code": 1,<br>"desc": "对接环境",<br>"name": "ENV"<br>}<br>]<br>},<br>"code": ""<br>\} |  |  |  |

#### 构建环境版本去重检查(已经过时，不建议使用)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv/project/versionCheck | JSON | GET |  |

###### 参数说明（无参数）

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回结果信息 |  |
| 例如：<br>{<br>"msg": "",<br>"data": true,<br>"code": ""<br>\} |  |  |  |

#### 构建环境递进过滤

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cicd/fluxesWrapper/v1.0/buildEnv/project/{projectId}/filterBuild | JSON | GET |  |

###### 参数说明

| Path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 组织ID |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| system | false | String | 系统，1-64位 |
| tool | false | String | 工具，1-64位 |
| type | false | int | 构建环境类型，0：容器镜像，1：构建环境 |
| version | false | String | 版本，64位 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 接口返回信息 |  |
| 例如：<br>{<br>"msg": "",<br>"data": {<br>"type": [<br>{<br>"code": 1,<br>"desc": "对接环境",<br>"name": "ENV"<br>},<br>{<br>"code": 0,<br>"desc": "容器镜像",<br>"name": "IMAGE"<br>}<br>],<br>"system": [<br>"redhat",<br>"linux",<br>"CentOS6.5",<br>"unix"<br>],<br>"tool": [<br>"Maven"<br>],<br>"dependency": [<br>{<br>"dependencyId": "1153562854213812224",<br>"options": "SFD",<br>"version": "v3"<br>}<br>]<br>},<br>"code": ""<br>\} |  |  |  |

### CICD任务模板

#### 创建任务模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_templates | JSON | POST |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | 是 | string |  |
| tenant_id | 是 | string |  |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| accessScope | 是 | String |  |
| category | 否 | String |  |
| attributes | 否 | Map |  |
| definition | 是 | Object |  |
| description | 否 | String |  |
| name | 是 | String |  |
| projectId | 是 |  |  |
| 例如：<br>{<br>"accessScope": "PROJECT",<br>“category”: “BUILD”,<br>“attributes”: {},<br>"definition": {<br>"name": 0.1865741753707868,<br>"parameters": {},<br>"tasks": [<br>{<br>"body": {<br>"script": "ls"<br>},<br>"flowDefinition": "",<br>"name": "执行shell脚本",<br>"type": "SHELL"<br>}<br>],<br>"type": "FLUXES"<br>},<br>"description": "xx",<br>"name": "xx",<br>"projectId": "1183560861457842176"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除失败信息 | 正常成功情况下，data为null |

#### 修改任务模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_templates | JSON | PUT |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | 是 | string |  |
| tenant_id | 是 | string |  |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| accessScope | 是 | String |  |
| category | 否 | String |  |
| attributes | 否 | Map |  |
| definition | 是 | Object |  |
| description | 否 | String |  |
| name | 是 | String |  |
| projectId | 是 |  |  |
| 例如：<br>{<br>"accessScope": "PROJECT",<br>“category”: “BUILD”,<br>“attributes”: {},<br>"definition": {<br>"name": 0.1865741753707868,<br>"parameters": {},<br>"tasks": [<br>{<br>"body": {<br>"script": "ls"<br>},<br>"flowDefinition": "",<br>"name": "执行shell脚本",<br>"type": "SHELL"<br>}<br>],<br>"type": "FLUXES"<br>},<br>"description": "xx",<br>"name": "xx",<br>"projectId": "1183560861457842176"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除失败信息 | 正常成功情况下，data为更新资源时数据 |

#### 删除任务模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_templates/{id\} |  | DELETE |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | 是 | string |  |
| tenant_id | 是 | string |  |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | 是 | Long |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除失败信息 | 正常成功情况下，data为null |

#### 检查任务模板名称

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_templates/names |  | POST |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | 是 | string |  |
| tenant_id | 是 | string |  |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | 是 | String | 名称 |
| projectId | 是 | String |  |
| excludedJobTemplateId | 是 | Integer |  |
| category | 否 | String |  |
| attributes | 否 | Map |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | bool | 删除失败信息 | 正常成功情况下，data为true |
| {<br>"code": "string",<br>"data": true<br>"msg": "string",<br>\} |  |  |  |

#### 获取任务模板详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_templates/{id\} | JSON | GET |  |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String |  |

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | 是 | string |  |
| tenant_id | 是 | string |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回接口详细的信息 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"accessScope": "PROJECT",<br>"createTime": "2020-01-02T06:17:40.000+0000",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"definition": {<br>"name": "0.45759221205648704",<br>"tasks": [<br>{<br>"body": {<br>"script": "sfsf",<br>"type": "SHELL"<br>},<br>"name": "执行shell脚本",<br>"properties": {},<br>"type": "SHELL"<br>}<br>],<br>"type": "FLUXES"<br>},<br>"description": "sdfsf",<br>"id": "1212618946268626944",<br>"instanceId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "xvdcv",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd"<br>},<br>"msg": ""<br>\} |  |  |  |

#### 任务模板列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/job_templates | Query | GET |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String |  |
| tenant_id | true | String |  |
| Query参数名 | 必选 | 类型及范围 | 说明 |
| page | true | Integer | 分页码，从1开始 |
| size | true | Integer | 分页大小 |
| projectId | true | String | 项目id |
| order | false | String | 排序 |
| instanceId | false | String | 实例Id |
| category | false | String | 类型名称 |
| jobSchema | false | bool | Job类型 |
| query | false | String | 查询 |
| creatorId | false | String | 创建者Id |
| language | false | String | 语言类型 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回列表 |
| total | Integer | 列表的条数 | 列表的条数 |
| 例如：<br>{<br>"code": "",<br>"data": [<br>{<br>"accessScope": "PROJECT",<br>"createTime": "2020-01-02T06:17:40.000+0000",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"description": "sdfsf",<br>"id": "1212618946268626944",<br>"instanceId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "xvdcv",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd"<br>}<br>],<br>"msg": "",<br>"total": 2<br>\} |  |  |  |

### CICD日志

#### 查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxesLog/v1.0/logs? &jobExecutionId={id\} | JSON | GET |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String |  |
| tenant_id | true | String |  |
| 参数名 | 必选 | 类型及范围 | 说明 |
| jobExecutionId | true | Integer |  |
| type | false | String | Limit或者为空 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回日志数据 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"hasMore": false,<br>"log": "xxxxx",<br>"logSourceType": ""<br>},<br>"msg": ""<br>\} |  |  |  |

### CICD Pipeline

#### 创建流水线

| URL | 支持格式 | HTTP请求方式 |  | 说明 |  |
| --- | --- | --- | --- | --- | --- |
| /cicd/fluxes/v1.0/pipelines |  | JSON | POST |  |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String |  |
| tenant_id | true | String |  |
| 参数名 | 必选 | 类型及范围 | 说明 |
| definition | true | Definition Object | 流水线定义 |
| description | 否 | String |  |
| name | true | String | 流水线名称 |
| projectId | true | String | 项目id |
| triggers | true | Trigger Object | 触发方式 |
| attributes | false | Map | 属性 |
| source | false | int | 来源 |
| {<br>"definition": {<br>"jobDefinitions": [],<br>"parameters": {},<br>"stages": [<br>{<br>"jobs": [<br>{<br>"category": "BUILD",<br>"externalJob": true,<br>"externalJobName": "build_image",<br>"jobDefinitionName": "job1",<br>"jobId": "1211907152231268352",<br>"name": "job1",<br>"startMode": "AUTO"<br>}<br>],<br>"name": "ccc",<br>"parallel": false<br>}<br>]<br>},<br>"description": "",<br>"name": "dd",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"triggers": {<br>"codeUrl": "",<br>"date": [<br>"1",<br>"2",<br>"3",<br>"4",<br>"5",<br>"6",<br>"7"<br>],<br>"duration": "15m",<br>"endTime": "01: 00",<br>"events": [<br>"push"<br>],<br>"startTime": "00: 00",<br>"type": "MANUAL",<br>"versionFilter": ""<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回创建的流水线数据 |
| 例如：<br>{<br>"code": "",<br>"data": {}, // 该流水线详情数据<br>"msg": ""<br>\} |  |  |  |

#### 更新流水线

| URL | 支持格式 | HTTP请求方式 |  | 说明 |  |
| --- | --- | --- | --- | --- | --- |
| /cicd/fluxes/v1.0/pipelines/{id\} |  | JSON | PUT |  |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String |  |
| tenant_id | true | String |  |
| 参数名 | 必选 | 类型及范围 | 说明 |
| definition | true | Definition Object | 流水线定义 |
| description | 否 | String | 描述 |
| name | true | String | 流水线名称 |
| projectId | true | String | 项目id |
| triggers | true | Trigger Object | 触发方式 |
| attributes | false | Map | 属性 |
| source | false | int | 来源 |
| {<br>"definition": {<br>"jobDefinitions": [],<br>"parameters": {},<br>"stages": [<br>{<br>"jobs": [<br>{<br>"category": "BUILD",<br>"externalJob": true,<br>"externalJobName": "build_image",<br>"jobDefinitionName": "job1",<br>"jobId": "1211907152231268352",<br>"name": "job1",<br>"startMode": "AUTO"<br>}<br>],<br>"name": "ccc",<br>"parallel": false<br>}<br>]<br>},<br>"description": "",<br>"name": "dd",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"triggers": {<br>"codeUrl": "",<br>"date": [<br>"1",<br>"2",<br>"3",<br>"4",<br>"5",<br>"6",<br>"7"<br>],<br>"duration": "15m",<br>"endTime": "01: 00",<br>"events": [<br>"push"<br>],<br>"startTime": "00: 00",<br>"type": "MANUAL",<br>"versionFilter": ""<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回更新的流水线数据 |

#### 查询流水线详情

| URL | 支持格式 | HTTP请求方式 |  | 说明 |  |
| --- | --- | --- | --- | --- | --- |
| /cicd/fluxes/v1.0/pipelines/{id\} |  | JSON | GET |  |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String | 用户id |
| tenant_id | true | String | 租户或组织id |
| Path参数名 | 必选 | 类型及范围 | 说明 |
| Id | true | Int | 流水线定义 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回创建的流水线数据 |
| 响应体示例：<br>{<br>"code": "",<br>"data": {<br>"createTime": 1577949207000,<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"creatorName": "",<br>"definition": {<br>"jobDefinitions": [],<br>"parameterDefinitions": [],<br>"stages": [<br>{<br>"index": 0,<br>"jobs": [<br>{<br>"category": "BUILD",<br>"externalJob": true,<br>"externalJobName": "build_image",<br>"id": "1212643069644505088",<br>"indexInStage": 0,<br>"jobDefinition": {<br>"externalId": "1211907152231268352",<br>"name": "build_image-snapshot-1212643069262823424",<br>"parameterDefinitions": [<br>{<br>"defaultValue": "bootmvn",<br>"displayName": "SERVICE_NAME",<br>"name": "SERVICE_NAME",<br>"required": false,<br>"type": "STRING"<br>}<br>],<br>"tasks": [<br>{<br>"body": {<br>"codeUrl": "http://10.125.196.110:21100/h3ccloud/devopscloud/demos/spring-boot-maven.git",<br>"codeVersion": "master",<br>"type": "CHECK_OUT",<br>"vcsType": "GIT"<br>},<br>"name": "代码检出",<br>"properties": {},<br>"type": "CHECK_OUT"<br>},<br>{<br>"body": {<br>"options": "-Dmaven.test.skip=true package ",<br>"toolType": "MAVEN",<br>"type": "BUILD",<br>"unitPassPercent": 0,<br>"unitTestCmd": "test -Dmaven.test.failure.ignore=true",<br>"useJunit": false,<br>"workerSpecs": {<br>"image": "os-harbor-svc.default.svc.cloudos:443/helm/h3ccloud/cicd/jenkins-jnlp-agent:dind-centos-universal-M190919",<br>"type": "CLOUD_WORKER"<br>}<br>},<br>"properties": {<br>"dependencyId": "1163654573970161664",<br>"dependencyType": "BUILD",<br>"system": "Centos6.5",<br>"type": "0"<br>},<br>"type": "BUILD"<br>},<br>{<br>"body": {<br>"baseImage": "os-harbor-svc.default.svc.cloudos:443/helm/h3ccloud/cicd/openjdk:8u191",<br>"imageBuildType": "SPRINGBOOT",<br>"jvmArgs": "-Xms128m -Xmx512m -XX:+UseG1GC -Duser.timezone='GMT+08:00'",<br>"targetImage": "${SERVICE_NAME}:${BUILD_VERSION}",<br>"type": "BUILD_IMAGE"<br>},<br>"name": "镜像构建",<br>"properties": {},<br>"type": "BUILD_IMAGE"<br>},<br>{<br>"body": {<br>"imageName": "${SERVICE_NAME}:${BUILD_VERSION}",<br>"serviceContext": {<br>"authentications": [<br>{<br>"description": "docker-image-service context",<br>"id": "1211930055094042624",<br>"order": 0,<br>"password": "EziowOgVKVOlZSj0Y%2BDHUg%3D%3D",<br>"type": "USERNAME_PASSWORD",<br>"username": "admin"<br>},<br>{<br>"description": "default paas-docker-registry-service context",<br>"id": "1211930055094042625",<br>"order": 0,<br>"password": "vZMvcvUf5VQJaMu5VgXV1A%3D%3D",<br>"type": "USERNAME_PASSWORD",<br>"username": "admin"<br>}<br>],<br>"category": "PAAS-DOCKER-REGISTRY",<br>"dockerRegistryAuthenticationId": "1211930055094042625",<br>"dockerRegistryEndpoint": "os-harbor-svc.default.svc:443",<br>"endpoint": "os-hcr-svc.cloudos-paas.svc:15400",<br>"id": "6303e88b9c9440ee8b47d8bf0a628f7e",<br>"imageServiceAuthenticationId": "1211930055094042624",<br>"order": 0,<br>"protocol": "http"<br>},<br>"type": "PAAS_PUSH_DOCKER_IMAGE"<br>},<br>"properties": {<br>"dependencyType": "PAAS_PUSH_DOCKER_IMAGE"<br>},<br>"type": "PAAS_PUSH_DOCKER_IMAGE"<br>},<br>{<br>"body": {<br>"appPackName": "qwe",<br>"appPackType": "JAR",<br>"appPackVersion": "1.0",<br>"artifactFile": "/",<br>"serviceContext": {<br>"authentications": [<br>{<br>"description": "default paas-app-repository-service context",<br>"id": "1211930055127597056",<br>"order": 0,<br>"password": "EziowOgVKVOlZSj0Y%2BDHUg%3D%3D",<br>"type": "USERNAME_PASSWORD",<br>"username": "admin"<br>}<br>],<br>"category": "PAAS-APP-REPOSITORY",<br>"endpoint": "os-deploy-mgt-svc.cloudos-paas:16800",<br>"id": "35e4cf2958234ce5828641765403bc97",<br>"order": 0,<br>"protocol": "http"<br>},<br>"type": "PAAS_UPLOAD_ARTIFACT",<br>"visible": false<br>},<br>"name": "软件包上传",<br>"properties": {},<br>"type": "PAAS_UPLOAD_ARTIFACT"<br>}<br>],<br>"type": "FLUXES"<br>},<br>"jobDefinitionName": "build_image-snapshot-1212643069262823424",<br>"jobId": "1211907152231268352",<br>"name": "job1",<br>"startMode": "AUTO"<br>}<br>],<br>"name": "ccc",<br>"parallel": false,<br>"stageStatus": {}<br>}<br>]<br>},<br>"description": "",<br>"id": "1212632982787981313",<br>"instanceId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "dd",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"projectName": "",<br>"source": 0,<br>"status": "RUNNABLE",<br>"triggers": {<br>"type": "MANUAL"<br>},<br>"updateTime": 1577951612251<br>},<br>"msg": ""<br>\} |  |  |  |

#### 删除流水线

查询流水线详情

| URL | 支持格式 | HTTP请求方式 |  | 说明 |  |
| --- | --- | --- | --- | --- | --- |
| /cicd/fluxes/v1.0/pipelines/{id\} |  | JSON | DELETE |  |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String | 用户id |
| tenant_id | true | String | 租户或组织id |
| 参数名 | 必选 | 类型及范围 | 说明 |
| Id | true | Integer | 流水线id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回空对象 |

#### 查询流水线列表

| URL | 支持格式 | HTTP请求方式 |  | 说明 |  |
| --- | --- | --- | --- | --- | --- |
| /cicd/fluxes/v1.0/pipelines?page={page}&size={size}&projectId={projectId\} |  | JSON | GET |  |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String | 用户id |
| tenant_id | true | String | 租户或组织id |
| 参数名 | 必选 | 类型及范围 | 说明 |
| page | true | Integer | 页索引 |
| size | true | Integer | 页大小 |
| projectId | true | Integer | 项目id |
| order | false | String | 排序 |
| instanceId | false | String | 实例Id |
| query | false | String | 查询 |
| creatorId | false | String | 创建者Id |
| sourceId | false | int | 源Id |
| clusterId | false | String |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回创建的流水线数据 |
| 返回示例：<br>{<br>"code": "",<br>"data": [<br>{<br>"createTime": 1577775052000,<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"creatorName": "",<br>"definition": {<br>"jobDefinitions": [],<br>"parameterDefinitions": [],<br>"stages": [<br>{<br>"index": 0,<br>"jobs": [<br>],<br>"name": "a",<br>"parallel": false,<br>"stageStatus": {}<br>},<br>{<br>"index": 1,<br>"jobs": [<br>],<br>"name": "b",<br>"parallel": false,<br>"stageStatus": {}<br>},<br>{<br>"index": 2,<br>"jobs": [],<br>"name": "c",<br>"parallel": false,<br>"stageStatus": {}<br>}<br>]<br>},<br>"description": "",<br>"executionStats": {<br>"countByStatus": {<br>"CANCELED": "0",<br>"FAILED": "0",<br>"PAUSED": "0",<br>"PENDING": "0",<br>"RUNNING": "0",<br>"SCHEDULED": "0",<br>"SUCCESS": "1"<br>},<br>"last10ExecutionStatues": [<br>{<br>"beginTime": 1577775058000,<br>"endTime": 1577775569000,<br>"executionId": "1211902548345094144",<br>"executionStatus": "SUCCESS"<br>}<br>]<br>},<br>"id": "1211902523997159425",<br>"instanceId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "ls4",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"projectName": "",<br>"source": 0,<br>"status": "RUNNABLE",<br>"triggers": {<br>"type": "MANUAL"<br>},<br>"updateTime": 1577775052000<br>}<br>],<br>"msg": "",<br>"total": 23<br>\} |  |  |  |

#### 复制流水线

| URL | 支持格式 | HTTP请求方式 |  | 说明 |  |
| --- | --- | --- | --- | --- | --- |
| /cicd/fluxes/v1.0/pipelines/clone |  | JSON | POST |  |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String | 用户id |
| tenant_id | true | String | 租户或组织id |
| 参数名 | 必选 | 类型及范围 | 说明 |
| name | true | String | 页索引 |
| pipelineId | true | Integer | 被复制的流水线id |
| targetProjectId | true | String | 复制到所属项目id |
| attributes | false | Map | 属性 |
| {<br>"name": "string",<br>"pipelineId": 0,<br>"targetProjectId": "string"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 返回列表 | 返回创建的流水线数据 |
| 返回示例：<br>{<br>"code": "string",<br>"data": {<br>"attributes": {},<br>"createTime": "2020-01-02T06:37:05.237Z",<br>"creatorId": "string",<br>"creatorName": "string",<br>"definition": {<br>"jobDefinitions": [<br>{<br>"externalId": 0,<br>"name": "string",<br>"parameters": {}<br>}<br>],<br>"parameters": {},<br>"stages": [<br>{<br>"index": 0,<br>"jobs": [<br>{<br>"clusterSelectors": [<br>{}<br>],<br>"externalJob": true,<br>"id": 0,<br>"indexInStage": 0,<br>"jobDefinitionName": "string",<br>"jobExecution": {<br>"attributes": {},<br>"beginTime": "2020-01-02T06:37:05.237Z",<br>"buildVersion": "string",<br>"cancelInProgress": true,<br>"codeVersions": [<br>{<br>"authorEmail": "string",<br>"authorName": "string",<br>"createTime": "2020-01-02T06:37:05.237Z",<br>"eventParameters": "string",<br>"eventType": "string",<br>"executionId": 0,<br>"id": 0,<br>"instanceId": "string",<br>"jobExecutionId": 0,<br>"jobId": 0,<br>"message": "string",<br>"pipelineId": 0,<br>"projectId": "string",<br>"reference": "string",<br>"repository": "string",<br>"repositoryType": "string",<br>"revisionId": "string",<br>"timestamp": "2020-01-02T06:37:05.237Z"<br>}<br>],<br>"createTime": "2020-01-02T06:37:05.237Z",<br>"creatorId": "string",<br>"endTime": "2020-01-02T06:37:05.237Z",<br>"executionId": 0,<br>"finished": true,<br>"id": 0,<br>"indexInStage": 0,<br>"instanceId": "string",<br>"jobId": 0,<br>"lastScheduleTime": "2020-01-02T06:37:05.237Z",<br>"lastSyncTime": "2020-01-02T06:37:05.237Z",<br>"log": "string",<br>"name": "string",<br>"parameters": {},<br>"pipelineId": 0,<br>"projectId": "string",<br>"stageIndex": 0,<br>"standalone": true,<br>"status": "PENDING"<br>},<br>"jobId": 0,<br>"name": "string",<br>"parameterGroup": "string",<br>"startMode": "AUTO",<br>"workerSpecs": {}<br>}<br>],<br>"name": "string",<br>"stageStatus": {<br>"log": "string",<br>"status": "PENDING"<br>}<br>}<br>]<br>},<br>"description": "string",<br>"executionStats": {<br>"countByStatus": {},<br>"last10ExecutionStatues": [<br>{<br>"beginTime": "2020-01-02T06:37:05.239Z",<br>"buildNumber": 0,<br>"endTime": "2020-01-02T06:37:05.239Z",<br>"executionId": 0,<br>"executionStatus": "PENDING"<br>}<br>]<br>},<br>"id": 0,<br>"instanceId": "string",<br>"mergedParameters": {},<br>"name": "string",<br>"projectId": "string",<br>"projectName": "string",<br>"source": 0,<br>"status": "EDIT",<br>"triggers": {},<br>"updateTime": "2020-01-02T06:37:05.239Z"<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

#### 获取流水线参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/pipelines/{pipelineId}/parameters | JSON | GET |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String | 用户id |
| tenant_id | true | String | 租户或组织id |
| 参数名 | 必选 | 类型及范围 | 说明 |
| pipelineId | true | Integer | 流水线id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 返回列表 | 返回创建的流水线数据 |
| 返回示例：<br>{<br>"code": "string",<br>"data": {<br>"parameters": {},<br>"pipelineId": 0<br>},<br>"msg": "string",<br>"total": 0<br>\} |  |  |  |

### CICD webHook

#### 创建webHook

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/fluxes/v1.0/triggers | JSON | POST |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| user_id | true | String | 用户id |
| tenant_id | true | String | 租户或组织id |
| body参数名 | 必选 | 类型及范围 | 说明 |
| payloadString | true | String | 流水线id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{\} |  |  |  |

### CICD 自动化测试用例分组

#### 创建测试用例分组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/tree | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 组织id |
| parentId | false | Long | 父分组id，可以为null |
| name | true | String | 分组名 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| nodeKey | String | 当前记录的key值 |  |
| id | String | ID值 |  |
| projectId | String | 组织ID |  |
| name | String | 名称 |  |
| paraentId | Long | 父节点ID |  |
| type | String | 表示类型，可以有item,book |  |
| next | long | 下含的记录数 |  |
| 返回示例：<br>{<br>"msg": "",<br>"data": {<br>"nodeKey": "book4",<br>"id": "4",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "sss",<br>"parentId": null,<br>"rootId": null,<br>"type": null,<br>"lft": 1,<br>"rgt": 2,<br>"next": 0<br>},<br>"code": ""<br>\} |  |  |  |

#### 删除测试用例分组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/tree/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | Long | 分组ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": 4,<br>"code": ""<br>\} |  |  |  |

#### 修改测试用例分组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/tree/{id\} | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | Long | 分组ID |
| name | true | String | 分组名 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": 4,<br>"code": ""<br>\} |  |  |  |

#### 查询测试用例分组或测试项

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/tree | JSON | GET |  |

###### 参数说明

| qs参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | false | Long | 分组ID，不指定取全集 |
| name | false | String | 分组名，不指定取全集 |
| projectId | true | String | 组织ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| nodeKey | String | 当前记录的key值 |  |
| id | Long | ID值 |  |
| projectId | String | 组织ID |  |
| name | String | 名称 |  |
| paraentId | Long | 父节点ID |  |
| type | String | 表示类型，可以有item,book |  |
| next | long | 下含的记录数 |  |
| bookId | Long | 测试项所属分组ID， | 只有测试项才有此值 |
| description | String | 测试项描述 | 只有测试项才有此值 |
| status | String | 测试项状态 | 只有测试项才有此值 |
| method | String | 测试项HTTP方法 | 只有测试项才有此值 |
| request | Object | 测试项HTTP请求定义 | 只有测试项才有此值 |
| 返回示例：<br>{<br>"msg": "",<br>"data": [<br>{<br>"nodeKey": "book5",<br>"id": "5",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "sss",<br>"parentId": "1",<br>"rootId": "1",<br>"type": "book",<br>"lft": "2",<br>"rgt": "3",<br>"next": "1"<br>},<br>{<br>"nodeKey": "item1",<br>"id": "1",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "vcvc",<br>"parentId": null,<br>"rootId": null,<br>"type": "item",<br>"lft": "0",<br>"rgt": "0",<br>"next": "0",<br>"bookId": "1",<br>"description": "cvcv",<br>"status": "open",<br>"method": "GET",<br>"request": null,<br>"event": []<br>}<br>],<br>"code": ""<br>\} |  |  |  |

### CICD 自动化测试用例

#### 创建测试用例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/item | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 组织id |
| bookId | true | Long | 分组id |
| name | true | String | 分组名 |
| status | false | String | open状态 |
| description | false | String | 测试用例描述 |
| request | true | object | 请求定义 |
| event | true | Array | 预执行脚本及断言测试脚本 |
| 示例：<br>{<br>"name": "QWEWQE",<br>"description": "",<br>"status": "plan",<br>"request": {<br>"auth": {<br>"type": "noAuth"<br>},<br>"method": "GET",<br>"header": [<br>{<br>"headerKey": "",<br>"value": "",<br>"desc": "",<br>"enabled": false<br>}<br>],<br>"body": {<br>"mode": ""<br>},<br>"url": {<br>"raw": "",<br>"query": [<br>{<br>"queryKey": "",<br>"value": "",<br>"desc": "",<br>"enabled": false<br>}<br>]<br>}<br>},<br>"event": [<br>{<br>"listen": "test",<br>"script": {<br>"exec": []<br>}<br>},<br>{<br>"listen:" "prerequest",<br>"script":{<br>exec: [] *代码片段，将代码片段按行切割为数组*<br>}<br>],<br>"bookId": "3",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": {<br>"nodeKey": "item6",<br>"id": "6",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "test",<br>"parentId": null,<br>"rootId": null,<br>"type": null,<br>"lft": "0",<br>"rgt": "0",<br>"next": "0",<br>"bookId": "5",<br>"description": "",<br>"status": "plan",<br>"method": null,<br>"request": {<br>"auth": {<br>"type": "basic",<br>"basic": {<br>"username": "admin",<br>"password": "admin"<br>},<br>"bearer": null,<br>"apiKey": null<br>},<br>"method": "GET",<br>"header": [<br>{<br>"headerKey": "kva",<br>"value": "kvalue",<br>"desc": "",<br>"enabled": true<br>},<br>{<br>"headerKey": "",<br>"value": "",<br>"desc": "",<br>"enabled": false<br>}<br>],<br>"body": {<br>"mode": "",<br>"raw": null<br>},<br>"url": {<br>"raw": "http://123.23.2.13",<br>"query": [<br>{<br>"queryKey": "",<br>"value": "",<br>"desc": "",<br>"enabled": false<br>}<br>]<br>}<br>},<br>"event": [<br>{<br>"listen": "test",<br>"script": {<br>"id": "1287631044006117376",<br>"type": "text/javascript",<br>"exec": [<br>"",<br>"pm.test(\"Status code is 200\", function () {",<br>"    pm.response.to.have.status(200);",<br>"});",<br>"",<br>"pm.test(\"Response time is less than 200ms\", function () {",<br>"    pm.expect(pm.response.responseTime).to.be.below(200);",<br>"});",<br>""<br>]<br>}<br>}<br>]<br>},<br>"code": ""<br>\} |  |  |  |

#### 删除测试用例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/item/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | Long | 测试用例ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": 4,<br>"code": ""<br>\} |  |  |  |

#### 修改测试用例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/tree/{id\} | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | Long | 测试用例ID |
| 示例： 修改的消息体与创建时消息体一致<br>{<br>"name": "QWEWQE",<br>"description": "",<br>"status": "plan",<br>"request": {<br>"auth": {<br>"type": "noAuth"<br>},<br>"method": "GET",<br>"header": [<br>{<br>"headerKey": "",<br>"value": "",<br>"desc": "",<br>"enabled": false<br>}<br>],<br>"body": {<br>"mode": ""<br>},<br>"url": {<br>"raw": "",<br>"query": [<br>{<br>"queryKey": "",<br>"value": "",<br>"desc": "",<br>"enabled": false<br>}<br>]<br>}<br>},<br>"event": [<br>{<br>"listen": "test",<br>"script": {<br>"exec": []<br>}<br>}<br>],<br>"bookId": "3",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": 4,<br>"code": ""<br>\} |  |  |  |

#### 查询测试用例详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/item/{id\} | JSON | GET |  |

###### 参数说明

| qs参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | Long | 测试用例ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": {<br>"nodeKey": "item1",<br>"id": "1",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "vcvc",<br>"parentId": null,<br>"rootId": null,<br>"type": "item",<br>"lft": "0",<br>"rgt": "0",<br>"next": "0",<br>"bookId": "1",<br>"description": "cvcv",<br>"status": "open",<br>"method": "GET",<br>"request": null,<br>"event": []<br>},<br>"code": ""<br>\} |  |  |  |

#### 测试用例重名校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/item/nameChec | JSON | POST |  |

###### 参数说明

| qs参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| bookId | true | Long | 分组ID |
| Name | true | String | 测试用例名 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data":{<br>"bookId": "1",<br>"name": "test"<br>},<br>"code": ""<br>\} |  |  |  |

#### 从URL导入测试用例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/item/url | JSON | POST |  |

###### 参数说明

| qs参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| url | true | String | 需要导入的swagger文档的URL |
| projectId | true | String | 组织ID |
| id | false | Long | 测试用例分组Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": null,<br>"code": ""<br>\} |  |  |  |

#### 从文件导入测试用例(swagger和postman Collection)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/testCase/import/auto/file | JSON | POST |  |

###### 参数说明

| qs参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 组织ID |
| id | false | Long | 测试用例分组Id |
| file | True | form-data | form-data消息体，仅支持utf-8编码 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": null,<br>"code": ""<br>\} |  |  |  |

#### 获取postman代码模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/codeSnippets | JSON | GET |  |

###### 参数说明

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| en | String | 中文描述 |  |
| zn | String | 英文描述 |  |
| code | String | 模板代码 |  |
| 返回示例：<br>{<br>code: "",<br>data:[<br>{<br>en: "Get an environment variable"<br>zn: "获取一个环境变量",<br>code: "pm.environment.get("variable_key")"<br>}<br>]<br>msg:""<br>\} |  |  |  |

#### 获取postman预执行代码模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/ preCodeSnippets | JSON | GET |  |

###### 参数说明

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| en | String | 中文描述 |  |
| zn | String | 英文描述 |  |
| code | String | 模板代码 |  |
| 返回示例：<br>{<br>code: "",<br>data:[<br>{<br>en: "Get an environment variable"<br>zn: "获取一个环境变量",<br>code: "pm.environment.get("variable_key")"<br>}<br>]<br>msg:""<br>\} |  |  |  |

### CICD 自动化测试套件

#### 查询测试套件列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/plan | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | Long | 组织ID |
| page | false | Integer |  |
| size | false | Integer |  |
| authorIds | false | String |  |
| environmentIds | False | String |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{"msg":"",<br>"data":<br>[{  "id":"2",<br>"projectId":"9052ae85792143ff955c35c52e8e41bd",<br>"projectName":"root",<br>"name":"test",<br>"authorId":null,<br>"authorLoginName":null,<br>"authorName":null,<br>"environmentId":null,<br>"environmentName":"demo",<br>"lastRunTime":"1636509057110",<br>"totalTestsCount":1,<br>"authorIds":null,<br>"environmentIds":null,<br>"page":null,<br>"size":null,<br>"desc":null,<br>"planPool":null}],<br>"code":"",<br>"total":2\} |  |  |  |

#### 创建测试套件

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/plan | JSON | POST |  |

###### 参数说明

| 参数名 | 类型 | 是否必选 | 说明 |
| --- | --- | --- | --- |
| projectId | String | 是 | 项目ID |
| name | String | 是 | 测试套件名称 |
| authorId | String | 否 | 测试用例负责人 |
| environmentId | String | 否 | 环境ID |
| desc | String | 否 | 测试目的 |
| planPool | Array | 否 | 测试用例集合 |
| 示例：<br>{<br>"name": "00001",<br>"projectId":"1001",<br>"authorId": 11,<br>"environmentId":122,<br>"desc": "description",<br>"planPool":[<br>[<br>{<br>"id":1,<br>"type":"item",<br>"projectId":"12333",<br>"name":"subject",<br>"parentId":14,<br>"status":""<br>} ,<br>{<br>"id":2,<br>"type":"item",<br>"projectId":"12333",<br>"name":"subject",<br>"parentId":14,<br>"status":""<br>}<br>],<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": {<br>"name": "00001",<br>"projectId":"1001",<br>"authorId": 11,<br>"environmentId":122,<br>"desc": "description",<br>"planPool":[<br>[<br>{<br>"id":1,<br>"type":"item",<br>"projectId":"12333",<br>"name":"subject",<br>"parentId":14,<br>"status":""<br>} ,<br>{<br>"id":2,<br>"type":"item",<br>"projectId":"12333",<br>"name":"subject",<br>"parentId":14,<br>"status":""<br>}<br>],<br>]<br>},<br>"code": ""<br>\} |  |  |  |

#### 删除测试套件

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/plan/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | Long | 测试套件ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": 4,<br>"code": ""<br>\} |  |  |  |

#### 修改测试套件

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/plan/{id\} | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | Long | 测试套件ID |
| 示例： 修改的消息体与创建时消息体一致<br>{<br>"name": "00001",<br>"projectId":"1001",<br>"authorId": 11,<br>"environmentId":122,<br>"desc": "description",<br>"planPool":[<br>[<br>{<br>"id":1,<br>"type":"item",<br>"projectId":"12333",<br>"name":"subject",<br>"parentId":14,<br>"status":""<br>} ,<br>{<br>"id":2,<br>"type":"item",<br>"projectId":"12333",<br>"name":"subject",<br>"parentId":14,<br>"status":""<br>}<br>],<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": 4,<br>"code": ""<br>\} |  |  |  |

#### 查询测试套件详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/plan/{id\} | JSON | GET |  |

###### 参数说明

| qs参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | false | Long | 测试套件ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": {<br>"name": "00001",<br>"projectId":"1001",<br>"authorId": 11,<br>"environmentId":122,<br>"desc": "description",<br>"planPool":[<br>[<br>{<br>"id":1,<br>"type":"item",<br>"projectId":"12333",<br>"name":"subject",<br>"parentId":14,<br>"status":""<br>} ,<br>{<br>"id":2,<br>"type":"item",<br>"projectId":"12333",<br>"name":"subject",<br>"parentId":14,<br>"status":""<br>}<br>],<br>]<br>}，<br>"code": ""<br>\} |  |  |  |

#### 测试套件汇总信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/summary/chart/{id\} | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | Lo | 测试套件ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| totole | integer | 总请求数 |  |
| fails | integer | 失败请求数 |  |
| suces | integer | 成功请求数 |  |
| 返回示例：<br>{<br>"msg": "",<br>"data":  {<br>"total": "4",<br>"fails": "1",<br>"suces": "3"<br>},<br>"code": ""<br>\} |  |  |  |

### CICD 自动化测试环境

#### 创建测试环境

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/env | JSON | POST |  |

###### 参数说明

| 参数名 | 类型 | 是否必选 | 说明 |
| --- | --- | --- | --- |
| projectId | String | 是 | 组织ID |
| name | String | 环境名 | 计划名称 |
| 示例：<br>{<br>"name": "10.125.30.80",<br>"projectId": "1148045530467663872"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": {<br>"uuid": "db7f2cb5f1a448128e92fc4712e573ed",<br>"id": "db7f2cb5-f1a4-4812-8e92-fc4712e573ed",<br>"name": "10.125.30.80",<br>"projectId": "1148045530467663872",<br>"createdAt": "1563971012329"<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

#### 删除测试环境

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/env/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 测试套件ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": "xxxxxxx-xxxx",<br>"code": ""<br>\} |  |  |  |

#### 更新测试环境信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/autoTest/env/{id\} | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 测试套件ID |
| 示例： 修改的消息体与创建时消息体一致<br>{<br>"name": "10.125.30.80",<br>"projectId": "1148045530467663872"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": "xxxxxx-xxxx-xxxx",<br>"code": ""<br>\} |  |  |  |

#### 新增测试环境及参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/envAndProps | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | String | 是 | 所属项目ID |
| name | String | 是 | 环境名称 |
| valKey | string | 是 | 属性key |
| valValue | String | 是 | 属性值 |
| vatType | String | 是 | 属性类型 |
| enabled | String | 是 | 是否启用该属性 |
| 请求示例：<br>{<br>"name": "10.125.30.80",<br>"projectId": "1148045530467663872",<br>"values": [<br>{<br>"valKey": "ip",<br>"valValue": "10.125.30.80",<br>"valType": "text",<br>"enabled": true<br>},<br>{<br>"valKey": "redmine-adt",<br>"valValue": "21060",<br>"valType": "text",<br>"enabled": true<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": {<br>"uuid": "99e8cf2c02fa4002933be037dc933630",<br>"id": "99e8cf2c-02fa-4002-933b-e037dc933630",<br>"name": "10.125.30.123",<br>"projectId": "1148045530467663872",<br>"createdAt": null,<br>"values": [<br>{<br>"id": 78,<br>"envId": "99e8cf2c02fa4002933be037dc933630",<br>"valKey": "ip",<br>"valValue": "10.125.31.240",<br>"valType": "text",<br>"enabled": true<br>},<br>{<br>"id": 79,<br>"envId": "99e8cf2c02fa4002933be037dc933630",<br>"valKey": "redmine-adt",<br>"valValue": "21060",<br>"valType": "text",<br>"enabled": true<br>}<br>]<br>},<br>"code": ""<br>\} |  |  |  |

#### 更新测试环境及参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/envAndProps/{id\} | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 测试套件ID |
| 请求示例（请求体与创建时请求体一致）：<br>{<br>"name": "10.125.30.80",<br>"projectId": "1148045530467663872",<br>"values": [<br>{<br>"valKey": "ip",<br>"valValue": "10.125.30.80",<br>"valType": "text",<br>"enabled": true<br>},<br>{<br>"valKey": "redmine-adt",<br>"valValue": "21060",<br>"valType": "text",<br>"enabled": true<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": {<br>"uuid": "99e8cf2c02fa4002933be037dc933630",<br>"id": "99e8cf2c-02fa-4002-933b-e037dc933630",<br>"name": "10.125.30.123",<br>"projectId": "1148045530467663872",<br>"createdAt": null,<br>"values": [<br>{<br>"id": 78,<br>"envId": "99e8cf2c02fa4002933be037dc933630",<br>"valKey": "ip",<br>"valValue": "10.125.31.240",<br>"valType": "text",<br>"enabled": true<br>},<br>{<br>"id": 79,<br>"envId": "99e8cf2c02fa4002933be037dc933630",<br>"valKey": "redmine-adt",<br>"valValue": "21060",<br>"valType": "text",<br>"enabled": true<br>}<br>]<br>},<br>"code": ""<br>\} |  |  |  |

#### 根据组织ID获取测试环境

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/{projectId}/env | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 组织ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": [<br>{<br>"uuid": "2ad593473b4c42f9960ea721f19e90be",<br>"id": "2ad59347-3b4c-42f9-960e-a721f19e90be",<br>"name": "10.125.30.80",<br>"projectId": "1148045530467663872",<br>"createdAt": "1563970119650"<br>}<br>],<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

#### 查询执行环境详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/env/{id\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 测试环境ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"msg": "",<br>"data": {<br>"uuid": "99e8cf2c02fa4002933be037dc933630",<br>"id": "99e8cf2c-02fa-4002-933b-e037dc933630",<br>"name": "10.125.30.123",<br>"projectId": "1148045530467663872",<br>"createdAt": null,<br>"values": [<br>{<br>"id": 78,<br>"envId": "99e8cf2c02fa4002933be037dc933630",<br>"valKey": "ip",<br>"valValue": "10.125.31.240",<br>"valType": "text",<br>"enabled": true<br>},<br>{<br>"id": 79,<br>"envId": "99e8cf2c02fa4002933be037dc933630",<br>"valKey": "redmine-adt",<br>"valValue": "21060",<br>"valType": "text",<br>"enabled": true<br>}<br>]<br>},<br>"code": ""<br>\} |  |  |  |

#### 复制自动化测试环境

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/env/copyFrom/{fromId\} | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| fromId | true | String | 被复制测试套件ID |
| 示例： 修改的消息体与创建时消息体一致<br>{<br>"name": "10.125.30.80",<br>"projectId": "1148045530467663872"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": {<br>"uuid": "db7f2cb5f1a448128e92fc4712e573ed",<br>"id": "db7f2cb5-f1a4-4812-8e92-fc4712e573ed",<br>"name": "10.125.30.80",<br>"projectId": "1148045530467663872",<br>"createdAt": "1563971012329"<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

### CICD 自动化测试用例执行

#### 调试用例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/testCase/debug | JSON | POST |  |

###### 参数说明

| 参数名 | 类型 | 是否必选 | 说明 |
| --- | --- | --- | --- |
| envId | String | 是 | 环境Id |
| request | Object | 是 | 请求定义 |
| 示例：<br>{<br>"envId": "8c68a02482e44d50bf9a0dea4a94c4c7",<br>"request": {<br>"auth": {<br>"type": "basic",<br>"basic": {<br>"username": "111",<br>"password": "111"<br>},<br>"bearer": null,<br>"apiKey": null<br>},<br>"method": "POST",<br>"header": [<br>{<br>"headerKey": "Content-Type",<br>"value": "application/x-www-form-urlencoded",<br>"desc": ""<br>}<br>],<br>"body": {<br>"mode": "raw",<br>"raw": "[\n\t\"1120165078960701440\"\n\t]"<br>},<br>"url": {<br>"raw": "http://10.125.26.66/dashboard/osDashboard/capacityNew?aaa&aaaa",<br>"query": [<br>{<br>"queryKey": "aaa",<br>"value": "",<br>"desc": ""<br>},<br>{<br>"queryKey": "aaaa",<br>"value": "",<br>"desc": ""<br>}<br>]<br>}<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"code": "ok",<br>"data": {<br>"body": {},<br>"status": {}<br>},<br>"msg": "调试成功",<br>"total": 0<br>\} |  |  |  |

#### 运行单个测试用例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/testCase/execute/{testcaseId\} | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| testcaseId | true | Integer | 测试用例ID |
| envId | true | String | 测试环境Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"code": "ok",<br>"data": {<br>"body": {},<br>"status": {}<br>},<br>"msg": "调试成功",<br>"total": 0<br>\} |  |  |  |

#### 套件用例执行

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/testPlan/execute/{testPlanId\} | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| testPlanId | true | Long | 测试套件ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| results | 数组 | 执行结果数组 |  |
| record | Object | 一次执行记录 |  |
| 返回示例：<br>{<br>"data": {<br>"results": [<br>{<br>"id": "1564652314381",<br>"testCaseName": "testExe2",<br>"testCaseId": "717",<br>"responseTime": 100,<br>"responseSize": 3262,<br>"code": "200",<br>"message": null,<br>"stack": null<br>},<br>{<br>"id": "1564652314382",<br>"testCaseName": "testExe3",<br>"testCaseId": "719",<br>"responseTime": 74,<br>"responseSize": 47,<br>"code": "200",<br>"message": null,<br>"stack": null<br>}<br>],<br>"record": {<br>"id": "1564652312018",<br>"testPlanId": "5",<br>"testHome": "/home/auto_test_plan/5",<br>"testCasePath": "1564652312018/execute-collections.json",<br>"envPath": "1564652312018/execute-environment.json",<br>"resultPath": "1564652312018/execute-result.json",<br>"logPath": "1564652312018/execute-log.log",<br>"executeTime": "1564652312018",<br>"executeStatus": 4,<br>"requests": 2,<br>"items": 2,<br>"tests": 2,<br>"spentTime": 216,<br>"spentAverage": 87,<br>"executions": 2,<br>"failures": 0,<br>"createdAt": "1564652312018"<br>}<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

#### 套件详情的执行历史记录

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/testPlan/execute/history/{testPlanId\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| testPlanId | Long | 是 | 测试套件ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": [<br>{<br>"id": "1564658013259",<br>"testPlanId": "5",<br>"testHome": "/home/auto_test_plan/5",<br>"testCasePath": "1564658013259/execute-collections.json",<br>"envPath": "1564658013259/execute-environment.json",<br>"resultPath": "1564658013259/execute-result.json",<br>"logPath": "1564658013259/execute-log.log",<br>"executeTime": "1564658013259",<br>"executeStatus": 4,<br>"requests": 2,<br>"items": 2,<br>"tests": 2,<br>"spentTime": 243,<br>"spentAverage": 100,<br>"executions": 2,<br>"failures": 0,<br>"createdAt": "1564658013259"<br>},<br>{<br>"id": "1564662964079",<br>"testPlanId": "5",<br>"testHome": "/home/auto_test_plan\\5",<br>"testCasePath": "1564662964079\\execute-collections.json",<br>"envPath": "1564662964079\\execute-environment.json",<br>"resultPath": "1564662964079\\execute-result.json",<br>"logPath": "1564662964079\\execute-log.log",<br>"executeTime": "1564662964079",<br>"executeStatus": 6,<br>"requests": 0,<br>"items": 0,<br>"tests": 0,<br>"spentTime": 0,<br>"spentAverage": 0,<br>"executions": 0,<br>"failures": 0,<br>"createdAt": "1564662964079"<br>}<br>],<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

#### 套件中用例执行日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/testPlan/execute/log/{testPlanId\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| testPlanId | Long | 是 | 测试套件ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": "askjdhflkasjdgfad",<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

### CICD 自动化测试报告

#### 测试套件执行记录的详情基本信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/report/detail/{exeId}/bases | JSON | GET |  |

###### 参数说明

| 参数名 | 类型 | 是否必选 | 说明 |
| --- | --- | --- | --- |
| exeId | Integer | 是 | 一次执行的ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| total | int | 总数 |  |
| successNum | int | 成功执行次数 |  |
| failNum | int | 失败次数 |  |
| env | object | 环境变量 |  |
| exeStartTime | Date | 开始执行时间 |  |
| exeCompletedTime | Date | 执行完成时间 |  |
| dataSize | long | 接收数据 |  |
| responseAverage | float | 平均响应时间 |  |
| 返回示例：<br>{<br>"data": {<br>"total": 1,<br>"successNum": 1,<br>"failNum": 0,<br>"env": null,<br>"exeStartTime": 1563454285485,<br>"exeCompletedTime": 1563454285648,<br>"dataSize": "3272",<br>"responseAverage": 130<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

#### 测试套件执行记录的详情的API列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/report/detail/{exeId}/apis | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| exeId | true | Long | 执行ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | String | 用例id | 待澄清 |
| name | String | 用例名称 |  |
| consumeTime | float | 耗时 |  |
| dataSize | float | 数据量 |  |
| method | String | 请求类型 |  |
| url | String | URL | 未展示query参数 |
| code | Integer | 响应状态码 |  |
| status | String | 响应状态 |  |
| 返回示例：<br>{<br>"data": [<br>{<br>"id": "613409aa-4f55-41a9-865b-536d9f3a340d",<br>"name": "getReports",<br>"method": "GET",<br>"url": "http://10.125.30.80:21400/codeAnalysis/v1.0/reports",<br>"code": 200,<br>"status": "OK",<br>"consumeTime": 130,<br>"dataSize": 3272<br>}<br>],<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

#### 详情API列表中测试用例请求信息和返回信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/nichol/v1.0/apiAutoTest/report/detail/{exeId}/case/{caseId\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| exeId | Long | 是 | 执行记录id |
| cseId | String | 是 | 用例id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| requestHeader | Object | 测试用例请求头 |  |
| responseHeader | Object | 响应头 |  |
| responseBody | Object | 响应体 |  |
| 返回示例：<br>{<br>"data": {<br>"requestHeader": [<br>{<br>"key": "User-Agent",<br>"system": "true",<br>"value": "PostmanRuntime/7.15.1"<br>},<br>{<br>"key": "Accept",<br>"system": "true",<br>"value": "*/*"<br>},<br>{<br>"key": "Cache-Control",<br>"system": "true",<br>"value": "no-cache"<br>},<br>{<br>"key": "Postman-Token",<br>"system": "true",<br>"value": "19d061b2-e225-4293-bc86-7d7dcdda67a9"<br>},<br>{<br>"key": "Host",<br>"system": "true",<br>"value": "10.125.30.80:21400"<br>},<br>{<br>"key": "accept-encoding",<br>"system": "true",<br>"value": "gzip, deflate"<br>},<br>{<br>"key": "Connection",<br>"system": "true",<br>"value": "keep-alive"<br>}<br>],<br>"responseHeader": [<br>{<br>"key": "Connection",<br>"object": null<br>},<br>{<br>"key": "Transfer-Encoding",<br>"object": null<br>},<br>{<br>"key": "Content-Type",<br>"object": null<br>},<br>{<br>"key": "Date",<br>"object": null<br>}<br>],<br>"responseBody": [<br>{<br>"id": "0",<br>"autoCheck": false,<br>"badTaste": 657,<br>"bug": 12,<br>"vulnerability": 10,<br>"codeLines": 6282,<br>"coverage": 0,<br>"language": "java",<br>"name": "manuel",<br>"newAdd": {<br>"badTaste": 4,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 1.8,<br>"scanStatus": 4,<br>"sqProjectKey": "beta_develop-devopscloud-adapter-sonarqube-1562656189691",<br>"taskId": 19,<br>"updateAt": 1563445565225,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": false,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "test_test",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 4,<br>"sqProjectKey": "master-test-1563425932400",<br>"taskId": 33,<br>"updateAt": 1563439505358,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": false,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "test_code_repo",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 4,<br>"sqProjectKey": "master-test-1563431779101",<br>"taskId": 36,<br>"updateAt": 1563439385249,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": false,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "test_one",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 7,<br>"sqProjectKey": "master-test-1563426831473",<br>"taskId": 34,<br>"updateAt": 1563434928638,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": true,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "test_auto_scanner_12345",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 2,<br>"sqProjectKey": "cloudNG_develop-devopscloud-adapter-sonarqube-1563420852271",<br>"taskId": 32,<br>"updateAt": 1563420857708,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": true,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "test_auto_scanner_1234",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 2,<br>"sqProjectKey": "cloudNG_develop-devopscloud-adapter-sonarqube-1563420752455",<br>"taskId": 31,<br>"updateAt": 1563420753046,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": true,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "test_auto_scanner_123",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 2,<br>"sqProjectKey": "cloudNG_develop-devopscloud-adapter-sonarqube-1563415828299",<br>"taskId": 30,<br>"updateAt": 1563415829346,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": true,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "213",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 4,<br>"sqProjectKey": "master-test-1562913680769",<br>"taskId": 28,<br>"updateAt": 1563157085515,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": true,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "3123",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 4,<br>"sqProjectKey": "master-test-1562913038932",<br>"taskId": 27,<br>"updateAt": 1563157085308,<br>"checking": false<br>},<br>{<br>"id": "0",<br>"autoCheck": false,<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"codeLines": 0,<br>"coverage": 0,<br>"language": null,<br>"name": "èŒƒå¼",<br>"newAdd": {<br>"badTaste": 0,<br>"bug": 0,<br>"vulnerability": 0<br>},<br>"repeatRate": 0,<br>"scanStatus": 1,<br>"sqProjectKey": "master-test-1562913705156",<br>"taskId": 29,<br>"updateAt": 1562913705156,<br>"checking": false<br>}<br>]<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |  |

### CICD 代码分析代码库管理

#### 获取代码库地址列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/connectors/v1.0/codes/accounts/{projectId\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 组织id |
| url | false | String（qs参数） | git库地址 |
| name | false | String  (query) | 名称 |
| http | false | boolean | 协议 |
| thirdPartyName | false | String  (query) | 第三方名字 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | Integer | 代码库id |  |
| adminPrivateToken | String | token |  |
| url | String | 代码库地址 |  |
| projectId | String | 项目id |  |
| name | String | 代码库名称 |  |
| apiVersionLabel | String | 标签 |  |
| apiVersion | String | 版本 |  |
| 返回示例：<br>{<br>"data": [<br>{<br>"id": 20,<br>"adminPrivateToken": "",<br>"apiVersion": "v4",<br>"url": "http://10.125.30.80:21100/test/test",<br>"projectId": "1134275050367614976",<br>"name": "test",<br>"apiVersionLabel": "GitLab V4",<br>"apiVersion": "1.2"<br>},<br>{<br>"id": 21,<br>"adminPrivateToken": "",<br>"apiVersion": "v4",<br>"url": "http://10.125.196.111:21100/h3ccloud/devopscloud/devopscloud-nichol",<br>"projectId": "1134275050367614976",<br>"name": "TestApi",<br>"apiVersionLabel": "GitLab V4"<br>"apiVersion": "1.2"<br>}<br>],<br>"msg": "OK",<br>"code": "200",<br>"total": 2<br>\} |  |  |  |

#### 获取代码分支列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/connectors/v1.0/branch/{codeId\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| codeId | true | Integer (path) | 代码库ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>[<br>{<br>"name": "V_B02_190424_0426M",<br>"commit": {<br>"id": "9a1611f110d00c7ae459ab41a24c1d5b21f9a2f1",<br>"short_id": "9a1611f1",<br>"title": "Merge branch 'bugfix/planUpdateErr' into 'rc/B02_190424'",<br>"created_at": "2019-04-26T11:29:34.000+08:00",<br>"parent_ids": null,<br>"message": "Merge branch 'bugfix/planUpdateErr' into 'rc/B02_190424'",<br>"author_name": "wangtijun (Cloud)",<br>"author_email": "<wang.tijun@h3c.com>",<br>"authored_date": "2019-04-26T11:29:34.000+08:00",<br>"committer_name": "wangtijun (Cloud)",<br>"committer_email": "<wang.tijun@h3c.com>",<br>"committed_date": "2019-04-26T11:29:34.000+08:00"<br>},<br>"merged": false,<br>"protected": false,<br>"developers_can_push": false,<br>"developers_can_merge": false<br>},<br>{<br>"name": "cloudNG_develop",<br>"commit": {<br>"id": "a101304b5772a519acf8428b553d5706cf5e9aa9",<br>"short_id": "a101304b",<br>"title": "Merge branch 'bugfix/add-moduleName-w15862' into 'cloudNG_develop'",<br>"created_at": "2019-05-10T17:38:12.000+08:00",<br>"parent_ids": null,<br>"message": "Merge branch 'bugfix/add-moduleName-w15862' into 'cloudNG_develop'",<br>"author_name": "wenlang (Cloud)",<br>"author_email": "<wen.lang@h3c.com>",<br>"authored_date": "2019-05-10T17:38:12.000+08:00",<br>"committer_name": "wenlang (Cloud)",<br>"committer_email": "<wen.lang@h3c.com>",<br>"committed_date": "2019-05-10T17:38:12.000+08:00"<br>},<br>"merged": false,<br>"protected": true,<br>"developers_can_push": false,<br>"developers_can_merge": false<br>}<br>] |  |  |  |

#### 3.20.3   获取取编程语言列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/system/languages | JSON | GET |  |

###### 参数说明

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | int | 语言id |  |
| fullName | String | 语言名称 |  |
| supportBuildType | String | 支持的构建类型 |  |
| is_system | int | 是否系统内置 |  |
| is_deleted | boolean | 是否已删除 |  |
| tenantId |  | 租户id |  |
| description |  | 语言描述 |  |
| createAt |  | 创建时间 |  |
| 返回示例：<br>{<br>"data": [<br>{<br>"id": 1,<br>"fullName": "java",<br>"description": "系统内置java语言",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"createAt": "159123987945",<br>"isSystem": 1,<br>"supportBuildType": "GRADLE;SBT;MAVEN",<br>"deleted": false<br>},<br>{<br>"id": 2,<br>"fullName": "py",<br>"description": "系统内置python2",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"createAt": "159123987945",<br>"isSystem": 1,<br>"supportBuildType": null,<br>"deleted": false<br>}<br>],<br>"msg": "success",<br>"code": "OK",<br>"total": 1<br>\} |  |  |  |

### CICD 代码扫描任务

#### 新建代码扫描任务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/task/createNew | JSON | POST |  |

###### 参数说明

| 参数名 | 类型及范围 | 必选 | 说明 | 备注 |
| --- | --- | --- | --- | --- |
| user_id | String | 是 | 用户id(header) |  |
| taskName | String | 是 | 任务名称 | 长度255 |
| codeRepo | String | 是 | 代码库地址 | 长度255 |
| branch | String | 是 | 代码分支 | 长度128 |
| language | int | 是 | 编程语言 | 传入的是 id， 基于 3.20.3 接口获取 |
| buildMethod | String | 是 | 编译方式 | 编译性语言需要传入 |
| compileModel | Integer | 是 | 编译方式code | SBT (0), GRADLE (1), MAVEN (2), GO (3), ANT (4) |
| projectId | String | 是 | 项目id |  |
| tenantId | String | 否 | 租户id |  |
| operatorId | String | 是 | 操作员id |  |
| ruleSetId | int | 否 | 规则集id |  |
| 请求示例：<br>{<br>"taskName": "demo-spring",<br>"autoCheck": false,<br>"codeRepo": "ssh://git@10.125.196.110:21102/h3ccloud/devopscloud/demos/spring-boot-maven",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"branch": "master",<br>"language": 1,<br>"tenantId": "",<br>"ruleSetId": "",<br>"buildMethod": "GRADLE",<br>"compileModel": 1,<br>"codeConfigId": 14<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": {<br>"id": 6,<br>"taskName": "demo-spring",<br>"description": null,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"operatorId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"currConfigId": 6,<br>"pipelineId": null,<br>"createAt": "1635743600060",<br>"lastCheckTime": null,<br>"execStatus": 2,<br>"isDeleted": 0,<br>"qualityGate": 0,<br>"sqProjectKey": "master-spring-boot-maven-1635743600060",<br>"lastExecutionId": null<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": 1<br>\} |  |  |  |

#### 扫描任务列表&搜索

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/reports | JSON | GET |  |

###### 参数说明

| 参数名 | 类型及范围 | 是否必选 | 说明 |
| --- | --- | --- | --- |
| taskName | string | 否 | 任务名 |
| tenantId | string | 否 | 租户id |
| projectId | string | 否 | 项目id |
| page | int | 是 | 分页 |
| size | int | 是 | 分页 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": [<br>{<br>"taskId": 6,<br>"sqProjectKey": "master-spring-boot-maven-1635743600060",<br>"name": "demo-spring",<br>"updateAt": "1635743600060",<br>"language": null,<br>"codeLines": 0,<br>"coverage": 0,<br>"repeatRate": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"badTaste": 0,<br>"isChecking": false,<br>"newAdd": {<br>"bug": 0,<br>"vulnerability": 0,<br>"badTaste": 0<br>},<br>"autoCheck": false,<br>"scanStatus": 1<br>},<br>{<br>"taskId": 5,<br>"sqProjectKey": "master-spring-boot-maven-1634627506942",<br>"name": "spring-boot",<br>"updateAt": "1634629800133",<br>"language": "java",<br>"codeLines": 39,<br>"coverage": 0,<br>"repeatRate": 0,<br>"bug": 0,<br>"vulnerability": 0,<br>"badTaste": 1,<br>"isChecking": false,<br>"newAdd": {<br>"bug": 0,<br>"vulnerability": 0,<br>"badTaste": 0<br>},<br>"autoCheck": false,<br>"scanStatus": 4<br>}    ],<br>"msg": "success",<br>"code": "OK",<br>"total": 2<br>\} |  |  |  |

#### scanner端创建扫描任务配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeScanner/v1.0/scanner/taskConfig/create | JSON | POST |  |

###### 参数说明

| 参数名 | 类型及范围 | 说明 | 是否必选 |
| --- | --- | --- | --- |
| codeBranch | String | 代码分支 | 是 |
| codeRepo | String | 代码库URL | 是 |
| compileModel | Integer | 编译方式；<br>SBT (0), GRADLE (1), MAVEN (2), GO (3), ANT (4) | 是 |
| languages | String | 开发语言 | 是 |
| ruleSetId | int | 规则集id | 是 |
| sqProjectKey | String | sqProjectKey | 是 |
| srcPath | String | SrcPath | 是 |
| taskId | int | taksID | 是 |
| 请求示例：<br>{<br>"codeBranch": "develop",<br>"codeRepo": "http://10.125.196.110:21100/h3ccloud/devopscloud/devopscloud-adapter-sonarqube.git",<br>"compileModel": 2,<br>"languages": "java",<br>"ruleSetId": 1,<br>"sqProjectKey": "test12334",<br>"srcPath": "src",<br>"taskId": 3<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": {<br>"id": 66,<br>"tenantId": "198239883",<br>"taskId": 3,<br>"ruleSetId": 1,<br>"codeRepo": "http://10.125.196.110:21100/h3ccloud/devopscloud/devopscloud-adapter-sonarqube.git",<br>"codeBranch": "develop",<br>"sqProjectKey": "test12334",<br>"createdOn": null,<br>"operatorId": "198239883",<br>"languages": "java",<br>"srcPath": "src",<br>"compileModel": 2<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": 1<br>\} |  |  |  |

#### 以任务查询标签列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/codeAnalysisDetail/issues/details/list/{sqProjectKey\} | JSON | GET |  |

###### 参数说明

| 参数 | 类型 | 是否必选 | 说明 |
| --- | --- | --- | --- |
| sqProjectKey | String | 是 | 任务列表都有唯一的sqProjectKey，带人表示该任务的唯一标示 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": [<br>{<br>"tag": "unused"<br>},<br>{<br>"tag": "cert"<br>}<br>],<br>"msg": "success",<br>"code": "OK",<br>"total": 2<br>\} |  |  |  |

#### 扫描任务问题列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/codeAnalysisDetail/issues/details/list/{sqProjectKey\} | JSON | GET |  |

###### 参数说明

| 参数名 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| sqProjectKey | String | 任务列表都有唯一的sqProjectKey，带人表示该任务的唯一标示 | 列表数据中有返回，可以咨询中山 |
| page | Int | 分页参数 |  |
| size | Int | 分页参数 |  |
| tags | String | 标签参数，不同标签按逗号隔开 | 举例：tags=cert,unused |
| types | String | 类型参数，不同类型按逗号隔开,<br>参数定义：<br>BUG<br>VULNERABILITY  漏洞<br>CODE_SMELL 坏味道 | 举例：types=CODE_SMELL,VULNERABILITY |
| severities | String | 严重程度参数，不同严重程度按逗号<br>BLOCKER   阻断<br>MINOR  次要<br>CRITICAL 严重<br>MAJOR  主要<br>INFO 提示 | 举例：severities=MAJOR,MINOR |
| authors | String | 查询作者问题，不同作者按照逗号隔开<br>作者暂定传递查询邮箱地址 | 举例：authors=1@3.com，2@6.com |
| sinceLeakPeriod | boolean | true/false | sinceLeakPeriod |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| line | int | 标示问题在代码的行数 |  |
| severity | String | 严重程度 |  |
| rule | String | 违反规则 |  |
| component | String | 文件路径，获得代码详情需要此参数 |  |
| creationDate | Data | 创建时间，标记问题产生日期 |  |
| status | String | 状态 |  |
| message | String | 提示信息 |  |
| type | String | 类型 |  |
| tags | Array | 标签集合 |  |
| textRange | Object | 为json对象，标记下划线开始截止位置，以下为对象参数 |  |
| textRange.startLine | int | 开始行数 |  |
| textRange.endLine | int | 结束行数 |  |
| textRange.startOffset | int | 下划线开始位置 |  |
| textRange.endOffset | int | 下划线结束位置 |  |
| 返回示例：<br>{<br>"data": [<br>{<br>"key": "AWslM6kiyZgn2BLe6NYu",<br>"line": 48,<br>"severity": "CRITICAL",<br>"rule": "squid:S3776",<br>"project": "devopscloud-adapter-sonarqube-develop",<br>"component": "devopscloud-adapter-sonarqube-develop:src/main/java/com/h3c/devopscloud/sonarqube/utils/http/HttpUtils.java",<br>"creationDate": "2019-03-17T08:55:30+0000",<br>"textRange": {<br>"startLine": 48,<br>"endLine": 48,<br>"startOffset": 26,<br>"endOffset": 30<br>},<br>"status": "OPEN",<br>"message": "Refactor this method to reduce its Cognitive Complexity from 19 to the 15 allowed.",<br>"author": "he.zhongshan@h3c.com",<br>"type": "CODE_SMELL",<br>"tags": [<br>"brain-overload"<br>]<br>},<br>{<br>"key": "AWslM6keyZgn2BLe6NYl",<br>"line": 55,<br>"severity": "CRITICAL",<br>"rule": "squid:S1192",<br>"project": "devopscloud-adapter-sonarqube-develop",<br>"component": "devopscloud-adapter-sonarqube-develop:src/main/java/com/h3c/devopscloud/sonarqube/utils/http/HttpUtils.java",<br>"creationDate": "2019-03-17T08:55:30+0000",<br>"textRange": {<br>"startLine": 55,<br>"endLine": 55,<br>"startOffset": 35,<br>"endOffset": 44<br>},<br>"status": "OPEN",<br>"message": "Define a constant instead of duplicating this literal \"user_id\" 8 times.",<br>"author": "he.zhongshan@h3c.com",<br>"type": "CODE_SMELL",<br>"tags": [<br>"design"<br>]<br>}<br>],<br>"msg": "success",<br>"code": "OK",<br>"total": 12<br>\} |  |  |  |

#### scanner端创建扫描任务配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeScanner/v1.0/scanner/taskConfig | JSON | POST |  |

###### 参数说明

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| codeBranch | String | 代码分支 |  |
| codeRepo | String | 代码库URL |  |
| compileModel | Integer | 编译方式 | SBT (0), GRADLE (1), MAVEN (2), GO (3), ANT (4) |
| languages | String | 开发语言 |  |
| ruleSetId | int | 否 | 规则集id |
| sqProjectKey | String | sqProjectKey |  |
| srcPath | String | SrcPath |  |
| taskId | int | taksID |  |
| tenantId | String | 租户 id |  |
| 请求示例：<br>{<br>"codeBranch": "develop",<br>"codeRepo": "http://10.125.196.110:21100/h3ccloud/devopscloud/devopscloud-adapter-sonarqube.git",<br>"compileModel": 2,<br>"languages": "java",<br>"ruleSetId": 1,<br>"sqProjectKey": "test12334",<br>"srcPath": "src",<br>"taskId": 3<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": {<br>"id": 66,<br>"tenantId": "198239883",<br>"taskId": 3,<br>"ruleSetId": 1,<br>"codeRepo": "http://10.125.196.110:21100/h3ccloud/devopscloud/devopscloud-adapter-sonarqube.git",<br>"codeBranch": "develop",<br>"sqProjectKey": "test12334",<br>"createdOn": null,<br>"operatorId": "198239883",<br>"languages": "java",<br>"srcPath": "src",<br>"compileModel": 2<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": 1<br>\} |  |  |  |

#### 问题详情信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/codeAnalysisDetail/issues/details/line | JSON | GET |  |

###### 参数说明

| 返回键 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- |
| start | int | 是 | 开始行数  从1开始 |
| end | int | 是 | 结束行数 |
| component | String | 是 | 文件路径，获得代码详情需要此参数，由代码扫描详情接口给出 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": [<br>{<br>"code": "<span class="k">package</span> com.h3c.devopscloud.sonarqube.utils.http;",<br>"duplicated": "false",<br>"line": "1",<br>"scmAuthor": "he.zhongshan@h3c.com",<br>"scmDate": "2019-02-22T13:08:31+0000",<br>"scmRevision": "62e0a964a1e0253990cba33f8993e794dccdf787"<br>},<br>{<br>"code": "",<br>"duplicated": "false",<br>"line": "2",<br>"scmAuthor": "he.zhongshan@h3c.com",<br>"scmDate": "2019-02-22T13:08:31+0000",<br>"scmRevision": "62e0a964a1e0253990cba33f8993e794dccdf787"<br>}<br>],<br>"msg": "success",<br>"code": "OK",<br>"total": 2<br>\} |  |  |  |

#### 具体文件路径下所有问题汇总接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/codeAnalysisDetail/issues/details/allIssueByFile | JSON | GET |  |

###### 参数说明

| 返回键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| component | String | 是 | 文件路径，获得代码详情需要此参数，由代码扫描详情接口给出 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| line | int | 代码哪一行存在问题，line=0表示文件存在问题，需要在代码开始前面提示问题 |  |
| severity | String | 严重程度 |  |
| rule | String | 违背规则 |  |
| component | String | 文件路径 |  |
| creationDate | Data | 文件创建时间 |  |
| textRange | object | 同扫描问题列表说明 |  |
| type | String | 同扫描问题列表说明 |  |
| tags | String | 同扫描问题列表说明 |  |
| message | String | 同扫描问题列表说明 |  |
| 返回示例：<br>参考扫描任务问题列表 |  |  |  |

#### 删除扫描任务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/task/{taskId\} | JSON | DELETE |  |

###### 参数说明

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| taskId | int | 是 | 任务ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 返回示例：<br>{<br>"data": "",<br>"msg": "delete task by id success!",<br>"code": "codeAnalysis.task.moved_temp",<br>"total": null<br>\} |  |  |  |

#### 规则列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/rules | JSON | GET |  |

###### 参数说明

| 返回键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | int | 否 | 分页 |
| size | int | 否 | 分页 |
| languages | String | 否 | 编程语言<br>java:"Java"<br>js:"JavaScript"<br>jsp:"JSP"<br>php:"PHP"<br>py:"Python"<br>web:"Web"<br>xml:"XML" |
| types | String | 否 | 类型<br>BUG<br>CODE_SMELL（坏味道）<br>VULNERABILITY（漏洞） |
| tags | 标签 | 否 | 标签 |
| severity | String | 否 | 严重程度<br>BLOCKER：阻断<br>CRITICAL：严重<br>MAJOR：主要<br>INFO：提示<br>MINOR：次要 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| rules | Array | 规则列表 | 第二层 |
| facets | Array | 过滤器数据 | 第二层 |
| key | String | 规则key |  |
| name | String | 规则名称 |  |
| tags | Array | 规则标签 |  |
| sysTags | Array | 规则系统标签 |  |
| langName | String | 编程语言 |  |
| lang | String | 编程语言简写 |  |
| type | String | 类型 |  |
| 返回示例：<br>{<br>"data": {<br>"rules": [<br>{<br>"key": "squid:S2204",<br>"name": "\".equals()\" should not be used to test the values of \"Atomic\" classes",<br>"status": "READY",<br>"tags": [<br>"cwe",<br>"i18n"<br>],<br>"sysTags": [<br>"multi-threading"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "BUG"<br>},<br>{<br>"key": "squid:S1161",<br>"name": "\"@Override\" should be used on overriding and implementing methods",<br>"status": "READY",<br>"tags": [<br>"api-design",<br>"backbone"<br>],<br>"sysTags": [<br>"bad-practice"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "CODE_SMELL"<br>},<br>{<br>"key": "squid:S1258",<br>"name": "Classes and enums with private members should have a constructor",<br>"status": "READY",<br>"tags": [],<br>"sysTags": [<br>"pitfall"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "CODE_SMELL"<br>},<br>{<br>"key": "squid:S2225",<br>"name": "\"toString()\" and \"clone()\" methods should not return null",<br>"status": "READY",<br>"tags": [],<br>"sysTags": [<br>"cwe"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "BUG"<br>},<br>{<br>"key": "squid:S00115",<br>"name": "Constant names should comply with a naming convention",<br>"status": "READY",<br>"tags": [],<br>"sysTags": [<br>"convention"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "CODE_SMELL"<br>},<br>{<br>"key": "squid:S2589",<br>"name": "Boolean expressions should not be gratuitous",<br>"status": "READY",<br>"tags": [],<br>"sysTags": [<br>"cert",<br>"cwe",<br>"misra",<br>"redundant"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "CODE_SMELL"<br>},<br>{<br>"key": "squid:S2226",<br>"name": "Servlets should not have mutable instance fields",<br>"status": "READY",<br>"tags": [],<br>"sysTags": [<br>"cert",<br>"multi-threading",<br>"struts"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "BUG"<br>},<br>{<br>"key": "squid:S00116",<br>"name": "Field names should comply with a naming convention",<br>"status": "READY",<br>"tags": [],<br>"sysTags": [<br>"convention"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "CODE_SMELL"<br>},<br>{<br>"key": "squid:S1135",<br>"name": "Track uses of \"TODO\" tags",<br>"status": "READY",<br>"tags": [],<br>"sysTags": [<br>"cwe"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "CODE_SMELL"<br>},<br>{<br>"key": "squid:S00113",<br>"name": "Files should contain an empty new line at the end",<br>"status": "READY",<br>"tags": [],<br>"sysTags": [<br>"convention"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"type": "CODE_SMELL"<br>}<br>],<br>"facets": [<br>{<br>"property": "languages",<br>"values": [<br>{<br>"val": "java",<br>"count": 1669<br>},<br>{<br>"val": "py",<br>"count": 240<br>},<br>{<br>"val": "js",<br>"count": 187<br>},<br>{<br>"val": "php",<br>"count": 127<br>},<br>{<br>"val": "web",<br>"count": 59<br>},<br>{<br>"val": "xml",<br>"count": 13<br>},<br>{<br>"val": "jsp",<br>"count": 12<br>}<br>]<br>},<br>{<br>"property": "types",<br>"values": [<br>{<br>"val": "CODE_SMELL",<br>"count": 993<br>},<br>{<br>"val": "BUG",<br>"count": 553<br>},<br>{<br>"val": "VULNERABILITY",<br>"count": 123<br>}<br>]<br>},<br>{<br>"property": "severities",<br>"values": [<br>{<br>"val": "MAJOR",<br>"count": 1060<br>},<br>{<br>"val": "INFO",<br>"count": 231<br>},<br>{<br>"val": "MINOR",<br>"count": 229<br>},<br>{<br>"val": "CRITICAL",<br>"count": 110<br>},<br>{<br>"val": "BLOCKER",<br>"count": 39<br>}<br>]<br>},<br>{<br>"property": "tags",<br>"values": [<br>{<br>"val": "correctness",<br>"count": 309<br>},<br>{<br>"val": "style",<br>"count": 166<br>},<br>{<br>"val": "cwe",<br>"count": 137<br>},<br>{<br>"val": "bad-practice",<br>"count": 107<br>},<br>{<br>"val": "cert",<br>"count": 100<br>},<br>{<br>"val": "performance",<br>"count": 100<br>},<br>{<br>"val": "multi-threading",<br>"count": 81<br>},<br>{<br>"val": "convention",<br>"count": 47<br>},<br>{<br>"val": "pitfall",<br>"count": 46<br>},<br>{<br>"val": "error-handling",<br>"count": 33<br>}<br>]<br>}<br>]<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1669"<br>\} |  |  |  |

#### 规则详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/rules/{key\} | JSON | GET |  |

###### 参数说明

| 返回键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| key | String | 是 | 规则key |

###### 正常返回结果

| 返回键 | 返回值含义 | 说明 |
| --- | --- | --- |
| data | 返回数据 |  |
| rule | 规则详情 |  |
| htmlDesc | 规则描述HTML语法 |  |
| mdDesc | 规则描述md语法 |  |
| key | 规则key |  |
| name | 规则名称 |  |
| tags | 规则标签 | 详情标签处为tags和sysTags，tags可以编辑，sysTags不可编辑 |
| sysTags | 规则系统标签 |  |
| severity | 严重程度 |  |
| 返回示例：<br>{<br>"data": {<br>"rule": {<br>"key": "squid:S2204",<br>"repo": "squid",<br>"name": "\".equals()\" should not be used to test the values of \"Atomic\" classes",<br>"createdAt": "2019-03-28T11:55:17+0000",<br>"htmlDesc": "<p><code>AtomicInteger</code>, and <code>AtomicLong</code> extend <code>Number</code>, but they're distinct from <code>Integer</code> and\n<code>Long</code> and should be handled differently. <code>AtomicInteger</code> and <code>AtomicLong</code> are designed to support lock-free,\nthread-safe programming on single variables. As such, an <code>AtomicInteger</code> will only ever be \"equal\" to itself. Instead, you should\n<code>.get()</code> the value and make comparisons on it.</p>\n<p>This applies to all the atomic, seeming-primitive wrapper classes: <code>AtomicInteger</code>, <code>AtomicLong</code>, and\n<code>AtomicBoolean</code>.</p>\n<h2>Noncompliant Code Example</h2>\n<pre>\nAtomicInteger aInt1 = new AtomicInteger(0);\nAtomicInteger aInt2 = new AtomicInteger(0);\n\nif (aInt1.equals(aInt2)) { ... }  // Noncompliant\n</pre>\n<h2>Compliant Solution</h2>\n<pre>\nAtomicInteger aInt1 = new AtomicInteger(0);\nAtomicInteger aInt2 = new AtomicInteger(0);\n\nif (aInt1.get() == aInt2.get()) { ... }\n</pre>",<br>"htmlNote": "威风为人父",<br>"mdDesc": "<p><code>AtomicInteger</code>, and <code>AtomicLong</code> extend <code>Number</code>, but they're distinct from <code>Integer</code> and\n<code>Long</code> and should be handled differently. <code>AtomicInteger</code> and <code>AtomicLong</code> are designed to support lock-free,\nthread-safe programming on single variables. As such, an <code>AtomicInteger</code> will only ever be \"equal\" to itself. Instead, you should\n<code>.get()</code> the value and make comparisons on it.</p>\n<p>This applies to all the atomic, seeming-primitive wrapper classes: <code>AtomicInteger</code>, <code>AtomicLong</code>, and\n<code>AtomicBoolean</code>.</p>\n<h2>Noncompliant Code Example</h2>\n<pre>\nAtomicInteger aInt1 = new AtomicInteger(0);\nAtomicInteger aInt2 = new AtomicInteger(0);\n\nif (aInt1.equals(aInt2)) { ... }  // Noncompliant\n</pre>\n<h2>Compliant Solution</h2>\n<pre>\nAtomicInteger aInt1 = new AtomicInteger(0);\nAtomicInteger aInt2 = new AtomicInteger(0);\n\nif (aInt1.get() == aInt2.get()) { ... }\n</pre>",<br>"mdNote": "威风为人父",<br>"noteLogin": "admin",<br>"severity": "MAJOR",<br>"status": "READY",<br>"isTemplate": false,<br>"tags": [<br>"cwe",<br>"i18n"<br>],<br>"sysTags": [<br>"multi-threading"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"params": [],<br>"defaultDebtRemFnType": "CONSTANT_ISSUE",<br>"defaultDebtRemFnOffset": "5min",<br>"debtOverloaded": false,<br>"debtRemFnType": "CONSTANT_ISSUE",<br>"debtRemFnOffset": "5min",<br>"defaultRemFnType": "CONSTANT_ISSUE",<br>"defaultRemFnBaseEffort": "5min",<br>"remFnType": "CONSTANT_ISSUE",<br>"remFnBaseEffort": "5min",<br>"remFnOverloaded": false,<br>"type": "BUG"<br>},<br>"actives": []<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |

#### 为规则添加标签

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/rules/{key\} | JSON | PUT |  |

###### 参数说明

| 返回键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| key | int | 是 | 规则key |
| tags | List | 否 | 标签列表 |
| 消息体：<br>["tag1", "tag2"] |  |  |  |

###### 正常返回结果

| 返回键 | 返回值含义 | 说明 |
| --- | --- | --- |
| data | 返回数据 |  |
| rule | 规则详情 |  |
| htmlDesc | 规则描述HTML语法 |  |
| mdDesc | 规则描述md语法 |  |
| key | 规则key |  |
| name | 规则名称 |  |
| tags | 规则标签 |  |
| sysTags | 规则系统标签 |  |
| severity | 严重程度 |  |
| 返回体:<br>{<br>"data": {<br>"rule": {<br>"key": "squid:S2204",<br>"repo": "squid",<br>"name": "\".equals()\" should not be used to test the values of \"Atomic\" classes",<br>"createdAt": "2019-03-28T11:55:17+0000",<br>"htmlDesc": "<p><code>AtomicInteger</code>, and <code>AtomicLong</code> extend <code>Number</code>, but they're distinct from <code>Integer</code> and\n<code>Long</code> and should be handled differently. <code>AtomicInteger</code> and <code>AtomicLong</code> are designed to support lock-free,\nthread-safe programming on single variables. As such, an <code>AtomicInteger</code> will only ever be \"equal\" to itself. Instead, you should\n<code>.get()</code> the value and make comparisons on it.</p>\n<p>This applies to all the atomic, seeming-primitive wrapper classes: <code>AtomicInteger</code>, <code>AtomicLong</code>, and\n<code>AtomicBoolean</code>.</p>\n<h2>Noncompliant Code Example</h2>\n<pre>\nAtomicInteger aInt1 = new AtomicInteger(0);\nAtomicInteger aInt2 = new AtomicInteger(0);\n\nif (aInt1.equals(aInt2)) { ... }  // Noncompliant\n</pre>\n<h2>Compliant Solution</h2>\n<pre>\nAtomicInteger aInt1 = new AtomicInteger(0);\nAtomicInteger aInt2 = new AtomicInteger(0);\n\nif (aInt1.get() == aInt2.get()) { ... }\n</pre>",<br>"htmlNote": "威风为人父",<br>"mdDesc": "<p><code>AtomicInteger</code>, and <code>AtomicLong</code> extend <code>Number</code>, but they're distinct from <code>Integer</code> and\n<code>Long</code> and should be handled differently. <code>AtomicInteger</code> and <code>AtomicLong</code> are designed to support lock-free,\nthread-safe programming on single variables. As such, an <code>AtomicInteger</code> will only ever be \"equal\" to itself. Instead, you should\n<code>.get()</code> the value and make comparisons on it.</p>\n<p>This applies to all the atomic, seeming-primitive wrapper classes: <code>AtomicInteger</code>, <code>AtomicLong</code>, and\n<code>AtomicBoolean</code>.</p>\n<h2>Noncompliant Code Example</h2>\n<pre>\nAtomicInteger aInt1 = new AtomicInteger(0);\nAtomicInteger aInt2 = new AtomicInteger(0);\n\nif (aInt1.equals(aInt2)) { ... }  // Noncompliant\n</pre>\n<h2>Compliant Solution</h2>\n<pre>\nAtomicInteger aInt1 = new AtomicInteger(0);\nAtomicInteger aInt2 = new AtomicInteger(0);\n\nif (aInt1.get() == aInt2.get()) { ... }\n</pre>",<br>"mdNote": "威风为人父",<br>"noteLogin": "admin",<br>"severity": "MAJOR",<br>"status": "READY",<br>"isTemplate": false,<br>"tags": [<br>"cwe",<br>"i18n"<br>],<br>"sysTags": [<br>"multi-threading"<br>],<br>"lang": "java",<br>"langName": "Java",<br>"params": [],<br>"defaultDebtRemFnType": "CONSTANT_ISSUE",<br>"defaultDebtRemFnOffset": "5min",<br>"debtOverloaded": false,<br>"debtRemFnType": "CONSTANT_ISSUE",<br>"debtRemFnOffset": "5min",<br>"defaultRemFnType": "CONSTANT_ISSUE",<br>"defaultRemFnBaseEffort": "5min",<br>"remFnType": "CONSTANT_ISSUE",<br>"remFnBaseEffort": "5min",<br>"remFnOverloaded": false,<br>"type": "BUG"<br>}<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |

#### 查询标签列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/ruleTags | JSON | GET |  |

###### 参数说明

| 返回键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| filter | String | 否 | 按标签名称过滤 |

###### 正常返回结果

| 返回键 | 返回值含义 | 说明 |
| --- | --- | --- |
| data | 返回数据 |  |
| rule | 规则详情 |  |
| htmlDesc | 规则描述HTML语法 |  |
| mdDesc | 规则描述md语法 |  |
| key | 规则key |  |
| name | 规则名称 |  |
| tags | 规则标签 |  |
| sysTags | 规则系统标签 |  |
| severity | 严重程度 |  |
| 返回体:<br>{<br>"data": {<br>"tags": [<br>"accessibility",<br>"wasc",<br>"websphere"<br>]<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |

#### 任务执行状态同步接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/task/sync/{taskId\} | JSON | GET |  |

###### 参数说明

| 返回键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| taskId | Integer | 是 | taskId |
| status | Object | 是 | 任务状态同步 Body |
| 请求体：<br>{<br>“ executeId”: 123<br>"execStatus":3<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 返回值含义 | 说明 |
| --- | --- | --- |
| 返回体:<br>{<br>"data": {<br>"id": 51,<br>"taskName": "110C",<br>"description": null,<br>"tenantId": "3dd4c93f453246588e18b3d587837165",<br>"operatorId": "74dea9e1d67a47d8b2f86b9ae8df5eeb",<br>"projectId": "1122439730793807872",<br>"currConfigId": 52,<br>"pipelineId": "1138716785265082368",<br>"createAt": "1560326212574",<br>"lastCheckTime": "1560326356218",<br>"execStatus": 3,<br>"isDeleted": 0,<br>"qualityGate": 0,<br>"sqProjectKey": "master-js-scanner1560326212574",<br>"lastExecutionId": "53"<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": "1"<br>\} |  |  |

#### 执行扫描任务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeAnalysis/v1.0/task/execute/{taskId\} | JSON | POST |  |

###### 参数说明

| 返回键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| taskId | Integer | 是 | 扫描任务id |

###### 正常返回结果

| 返回键 | 返回值含义 | 说明 |
| --- | --- | --- |
| 返回体:<br>{<br>"data": {<br>"id": 3,<br>"taskName": "dsd",<br>"description": null,<br>"tenantId": "9aa54f4b273f485d95b14072d4e13d28",<br>"operatorId": "36dd339c59b947b69ee93d83699890ca",<br>"projectId": "1115201663339069440",<br>"currConfigId": 3,<br>"pipelineId": "1118388495887368192",<br>"createAt": "1555479570071",<br>"lastCheckTime": "1560498079960",<br>"execStatus": 2,<br>"isDeleted": 0,<br>"qualityGate": 0,<br>"sqProjectKey": "weddd-web1555479570071",<br>"lastExecutionId": "59"<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": 1<br>\} |  |  |

#### 执行扫描任务scanner端

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cicd/codeScanner/v1.0/scanner/codeScanner/{taskId\} | JSON | POST |  |

###### 参数说明

| 返回键 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| taskId | Integer | 是 | 扫描任务id |

###### 正常返回结果

| 返回键 | 返回值含义 | 说明 |
| --- | --- | --- |
| 返回体:<br>{<br>"data": {<br>"id": 95,<br>"taskId": 82,<br>"operatorId": "74dea9e1d67a47d8b2f86b9ae8df5eeb",<br>"execStatus": 2,<br>"createdOn": 1560607136737,<br>"sqProjectKey": "cloudNG_develop-devopscloud-adapter-sonarqube-1560511086325",<br>"finished": 0<br>},<br>"msg": "success",<br>"code": "OK",<br>"total": 1<br>\} |  |  |

### CICD质量门禁

#### 查询质量门禁列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/qualitygates/qualitygates | JSON | GET | 查询质量门禁列表 |

###### 参数说明

| 参数名 | 类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| projectId | Query | true | String | 所属组织 |
| name | Query | true | String | 查询的质量门禁名称 |
| size | Query | false | int | 分页查询每页查询的个数，大于等于1 |
| page | Query | false | int | 查询页数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回的数据 |
| total | Integer | 数据总数 | 数据总数 |
| 例如：<br>{<br>"msg": "",<br>"data": [<br>{<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "现场v在",<br>"description": "",<br>"metricConditions": {<br>"unitTest": {<br>"passRate": {<br>"condition": 100,<br>"operator": ">=",<br>"name": "passRate"<br>},<br>"name": "unitTest"<br>},<br>"apiTest": {<br>"passRate": {<br>"condition": 100,<br>"operator": ">=",<br>"name": "passRate"<br>},<br>"name": "apiTest"<br>},<br>"codeCheck": {<br>"bugs": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "bugs"<br>},<br>"vulnerabilities": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "vulnerabilities"<br>},<br>"codeSmells": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "codeSmells"<br>},<br>"duplicatedLinesDensity": {<br>"condition": 5,<br>"operator": "<=",<br>"name": "duplicatedLinesDensity"<br>},<br>"name": "codeCheck"<br>}<br>},<br>"id": "1450363990394798080",<br>"refs": 1,<br>"checks": 0,<br>"blocks": 0,<br>"passes": 0,<br>"createdBy": "1df71957d770436493a84f24d9344853",<br>"createdAt": 1634628696000,<br>"lastModifiedBy": "1df71957d770436493a84f24d9344853",<br>"lastModifiedAt": 1634628696000<br>},<br>{<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "x",<br>"description": "",<br>"metricConditions": {<br>"unitTest": {<br>"passRate": {<br>"condition": 100,<br>"operator": ">=",<br>"name": "passRate"<br>},<br>"name": "unitTest"<br>},<br>"apiTest": {<br>"passRate": {<br>"condition": 100,<br>"operator": ">=",<br>"name": "passRate"<br>},<br>"name": "apiTest"<br>},<br>"codeCheck": {<br>"bugs": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "bugs"<br>},<br>"vulnerabilities": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "vulnerabilities"<br>},<br>"codeSmells": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "codeSmells"<br>},<br>"duplicatedLinesDensity": {<br>"condition": 5,<br>"operator": "<=",<br>"name": "duplicatedLinesDensity"<br>},<br>"name": "codeCheck"<br>}<br>},<br>"id": "1432903915019436032",<br>"refs": 0,<br>"checks": 0,<br>"blocks": 0,<br>"passes": 0,<br>"createdBy": "1df71957d770436493a84f24d9344853",<br>"createdAt": 1630465890000,<br>"lastModifiedBy": "1df71957d770436493a84f24d9344853",<br>"lastModifiedAt": 1630465890000<br>}<br>],<br>"code": "",<br>"total": 2<br>\} |  |  |  |

#### 创建质量门禁

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/qualitygates/qualitygates | JSON | POST | 创建质量门禁 |

###### 参数说明

| 参数名 | 类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| name | String | true | Body | 名称 |
| description | String | false | Body | 描述信息 |
| metricConditions | Object | true | Json | 请求体数据 |
| 例如：<br>{<br>"metricConditions": {<br>"apiTest": {<br>"passRate": {<br>"condition": 100<br>}<br>},<br>"unitTest": {<br>"passRate": {<br>"condition": 100<br>}<br>},<br>"codeCheck": {<br>"bugs": {<br>"condition": 10<br>},<br>"duplicatedLinesDensity": {<br>"condition": 5<br>},<br>"vulnerabilities": {<br>"condition": 10<br>},<br>"codeSmells": {<br>"condition": 100<br>}<br>}<br>},<br>"name": "string",<br>"description": "string",<br>"projectId": "test"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回的数据 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"metricConditions": {<br>"apiTest": {<br>"passRate": {<br>"condition": 100<br>}<br>},<br>"unitTest": {<br>"passRate": {<br>"condition": 100<br>}<br>},<br>"codeCheck": {<br>"bugs": {<br>"condition": 10<br>},<br>"duplicatedLinesDensity": {<br>"condition": 5<br>},<br>"vulnerabilities": {<br>"condition": 10<br>},<br>"codeSmells": {<br>"condition": 100<br>}<br>}<br>},<br>"lastModifiedAt": 100,<br>"blocks": 100,<br>"lastModifiedBy": "string",<br>"description": "this is description",<br>"createdAt": 100,<br>"checks": 100,<br>"passes": 100,<br>"refs": 100,<br>"createdBy": "string",<br>"name": "string",<br>"id": "10",<br>"projectId": "string"<br>}<br>\} |  |  |  |

#### 更新质量门禁

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/qualitygates/qualitygates/{id\} | JSON | PUT | 更新质量门禁 |

###### 参数说明

| 参数名 | 类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| id | String | true | String (Path) | 要更新的质量门禁Id |
| name | String | true | Body | 名称 |
| description | String | false | Body | 描述信息 |
| projectId | String | true | Body | 项目 id |
| metricConditions | Object | true | Body | 请求体数据 |
| 例如：<br>{<br>"metricConditions": {<br>"apiTest": {<br>"passRate": {<br>"condition": 100<br>}<br>},<br>"unitTest": {<br>"passRate": {<br>"condition": 100<br>}<br>},<br>"codeCheck": {<br>"bugs": {<br>"condition": 10<br>},<br>"duplicatedLinesDensity": {<br>"condition": 5<br>},<br>"vulnerabilities": {<br>"condition": 10<br>},<br>"codeSmells": {<br>"condition": 100<br>}<br>}<br>},<br>"name": "string",<br>"description": "string",<br>"projectId": "test"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回的数据 |
| 例如：<br>{<br>"msg": "",<br>"data": {<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "demo",<br>"description": "sdfsd",<br>"metricConditions": {<br>"unitTest": {<br>"passRate": {<br>"condition": 100,<br>"operator": ">=",<br>"name": "passRate"<br>},<br>"name": "unitTest"<br>},<br>"apiTest": {<br>"passRate": {<br>"condition": 100,<br>"operator": ">=",<br>"name": "passRate"<br>},<br>"name": "apiTest"<br>},<br>"codeCheck": {<br>"bugs": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "bugs"<br>},<br>"vulnerabilities": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "vulnerabilities"<br>},<br>"codeSmells": {<br>"condition": 0,<br>"operator": "<=",<br>"name": "codeSmells"<br>},<br>"duplicatedLinesDensity": {<br>"condition": 5,<br>"operator": "<=",<br>"name": "duplicatedLinesDensity"<br>},<br>"name": "codeCheck"<br>}<br>},<br>"id": "1455034327657086976",<br>"refs": 0,<br>"checks": 0,<br>"blocks": 0,<br>"passes": 0,<br>"createdBy": "1df71957d770436493a84f24d9344853",<br>"createdAt": 1635742191000,<br>"lastModifiedBy": "1df71957d770436493a84f24d9344853",<br>"lastModifiedAt": 1635742518466<br>},<br>"code": ""<br>\} |  |  |  |

#### 删除质量门禁

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/qualitygates/qualitygates/{id\} | JSON | DELETE | 删除质量门禁 |

###### 参数说明

| 参数名 | 类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| id | PathVariable | true | String | 要删除的质量门禁Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回的数据 |
| {<br>"msg": "",<br>"code": "",<br>“data”: null<br>\} |  |  |  |

#### 获取质量门禁详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/qualitygates/qualitygates/{id\} | JSON | GET | 获取质量门禁详情 |

###### 参数说明

| 参数名 | 类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| id | String | true | String(Path) | 要获取的质量门禁Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回的数据 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"metricConditions": {<br>"apiTest": {<br>"passRate": {<br>"condition": 100<br>}<br>},<br>"unitTest": {<br>"passRate": {<br>"condition": 100<br>}<br>},<br>"codeCheck": {<br>"bugs": {<br>"condition": 10<br>},<br>"duplicatedLinesDensity": {<br>"condition": 5<br>},<br>"vulnerabilities": {<br>"condition": 10<br>},<br>"codeSmells": {<br>"condition": 100<br>}<br>}<br>},<br>"lastModifiedAt": 100,<br>"blocks": 100,<br>"lastModifiedBy": "string",<br>"description": "this is description",<br>"createdAt": 100,<br>"checks": 100,<br>"passes": 100,<br>"refs": 100,<br>"createdBy": "string",<br>"name": "string",<br>"id": "10",<br>"projectId": "string"<br>}<br>\} |  |  |  |

#### 查询质量门禁名称是否可用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/qualitygates/qualitygate-names | JSON | POST | 获取质量门禁详情 |

###### 参数说明

| 参数名 | 类型 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- | --- |
| 请求体 | Body | true | Json | 请求体数据 |
| {<br>“excludeId”: nul<br>"name": "demo",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回的数据 |
| 例如：<br>{<br>"msg": "",<br>"data": {<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "demo",<br>"excludeId": null,<br>"occupied": true<br>},<br>"code": ""<br>\} |  |  |  |

### 服务网关接口组管理

#### 接口组查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apigroups | JSON | GET | 获取接口列表 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | int | 分页查询的页数，大于等于1 |
| size | true | int | 分页查询每页查询的个数，大于等于1 |
| name | false | String | 查询条件的name |
| orderBy | false | String | 排序条件 |
| id | false | String | 查询条件id |

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回公有镜像信息 |
| total | Integer | 公有镜像个数 | 公有镜像个数 |
| 例如：<br>{<br>"msg": "",<br>"total": 2,<br>"code": "",<br>"data": [<br>{<br>"id": "93ffe733-3f9b-4383-80d9-8e99cdecfc66",<br>"number": "3",<br>"name": "srhtst",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"description": "srhtst",<br>"createTime": "2021-10-22T03:15:28.394+0000",<br>"updateTime": "2021-10-22T03:15:38.082+0000",<br>"projectName": "root"<br>},<br>{<br>"id": "75c875a9-e31a-48b1-a11a-14af90fc59d5",<br>"number": "1",<br>"name": "g1",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"description": "",<br>"createTime": "2021-10-22T02:59:49.583+0000",<br>"updateTime": "2021-10-22T02:59:49.583+0000",<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 创建接口组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apigroups | JSON | POST | 创建接口组 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

###### 请求体

| name | true | String | 接口组的名字 |
| --- | --- | --- | --- |
| description | true | String | 接口组的描述 |
| 例如：<br>{<br>"name": "test",<br>"description": "testdescrip"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回创建接口组详情 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "096b0c24-7e54-4e6d-b40e-f0bee174ddd5",<br>"number": null,<br>"name": "test",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"description": "testdescrip",<br>"createTime": "2021-10-28T07:46:14.098+0000",<br>"updateTime": "2021-10-28T07:46:14.098+0000"<br>},<br>"status": true<br>\} |  |  |  |

#### 修改接口组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apigroups | JSON | PUT | 修改接口组 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

###### 请求体

| id | true | String | 接口组Id |
| --- | --- | --- | --- |
| name | true | String | 接口组名称 |
| description | true | String | 接口组描述. |
| 例如：<br>{<br>"id": "096b0c24-7e54-4e6d-b40e-f0bee174ddd5",<br>"name": "test",<br>"description": "testdescrip2"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回修改后的接口详情 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "096b0c24-7e54-4e6d-b40e-f0bee174ddd5",<br>"number": null,<br>"name": "test",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"description": "testdescrip2",<br>"createTime": "2021-10-28T07:46:14.098+0000",<br>"updateTime": "2021-10-28T07:48:12.572+0000"<br>},<br>"status": true<br>\} |  |  |  |

#### 删除接口组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apigroups/{id}/delete | JSON | DELETE | 删除接口组 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | ture | String | 接口组的id |

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常全部删除成功情况下，code为空串 |
| msg | String | 错误信息 | 正常全部删除成功情况下，msg为空串 |
| data | Object | null | null |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>1.删除单个镜像成功或者批量删除全部镜像成功时返回<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

### 服务网关接口管理

#### 查询接口列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis | JSON | GET | 查询接口列表 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | int | 分页查询的页数，大于等于1 |
| size | true | int | 分页查询每页查询的个数，大于等于1 |
| queryName | false | String | 查询条件，按接口名称查询 |
| orderBy | false | String | 排序条件 |
| groupId | false | String | 查询条件,按接口组查询 |
| authPolicy | false | String | 查询条件,按认证策略id查询 |
| accessPolicy | false | String | 查询条件,按访问策略id查询 |
| flowControlPolicy | false | String | 查询条件,按流控策略id查询 |
| status | false | int | 查询条件，接口上下线 |
| apiStatus | false | String | 查询条件，接口状态 |
| id | false | String | 查询条件，接口id |

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |
| 例如：<br>/cloud/svcgw/v1.0/apimgt/apis?page=1&size=10 |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回接口的信息 | 返回接口信息 |
| total | Integer | 数量 | 接口数量 |
| 例如：<br>{<br>"msg": "",<br>"total": 4,<br>"code": "",<br>"data": [<br>{<br>"id": "a78666077dca40f1b954df1d27a11d40",<br>"name": "srhtst1",<br>"type": "REST",<br>"groupName": "srhtst",<br>"groupId": "93ffe733-3f9b-4383-80d9-8e99cdecfc66",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"description": "srhtst1",<br>"authPolicy": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"authPolicyVO": {<br>"id": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"projectId": null,<br>"name": "srhtst",<br>"description": null,<br>"number": null,<br>"type": "jwt",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": null,<br>"group": null,<br>"credentialId": null,<br>"createTime": null,<br>"config": null<br>},<br>"accessPolicy": "9f97cf2b-381a-48b8-866e-69dd312eddf6",<br>"accessPolicyVO": null,<br>"flowControlPolicy": null,<br>"flowControlPolicyVO": null,<br>"protocol": "http",<br>"host": "10.125.28.86",<br>"port": 48000,<br>"path": "/srhtst1",<br>"method": "GET",<br>"regex": 0,<br>"paramModel": "mapping",<br>"svcId": "90e75d82426249d3b07b32da2227ee90",<br>"svcProtocol": "http",<br>"status": 1,<br>"apiStatus": "NORMAL",<br>"accessStatus": "NORMAL",<br>"authStatus": "NORMAL",<br>"flowControlStatus": "NORMAL",<br>"config": null,<br>"createTime": "2021-10-22T05:41:04.112+0000",<br>"subAuth": null,<br>"number": 1,<br>"subProjects": [<br>{<br>"apiId": "a78666077dca40f1b954df1d27a11d40",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"projectName": "root"<br>}<br>],<br>"cacheEnable": null,<br>"cacheTTL": null,<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 查询接口详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/{id\} | JSON | GET | 查询秘钥对 |

###### 参数说明

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 查询接口id |

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回接口详细的信息 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "a78666077dca40f1b954df1d27a11d40",<br>"name": "srhtst1",<br>"apiType": "REST",<br>"groupId": "93ffe733-3f9b-4383-80d9-8e99cdecfc66",<br>"groupName": "srhtst",<br>"description": "srhtst1",<br>"authPolicy": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"authPolicyVO": {<br>"id": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"projectId": null,<br>"name": "srhtst",<br>"description": "",<br>"number": null,<br>"type": "jwt",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"group": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"credentialId": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"createTime": "2021-10-22T03:49:21.218+0000",<br>"config": "{\"claims_to_verify\":[\"exp\"],\"maximum_expiration\":1296000}"<br>},<br>"accessPolicy": "9f97cf2b-381a-48b8-866e-69dd312eddf6",<br>"accessPolicyVO": {<br>"id": "9f97cf2b-381a-48b8-866e-69dd312eddf6",<br>"projectId": null,<br>"name": "srhtst",<br>"type": "IP",<br>"action": "FORBID",<br>"ipAddress": "[\"10.125.104.154\"]",<br>"number": null,<br>"createTime": "2021-10-22T06:39:03.418+0000",<br>"updateTime": "2021-10-22T06:39:03.418+0000",<br>"config": null<br>},<br>"flowControlPolicy": null,<br>"flowControlPolicyVO": null,<br>"kongProtocol": "http",<br>"kongMethod": "GET",<br>"path": "/srhtst1",<br>"kongPort": 48000,<br>"kongHost": "10.125.28.86",<br>"kongPorts": [<br>48000<br>],<br>"kongHosts": [<br>"10.125.28.86"<br>],<br>"kongUrl": [<br>"10.125.28.86:48000/srhtst1"<br>],<br>"regex": 0,<br>"apiVersion": "v1",<br>"versionDescription": "",<br>"svcProtocol": "http",<br>"svcHost": "10.125.31.185:8080",<br>"svcMethod": "GET",<br>"svcPath": "/",<br>"retries": 5,<br>"connectTimeout": 60000,<br>"paramModel": "mapping",<br>"svcId": "90e75d82426249d3b07b32da2227ee90",<br>"status": 1,<br>"inputParam": null,<br>"inputParamType": null,<br>"outputParam": null,<br>"outputParamType": null,<br>"serviceName": null,<br>"wsdlUrl": null,<br>"namespace": null,<br>"endpoint": null,<br>"binding": null,<br>"soapAction": null,<br>"portType": null,<br>"operation": null,<br>"inputName": null,<br>"outputName": null,<br>"apiStatus": "NORMAL",<br>"accessStatus": "NORMAL",<br>"authStatus": "NORMAL",<br>"flowControlStatus": "NORMAL",<br>"config": null,<br>"userId": null,<br>"userName": null,<br>"projectId": null,<br>"projectName": null,<br>"createTime": "2021-10-22T05:41:04.112+0000",<br>"number": 1,<br>"weights": "100",<br>"serviceList": [<br>{<br>"host": "10.125.31.185:8080",<br>"weight": 100<br>}<br>],<br>"algorithm": "round-robin",<br>"hashOn": "none",<br>"hashOnHeader": null,<br>"hashOnCookie": null,<br>"hashOnCookiePath": null,<br>"enableStripPathPrefix": false,<br>"pathPrefix": null<br>},<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 创建接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis | JSON | POST | 创建接口 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |
| Content-Type | true | String | application/json |

###### 请求体

| groupId | true | String | 接口组的id |
| --- | --- | --- | --- |
| groupName | false | String | 接口组的名称 |
| name | true | String | 接口名称 |
| authPolicy | false | String | 认证策略 |
| accessPolicy | false | String | 访问策略ID |
| flowControlPolicy | false | String | 流控策略ID |
| description | false | String | 接口描述ID |
| kongProtocol | true | String | 对外暴露的接口协议 |
| path | true | String | 对外暴露的接口路径 |
| kongMethod | true | String | 对外暴露的疾控方法 |
| apiType | true | String | 接口类型，枚举类SOAP2REST或者REST |
| regex | False | Integer | 是否正则匹配 |
| paramModel | false | String | 接口访问类型 |
| svcProtocol | false | String | 入参请求模式：mapping(入参映射)、passthrough(入参透传) |
| wsdlUrl | false | String | WSDL 的url,当apiType为SOAP2REST的时候传入 |
| namespace | false | String | SOAP接口的namespace,当apiType为SOAP2REST的时候传入 |
| endpoint | false | String | SOAP接口的endpoint,当apiType为SOAP2REST的时候传入 |
| binding | false | String | SOAP接口绑定的服务名,当apiType为SOAP2REST的时候传入 |
| soapAction | false | String | SOAP接口，可选填 |
| operation | false | String | SOAP接口的operation,,当apiType为SOAP2REST的时候传入 |
| svcMethod | true | String | 后端请求的method |
| svcPath | false | String | 后端请求的path |
| serviceList | false | array | 后端服务列表 |
| serviceList.host | false | string | 后端服务地址 |
| serviceList.weight | false | int | 当前地址的权重，取值范围：0-1000，默认值100 |
| algorithm | optional | string | 负载均衡策略，默认为round-robin，可选值: "consistent-hashing"，"least-connections"，"round-robin" |
| hashOn | optional | string | 用作哈希输入的内容。使用的none结果是没有散列的加权循环方案。可接受的值是："none"，"ip"，"header"，"cookie"。默认为"none" |
| hashOnHeader | optional | string | 将从中获取值的标头名称作为哈希输入。仅当hashOn设置为header时才需要 |
| hashOnCookie | optional | string | 从中获取值的cookie名称作为哈希输入。仅在hashOn设置为cookie时需要。如果请求中没有指定的cookie，则Kong将生成一个值并在响应中设置cookie |
| hashOnCookiePath | optional | string | 在响应头中设置的cookie路径。仅在hashOn设置为cookie时需要。默认为"/" |
| apiVersion | false | string | 版本号，默认v1 |
| versionDescription | false | string | 版本特性描述 |
| retries | false | int | 接口重试次数，默认为5 |
| connectTimeout | false | int | 接口连接此时，默认60000，单位毫秒 |
| enableStripPathPrefix | false | bool | 是否开启路径前缀截取映射 |
| {<br>"groupId": "93ffe733-3f9b-4383-80d9-8e99cdecfc66",<br>"groupName": "srhtst",<br>"name": "srhtst111",<br>"authPolicy": null,<br>"accessPolicy": null,<br>"flowControlPolicy": null,<br>"description": "srhtst111des",<br>"kongProtocol": "http",<br>"path": "/srhtst1111",<br>"kongMethod": "GET",<br>"apiType": "REST",<br>"regex": 0,<br>"paramModel": "mapping",<br>"svcProtocol": "http",<br>"wsdlUrl": "",<br>"namespace": "",<br>"endpoint": "",<br>"binding": "",<br>"soapAction": "",<br>"operation": "",<br>"svcMethod": "GET",<br>"svcPath": "/test",<br>"serviceList": [<br>{<br>"host": "10.111.111.111:2312",<br>"weight": 100<br>}<br>],<br>"algorithm": "round-robin",<br>"hashOn": "none",<br>"hashOnHeader": "",<br>"hashOnCookie": null,<br>"hashOnCookiePath": null,<br>"retries": 5,<br>"connectTimeout": 60000,<br>"apiVersion": "v1",<br>"versionDescription": "",<br>"enableStripPathPrefix": false<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回秘钥对的详情信息 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 删除接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/{id}/delete | JSON | DELETE | 删除接口 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

| param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | ture | String | 接口的id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回删除秘钥对成功的结果信息 | "message": "success" |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量删除接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/batchDelete | JSON | POST | 批量删除接口 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

###### 请求体(form-data)

| ids | true | JSONObject | 接口列表id组 |
| --- | --- | --- | --- |
| 例如：<br>{"ids":["1212bd00404149339dc1e6c77e2d210e","db0aa87b45d640529314e41c6dd87d90"]\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回删除秘钥对成功的结果信息 | "message": "success" |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 修改接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis | JSON | PUT | 修改接口 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |
| Content-Type | true | String | application/json |

###### 请求体

| groupId | true | String | 接口组的id |
| --- | --- | --- | --- |
| id | true | String | 接口id |
| groupName | true | String | 接口组的名称 |
| name | true | String | 接口名称 |
| description | true | String | 接口描述 |
| kongProtocol | true | String | 对外暴露的接口协议 |
| path | true | String | 对外暴露的接口路径 |
| kongMethod | true | String | 对外暴露的疾控方法 |
| apiType | true | String | 接口类型，枚举类SOAP2REST或者REST |
| paramModel | true | String | 接口访问类型 |
| svcProtocol | true | String | 入参请求模式：mapping(入参映射)、passthrough(入参透传) |
| wsdlUrl | false | String | WSDL 的url,当apiType为SOAP2REST的时候传入 |
| namespace | false | String | SOAP接口的namespace,当apiType为SOAP2REST的时候传入 |
| endpoint | false | String | SOAP接口的endpoint,当apiType为SOAP2REST的时候传入 |
| binding | false | String | SOAP接口绑定的服务名,当apiType为SOAP2REST的时候传入 |
| soapAction | false | String | SOAP接口，可选填 |
| operation | false | String | SOAP接口的operation,,当apiType为SOAP2REST的时候传入 |
| svcMethod | true | String | 后端请求的method |
| svcPath | false | String | 后端请求的path |
| serviceList | true | array | 后端服务列表 |
| serviceList.host | true | string | 后端服务地址 |
| serviceList.weight | true | int | 当前地址的权重，取值范围：0-1000，默认值100 |
| algorithm | optional | string | 负载均衡策略，默认为round-robin，可选值: "consistent-hashing"，"least-connections"，"round-robin" |
| hashOn | optional | string | 用作哈希输入的内容。使用的none结果是没有散列的加权循环方案。可接受的值是："none"，"ip"，"header"，"cookie"。默认为"none" |
| hashOnHeader | optional | string | 将从中获取值的标头名称作为哈希输入。仅当hashOn设置为header时才需要 |
| hashOnCookie | optional | string | 从中获取值的cookie名称作为哈希输入。仅在hashOn设置为cookie时需要。如果请求中没有指定的cookie，则Kong将生成一个值并在响应中设置cookie |
| hashOnCookiePath | optional | string | 在响应头中设置的cookie路径。仅在hashOn设置为cookie时需要。默认为"/" |
| apiVersion | true | string | 版本号，默认v1 |
| versionDescription | false | string | 版本特性描述 |
| retries | true | int | 接口重试次数，默认为5 |
| connectTimeout | true | int | 接口连接此时，默认60000，单位毫秒 |
| authPolicy | false | string | 认证策略ID |
| authPolicyVO | False | Jsonobject | 认证策略详情 |
| accessPolicy | False | String | 访问策略ID |
| accessPolicyVO | False | Jsonobject | 访问策略详情 |
| flowControlPolicy | False | String | 流控策略ID |
| flowControlPolicyVO | False | Jsonobject | 流控策略详情 |
| kongHost | False | string | 接口请求的Host |
| kongPorts | False | Integer[] | 接口请求的ports |
| kongHosts | False | String[] | 接口请求的Hosts |
| kongUrl | false | String[] | 接口请求地址 |
| regex | false | integer | 是否正则匹配: 0(no),1(yes) |
| svcId | false | String | 后端服务id,创建API不用传入 |
| inputParam | false | JSONObject | 入参,当apiType为REST的时候，需要传入 |
| inputParamType | false | String | 入参请求模式,当apiType为REST的时候，需要传入 |
| outputParam | False | JSONObject | 出参,当apiType为REST的时候，需要传入 |
| outputParamType | false | String | 出参请求模式,当apiType为REST的时候，需要传入 |
| serviceName | false | String | 可以为null,当apiType为SOAP2REST的时候，后端自动获取 |
| portType | false | String | SOAP的port类型，可以不填，后端获取 |
| inputName | false | String | SOAP接口的传入名字，后端自动获取 |
| outputName | false | String | SOAP接口的传出名字，后端自动获取 |
| apiStatus | false | integer | 当前api状态 |
| accessStatus | false | integer | 访问策略状态 |
| authStatus | false | integer | 认证策略状态 |
| flowControlStatus | false | integer | 流控策略状态 |
| config | false | JSONObject | 动态路由配置 |
| userId | false | String | 创建者id |
| userName | false | String | 创建者名称 |
| projectId | false | String | 所属组织id |
| projectName | false | String | 所属组织名称 |
| createTime | false | Date | 创建时间 |
| number | false | Integer | 订阅app数量 |
| weights | false | String | 权重 |
| enableStripPathPrefix | false | Boolean | 是否开启路径前缀截取映射 |
| {<br>"id": "af0aafff563d4a79b1a571eb198e531b",<br>"name": "test",<br>"apiType": "REST",<br>"groupId": "4bf5c39a-d980-4d84-bac0-1c97183488c4",<br>"groupName": "g1",<br>"description": "12",<br>"authPolicy": null,<br>"authPolicyVO": null,<br>"accessPolicy": null,<br>"accessPolicyVO": null,<br>"flowControlPolicy": null,<br>"flowControlPolicyVO": null,<br>"kongProtocol": "http",<br>"kongMethod": "GET",<br>"path": "/papakjak",<br>"kongPort": 48000,<br>"kongHost": "10.125.29.82",<br>"kongPorts": [<br>48000<br>],<br>"kongHosts": [<br>"10.125.29.82"<br>],<br>"kongUrl": [<br>"10.125.29.82:48000/papakjak"<br>],<br>"regex": 0,<br>"apiVersion": "v1",<br>"versionDescription": "1",<br>"svcProtocol": "http",<br>"svcHost": "10.125.31.125:80",<br>"svcMethod": "GET",<br>"svcPath": "/paht",<br>"retries": 5,<br>"connectTimeout": 60000,<br>"paramModel": "mapping",<br>"svcId": "c3dc5fb70f6747ed942bbf540cce3952",<br>"status": 1,<br>"inputParam": null,<br>"inputParamType": null,<br>"outputParam": null,<br>"outputParamType": null,<br>"serviceName": null,<br>"wsdlUrl": null,<br>"namespace": null,<br>"endpoint": null,<br>"binding": null,<br>"soapAction": null,<br>"portType": null,<br>"operation": null,<br>"inputName": null,<br>"outputName": null,<br>"apiStatus": "NORMAL",<br>"accessStatus": "NORMAL",<br>"authStatus": "NORMAL",<br>"flowControlStatus": "NORMAL",<br>"config": null,<br>"userId": null,<br>"userName": null,<br>"projectId": null,<br>"projectName": null,<br>"createTime": "2021-11-06T08:26:03.829+0000",<br>"number": 0,<br>"weights": "100",<br>"serviceList": [<br>{<br>"host": "10.125.31.125:80",<br>"weight": 100<br>}<br>],<br>"algorithm": "round-robin",<br>"hashOn": "none",<br>"hashOnHeader": null,<br>"hashOnCookie": null,<br>"hashOnCookiePath": null,<br>"enableStripPathPrefix": false<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回秘钥对的详情信息 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 参数校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/check/urlParams | JSON | POST | 参数校验接口 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

###### 请求体(form-data)

| id | true | String | 接口ID |
| --- | --- | --- | --- |
| method | true | String | 校验参数，方法校验 |
| path | true | String | 校验参数，路径校验 |
| {<br>"id": "af0aafff563d4a79b1a571eb198e531b",<br>"method": "GET",<br>"path": "/srh"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 成功返回为null |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": true,<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 接口上线

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/{apiId}/online | JSON | POST | 接口上线 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

| Param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiId | true | String | 上线接口的id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回结果信息 |  |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 接口下线

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/{apiId}/offline | JSON | POST | 接口下线 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

| Param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiId | true | String | 下线接口的id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回结果信息 |  |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 接口请求次数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/monitoring/apis/{apiId}/total | JSON | GET | 接口请求次数 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

| Param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiId | true | String | 接口的id |
| start | true | String | 起始时间戳 |
| end | true | String | 结束时间戳 |
| step | false | String | 查询步长 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 接口返回信息 |  |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"metric": {},<br>"description": "Total of abdebb3627ad4e61a9dc3e7c2899c3b3 request numbers",<br>"values": [<br>[<br>1.635496290877E9,<br>"0"<br>],<br>[<br>1.635496350877E9,<br>"0"<br>],<br>[<br>1.635496410877E9,<br>"0"<br>]<br>]<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 接口访问出错统计

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/monitoring/apis/{apiId}/error | JSON | GET | 接口访问出错统计 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

| Param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiId | true | String | 接口的id |
| start | true | String | 起始时间戳 |
| end | true | String | 结束时间戳 |
| step | false | String | 查询步长 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 接口返回信息 |  |
| {<br>"msg": "",<br>"total": 2,<br>"code": "",<br>"data": [<br>{<br>"description": "Total of abdebb3627ad4e61a9dc3e7c2899c3b3 request numbers start with 4"<br>},<br>{<br>"metric": {},<br>"description": "Total of abdebb3627ad4e61a9dc3e7c2899c3b3 request numbers start with 5",<br>"values": [<br>[<br>1.635496290877E9,<br>"0"<br>],<br>[<br>1.635496350877E9,<br>"0"<br>],<br>[<br>1.635496410877E9,<br>"0"<br>]<br>]<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 接口调用时延

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/monitoring/apis/{apiId}/latency | JSON | GET | 接口调用时延 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

| Param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiId | true | String | 接口的id |
| start | true | String | 起始时间戳 |
| end | true | String | 结束时间戳 |
| step | false | String | 查询步长 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 接口返回信息 |  |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"metric": {<br>"endpoint": "admin",<br>"instance": "10.125.29.84:24101",<br>"route": "9e30b256-b15e-40b6-b4e3-7bce48c8dfc5",<br>"service": "svcgwbdfa8a08-svcgwf59ad009-kong-admin-svc",<br>"namespace": "svcgwbdfa8a08",<br>"exported_service": "service_abdebb3627ad4e61a9dc3e7c2899c3b3",<br>"job": "svcgwbdfa8a08-svcgwf59ad009-kong-admin-svc",<br>"type": "request"<br>},<br>"description": "Request average latency",<br>"values": [<br>[<br>1.635496290877E9,<br>"NaN"<br>],<br>[<br>1.635496350877E9,<br>"NaN"<br>],<br>[<br>1.635496410877E9,<br>"NaN"<br>]<br>]<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 接口数据流量

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/monitoring/apis/{apiId}/total | JSON | GET | 接口数据流量 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |

| Param参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiId | true | String | 接口的id |
| start | true | String | 起始时间戳 |
| end | true | String | 结束时间戳 |
| step | false | String | 查询步长 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 接口返回信息 |  |
| {<br>"msg": "",<br>"total": 2,<br>"code": "",<br>"data": [<br>{<br>"metric": {<br>"endpoint": "admin",<br>"instance": "10.125.29.84:24101",<br>"route": "9e30b256-b15e-40b6-b4e3-7bce48c8dfc5",<br>"service": "svcgwbdfa8a08-svcgwf59ad009-kong-admin-svc",<br>"namespace": "svcgwbdfa8a08",<br>"exported_service": "service_abdebb3627ad4e61a9dc3e7c2899c3b3",<br>"job": "svcgwbdfa8a08-svcgwf59ad009-kong-admin-svc",<br>"type": "egress"<br>},<br>"description": "Bandwidth of abdebb3627ad4e61a9dc3e7c2899c3b3 and the type is egress",<br>"values": [<br>[<br>1.635496290877E9,<br>"0"<br>],<br>[<br>1.635496350877E9,<br>"0"<br>],<br>[<br>1.635496410877E9,<br>"0"<br>]<br>]<br>},<br>{<br>"metric": {<br>"endpoint": "admin",<br>"instance": "10.125.29.84:24101",<br>"route": "9e30b256-b15e-40b6-b4e3-7bce48c8dfc5",<br>"service": "svcgwbdfa8a08-svcgwf59ad009-kong-admin-svc",<br>"namespace": "svcgwbdfa8a08",<br>"exported_service": "service_abdebb3627ad4e61a9dc3e7c2899c3b3",<br>"job": "svcgwbdfa8a08-svcgwf59ad009-kong-admin-svc",<br>"type": "ingress"<br>},<br>"description": "Bandwidth of abdebb3627ad4e61a9dc3e7c2899c3b3 and the type is ingress",<br>"values": [<br>[<br>1.635496290877E9,<br>"0"<br>],<br>[<br>1.635496350877E9,<br>"0"<br>],<br>[<br>1.635496410877E9,<br>"0"<br>]<br>]<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 接口名称校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/name/check | JSON | POST | 接口名称校验 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 过网关对应的apimgt的service的k8s DNS解析后IP |
| Content-Type | true | String | application/json |

###### 请求体

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | api名称 |
| groupId | true | String | api组id |
| {<br>"name": "tests",<br>"groupId": "1c2c442b-823e-4905-9236-fd7c7bbb6061"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | boolean | 校验结果 | 名称可用为true，否则为false |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": true,<br>"resource": null,<br>"status": true<br>\} |  |  |  |

### 服务网关接口版本管理

#### 接口版本列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/{apiId}/versions | JSON | GET | 获取接口版本列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiId | true | string | api id |
| page | true | int | 分页序列数 |
| size | true | int | 分页大小 |
| orderBy | false | string | 排序字段，以desc或asc结尾，示例：name desc（表示根据name字段降序排序） |
| apiVersion | false | string | 根据版本号查询 |
| Id | False | String | 版本ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回列表 |
| total | Integer | 列表的条数 | 列表的条数 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "90e75d82426249d3b07b32da2227ee90",<br>"apiId": "a78666077dca40f1b954df1d27a11d40",<br>"apiVersion": "v1",<br>"versionDescription": "",<br>"enableDefault": true,<br>"protocol": "http",<br>"method": "GET",<br>"path": "/",<br>"serviceList": [<br>{<br>"host": "10.125.31.185:8080",<br>"weight": 100<br>}<br>],<br>"retries": 5,<br>"connectTimeout": 60000,<br>"algorithm": "round-robin",<br>"hashOn": "none",<br>"hashOnHeader": null,<br>"hashOnCookie": null,<br>"hashOnCookiePath": null,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createTime": "2021-10-22T05:41:04.112+0000",<br>"enableCustom": false,<br>"itemsHeader": null,<br>"itemsBody": null<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 添加接口版本

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/versions | JSON | POST | 添加接口版本 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| apiId | true | string | api id |
| --- | --- | --- | --- |
| apiVersion | true | string | 版本号 |
| versionDescription | false | string | 版本特性描述 |
| serviceList | true | array | 后端服务列表 |
| serviceList.host | true | string | 后端服务地址，ip地址或域名+端口号，端口默认值：http为80，https为443 |
| serviceList.weight | true | int | 当前地址的权重，取值范围：0-1000，默认值100 |
| algorithm | true | string | 使用哪种负载均衡算法。可接受的值是："consistent-hashing"，"least-connections"，"round-robin"。默认为"round-robin"。 |
| hashOn | optional | string | 用作哈希输入的内容。使用的none结果是没有散列的加权循环方案。可接受的值是："none"，"ip"，"header"，"cookie"。默认为"none" |
| hashOnHeader | optional | string | 将从中获取值的标头名称作为哈希输入。仅当hashOn设置为header时才需要 |
| hashOnCookie | optional | string | 从中获取值的cookie名称作为哈希输入。仅在hashOn设置为cookie时需要。如果请求中没有指定的cookie，则Kong将生成一个值并在响应中设置cookie |
| hashOnCookiePath | optional | string | 在响应头中设置的cookie路径。仅在hashOn设置为cookie时需要。默认为"/" |
| enableCustom | false | boolean | 是否开启自定义规则，默认为false，为true时，itemsHeader和itemsBody字段不能同时为空 |
| itemsHeader | optional | array | 在请求头Header中的一个或多个键值对，如请求头中存在其中某个或多个键值对，则匹配该路由。例，itemsHeader : [ ["k1:v1"], ["k2:v2", "k3:v3"] ] |
| itemsBody | optional | array | 在请求头Body中的一个或多个键值对，如请求体中存在其中某个或多个键值对，则匹配该路由。例，itemsHeader : [ ["k1:v1"], ["k2:v2", "k3:v3"] ] |
| 例如：<br>{<br>"apiId": "{{apiId}}",<br>"apiVersion": "v1.0.6",<br>"versionDescription": "test",<br>"serviceList": [<br>{<br>"host": "10.125.31.10",<br>"weight": 100<br>}<br>],<br>"algorithm": "consistent-hashing",<br>"hashOn": "header",<br>"hashOnHeader": "name",<br>"enableCustom": true,<br>"itemsHeader": [<br>[<br>"k1:v1",<br>"vvv:kk"<br>],<br>[<br>"k2:v2"<br>]<br>],<br>"itemsBody": [<br>[<br>"KKK:vvv"<br>],<br>[<br>"jj:vv"<br>]<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 删除接口版本

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/versions/{versionId\} | JSON | DELETE | 删除接口版本 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |
| path参数名 | 必选 | 类型及范围 | 说明 |
| versionId | true | String | 接口版本id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 无实际意义 | 无实际意义 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 修改接口版本

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/versions | JSON | PUT | 修改接口版本 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| id | true | string | api版本id |
| --- | --- | --- | --- |
| apiId | true | string | api id |
| apiVersion | true | string | 版本号 |
| versionDescription | false | string | 版本特性描述 |
| serviceList | true | array | 后端服务列表 |
| serviceList.host | true | string | 后端服务地址，ip地址或域名+端口号，端口默认值：http为80，https为443 |
| serviceList.weight | true | int | 当前地址的权重，取值范围：0-1000，默认值100 |
| algorithm | true | string | 使用哪种负载均衡算法。可接受的值是："consistent-hashing"，"least-connections"，"round-robin"。默认为"round-robin"。 |
| hashOn | optional | string | 用作哈希输入的内容。使用的none结果是没有散列的加权循环方案。可接受的值是："none"，"ip"，"header"，"cookie"。默认为"none" |
| hashOnHeader | optional | string | 将从中获取值的标头名称作为哈希输入。仅当hashOn设置为header时才需要 |
| hashOnCookie | optional | string | 从中获取值的cookie名称作为哈希输入。仅在hashOn设置为cookie时需要。如果请求中没有指定的cookie，则Kong将生成一个值并在响应中设置cookie |
| hashOnCookiePath | optional | string | 在响应头中设置的cookie路径。仅在hashOn设置为cookie时需要。默认为"/" |
| enableCustom | false | boolean | 是否开启自定义规则，默认为false，为true时，itemsHeader和itemsBody字段不能同时为空 |
| itemsHeader | optional | array | 在请求头Header中的一个或多个键值对，如请求头中存在其中某个或多个键值对，则匹配该路由。例，itemsHeader : [ ["k1:v1"], ["k2:v2", "k3:v3"] ] |
| itemsBody | optional | array | 在请求头Body中的一个或多个键值对，如请求体中存在其中某个或多个键值对，则匹配该路由。例，itemsHeader : [["k1:v1"], ["k2:v2", "k3:v3"] ] |
| {<br>"apiId": "a78666077dca40f1b954df1d27a11d40",<br>"apiVersion": "srhtst10000",<br>"versionDescription": "srhtst10000des",<br>"serviceList": [<br>{<br>"host": "123.123.123.123:11233",<br>"weight": 100<br>}<br>],<br>"algorithm": "round-robin",<br>"hashOn": "none",<br>"enableCustom": true,<br>"itemsHeader": [<br>[<br>"hk:hv"<br>]<br>],<br>"itemsBody": [<br>[<br>"bk:bv"<br>]<br>],<br>"id": "90e75d82426249d3b07b32da2227ee90"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 设置为默认版本

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/versions/{versionId}/default | JSON | PUT | 设置为默认版本 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |
| versionId | true | string | api version id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 接口版本批量删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/versions/batch/delete | JSON | POST | 批量绑定访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 版本id数组<br>例如：<br>[<br>"1bac041a-0d90-45f0-9e9c-379d2fe0e900",<br>"8a2c7fdd-28e5-4258-8a67-c4c99df0748b"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"test2",<br>"test1"<br>],<br>"failed": []<br>},<br>"status": true<br>\} |  |  |  |

### 服务网关访问策略

#### 访问策略列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access | JSON | GET | 获取访问策略列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |
| 参数名 | 必选 | 类型及范围 | 说明 |
| page | true | Integer | 分页码，从1开始 |
| size | true | Integer | 分页大小 |
| orderBy | false | String | 排序查询：desc表示降序，asc和不填表示正序，例如：name desc |
| id | false | String | 条件查询：id |
| name | false | String | 模糊查询：name |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回列表 |
| total | Integer | 列表的条数 | 列表的条数 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "9f97cf2b-381a-48b8-866e-69dd312eddf6",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "srhtst",<br>"type": "IP",<br>"action": "FORBID",<br>"ipAddress": "[\"10.125.104.154\"]",<br>"number": 1,<br>"createTime": "2021-10-22T06:39:03.418+0000",<br>"updateTime": "2021-10-22T06:39:03.418+0000",<br>"config": null,<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 访问策略详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access/{id}/info | JSON | POST | 创建访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "9f97cf2b-381a-48b8-866e-69dd312eddf6",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "srhtst",<br>"type": "IP",<br>"action": "FORBID",<br>"ipAddress": "[\"10.125.104.154\"]",<br>"number": 1,<br>"createTime": "2021-10-22T06:39:03.418+0000",<br>"updateTime": "2021-10-22T06:39:03.418+0000",<br>"config": null<br>},<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 创建访问策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access | JSON | POST | 创建访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| name | true | String | 策略名称 |
| --- | --- | --- | --- |
| type | true | String | 策略类型：ip |
| action | ture | String | 动作：允许（ALLOW），禁止（FORBID） |
| ipAddress | true | String | ip地址，为数值类型格式的字符串 |
| {<br>"name": "srhtst2",<br>"type": "IP",<br>"action": "ALLOW",<br>"ipAddress": "[\"123.123.123.123\"]"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 删除访问策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access/{id}/delete | JSON | DELETE | 删除访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |
| path参数名 | 必选 | 类型及范围 | 说明 |
| id | true | String | 访问策略id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 无实际意义 | 无实际意义 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 修改访问策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access | JSON | PUT | 修改访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| id | true | String | 策略id |
| --- | --- | --- | --- |
| name | true | String | 策略名称 |
| type | true | String | 策略类型：ip |
| action | ture | String | 动作：允许（ALLOW），禁止（FORBID） |
| ipAddress | true | String | ip地址，为数值类型格式的字符串 |
| 例如：<br>{<br>"id": "6a10cb3e-c3ed-4ba0-94e2-a7c99e56fa1c",<br>"name": "访问策略66",<br>"type": "ip",<br>"action": "FORBID",<br>"ipAddress": "[\"10.125.104.126\"]"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 绑定访问策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access/{accessId}/binding/{apiId}/api | JSON | POST | 绑定访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 解绑访问策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access/{accessId}/binding/{apiId}/api | JSON | POST | 解绑访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量绑定访问策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access/{policyId}/binding/list | JSON | POST | 批量绑定访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量解绑访问策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/access/{policyId}/unbind/list | JSON | POST | 批量访问策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

### 服务网关流控策略

#### 流控策略列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control | JSON | GET | 获取流控策略列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |
| 参数名 | 必选 | 类型及范围 | 说明 |
| page | true | Integer | 分页码，从1开始 |
| size | true | Integer | 分页大小 |
| orderBy | false | String | 排序查询：desc表示降序，asc和不填表示正序，例如：name desc |
| id | false | String | 条件查询：id |
| name | false | String | 模糊查询：name |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回列表 |
| total | Integer | 列表的条数 | 列表的条数 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "e2a65736-fc18-4612-bb97-cc9442870849",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "srhtst",<br>"description": "",<br>"number": 0,<br>"perSecond": -1,<br>"perMinute": -1,<br>"perHour": -1,<br>"perDay": -1,<br>"createTime": "2021-10-22T05:52:11.823+0000",<br>"type": "CustomConfig",<br>"config": null,<br>"limitBy": "consumer",<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 流控策略详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control/{id}/info | JSON | GET | 获取流控策略详情 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |
| 参数名 | 必选 | 类型及范围 | 说明 |
| id | true | String | 流控策略id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回列表 | 返回列表 |
| total | Integer | 列表的条数 | 列表的条数 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "e2a65736-fc18-4612-bb97-cc9442870849",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "srhtst",<br>"description": "",<br>"number": 0,<br>"perSecond": -1,<br>"perMinute": -1,<br>"perHour": -1,<br>"perDay": -1,<br>"createTime": "2021-10-22T05:52:11.823+0000",<br>"type": "CustomConfig",<br>"config": {<br>"status_code": 321,<br>"body": "myssage",<br>"minute": 3<br>},<br>"limitBy": "consumer"<br>},<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 创建流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control | JSON | POST | 创建流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| name | true | String | 策略名称 |
| --- | --- | --- | --- |
| description | true | String | 策略描述 |
| perSecond | ture | String | 每秒请求数，-1表示不设限 |
| perMinute | ture | String | 每分钟请求数，-1表示不设限 |
| perHour | ture | String | 每小时请求数，-1表示不设限 |
| perDay | ture | String | 每天请求数，-1表示不设限 |
| limitBy | false | String | 限流模式，按接口（service）、按组织（project）、按凭证（consumer）、按源IP（ip） |
| config | false | JSONObject | 高级配置 |
| type | false | String | 类型 |
| {<br>"name": "srhtst2",<br>"description": "srhtst2",<br>"perSecond": "-1",<br>"perMinute": "-1",<br>"perHour": "-1",<br>"perDay": "1",<br>"config": null,<br>"limitBy": "service",<br>"type": "BaseConfig"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 删除流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control/{id}/delete | JSON | DELETE | 删除流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |
| path参数名 | 必选 | 类型及范围 | 说明 |
| id | true | String | 流控策略id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 无实际意义 | 无实际意义 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量删除流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control/batch/delete | JSON | DELETE | 删除流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| ids | true | List | 流控策略Id列表 |
| --- | --- | --- | --- |
| 例如：<br>[“63794dde-fa9d-4dad-b89c-56ce8ce68e1c”, “6fd94dde-fa9d-4dad-b89c-56ce8ce68e1c”] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 无实际意义 | 无实际意义 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 修改流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control | JSON | PUT | 修改流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

表格 1

| id | true | String | 策略id |
| --- | --- | --- | --- |
| name | true | String | 策略名称 |
| description | true | String | 策略描述 |
| perSecond | ture | String | 每秒请求数，-1表示不设限 |
| perMinute | ture | String | 每分钟请求数，-1表示不设限 |
| perHour | ture | String | 每小时请求数，-1表示不设限 |
| perDay | ture | String | 每天请求数，-1表示不设限 |
| limitBy | false | String | 限流模式，按接口（service）、按组织（project）、按凭证（consumer）、按源IP（ip） |
| config | false | JSONObject | 高级配置 |
| type | false | String | 类型 |
| {<br>"id": "d6eeb8e0-e904-429e-ac58-dacec79a3267",<br>"name": "test1",<br>"description": "test1descrip",<br>"perSecond": "1",<br>"perMinute": -1,<br>"perHour": -1,<br>"perDay": 2,<br>"config": null,<br>"limitBy": "service",<br>"type": "BaseConfig"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 绑定流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control/{policyId}/binding/{apiId}/api | JSON | POST | 绑定流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 解绑流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control/{policyId}/binding/{apiId}/api | JSON | POST | 解绑流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量绑定流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control/{policyId}/binding/list | JSON | POST | 创建流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量解绑流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/flow-control/{policyId}/unbind/list | JSON | POST | 批量解绑流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

### 服务网关认证策略

#### 查询策略列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth |  | GET | 查询策略列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| Id | False | String | 策略id |
| name | False | String | 策略名 |
| type | False | String | 策略类型 |
| orderBy | False | String | 排序字段 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "srhtst",<br>"description": "",<br>"number": 1,<br>"type": "jwt",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"group": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"credentialId": "e90cec2f-0281-4e1c-8e83-6c6a62b7cf51",<br>"createTime": "2021-10-22T03:49:21.218+0000",<br>"config": "{\"claims_to_verify\":[\"exp\"],\"maximum_expiration\":1296000}",<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 创建密钥认证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth | JSON | POST | 创建密钥认证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 策略名称 |
| description | False | String | 描述 |
| type | True | String | 策略类型 |
| key | True | String | 密钥 |
| {<br>"name": "test1",<br>"type": "key",<br>"key": "123456",<br>"description": "test1"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "95eda8e2-88a2-4c9b-b1c7-ddb690fc5b80",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "test1",<br>"description": "test1",<br>"number": null,<br>"type": "key",<br>"userName": null,<br>"password": null,<br>"key": "123456",<br>"consumerId": "95eda8e2-88a2-4c9b-b1c7-ddb690fc5b80",<br>"group": "95eda8e2-88a2-4c9b-b1c7-ddb690fc5b80",<br>"credentialId": "db298105-9e30-49f5-acfe-1336f71e5bd8",<br>"createTime": "2021-10-29T01:27:57.279+0000",<br>"config": null<br>},<br>"status": true<br>\} |  |  |  |

#### 更新密钥认证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth | JSON | PUT | 更新策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 策略id |
| name | True | String | 策略名称 |
| description | False | String | 描述 |
| type | True | String | 策略类型 |
| key | True | String | 密钥 |
| {<br>"name": "test1",<br>"type": "key",<br>"key": "654321",<br>"description": "test1",<br>"id": "95eda8e2-88a2-4c9b-b1c7-ddb690fc5b80"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "95eda8e2-88a2-4c9b-b1c7-ddb690fc5b80",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "test1",<br>"description": "test1",<br>"number": null,<br>"type": "key",<br>"userName": null,<br>"password": null,<br>"key": "654321",<br>"consumerId": "95eda8e2-88a2-4c9b-b1c7-ddb690fc5b80",<br>"group": "95eda8e2-88a2-4c9b-b1c7-ddb690fc5b80",<br>"credentialId": "db298105-9e30-49f5-acfe-1336f71e5bd8",<br>"createTime": "2021-10-29T01:27:57.279+0000",<br>"config": null<br>},<br>"status": true<br>\} |  |  |  |

#### 创建基础认证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth | JSON | POST | 创建基础认证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 策略名称 |
| description | False | String | 描述 |
| type | True | String | 策略类型 |
| userName | True | String | 用户名 |
| password | True | String | 密码 |
| {<br>"name": "test2",<br>"type": "basic",<br>"userName": "ttttt",<br>"password": "123456",<br>"description": "test2descrip"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "8dc5ca62-3805-4882-bd40-4895fe0fd0d1",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "test2",<br>"description": "test2descrip",<br>"number": null,<br>"type": "basic",<br>"userName": "ttttt",<br>"password": "0624decae57a7e9b6eecbb7dcc9e8ef74e050717",<br>"key": null,<br>"consumerId": "8dc5ca62-3805-4882-bd40-4895fe0fd0d1",<br>"group": "8dc5ca62-3805-4882-bd40-4895fe0fd0d1",<br>"credentialId": "466c6807-b2ad-41f0-ad2b-3a1798f24556",<br>"createTime": "2021-10-29T01:33:41.863+0000",<br>"config": null<br>},<br>"status": true<br>\} |  |  |  |

#### 更新基础认证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth | JSON | PUT | 更新基础认证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 策略id |
| name | True | String | 策略名称 |
| description | False | String | 描述 |
| type | True | String | 策略类型 |
| userName | True | String | 用户名 |
| password | True | String | 密码 |
| {<br>"name": "test2",<br>"type": "basic",<br>"userName": "tttt",<br>"password": "",<br>"description": "test2descrip",<br>"id": "8dc5ca62-3805-4882-bd40-4895fe0fd0d1"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "8dc5ca62-3805-4882-bd40-4895fe0fd0d1",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "test2",<br>"description": "test2descrip",<br>"number": null,<br>"type": "basic",<br>"userName": "tttt",<br>"password": "0624decae57a7e9b6eecbb7dcc9e8ef74e050717",<br>"key": null,<br>"consumerId": "8dc5ca62-3805-4882-bd40-4895fe0fd0d1",<br>"group": "8dc5ca62-3805-4882-bd40-4895fe0fd0d1",<br>"credentialId": "466c6807-b2ad-41f0-ad2b-3a1798f24556",<br>"createTime": "2021-10-29T01:33:41.863+0000",<br>"config": null<br>},<br>"status": true<br>\} |  |  |  |

#### 创建AK/SK认证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth | JSON | POST | 创建AK/SK认证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 策略名称 |
| description | False | String | 描述 |
| type | True | String | 策略类型 |
| config | True | String | 配置 |
| {<br>"name": "aksktest",<br>"type": "aksk",<br>"config": "{\"enforce_headers\":[\"Date\"],\"algorithms\":[\"hmac-sha1\"],\"validate_request_body\":false}",<br>"description": "aksktestDescrip"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "aksktest",<br>"description": "aksktestDescrip",<br>"number": null,<br>"type": "aksk",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"group": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"credentialId": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"createTime": "2021-10-29T04:33:29.694+0000",<br>"config": "{\"enforce_headers\":[\"Date\"],\"algorithms\":[\"hmac-sha1\"],\"validate_request_body\":false}"<br>},<br>"status": true<br>\} |  |  |  |

#### 更新AK/SK认证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth | JSON | PUT | 更新AK/SK认证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 策略id |
| name | True | String | 策略名称 |
| description | False | String | 描述 |
| type | True | String | 策略类型 |
| config | True | String | 配置 |
| {<br>"name": "aksktest2",<br>"type": "aksk",<br>"config": "{\"enforce_headers\":[\"Date\"],\"algorithms\":[\"hmac-sha1\"],\"validate_request_body\":false}",<br>"description": "aksktestDescrip",<br>"id": "4bffbda2-1f2b-4037-b490-f6250e205e8c"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "aksktest2",<br>"description": "aksktestDescrip",<br>"number": null,<br>"type": "aksk",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"group": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"credentialId": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"createTime": "2021-10-29T04:33:29.694+0000",<br>"config": "{\"enforce_headers\":[\"Date\"],\"algorithms\":[\"hmac-sha1\"],\"validate_request_body\":false}"<br>},<br>"status": true<br>\} |  |  |  |

#### 创建JWT认证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth | JSON | POST | 创建JWT认证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 策略名称 |
| description | False | String | 描述 |
| type | True | String | 策略类型 |
| config | True | String | 配置 |
| {<br>"name": "jwttest",<br>"type": "jwt",<br>"config": "{\"claims_to_verify\":[\"exp\"],\"maximum_expiration\":1296000}",<br>"description": "jwttestDescrip"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "1e4ac1d3-74f7-4336-9c26-20da3346108f",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "jwttest",<br>"description": "jwttestDescrip",<br>"number": null,<br>"type": "jwt",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": "1e4ac1d3-74f7-4336-9c26-20da3346108f",<br>"group": "1e4ac1d3-74f7-4336-9c26-20da3346108f",<br>"credentialId": "1e4ac1d3-74f7-4336-9c26-20da3346108f",<br>"createTime": "2021-10-29T04:37:58.336+0000",<br>"config": "{\"claims_to_verify\":[\"exp\"],\"maximum_expiration\":1296000}"<br>},<br>"status": true<br>\} |  |  |  |

#### 更新JWT认证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth | JSON | PUT | 更新JWT认证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 策略id |
| name | True | String | 策略名称 |
| description | False | String | 描述 |
| type | True | String | 策略类型 |
| config | True | String | 配置 |
| {<br>"name": "jwttest2",<br>"type": "jwt",<br>"config": "{\"claims_to_verify\":[\"exp\"],\"maximum_expiration\":1296000}",<br>"description": "jwttestDescrip",<br>"id": "1e4ac1d3-74f7-4336-9c26-20da3346108f"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "1e4ac1d3-74f7-4336-9c26-20da3346108f",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "jwttest2",<br>"description": "jwttestDescrip",<br>"number": null,<br>"type": "jwt",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": "1e4ac1d3-74f7-4336-9c26-20da3346108f",<br>"group": "1e4ac1d3-74f7-4336-9c26-20da3346108f",<br>"credentialId": "1e4ac1d3-74f7-4336-9c26-20da3346108f",<br>"createTime": "2021-10-29T04:37:58.336+0000",<br>"config": "{\"claims_to_verify\":[\"exp\"],\"maximum_expiration\":1296000}"<br>},<br>"status": true<br>\} |  |  |  |

#### 删除认证策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth/{id}/delete |  | DELETE | 删除认证策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 策略id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回创建可用域成功的结果信息 | 返回创建可用域成功的结果信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 认证策略绑定API

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth/{policyId}/binding/{apiId}/api |  | POST | 认证策略绑定API |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| policyId | True | String | 策略id |
| apiId | True | String | API id |
| 例如：<br>/cloud/svcgw/v1.0/apimgt/policy/auth/80e7f845-5095-4fed-b300-098ee4860f3f/binding/d13a1675a5d64659be7fadfddd1841fd/api |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回删除可用域的结果信息 | 返回删除可用域的结果信息 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null<br>\} |  |  |  |

#### 认证策略解绑API

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth/{policyId}/unbind/{apiId}/api | JSON | POST | 认证策略解绑API |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| policyId | True | String | 策略id |
| apiId | True | String | API id |
| 例如：<br>/cloud/svcgw/v1.0/apimgt/policy/auth/80e7f845-5095-4fed-b300-098ee4860f3f/unbind/d13a1675a5d64659be7fadfddd1841fd/api |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回修改可用域的结果信息 | 返回修改可用域的结果信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量绑定认证策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth/{policyId}/binding/list | JSON | POST | 批量绑定认证策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量解绑认证策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/auth/{policyId}/unbind/list | JSON | POST | 批量解绑认证策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

### 服务网关代理缓存策略

#### 查询策略列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/proxy-cache |  | GET | 查询策略列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| Id | False | String | 策略id |
| name | False | String | 策略名 |
| orderBy | false | String | 排序字段 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "e3511e38-9edb-4c36-a5bf-161f7c065d88",<br>"name": "a",<br>"description": "",<br>"cacheTTL": 300,<br>"responseCode": [<br>200,<br>301,<br>401<br>],<br>"contentType": [],<br>"varyHeaders": [],<br>"varyQueryParams": [],<br>"createTime": "2021-10-23T08:04:14.181+0000",<br>"updateTime": "2021-10-23T08:04:14.181+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"number": 0,<br>"requestMethod": null,<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 创建代理缓存策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/proxy-cache | JSON | POST | 创建代理缓存 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | string | 策略名称 |
| description | false | string | 描述 |
| cacheTTL | true | int | 缓存过期时间，单位秒，默认值300 |
| responseCode | true | array | 匹配返回码，默认值：200,301,404 |
| contentType | false | array | 被认可的返回Content-Type参数，如不填，则不校验该参数； |
| varyHeaders | false | array | 缓存需要校验的请求Header参数，如不填，则不校验任何Header参数 |
| varyQueryParams | false | array | 缓存需要校验的查询参数，如不填，则所有参数都进行校验； |
| {<br>"name": "test1",<br>"description": "descrip",<br>"cacheTTL": 300,<br>"responseCode": [<br>200,<br>301,<br>401<br>],<br>"contentType": [<br>"testC"<br>],<br>"varyHeaders": [<br>"testH"<br>],<br>"varyQueryParams": [<br>"testP"<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": "",<br>"status": true<br>\} |  |  |  |

#### 修改代理缓存策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/proxy-cache | JSON | PUT | 修改代理缓存策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| id | true | string | 策略id |
| --- | --- | --- | --- |
| name | true | string | 策略名称 |
| description | false | string | 描述 |
| cacheTTL | true | int | 缓存过期时间，单位秒，默认值300 |
| responseCode | true | array | 匹配返回码，默认值：200,301,404 |
| contentType | false | array | 被认可的返回Content-Type参数，如不填，则不校验该参数； |
| varyHeaders | false | array | 缓存需要校验的请求Header参数，如不填，则不校验任何Header参数 |
| varyQueryParams | false | arry | 缓存需要校验的查询参数，如不填，则所有参数都进行校验； |
| createTime | false | Date | 创建时间 |
| updateTime | false | Date | 更新时间 |
| userId | false | String | 用户id |
| projectId | false | String | 组织id |
| number | false | Integer | 绑定接口数 |
| requestMethod | False | String | 请求方式 |
| projectName | false | String | 组织名称 |
| {<br>"id": "9979f0ce-4b67-453a-b500-d9180f1e4dc5",<br>"name": "test1",<br>"description": "descrip",<br>"cacheTTL": 300,<br>"responseCode": [<br>200,<br>301<br>],<br>"contentType": [<br>"testC"<br>],<br>"varyHeaders": [<br>"testH"<br>],<br>"varyQueryParams": [<br>"testP"<br>],<br>"createTime": "2021-10-29T01:57:00.790+0000",<br>"updateTime": "2021-10-29T01:57:00.790+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"number": 0,<br>"requestMethod": null,<br>"projectName": "root",<br>"index": 0<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量绑定流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/proxy-cache/{policyId}/binding/api/list | JSON | POST | 批量绑定策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"a78666077dca40f1b954df1d27a11d40"<br>],<br>"failed": []<br>},<br>"status": true<br>\} |  |  |  |

#### 批量解绑流控策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/proxy-cache/{policyId}/unbind/api/list | JSON | POST | 批量解绑流控策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"a78666077dca40f1b954df1d27a11d40"<br>],<br>"failed": []<br>},<br>"status": true<br>\} |  |  |  |

#### 批量删除代理缓存策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/proxy-cache/batch/delete | JSON | POST | 批量删除策略 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| ids | true | List | 流控策略Id列表 |
| --- | --- | --- | --- |
| 例如：<br>[“63794dde-fa9d-4dad-b89c-56ce8ce68e1c”, “6fd94dde-fa9d-4dad-b89c-56ce8ce68e1c”] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 无实际意义 | 无实际意义 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"9979f0ce-4b67-453a-b500-d9180f1e4dc5"<br>],<br>"failed": []<br>},<br>"status": true<br>\} |  |  |  |

### 服务网关接口套餐管理

#### 查询套餐列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/api-package |  | GET | 查询套餐列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| Id | False | String | 策略id |
| name | False | String | 策略名 |
| orderBy | false | String | 排序字段 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "bfe8710d-a758-40c1-98ef-6b0b80d4a6ce",<br>"name": "test1",<br>"description": "test1",<br>"createTime": "2021-10-29T02:24:53.026+0000",<br>"updateTime": "2021-10-29T02:24:53.026+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"number": 0<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 创建接口套餐

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/api-package | JSON | POST | 创建接口套餐 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | string | 策略名称 |
| description | false | string | 描述 |
| {<br>"name": "test1",<br>"description": "test1"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 修改接口套餐

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/api-package | JSON | PUT | 修改接口套餐 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| id | true | string | 策略id |
| --- | --- | --- | --- |
| name | true | string | 策略名称 |
| description | false | string | 描述 |
| createTime | false | Date | 创建时间 |
| updateTime | false | Date | 更新时间 |
| userId | false | String | 用户id |
| projectId | false | String | 组织id |
| number | false | Integer | 绑定接口数 |
| {<br>"id": "bfe8710d-a758-40c1-98ef-6b0b80d4a6ce",<br>"name": "test2",<br>"description": "test1",<br>"createTime": "2021-10-29T02:24:53.026+0000",<br>"updateTime": "2021-10-29T02:24:53.026+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"number": 0<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量添加接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/api-package/{packageId}add/api/batch | JSON | POST | 批量添加接口 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体(form-data)

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"79aad982fae44c33a99318b2e14f33aa"<br>],<br>"failed": []<br>},<br>"status": true<br>\} |  |  |  |

#### 批量删除套餐接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/api-package/{packageId}/remove/api/batch | JSON | POST | 批量删除接口 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体(form-data)

| 接口id数组<br>例如：<br>[<br>"aaac0a7325b04890b61ed5aa6b757ba9",<br>"f806e145b8d84441a2d807330cb764be"<br>] |
| --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回信息 | 返回信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"0923d34afbd046daa9deac9318da5e40",<br>"0b244e50feca4be9b634d693a573508d"<br>],<br>"failed": []<br>},<br>"status": true<br>\} |  |  |  |

#### 批量删除接口套餐

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/policy/proxy-cache/batch/delete | JSON | POST | 批量删除套餐 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | true | String | 网关实例服务地址 |

###### 请求体

| ids | true | List | 套餐Id列表 |
| --- | --- | --- | --- |
| 例如：<br>[“63794dde-fa9d-4dad-b89c-56ce8ce68e1c”, “6fd94dde-fa9d-4dad-b89c-56ce8ce68e1c”] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 无实际意义 | 无实际意义 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"993f4ca1-6d01-48e5-b30f-aa95c19c77df"<br>],<br>"failed": []<br>},<br>"status": true<br>\} |  |  |  |

### 服务网关订阅管理

#### 订阅服务列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/subscribe | JSON | GET | 订阅服务列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| Id | False | String | 订阅id |
| queryName | False | String | api名称 |
| certId | True | String | 凭证id |
| groupId | False | String | api组id |
| projectId | False | String | 组织id |
| orderBy | False | String | 排序字段，以desc或asc结尾 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "79aad982fae44c33a99318b2e14f33aa",<br>"name": "srhtst2",<br>"versionList": [<br>{<br>"apiId": "79aad982fae44c33a99318b2e14f33aa",<br>"enableDefault": true,<br>"apiVersion": "v1",<br>"versionDescription": "",<br>"enableCustom": false,<br>"itemsHeader": null,<br>"itemsBody": null<br>}<br>],<br>"type": "REST",<br>"status": "1",<br>"description": "",<br>"authPolicy": null,<br>"auth": {<br>"id": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"projectId": null,<br>"name": "aksktest2",<br>"description": "aksktestDescrip",<br>"number": null,<br>"type": "aksk",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": null,<br>"group": null,<br>"credentialId": null,<br>"createTime": "2021-10-29T04:33:29.694+0000",<br>"config": "{\"enforce_headers\":[\"Date\"],\"algorithms\":[\"hmac-sha1\"],\"validate_request_body\":false}"<br>},<br>"accessPolicyVO": null,<br>"flowControlPolicyVO": null,<br>"protocol": "http",<br>"host": "10.125.29.82",<br>"port": 48000,<br>"path": "/srhtst2",<br>"method": "GET",<br>"kongUrl": [<br>"http://10.125.29.82:48000/srhtst2"<br>],<br>"regex": 0,<br>"paramModel": "mapping",<br>"groupId": "ef40d9ee-58c9-47f9-802b-b685547f3bc2",<br>"groupName": "srhtst",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"projectName": "root",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"userName": null<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 订阅服务列表接口可选参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/subscribe/params | JSON | GET | 订阅服务列表接口可选参数 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"projectName": "root",<br>"apiGroups": [<br>{<br>"id": "ef40d9ee-58c9-47f9-802b-b685547f3bc2",<br>"number": null,<br>"name": "srhtst",<br>"projectId": null,<br>"description": null,<br>"createTime": null,<br>"updateTime": null<br>}<br>]<br>}<br>],<br>"status": true<br>\} |  |  |  |

#### 用户已订阅服务列表接口可选参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/user/subscribed/params | JSON | GET | 用户已订阅服务列表接口可选参数 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"projectName": "root",<br>"apiGroups": [<br>{<br>"id": "ef40d9ee-58c9-47f9-802b-b685547f3bc2",<br>"number": null,<br>"name": "srhtst",<br>"projectId": null,<br>"description": null,<br>"createTime": null,<br>"updateTime": null<br>}<br>]<br>}<br>],<br>"status": true<br>\} |  |  |  |

#### 用户已订阅服务列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/user/subscribed | JSON | GET | 用户已订阅服务列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| Id | False | String | 订阅id |
| queryName | False | String | api名称 |
| certId | True | String | 凭证id |
| groupId | False | String | api组id |
| projectId | False | String | 组织id |
| orderBy | False | String | 排序字段，以desc或asc结尾 |
| svcStatus | False | String | api状态 |
| subStatus | False | String | 订阅状态 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "5f4b50cc-de17-4a70-ae31-ef5a59431eed",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"certId": "3c542aaf-ed54-457d-9b62-98163d872b1d",<br>"certName": "srhtst1",<br>"apiId": "79aad982fae44c33a99318b2e14f33aa",<br>"apiName": "srhtst2",<br>"apiStatus": "1",<br>"apiGroupId": "ef40d9ee-58c9-47f9-802b-b685547f3bc2",<br>"apiGroupName": "srhtst",<br>"auth": {<br>"id": "4bffbda2-1f2b-4037-b490-f6250e205e8c",<br>"projectId": null,<br>"name": "aksktest2",<br>"description": "aksktestDescrip",<br>"number": null,<br>"type": "aksk",<br>"userName": null,<br>"password": null,<br>"key": null,<br>"consumerId": null,<br>"group": null,<br>"credentialId": null,<br>"createTime": "2021-10-29T04:33:29.694+0000",<br>"config": "{\"enforce_headers\":[\"Date\"],\"algorithms\":[\"hmac-sha1\"],\"validate_request_body\":false}"<br>},<br>"vo": {<br>"id": "79aad982fae44c33a99318b2e14f33aa",<br>"name": "srhtst2",<br>"versionList": [<br>{<br>"apiId": "79aad982fae44c33a99318b2e14f33aa",<br>"enableDefault": true,<br>"apiVersion": "v1",<br>"versionDescription": "",<br>"enableCustom": false,<br>"itemsHeader": null,<br>"itemsBody": null<br>}<br>],<br>"type": "REST",<br>"status": "1",<br>"description": "",<br>"authPolicy": null,<br>"auth": null,<br>"accessPolicyVO": null,<br>"flowControlPolicyVO": null,<br>"protocol": "http",<br>"host": "10.125.29.82",<br>"port": 48000,<br>"path": "/srhtst2",<br>"method": "GET",<br>"kongUrl": [<br>"http://10.125.29.82:48000/srhtst2"<br>],<br>"regex": 0,<br>"paramModel": "mapping",<br>"groupId": "ef40d9ee-58c9-47f9-802b-b685547f3bc2",<br>"groupName": "srhtst",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"projectName": "root",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"userName": null<br>},<br>"status": "TO_BE_AUDITED",<br>"auditOpinion": null,<br>"createTime": "2021-10-29T05:01:45.235+0000",<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 订阅api服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/subscribe | JSON | POST | 订阅api服务 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiId | True | String | api id |
| certId | True | String | 凭证id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 冻结/解冻订阅

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/subscribe/{id}/status | JSON | POST | 冻结/解冻订阅 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | False | String | 订阅id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 退订api服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/{id}/unsubscribe | JSON | POST | 退订api服务 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | False | String | 订阅id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量订阅api服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/subscribe/batch | JSON | POST | 批量订阅api服务 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| certId | True | String | 凭证id |
| apiIds | True | Array | api id列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 批量退订api服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/unsubscribe/batch | JSON | GET | 批量退订api服务 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Ids | True | Array | 订阅id列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

### 服务网关凭证管理

#### 查询凭证列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/certificates | JSON | GET | 查询凭证列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| Id | False | String | 凭证id |
| queryName | False | String | 凭证名称 |
| orderBy | False | String | 排序字段，以desc或asc结尾，示例：name desc（表示根据name字段降序排序） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "c03e3705-3c30-41f8-88ce-f7e90c8056fc",<br>"subId": null,<br>"name": "srhtst1",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"status": "t",<br>"key": {<br>"accessKey": "c3beb207-e3e8-4aa2-bb76-3faf193055dc",<br>"secretKey": "55304630-ad4d-4d99-bd1c-a2cb2cd8258a"<br>},<br>"keyNew": null,<br>"description": "srhtst1des",<br>"createTime": "2021-10-29T04:46:12.212+0000",<br>"updateTime": "2021-10-29T04:46:12.212+0000",<br>"number": 0,<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 查询凭证详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/certificates/{id\} | JSON | GET | 查询凭证详情 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | False | String | 凭证id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "4478e507-afff-48e9-a13d-c876b248c38c",<br>"subId": null,<br>"name": "test",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"status": "t",<br>"key": {<br>"accessKey": "3668e03f-f3a8-4734-8fe8-18c907317cc3",<br>"secretKey": "a0f47249-6312-43f8-8ef2-54e2dfc5146f"<br>},<br>"keyNew": null,<br>"description": "asdfasdfa",<br>"createTime": "2022-03-01T07:54:33.575+0000",<br>"updateTime": "2022-03-07T02:44:30.561+0000",<br>"number": 2,<br>"enableCertificateAccess": true,<br>"ipInfo": {<br>"certificateAccessType": "WHITELIST",<br>"certificateAccessList": [{<br>"ipAddress": "10.125.104.155",<br>"describe": ""<br>}]<br>}<br>},<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 查询api已订阅凭证列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/apis/{apiId}/certificates | JSON | GET | 查询api已订阅凭证列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| Id | False | String | 凭证id |
| queryName | False | String | 凭证名称 |
| orderBy | False | String | 排序字段，以desc或asc结尾，示例：name desc（表示根据name字段降序排序） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "3c542aaf-ed54-457d-9b62-98163d872b1d",<br>"subId": "5f4b50cc-de17-4a70-ae31-ef5a59431eed",<br>"name": "srhtst1",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"status": "ACTIVATION",<br>"key": null,<br>"keyNew": null,<br>"description": "srhtst1desc",<br>"createTime": "2021-10-29T04:55:30.075+0000",<br>"updateTime": "2021-10-29T04:55:30.075+0000",<br>"number": null,<br>"projectName": "root"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 创建凭证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/certificates | JSON | POST | 创建凭证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 凭证名称 |
| description | False | String | 描述 |
| enableCertificateAccess | True | Boolean | 是否开启凭证访问 |
| {<br>"name": "test2",<br>"description": "",<br>"enableCertificateAccess": true,<br>"ipInfo": {<br>"certificateAccessType": "BLACKLIST",<br>"certificateAccessList": [{<br>"ipAddress": "10.125.31.81",<br>"describe": "restore"<br>}, {<br>"ipAddress": "10.125.104.172",<br>"describe": "ok"<br>}]<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 编辑凭证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/certificates | JSON | PUT | 编辑凭证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 凭证名称 |
| description | False | String | 描述 |
| Id | true | String | 凭证ID |
| {<br>"id": "c03e3705-3c30-41f8-88ce-f7e90c8056fc",<br>"name": "srhtst2",<br>"description": "srhtst1des"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 控制凭证访问

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/certificates/{id}/access | JSON | PUT | 控制凭证访问权限 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| enableCertificateAccess | True | Boolean | 是否开启凭证访问 |
| ipInfo | False | String | 凭证访问详情 |
| {<br>"enableCertificateAccess": true,<br>"ipInfo": {<br>"certificateAccessType": "BLACKLIST",<br>"certificateAccessList": [{<br>"ipAddress": "10.125.104.155",<br>"describe": ""<br>}]<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 更新凭证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/certificates/{id}/update | JSON | POST | 更新凭证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | False | String | 凭证id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 取代凭证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/certificates/{id}/replace | JSON | POST | 取代凭证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | False | String | 凭证id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 删除凭证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/certificates/{id}/delete | JSON | DELETE | 删除凭证 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| gatewayIp | True | String | 服务网关实例地址 |

| path参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | False | String | 凭证id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回查询列表成功的结果信息 | 查询列表成功时返回 |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

### 服务网关后端服务管理

#### 查询后端服务列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/​native-services | JSON | GET | 查询后端服务列表 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| queryName | False | String | 搜索内容 |
| tags | False | array[String] | 标签 |
| orderBy | False | String | 排序 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [<br>{<br>"id": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"name": "docker",<br>"tags": [<br>"docker",<br>"微服务"<br>],<br>"url": "http://10.125.31.186",<br>"port": 2375,<br>"apiNums": 96,<br>"description": "",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createTime": "2021-01-29T08:37:47.677+0000",<br>"updateTime": "2021-02-02T02:08:12.553+0000"<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 新建后端服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt​/native-services | JSON | POST | 新建后端服务 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 名称 |
| tags | False | List<String> | 标签 |
| url | True | String | 地址 |
| port | True | Integer | 端口 |
| description | False | String | 描述 |
| 例如：<br>{<br>"name": "k8s",<br>"port": "8443",<br>"description": "",<br>"tags": [<br>"k8s"<br>],<br>"url": "http://10.125.31.186"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "01cd2e35-9b94-4f49-b158-d0cdd58073bd",<br>"name": "k8s",<br>"tags": [<br>"k8s"<br>],<br>"url": "http://10.125.31.186",<br>"port": 8443,<br>"apiNums": 0,<br>"description": "",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createTime": "2021-02-02T02:17:12.730+0000",<br>"updateTime": "2021-02-02T02:17:12.730+0000"<br>},<br>"status": true<br>\} |  |  |  |

#### 查询后端服务详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| ​/cloud/svcgw/v1.0/apimgt/native-services​/{nativeSvcId\} | JSON | GET | 查询后端服务详情 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| nativeSvcId | True | String | 后端服务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"name": "docker",<br>"tags": [<br>"docker",<br>"微服务"<br>],<br>"url": "http://10.125.31.186",<br>"port": 2375,<br>"apiNums": 96,<br>"description": "",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createTime": "2021-01-29T08:37:47.677+0000",<br>"updateTime": "2021-02-02T02:08:12.553+0000"<br>},<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 修改后端服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| ​/cloud/svcgw/v1.0/apimgt/native-services​/{nativeSvcId\} | JSON | PUT | 修改后端服务 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| nativeSvcId | True | String | 后端服务id |
| name | True | String | 名称 |
| tags | False | List<String> | 标签 |
| url | True | String | 地址 |
| port | True | Integer | 端口 |
| description | False | String | 描述 |
| 例如：<br>{<br>"name": "k8s",<br>"port": 8443,<br>"description": "k8s api",<br>"tags": [<br>"k8s"<br>],<br>"url": "http://10.125.31.186"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "01cd2e35-9b94-4f49-b158-d0cdd58073bd",<br>"name": "k8s",<br>"tags": [<br>"k8s"<br>],<br>"url": "http://10.125.31.186",<br>"port": 8443,<br>"apiNums": 0,<br>"description": "k8s api",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createTime": "2021-02-02T02:17:12.730+0000",<br>"updateTime": "2021-02-02T02:54:56.875+0000"<br>},<br>"status": true<br>\} |  |  |  |

#### 删除后端服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| ​/cloud/svcgw/v1.0/apimgt/native-services​/{nativeSvcId\} | JSON | DELETE | 删除后端服务 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| nativeSvcId | True | String | 后端服务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 查询后端服务的标签列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/native-services-tags | JSON | GET | 查询后端服务的标签列表 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| 例如：<br>{<br>"msg": "",<br>"total": 2,<br>"code": "",<br>"data": [<br>"docker",<br>"微服务"<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 查询后端服务API列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt​/native-services​/{nativeSvcId}​/apis | JSON | GET | 查询后端服务API列表 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| nativeSvcId | True | String | 后端服务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| 例如：<br>{<br>"msg": "",<br>"total": 96,<br>"code": "",<br>"data": [<br>{<br>"id": "a335dba6-1ab1-4447-bacc-71bc04494ed5",<br>"nativeSvcId": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"path": "/v1.26/containers/json",<br>"method": "GET",<br>"summary": "List containers",<br>"tags": [<br>"Container"<br>],<br>"description": null,<br>"operationId": "ContainerList",<br>"apiTestStatus": "untested",<br>"apiTestLastTime": null,<br>"createTime": "2021-01-29T08:38:02.106+0000",<br>"updateTime": "2021-01-29T08:38:02.106+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"parameters": null,<br>"requestBody": null,<br>"requestExample": null,<br>"responses": null<br>},<br>{<br>"id": "a27ddada-17f8-42fc-b698-0b60a09346b2",<br>"nativeSvcId": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"path": "/v1.26/containers/create",<br>"method": "POST",<br>"summary": "Create a container",<br>"tags": [<br>"Container"<br>],<br>"description": null,<br>"operationId": "ContainerCreate",<br>"apiTestStatus": "untested",<br>"apiTestLastTime": null,<br>"createTime": "2021-01-29T08:38:02.106+0000",<br>"updateTime": "2021-01-29T08:38:02.106+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"parameters": null,<br>"requestBody": null,<br>"requestExample": null,<br>"responses": null<br>}<br>],<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 新建后端服务API

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt​/native-services​/{nativeSvcId}​/apis | JSON | POST | 新建后端服务API |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| nativeSvcId | True | String | 后端服务id |
| path | True | String | 路径 |
| method | True | String | 方法 |
| operationId | True | String | 操作标识符 |
| description | False | String | 描述 |
| parameters | False | Map | 参数 |
| requestExample | False | Map | 请求JSON实例 |
| responses | False | Map | 返回体 |
| 例如<br>{<br>"path": "/v1/testapi/{id}",<br>"method": "GET",<br>"operationId": "testapi",<br>"description": "",<br>"parameters": {<br>"id": {<br>"in": "path",<br>"required": true,<br>"description": "",<br>"schema": {<br>"type": "string",<br>"title": "id"<br>}<br>}<br>},<br>"requestExample": null,<br>"responses": {<br>"200": {<br>"description": "ok",<br>"responseExample": null<br>}<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "71e3f14b-7fdf-434d-9b8b-b8b26eb476a2",<br>"nativeSvcId": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"path": "/v1/testapi/{id}",<br>"method": "GET",<br>"summary": null,<br>"tags": [],<br>"description": "",<br>"operationId": "testapi",<br>"apiTestStatus": "untested",<br>"apiTestLastTime": null,<br>"createTime": "2021-02-02T03:22:56.648+0000",<br>"updateTime": "2021-02-02T03:22:56.648+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"parameters": {<br>"id": {<br>"schema": {<br>"type": "string",<br>"title": "id"<br>},<br>"in": "path",<br>"description": "",<br>"required": true<br>}<br>},<br>"requestBody": null,<br>"requestExample": null,<br>"responses": {<br>"200": {<br>"description": "ok"<br>}<br>}<br>},<br>"status": true<br>\} |  |  |  |

#### 查询后端服务API详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/native-services​/{nativeSvcId}​/apis​/{apiId\} | JSON | GET | 查询后端服务API详情 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| nativeSvcId | True | String | 后端服务id |
| apiId | True | String | 后端服务API的id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "31ca3efb-0e97-47db-94d2-40535e80ee53",<br>"nativeSvcId": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"path": "/v1.26/containers/{id}/stats",<br>"method": "GET",<br>"summary": "Get container stats based on resource usage",<br>"tags": [<br>"Container"<br>],<br>"description": "This endpoint returns a live stream of a container’s resource usage statistics.\n\nThe `precpu_stats` is the CPU statistic of last read, which is used for calculating the CPU usage percentage. It is not the same as the `cpu_stats` field.\n",<br>"operationId": "ContainerStats",<br>"apiTestStatus": "untested",<br>"apiTestLastTime": null,<br>"createTime": "2021-01-29T08:38:02.106+0000",<br>"updateTime": "2021-01-29T08:38:02.106+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"parameters": {<br>"stream": {<br>"schema": {<br>"type": "boolean"<br>},<br>"in": "query",<br>"description": "Stream the output. If false, the stats will be output once and then it will disconnect."<br>},<br>"id": {<br>"schema": {<br>"type": "string"<br>},<br>"in": "path",<br>"description": "ID or name of the container",<br>"required": true<br>}<br>},<br>"requestBody": null,<br>"requestExample": null,<br>"responses": {<br>"200": {<br>"responseBody": {<br>"type": "object"<br>},<br>"description": "no error",<br>"responseExample": "null"<br>},<br>"404": {<br>"responseBody": {<br>"type": "object",<br>"required": [<br>"message"<br>],<br>"properties": {<br>"message": {<br>"type": "string"<br>}<br>}<br>},<br>"description": "no such container",<br>"responseExample": {}<br>},<br>"500": {<br>"responseBody": {<br>"type": "object",<br>"required": [<br>"message"<br>],<br>"properties": {<br>"message": {<br>"type": "string"<br>}<br>}<br>},<br>"description": "server error",<br>"responseExample": {}<br>}<br>}<br>},<br>"resource": null,<br>"status": true<br>\} |  |  |  |

#### 修改后端服务API

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/native-services​/{nativeSvcId}​/apis​/{apiId\} | JSON | PUT | 修改后端服务API |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| nativeSvcId | True | String | 后端服务id |
| apiId | True | String | 后端服务API的id |
| path | True | String | 路径 |
| method | True | String | 方法 |
| operationId | True | String | 操作标识符 |
| description | False | String | 描述 |
| parameters | False | Map | 参数 |
| requestExample | False | Map | 请求JSON实例 |
| responses | False | Map | 返回体 |
| 例如<br>{<br>"path": "/v1.26/containers/{id}/stats",<br>"method": "GET",<br>"operationId": "ContainerStats",<br>"description": "This endpoint returns a live stream of a container’s resource usage statistics.\n\nThe `precpu_stats` is the CPU statistic of last read, which is used for calculating the CPU usage percentage. It is not the same as the `cpu_stats` field.\n",<br>"parameters": {<br>"stream": {<br>"in": "query",<br>"description": "Stream the output. If false, the stats will be output once and then it will disconnect.",<br>"schema": {<br>"type": "boolean",<br>"title": "stream"<br>}<br>},<br>"id": {<br>"in": "path",<br>"required": true,<br>"description": "ID or name of the container",<br>"schema": {<br>"type": "string",<br>"title": "id"<br>}<br>}<br>},<br>"requestExample": null,<br>"responses": {<br>"200": {<br>"description": "no error",<br>"responseExample": "null"<br>},<br>"404": {<br>"description": "no such container",<br>"responseExample": {}<br>},<br>"500": {<br>"description": "server error",<br>"responseExample": {}<br>}<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"id": "31ca3efb-0e97-47db-94d2-40535e80ee53",<br>"nativeSvcId": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"path": "/v1.26/containers/{id}/stats",<br>"method": "GET",<br>"summary": "Get container stats based on resource usage",<br>"tags": [<br>"Container"<br>],<br>"description": "This endpoint returns a live stream of a container’s resource usage statistics.\n\nThe `precpu_stats` is the CPU statistic of last read, which is used for calculating the CPU usage percentage. It is not the same as the `cpu_stats` field.\n",<br>"operationId": "ContainerStats",<br>"apiTestStatus": "untested",<br>"apiTestLastTime": null,<br>"createTime": "2021-01-29T08:38:02.106+0000",<br>"updateTime": "2021-02-02T03:06:34.676+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"parameters": {<br>"stream": {<br>"schema": {<br>"type": "boolean",<br>"title": "stream"<br>},<br>"in": "query",<br>"description": "Stream the output. If false, the stats will be output once and then it will disconnect."<br>},<br>"id": {<br>"schema": {<br>"type": "string",<br>"title": "id"<br>},<br>"in": "path",<br>"description": "ID or name of the container",<br>"required": true<br>}<br>},<br>"requestBody": null,<br>"requestExample": null,<br>"responses": {<br>"200": {<br>"responseBody": {<br>"type": "string"<br>},<br>"description": "no error",<br>"responseExample": "null"<br>},<br>"404": {<br>"responseBody": {<br>"type": "object"<br>},<br>"description": "no such container",<br>"responseExample": {}<br>},<br>"500": {<br>"responseBody": {<br>"type": "object"<br>},<br>"description": "server error",<br>"responseExample": {}<br>}<br>}<br>},<br>"status": true<br>\} |  |  |  |

#### 删除后端服务API

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/native-services​/{nativeSvcId}​/apis​/{apiId\} | JSON | DELETE | 删除后端服务API |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| nativeSvcId | True | String | 后端服务id |
| apiId | True | String | 后端服务API的id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 导入后端服务API

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/native-services​/{nativeSvcId}​/swagger | formData | POST | 导入后端服务API |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| nativeSvcId | True | String | 后端服务id |
| file | False | File | Swagger文档文件 |
| url | False | String | Swagger文档url |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"total": 96,<br>"code": "",<br>"data": [<br>{<br>"id": "b9050a3c-f5a5-4f7e-8849-e773abb0ee5b",<br>"nativeSvcId": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"path": "/v1.26/containers/{id}/export",<br>"method": "GET",<br>"summary": "Export a container",<br>"tags": [<br>"Container"<br>],<br>"description": "Export the contents of a container as a tarball.",<br>"operationId": "ContainerExport",<br>"apiTestStatus": "untested",<br>"apiTestLastTime": null,<br>"createTime": "2021-02-02T03:29:12.015+0000",<br>"updateTime": "2021-02-02T03:29:12.015+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"parameters": null,<br>"requestBody": null,<br>"requestExample": null,<br>"responses": null<br>},<br>{<br>"id": "423ac005-5872-489c-ae3c-d4d7e7bd61be",<br>"nativeSvcId": "7d0870d7-ba4c-4abc-9418-34affe43aa1e",<br>"path": "/v1.26/swarm/leave",<br>"method": "POST",<br>"summary": "Leave a swarm",<br>"tags": [<br>"Swarm"<br>],<br>"description": null,<br>"operationId": "SwarmLeave",<br>"apiTestStatus": "untested",<br>"apiTestLastTime": null,<br>"createTime": "2021-02-02T03:29:12.015+0000",<br>"updateTime": "2021-02-02T03:29:12.015+0000",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"parameters": null,<br>"requestBody": null,<br>"requestExample": null,<br>"responses": null<br>}<br>],<br>"status": true<br>\} |  |  |  |

#### 后端服务API创建至网关

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt/native-services​/{nativeSvcId}​/publish | JSON | GET | 后端服务API创建至网关 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| apiGroupId | True | String | 接口分组id |
| pathPrefix | False | String | 接口前缀路径(以/开头) |
| protocol | True | String | 协议 |
| connectTimeout | True | Integer | 重试时间（ms） |
| retries | True | Integer | 重试次数 |
| apis | True | List<String> | 要发布的api id列表 |
| 例如：<br>{<br>"apiGroupId": "df7942d0-07e3-4455-93b2-197cb3b72525",<br>"pathPrefix": "/prefix-0202",<br>"protocol": "http",<br>"connectTimeout": 60000,<br>"retries": 5,<br>"apis": [<br>"2bbbe74e-07a8-4139-8985-ace1d9e70229",<br>"b4bf2ef4-e1d3-4a2b-bafb-701ac61e70d9",<br>"bba3a753-45e0-4bc8-836d-b1a6523aa981",<br>"f479aa86-b465-469b-b8e9-9a1304504973",<br>"6225e0d0-8924-4792-80ca-bcc8efc3c4ad",<br>"42478ae8-56ba-409b-b0c1-8889770cbbf2",<br>"b9050a3c-f5a5-4f7e-8849-e773abb0ee5b",<br>"423ac005-5872-489c-ae3c-d4d7e7bd61be",<br>"1e6c9103-2c2e-46db-af20-378c6b3ee42c",<br>"05d6382a-4ffe-40b8-89e7-f16f2deb065c"<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"success": [<br>"b9050a3c-f5a5-4f7e-8849-e773abb0ee5b",<br>"423ac005-5872-489c-ae3c-d4d7e7bd61be",<br>"bba3a753-45e0-4bc8-836d-b1a6523aa981",<br>"f479aa86-b465-469b-b8e9-9a1304504973",<br>"2bbbe74e-07a8-4139-8985-ace1d9e70229",<br>"6225e0d0-8924-4792-80ca-bcc8efc3c4ad",<br>"42478ae8-56ba-409b-b0c1-8889770cbbf2",<br>"b4bf2ef4-e1d3-4a2b-bafb-701ac61e70d9",<br>"05d6382a-4ffe-40b8-89e7-f16f2deb065c",<br>"1e6c9103-2c2e-46db-af20-378c6b3ee42c"<br>],<br>"failed": []<br>},<br>"status": true<br>\} |  |  |  |

#### 调试后端服务API

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt//native-services/{nativeSvcId}/apis/{apiId}/test | JSON | POST | 调试后端服务API |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| parameters | True | List<Json> | 参数 |
| auth | True | Json | 授权信息 |
| headers | True | Json | 头部 |
| requestBody | False | Json | 请求体 |
| nativeSvcId | True | String | 后端服务id |
| apiId | True | String | 后端服务API id |
| 例如：<br>{<br>"parameters": [<br>{<br>"name": "all",<br>"in": "query",<br>"value": false<br>},<br>{<br>"name": "size",<br>"in": "query",<br>"value": ""<br>},<br>{<br>"name": "limit",<br>"in": "query",<br>"value": "1"<br>},<br>{<br>"name": "filters",<br>"in": "query",<br>"value": ""<br>}<br>],<br>"headers": {},<br>"auth": {<br>"token": "test token",<br>"type": "bearer"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"status": true,<br>"httpCode": 200,<br>"content": [<br>{<br>"Id": "51ee5b7f33581d02f327fd469ae8f61e592d23676333e455afd9fba65416993a",<br>"Names": [<br>"/k8s_httpd_os-deploy-mgt-0_cloudos-paas_03e6eaca-d6a2-11eb-9cb4-0cda411d0674_0"<br>],<br>"Image": "sha256:4a2de5f2d3c8a2639f38373573960cb6f25321c6221bb9b57a91aab21f412993",<br>"ImageID": "sha256:4a2de5f2d3c8a2639f38373573960cb6f25321c6221bb9b57a91aab21f412993",<br>"Command": "httpd-foreground",<br>"Created": 1624727697,<br>"Ports": [],<br>"Labels": {<br>"annotation.io.kubernetes.container.hash": "2525022",<br>"annotation.io.kubernetes.container.ports": "[{\"containerPort\":80,\"protocol\":\"TCP\"}]",<br>"annotation.io.kubernetes.container.restartCount": "0",<br>"annotation.io.kubernetes.container.terminationMessagePath": "/dev/termination-log",<br>"annotation.io.kubernetes.container.terminationMessagePolicy": "File",<br>"annotation.io.kubernetes.pod.terminationGracePeriod": "5",<br>"io.kubernetes.container.logpath": "/var/log/pods/03e6eaca-d6a2-11eb-9cb4-0cda411d0674/httpd/0.log",<br>"io.kubernetes.container.name": "httpd",<br>"io.kubernetes.docker.type": "container",<br>"io.kubernetes.pod.name": "os-deploy-mgt-0",<br>"io.kubernetes.pod.namespace": "cloudos-paas",<br>"io.kubernetes.pod.uid": "03e6eaca-d6a2-11eb-9cb4-0cda411d0674",<br>"io.kubernetes.sandbox.id": "305f76d5be5ce73cc97779d0b5934e16823be21526548e2147634ed315427c6e"<br>},<br>"State": "running",<br>"Status": "Up 37 hours",<br>"HostConfig": {<br>"NetworkMode": "container:305f76d5be5ce73cc97779d0b5934e16823be21526548e2147634ed315427c6e"<br>},<br>"NetworkSettings": {<br>"Networks": {}<br>},<br>"Mounts": [<br>{<br>"Type": "bind",<br>"Source": "/var/lib/origin/openshift.local.volumes/pods/03e6eaca-d6a2-11eb-9cb4-0cda411d0674/volumes/kubernetes.io~secret/admin-token-8vds5",<br>"Destination": "/var/run/secrets/kubernetes.io/serviceaccount",<br>"Mode": "ro,Z",<br>"RW": false,<br>"Propagation": "rprivate"<br>},<br>{<br>"Type": "bind",<br>"Source": "/var/lib/origin/openshift.local.volumes/pods/03e6eaca-d6a2-11eb-9cb4-0cda411d0674/etc-hosts",<br>"Destination": "/etc/hosts",<br>"Mode": "Z",<br>"RW": true,<br>"Propagation": "rprivate"<br>},<br>{<br>"Type": "bind",<br>"Source": "/var/lib/origin/openshift.local.volumes/pods/03e6eaca-d6a2-11eb-9cb4-0cda411d0674/containers/httpd/17e0ade1",<br>"Destination": "/dev/termination-log",<br>"Mode": "Z",<br>"RW": true,<br>"Propagation": "rprivate"<br>},<br>{<br>"Type": "bind",<br>"Source": "/var/lib/origin/openshift.local.volumes/pods/03e6eaca-d6a2-11eb-9cb4-0cda411d0674/volumes/kubernetes.io~iscsi/paas-repo",<br>"Destination": "/usr/local/apache2/htdocs",<br>"Mode": "Z",<br>"RW": true,<br>"Propagation": "rprivate"<br>}<br>]<br>}<br>],<br>"responseHeaders": {<br>"Api-Version": [<br>"1.26"<br>],<br>"Content-Type": [<br>"application/json"<br>],<br>"Docker-Experimental": [<br>"false"<br>],<br>"Server": [<br>"Docker/1.13.1 (linux)"<br>],<br>"Date": [<br>"Mon, 28 Jun 2021 06:33:21 GMT"<br>],<br>"Transfer-Encoding": [<br>"chunked"<br>]<br>}<br>},<br>"status": true<br>\} |  |  |  |

#### 配置后端服务API调试参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/apimgt//native-services/{nativeSvcId}/apis/{apiId}/test/config | JSON | POST | 配置后端服务API调试参数 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| parameters | True | List<Json> | 参数 |
| auth | True | Json | 授权信息 |
| headers | True | Json | 头部 |
| requestBody | False | Json | 请求体 |
| nativeSvcId | True | String | 后端服务id |
| apiId | True | String | 后端服务API id |
| 例如：<br>{<br>"parameters": [<br>{<br>"name": "all",<br>"in": "query",<br>"value": false<br>},<br>{<br>"name": "size",<br>"in": "query",<br>"value": ""<br>},<br>{<br>"name": "limit",<br>"in": "query",<br>"value": "1"<br>},<br>{<br>"name": "filters",<br>"in": "query",<br>"value": ""<br>}<br>],<br>"headers": {},<br>"auth": {<br>"token": "test token",<br>"type": "bearer"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 结果信息 | 结果信息 |
| status | Boolean | 是否成功 |  |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

### 服务网关管理

#### 服务网关列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications | JSON | GET | 服务网关列表 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| name | False | String | 服务网关名 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 创建快照成功的结果信息 | 创建快照成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"msg": "",<br>"total": 3,<br>"code": "",<br>"data": [<br>{<br>"uuid": "3bf9f630-6c8c-439e-8848-9eee0eafbcab",<br>"name": "tt-10.125.29.82",<br>"description": "",<br>"openService": "1",<br>"approval": "disable",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"serviceType": "svcgw",<br>"serviceVersion": "1.0",<br>"kongIp": "10.125.29.82/22",<br>"kongHttpProxyPort": "48000",<br>"kongHttpsProxyPort": "48443",<br>"status": "ERROR",<br>"metadata": null,<br>"resourceType": "CONTAINER",<br>"namespace": "svcgw996aa88f",<br>"releaseName": "svcgw4ea4976c",<br>"svcApimgtName": "svcgw996aa88f-svcgw4ea4976c-apimgt-svc.svcgw996aa88f.svc:24180",<br>"svcKonjacName": "svcgw996aa88f-svcgw4ea4976c-konjac-svc.svcgw996aa88f.svc",<br>"svcKongName": "svcgw996aa88f-svcgw4ea4976c-kong-admin-svc.svcgw996aa88f.svc",<br>"svcDbName": "svcgw996aa88f-svcgw4ea4976c-psql-svc.svcgw996aa88f.svc",<br>"k8sClusterId": "123456",<br>"k8sClusterVip": "",<br>"createTime": "2021-10-29T06:53:42.000+0000",<br>"updateTime": "2021-10-29T06:53:44.000+0000",<br>"zoneId": "default",<br>"zoneName": "",<br>"projectName": "root",<br>"userName": "admin",<br>"resource": "{\"limits\":{\"cpu\":\"2\",\"memory\":\"2Gi\"},\"requests\":{\"cpu\":\"0.5\",\"memory\":\"0.5Gi\"}}",<br>"pvcType": "dynamic",<br>"storageClass": "jaeger",<br>"dataVolume": "5Gi",<br>"replicas": 1,<br>"volumeName": null,<br>"clusterType": "PUBLIC_ZONE",<br>"clusterPort": "8443",<br>"privateKaasName": null<br>},<br>{<br>"uuid": "480cb7e1-3f9e-428e-aaf0-66f001c49fa8",<br>"name": "多实例1026",<br>"description": "",<br>"openService": "1",<br>"approval": "disable",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"serviceType": "svcgw",<br>"serviceVersion": "1.0",<br>"kongIp": "10.125.29.86/22,10.125.29.87/22",<br>"kongHttpProxyPort": "48000,48000",<br>"kongHttpsProxyPort": "48443,48443",<br>"status": "PENDING_UPGRADE",<br>"metadata": null,<br>"resourceType": "CONTAINER",<br>"namespace": "svcgwd99f454a",<br>"releaseName": "svcgw05af38b7",<br>"svcApimgtName": "svcgwd99f454a-svcgw05af38b7-apimgt-svc.svcgwd99f454a.svc:24180",<br>"svcKonjacName": "svcgwd99f454a-svcgw05af38b7-konjac-svc.svcgwd99f454a.svc",<br>"svcKongName": "svcgwd99f454a-svcgw05af38b7-kong-admin-svc.svcgwd99f454a.svc",<br>"svcDbName": "svcgwd99f454a-svcgw05af38b7-psql-svc.svcgwd99f454a.svc",<br>"k8sClusterId": "123456",<br>"k8sClusterVip": "10.125.31.185",<br>"createTime": "2021-10-26T08:28:05.000+0000",<br>"updateTime": "2021-10-27T07:44:00.000+0000",<br>"zoneId": "739c7208-7",<br>"zoneName": "主集群工作节点",<br>"projectName": "root",<br>"userName": "admin",<br>"resource": "{\"limits\":{\"cpu\":\"2\",\"memory\":\"2Gi\"},\"requests\":{\"cpu\":\"0.5\",\"memory\":\"0.5Gi\"}}",<br>"pvcType": "dynamic",<br>"storageClass": "paas-nfs",<br>"dataVolume": "5Gi",<br>"replicas": 2,<br>"volumeName": "pvc-a5e2087b-3636-11ec-b8df-0cda411d78fe",<br>"clusterType": "PUBLIC_ZONE",<br>"clusterPort": "8443",<br>"privateKaasName": null<br>},<br>{<br>"uuid": "97b81212-f89a-4df6-b796-ca3d1ee12e65",<br>"name": "t10.125.29.84",<br>"description": "",<br>"openService": "1",<br>"approval": "disable",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"serviceType": "svcgw",<br>"serviceVersion": "1.0",<br>"kongIp": "10.125.29.84/22",<br>"kongHttpProxyPort": "48000",<br>"kongHttpsProxyPort": "48443",<br>"status": "RUNNING",<br>"metadata": null,<br>"resourceType": "CONTAINER",<br>"namespace": "svcgwbdfa8a08",<br>"releaseName": "svcgwf59ad009",<br>"svcApimgtName": "svcgwbdfa8a08-svcgwf59ad009-apimgt-svc.svcgwbdfa8a08.svc:24180",<br>"svcKonjacName": "svcgwbdfa8a08-svcgwf59ad009-konjac-svc.svcgwbdfa8a08.svc",<br>"svcKongName": "svcgwbdfa8a08-svcgwf59ad009-kong-admin-svc.svcgwbdfa8a08.svc",<br>"svcDbName": "svcgwbdfa8a08-svcgwf59ad009-psql-svc.svcgwbdfa8a08.svc",<br>"k8sClusterId": "123456",<br>"k8sClusterVip": "10.125.31.185",<br>"createTime": "2021-08-21T01:02:58.000+0000",<br>"updateTime": "2021-10-29T02:16:33.000+0000",<br>"zoneId": "739c7208-7",<br>"zoneName": "主集群工作节点",<br>"projectName": "root",<br>"userName": "admin",<br>"resource": "{\"limits\":{\"cpu\":\"2\",\"memory\":\"2Gi\"},\"requests\":{\"cpu\":\"0.5\",\"memory\":\"0.5Gi\"}}",<br>"pvcType": "dynamic",<br>"storageClass": "paas-nfs",<br>"dataVolume": "5Gi",<br>"replicas": 1,<br>"volumeName": "pvc-88106e34-021b-11ec-98e3-0cda411d616d",<br>"clusterType": "PUBLIC_ZONE",<br>"clusterPort": "8443",<br>"privateKaasName": null<br>}<br>],<br>"status": true<br>\} |  |  |  |

#### 服务网关详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications/{id\} | JSON | GET | 服务网关详情 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 实例ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 创建快照成功的结果信息 | 创建快照成功时返回 |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": {<br>"uuid": "97b81212-f89a-4df6-b796-ca3d1ee12e65",<br>"name": "t10.125.29.84",<br>"description": "",<br>"openService": "1",<br>"approval": "disable",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"serviceType": "svcgw",<br>"serviceVersion": "1.0",<br>"kongIp": "10.125.29.84/22",<br>"kongHttpProxyPort": "48000",<br>"kongHttpsProxyPort": "48443",<br>"status": "RUNNING",<br>"metadata": null,<br>"resourceType": "CONTAINER",<br>"namespace": "svcgwbdfa8a08",<br>"releaseName": "svcgwf59ad009",<br>"svcApimgtName": "svcgwbdfa8a08-svcgwf59ad009-apimgt-svc.svcgwbdfa8a08.svc:24180",<br>"svcKonjacName": "svcgwbdfa8a08-svcgwf59ad009-konjac-svc.svcgwbdfa8a08.svc",<br>"svcKongName": "svcgwbdfa8a08-svcgwf59ad009-kong-admin-svc.svcgwbdfa8a08.svc",<br>"svcDbName": "svcgwbdfa8a08-svcgwf59ad009-psql-svc.svcgwbdfa8a08.svc",<br>"k8sClusterId": "123456",<br>"k8sClusterVip": "10.125.31.185",<br>"createTime": "2021-08-21T01:02:58.000+0000",<br>"updateTime": "2021-10-29T02:16:33.000+0000",<br>"zoneId": "739c7208-7",<br>"zoneName": "主集群工作节点",<br>"projectName": "root",<br>"userName": "admin",<br>"resource": "{\"limits\":{\"cpu\":\"2\",\"memory\":\"2Gi\"},\"requests\":{\"cpu\":\"0.5\",\"memory\":\"0.5Gi\"}}",<br>"pvcType": "dynamic",<br>"storageClass": "paas-nfs",<br>"dataVolume": "5Gi",<br>"replicas": 1,<br>"volumeName": "pvc-88106e34-021b-11ec-98e3-0cda411d616d",<br>"clusterType": "PUBLIC_ZONE",<br>"clusterPort": "8443",<br>"privateKaasName": null,<br>"kongPodEntityList": [<br>{<br>"name": "svcgwbdfa8a08-svcgwf59ad009-kong-0a7d1d54-66dfd6f698-jbc6c",<br>"ready": false,<br>"status": "Running",<br>"kongIp": "10.125.29.84/22",<br>"kongHttpProxyPort": "48000",<br>"kongHttpsProxyPort": "48443",<br>"created_at": "2021-10-29 10:15:43",<br>"up_time": 17279948,<br>"cpu_info": {<br>"usage": 0.01,<br>"total": 2.0<br>},<br>"mem_info": {<br>"usage": 701.738,<br>"total": 2048.0<br>}<br>}<br>]<br>},<br>"status": true<br>\} |  |  |  |

#### 创建服务网关

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications | JSON | POST | 创建服务网关 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 名称 |
| description | True | String | 描述 |
| approval | True | String | 是否开启服务发布审批 |
| openService | True | String | 实例公私有 |
| serviceType | True | String | 服务类型 |
| serviceVersion | True | String | 服务版本 |
| kongIp | True | String | 虚IP，多实例逗号分隔 |
| kongHttpProxyPort | True | String | http端口，多实例逗号分隔 |
| kongHttpsProxyPort | True | String | https端口，多实例逗号分隔 |
| resource | True | Json | 资源限制 |
| dataVolume | True | String | 存储卷大小 |
| isStatic | True | Boolean | 是否静态PV |
| storageClass | False | String | isStatic为False时，添加 |
| resourceType | True | String | 资源类型 |
| clusterId | True | String | 集群id |
| clusterVip | True | String | 集群vip |
| zone | True | List | 可用域 |
| zoneId | True | String | 可用域id |
| clusterType | False | String | 独享集群/共享集群（PRIVATE_KAAS/PUBLIC_ZONE） |
| clusterPort | False | String | k8s api server端口 |
| {<br>"name": "srhtst",<br>"description": "srhtstdesc",<br>"serviceType": "svcgw",<br>"serviceVersion": "1.0",<br>"openService": "1",<br>"approval": "disable",<br>"kongAddr": [<br>{<br>"kongIp": "10.125.29.88/22",<br>"kongHttpProxyPort": 48000,<br>"kongHttpsProxyPort": 48443,<br>"name": "srhtst"<br>}<br>],<br>"resource": {<br>"limits": {<br>"cpu": "2",<br>"memory": "2Gi"<br>},<br>"requests": {<br>"cpu": "0.5",<br>"memory": "0.5Gi"<br>}<br>},<br>"dataVolume": "5Gi",<br>"isStatic": false,<br>"resourceType": "CONTAINER",<br>"storageClass": "jaeger",<br>"clusterId": "123456",<br>"clusterVip": "",<br>"zone": [<br>"2df75ce8-7"<br>],<br>"zoneId": "2df75ce8-7",<br>"num": 1,<br>"kongIp": "10.125.29.88/22",<br>"kongHttpProxyPort": "48000",<br>"kongHttpsProxyPort": "48443"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 错误信息 | 正常成功情况下，data为null |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 申请服务网关

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications/bpm | JSON | POST | 申请服务网关 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 名称 |
| description | True | String | 描述 |
| approval | True | String | 是否开启服务发布审批 |
| openService | True | String | 实例公私有 |
| serviceType | True | String | 服务类型 |
| serviceVersion | True | String | 服务版本 |
| kongIp | True | String | 虚IP，多实例逗号分隔 |
| kongHttpProxyPort | True | String | http端口，多实例逗号分隔 |
| kongHttpsProxyPort | True | String | https端口，多实例逗号分隔 |
| resource | True | Json | 资源限制 |
| dataVolume | True | String | 存储卷大小 |
| isStatic | True | Boolean | 是否静态PV |
| storageClass | False | String | isStatic为False时，添加 |
| resourceType | True | String | 资源类型 |
| clusterId | True | String | 集群id |
| clusterVip | True | String | 集群vip |
| zone | True | List | 可用域 |
| zoneId | True | String | 可用域id |
| clusterType | False | String | 独享集群/共享集群（PRIVATE_KAAS/PUBLIC_ZONE） |
| clusterPort | False | String | k8s api server端口 |
| 例如：<br>{<br>"name": "xyh2",<br>"description": "1234567890",<br>"openService": "0",<br>"approval": "enable",<br>"serviceType": "svcgw",<br>"serviceVersion": "1.0",<br>"kongIp": "10.125.29.27/22,10.125.29.28/22",<br>"kongHttpProxyPort": "48000,48000",<br>"kongHttpsProxyPort":"48443,48443",<br>"resource": {<br>"limits": {<br>"cpu": "2",<br>"memory": "2Gi"<br>},<br>"requests": {<br>"cpu": "500m",<br>"memory": "512Mi"<br>}<br>},<br>"dataVolume": "6Gi",<br>"isStatic": true,<br>"resourceType": "CONTAINER",<br>"clusterId": "123456",<br>"clusterVip": "10.125.30.179",<br>"zone": ["zone-default"],<br>"zoneId": "default"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 错误信息 | 正常成功情况下，data为null |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null<br>\} |  |  |  |

#### 独享集群列表查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/svcgw/v1.0/kaas_clusters | JSON | GET | 独享集群列表查询 |

###### 参数说明

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 重启错误信息 | 正常成功情况下，data为null |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "137165F7-4C04-4EA8-AFB5-C0B7EF73",<br>"name": "ccx",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "10.125.41.120",<br>"status": "running",<br>"description": "",<br>"createTime": 1630564625000,<br>"networkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "PaaS31185",<br>"version": "1.17.5",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"token": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImF4Zjc5cmE2NHI0VkRqbWFaak1qcjFjdG9raURZVjNDc1VobkQ2bDdMS2MifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrYWFzLXN5c3RlbSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJrYWFzLWFkbWluLXRva2VuLXpwZDJ0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImthYXMtYWRtaW4iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiIxMzg0ZTg0OC04MDJkLTRjN2YtOTFjYS1mYjQ4MGI4NTFhZTEiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a2Fhcy1zeXN0ZW06a2Fhcy1hZG1pbiJ9.AN8jBFYvKfp8tXudaEofhVlFuCA75PuZgys1G-iRFG4URwhgi3hzMwzLJcWDw_A7-xikfUHL8A0rSOOSl6OB7YUkebuwIAlkRYyI61QILEK81OCEPJy3eYwDdDuSFHHIoJunepVq-w5sH0x8Fog0fI1JSWCCeVLAm32bArm8O6FzPZZfxGeQ9XJ5lxNUQoSztHW1sYYKXrRi0hVsNyAolFmDlnoKP-8Lo5NuQN7B9i6JV6CgyjnbP6U18W8HegCuy69MWMHEgdAAZbF0gW1zgRdaIb7PNfPJD44UIDQCnMYkd4Sur4lEfxslm4GNhSHnlGowxhkjhs3K--xwk-93Hw",<br>"vip": "10.125.41.120",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenantName": "admin",<br>"releaseId": "r-304j5h5i,r-dvtc1fzj",<br>"subnetInfo": "subnet(10.125.41.0/24)",<br>"subnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"loadBalance": false,<br>"businessNetworkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"businessNetworkName": "PaaS31185",<br>"businessSubnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"businessSubnetInfo": "subnet(10.125.41.0/24)",<br>"businessMaster": "10.125.41.120",<br>"businessVip": "10.125.41.120",<br>"nodeNum": 3,<br>"masterIps": "{\"10.125.41.120\":\"10.125.41.120\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"totalWorker": 2,<br>"avilableWorker": 1,<br>"core": "12.0",<br>"memory": "23.34G",<br>"multiTenantNet": false,<br>"newestVersion": false,<br>"process": true<br>},<br>{<br>"uuid": "5A0D6677-3742-4F34-A909-9FC92C43",<br>"name": "crt.nineqq",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "10.125.41.63",<br>"status": "running",<br>"description": "crt.nineqq",<br>"createTime": 1634381159000,<br>"networkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "PaaS31185",<br>"version": "1.19.10",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IllBeFlMZVFxbVlNOUo4d1dBXzFHbWJfa0lQZm5nOHpYblg4WWJDaWxyWUkifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrYWFzLXN5c3RlbSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJrYWFzLWFkbWluLXRva2VuLWwycHNxIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImthYXMtYWRtaW4iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiI3OGY3MDAyOS1mNmNkLTQyZTgtYjdiNS1mOGU1MDVmN2RlZDkiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a2Fhcy1zeXN0ZW06a2Fhcy1hZG1pbiJ9.GTRS5TWB6ACNppuoP_Jqbc5RsaekHSkiUVAkLM-ogWGwYUD_o7p31Sm3X4fuQSiLY-5H2wFw4U9FBAI-SjGwEplmtER-_5GPTPJqK_Bx1qmJOwUmWmecm-8JyP3lsIVW3Og2R0rmUuKDoNvmDy_uKvnDPCh3aSGGffxlFMlaOOjU7nsdN_wG7-WQ-cVEzhMdI1cy57-i37Bj5hJMeg3uDI5wtiHD196iTfmCoMf3eqbMLUYNc6TSC7cwBGpMNhJrYbRJxlWn0WzZFgfMz_bW9o5c2YmHx9C5gHoZJF4hZsRtWH6KEk0ayVuD52ljFYSJvCzX-MBVyt5jWLQWKXtf4g",<br>"vip": "10.125.41.63",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenantName": "admin",<br>"releaseId": "r-05id38eq,r-8kuzzfzi",<br>"subnetInfo": "subnet(10.125.41.0/24)",<br>"subnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"loadBalance": false,<br>"businessNetworkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"businessNetworkName": "PaaS31185",<br>"businessSubnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"businessSubnetInfo": "subnet(10.125.41.0/24)",<br>"businessMaster": "10.125.41.63",<br>"businessVip": "10.125.41.63",<br>"nodeNum": 3,<br>"masterIps": "{\"10.125.41.63\":\"10.125.41.63\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"totalWorker": 2,<br>"avilableWorker": 2,<br>"core": "12.0",<br>"memory": "23.34G",<br>"multiTenantNet": false,<br>"newestVersion": true,<br>"process": true<br>},<br>{<br>"uuid": "82E61542-04EE-448E-A0E9-5F8E8565",<br>"name": "crt.fourqq",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "10.125.41.81",<br>"status": "running",<br>"description": "crt.fourqq",<br>"createTime": 1634379723000,<br>"networkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "PaaS31185",<br>"version": "1.14.5",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrYWFzLXN5c3RlbSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJrYWFzLWFkbWluLXRva2VuLXZ4anBwIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImthYXMtYWRtaW4iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiIxZTU0MGVkMS0yZTZkLTExZWMtOTZlYS1mYTE2M2VkZmJhN2UiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a2Fhcy1zeXN0ZW06a2Fhcy1hZG1pbiJ9.AYuATW2HaQXiJEFrrDcVsdz1apCjr5MhEScfuCVeqj5U6p4LwhF6BgX40WNgzqcZ3hJCGhVOYPUfA6wab5qemO2ZcH-OXhF7Y1SFw8YdUluWn5_EGCXHkcCUcfkdbSWI8vTplmeuCPRBednAFkZfbcZ6lhGL03E7aUO6uR-0cbmKty8ERpx-8VV0IjEhPLaFdfZacjCcW5MeFcWc1UrCopLikA0l_7fyvY-3dnHzJlDx5CMi-R8JnYBjyOsiA02UJbL245aIem6qgsETWmRliIrFMOact6-25PJj2NKFG-1qDjhtOn1ooV6lrBQ1lUhV5RhfMP2uXt4zLsjTFA0euQ",<br>"vip": "10.125.41.81",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenantName": "admin",<br>"releaseId": "r-xbtz08em,r-uqksmpwm",<br>"subnetInfo": "subnet(10.125.41.0/24)",<br>"subnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"loadBalance": false,<br>"businessNetworkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"businessNetworkName": "PaaS31185",<br>"businessSubnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"businessSubnetInfo": "subnet(10.125.41.0/24)",<br>"businessMaster": "10.125.41.81",<br>"businessVip": "10.125.41.81",<br>"nodeNum": 2,<br>"masterIps": "{\"10.125.41.81\":\"10.125.41.81\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"totalWorker": 1,<br>"avilableWorker": 0,<br>"core": "8.0",<br>"memory": "15.56G",<br>"multiTenantNet": false,<br>"newestVersion": false,<br>"process": true<br>},<br>{<br>"uuid": "9823D472-6BCF-4307-8C74-253D383C",<br>"name": "flannel.pp",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "10.125.41.110",<br>"status": "running",<br>"description": "flannel.pp",<br>"createTime": 1632537124000,<br>"networkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "PaaS31185",<br>"version": "1.17.5",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"token": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImRqbUlvajM3dWlxdGFEYlpLcTN6bWZwOTJQVjR0eTRhbUhLRG5sY0ZraGcifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrYWFzLXN5c3RlbSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJrYWFzLWFkbWluLXRva2VuLXpkeGMyIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImthYXMtYWRtaW4iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiJkZTQ5OTk2NC1iYjFmLTQ1OWItOGJmNy0wNzRmYTYyNThkMDEiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a2Fhcy1zeXN0ZW06a2Fhcy1hZG1pbiJ9.SA6u3yqalx_yRRHoQDvx7gxjFi32jAr48UVCntTh_JIey_Zw6Hye7C3zh_DfsRqRWZAnMfwNNnHLz7bRPR9aZQrm-obrwX_QONPBA03d3iB7n620Z1UcajzP-AzNONBNfbSKDywLm3mnBCfONJ70dhVWq73t3zzIbnXkRVQWjwrlTT7jMonN6uqMa76JPwGGAVgNHRn6XncEcTnyBGKmkVvqa3Gao4q1XUqUYF04sz1m1DEHeHqjUn0lK4EvB8PNJL0BIvO9YxXtrglBQCMxedsHrL9h7RVDIhgFSubIklQTVGhyxUzr9nJSShtV6fFVUL1AnfbywOmHzvtxbzi57A",<br>"vip": "10.125.41.110",<br>"tenantId": "78a8084ab62d4608ad3cc2de3f603cad",<br>"tenantName": "test-org-zb",<br>"releaseId": "r-xglios2t,r-qpcysg3j",<br>"subnetInfo": "subnet(10.125.41.0/24)",<br>"subnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"loadBalance": false,<br>"businessNetworkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"businessNetworkName": "PaaS31185",<br>"businessSubnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"businessSubnetInfo": "subnet(10.125.41.0/24)",<br>"businessMaster": "10.125.41.110",<br>"businessVip": "10.125.41.110",<br>"nodeNum": 1,<br>"masterIps": "{\"10.125.41.110\":\"10.125.41.110\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"totalWorker": 0,<br>"avilableWorker": 0,<br>"core": "4.0",<br>"memory": "7.78G",<br>"multiTenantNet": false,<br>"newestVersion": false,<br>"process": true<br>},<br>{<br>"uuid": "A4F4F6A2-716D-4FCB-865C-76951A6C",<br>"name": "upupup.bb",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "10.125.41.112",<br>"status": "running",<br>"description": "upupup.bb",<br>"createTime": 1634107014000,<br>"networkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "PaaS31185",<br>"updateTime": 1634112082000,<br>"version": "1.19.10",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrYWFzLXN5c3RlbSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJrYWFzLWFkbWluLXRva2VuLWpjZmpoIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImthYXMtYWRtaW4iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiJlNzk4NWY5Yy0yYmYxLTExZWMtYmVmYi1mYTE2M2ViMzJiYTkiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a2Fhcy1zeXN0ZW06a2Fhcy1hZG1pbiJ9.xfFEGGqLSbzUZgLhIkeJwJq_oKQh7NPQtD6gOe5ATAfyO4kvNklm535P5Wz1oLCCzdcTfh_SdSxeVxoNX12AjixDXzBrRn2pyCkLzXKh5BA9iw1z_Td0hRgCNMIiVMFX9EFgFoApgffN2HoqsRAxaEosRMmuzybh8MpxOj7tnhLxhe0IQ3AYGCV8_dTa-o-arikQqlG5T0ZXGWqfWW3hSN_s1GCzZzZj2uJLljyANxFazEqOEYSLOkKfq0tprmnIR2tLZTRkWFFsLYYDZ08cVPJkIyPgiHcoqICwrkvXweI8k3kWwE8LCNilUXGwiIZARUZuwu-tzzw9nuwWZS0gJA",<br>"vip": "10.125.41.112",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenantName": "admin",<br>"releaseId": "r-kg0y0jsy,r-fpqlhd07",<br>"subnetInfo": "subnet(10.125.41.0/24)",<br>"subnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"loadBalance": false,<br>"businessNetworkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"businessNetworkName": "PaaS31185",<br>"businessSubnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"businessSubnetInfo": "subnet(10.125.41.0/24)",<br>"businessMaster": "10.125.41.112",<br>"businessVip": "10.125.41.112",<br>"nodeNum": 2,<br>"masterIps": "{\"10.125.41.112\":\"10.125.41.112\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"totalWorker": 1,<br>"avilableWorker": 1,<br>"core": "8.0",<br>"memory": "15.56G",<br>"multiTenantNet": false,<br>"newestVersion": true,<br>"process": true<br>},<br>{<br>"uuid": "CA37DF06-BEFA-46B0-A4B9-1933422B",<br>"name": "jjj",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "12.12.0.44",<br>"status": "fail",<br>"description": "",<br>"createTime": 1635318115000,<br>"networkId": "11f08684-76a0-4494-a5a2-91c03e844d2b",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "sdfsdf",<br>"version": "1.18.9",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"vip": "12.12.0.44",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenantName": "admin",<br>"releaseId": "r-yggw9d8f,r-ra406105",<br>"subnetInfo": "test(12.12.0.0/16)",<br>"subnetId": "f10adf2b-6f5c-4318-8009-286f7eea5107",<br>"loadBalance": false,<br>"businessNetworkId": "11f08684-76a0-4494-a5a2-91c03e844d2b",<br>"businessNetworkName": "sdfsdf",<br>"businessSubnetId": "f10adf2b-6f5c-4318-8009-286f7eea5107",<br>"businessSubnetInfo": "test(12.12.0.0/16)",<br>"businessMaster": "12.12.0.44",<br>"businessVip": "12.12.0.44",<br>"nodeNum": 0,<br>"masterIps": "{\"12.12.0.44\":\"12.12.0.44\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "11f08684-76a0-4494-a5a2-91c03e844d2b",<br>"multiTenantNet": false,<br>"newestVersion": true,<br>"process": true<br>},<br>{<br>"uuid": "D0799B10-F4D6-4198-80B6-13377756",<br>"name": "cert.aa",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "10.125.41.104",<br>"status": "running",<br>"description": "cert.aa",<br>"createTime": 1634217695000,<br>"networkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "PaaS31185",<br>"version": "1.19.10",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IlpaNDg3Z1QxRTJKZV9qTlRoTUcycnhabDQ0WUlMNUNkanU5TVlkWHpmMUkifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrYWFzLXN5c3RlbSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJrYWFzLWFkbWluLXRva2VuLTk0YzVyIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImthYXMtYWRtaW4iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiIzM2ZmODE4OS1hY2EwLTRkY2EtOTBjOC0xOWJlZDkxNjAzNDUiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a2Fhcy1zeXN0ZW06a2Fhcy1hZG1pbiJ9.TC33lvTRY0Ur87sHlVsJdeggJg7IhtrocG5ongf-Gjc8uYd1y916s11pMGwGQUBPzWGwWeVYg3T0RjKlVlTlG-BVujEP1zo3gHLcxYO5r9Q0LuAPSnVFt-1ixgxNPWQRXjY4NenMKLPCsHYjCV9X1vmMXgzA2fhiOi5bJynAKp1FG6JU4CrvfdCCKxf6v16smFlsoTI70hCfSgRcjvP8cA9wNbiYJhrW1DhPPaASf0JAxgITKsf745fQ0Aht88FIlBZYLKmgnQytOBxcbiFN7YSYswfaEPGRUc0_A43pVYizxamDt9dvOY9dssFDpY1axEsczVy_P5zdv5MSt17Iig",<br>"vip": "10.125.41.104",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenantName": "admin",<br>"releaseId": "r-81uobgyu,r-jvlh79as",<br>"subnetInfo": "subnet(10.125.41.0/24)",<br>"subnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"loadBalance": false,<br>"businessNetworkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"businessNetworkName": "PaaS31185",<br>"businessSubnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"businessSubnetInfo": "subnet(10.125.41.0/24)",<br>"businessMaster": "10.125.41.104",<br>"businessVip": "10.125.41.104",<br>"nodeNum": 2,<br>"masterIps": "{\"10.125.41.104\":\"10.125.41.104\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"totalWorker": 1,<br>"avilableWorker": 1,<br>"core": "8.0",<br>"memory": "19.5G",<br>"multiTenantNet": false,<br>"newestVersion": true,<br>"process": true<br>},<br>{<br>"uuid": "DD99756C-2DBC-484D-B41D-83A5B14B",<br>"name": "crt.sevenqq",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "10.125.41.73",<br>"status": "running",<br>"description": "crt.sevenqq",<br>"createTime": 1634379790000,<br>"networkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "PaaS31185",<br>"updateTime": 1634519157000,<br>"version": "1.19.10",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"token": "eyJhbGciOiJSUzI1NiIsImtpZCI6Ik5KR0dKR1FtS2hiaWpfWkkwbkszb3Vmd2ktOWtONlZfMGFSSnhEczVvT28ifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrYWFzLXN5c3RlbSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJrYWFzLWFkbWluLXRva2VuLWhrd3ByIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImthYXMtYWRtaW4iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiJkN2NjMTc1ZS05NzlkLTQ1NGQtYWNhMi0yOWY1NDQ3YmMzNjUiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a2Fhcy1zeXN0ZW06a2Fhcy1hZG1pbiJ9.h2vba8GsrOS-n1tmyFa_bF1cUzE2wcYCBDRzMLrZ5C9tnOM7U3NAwVmMVkXfv7nQ7o5QqZ9gLua_ovCHmYZOTjzzN74Ws_O_irYLeI3EnyAI4NUvdErMVVw5ntGWCIRWXzuBHsF_HO42zNlcgNlsl2_L5nEZg-fZudr8A1DKt6l4RuUkzXnRNRB2FzBs8NPLfRR7qxO99MTf-JpSANDWvr7kZpfU05bSN39PnzMqCZMXwQVbOA9AxqutU96CHOp8ZPGXuk2nI_UlTC1v0SXCcG5W13vs1APrnSt_VXlVvt5h2ws7Inqd-i7GUTtGgldTT4K9PTqO5X7tsAiJVpMT1Q",<br>"vip": "10.125.41.73",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenantName": "admin",<br>"releaseId": "r-0y0qama6,r-i30zzk4i",<br>"subnetInfo": "subnet(10.125.41.0/24)",<br>"subnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"loadBalance": false,<br>"businessNetworkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"businessNetworkName": "PaaS31185",<br>"businessSubnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"businessSubnetInfo": "subnet(10.125.41.0/24)",<br>"businessMaster": "10.125.41.73",<br>"businessVip": "10.125.41.73",<br>"nodeNum": 3,<br>"masterIps": "{\"10.125.41.73\":\"10.125.41.73\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"totalWorker": 2,<br>"avilableWorker": 2,<br>"core": "12.0",<br>"memory": "23.34G",<br>"multiTenantNet": false,<br>"newestVersion": true,<br>"process": true<br>},<br>{<br>"uuid": "E79A32A7-DE8F-4406-B3C7-7FF0A84B",<br>"name": "foristio",<br>"region": "local",<br>"port": "6443",<br>"availableZoneId": "6a08e460-0a6f-4d69-9cc3-aab3fcec0076",<br>"availableZone": "cas228",<br>"serviceNet": "10.100.0.0/16",<br>"podNet": "10.240.0.0/12",<br>"master": "10.125.41.71",<br>"status": "running",<br>"description": "kaas for istio",<br>"createTime": 1635483419000,<br>"networkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"vpc": "",<br>"ioType": "flannel",<br>"networkName": "PaaS31185",<br>"version": "1.18.9",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"type": "virtual",<br>"creatorName": "admin",<br>"token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IndzbGZRcWV6OER6YlpCU0FBSzVwM3ZvRHd1RUV1SG9IbXFyMU5rblRBaHMifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrYWFzLXN5c3RlbSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJrYWFzLWFkbWluLXRva2VuLTlodzhnIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImthYXMtYWRtaW4iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiJlNTUwYzUzMi0zZDkwLTQ5MzctOTBhZS1hMWUzMDUxZTg0MmUiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a2Fhcy1zeXN0ZW06a2Fhcy1hZG1pbiJ9.GujxZ6Kr0QPwpqy4CuCz8-OxlEyP2UnpIS61t5VWUzpQ4EoIYjE10-6BnpEb2Rp7j9z1W_dr86xbncCusFfkGrns7rYFK4qW2uGPGzzNBVJnVHRU087zeuvqwDEi-asouIUNtTkjPugxnjvEuULdMhXXvd5C7sYczuj8El326gBmEJ_RenmbIERZGygNliMGDckqTtGGBjcuvNDfKKhPsi3EPsAupgB475g4InC5k6dDoN1JBzzgClkPjLIKxqDr0MiP9KlKk-lw0QASOUauL8WBmwRw7MByVnqmGiZ7rK917Xh3mfU-nHhP6Lt2DH9-iQAIGjWETk7LweRjzxi4ag",<br>"vip": "10.125.41.71",<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenantName": "admin",<br>"releaseId": "r-i198kpyv,r-ovfys3fu,r-zo7cf5q3",<br>"subnetInfo": "subnet(10.125.41.0/24)",<br>"subnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"floatNetworkId": "0a20f762-9b3b-490a-b9aa-beafcbc5da14",<br>"loadBalance": true,<br>"businessNetworkId": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"businessNetworkName": "PaaS31185",<br>"businessSubnetId": "6a6c5ea3-7dd8-443e-a8c2-787c8d4e8ef7",<br>"businessSubnetInfo": "subnet(10.125.41.0/24)",<br>"businessMaster": "10.125.41.71",<br>"businessVip": "10.125.41.71",<br>"nodeNum": 3,<br>"masterIps": "{\"10.125.41.71\":\"10.125.41.71\"}",<br>"sans": "",<br>"multiNetwork": false,<br>"defaultGateway": "d3a06a89-319d-4b9e-85d7-4e3ce1743fec",<br>"totalWorker": 2,<br>"avilableWorker": 2,<br>"core": "16.0",<br>"memory": "31.2G",<br>"multiTenantNet": false,<br>"newestVersion": true,<br>"process": true<br>}<br>],<br>"status": true<br>\} |  |  |  |

#### 独享集群工作节点列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/svcgw/v1.0/kaas_clusters/{uuid\} | JSON | GET | 独享集群工作节点列表 |

###### 参数说明

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 重启错误信息 | 正常成功情况下，data为null |
| 例如：<br>{<br>"msg": "",<br>"total": 3,<br>"code": "",<br>"data": [<br>{<br>"uuid": "0aa46a55-8f74-4b3e-9ea9-d2e5f76d5202",<br>"name": "worker-a2328-1",<br>"clusterId": "60AF496A-1A40-4561-8AC9-30375C4B",<br>"ip": "10.125.42.19",<br>"businessIp": "10.125.42.19/24",<br>"type": "virtual",<br>"releaseId": "r-ezwr4kc1,r-vxw3q960",<br>"createTime": "2021-05-18T12:31:03.000+0000",<br>"nodeType": "worker",<br>"status": "running",<br>"username": "root",<br>"password": "*****",<br>"cpu": "4",<br>"memory": "8",<br>"disk": "100",<br>"totalCPU": "4.0",<br>"userCPU": "0.17",<br>"totalMemory": "7.78G",<br>"usedMemory": "2.26 GB",<br>"limitPod": "110",<br>"usedPod": "13"<br>},<br>{<br>"uuid": "a6729f66-9347-4669-b039-334fe87f92bd",<br>"name": "worker-a2328-2",<br>"clusterId": "60AF496A-1A40-4561-8AC9-30375C4B",<br>"ip": "10.125.42.164",<br>"businessIp": "10.125.42.164/24",<br>"type": "virtual",<br>"releaseId": "r-ezwr4kc1,r-vxw3q960",<br>"createTime": "2021-05-18T12:31:03.000+0000",<br>"nodeType": "worker",<br>"status": "running",<br>"username": "root",<br>"password": "*****",<br>"cpu": "4",<br>"memory": "8",<br>"disk": "100",<br>"totalCPU": "4.0",<br>"userCPU": "0.32",<br>"totalMemory": "7.78G",<br>"usedMemory": "2.15 GB",<br>"limitPod": "110",<br>"usedPod": "14"<br>},<br>{<br>"uuid": "af24fcdc-c58b-4edb-94a6-518ff861863c",<br>"name": "worker-a2328-3",<br>"clusterId": "60AF496A-1A40-4561-8AC9-30375C4B",<br>"ip": "10.125.42.102",<br>"businessIp": "10.125.42.102/24",<br>"type": "virtual",<br>"releaseId": "r-ezwr4kc1,r-vxw3q960",<br>"createTime": "2021-05-18T12:31:03.000+0000",<br>"nodeType": "worker",<br>"status": "running",<br>"username": "root",<br>"password": "*****",<br>"cpu": "4",<br>"memory": "8",<br>"disk": "100",<br>"totalCPU": "4.0",<br>"userCPU": "0.24",<br>"totalMemory": "7.78G",<br>"usedMemory": "2.82 GB",<br>"limitPod": "110",<br>"usedPod": "16"<br>}<br>],<br>"status": true<br>\} |  |  |  |

#### 编辑服务网关

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications | JSON | PUT | 编辑服务网关 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | True | String | 实例id |
| description | True | String | 描述 |
| name | True | String | 实例名称 |
| 例如：<br>{<br>"uuid": "5066c779-02a0-47f5-b0f0-359e7db29b86",<br>"name": "instance-1",<br>"description": "test"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 错误信息 | 正常成功情况下，data为null |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 升级服务网关实例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications/{id}/update | JSON | POST | 升级服务网关实例 |

###### 参数说明

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 重启错误信息 | 正常成功情况下，data为null |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 重启服务网关实例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications/{id}/restart | JSON | POST | 重启服务网关实例 |

###### 参数说明

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 重启错误信息 | 正常成功情况下，data为null |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 服务网关容器实例数扩缩容

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications/{id}/replicas | JSON | POST | 服务网关容器实例数扩缩容 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"kongIp": "10.125.29.86/22,10.125.29.87/22",<br>"kongHttpProxyPort": "48000,48000",<br>"kongHttpsProxyPort": "48443,48443"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除错误消息 | 正常成功情况下，data为null |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 服务网关容器规格扩缩容

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications/{id}/resize | JSON | POST | 服务网关容器规格扩缩容 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| {<br>"limits": {<br>"cpu": "2",<br>"memory": "3Gi"<br>},<br>"requests": {<br>"cpu": "0.5",<br>"memory": "0.5Gi"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除错误消息 | 正常成功情况下，data为null |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 申请服务网关容器实例数扩缩容

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications/{id}/replicas/bpm | JSON | POST | 申请服务网关容器实例数扩缩容 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"kongIp": "10.125.29.86/22,10.125.29.87/22",<br>"kongHttpProxyPort": "48000,48000",<br>"kongHttpsProxyPort": "48443,48443"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除错误消息 | 正常成功情况下，data为null |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null<br>\} |  |  |  |

#### 申请服务网关容器规格扩缩容

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/svcgw/v1.0/applications/{id}/resize/bpm | JSON | POST | 申请服务网关容器规格扩缩容 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"limits": {<br>"cpu": "2",<br>"memory": "2Gi"<br>},<br>"requests": {<br>"cpu": "0.5",<br>"memory": "0.5Gi"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除错误消息 | 正常成功情况下，data为null |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null<br>\} |  |  |  |

#### 删除服务网关实例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/svcgw/v1.0/applications/{id}/delete | JSON | POST | 删除服务网关实例 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除失败信息 | 正常成功情况下，data为null |
| status | Boolean | 状态 | true为成功 |
| {<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 新建服务网关参数校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/svcgw/v1.0/parameter/check | JSON | POST | 新建服务网关参数校验 |

###### 参数说明

无

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 服务网关实例名称 |
| kongIp | True | String | kong实例IP |
| kongHttpProxyPort | True | Integer | kong实例http端口 |
| kongHttpsProxyPort | True | Integer | kong实例https端口 |
| zoneId | True | String | 可用域ID |
| 例如：<br>{<br>"kongIp": "10.125.105.123/24",<br>"kongHttpProxyPort": 48000,<br>"kongHttpsProxyPort": 48443,<br>"name": "testA",<br>"zoneId": "8f5f14a1-9"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除失败信息 | 正常成功情况下，data为null |
| status | Boolean | 状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": "",<br>"status": true<br>\} |  |  |  |

### 分布式任务管理

#### 分布式任务创建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/jobinfo/add | application/x-www-form-urlencoded | POST |  |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| jobGroup | true | integer | 执行器id |
| jobDesc | true | String | 任务描述 |
| jobCron | true | String | Cron表达式 |
| executorRouteStrategy | true | String | 路由策略 |
| glueType | true | String | 运行模式 |
| executorBlockStrategy | true | String | 阻塞处理策略 |
| executorHandler | False | String | Bean模式JobHandler |
| executorFailRetryCount | False | Integer | 重试次数 |
| childJobId | False | String | 子任务id |
| executorTimeout | False | Integer | 超时时间（单位秒） |
| alarmEmail | False | String | 告警邮箱 |
| executorParam | False | String | 任务执行参数 |
| glueSource | true | String | Glue模式初始化源码 |
| glueRemark | true | String | Glue模式初始化标签 |
| cronGen_display | true | String | Cron表达式 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 创建失败信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式任务列表查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/ jobinfo/pageList | JSON | GET | 获取分布式任务列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回所有分布式任务信息 |
| status | Boolean | 返回状态 | 接口响应状态 |
| total | Integer | 无实际含义 | 通用返回 |
| {<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"id": 3,<br>"jobGroup": 20,<br>"jobCron": "* * 0/1 * * ?",<br>"jobDesc": "testshell",<br>"addTime": 1584534483000,<br>"updateTime": 1584536575000,<br>"author": "admin",<br>"alarmEmail": "",<br>"executorRouteStrategy": "FIRST",<br>"executorHandler": "",<br>"executorParam": "test",<br>"executorBlockStrategy": "SERIAL_EXECUTION",<br>"executorTimeout": 0,<br>"executorFailRetryCount": 0,<br>"glueType": "GLUE_SHELL",<br>"glueSource": "#!/bin/bash \necho \"xxl-job: hello shell\" \n\necho \"脚本位置：$0\" \necho \"任务参数：$1\" \necho \"分片序号 = $2\" \necho \"分片总数 = $3\" \n\necho \"Good bye!\" \nexit 0",<br>"glueRemark": "GLUE代码初始化",<br>"glueUpdatetime": 1584534483000,<br>"childJobId": "",<br>"triggerStatus": 0,<br>"triggerLastTime": 0,<br>"triggerNextTime": 0<br>},<br>{<br>"id": 2,<br>"jobGroup": 20,<br>"jobCron": "0,2 * * * * ?",<br>"jobDesc": "222afsafaff",<br>"addTime": 1584520512000,<br>"updateTime": 1584525959000,<br>"author": "admin",<br>"alarmEmail": "",<br>"executorRouteStrategy": "FIRST",<br>"executorHandler": "",<br>"executorParam": "",<br>"executorBlockStrategy": "SERIAL_EXECUTION",<br>"executorTimeout": 0,<br>"executorFailRetryCount": 0,<br>"glueType": "GLUE_GROOVY",<br>"glueSource": "package com.xxl.job.service.handler \r\n\r\nimport com.xxl.job.core.log.XxlJobLogger \r\nimport com.xxl.job.core.biz.model.ReturnT \r\nimport com.xxl.job.core.handler.IJobHandler \r\n\r\npublic class DemoGlueJobHandler extends IJobHandler { \r\n\r\n    @Override \r\n    public ReturnT<String> execute(String param) throws Exception { \r\n        XxlJobLogger.log(\"XXL-JOB, Hello World.\") \r\n        return ReturnT.SUCCESS \r\n} \r\n\r\n}",<br>"glueRemark": "GLUE代码初始化",<br>"glueUpdatetime": 1584520512000,<br>"childJobId": "",<br>"triggerStatus": 1,<br>"triggerLastTime": 1584771482000,<br>"triggerNextTime": 1584771540000<br>}<br>],<br>"status": true<br>\} |  |  |  |

#### 分布式任务更新

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/jobinfo/update | application/x-www-form-urlencoded | PUT | 获取分布式任务更新 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | true | integer | 需要更新的任务id |
| jobGroup | true | integer | 执行器id |
| jobDesc | true | String | 任务描述 |
| jobCron | true | String | Cron表达式 |
| executorRouteStrategy | true | String | 路由策略 |
| glueType | true | String | 运行模式 |
| executorBlockStrategy | true | String | 阻塞处理策略 |
| executorHandler | False | String | Bean模式JobHandler |
| executorFailRetryCount | False | Integer | 重试次数 |
| childJobId | False | String | 子任务id |
| executorTimeout | False | Integer | 超时时间（单位秒） |
| alarmEmail | False | String | 告警邮箱 |
| executorParam | False | String | 任务执行参数 |
| glueSource | true | String | Glue模式初始化源码 |
| glueRemark | true | String | Glue模式初始化标签 |
| cronGen_display | true | String | Cron表达式 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 更新失败信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式任务删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/jobinfo/remove | application/x-www-form-urlencoded | DELETE | 分布式任务删除 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | integer | 删除的任务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 删除失败信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式任务执行

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/jobinfo/trigger | application/x-www-form-urlencoded | PUT | 分布式任务执行 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | integer | 执行的任务id |
| executorParam | False | String | 执行参数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 执行失败信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式任务启动

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/jobinfo/start | application/x-www-form-urlencoded | PUT | 分布式任务启动 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | integer | 启动的任务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 启动失败信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式任务停止

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/jobinfo/stop | application/x-www-form-urlencoded | PUT | 分布式任务停止 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | integer | 停止的任务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 停止失败信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式任务GLUE源码更新

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/jobcode/save | application/x-www-form-urlencoded | PUT | 任务Glue源码更新 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | integer | 停止的任务id |
| glueRemark | True | String | Glue更新标签 |
| glueSource | True | String | Glue更新源码 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 停止失败信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

### 分布式日志管理

#### 分布式日志列表查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/joblogs | JSON | GET | 日志列表查询 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| jobId | True | integer | 任务ID(缺省时为0-全部任务) |
| start | True | integer | 页码（缺省时为0） |
| length | True | integer | 每页条数（缺省时为10） |
| jobGroup | True | integer | 执行器ID(缺省时为0-全部执行器) |
| logStatus | True | integer | 状态(-1-全部,1-成功,2-失败,3-进行中 ) |
| filterTime | True | String | 调度时间 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 所有日志信息 | 正常成功情况下，data为所有日志信息 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"id": 8225,<br>"jobGroup": 20,<br>"jobId": 3,<br>"executorAddress": "10.125.31.173:17109",<br>"executorHandler": "",<br>"executorParam": "",<br>"executorShardingParam": null,<br>"executorFailRetryCount": 0,<br>"triggerTime": 1584773040000,<br>"triggerCode": 200,<br>"triggerMsg": "任务触发类型：手动触发<br>调度机器：10.240.1.135<br>执行器-注册方式：自动注册<br>执行器-地址列表：[10.125.31.173:17109]<br>路由策略：第一个<br>阻塞处理策略：单机串行<br>任务超时时间：0<br>失败重试次数：0<br><br><span style="color:#00c0ef;"> >>>>>>>>>>>触发调度<<<<<<<<<<< </span><br>触发调度：<br>address：10.125.31.173:17109<br>code：200<br>msg：null",<br>"handleTime": 1584773040000,<br>"handleCode": 200,<br>"handleMsg": "",<br>"alarmStatus": 0<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 分布式日志清理

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/joblogs | JSON | DELETE | 日志列表查询 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| jobId | True | integer | 任务ID(缺省时为0-全部任务) |
| jobGroup | True | integer | 执行器ID(缺省时为0-全部执行器) |
| type | True | integer | 调度时间类型(1-一个月之前;2-三个月之前;3-六个月之前;4一年之前;5-一千条以前;6-一万条以前;7-三万条以前;8-十万条以前;9-所有日志) |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 清理失败信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式日志详情查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/joblogs/{logId\} | JSON | GET | 日志列表查询 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | integer | 日志id |
| executorAddress | True | String | 执行器地址 |
| triggerTime | True | integer | 触发时间 |
| fromLineNum | True | integer | 起始行数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 指定id日志详情 | 正常成功情况下，data为日志详情 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"fromLineNum": 1,<br>"toLineNum": 10,<br>"logContent": "2020-03-21 14:44:00 [com.xxl.job.core.thread.JobThread#run]-[124]-[Thread-23] <br>----------- xxl-job job execute start -----------<br>----------- Param:\n2020-03-21 14:44:00 [com.xxl.job.core.handler.impl.ScriptJobHandler#execute]-[81]-[Thread-23] ----------- script file:/data/applogs/xxl-job/jobhandler/gluesource/3_1584534483000.sh -----------\nxxl-job: hello shell\n脚本位置：/data/applogs/xxl-job/jobhandler/gluesource/3_1584534483000.sh\n任务参数：\n分片序号 = 0\n分片总数 = 1\nGood bye!\n2020-03-21 14:44:00 [com.xxl.job.core.thread.JobThread#run]-[164]-[Thread-23] <br>----------- xxl-job job execute end(finish) -----------<br>----------- ReturnT:ReturnT [code=200, msg=null, content=null]\n2020-03-21 14:44:00 [com.xxl.job.core.thread.TriggerCallbackThread#callbackLog]-[190]-[xxl-job, executor TriggerCallbackThread] <br>----------- xxl-job job callback finish.\n",<br>"end": false<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 分布式执行器管理

#### 分布式执行器列表查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/dts/v1.0/jobgroup | JSON | GET | 执行器列表查询 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| title | False | String | 执行器名称 |
| appName | False | String | 执行器appName |
| addressType | True | integer | 注册方式 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 所有执行器 | 正常成功情况下，data为所有执行器详情 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"id": 20,<br>"appName": "executor-meng",<br>"title": "test-meng",<br>"order": 1,<br>"addressType": 0,<br>"addressList": "10.125.31.173:17109",<br>"registryList": [<br>"10.125.31.173:17109"<br>]<br>},<br>{<br>"id": 62,<br>"appName": "fdsdd",<br>"title": "vvdvdv",<br>"order": 1,<br>"addressType": 0,<br>"addressList": null,<br>"registryList": null<br>}<br>],<br>"status": true,<br>"total": 2<br>\} |  |  |  |

#### 分布式执行器添加

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/dts/v1.0/jobgroup | JSON | POST | 执行器添加 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Title | True | String | 执行器名称 |
| appName | True | String | 执行器appName |
| addressType | True | integer | 注册方式 |
| order | True | Integer | 排序 |
| addressList | False | String | 执行器地址（手动方式必填） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 添加执行器错误信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式执行器更新

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/dts/v1.0/jobgroup/{id\} | JSON | PUT | 执行器编辑更新 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Title | True | String | 执行器名称 |
| appName | True | String | 执行器appName |
| addressType | True | integer | 注册方式 |
| order | True | Integer | 排序 |
| addressList | False | String | 执行器地址（手动方式必填） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 更新执行器错误信息 | 正常成功情况下，data为true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":true,"status":true,"total":1\} |  |  |  |

#### 分布式执行器删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/dts/v1.0/jobgroup/{ids\} | JSON | DELETE | 执行器删除 |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| ids | True | Int[] | 执行器ids (多个id用逗号隔开) |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | ArrayList | 删除执行器错误信息 | 正常成功情况下，data为[] |
| status | Boolean | 响应状态信息 | 正常情况为true；失败为false |
| total | Integer | 无实际含义 | 通用返回 |
| 例如：<br>{<br>"msg": "执行器: xxx",<br>"code": "",<br>"data": [],<br>“status”: true;<br>}<br>失败情况：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"id": 20,<br>"status": false,<br>"msg": "拒绝删除，该执行器使用中"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 应用编排服务配置

#### 新建分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/classification | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| parent_id | False | String | 父级分类Id |
| name | True | String | 分类名称 |
| description | False | String | 分类描述 |
| icon | False | String | 分类图标 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 创建分类成功的结果信息 | 创建成功时返回true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 编辑分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/classifications | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| parent_id | False | String | 父级分类Id |
| name | True | String | 分类名称 |
| description | False | String | 分类描述 |
| icon | False | String | 分类图标 |
| uuid | True | String | 分类Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 编辑分类成功的结果信息 | 编辑分类成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/classification/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 分类Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 删除分类成功的结果信息 | 删除分类成功时返回true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 获取资源分类树

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/classification-tree | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 分类树查询成功返回 | 分类树查询成功时返回相关信息 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "2dc7729f-7daf-4ebf-8f92-10eb16f167ed",<br>"name": "企业应用",<br>"icon": "/apply-composer/images/tree/app.svg",<br>"description": "",<br>"properties": null,<br>"id": null,<br>"version": null,<br>"children": [<br>{<br>"uuid": "7cca2be2-6833-11ea-b3bb-0a580af00007",<br>"name": "虚拟机",<br>"icon": "/apply-composer/images/tree/host.svg",<br>"description": "",<br>"properties": null,<br>"id": null,<br>"version": null,<br>"children": [<br>{<br>"uuid": "2e167142-9e93-4464-8616-9c5aef98ff89",<br>"name": {<br>"en": "vm",<br>"zh-CN": "虚拟机"<br>},<br>"icon": "/apply-composer/images/tree/host.svg",<br>"description": {<br>"en": "vm",<br>"zh-CN": "虚拟机资源体"<br>},<br>"properties": {<br>"subnetId": {<br>"allowedValues": [],<br>"default": "",<br>"necessary": true,<br>"minLength": "1",<br>"label": {<br>"en": "subnetId",<br>"zh-CN": "子网Id"<br>},<br>"constraintDescription": {<br>"en": "only allowed lowercase, capital letters, numbers and character-./: ",<br>"zh-CN": "只允许英文小写、大写字母与数字及符号-./:"<br>},<br>"type": "STRING",<br>"allowedPattern": "[a-zA-z0-9-./:]{1,64}",<br>"maxLength": "64",<br>"order": 2<br>},<br>"instanceId": {<br>"allowedValues": [],<br>"default": "",<br>"necessary": true,<br>"minLength": "1",<br>"label": {<br>"en": "instanceId",<br>"zh-CN": "虚拟机Id"<br>},<br>"constraintDescription": {<br>"en": "only allowed lowercase, capital letters, numbers and character-./: ",<br>"zh-CN": "只允许英文小写、大写字母与数字及符号-./:"<br>},<br>"type": "STRING",<br>"event": {<br>},<br>"allowedPattern": "[a-zA-z0-9-./:]{1,64}",<br>"maxLength": "64",<br>"order": 3<br>}<br>}<br>"id": "H3C.APP.INSTANCE",<br>"version": 1.0,<br>"children": null,<br>"supplier": "H3C",<br>"fulfillment": {<br>"transfer": "",<br>"create": "",<br>"resize": "",<br>"renew": ""<br>},<br>"request": {},<br>"alias": {<br>"en": "vm",<br>"zh-CN": "虚拟机"<br>},<br>"status": true,<br>"image": "/apply-composer/images/topo/host.png",<br>"dependency": [<br>"*"<br>],<br>"parent_id": "7cca2be2-6833-11ea-b3bb-0a580af00007",<br>"created_at": "2020-02-19 11:14:38",<br>"updated_at": "2020-03-18 16:56:12",<br>"isGroup": false<br>}<br>],<br>"supplier": null,<br>"fulfillment": null,<br>"request": null,<br>"alias": null,<br>"status": null,<br>"image": null,<br>"dependency": null,<br>"parent_id": "2dc7729f-7daf-4ebf-8f92-10eb16f167ed",<br>"created_at": "2020-01-22 16:52:12",<br>"updated_at": "2020-02-07 10:41:35",<br>"isGroup": null<br>}<br>],<br>"parent_id": "",<br>"created_at": "2020-01-16 10:53:40",<br>"updated_at": "2020-02-07 00:09:39",<br>"isGroup": null<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 资源Id验重接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/check/resource | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 资源Id |
| uuid | False | String | 资源的UUid，更新时候验重必填 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 验重成功的结果信息 | 验重资源Id成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,//true表示重复，false表示不重复<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 新建资源

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/resource | JSON | POST | 新建资源 |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| alias | True | Object | 资源别名，国际化格式 |
| classification_id | True | String | 所属分类Id |
| description | False | Object | 资源描述，国际化格式 |
| icon | False | String | 资源图标 |
| id | True | String | 资源的ID |
| image | False | String | 资源缩略图 |
| resource_body | Ture | JSON | 资源模板数据 |
| status | True | Boolean | 资源状态，是否上线 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 创建资源成功的结果信息 | 创建资源成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 资源图标上传

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/resource-photo-upload | Form-Data | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| photo | True | binary | 上传图片 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 图标上传成功的结果信息 | 图片url地址 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": "/api/cloud/orch/v1/resource-photo/33bcd78a-3a45-4bf4-8d97-7026132af14d",<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 更新资源

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/resource | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| alias | True | Object | 资源别名，国际化格式 |
| classification_id | True | String | 所属分类Id |
| description | False | Object | 资源描述，国际化格式 |
| icon | False | String | 资源图标 |
| Id | True | String | 资源id |
| image | False | String | 资源缩略图 |
| resource_body | True | JSON | 资源模板数据 |
| status | True | Boolean | 资源状态 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Map | 编辑成功的结果信息 | 编辑资源成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 上架资源

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/putaway/resource | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| status | True | Boolean | 资源上架状态 |
| uuid | True | String | 资源uuid |
| id | True | String | 资源Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 资源上架成功的结果信息 | 资源上架成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 下架资源

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/soldout/resource | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| status | True | Boolean | 资源状态 |
| uuid | True | String | 资源uuid |
| id | True | String | 资源Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 下架资源成功的结果信息 | 下架资源成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除资源

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/resource/{uuid\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | True | String | 资源uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 资源删除成功的结果信息 | 资源删除成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 批量删除资源

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/resources | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| ids | True | ArrayList | 资源uuid集合 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 资源删除成功的结果信息 | 资源删除成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"error": [],//删除失败的资源对象集合<br>"success": [<br>"H3C.AAA.TST"<br>] //删除成功的资源对象集合<br>},<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询资源列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/resources-list | JSON | GET |  |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| alias | False | Object | 资源别名 |
| status | False | Boolean | 资源状态 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 查询资源列表成功的结果信息 | 查询资源列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "54e65dd3-afe5-4368-984b-0a9d891578e9",<br>"id": "H3C.APP.CDC",<br>"version": 1.0,<br>"name": {<br>"en": "aaa",<br>"zh-CN": "test"<br>},<br>"dependency": null,<br>"description": {<br>"en": "",<br>"zh-CN": ""<br>},<br>"attribute": null,<br>"properties": null,<br>"format": null,<br>"fulfillment": null,<br>"request": null,<br>"response": null,<br>"supplier": null,<br>"image": "",<br>"icon": "",<br>"status": false,<br>"alias": {<br>"en": "aaa",<br>"zh-CN": "test"<br>},<br>"isGroup": null,<br>"classification_id": "6010af05-12e3-4be2-9fc4-93e17df7b882",<br>"created_at": "2020-03-19 09:05:24",<br>"updated_at": "2020-03-20 09:36:41"<br>}<br>],<br>"status": true,<br>"total": 16<br>\} |  |  |  |

#### 校验资源被模板使用情况

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/used/resource/{id\} | JSON | GET |  |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 资源id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | ArrayList | 查询使用该资源的模板名称 | 查询使用该资源的模板名称 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{"msg":"","code":"","data":["muban1"],"status":true,"total":1\} |  |  |  |

### 应用编排我的模板&公有模板

#### 创建模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/templates | JSON | POST |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

| 参数名 | 参数类型 | 必选 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 模板名称 |
| version | String | 是 | 模板版本 |
| description | String | 否 | 模板描述 |
| template_type | Boolean | 是 | 模板类型，公有（true）/私有（false） |
| supplier | String | 是 | 模板拥有者 |
| template_body | JSON | 是 | 模板数据体 |
| 例如：<br>{<br>"description": "helm package deployed in cluster",<br>"name": "Helm包部署集群",<br>"template_type": false,<br>"template_body":{<br>"outputs": {},<br>"resources": {<br>"group_5866": {<br>"id": "e8cb0086-df1f-43ad-b2ea-b79a62c50bd0",<br>"type": "DEFAULT.APP.GROUP",<br>"request": "create",<br>"description": "",<br>"properties": {<br>"svc_engine_id": {<br>"GET::Attribute": "istio_5629"<br>},<br>"name": "xx",<br>"type": {<br>"GET::Attribute": "istio_5629"<br>}<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>410,<br>250<br>],<br>"size": [<br>300,<br>200<br>]<br>},<br>"istio_5629": {<br>"id": "1fa0b84e-ad31-4ee6-8f2e-5683ab9fd770",<br>"type": "DEFAULT.APP.ISTIO",<br>"request": "create",<br>"properties": {<br>"svc_engine_id": "",<br>"type": "Istio"<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>223,<br>371<br>]<br>}<br>},<br>"parameters": {}<br>}},<br>"supplier": "H3C.APP",<br>"version": "1.0"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 创建模板结果信息 | 创建模板成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>created_at: null<br>description: ""<br>id: "df4310ed-3f0a-4330-aabd-66ebc25d5830"<br>name: "xx1"<br>supplier: "admin"<br>template_body: {outputs: {}, resources: {,…}, parameters: {}}<br>template_type: false<br>updated_at: null<br>user_id: "1df71957d770436493a84f24d9344853"<br>version: "1.0"<br>},<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 编辑模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api /cloud/orch/v1/templates | JSON | PUT |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

| 参数名 | 参数类型 | 必选 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 模板名称 |
| version | String | 是 | 模板版本 |
| description | String | 否 | 模板描述 |
| template_type | Boolean | 是 | 模板类型，公有（true）/私有（false） |
| supplier | String | 是 | 模板拥有者 |
| template_body | JSON | 是 | 模板数据体 |
| Id | String | 是 | 模板id |
| 例如：<br>{<br>"id":"27a84507-51f9-42d8-9aa5-36ce33cf981a",<br>"description": "condition test  17",<br>"name": "Condition17",<br>"template_type": false,<br>"template_body":{<br>"outputs": {},<br>"resources": {<br>"group_5866": {<br>"id": "e8cb0086-df1f-43ad-b2ea-b79a62c50bd0",<br>"type": "DEFAULT.APP.GROUP",<br>"request": "create",<br>"description": "",<br>"properties": {<br>"svc_engine_id": {<br>"GET::Attribute": "istio_5629"<br>},<br>"name": "xx",<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>410,<br>250<br>],<br>"size": [<br>300,<br>200<br>]<br>},<br>"istio_5629": {<br>"id": "1fa0b84e-ad31-4ee6-8f2e-5683ab9fd770",<br>"type": "DEFAULT.APP.ISTIO",<br>"request": "create",<br>"properties": {<br>"svc_engine_id": "",<br>"type": "Istio"<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>223,<br>371<br>]<br>}<br>},<br>"parameters": {}<br>},<br>"supplier": "H3C.APP",<br>"version": "1.0"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 编辑模板结果信息 | 编辑模板成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": true,<br>"status": true<br>\} |  |  |  |

#### 删除模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/templates/single/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | ture | String | 模板的id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 删除模板成功结果信息 | 删除模板成功时返回true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": true,<br>"status": true<br>\} |  |  |  |

#### 批量删除模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/templates/{ids\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| ids | True | String | 模板Id集合,id以逗号分隔,例如35f9273f-53cc-4193-8653-d99c0997245e,18624534-4d14-4b37-8a96-c129a2919e1d |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 删除模板结果信息 | 删除模板成功时返回true |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": true,<br>"status": true<br>\} |  |  |  |

#### 查询模板列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/templates | JSON | GET |  |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| name | False | String | 模板名称 |
| version | False | String | 模板版本 |
| supplier | False | String | 发布者 |
| description | False | String | 描述 |
| templateType | False | Boolean | 公私有 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 查询模板列表结果信息 | 查询模板列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [{<br>"id": "8d3705d6-2968-48ff-8dfb-6a65807b372b",<br>"version": "v4",<br>"supplier": "admin",<br>"name": "jar1112",<br>"description": "",<br>"template_type": false,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"created_at": "2020-03-20 10:18:06",<br>"updated_at": "2020-03-20 10:18:06",<br>"template_body": {<br>"outputs": {},<br>"resources": {<br>"jar_4395": {<br>"id": "196de809-2e08-49fa-9263-3bf423194f50",<br>"type": "H3C.APP.JAR",<br>"request": "create",<br>"description": "",<br>"properties": {<br>"package": "false",<br>"packageId": "179d74d2-134d-4539-9c32-05de44ce9d1c",<br>"containerEnv": [<br>{<br>"name": "test",<br>"value": "test"<br>}<br>],<br>"version_id": "hPryRhyxqb",<br>"jdk_version": "1.8",<br>"env": {},<br>"type": "jar",<br>"image_metadata": {<br>"GET::Attribute": "container_9138"<br>},<br>"vm_metadata": {<br>"instances": [],<br>"port": 80,<br>"accessMode": "none"<br>},<br>"azone_id": {<br>"GET::Attribute": "container_9138"<br>},<br>"jvm_args": "",<br>"apm": "false",<br>"resourceType": {<br>"GET::Attribute": "container_9138"<br>}<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>499,<br>189<br>]<br>}<br>"container_9138": {<br>"id": "5eb6fd56-aead-4314-95e3-e3357c55396b",<br>"type": "H3C.APP.CONTAINER",<br>"request": "create",<br>"description": "",<br>"properties": {<br>"request_memory": "100Mi",<br>"request_cpu": "0.1",<br>"requirePodAffinityRules": [],<br>"command": [],<br>"args": [],<br>"perferPodAntiAffinityRules": [],<br>"image_metadata": {},<br>"perferPodAffinityRules": [],<br>"security": {<br>"privileged": true,<br>"runAsUser": "",<br>"capabilities": [],<br>"hostNetwork": false,<br>"hostIPC": false,<br>"hostPID": false,<br>"readOnlyRootFilesystem": false<br>},<br>"limit_memory": "500Mi",<br>"service": {<br>"GET::Attribute": "service_3810"<br>},<br>"requirePodAntiAffinityRules": [],<br>"limit_cpu": "0.4",<br>"azone_id": "default",<br>"resourceType": "container"<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>406,<br>239<br>]<br>},<br>"application_8683": {<br>"id": "d4671860-b454-4f8c-9c53-4885deb62b91",<br>"type": "H3C.APP.APPLICATION",<br>"request": "create",<br>"description": "",<br>"properties": {<br>"appgroup_uuid": {<br>"GET::Attribute": "group_5194"<br>},<br>"cluster_id": "",<br>"metadata": {<br>"GET::Attribute": "jar_4395"<br>},<br>"package": {<br>"GET::Attribute": "jar_4395"<br>},<br>"namespace": {<br>"GET::Attribute": "group_5194"<br>},<br>"name": {<br>"GET::Parameters": "name"<br>},<br>"resource_type": {<br>"GET::Attribute": "jar_4395"<br>},<br>"description": "",<br>"azone_id": {<br>"GET::Attribute": "jar_4395"<br>},<br>"package_type": {<br>"GET::Attribute": "jar_4395"<br>}<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>706,<br>248<br>]<br>}<br>},<br>"parameters": {<br>"name": {<br>"label": {<br>"zh-CN": "应用名称",<br>"en": ""<br>},<br>"type": "STRING",<br>"default": "",<br>"unit": "",<br>"necessary": true,<br>"allowedPattern": "",<br>"constraintDescription": {<br>"zh-CN": "",<br>"en": ""<br>},<br>"disable": false,<br>"hide": false,<br>"readOnly": false<br>}<br>}<br>}<br>}],<br>"status": true<br>\} |  |  |  |

#### 查询模板详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/templates/{id\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 模板Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 查询模板详情结果信息 | 查询模板详情成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": {<br>"id": "8d3705d6-2968-48ff-8dfb-6a65807b372b",<br>"version": "v4",<br>"supplier": "admin",<br>"name": "jar1112",<br>"description": "",<br>"template_type": false,<br>"user_id": "1df71957d770436493a84f24d9344853",<br>"created_at": "2020-03-20 10:18:06",<br>"updated_at": "2020-03-20 10:18:06",<br>"template_body": {<br>"outputs": {},<br>"resources": {<br>"jar_4395": {<br>"id": "196de809-2e08-49fa-9263-3bf423194f50",<br>"type": "H3C.APP.JAR",<br>"request": "create",<br>"description": "",<br>"properties": {<br>"package": "false",<br>"packageId": "179d74d2-134d-4539-9c32-05de44ce9d1c",<br>"containerEnv": [<br>{<br>"name": "test",<br>"value": "test"<br>}<br>],<br>"version_id": "hPryRhyxqb",<br>"jdk_version": "1.8",<br>"env": {},<br>"type": "jar",<br>"image_metadata": {<br>"GET::Attribute": "container_9138"<br>},<br>"vm_metadata": {<br>"instances": [],<br>"port": 80,<br>"accessMode": "none"<br>},<br>"azone_id": {<br>"GET::Attribute": "container_9138"<br>},<br>"jvm_args": "",<br>"apm": "false",<br>"resourceType": {<br>"GET::Attribute": "container_9138"<br>}<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>499,<br>189<br>]<br>},<br>"container_9138": {<br>"id": "5eb6fd56-aead-4314-95e3-e3357c55396b",<br>"type": "H3C.APP.CONTAINER",<br>"request": "create",<br>"description": "",<br>"properties": {<br>"request_memory": "100Mi",<br>"request_cpu": "0.1",<br>"requirePodAffinityRules": [],<br>"command": [],<br>"args": [],<br>"perferPodAntiAffinityRules": [],<br>"image_metadata": {},<br>"perferPodAffinityRules": [],<br>"security": {<br>"privileged": true,<br>"runAsUser": "",<br>"capabilities": [],<br>"hostNetwork": false,<br>"hostIPC": false,<br>"hostPID": false,<br>"readOnlyRootFilesystem": false<br>},<br>"limit_memory": "500Mi",<br>"service": {<br>"GET::Attribute": "service_3810"<br>},<br>"requirePodAntiAffinityRules": [],<br>"limit_cpu": "0.4",<br>"azone_id": "default",<br>"resourceType": "container"<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>406,<br>239<br>]<br>},<br>"application_8683": {<br>"id": "d4671860-b454-4f8c-9c53-4885deb62b91",<br>"type": "H3C.APP.APPLICATION",<br>"request": "create",<br>"description": "",<br>"properties": {<br>"appgroup_uuid": {<br>"GET::Attribute": "group_5194"<br>},<br>"cluster_id": "",<br>"metadata": {<br>"GET::Attribute": "jar_4395"<br>},<br>"package": {<br>"GET::Attribute": "jar_4395"<br>},<br>"namespace": {<br>"GET::Attribute": "group_5194"<br>},<br>"name": {<br>"GET::Parameters": "name"<br>},<br>"resource_type": {<br>"GET::Attribute": "jar_4395"<br>},<br>"description": "",<br>"azone_id": {<br>"GET::Attribute": "jar_4395"<br>},<br>"package_type": {<br>"GET::Attribute": "jar_4395"<br>}<br>},<br>"version": 1,<br>"parent": "",<br>"position": [<br>706,<br>248<br>]<br>}<br>},<br>"parameters": {<br>"name": {<br>"label": {<br>"zh-CN": "应用名称",<br>"en": ""<br>},<br>"type": "STRING",<br>"default": "",<br>"unit": "",<br>"necessary": true,<br>"allowedPattern": "",<br>"constraintDescription": {<br>"zh-CN": "",<br>"en": ""<br>},<br>"disable": false,<br>"hide": false,<br>"readOnly": false<br>}<br>}<br>}<br>},<br>"status": true<br>\} |  |  |  |

#### 校验模板重名

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/check/template | JSON | POST |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

| 参数名 | 参数类型 | 必选 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 模板名称 |
| id | String | 否 | 新建时id可不传，编辑时需要传递id. |
| 例如：<br>{ name: "xxxx"，id:"xxxx" \} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Boolean | 校验模板重名结果 | 校验通过与否 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{msg: "", code: "", data: true, status: true, total: 1} 异常：<br>{msg: "", code: "", data: false, status: true, total: 1\} |  |  |  |

#### 查询模板设计树

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/designer-tree | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 查询堆栈设计树结果信息 | 查询堆栈设计树成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": [<br>{<br>"children": [<br>{<br>"children": [],<br>"created_at": "2020-03-26 16:26:33",<br>"description": "软件包555",<br>"icon": "icon",<br>"name": "软件包555",<br>"parent_id": "2dc7729f-7daf-4ebf-8f92-10eb16f167ed",<br>"updated_at": "2020-03-26 16:26:33",<br>"uuid": "b41923eb-d639-4fae-995c-ef2bf3232008"<br>},<br>{<br>"children": [],<br>"created_at": "2020-03-25 15:14:53",<br>"description": "软件包",<br>"icon": "icon",<br>"name": "软件包",<br>"parent_id": "2dc7729f-7daf-4ebf-8f92-10eb16f167ed",<br>"updated_at": "2020-03-25 15:14:53",<br>"uuid": "c9eaa9d8-fdef-49ef-8314-320632c54f51"<br>},<br>{<br>"children": [<br>{<br>"alias": {<br>"en": "Istio",<br>"zh-CN": "Istio111"<br>},<br>"created_at": "2020-03-24 13:39:01",<br>"description": {<br>"en": "Istio微服务引擎资源",<br>"zh-CN": "Istio svc engine resource"<br>},<br>"icon": "",<br>"id": "H3C.APP.ISTIO1",<br>"image": "",<br>"isGroup": false,<br>"name": {<br>"en": "Istio",<br>"zh-CN": "Istio111"<br>},<br>"parent_id": "63025a9e-58d8-11ea-bfbe-0a580af10013",<br>"properties": {<br>"name": {<br>"allowedPattern": "[a-zA-z0-9-./:]{1,64}",<br>"constraintDescription": {<br>"en": "only allowed 1 to 64-bit lowercase, capital letters, numbers and character-./: ",<br>"zh-CN": "只允许英文小写、大写字母与数字及符号-./:"<br>},<br>"default": "",<br>"label": {<br>"en": "svc engine id",<br>"zh-CN": "名称"<br>},<br>"maxLength": "64",<br>"minLength": "1",<br>"tips": {<br>"en": "",<br>"zh-CN": "Istio微服务引擎不需要绑定对应实例"<br>},<br>"type": "STRING"<br>}<br>},<br>"request": {<br>"create": {<br>"properties": {<br>"cluster_id": false,<br>"engineType": true,<br>"name": true,<br>"system": true,<br>"version": true,<br>"zone": true,<br>"zoneId": true<br>},<br>"response": {<br>"name": true,<br>"project": true<br>}<br>}<br>},<br>"status": true,<br>"supplier": "H3C",<br>"updated_at": "2020-03-26 15:11:12",<br>"uuid": "11e98771-6a11-48be-9259-9cd8932be37b",<br>"version": 1.0<br>},<br>{<br>"alias": {<br>"en": "Istio",<br>"zh-CN": "Istio"<br>},<br>"created_at": "2020-02-19 14:52:14",<br>"description": {<br>"en": "Istio svc engine resource",<br>"zh-CN": "Istio微服务引擎资源"<br>},<br>"icon": "/apply-composer/images/tree/istio.svg",<br>"id": "DEFAULT.APP.ISTIO",<br>"image": "/apply-composer/images/topo/istio.png",<br>"isGroup": false,<br>"name": {<br>"en": "Istio",<br>"zh-CN": "Istio"<br>},<br>"parent_id": "63025a9e-58d8-11ea-bfbe-0a580af10013",<br>"peculiarity": [<br>"!feature_arm"<br>],<br>"properties": {<br>"type": {<br>"allowedPattern": "[a-zA-z0-9-./:]{1,64}",<br>"constraintDescription": {<br>"en": "only allowed 1 to 64-bit lowercase, capital letters, numbers and character-./: ",<br>"zh-CN": "只允许英文小写、大写字母与数字及符号-./:"<br>},<br>"default": "Istio",<br>"hide": true,<br>"label": {<br>"en": "svc engine type",<br>"zh-CN": "微服务引擎类型"<br>},<br>"maxLength": "64",<br>"minLength": "1",<br>"type": "STRING"<br>}<br>},<br>"request": {},<br>"status": true,<br>"supplier": "DEFAULT",<br>"updated_at": "2020-03-18 16:55:47",<br>"uuid": "2dc94c57-461d-4d6a-a066-4c19ffe66935",<br>"version": 1.0<br>},<br>{<br>"alias": {<br>"en": "Spring Cloud",<br>"zh-CN": "Spring Cloud"<br>},<br>"created_at": "2020-02-19 14:52:20",<br>"description": {<br>"en": "spring cloud svc engine resource",<br>"zh-CN": "Spring Cloud微服务引擎资源"<br>},<br>"icon": "/apply-composer/images/tree/springCloud.svg",<br>"id": "DEFAULT.APP.SPRINGCLOUD",<br>"image": "/apply-composer/images/topo/springCloud.png",<br>"isGroup": false,<br>"name": {<br>"en": "Spring Cloud",<br>"zh-CN": "Spring Cloud"<br>},<br>"parent_id": "63025a9e-58d8-11ea-bfbe-0a580af10013",<br>"properties": {<br>"type": {<br>"allowedPattern": "",<br>"constraintDescription": {<br>"en": "",<br>"zh-CN": ""<br>},<br>"default": "Spring Cloud",<br>"hide": true,<br>"label": {<br>"en": "svc engine type",<br>"zh-CN": "微服务引擎类型"<br>},<br>"maxLength": "64",<br>"minLength": "1",<br>"type": "STRING"<br>}<br>},<br>"request": {},<br>"status": true,<br>"supplier": "DEFAULT",<br>"updated_at": "2020-03-18 16:57:23",<br>"uuid": "3aee7fe1-7f85-49aa-8436-5878be74b1c6",<br>"version": 1.0<br>}<br>],<br>"created_at": "2020-01-22 16:52:12",<br>"description": "",<br>"icon": "/apply-composer/images/tree/microService.svg",<br>"name": "微服务引擎",<br>"parent_id": "2dc7729f-7daf-4ebf-8f92-10eb16f167ed",<br>"updated_at": "2020-02-07 10:41:35",<br>"uuid": "63025a9e-58d8-11ea-bfbe-0a580af10013"<br>}<br>],<br>"created_at": "2020-01-16 10:53:40",<br>"description": "",<br>"icon": "/apply-composer/images/tree/app.svg",<br>"name": "企业应用",<br>"parent_id": "",<br>"updated_at": "2020-02-07 00:09:39",<br>"uuid": "2dc7729f-7daf-4ebf-8f92-10eb16f167ed"<br>}<br>],<br>"msg": "",<br>"status": true<br>\} |  |  |  |

### 应用编排我的堆栈

#### 创建堆栈

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/stacks | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| basicInfo | True | JSON | 堆栈基础信息，包括name, template_id,description, timeout等 |
| parameters | True | JSON | 堆栈入参(根据模板设定参数的不同而不同) |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 堆栈执行成功的结果信息 | 堆栈执行成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除单个堆栈

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/stacks/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 堆栈Id集合 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 堆栈删除成功结果信息 | 堆栈删除成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 批量删除堆栈

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/stacks | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| ids | True | ArrayList | 堆栈Id集合 |
| 例如：<br>{"ids":["e19c0d59-ded8-48aa-9e2d-ae9953fe285e","6de1c794-9141-4624-8fbc-fe04ea328f67"]\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 堆栈删除成功结果信息 | 堆栈删除成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": true,<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询堆栈列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/stacks | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| status | False | String | 堆栈状态，可选值SUCCESS/RUNNING/FAILED/EXCEPTION |
| name | False | String | 堆栈名称 |
| description | False | String | 堆栈描述 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 查询堆栈列表结果信息 | 查询堆栈列表成功时返回 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": [<br>{<br>"created_at": 1584584467000,<br>"description": "",<br>"id": "01af52cb-1179-4c39-9b23-d7b5104caa52",<br>"last_exec_time": 1584587882000,<br>"name": "spring175222",<br>"status": "SUCCESS",<br>"template_body": "",<br>"template_id": "2fee8446-1b12-4609-b709-097ff3cbc5a2",<br>"template_info": "ambitionch:1.0",<br>"timeout": 0,<br>"updated_at": 1584584474000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1585049228000,<br>"description": "",<br>"id": "01e308d0-0363-4eb5-9aae-748cef43d085",<br>"last_exec_time": 1585052666000,<br>"name": "spring1709",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_id": "1b47b01e-0ae7-4e71-9844-e4313f7003c0",<br>"template_info": "warVm1632:v1",<br>"timeout": 0,<br>"updated_at": 1585049308000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1585197437000,<br>"description": "",<br>"id": "02590890-e696-4810-b5cd-cb5d816d351f",<br>"last_exec_time": 1585200881000,<br>"name": "outtime1",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_id": "d4ae3c49-e53e-41e7-bb98-6abf6f2ed492",<br>"template_info": "templateouttime:1.0",<br>"timeout": 0,<br>"updated_at": 1585197449000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1585034167000,<br>"description": "",<br>"id": "03285ea6-02d8-455b-85b3-b2b9f747a5c8",<br>"last_exec_time": 1585037603000,<br>"name": "outtime1",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_id": "d4ae3c49-e53e-41e7-bb98-6abf6f2ed492",<br>"template_info": "templateouttime:1.0",<br>"timeout": 0,<br>"updated_at": 1585034720000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1584692303000,<br>"description": "",<br>"id": "05c2a877-8c7a-4eb5-8949-2451f938a1a8",<br>"last_exec_time": 1584695723000,<br>"name": "spring1752",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_id": "088cbf06-8add-4d19-905c-ea916371de51",<br>"template_info": "war1108:v1107",<br>"timeout": 0,<br>"updated_at": 1584692592000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1585206819000,<br>"description": "",<br>"id": "0784a366-70e1-44ec-8447-cf0346446821",<br>"last_exec_time": 1585210264000,<br>"name": "outtime1",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_id": "d4ae3c49-e53e-41e7-bb98-6abf6f2ed492",<br>"template_info": "templateouttime:1.0",<br>"timeout": 0,<br>"updated_at": 1585206872000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1585034151000,<br>"description": "",<br>"id": "08162e62-ca3f-45bf-83dc-99e2c6e82ba4",<br>"last_exec_time": 1585034151000,<br>"name": "war1612",<br>"status": "SUCCESS",<br>"template_body": "",<br>"template_id": "7410d138-74ea-42a4-b7fc-2ed57d0eeb26",<br>"template_info": "warvm1230:v30",<br>"timeout": 0,<br>"updated_at": 1585034152000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1585186996000,<br>"description": "",<br>"id": "08c88c92-8bf5-45d8-83a3-d87ce6840e11",<br>"last_exec_time": 1585190440000,<br>"name": "outtime1",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_id": "d4ae3c49-e53e-41e7-bb98-6abf6f2ed492",<br>"template_info": "templateouttime:1.0",<br>"timeout": 0,<br>"updated_at": 1585187010000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1584177911000,<br>"description": "",<br>"id": "0be1ffcf-4777-4c00-8101-5d65c708f11b",<br>"last_exec_time": 1584181306000,<br>"name": "spring1752",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_id": "3c0333fd-cc75-4ca8-a960-cd2dd16c6c4e",<br>"timeout": 0,<br>"updated_at": 1584177923000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>{<br>"created_at": 1584282270000,<br>"description": "",<br>"id": "0c18244d-baac-4794-90d4-8ffd0cd6963d",<br>"last_exec_time": 1584285669000,<br>"name": "test9",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_id": "c089fe06-87b0-47f5-afae-c0c2e47b07ce",<br>"template_info": "testGroupCreate:1.0",<br>"timeout": 0,<br>"updated_at": 1584283077000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>}<br>],<br>"msg": "",<br>"status": true,<br>"total": 527<br>\} |  |  |  |

#### 查询堆栈详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/stacks/{id\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 堆栈id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 查询堆栈详情结果信息 | 查询堆栈详情成功时返回 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"created_at": 1585049228000,<br>"description": "",<br>"id": "01e308d0-0363-4eb5-9aae-748cef43d085",<br>"last_exec_time": 1585052666000,<br>"name": "spring1709",<br>"status": "EXCEPTION",<br>"template_body": "",<br>"template_info": "warVm1632:v1",<br>"timeout": 0,<br>"updated_at": 1585049308000,<br>"user_id": "1df71957d770436493a84f24d9344853"<br>},<br>"msg": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 校验堆栈名称

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/orch/v1/check/stack | JSON | POST |  |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

| 参数名 | 参数类型 | 必选 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 待校验的模板名称 |
| 例如：<br>{ name: "xxxx"\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Boolean | 校验堆栈重名结果 | 校验通过与否 |
| total | Integer | 无实际含义 | 通用返回 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>正常：<br>{msg: "", code: "", data: true, status: true, total: 1}<br>异常：<br>{msg: "", code: "", data: false, status: true, total: 1\} |  |  |  |

### 应用诊断应用组管理

#### 接入应用组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 请求体

| uuid | true | String | 应用组Id |
| --- | --- | --- | --- |
| name | true | String | 应用组名称 |
| type | ture | Enum(string) | 包括: plat (平台应用) , outside(外部应用) |
| 例如：<br>{<br>"uuid":"32750820-92c8-421d-8a88-8b3cc411f0be",<br>"name":" test-group",<br>"type":" plat"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"uuid": "c060d511-4ce2-43be-9273-83743925f3bc",<br>"name": "test-group",<br>"namespace": "namespace1",<br>"type": " plat",<br>"appNumber": 2,<br>"metadata": null,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createdAt": "2021-10-29 11:38:43",<br>"updatedAt": "2021-10-29 11:38:43"<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用组列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | false | String | 应用组名称 |
| page | true | Integer | 当前页数 |
| pageSize | true | Integer | 每页的数量 |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "32750820-92c8-421d-8a88-8b3cc411f0be",<br>"name": "test-group",<br>"namespace": "namespace1",<br>"type": "type1",<br>"appNumber": 2,<br>"metadata": "metadata",<br>"userId": "userId",<br>"projectId": "projectId",<br>"createdAt": "2021-10-28 15:36:38",<br>"updatedAt": "2021-10-28 15:36:38",<br>"health": 1,<br>"throughput": 2,<br>"success": 3,<br>"p95ResponseTime": 4,<br>"alarmNumber": 0<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除接入的应用组

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups/{appGroupId\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appGroupId | True | String | 应用组Id |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Boolean | 结果信息 | 成功true |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用组下应用概览

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups/statistics | JSON | GET |  |

###### 参数说明

无

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"appGroupNumber": 0,<br>"appNumber": 2,<br>"appNumberError": 0<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用组健康度吞吐量趋势

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups/{appGroupId}/performance | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appGroupId | true | string | 应用组Id |
| startTime | true | Long | 查询开始时间戳 |
| endTime | true | Long | 查询结束时间戳 |
| type | true | Enum(string) | 类型,包括: healthWeight(健康度), throughput(吞吐量) |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"code": null,<br>"msg": null,<br>"data": {<br>"units": "calls/1h",<br>"data": [<br>{<br>"timestamp": 1635756720000,<br>"value": "0"<br>},<br>{<br>"timestamp": 1635756720000,<br>"value": "0"<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用组下应用健康度权重列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups/{appGroupId}/weight | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appGroupId | true | string | 应用组Id |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Boolean | 结果信息 | 成功true |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"uuid": "32750820-92c8-421d-8a88-8b3cc411f0be",<br>"name": "test-group",<br>"namespace": "namespace1",<br>"type": "type1",<br>"appNumber": 2,<br>"metadata": "metadata",<br>"userId": "userId",<br>"projectId": "projectId",<br>"createdAt": "2021-10-28 15:36:38",<br>"updatedAt": "2021-10-28 15:36:38",<br>"applicationList": [<br>{<br>"uuid": "adfc8e20-2742-48ad-8406-5fd2c3972bb0",<br>"name": "test-app",<br>"clusterId": "clusterId-1",<br>"clusterType": "clusterType-1",<br>"appGroupId": "32750820-92c8-421d-8a88-8b3cc411f0be",<br>"resourceType": "resourceType-1",<br>"health": "1",<br>"appType": "appType-1",<br>"metadata": "metadata",<br>"swInstanceId": "swInstanceId-1",<br>"healthWeight": 1,<br>"appEnvId": "appEnvId",<br>"userId": "userId",<br>"projectId": "projectId",<br>"createdAt": "2021-10-28 12:53:37",<br>"updatedAt": "2021-10-28 15:37:49"<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用组健康配置修改

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups/{appGroupId}/health/config | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appGroupId | true | string | 应用组Id |

###### 请求体

| applicationList | true | List<Object> | 应用组下应用健康度配置列表 |
| --- | --- | --- | --- |
| applicationList .uuid | true | String | 应用ID |
| applicationList .healthWeight | ture | Integer | 应用权重 |
| applicationList apdexThreshold | ture | Integer | Apdex 阀值 |
| 例如：<br>{<br>"applicationList": [<br>{<br>"uuid": "8fb18a30f4584166865930f446960eaa",<br>"healthWeight": 2,<br>"apdexThreshold": 288<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Boolean | 结果信息 | 成功true |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": true,<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用组拓扑图

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups/{appGroupId}/topology | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appGroupId | true | string | 应用组Id |
| start | true | Long | 查询开始时间戳 |
| end | true | Long | 查询结束时间戳 |
| retrospectiveTime | true | Enum(string) | 回溯时间,包括:<br>last30Minutes,lastHour,last2Hours,last5Hours,lastDay |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Boolean | 结果信息 | 成功true |
| 例如：<br>{<br>"data": {<br>"edges": [],<br>"nodes": [<br>{<br>"nodeId": "14ce04bb49f94f6b978b594dba984289",<br>"nodeName": "service-x",<br>"type": "component",<br>"level": null,<br>"version": null,<br>"status": "Error",<br>"labelMap": {<br>"container": "Unknown",<br>"instanceNo": "1",<br>"success": "0",<br>"responseTime": "0",<br>"namespace": "space49qvys8a",<br>"health": "100",<br>"throughput": "0",<br>"skywalking": "false"<br>},<br>"children": null,<br>"traffic": null<br>}<br>],<br>"timeStamp": 1635757252<br>},<br>"code": null,<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 应用诊断应用监控

#### 应用列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/appGroups/{appGroupId}/applications | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appGroupId | true | String | 应用组ID |
| page | true | Integer | 当前页数 |
| pageSize | true | Integer | 每页的数量 |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"data": [<br>{<br>"uuid": "5172df9231154700bbe9240078c976c1",<br>"name": "env-app6",<br>"clusterId": "123456",<br>"clusterType": "k8sCluster",<br>"appGroupId": "3dfefb15-4fdf-435c-af6f-015d5d68dfeb",<br>"namespace": "spaced2oy6094",<br>"resourceType": "container",<br>"health": "100",<br>"appType": "",<br>"metadata": {<br>"appEnv": {<br>"id": "398c15c4-dbe2-44db-aa37-2c2d7278d7e6",<br>"resources": [<br>{<br>"id": "default",<br>"name": "default",<br>"params": {},<br>"type": "k8sCluster"<br>}<br>]<br>}<br>},<br>"swInstanceId": "default",<br>"healthWeight": 100,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createdAt": "2022-01-12 15:47:35",<br>"updatedAt": "2022-01-12 17:47:30",<br>"apdexThreshold": 500,<br>"applicationLabel": "spaced2oy6094:env-app6"<br>}<br>],<br>"code": null,<br>"msg": null,<br>"total": 10<br>\} |  |  |  |

#### 应用监控数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/applications/{applicationId}/{metrics\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| applicationId | true | String | 应用ID |
| metrics | true | Enum(string) | 监控类型, 包括: throughput (吞吐量) , successrate (成功率),<br>responsetime(响应时间), avgresponsetime(平均响应时间) |
| start | true | Long | 查询开始时间戳 |
| end | true | Long | 查询结束时间戳 |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"data": {<br>"units": null,<br>"items": [<br>{<br>"name": "techops-other-cluster",<br>"values": [<br>{<br>"timestamp": 1599806280,<br>"value": "100"<br>},<br>{<br>"timestamp": 1599806400,<br>"value": "100"<br>}<br>]<br>}<br>]<br>},<br>"code": null,<br>"msg": null,<br>"status": true,<br>"total": 0<br>\} |  |  |  |

#### 应用链路TopN

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/applications/{applicationId}/traces | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| applicationId | true | String | 应用ID |
| type | true | Enum(string) | 包括: slow (慢链路) , error(错误链路) |
| start | true | Long | 查询开始时间戳 |
| end | true | Long | 查询结束时间戳 |
| size | true | Integer | 查询链路条数 |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"total": 10000,<br>"traces": [<br>{<br>"key": "e58ff12ea83b4236b60730576473fba3.71.16387708047429268",<br>"duration": 692,<br>"start": "1638770804742",<br>"isError": false,<br>"swInstanceId": "cb16e116-df86-40ff-9619-e3eb3fee334b",<br>"endpointNames": [<br>"/actuator/prometheus"<br>],<br>"traceIds": [<br>"e58ff12ea83b4236b60730576473fba3.71.16387708047429269"<br>]<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用接口列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/applications/{applicationId}/endpoints | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| applicationId | true | String | 应用ID |
| sortedsign | true | Enum(string) | 排序指标, 包括: throughput , avgresponsetime, successrate |
| sortedtype | true | Enum(string) | 排序方式, 包括: asc(升序), desc(降序) |
| start | true | Long | 查询开始时间戳 |
| end | true | Long | 查询结束时间戳 |
| size | false | Integer | 查询链路条数 |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"httpMethod": null,<br>"uri": "/books/1/comments",<br>"metrics": [<br>{<br>"key": "avgresponsetime",<br>"value": "0.00"<br>},<br>{<br>"key": "throughput",<br>"value": "20"<br>},<br>{<br>"key": "successrate",<br>"value": "0"<br>}<br>]<br>}<br>],<br>"status": true,<br>"total": 4<br>\} |  |  |  |

#### 应用接口监控

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/applications/{applicationId}/endpoints/{metrics\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| applicationId | true | String | 应用ID |
| metrics | true | Enum(string) | 监控类型, 包括: throughput (吞吐量) , successrate (成功率),<br>responsetime(响应时间), avgresponsetime(平均响应时间) |
| start | true | Long | 查询开始时间戳 |
| end | true | Long | 查询结束时间戳 |
| endpoint | true | String | 接口URI |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"data": {<br>"units": "%",<br>"items": [<br>{<br>"name": "/comments/{typeId}",<br>"values": [<br>{<br>"timestamp": 1635746400,<br>"value": "100.00"<br>}<br>]<br>}<br>]<br>},<br>"code": null,<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

#### 应用实例列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0 /applications/{applicationId}/instances | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| applicationId | true | String | 应用ID |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"name": "devtestwxx-0",<br>"id": ""<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用实例监控

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/applications/{applicationId}/instances/{instanceId}/performance | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| applicationId | true | String | 应用ID |
| instanceId | true | String | 实例ID |
| metrics | true | Enum(string) | 监控数据类型, basics: 基础指标; performance: 性能指标; jvm: jvm |
| metricsType | true | Enum(string) | 监控数据类型下分指标类型:<br>basics基础指标有: cpuUsage(CPU使用);memoryUsage(内存使用量); netTransmit(网络出口流量) ;netReceive(网络入口流量)<br>performance性能指标有:throughput(吞吐量);successRate(成功率);    responseTime(响应时间)<br>jvm: 性能指标有:gcNumberMinor(Minor GC次数);gcNumberFull(Full GC次数);gcTimeMinor(MinorGC时间);gcTimeFull(Full G时间);<br>heapUsage(HeapUsage);nonHeapUsage(Non-HeapUsage);<br>threadLive(JVM Thread Live Count); threadDaemon(JVM Thread Daemon Count) ;threadPeak(JVM Thread Peak Count) |
| start | true | Long | 查询开始时间戳 |
| end | true | Long | 查询结束时间戳 |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"code": null,<br>"msg": null,<br>"data": {<br>"units": "calls/1h",<br>"data": [<br>{<br>"timestamp": 1635756720000,<br>"value": "0"<br>},<br>{<br>"timestamp": 1635756720000,<br>"value": "0"<br>}<br>]<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用实例运行日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/quark/v1.0/applications/{applicationId}/instances/{instanceId}/logs | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| applicationId | true | String | 应用ID |
| instanceId | true | String | 实例ID |
| tailingLines | true | Integer | 查询日志行数 |
| sinceSeconds | true | Integer | 查询最近多少秒的日志 |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| total | Integer | 列表元素数量 | 列表元素数量 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": "loginfo.....",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 应用智慧运维大屏

#### 告警统计

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/alarm/v1.0/alarms/quantities | JSON | GET | 告警统计 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"total": 3, //总数<br>"active": { //未确认<br>"total": 1,<br>"waring": 0, //一般<br>"major": 0, //重要<br>"critical": 1 //紧急<br>},<br>"confirmed": { //已确认<br>"total": 0,<br>"waring": 0,<br>"major": 0,<br>"critical": 0<br>},<br>"restored": { //已恢复<br>"total": 2,<br>"waring": 2,<br>"major": 0,<br>"critical": 0<br>}<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 应用业务系统告警列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/alarm/v1.0/appgroups/alarms/quantities | JSON | GET | 应用业务系统告警列表 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"appGroupId": "4cae9260-b7bc-4163-a4c3-b8bd1ddd15d3",//应用组ID<br>"total": 1,  // 告警总数<br>"active": 1 // 未确认告警数<br>},<br>{<br>"appGroupId": "b9150c52-81fd-419e-9726-e34fdc6b09ae",<br>"total": 1,<br>"active": 0<br>}<br>],<br>"status": true,<br>"total": 2<br>\} |  |  |  |

#### 应用统计

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/quark/v1.0/applications/all | JSON | GET | 应用统计 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [<br>{<br>"uuid": "165cce9279d74a97a1c519d5e9decded",<br>"name": "multi-vm-env-app",<br>"clusterId": null,<br>"clusterType": null,<br>"appGroupId": "4cae9260-b7bc-4163-a4c3-b8bd1ddd15d3",<br>"namespace": "spaceng5svw52",<br>"appGroupName": "sw-test",<br>"appGroupType": "others",<br>"resourceType": "vm",<br>"appType": "Tomcat",<br>"swInstanceId": "default",<br>"metadata": {<br>"appEnv": {<br>"id": "f5da3803-97fe-46c5-af0b-240e8fd07e35",<br>"resources": [<br>{<br>"id": "d8eca485-8888-4589-a371-05a992c24ee9",<br>"name": "PaaS-NEO-2",<br>"params": {},<br>"type": "iaas_platform"<br>},<br>{<br>"id": "kureb3vgnftutxip1pmz",<br>"name": "sp",<br>"params": {<br>"REGISTRY_TYPE": "eureka",<br>"EUREKA_URL": "http://10.125.31.85:32079/eureka,<br>http://10.125.31.83:32079/eureka,http://10.125.31.84:32079/eureka,http://10.125.31.81:32079/eureka",<br>"CONFIG_URL": "http://10.125.31.83:30467,http://10.125.31.81:30467,http://10.125.31.84:30467,http://10.125.31.80:30467"<br>},<br>"type": "Spring Cloud"<br>}<br>]<br>}<br>},<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"health": "100"<br>}<br>],<br>"code": null,<br>"msg": null,<br>"total": 1<br>\} |  |  |  |

#### 虚拟机应用实例数量统计

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/quark/v1.0/vm_instances/statistic | JSON | GET | 虚拟机应用实例的数量统计 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"running": 100,<br>"stopped": 50,<br>"error": 3,<br>"others": 12<br>},<br>"status": true<br>\} |  |  |  |

#### 容器应用实例数量统计

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/quark/v1.0/instances/quantity/statistic | JSON | GET | 容器应用实例数量统计 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

###### 请求体

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"normalQuantity": 100,<br>"abnormalQuantity": 50<br>},<br>"status": true<br>\} |  |  |  |

#### 低健康度topN应用列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/techops/quark/v1.0/applications/topN/lowHealth | JSON | GET | 低健康度topN应用列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| size | true | Integer | topN |
| start | true | Integer | 开始回溯时间戳（毫秒） |
| end | true | Integer | 结束回溯时间戳（毫秒） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"serviceName": "sw66-c-x6",<br>"value": "33.33",<br>"units": "",<br>"appGroupName": "spaceng5svw52"<br>},<br>{<br>"serviceName": "sw-c-x4",<br>"value": "33.33",<br>"units": "",<br>"appGroupName": "spaceng5svw52"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 高错误率topN应用列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/techops/quark/v1.0/applications/topN/errorRate | JSON | GET | 高错误率topN应用列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| size | true | Integer | topN |
| start | true | Integer | 开始回溯时间戳（毫秒） |
| end | true | Integer | 结束回溯时间戳（毫秒） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"serviceName": "sw66-c-x6",<br>"value": "33.34",<br>"units": "%",<br>"appGroupName": "spaceng5svw52"<br>},<br>{<br>"serviceName": "trace-db",<br>"value": "29.42",<br>"units": "%",<br>"appGroupName": "spacecl3574st"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 高响应时间topN应用列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/techops/quark/v1.0/applications/topN/slowResponse | JSON | GET | 高响应时间topN应用列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| size | true | Integer | topN |
| start | true | Integer | 开始回溯时间戳（毫秒） |
| end | true | Integer | 结束回溯时间戳（毫秒） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"serviceName": "sw66-c-x6",<br>"value": "1220",<br>"units": "ms",<br>"appGroupName": "spaceng5svw52"<br>},<br>{<br>"serviceName": "sw-c-x4",<br>"value": "1180",<br>"units": "ms",<br>"appGroupName": "spaceng5svw52"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 高吞吐量topN应用列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/techops/quark/v1.0/applications/topN/throughput | JSON | GET | 高吞吐量topN应用列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| size | true | Integer | topN |
| start | true | Integer | 开始回溯时间戳（毫秒） |
| end | true | Integer | 结束回溯时间戳（毫秒） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| status | Boolean | 请求状态 | true为成功 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"serviceName": "book1",<br>"value": "49",<br>"units": "cpm",<br>"appGroupName": "spaces37mqwfm"<br>},<br>{<br>"serviceName": "trace-db",<br>"value": "34",<br>"units": "cpm",<br>"appGroupName": "spacecl3574st"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 应用诊断链路分析

#### 获取接入链路追踪的应用列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/quark/v1.0/links/{instanceId}/apps | JSON | GET | 获取接入链路追踪的应用列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| instanceId | true | String | 链路追踪实例ID |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | null | null |
| 例如：<br>{<br>"data": [<br>{<br>"id": "4cae9260-b7bc-4163-a4c3-b8bd1ddd15d3",<br>"appGroupName": "sw-test",<br>"namespace": "spaceng5svw52",<br>"apps": [<br>{<br>"id": "53b05e0d568f44d78ef9f53633d14a4b",<br>"serviceName": "sw-c-x4",<br>"appId": "53b05e0d568f44d78ef9f53633d14a4b",<br>"appGroupAppId": "53b05e0d568f44d78ef9f53633d14a4b",<br>"namespace": "spaceng5svw52",<br>"resourceType": "container",<br>"containerType": null,<br>"createAt": null,<br>"clusterId": "123456",<br>"skywalkingInstanceId": "e0723a3c-882a-4fe1-83a7-3a3a24a775ef",<br>"skywalking": true<br>}<br>]<br>}<br>],<br>"code": null,<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

#### 获取应用实例列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/quark/v1.0/links/{instanceId}<br>/apps/{appId}/instances | JSON | GET | 查询应用实例列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| instanceId | true | String | 链路追踪实例ID |
| appId | true | String | 应用ID |
| start | true | Integer | 开始回溯时间戳（毫秒） |
| end | true | Integer | 结束回溯时间戳（毫秒） |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| total | Integer | null | null |
| 例如：<br>{<br>"data": [<br>{<br>"appInstanceId": "533",<br>"appInstanceName": "user-5c77df8954-klsrd"<br>}<br>],<br>"code": null,<br>"msg": null,<br>"total": 1<br>\} |  |  |  |

#### 获取应用接口列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/quark/v1.0/links/{instanceId}<br>/apps/{appId}/endpoints | JSON | GET | 获取链路分析应用接口列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| instanceId | true | String | 链路追踪实例ID |
| appId | true | String | 应用ID |
| start | true | Integer | 开始回溯时间戳（毫秒） |
| end | true | Integer | 结束回溯时间戳（毫秒） |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| total | Integer | null | null |
| 例如：<br>{<br>"data": [<br>"/slowSql",<br>"/err",<br>"/ok"<br>],<br>"code": null,<br>"msg": null,<br>"total": 3<br>\} |  |  |  |

#### 链路列表查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/quark/v1.0/links/{instanceId}/traces | JSON | GET | 获取链路分析应用链路列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| instanceId | true | String | 链路实例ID |
| pageNum | true | String | 页码 |
| pageSize | true | String | 每页数量 |
| instanceVersion | false | String | 链路实例版本 |
| appId | false | String | 应用ID |
| start | true | Integer | 开始回溯时间戳（毫秒） |
| end | true | Integer | 结束回溯时间戳（毫秒） |
| queryOrder | true | String | 排序规则（BY_START_TIME/BY_DURATION：按开始时间排序/按持续时间排序） |
| traceState | false | String | 状态 |
| endpointName | false | String | 接口 |
| minTraceDuration | false | Integer | 最小持续时间 |
| maxTraceDuration | false | Integer | 最大持续时间 |
| traceId | false | String | 追踪ID |
| serviceInstanceId | false | String | 实例ID |
| 例如：<br>/api/cloud/techops/quark/v1.0/links/default/traces? pageNum=1&pageSize=10&instanceId=default&instanceVersion=6.6.0&appId=ALL&start=1642226648255<br>&end=1642230248255&queryOrder=BY_DURATION&traceState=ALL&endpointName=ALL<br>&traceId=&serviceInstanceId=ALL |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| total | Integer | null | null |
| 例如：<br>{<br>"data": [<br>{<br>"key": "356.1.16422271732330002",<br>"duration": 9007,<br>"start": "1642227173233",<br>"isError": false,<br>"swInstanceId": null,<br>"endpointNames": [<br>"/eureka/apps/"<br>],<br>"traceIds": [<br>"356.1.16422271732330003"<br>]<br>}<br>],<br>"code": null,<br>"msg": null,<br>"total": 10000<br>\} |  |  |  |

#### 链路详情查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/techops/quark/v1.0/links/{instanceId}/traces/{traceId\} | JSON | GET | 查询链路详情 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| instanceId | true | String | 链路追踪实例ID |
| traceId | true | String | 追踪ID |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"traceId": "75488f41c7724869a982f4f2f84d9794.46.16422296864248753",<br>"segmentId": "75488f41c7724869a982f4f2f84d9794.46.16422296864248752",<br>"spanId": 0,<br>"parentSpanId": -1,<br>"serviceCode": "space9mqd8y56:book-mysql",<br>"serviceInstanceName": null,<br>"startTime": 1642229686424,<br>"endTime": 1642229686435,<br>"endpointName": "/eureka/apps/BOOK-MYSQL/book-mysql:32100",<br>"type": "Exit",<br>"peer": "10.125.29.149:30600",<br>"component": "HttpClient",<br>"isError": false,<br>"layer": "Http",<br>"refs": [],<br>"tags": [<br>{<br>"key": "url",<br>"value": "http://10.125.29.149:30600/eureka/apps/BOOK-MYSQL/book-mysql:32100?status=UP&lastDirtyTimestamp=1641856848680"<br>},<br>{<br>"key": "http.method",<br>"value": "PUT"<br>}<br>],<br>"logs": [],<br>"children": []<br>},<br>"code": null,<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 应用诊断链路追踪

#### 查询链路追踪实例列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/skymgt/v1.0/applications | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [{<br>"uuid": "99822d76-27bc-404a-8af4-bc650790e9ea",<br>"name": "test-child-cluster",<br>"description": "123",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"serviceType": "skywalking",<br>"serviceVersion": "6.6.0",<br>"esAddress": "10.125.30.240:9200",<br>"status": "RUNNING",<br>"metadata": null,<br>"resourceType": "CONTAINER",<br>"namespace": "skywalkingdd73f598",<br>"releaseName": "skywalking4e8e0162",<br>"svcOapName": "10.125.31.60:32230",<br>"svcUiName": "10.125.31.60:32136",<br>"k8sClusterId": "f6270d76-c",<br>"k8sClusterVip": "10.125.31.60",<br>"createTime": "2021-06-16T08:14:44.000+0000",<br>"updateTime": "2021-06-17T01:48:47.000+0000",<br>"zoneId": "f6270d76-c",<br>"zoneName": "subcluster",<br>"projectName": "root",<br>"userName": "admin",<br>"resource": "{\"limits\":{\"cpu\":\"1\",\"memory\":\"1024Mi\"},\"requests\":{\"cpu\":\"0.5\",\"memory\":\"1024Mi\"}}",<br>"replicas": null,<br>"clusterType": "PUBLIC_ZONE",<br>"clusterPort": "8443",<br>"privateKaasName": null<br>}],<br>"status": true<br>\} |  |  |  |

#### 创建链路分析实例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/skymgt/v1.0/applications | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 名称 |
| description | True | String | 描述 |
| serviceType | True | String | 服务类型，默认值skywalking |
| serviceVersion | True | String | 服务版本，默认值6.6.0 |
| resource | True | Json | 资源限制 |
| resourceType | True | String | 资源类型 |
| clusterId | True | String | 集群id |
| clusterVip | True | String | 集群vip |
| zoneId | True | String | 可用域id |
| clusterType | False | String | 独享集群/共享集群（PRIVATE_KAAS/PUBLIC_ZONE） |
| clusterPort | False | String | k8s api server端口 |
| esAddress | True | String | Elasticsearch集群地址，逗号分隔，如“192.168.0.1:9200, 192.168.0.2:9200” |
| 例如：<br>{<br>"description": "",<br>"name": "af",<br>"resource": {<br>"limits": {<br>"cpu": "2",<br>"memory": "2Gi"<br>},<br>"requests": {<br>"cpu": "0.5",<br>"memory": "0.5Gi"<br>}<br>},<br>"resourceType": "CONTAINER",    //默认值CONTAINER<br>"serviceType": "skywalking",    //默认值skywalking<br>"serviceVersion": "6.6.0",      //默认值6.6.0<br>"clusterId": "123456",          //选择集群的Id<br>"clusterVip": "172.25.16.60",   //选择集群的vip<br>"clusterType": "PUBLIC_ZONE",   //集群类型<br>"clusterPort": "8443",          //集群端口，仅当为独享集群时传递参数，缺省参数8443<br>"zoneId": "1498b05e-8",         //可用域Id<br>"esAddress": "172.25.16.60:9200"    //es地址格式为：IP:PORT<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 查询链路追踪实例详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/skymgt/v1.0/applications/{uuid\} | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | True | String | 实例id |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "test-uuid",<br>"name": "test-instance",   //名称<br>"description": "test instance ",<br>"projectId": "077549391d0547dd90adcfee63c21ed8",<br>"userId": "f129c117c576466681d6dd23e1dc8144",<br>"serviceType": "skywalking",<br>"serviceVersion": "6.6.0",      //版本<br>"esAddress": "10.125.31.185:9200",<br>"status": "ERROR",  //状态<br>"metadata": null,<br>"resourceType": "CONTAINER",<br>"namespace": "test-ns",<br>"releaseName": "test-release",<br>"svcOapName": "10.125.31.185:26200",    //接入地址<br>"svcUiName": "10.125.31.185:26201",     //管理地址<br>"k8sClusterId": "12345",<br>"k8sClusterVip": "10.125.31.185",<br>"createTime": "2021-06-09T20:27:57.000+0000",       //创建时间<br>"updateTime": "2021-06-10T03:37:56.000+0000",<br>"zoneId": "test-zone",<br>"zoneName": "",                      //可用域名称，仅在共享集群时存在值<br>"userName": "techopsAdmin",<br>"resource": {                       //资源规格<br>"limits": {<br>"cpu": "2",<br>"memory": "2Gi"<br>},<br>"requests": {<br>"cpu": "0.5",<br>"memory": "0.5Gi"<br>}<br>},<br>"replicas": null,<br>"clusterType": "PUBLIC_ZONE",    //集群类型<br>"clusterPort": "8443",<br>"privateKaasName": null          //kaas集群名称，仅在kaas集群实例时存在值<br>}<br>],<br>"status": true<br>\} |  |  |  |

#### 编辑链路追踪实例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/skymgt/v1.0/applications | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 名称 |
| description | True | String | 描述 |
| uuid | True | String | 实例id |
| 例如：<br>{<br>"description": "",<br>"name": "af",<br>"uuid": "uuid"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| status | Boolean | 请求状态 | 成功true |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 删除链路追踪实例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/skymgt/v1.0/applications/{uuid}/delete | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | True | String | 实例id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 重启链路追踪实例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/skymgt/v1.0/applications/{uuid}/restart | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | True | String | 实例id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

#### 升级链路追踪实例

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/skymgt/v1.0/applications/{uuid}/update | JSON | POST |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | True | String | 实例id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": null,<br>"status": true<br>\} |  |  |  |

### 应用诊断中间件（目前只支持数据库）

#### 查询中间件列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cloud/techops/quark/v1.0/appGroups/{appGroupId}/middlewares | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| appGroupId | True | String | 用户组ID |
| start | True | Long | 开始时间戳(可空, 空默认查询最近1小时) |
| end | True | Long | 结束时间戳(可空, 空默认查询最近1小时) |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"id": "adfc8e20-2742-48ad-8406-5fd2c3972bb0",<br>"name": "10.125.31.80:3306",<br>"resourceType": "database",<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询数据库监控信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cloud/techops/quark/v1.0/database/{databaseId}/metrics | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| databaseId | True | String | 数据库ID |
| metrics | True | String | 监控数据类型，包括“throughput”,” successrate”,” responsetime”,” avgresponsetime” |
| start | True | Long | 开始时间戳 |
| end | True | Long | 结束时间戳 |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"data": {<br>"units": null,<br>"items": [<br>{<br>"name": "databse-1",<br>"values": [<br>{<br>"timestamp": 1599806280,<br>"value": "100"<br>},<br>{<br>"timestamp": 1599806400,<br>"value": "100"<br>},<br>{<br>"timestamp": 1599806520,<br>"value": "100"<br>}<br>]<br>}<br>]<br>},<br>"code": null,<br>"msg": null,<br>"status": true,<br>"total": 0<br>\} |  |  |  |

#### 查询数据库慢SQL

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| cloud/techops/quark/v1.0//database/{databaseId}/statement/latency/top | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| databaseId | True | String | 数据库ID |
| Size | True | Integer | 查询Top数量 |
| start | True | Long | 开始时间戳 |
| end | True | Long | 结束时间戳 |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"statement": "select id,typeId,username,photo,content,createTime from comment_info where typeId=?",<br>"latency": 311, //单位ms<br>"traceId": "85ea8a44dbcf43aea47a6d378efa00f9.77.16382615606750003"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 应用诊断应用告警

#### 查询告警支持的指标模板列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0/alarms/templates | JSON | GET |  |

###### 参数说明

无

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "1",<br>"name": "应用成功率(%)",<br>"metricsName": "service_sla",<br>"message": "最近{range}分钟内服务 {name} 的成功率{op}{value}"<br>},<br>{<br>"uuid": "2",<br>"name": "应用健康度",<br>"metricsName": "service_apdex",<br>"message": "最近{range}分钟内服务 {name} 的健康度{op}{value}"<br>},<br>{<br>"uuid": "3",<br>"name": "应用吞吐量",<br>"metricsName": "service_cpm",<br>"message": "最近{range}分钟内服务 {name} 的吞吐量{op}{value}"<br>},<br>{<br>"uuid": "4",<br>"name": "应用响应时间(ms)",<br>"metricsName": "service_resp_time",<br>"message": "最近{range}分钟内服务 {name} 的平均响应时间{op}{value}ms"<br>}<br>],<br>"status": true,<br>"total": 4<br>\} |  |  |  |

#### 查询应用告警规则列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms/rules | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| pageSize | True | Integer | 每页数据量 |
| pageNum | True | Integer | 页数 |
| name | False | String | 规则名称 |
| metricsName | False | String | 规则指标：<br>service_sla（成功率）、service_apdex（健康度）、service_cpm（吞吐量）、service_resp_time（响应时间） |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "9373419f-a811-4fcf-a226-2dd8c4af3c3a",<br>"name": "规则名称",<br>"templateName": "指标名称：应用吞吐量",<br>"templateId": "3",<br>"level": "info",<br>"metricsName": "service_cpm",<br>"op": ">",<br>"threshold": 1,<br>"swInstanceId": "6878556a-fb69-4fab-ba11-be2e95201142",<br>"swInstanceName": "sw-86",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"createdAt": "2021-11-08T19:47:06.000+0000",<br>"updatedAt": "2021-11-08T19:47:06.000+0000",<br>//通知信息<br>"notifyInfo": [<br>{<br>"uuid": "c310ceea-85ac-4f24-aae9-1b0bb97d891c",<br>"type": "email",<br>"receiver": "zhangsan@qq.com,z15115@h3c.com",   //联系人<br>"alarmRuleId": "9373419f-a811-4fcf-a226-2dd8c4af3c3a",<br>"configMetadata": null,<br>"createdAt": "2021-11-08T19:47:06.000+0000",<br>"updatedAt": "2021-11-08T19:47:06.000+0000"<br>}<br>]<br>}<br>],<br>"status": true,<br>"total": 1   //总数<br>\} |  |  |  |

#### 新建告警规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms/rules | JSON | POST |  |

###### 参数说明

无

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 规则名称 |
| templateId | True | String | 指标模板id |
| metricsName | True | String | 规则指标：<br>service_sla（成功率）、service_apdex（健康度）、service_cpm（吞吐量）、service_resp_time（响应时间） |
| level | True | String | 告警级别：WARNING(一般)，MAJOR(重要)，CRITICAL(紧急) |
| op | True | String | 告警比较方式：大于＞、小于＜ |
| threshold | True | Integer | 告警阈值 |
| period | True | Integer | 告警间隔 |
| count | True | Integer | 告警次数 |
| silencePeriod | True | Integer | 告警静默时间 |
| swInstanceId | True | String | Skywalking实例Id |
| appInfo | True | List | 应用信息，传空列表 表示作用于全部应用 |
| notifyInfo | True | List | 通知人信息 |
| 例如：<br>{<br>"name": "规则名称",  //长度<128<br>"templateId": "3",  //指标模板id<br>"metricsName": "service_cpm",<br>"level": "info",<br>"op": ">",<br>"threshold": "1",   //阈值<br>"period": "1",      //间隔<br>"count": "1",       //次数<br>"silencePeriod": "1",    //静默时间<br>"swInstanceId": "xxx",<br>//应用信息，传空列表 表示作用于全部应用<br>"appInfo": [<br>{<br>"uuid": "appid",<br>"name": "appName",<br>"namespace": "ns",<br>"resourceType": "container",<br>"clusterId": "",<br>"clusterType": ""<br>}<br>],<br>"notifyInfo": [<br>{<br>"type": "email",<br>"receiver": "zhangsan@qq.com,z15115@h3c.com"<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 修改告警规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms/rules/{id\} | JSON | PUT |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | True | String | 告警规则id |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 规则名称 |
| op | True | String | 告警比较方式：大于＞、小于＜ |
| threshold | True | Integer | 告警阈值 |
| period | True | Integer | 告警间隔 |
| count | True | Integer | 告警次数 |
| silencePeriod | True | Integer | 告警静默时间 |
| appInfo | True | List | 应用信息，传空列表 表示作用于全部应用 |
| notifyInfo | True | List | 通知人信息 |
| 例如：<br>{<br>"name": "规则名称",<br>"op": ">",<br>"threshold": "1",   //阈值<br>"period": "1",      //间隔<br>"count": "1",       //次数<br>"silencePeriod": "1",    //静默时间<br>//应用信息，传空列表 表示作用于全部应用<br>"appInfo": [<br>{<br>"uuid": "appid",<br>"name": "appName",<br>"namespace": "ns",<br>"resourceType": "container",<br>"clusterId": "",<br>"clusterType": ""<br>}<br>],<br>"notifyInfo": [<br>{<br>"uuid": "c310ceea-85ac-4f24-aae9-1b0bb97d891c",<br>"type": "email",<br>"receiver": "zhangsan@qq.com,z15115@h3c.com"<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 删除告警规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms/rules/{id\} | JSON | DELETE |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | True | String | 告警规则id |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询告警列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| pageSize | True | Integer | 每页数据量 |
| pageNum | True | Integer | 页数 |
| orderBy | False | String | 排序规则：createdAt asc、createdAt desc |
| ruleName | False | String | 规则名称 |
| status | False | String | 逗号分隔，如：ACTIVE,CONFIRMED。不传默认查询所有状态<br>告警状态：ACTIVE(告警中)、CONFIRMED（已确认）、RESTORED（已恢复 |
| Level | False | String | 逗号分隔，如：WARNING,MAJOR。不传默认查询所有级别<br>告警级别：WARNING（一般）、MAJOR（重要）、CRITICAL（紧急） |
| appid | False | String | 应用id |
| swInstanceId | False | String | 告警来源，skywalking实例id |
| start | False | Long | 开始时间13位时间戳 |
| end | False | Long | 结束时间13位时间戳 |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "test12",<br>"summaryMessage": "最近2分钟内服务 spaceitwiz5jc:app1 的吞吐量>100",<br>"ruleName": "sss",<br>"level": "warn",<br>"swInstanceId": "test1",<br>"swInstanceName": "test12345",<br>"appId": "test1",<br>"appName": "test1",<br>"appGroupName": "test1",<br>"metricsName": "test1",<br>"status": "CONFIRMED",<br>"repeatTimes": 2,<br>"userId": "test1",<br>"projectId": "test1",<br>"createdAt": "2021-11-16T07:38:59.000+0000",<br>"updatedAt": "2021-11-16T11:30:55.000+0000",<br>"continuedSeconds": 13916<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询应用告警事件列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms/{uuid}/events | JSON | GET |  |

###### 参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | True | String | 告警信息id |

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>//后端无分页<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "test2",<br>"message": "test message",<br>"startTime": "2021-11-16T12:08:42.000+0000",<br>"ruleId": "test",<br>"appId": "test",<br>"alarmSortId": "test12",<br>"notified": false,<br>"createdAt": "2021-11-16T12:08:47.000+0000",<br>"updatedAt": "2021-11-16T12:08:48.000+0000"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 确认告警

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms | JSON | PUT |  |

###### 参数说明

无

###### 请求体

例如：

```json
[
{
"uuid": "test12",
"ruleId": "test1"
}
]
```

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": [<br>{<br>"uuid": "test12",<br>"summaryMessage": "最近2分钟内服务 spaceitwiz5jc:app1 的吞吐量>100",<br>"ruleId": "test1",<br>"ruleName": "sss",<br>"level": "warn",<br>"swInstanceId": "test1",<br>"swInstanceName": "test12345",<br>"appId": "test1",<br>"appName": "test1",<br>"appGroupName": "test1",<br>"metricsName": "test1",<br>"status": "CONFIRMED",<br>"repeatTimes": 2,<br>"userId": "test1",<br>"projectId": "test1",<br>"createdAt": "2021-11-16T07:38:59.000+0000",<br>"updatedAt": "2021-11-16T11:30:55.000+0000"<br>}<br>],<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 查询告警数量

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms/quantities | JSON | GET |  |

###### 参数说明

无

###### 请求体

无

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": {<br>"total": 2,   //总数<br>//未确认<br>"active": {<br>"total": 1,<br>"waring": 0,   //一般<br>"major": 0,    //重要<br>"critical": 0  //紧急<br>},<br>//已确认<br>"confirmed": {<br>"total": 1,<br>"waring": 0,<br>"major": 0,<br>"critical": 0<br>},<br>//已恢复<br>"restored": {<br>"total": 0,<br>"waring": 0,<br>"major": 0,<br>"critical": 0<br>}<br>},<br>"status": true,<br>"total": 1<br>\} |  |  |  |

#### 新建告警规则参数校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /cloud/techops/alarm/v1.0 /alarms/rules/parameters/check | JSON | POST |  |

###### 参数说明

无

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | True | String | 规则名称 |
| 例如：<br>{<br>"name": "rule-test-config"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| status | Boolean | 请求状态 | 成功true |
| data | Object | 结果信息 | 结果信息 |
| 例如：<br>{<br>"msg": "",<br>"code": "",<br>"data": "",<br>"status": true,<br>"total": 1<br>\} |  |  |  |

### 分布式事务管理

#### 分布式事务列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1/seata | JSON | GET |  |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | True | Integer | 页数 |
| size | True | Integer | 行数 |
| name | False | String | 分布式事务名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回分布式事务信息 | 返回分布式事务信息 |
| total | Integer | 列表元素数量 | 列表元素数量 |
| {<br>"code": "",<br>"data": [<br>{<br>"cluster": {<br>"id": "ef981dbb-a",<br>"name": "PaaS2",<br>"type": "paasAzone"<br>},<br>"created_at": "Thu, 16 Dec 2021 01:13:56",<br>"creator": {<br>"id": "1df71957d770436493a84f24d9344853",<br>"name": "admin"<br>},<br>"id": "734e9f66-6a84-4dac-b630-2804f9d6a65e",<br>"instances_num": {<br>"running": 1,<br>"total": 1<br>},<br>"name": "testz",<br>"project": {<br>"id": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "root"<br>},<br>"resources": {<br>"limits": {<br>"cpu": 1,<br>"memory": "1024Mi"<br>},<br>"requests": {<br>"cpu": 0.6,<br>"memory": "512Mi"<br>}<br>},<br>"status": "running"<br>}<br>],<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 1<br>\} |  |  |  |

#### 分布式事务详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1/seata/{id\} | JSON | GET |  |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 分布式事务ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回分布式事务实例详细的信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"code": "",<br>"data": {<br>"access_endpoint": {<br>"external": "",<br>"internal": "seata-svc.seata200aa495:32501"<br>},<br>"cluster": {<br>"id": "ef981dbb-a",<br>"name": "PaaS2",<br>"type": "paasAzone"<br>},<br>"created_at": "Thu, 16 Dec 2021 01:13:56",<br>"creator": {<br>"id": "1df71957d770436493a84f24d9344853",<br>"name": "admin"<br>},<br>"description": "",<br>"id": "734e9f66-6a84-4dac-b630-2804f9d6a65e",<br>"instances": [<br>{<br>"created_at": "Mon, 27 Dec 2021 22:33:58",<br>"name": "seata-server-7896d69788-mptjf",<br>"today_transaction_num": 0<br>}<br>],<br>"instances_num": {<br>"running": 1,<br>"total": 1<br>},<br>"name": "testz",<br>"project": {<br>"id": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "admin"<br>},<br>"resource_type": "container",<br>"resources": {<br>"limits": {<br>"cpu": 1,<br>"memory": "1024Mi"<br>},<br>"requests": {<br>"cpu": 0.6,<br>"memory": "512Mi"<br>}<br>},<br>"status": "running"<br>},<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 1<br>\} |  |  |  |

#### 分布式事务监控详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1/seata/{id}/instances/{instanceName}/monitor | JSON | GET |  |

###### 参数说明

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | True | String | 分布式事务ID |
| instanceName | True | String | 分布式事务实例名称 |
| duration | True | String | 监控数据间隔时长， 1h/3h/6h/1d，代表1小时/3小时/6小时/1天 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回分布式事务实例事务监控的信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"code": "",<br>"data": {<br>"committed": [<br>{<br>"timestamp": 1641785836,<br>"value": 0<br>},<br>{<br>"timestamp": 1641785836,<br>"value": 0<br>}<br>],<br>"rollbacked": [<br>{<br>"timestamp": 1641785836,<br>"value": 0<br>},<br>{<br>"timestamp": 1641785836,<br>"value": 0<br>}<br>]<br>},<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 1<br>\} |  |  |  |

#### 集群可用资源校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1//helper/is_resource_available | JSON | POST | 集群可用cpu、内存资源校验 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| check | False | String | 资源类型，为空时代表的cpu、为memory时代表内存 |

###### 请求体(form-data)

| requests.cpu | true | String | cpu资源 |
| --- | --- | --- | --- |
| requests. memory | true | String | 内存资源 |
| cluster.type | true | String | 集群类型 |
| cluster.id | true | String | 集群id |
| {<br>"requests": {<br>"cpu": "0",<br>"memory": "512Mi"<br>},<br>"cluster": {<br>"type": "paasAzone",<br>"id": "ef981dbb-a"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回资源校验的结果信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"code": "",<br>"data": true,<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 0<br>\} |  |  |  |

#### 集群可用端口校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1/helper/is_port_available | JSON | POST | 集群可用端口资源校验 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

###### 请求体(form-data)

| port | true | Int | 端口资源 |
| --- | --- | --- | --- |
| cluster.type | true | String | 集群类型 |
| cluster.id | true | String | 集群id |
| {<br>"port": 32506,<br>"cluster": {<br>"type": "paasAzone",<br>"id": "ef981dbb-a"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Boolean | 具体返回结果体 | 返回资源校验的结果信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"code": "",<br>"data": true,<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 0<br>\} |  |  |  |

#### 创建分布式事务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1/seata | JSON | POST | 创建分布式事务 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

###### 请求体(form-data)

| name | true | Int | 名称 |
| --- | --- | --- | --- |
| description | true | String | 描述 |
| resource_type | true | String | 资源类型：容器 |
| cluster.type | true | String | 集群类型 |
| cluster.id | true | String | 集群id |
| resources. limits.cpu | true | String | 容器启动cpu规格限制 |
| resources. limits.memory | true | String | 容器启动内存规格限制 |
| resources. requests.cpu | true | String | 容器运行cpu规格限制 |
| resources. requests. memory | true | String | 容器运行内存规格限制 |
| access_mode. type | true | String | 集群访问类型：集群内/集群外访问 |
| access_mode. port | true | Int | 集群内端口 |
| access_mode. nodePort | false | Int | 集群外端口，集群外访问时必填 |
| mysql_config.host | true | String | MySQL数据库主机 |
| mysql_config.port | true | Int | MySQL数据库端口 |
| mysql_config.username | true | String | MySQL数据库用户 |
| mysql_config.password | true | String | MySQL数据库密码 |
| advanced_config. maxCommitRetryTimeout | false | String | 高级配置：二阶段提交重试超时时长, -1代表无限重试 |
| advanced_config.recovery_committingRetryPeriod | false | String | 高级配置：二阶段提交未完成状态全局事务重试提交线程间隔时间 |
| advanced_config.recovery_asynCommittingRetryPeriod | false | String | 高级配置：二阶段异步提交状态重试提交线程间隔时间 |
| advanced_config.recovery_rollbackingRetryPeriod | false | String | 高级配置：二阶段回滚状态重试回滚线程间隔时间 |
| advanced_config.recovery_timeoutRetryPeriod | false | String | 高级配置：超时状态检测重试线程间隔时间 |
| {<br>"name": "testz2",<br>"description": "testz2",<br>"resource_type": "container",<br>"cluster": {<br>"type": "paasAzone",<br>"id": "default"<br>},<br>"resources": {<br>"limits": {<br>"cpu": "1",<br>"memory": "1024Mi"<br>},<br>"requests": {<br>"cpu": "0.2",<br>"memory": "512Mi"<br>}<br>},<br>"access_mode": {<br>"type": "external",<br>"port": 32506,<br>"nodePort": 32506<br>},<br>"mysql_config": {<br>"host": "10.125.29.146",<br>"port": 3306,<br>"username": "root",<br>"password": "H3C_Cloud0S#PaaS@CTT2019"<br>},<br>"advanced_config": {<br>"maxCommitRetryTimeout": "1000",<br>"recovery_committingRetryPeriod": "1000",<br>"recovery_asynCommittingRetryPeriod": "1000",<br>"recovery_rollbackingRetryPeriod": "1000",<br>"recovery_timeoutRetryPeriod": "1000"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 具体返回结果体 | 返回分布式事务id信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"code": "",<br>"data": {<br>"id": "1869ad59-b8ae-410a-ae4b-f9227d159a1d"<br>},<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 0<br>\} |  |  |  |

#### 重启分布式事务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1/seata/{id}/restart | JSON | POST | 重启分布式事务 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | True | String | 分布式事务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回重启分布式事务的结果信息 | 分布式事务id信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"code": "",<br>"data": {<br>"id": "36f6e3b7-2c15-46ed-87a7-19ef0cad59b4"<br>},<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 0<br>\} |  |  |  |

#### 伸缩容分布式事务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1/seata/{id}/ resize | JSON | POST | 伸缩容分布式事务 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | True | String | 分布式事务id |

###### 请求体(form-data)

| limits.cpu | true | String | 容器启动cpu规格限制 |
| --- | --- | --- | --- |
| limits.memory | true | String | 容器启动内存规格限制 |
| requests.cpu | true | String | 容器运行cpu规格限制 |
| requests. memory | true | String | 容器运行内存规格限制 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回重启分布式事务的结果信息 | 分布式事务id信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"code": "",<br>"data": {<br>"id": "11856eaf-7d33-4a98-a638-01d47b5f8661"<br>},<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 0<br>\} |  |  |  |

#### 删除分布式事务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/seata-mgmt/v1/seata/{id\} | JSON | DELETE | 删除分布式事务 |

###### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |

| Query参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Id | True | String | 分布式事务id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | String | 返回删除分布式事务的结果信息 | 分布式事务id信息 |
| status | Boolean | 状态 | true为成功 |
| {<br>"code": "",<br>"data": {<br>"id": "36f6e3b7-2c15-46ed-87a7-19ef0cad59b4"<br>},<br>"msg": "\u6b63\u5e38",<br>"status": false,<br>"total": 0<br>\} |  |  |  |

## 容器云

### 容器镜像

镜像服务api前缀：/api/cloud/hcr/v1.0

#### 镜像类型查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types | JSON | GET | 镜像类型查询 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| Uuid | false | String | 查询参数，镜像类型ID |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常成功情况下，code为” imgType.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为”ok” |
| data | Object | 具体返回结果体 | 响应数据：（uuid：类型id，name：类型名称，createTime：创建时间，userId：创建者id，tentantId：组织id） |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"status": true,<br>"code": "imgType.0",<br>"msg": "ok",<br>"data": [<br>{<br>"uuid": "20e68cab-c634-468d-8d0c-56cb6096",<br>"name": "未分组",<br>"createTime": 1546950030000,<br>“userId”: "1df71957d770436493a84f24d9344853",<br>“tentantId”: "9052ae85792143ff955c35c52e8e41bd"<br>},<br>{<br>"uuid": "f1ee6eb5-a913-4d3c-972e-88ceef6d",<br>"name": "ctest",<br>"createTime": 1550197851000,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"tentantId": "78235b23a2c24b6aac2682cebc11b081"<br>}<br>],<br>"httpCode": 200<br>\} |  |  |  |

#### 镜像类型创建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types | JSON | POST | 镜像类型创建 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 镜像类型名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”imgType.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：（uuid：类型id，name：类型名称，createTime：创建时间，userId：创建者id，tentantId：组织id） |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像类型名称 | 镜像类型名称 |
| 例如：<br>{<br>"status": true,<br>"code": "imgType.0",<br>"msg": "ok",<br>"data": {<br>"uuid": "6657ff7d-1f4c-4bf4-b1be-f6d41e45",<br>"name": "testType",<br>"createTime": 1550200294701,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"tentantId": "78235b23a2c24b6aac2682cebc11b081"<br>},<br>"httpCode": 200<br>"resources": [<br>"abc"<br>]<br>\} |  |  |  |

#### 镜像类型修改

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types/{uuid\} | JSON | PUT | 镜像类型修改 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 镜像类型名称，规格：1-32位字符，英文字母（a-z,A-Z）、数字(0-9)以及括号中的特殊字符（.-_@），不能以特殊字符开头 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”imgType.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：（uuid：类型id，name：类型名称，createTime：创建时间，userId：创建者id，tentantId：组织id） |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像类型名称 | 镜像类型名称 |
| 例如：<br>{<br>"status": true,<br>"code": "imgType.0",<br>"msg": "ok",<br>"data": {<br>"uuid": "6657ff7d-1f4c-4bf4-b1be-f6d41e45",<br>"name": "testType",<br>"createTime": 1550200294701,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"tentantId": "78235b23a2c24b6aac2682cebc11b081"<br>},<br>"httpCode": 200<br>"resources": [<br>"edit"<br>]<br>\} |  |  |  |

#### 镜像类型删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types/{uuid\} | JSON | DELETE | 镜像类型删除 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”imgType.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：删除的镜像分类的uuid |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像类型名称 | 镜像类型名称 |
| 例如：<br>{<br>"status": true,<br>"code": "imgType.0",<br>"msg": "ok",<br>"data": {<br>"b9566a3f-0f59-4c46-8c8b-4d4ba8d6"<br>},<br>"httpCode": 200<br>"resources": [<br>"abc"<br>]<br>\} |  |  |  |

#### 上传镜像

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/load | JSON | POST | 上传镜像 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| imgName | true | String | 镜像名称 |
| version | true | String | 镜像版本 |
| imgType | true | String | 镜像分类uuid |
| type | true | String | 镜像仓库类型 |
| fileFlag | true | String | 镜像标识 |
| projectname | false | String | 项目名, 不填时私有镜像上传到默认项目 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| totalChunks | true | int | 镜像文件总分片数 |
| chunkNumber | true | int | 镜像文件当前分片编号 |
| description | false | String | 镜像描述 |
| totalSize | true | String | 镜像文件大小，单位kb |
| featureInfo | false | String | 镜像版本特性 |
| imgAlias | false | String | 镜像别名 |
| file | true | binary | 镜像tar文件二进制流 |
| imgLogo | false | binary | 镜像图标文件二进制流 |
| 例如：<br>{<br>“totalChunks”:1，<br>“chunkNumber”:1，<br>“description”: “fff”,<br>“totalSize”: “14586”,<br>“featureInfo”: “测试版本”,<br>“imgAlias”: “别名ssss”,<br>“file”: ,<br>“imgLogo”: ,<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：镜像名 |
| httpCode | Integer | http码 | 200 |

#### 镜像列表查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/versions | JSON | GET | 镜像列表查询，<br>/images/versions? harborProjectName=library&t=1635736224698&page=1&size=10&type=public&imgAlias=qh&imgType=0455117a-4d35-4dde-b6a6-1b572322 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| page | false | String | 页数 |
| size | false | String | 每页大小 |
| imgType | false | String | 镜像分类id，若查所有填“” |
| type | false | String | 镜像所属仓库，公有仓库=public，私有仓库=private |
| createUser | false | String | 镜像创建者ID |
| name | false | String | 镜像名称 |
| imgAlias | false | String | 镜像显示名称，支持模糊搜索 |
| harborProjectName | false | String | 镜像所在项目名，不填时私有镜像默认查询默认项目下的 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据，具体见下表描述 |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"status": true,<br>"code": "img.0",<br>"msg": "ok",<br>"data": {<br>"total": 1,<br>"data": [<br>{<br>"uuid": "shnnb7hn",<br>"imageName": "nginxtestpublic",<br>"imgAlias": "nginxtestpublic",<br>"description": "",<br>"tagCount": 1,<br>"type": "public",<br>"userName": "admin",<br>"tentantName": "root",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"tentantId": "9052ae85792143ff955c35c52e8e41bd",<br>"updateTime": "2021-10-20 14:50:03",<br>"createTime": "2021-10-20 14:49:56",<br>"imgType": "20e68cab-c634-468d-8d0c-56cb6096",<br>"images": []<br>}<br>]<br>},<br>"httpCode": 200<br>\} |  |  |  |

注： 消息响应体data中参数描述

| 参数 | 描述 |
| --- | --- |
| uuid | 镜像组id |
| imageName | 镜像名称 |
| imgAlias | 镜像别名 |
| tagCount | 镜像版本数量 |
| type | 镜像仓库类型：公有、私有 |
| userName | 用户名 |
| tentantName | 组织名 |
| userId | 用户Id |
| tentantId | 组织id |
| updateTime | 修改时间 |
| createTime | 创建时间 |
| totalSize | 镜像总大小，单位MB |
| imgType | 镜像分类 |
| images | 镜像列表接口镜像详情默认为空 |

#### 详情镜像查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /image/versions | JSON | GET | 镜像详情查询<br>/image/versions?name=nginxtestpublic&type=public&tentantId=9052ae85792143ff955c35c52e8e41bd&imgType=20e68cab-c634-468d-8d0c-56cb6096&harborProjectName=user |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| name | ture | String | 镜像名称 |
| type | true | String | 镜像仓库类型 |
| tentantId | true | String | 组织ID |
| ImgType | false | String | 镜像类型ID |
| imgId | false | String | 镜像ID |
| harborProjectName | false | String | 项目名，不填时私有镜像默认查询默认项目的 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据，具体见下表描述 |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"status": true,<br>"code": "img.0",<br>"msg": "ok",<br>"data": {<br>"uuid": "",<br>"totalSize": "118.37",<br>"images": [<br>{<br>"uuid": "3vcdf8zh",<br>“digest”:” sha256:a37a32d1d86e785f2e5de33e801c7b2f4d313284b619623deed7483d88181bc6”<br>"projectName": "library",<br>"imgName": "abc-software",<br>"versions": "1.01",<br>"creator": "1df71957d770436493a84f24d9344853",<br>"updator": "1df71957d770436493a84f24d9344853",<br>"imgType": "20e68cab-c634-468d-8d0c-56cb6096",<br>"userID": "1df71957d770436493a84f24d9344853",<br>"tentantID": "9052ae85792143ff955c35c52e8e41bd",<br>"imgStatus": "unused",<br>"imgSize": "118.37",<br>"imgUrl": "os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/abc-software:1.01",<br>"userName": "admin",<br>"tentantName": "admin",<br>"type": "public",<br>"createTime": "2019-11-23 13:55:06",<br>"updateTime": "2019-11-23 13:55:56"<br>}<br>]<br>},<br>"httpCode": 200<br>\} |  |  |  |

注： 消息响应体data中参数描述

| 参数 | 描述 |
| --- | --- |
| uuid | 镜像组id |
| tagCount | 镜像版本数量 |
| totalSize | 镜像总大小，单位MB |
| images | 镜像信息，（uuid：镜像id，digest: 镜像摘要，projectName:仓库名，imgName：镜像名，versions：镜像版本，creator：创建者id，updator：修改者id，imgType：镜像分类id，userID：用户id，tentantID：组织id，imgStatus：镜像状态，imgSize：镜像大小，单位MB, imgUrl:镜像url，userName：用户名，tentantName：组织名，type：仓库类型，createTime：创建时间，updateTime：修改时间） |

#### 镜像构建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/build | JSON | POST | 镜像构建 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| dockerfile | ture | String | Dockerfile文件内容 |
| imgName | true | String | 镜像名称 |
| version | true | String | 镜像版本 |
| imgType | true | String | 镜像分类 |
| description | false | String | 镜像描述 |
| type | true | String | 镜像仓库类型：公有私有 |
| fileFlag | true | String | 镜像文件标记 |
| project | false | String | 项目名，不填时为私有仓库默认项目 |
| 例如<br>{<br>"dockerfile":"FROM harbor-nginx.default.svc.cloudos:443/helm/mysql:5.6 ",<br>"imgName":"test",<br>"version":"3.0",<br>"imgType":"20e68cab-c634-468d-8d0c-56cb6096",<br>"description":"dgshshdhdh",<br>"type":"private",<br>"fileFlag":"3Gk2qIYP9G",<br>“project”:”user”<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 错误码, 正常成功情况下为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据，build:镜像构建日志，push:镜像推送日志 |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像名称：版本 | 镜像名称：版本 |
| 例如：<br>{<br>"status":true,<br>"code":"img.0",<br>"msg":"ok",<br>"data":{<br>"build":[<br>{<br>"stream":"Step 1/1 : FROM 10.125.31.185:30002/test-1119/1df71957d770436493a84f24d9344853/test001:test001\n"<br>},<br>{<br>"stream":" ---> 41c2669da537\n"<br>},<br>{<br>"stream":"Successfully built 41c2669da537\n"<br>}<br>],<br>"push":[<br>{<br>"status":"The push refers to a repository [10.125.31.185:30002/testdelete/1df71957d770436493a84f24d9344853/test1122gjjx]"<br>},<br>{<br>"status":"Preparing",<br>"progressDetail":{},<br>"id":"25e448890d3f"<br>},<br>{<br>"status":"Preparing",<br>"progressDetail":{},<br>"id":"ceaf9e1ebef5"<br>},<br>{<br>"status":"Preparing",<br>"progressDetail":{},<br>"id":"9b9b7f3d56a0"<br>},<br>{<br>"status":"Preparing",<br>"progressDetail":{},<br>"id":"f1b5933fe4b5"<br>},<br>{<br>"status":"Mounted from test12/1df71957d770436493a84f24d9344853/testa",<br>"progressDetail":{},<br>"id":"9b9b7f3d56a0"<br>},<br>{<br>"status":"Mounted from test12/1df71957d770436493a84f24d9344853/testa",<br>"progressDetail":{},<br>"id":"25e448890d3f"<br>},<br>{<br>"status":"Mounted from test12/1df71957d770436493a84f24d9344853/testa",<br>"progressDetail":{},<br>"id":"ceaf9e1ebef5"<br>},<br>{<br>"status":"Mounted from test12/1df71957d770436493a84f24d9344853/testa",<br>"progressDetail":{},<br>"id":"f1b5933fe4b5"<br>},<br>{<br>"status":"test1122gjjx: digest: sha256:6ffe76a781c55c14b0cb27459bb05026b415097f9e49943bcb6ac9f7f3fe05dd size: 1159"<br>},<br>{<br>"progressDetail":{},<br>"aux":{<br>"Tag":"test1122gjjx",<br>"Digest":"sha256:6ffe76a781c55c14b0cb27459bb05026b415097f9e49943bcb6ac9f7f3fe05dd",<br>"Size":1159<br>}<br>}<br>]<br>},<br>"httpCode":200,<br>"resources":[<br>"test1122gjjx:test1122gjjx"<br>]<br>\} |  |  |  |

#### 查看镜像扫描漏洞概览

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/images/scan/{imageUrl\} | JSON | GET | 例imageUrl=<br>os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/testbuild:1.0 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 错误码, 正常成功情况下为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据 |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"code": "img.0",<br>"data": {<br>“detail”: "{\"duration\":11,\"severity\":\"Critical\",\"summary\":{\"summary\":{\"High\":18,\"Low\":2,\"Medium\":15,\"Critical\":1},\"total\":36,\"fixable\":36},\"start_time\":\"2022-01-06T08:21:35.000Z\",\"scan_status\":\"Success\",\"report_id\":\"6d00eb57-4b6f-4660-bedc-805118e80f35\",\"complete_percent\":100,\"end_time\":\"2022-01-06T08:21:46.000Z\",\"scanner\":{\"vendor\":\"Aqua Security\",\"name\":\"Trivy\",\"version\":\"v0.16.0\"}}"<br>“imgName”: "1df71957d770436493a84f24d9344853/testbuild"<br>“projectName”: "user"<br>“scanStatus”: "success"<br>“version”: "1.0"<br>},<br>"httpCode": 200,<br>"msg": "ok",<br>"status": true<br>\} |  |  |  |

#### 查看镜像扫描漏洞详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/images/scanDetail/{imageUrl\} | JSON | GET | 例imageUrl=<br>os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/testbuild:1.0 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 错误码, 正常成功情况下为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据 |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"code": "img.0",<br>"data": {<br>“detail”:<br>"{\"application/vnd.scanner.adapter.vuln.report.harbor+json; version=1.0\":{\"generated_at\":\"2022-01-06T08:21:42.716829816Z\",\"scanner\":{\"name\":\"Trivy\",\"vendor\":\"Aqua Security\",\"version\":\"v0.16.0\"},\"severity\":\"Critical\",\"vulnerabilities\":[{\"id\":\"CVE-2021-36159\",\"package\":\"apk-tools\",….}]}”<br>“imgName”: "1df71957d770436493a84f24d9344853/testbuild"<br>“projectName”: "user"<br>“scanStatus”: "success"<br>“version”: "1.0"<br>},<br>"httpCode": 200,<br>"msg": "ok",<br>"status": true<br>\} |  |  |  |

#### 镜像扫描

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/images/scan | JSON | POST | 镜像扫描 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgName | true | String | 镜像名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 错误码,正常为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据，scanStatus:扫描状态，scanning为扫描中，imgName:镜像名称，version: 镜像版本 |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像名称：版本 | 镜像名称：版本 |
| 例如：<br>{<br>"status": true,<br>"code": "img.0",<br>"msg": "ok",<br>"data": {<br>"scanStatus": "scanning",<br>"imgName": "tomcat-0730",<br>"version": "v1.0"<br>},<br>"httpCode": 200,<br>"resources": [<br>"tomcat-0730:v1.0"<br>]<br>\} |  |  |  |

#### 镜像版本特性编辑

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/updateImage | JSON | POST | 镜像版本特性编辑 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 镜像id |
| featureInfo | false | String | 镜像版本特性 |
| type | true | String | 镜像仓库类型 |
| tentantID | true | String | 组织id |
| userID | true | String | 用户Id |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true为正常 |
| code | String | 错误码 | 正常成功情况下，code为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| resources | String | 具体返回结果体 | 镜像名称:镜像版本 |
| httpCode | Integer | 200 |  |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","httpCode":200,"resources":["testimage:2.0"]\} |  |  |  |

#### 镜像下载

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/expose/{imageUrl\} | JSON | GET | 根据镜像Url下载镜像tar包 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>二进制文件流 |  |  |  |

#### 镜像仓库删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/versions | JSON | POST | 镜像仓库的删除<br>/images/versions |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imageGroupId | true | String | 镜像仓库id |
| imageName | true | String | 镜像仓库名 |
| images | false | String | 镜像版本<br>[{“id”:”hyeoc9ds”,”imgUrl”:”os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/testbuild:1.0” }] |
| imgType | true | String | 镜像分类 |
| projectName | true | String | 项目名 |
| tagCount | true | String | 仓库版本数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| data | Object | 响应体 | 其中success为删除成功的镜像，failed为删除失败的镜像，notFound为未找到的镜像，notAllow为不允许删除的镜像，delAll是否该镜像所有版本都删除完 |
| httpCode | Integer | http状态码 | 200 |
| resources | List<String> | 删除的镜像名数组 | ["gg:v1"] |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":{"success":["v1"],"failed":[],"notFound":[],"notAllow":[],"delAll":true},"httpCode":200,"resources":["gg:v1"]\} |  |  |  |

#### 单一镜像删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/deleteOneImage | JSON | POST | 单一镜像删除<br>/images/deleteOneImage |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 镜像id |
| imgUrl | true | String | 镜像url |
| tentantID | true | String | 组织id |
| userID | true | String | 用户id |
| 例如<br>{"id":"igirui1o","tentantID":"9052ae85792143ff955c35c52e8e41bd","userID":"1df71957d770436493a84f24d9344853","imgType":"fb321900-4d4f-4f1a-afc1-fa6f53c8"\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| data | Boolean | 响应体 | 成功为true，否则为false |
| httpCode | Integer | http状态码 | 200 |
| resources | List<String> | 删除的镜像名数组 | ["tomcat:8.1"] |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":true,"httpCode":200,"resources":["tomcat:8.1"]\} |  |  |  |

#### 镜像批量删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/delete | JSON | POST | 镜像批量删除<br>/images/delete |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| images | true | String | 镜像id的数组 |
| imgType | true | String | 镜像分类的id |
| 例如<br>{<br>"images":[{"id":"sboky4ao",”imgUrl”:” os-harbor-svc.default.svc.cloudos:443/fff/testdel:2.0”}],<br>"imgType":"20e68cab-c634-468d-8d0c-56cb6096"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| data | Object | 响应体 | 其中success为删除成功的镜像，failed为删除失败的镜像，notFound为未找到的镜像，notAllow为不允许删除的镜像，delAll是否该镜像所有版本都删除完 |
| httpCode | Integer | http状态码 | 200 |
| 例如：<br>{<br>"status":true,<br>"code":"img.0",<br>"msg":"ok",<br>"data":{<br>"success":["2.0"],<br>"failed":[],<br>"notFound":[],<br>"notAllow":[],<br>"delAll":true<br>},<br>"httpCode":200,<br>"resources":[" testimage:2.0"]<br>\} |  |  |  |

#### 镜像发布到公有仓库

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/public?apply=false | JSON | POST | 私有镜像发布到公有仓库,apply=false非申请流程，apply=true为普通用户的申请流程 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgName | true | String | 镜像名称 |
| imgGroupId | true | String | 镜像组id |
| imgAlias | false | String | 镜像别名 |
| imageIds | true | String | 镜像id，多个镜像用逗号隔开 |
| type | true | String | 镜像仓库类型 |
| imgType | true | String | 镜像分类id |
| description | false | String | 镜像描述 |
| imgLogo | false | String | 镜像图片 |
| outImageUrls | false | String | 客户端上传的镜像的url |
| 例如：<br>{<br>"description": "",<br>"imgName":"cicd-porta-jarl",<br>"imgGroupId":"sgg8lpel",<br>"imgAlias":"cicd-porta-jarl",<br>"imageIds":"uthps2jw",<br>"type":"private",<br>"imgType":"20e68cab-c634-468d-8d0c-56cb6096",<br>“outImageUrls”:”os-harbor-svc.default.svc.cloudos:443/fff/test:ff”<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":false,"httpCode":200,"resources":["test"]\} |  |  |  |

#### 镜像公共信息编辑

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/updateImageGroup | JSON | POST | 镜像公共信息编辑 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgName | true | String | 镜像名称 |
| imgAlias | false | String | 镜像别名 |
| description | false | String | 镜像描述信息 |
| imageIds | true | String | 镜像id，多个镜像用逗号隔开 |
| type | true | String | 镜像仓库类型 |
| imgType | true | String | 镜像分类id |
| tentantId | true | String | 组织ID |
| userId | true | String | 用户ID |
| uuid | false | String | 镜像组ID |
| imgLogo | false | binary | 镜像图标的二进制文件 |
| harborProjectName | true | String | 默认项目传user，公有仓库传library，其他私有项目为项目名称 |
| 例如：<br>{<br>"imgAlias": "demo-boot",<br>"description": "built and pushed by pipeline",<br>"imgName": "demo-boot",<br>"imgType": "20e68cab-c634-468d-8d0c-56cb6096",<br>"type": "private",<br>"tentantId": "9052ae8579143ff955c35c52e41bd”,<br>"uuid": “grzdfnzg”,<br>"userId": “zg<br>"imageIds": "tdmtozii,ytxbc5om"，<br>“harborProjectName”:”user”<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| resoucres | List<String> | 编辑镜像组名称 | 编辑镜像组名称 |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","httpCode":200, "resources":[“abc”]\} |  |  |  |

#### 未使用镜像查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /image/unused | JSON | GET | 查询未使用状态的镜像 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| data | Object | 返回结果 | 查询结果，镜像信息 |
| 例如：<br>{<br>"status": true,<br>"code": "img.0",<br>"msg": "ok",<br>"data": [<br>{<br>"uuid": "pmpdqhy7",<br>"imgName": "liwei2",<br>"version": "v1",<br>"updateTime": "2020-07-17 15:41:47",<br>"type": "public",<br>"imgType": "20e68cab-c634-468d-8d0c-56cb6096",<br>"imgSize": "46.26",<br>"userName": "liweitest",<br>"imgUrl": "os-harbor-svc.default.svc.cloudos:443/library/a2fd40ac47eb4efc9a2921d73afff465/liwei2:v1"<br>},<br>{<br>"uuid": "cuu0uknl",<br>"imgName": "testtt",<br>"version": "123",<br>"updateTime": "2020-08-04 15:29:30",<br>"type": "public",<br>"imgType": "20e68cab-c634-468d-8d0c-56cb6096",<br>"imgSize": "46.26",<br>"userName": "admin",<br>"imgUrl": "os-harbor-svc.default.svc.cloudos:443/library/1df71957d770436493a84f24d9344853/testtt:123"<br>}<br>],<br>"httpCode": 200<br>\} |  |  |  |

#### 未使用镜像清理

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/v2/unused/clear | JSON | POST | 未使用镜像清理 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgs | true | List | 清理镜像，imgUrl:清理镜像url，uuid：清理镜像ID |
| 例如：<br>{<br>Imgs:[<br>{“imgUrl”: “os-harbor-svc.default.svc.cloudos:443/library/2kdjvnckl12j3134132k/abc:test”, “uuid”: “tm4jzj3”},<br>{“imgUrl”: “os-harbor-svc.default.svc.cloudos:443/library/ccm165jcccswk5:cba:1”, “uuid”: “5mmkl1”}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| resoucres | List<String> | 清理镜像 | 编辑镜像名称：版本 |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","httpCode":200, "resources":[“abc:1”]\} |  |  |  |

#### 镜像仓库容量查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/v2/systeminfo/volumes | JSON | GET | 镜像仓库容量查询 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| data | Object | 返回查询结果 | 查询结果，total:总容量, used:已使用容量， usedRate:已使用比例（%） |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","httpCode":200, "data":{“total”: “49.1 GB”, “used”: “788.93MB”, “usedRate”: “15.1%”}\} |  |  |  |

#### 镜像仓库自动清理查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /retains | JSON | GET | 镜像仓库自动清理查询 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | fasle | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| data | Object | 返回查询结果 | 查询结果，inherit:是否继承, enabled:是否启用， retainDays:保留时长，projectId：<br>组织id，parentId：父组织id |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":{"inherit":false,"enabled":false,"retainDays":15,<br>"projectId":"9052ae85792143ff955c35c52e8e41bd","parentId":"default"},"httpCode":200\} |  |  |  |

#### 镜像仓库自动清理设置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /retains | JSON | POST | 镜像仓库自动清理设置 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| enabled | true | True/false | 是否开启自动清理 |
| inherit | true | True/false | 是否继承父组织 |
| projectId | true | String | 组织id |
| retainDays | true | Int | 保留时长 |
| 例如：<br>{<br>"enabled": true,<br>"inherit": false,<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"retainDays": 1<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":{"uuid":"root-auto-clean","enabled":1,"inherit":0,"projectId":"9052ae85792143ff955c35c52e8e41bd","parentId":"default","ttl":180,<br>"createdAt":1629455604000,"createBy":"root","lastModifiedAt":1635745378863,"lastModifiedBy":"root"},"httpCode":200\} |  |  |  |

#### 已存在镜像校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/checkImgExisted | JSON | POST | 查询镜像是否已经存在 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgName | true | String | 镜像名称 |
| version | true | List<String> | 镜像版本列表 |
| type | true | String | 目标镜像仓库类型：公有（public）或私有（private） |
| 例如<br>{<br>"imgName": "existed-test-admin-public",<br>"version": [<br>"1"<br>],<br>"type": "public",<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：（success：不存在镜像版本列表 ，failed：已存在镜像版本列表） |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"status": true,<br>"code": "img.0",<br>"msg": "ok",<br>"data": {<br>"success": [<br>"1"<br>],<br>"failed": []<br>},<br>"httpCode": 200<br>\} |  |  |  |

#### 已存在镜像类型校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types/checkName | JSON | POST | 查询镜像类型是否已经存在 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 镜像类型名称 |
| uuid | 创建镜像类型 false<br>编辑镜像类型 true | String | 镜像类型ID |
| 例如<br>{<br>"name": "abc123",<br>"uuid": "41c5db78-1226-4ce2-8721-3e2f7f9c"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：imgType.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：false，镜像类型不存在；true，镜像类型已存在 |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"status": true,<br>"code": "",<br>"msg": "ok",<br>"data": false,<br>"httpCode": 200<br>\} |  |  |  |

#### 远端地址下载镜像

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/remoteLoad | JSON | POST | 从第三方获取镜像 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgType | ture | String | 镜像所属分类 |
| type | true | String | 镜像所属仓库 |
| imgName | true | String | 镜像名称 |
| version | true | String | 镜像版本 |
| imgAlias | false | String | 镜像显示名称 |
| description | false | String | 镜像描述 |
| featureInfo | false | String | 镜像版本特性 |
| remoteUri | true | String | 远端下载地址，支持http,ftp,sftp |
| usePwd | false | String | 是否使用密码 |
| username | false | String | 远端地址账号 |
| password | false | String | 远端地址密码 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data为镜像ID |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 | 操作对象，正常情况下为“镜像名:版本” |
| 例如：<br>{<br>"code": "string",<br>"data": "string",<br>"httpCode": int,<br>"msg": "string",<br>"resources": ["string"],<br>\} |  |  |  |

#### 创建项目

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/projects | JSON | POST | 创建新的项目 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectName | true | String | 项目名称 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data为true |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 | 操作对象，正常情况下为“项目名称” |
| 例如：<br>{<br>"code": "string",<br>"data": "string",<br>"httpCode": int,<br>"msg": "string",<br>"resources": ["string"],<br>\} |  |  |  |

#### 检查项目是否存在

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/projects/ check/{projectName\} | JSON | GET | 检查项目名是否存在 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中exist的值为是否存在 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "{\"exist\":false}",<br>"httpCode": 200<br>\} |  |  |  |

#### 项目列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects | JSON | GET | 项目列表：/projects? page=1&size=10&name=&type=0 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| page | false | String | 页数 |
| size | false | String | 每页大小 |
| name | false | String | 项目名称 |
| type | true | String | 项目类型：公有私有 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": {<br>"total": 1,<br>"data": [<br>{<br>"creation_time": "2021-11-22T18:09:15.000+08:00",<br>"current_user_role_id": 1,<br>"metadata": {<br>"public": "false"<br>},<br>"update_time": "2021-11-22T18:09:15.000+08:00",<br>"owner_name": "admin",<br>"project_id": 68,<br>"owner_id": 1,<br>"name": "tass1234",<br>"current_user_role_ids": [<br>1<br>],<br>"cve_allowlist": {<br>"creation_time": "2021-11-22T18:09:15.000+08:00",<br>"update_time": "2021-11-22T18:09:15.000+08:00",<br>"project_id": 68,<br>"id": 60,<br>"items": []<br>},<br>"repo_count": 1<br>}<br>]<br>},<br>"httpCode": 200<br>\} |  |  |  |

#### 项目删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects /{project_id\} | JSON | DELETE | project_id：要删除的项目id |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": 200,<br>"httpCode": 200,<br>"resources": [<br>"String"<br>]<br>\} |  |  |  |

#### 上传镜像-远端上传

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/ remoteLoad | JSON | POST | 上传镜像 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgName | true | String | 镜像名称 |
| version | true | String | 镜像版本 |
| imgType | true | String | 镜像分类uuid |
| type | true | String | 镜像仓库类型 |
| fileflag | true | String | 镜像标识 |
| projectname | true | String | 项目名 |
| imgAlias | true | String | 镜像别名 |
| description | false | String | 镜像描述 |
| remoteUri | true | String | 远程地址 |
| usePwd | true | String | 是否需要验证 |
| username | false | String | 远端用户名 |
| password | false | String | 远端密码 |
| 例如： |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：镜像名 |
| httpCode | Integer | http码 | 200 |

#### 镜像下发到集群查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /image/distribution | JSON | GET | 请求url:/image/distribution?imgUrl= |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| imgUrl | true | String | 要下发的镜像 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": 200,<br>"httpCode": 200,<br>"resources": [<br>"String"<br>]<br>\} |  |  |  |

#### 获取镜像扫描状态

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/images/scan/{imgUrl\} | JSON | GET | imgUrl:镜像全 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": {<br>"scanStatus": "success",<br>"detail": "",<br>"imgName": "test",<br>"version": "v2",<br>"projectName": "test"<br>},<br>"httpCode": 200,<br>\} |  |  |  |

#### 校验用户名是否存在

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/user/check/{userName\} | JSON | GET | userName:用户名 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "{\"exist\":false}",<br>"httpCode": 200<br>\} |  |  |  |

#### 添加用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/ addHarborUser | JSON | POST | 新增用户 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| userName | true | String | 用户名 |
| password | true | String | 密码 |
| realName | true | String | 真实姓名 |
| email | true | String | 邮箱 |
| comment | False | String | 备注信息 |
| 例如：<br>{<br>"userName":"test",<br>"password":"test@h3c",<br>"realName":"test",<br>"email":"test@h3c.com",<br>"comment":""<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>"test"<br>]<br>\} |  |  |  |

#### 用户列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/users | JSON | GET | 获取用户列表：<br>/harbor/users?page=1&size=10&username=&ownerName= |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-<br>jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| page | false | String | 页数 |
| size | false | String | 每页大小 |
| username | false | String | 用户名，用于条件过滤 |
| ownerName | false | String | 项目拥有者 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": {<br>"total": 2,<br>"data": [<br>{<br>"uuid": "22d5fffb-416e-45ed-9c6f-06286347",<br>"name": "9052ae85792143ff955c35c52e8e41bd",<br>"realName": "9052ae85792143ff955c35c52e8e41bd",<br>"email": "9052ae85792143ff955c35c52e8e41bd@ccr.com",<br>"comment": "",<br>"tentantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tentantName": "root",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"creatorName": "admin",<br>"createTime": 1638526737000,<br>"harborId": "13"<br>}<br>]<br>},<br>"httpCode": 200<br>\} |  |  |  |

#### 重置用户密码

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/ users/password/{user_id\} | JSON | PUT | user_id:用户编号 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| oldPassword | true | String | 原密码 |
| newPassword | true | String | 新密码 |
| 例如：<br>{<br>oldPassword：原密码<br>newPassword：新密码<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>"9"<br>]<br>\} |  |  |  |

#### 删除用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/ users/{uuid}/{harborId\} | JSON | DELETE | uuid：用户数据id<br>harborId：用户harbor上的id |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>""<br>]<br>\} |  |  |  |

#### 项目成员列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects/{project_id}/members | JSON | GET | 获取用户列表：<br>/projects/{project_id}/members? page=1&size=10&entityname=m |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-<br>jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| project_id | true | String | 项目 |
| page | false | String | 页数 |
| size | false | String | 每页大小 |
| entityname | false | String | 模糊查询entityname的项目 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": {<br>"total": 2,<br>"data": [<br>]<br>},<br>"httpCode": 200<br>\} |  |  |  |

#### 绑定项目成员

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects/{project_id}/members | JSON | POST | project_id：项目id |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| role_id | true | String | 角色 |
| username | true | String | 绑定的用户名 |
| 例如：<br>{<br>"role_id": 1,<br>"member_user": {<br>"username": "9052ae85792143ff955c35c52e8e41bd_guest"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>""<br>]<br>\} |  |  |  |

#### 解绑项目成员

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects/{project_id}/members/{user_id\} | JSON | DELETE | project_id：项目id<br>user_id: 成员id |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>]<br>\} |  |  |  |

#### 修改项目成员信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/project/updateProjectUser | JSON | POST | 修改项目中成员的权限 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 项目id |
| projectName | true | String | 项目名称 |
| memberId | true | String | 成员id |
| memberName | true | String | 成员名称 |
| roleId | true | String | 角色id，1：项目管理员 2：开发者 3：访客 |
| 例如：<br>{<br>"projectId":"58",<br>"projectName":"test",<br>"memberId":"109",<br>"memberName":"aaa",<br>"roleId":"3"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>]<br>\} |  |  |  |

#### 访问凭证下载

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/systeminfo/getcert | JSON | GET | 下载访问凭证 |

###### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

###### 正常返回结果

| 正常返回二进制文件 |
| --- |

## 服务治理接口文档

### SpringCloud接口文档

#### 查询环境接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/clusters/all/environments | JSON | GET | 查询当前组织的所有spring cloud环境 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| orgId | true | String | 当前组织id |
| page | true | int | 页码（大于0的整数） |
| size | true | int | 页面的条数（大于0的整数) |
| engineName | false | String | 引擎名称 |
| engineType | false | String | 引擎类型 |
| 例如：<br>api/msg/springcloud/v1.0/clusters/all/environments?<br>t=1635580532449&orgId=9052ae85792143ff955c35c52e8e41bd&page=1&size=16 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"id": "w7qow3ofuwkytwsuvs0q",<br>"name": "sc-gx-def",<br>"description": "fsdf",<br>"namespace": "springcloudwiahkvey",<br>"clusterId": "123456",<br>"clusterType": "1",<br>"zone": "default",<br>"zoneId": null,<br>"version": "Greenwich.SR1",<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"createTime": 1634955111058,<br>"updateTime": 1634972794894,<br>"totalServerNum": 3,<br>"healthyServerNum": 3,<br>"state": "RUNNING",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"components": [{<br>"id": null,<br>"name": null,<br>"version": null,<br>"nodePort": null,<br>"requestCpu": null,<br>"requestMemory": null,<br>"limitCpu": null,<br>"limitMemory": null,<br>"clusterPort": null,<br>"replica": 1,<br>"status": "RUNNING",<br>"clusterAddress": "http://springcloud-gateway.springcloudwiahkvey:8000",<br>"externalAddress": "http://172.25.16.60:32585",<br>"createTime": "2021-10-23T02:12:01Z",<br>"labels": {<br>"app": "springcloud-gateway"<br>},<br>"middlewareCustomType": null,<br>"typeEnum": "GATEWAY",<br>"componentType": "springcloud-gateway",<br>"engineId": "w7qow3ofuwkytwsuvs0q"<br>}, {<br>"id": null,<br>"name": null,<br>"version": null,<br>"nodePort": null,<br>"requestCpu": null,<br>"requestMemory": null,<br>"limitCpu": null,<br>"limitMemory": null,<br>"clusterPort": null,<br>"replica": 1,<br>"status": "RUNNING",<br>"clusterAddress": "http://springcloud-config.springcloudwiahkvey:9090",<br>"externalAddress": "http://172.25.16.60:31527",<br>"createTime": "2021-10-23T02:11:56Z",<br>"labels": {<br>"app": "springcloud-config"<br>},<br>"middlewareCustomType": null,<br>"typeEnum": "CONFIG",<br>"componentType": "springcloud-config",<br>"engineId": "w7qow3ofuwkytwsuvs0q"<br>}, {<br>"id": null,<br>"name": null,<br>"version": null,<br>"nodePort": null,<br>"requestCpu": null,<br>"requestMemory": null,<br>"limitCpu": null,<br>"limitMemory": null,<br>"clusterPort": null,<br>"replica": 1,<br>"status": "RUNNING",<br>"clusterAddress": "http://springcloud-discover.springcloudwiahkvey:8761",<br>"externalAddress": "http://172.25.16.60:31001",<br>"createTime": "2021-10-23T02:11:53Z",<br>"labels": {<br>"commonId": "vymnpae7"<br>},<br>"middlewareCustomType": null,<br>"typeEnum": "DISCOVER",<br>"componentType": "springcloud-discover",<br>"engineId": "w7qow3ofuwkytwsuvs0q"<br>}],<br>"componentsList": "注册中心、网关、配置中心",<br>"engineType": "Spring Cloud",<br>"engineFrameworksId": null,<br>"namespaceId": null,<br>"engineIp": "172.25.16.60",<br>"clusterList": null<br>}],<br>"code": null,<br>"msg": null,<br>"total": 1<br>\} |  |  |  |

#### 查询微服务引擎类型

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/engine_frameworks | JSON | GET | 查询微服务引擎类型 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>api/msg/springcloud/v1.0/engine_frameworks?t=1635581335789 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"id": "dubbo-2.7.3",<br>"name": "dubbo",<br>"version": "2.7.3",<br>"createTime": "1634875029",<br>"type": "framework",<br>"frameworkComponents": [{<br>"id": "dubbo-zookeeper",<br>"name": "dubbo-zookeeper",<br>"version": "v3.5.6",<br>"createTime": "1634875017",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "nacos-2.0.1",<br>"name": "nacos",<br>"version": "2.0.1",<br>"createTime": "1634875029",<br>"type": "Component",<br>"frameworkComponents": null<br>}]<br>}, {<br>"id": "springcloud-finchley",<br>"name": "Spring Cloud",<br>"version": "Finchley.SR4",<br>"createTime": "1634875008",<br>"type": "framework",<br>"frameworkComponents": [{<br>"id": "springcloud-config-finchley",<br>"name": "springcloud-config",<br>"version": "Finchley.SR4",<br>"createTime": "1634874992",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-discover-finchley",<br>"name": "springcloud-discover",<br>"version": "Finchley.SR4",<br>"createTime": "1634874991",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-discover-svc-finchley",<br>"name": "springcloud-discover-svc",<br>"version": "Finchley.SR4",<br>"createTime": "1634874991",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-gateway-finchley",<br>"name": "springcloud-gateway",<br>"version": "Finchley.SR4",<br>"createTime": "1634874992",<br>"type": "Component",<br>"frameworkComponents": null<br>}]<br>}, {<br>"id": "springcloud-greenwich",<br>"name": "Spring Cloud",<br>"version": "Greenwich.SR1",<br>"createTime": "1634874990",<br>"type": "framework",<br>"frameworkComponents": [{<br>"id": "springcloud-config-greenwich",<br>"name": "springcloud-config",<br>"version": "Greenwich.SR1",<br>"createTime": "1634874981",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-discover-greenwich",<br>"name": "springcloud-discover",<br>"version": "Greenwich.SR1",<br>"createTime": "1634874980",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-discover-svc-greenwich",<br>"name": "springcloud-discover-svc",<br>"version": "Greenwich.SR1",<br>"createTime": "1634874980",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-gateway-greenwich",<br>"name": "springcloud-gateway",<br>"version": "Greenwich.SR1",<br>"createTime": "1634874980",<br>"type": "Component",<br>"frameworkComponents": null<br>}]<br>}, {<br>"id": "springcloud-hoxton",<br>"name": "Spring Cloud",<br>"version": "Hoxton.SR8",<br>"createTime": "1634875029",<br>"type": "framework",<br>"frameworkComponents": [{<br>"id": "springcloud-config-hoxton",<br>"name": "springcloud-config",<br>"version": "Hoxton.SR8",<br>"createTime": "1634875011",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-discover-hoxton",<br>"name": "springcloud-discover",<br>"version": "Hoxton.SR8",<br>"createTime": "1634875009",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-discover-svc-hoxton",<br>"name": "springcloud-discover-svc",<br>"version": "Hoxton.SR8",<br>"createTime": "1634875009",<br>"type": "Component",<br>"frameworkComponents": null<br>}, {<br>"id": "springcloud-gateway-hoxton",<br>"name": "springcloud-gateway",<br>"version": "Hoxton.SR8",<br>"createTime": "1634875010",<br>"type": "Component",<br>"frameworkComponents": null<br>}]<br>}],<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 查询环境中的组件概况信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ clusters/{cluster_id}/environments/{env_id}/components | JSON | GET | 查询spring cloud环境env_id中spring cloud组件的信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| cluster_id | true | String | 集群ID |
| env_id | true | String | 环境ID |
| 例如：<br>/api/msg/springcloud/v1.0/ clusters/123456/environments/9052ae85792143ff955c35c52e8e41bd /components |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": "ue9rrpjhzgcbb5cddjcq",<br>"name": "spring-cloud-f",<br>"description": "",<br>"namespace": "springclouds3i4c4o0",<br>"clusterId": "123456",<br>"clusterType": "1",<br>"zone": "default",<br>"zoneId": null,<br>"version": "Finchley.SR4",<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"createTime": 1635340327493,<br>"updateTime": 1635340327493,<br>"totalServerNum": 0,<br>"healthyServerNum": 0,<br>"state": "RUNNING",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"components": [{<br>"id": null,<br>"name": null,<br>"version": null,<br>"nodePort": null,<br>"requestCpu": null,<br>"requestMemory": null,<br>"limitCpu": null,<br>"limitMemory": null,<br>"clusterPort": null,<br>"replica": 1,<br>"status": "RUNNING",<br>"clusterAddress": "http://springcloud-config.springclouds3i4c4o0:9090",<br>"externalAddress": "http://10.125.31.185:31166",<br>"createTime": "2021-10-27T13:12:19Z",<br>"labels": {<br>"app": "springcloud-config"<br>},<br>"middlewareCustomType": null,<br>"typeEnum": "CONFIG",<br>"componentType": "springcloud-config",<br>"engineId": "ue9rrpjhzgcbb5cddjcq"<br>}, {<br>"id": null,<br>"name": null,<br>"version": null,<br>"nodePort": null,<br>"requestCpu": null,<br>"requestMemory": null,<br>"limitCpu": null,<br>"limitMemory": null,<br>"clusterPort": null,<br>"replica": 2,<br>"status": "RUNNING",<br>"clusterAddress": "http://springcloud-discover.springclouds3i4c4o0:8761",<br>"externalAddress": "http://10.125.31.185:30050",<br>"createTime": "2021-10-27T13:12:11Z",<br>"labels": {<br>"commonId": "wliswt0r"<br>},<br>"middlewareCustomType": null,<br>"typeEnum": "DISCOVER",<br>"componentType": "springcloud-discover",<br>"engineId": "ue9rrpjhzgcbb5cddjcq"<br>}],<br>"componentsList": "paas.microservice.engine.register、paas.microservice.engine.configcenter",<br>"engineType": "1",<br>"engineFrameworksId": null,<br>"namespaceId": null,<br>"engineIp": "10.125.31.185",<br>"clusterList": null<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 查询环境中的组件详情信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/components/{env_id\} | JSON | GET | 查询spring cloud环境env_id中spring cloud组件的信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| 例如：<br>/api/msg/springcloud/v1.0/components/w7qow3ofuwkytwsuvs0q?t=1635392350498 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"components": [{<br>"id": "8pob1nm2q4405jgle8hp",<br>"name": "springcloud-config",<br>"type": 2,<br>"version": "2.0.9.RELEASE",<br>"createTime": 1635340326933,<br>"replica": 1,<br>"nodePort": "31166",<br>"requestCpu": "0.5",<br>"requestMemory": "512Mi",<br>"limitCpu": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": "9090",<br>"middlewareCustomType": 0,<br>"engineId": "ue9rrpjhzgcbb5cddjcq"<br>}, {<br>"id": "rxcbxazdh87e4aih1rhn",<br>"name": "springcloud-discover",<br>"type": 0,<br>"version": "2.0.9.RELEASE",<br>"createTime": 1635340325411,<br>"replica": 2,<br>"nodePort": "30050",<br>"requestCpu": "0.5",<br>"requestMemory": "512Mi",<br>"limitCpu": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": "8761",<br>"middlewareCustomType": 0,<br>"engineId": "ue9rrpjhzgcbb5cddjcq"<br>}],<br>"middleware": [{<br>"id": "cm1jbl8tbgwryzswntgt",<br>"middlewareType": "mysql",<br>"configType": 0,<br>"instanceName": null,<br>"username": "",<br>"password": null,<br>"url": "",<br>"port": "",<br>"createTime": 1635340326949,<br>"updateTime": 1635340326949,<br>"instanceId": null,<br>"envId": null<br>}]<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 查询引擎版本数据接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/components/version<br>/{type}/{version}?t=1635581576665 | JSON | GET | 查询引擎版本数据接口 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| type | true | String | 引擎类型 |
| version | true | int | 版本信息 |
| 例如：<br>api/msg/springcloud/v1.0/components/version/gateway-Finchley.SR4/Finchley.SR4?t=1635581576665 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"name": "Spring Cloud Gateway",<br>"version": "2.0.9"<br>}],<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 创建环境（部署Spring Cloud组件）

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ clusters/{cluster_id}/environments | JSON | POST | 创建spring cloud环境，并部署spring cloud组件 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| orgId | true | String | 组织ID |
| cluster_id | true | String | 集群ID |
| name | true | String | 环境名称 |
| description | true | String | 环境描述 |
| version | true | String | 环境版本 |
| zone | true | String | 可用域 |
| zoneId | true | String | 可用域ID |
| engineType | true | String | 引擎类型 |
| platMiddle | true | int | 中间件类型 |
| components | true | json | 组件配置信息 |
| middleWares | true | json | 中间件配置信息 |
| needApproval | false | boolean | 是否是流程申请，true申请，false创建 |
| middlewareCustomType | true | Int | 中间件类型 |
| clusterType | true | int | 集群类型:1 共享集群 2kaas集群 |
| 例如：<br>1.部署springcloud原生组件<br>{<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "sss-test",<br>"description": "",<br>"version": "Finchley.SR4",<br>"engineIp": "",<br>"zone": "tets",<br>"zoneId": "2df75ce8-7",<br>"clusterType": 1,<br>"engineType": "1",<br>"middlewareCustomType": 0,<br>"components": [{<br>"version": "2.0.9",<br>"duplicate": 1,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"storageClass": "",<br>"clusterPort": "",<br>"nodePort": "",<br>"component": "discover"<br>}, {<br>"component": "config",<br>"version": "2.0.9",<br>"duplicate": 1,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": "",<br>"nodePort": ""<br>}, {<br>"component": "gateway",<br>"version": "2.0.9",<br>"duplicate": 1,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": "",<br>"nodePort": ""<br>}],<br>"middleWares": [{<br>"middleWare": "mysql",<br>"url": "",<br>"port": "",<br>"user": "",<br>"passWord": ""<br>}, {<br>"middleWare": "redis",<br>"url": "",<br>"port": "",<br>"passWord": ""<br>}],<br>"needApproval": false<br>}<br>2.部署spring cloud nacos组件<br>{<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "test",<br>"description": "",<br>"version": "Finchley.SR4",<br>"engineIp": "",<br>"zone": "主集群工作节点",<br>"zoneId": "929f1d01-f",<br>"clusterType": 1,<br>"engineType": "1",<br>"middlewareCustomType": 0,<br>"components": [<br>{<br>"version": "2.0.1",<br>"duplicate": 1,<br>"reqCPU": "1",<br>"reqMemory": "1024Mi",<br>"limitCPU": "2",<br>"limitMemory": "2048Mi",<br>"storageClass": "ads",<br>"clusterPort": "",<br>"nodePort": "",<br>"component": "nacos"<br>}<br>],<br>"middleWares": [<br>{<br>"middleWare": "mysql",<br>"url": "",<br>"port": "",<br>"user": "",<br>"passWord": ""<br>}<br>],<br>"needApproval": false,<br>"componentType": "nacos"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"name": "xxx-test",<br>"description": "",<br>"clusterType": 1,<br>"version": "Finchley.SR4",<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"zone": "tets",<br>"zoneId": "2df75ce8-7",<br>"engineType": "1",<br>"middlewareCustomType": 0,<br>"namespace": null,<br>"clusterId": "123456",<br>"id": "b1tuttadr3jlnsfzyhou",<br>"userId": null,<br>"engineIp": "",<br>"needApproval": false,<br>"components": [{<br>"component": "discover",<br>"duplicate": 1,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": "",<br>"nodePort": "",<br>"version": "2.0.9.RELEASE"<br>}, {<br>"component": "config",<br>"duplicate": 1,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": "",<br>"nodePort": "",<br>"version": "2.0.9.RELEASE"<br>}, {<br>"component": "gateway",<br>"duplicate": 1,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": "",<br>"nodePort": "",<br>"version": "2.0.9.RELEASE"<br>}],<br>"middleWares": [{<br>"middleWare": "mysql",<br>"url": "",<br>"port": "",<br>"user": "",<br>"passWord": "",<br>"instanceId": null,<br>"instanceName": null,<br>"plantPassWord": null<br>}, {<br>"middleWare": "redis",<br>"url": "",<br>"port": "",<br>"user": null,<br>"passWord": "",<br>"instanceId": null,<br>"instanceName": null,<br>"plantPassWord": null<br>}]<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 编辑环境

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ clusters/{cluster_id}/environments | JSON | PUT | 编辑spring cloud环境信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| cluster_id | true | String | 集群ID |
| name | true | String | 环境名称 |
| description | true | String | 环境描述 |
| id | true | String | 环境ID |
| 例如：<br>{<br>"id": "dve2xoly3pumdnvfntzi",<br>"name": "d27028cloud",<br>"engineIp": "10.125.31.185",<br>"description": "xx"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": "zciv2uuzzzlwowtbomok",<br>"name": "testDemo",<br>"description": "",<br>"namespace": "springcloudbxqe3haj",<br>"clusterType": 1,<br>"clusterId": "123456",<br>"zone": "default",<br>"version": "Greenwich.SR1",<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"userId": "1df71957d770436493a84f24d9344853",<br>"createTime": 1635147420613,<br>"updateTime": 1635393913780,<br>"engineType": "1",<br>"engineIp": "172.25.16.60"<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 删除环境（移除组件）

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ clusters/{cluster_id}/environment/{envId\} | JSON | DELETE | 删除spring cloud环境，并移除spring cloud组件 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| cluster_id | true | String | 集群ID |
| envId | true | String | 环境ID |
| 例如：<br>api/msg/springcloud/v1.0/clusters/123456/environment/xwxp2szzd4injaummmod |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null<br>\} |  |  |  |

#### 查询环境上的服务详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ environments/{environment_id}/services | JSON | GET | 查询某个环境上的所有服务 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| environment_id | true | String | 环境ID |
| 例如：<br>/api/msg/springcloud/v1.0//environments/1/services |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"name": "comment-vm",<br>"svcName": null,<br>"nameSpace": "space7fz830w8",<br>"clusterId": "123456",<br>"innerAddr": null,<br>"outerAddr": null,<br>"instancesIp": ["10.125.41.74"],<br>"instances": 1,<br>"healthyInstances": 1,<br>"envId": "h7zfppzciqwddh29xhff",<br>"stateEnum": "RUNNING",<br>"serviceOverView": null,<br>"instanceDtoList": [{<br>"instanceId": "CMP-31185-Tomcat:comment-vm:32101",<br>"hostName": "10.125.41.74",<br>"status": "UP",<br>"ip": "10.125.41.74",<br>"port": 32101,<br>"registrationTimestamp": 1635318573088,<br>"lastRenewalTimestamp": 1635577635053<br>}]<br>}, {<br>"name": "comment-error",<br>"svcName": null,<br>"nameSpace": "space7fz830w8",<br>"clusterId": "123456",<br>"innerAddr": null,<br>"outerAddr": null,<br>"instancesIp": ["10.243.0.137"],<br>"instances": 1,<br>"healthyInstances": 1,<br>"envId": "h7zfppzciqwddh29xhff",<br>"stateEnum": "RUNNING",<br>"serviceOverView": null,<br>"instanceDtoList": [{<br>"instanceId": "comment-error-7dc99d6b5b-scr7h:comment-error:32101",<br>"hostName": "10.243.0.137",<br>"status": "UP",<br>"ip": "10.243.0.137",<br>"port": 32101,<br>"registrationTimestamp": 1635403509809,<br>"lastRenewalTimestamp": 1635577635722<br>}]<br>}, {<br>"name": "user-new",<br>"svcName": null,<br>"nameSpace": "space7fz830w8",<br>"clusterId": "123456",<br>"innerAddr": null,<br>"outerAddr": null,<br>"instancesIp": ["10.243.0.11"],<br>"instances": 1,<br>"healthyInstances": 1,<br>"envId": "h7zfppzciqwddh29xhff",<br>"stateEnum": "RUNNING",<br>"serviceOverView": null,<br>"instanceDtoList": [{<br>"instanceId": "user-new-5bb486784c-g662z:user-new:32099",<br>"hostName": "10.243.0.11",<br>"status": "UP",<br>"ip": "10.243.0.11",<br>"port": 32099,<br>"registrationTimestamp": 1635318577165,<br>"lastRenewalTimestamp": 1635577635722<br>}]<br>}],<br>"code": null,<br>"msg": null,<br>"total": 3<br>\} |  |  |  |

#### 监控

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ environments/{environment_id}/services/all/metrics | JSON | GET | 查询监控信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| environment_id | true | String | 环境ID |
| page | true | String | 页码（大于0的整数） |
| size | true | String | 页面的条数（大于0的整数） |
| serviceName | false | String | 服务名称 |
| 例如：<br>api/msg/springcloud/v1.0/environments/h7zfppzciqwddh29xhff/services/all/metrics?page=1&size=4&serviceName=comment-vm&t=1635582807795 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"name": "comment-vm",<br>"svcName": null,<br>"nameSpace": null,<br>"clusterId": null,<br>"innerAddr": null,<br>"outerAddr": null,<br>"instancesIp": ["10.125.41.74"],<br>"instances": 1,<br>"healthyInstances": 1,<br>"envId": "h7zfppzciqwddh29xhff",<br>"stateEnum": "RUNNING",<br>"serviceOverView": {<br>"requestsTotal": 0,<br>"requestsError": 0,<br>"throughtput": "0.0",<br>"avgResponseTme": "0",<br>"quantiles": {<br>"95th": "0",<br>"99.9th": "0",<br>"80th": "0",<br>"50th": "0"<br>},<br>"last5MinRequestsMatrix": {<br>"name": null,<br>"chars": {}<br>},<br>"trafficHealth": null<br>},<br>"instanceDtoList": [{<br>"instanceId": "CMP-31185-Tomcat:comment-vm:32101",<br>"hostName": "10.125.41.74",<br>"status": "UP",<br>"ip": "10.125.41.74",<br>"port": 32101,<br>"registrationTimestamp": 1635318573088,<br>"lastRenewalTimestamp": 1635577844716<br>}]<br>}],<br>"code": "200",<br>"msg": "查询成功！",<br>"total": 3<br>\} |  |  |  |

#### 服务监控查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{engine_id}/services/{service_name}/dashboard | JSON | GET | 查询服务监控详情 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engine_id | true | String | 引擎ID |
| service_name | true | String | 服务名 |
| start | True | Long | 开始时间 |
| end | True | Long | 结束时间 |
| instance_ip | true | string | 实例ip |
| t | True | Long | 当前系统时间戳 |
| 例如：<br>api/msg/springcloud/v1.0/environments/h7zfppzciqwddh29xhff/services/comment-vm/dashboard?start=1635581062217&end=1635582862217&instance_ip=10.125.41.74&t=1635582862280 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如<br>{<br>"data": {<br>"name": null,<br>"chars": {<br>"avgResponseTime": {<br>"unit": "ms",<br>"valueList": [{<br>"timestamp": 1602495004,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495124,<br>"value": "0"<br>}]<br>},<br>"topKSlowMethod": {<br>"unit": "s",<br>"values": {<br>"/test-env": [{<br>"timestamp": 1602495131,<br>"value": "0.003589129742975171"<br>}],<br>"/**": [{<br>"timestamp": 1602495131,<br>"value": "0.109893211"<br>}]<br>}<br>},<br>"requestMatrixByPod": {<br>"unit": null,<br>"values": {<br>"10.242.1.113:32077": [{<br>"timestamp": 1602495004,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495124,<br>"value": "0"<br>}]<br>}<br>},<br>"requestSuccessRate": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1602495004,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495124,<br>"value": "0"<br>}]<br>},<br>"responseQuantile": {<br>"unit": "s",<br>"values": {<br>"80th": [{<br>"timestamp": 1602495004,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495124,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495244,<br>"value": "0"<br>}],<br>"50th": [{<br>"timestamp": 1602495004,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495124,<br>"value": "0"<br>}],<br>"95th": [{<br>"timestamp": 1602495004,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495124,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495244,<br>"value": "0"<br>}],<br>"99th": [{<br>"timestamp": 1602495004,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495124,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495244,<br>"value": "0"<br>}]<br>}<br>},<br>"requestMatrix": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1602495004,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495124,<br>"value": "0"<br>}]<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 实例监控查询

##### 参数说明

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{engine_id}/services/{service_name}/instances/dashboard | JSON | GET | 查询实例监控信息 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engine_id | true | String | 引擎ID |
| service_name | true | String | 服务名 |
| start | True | Long | 开始时间 |
| end | True | Long | 结束时间 |
| instance_ip | true | string | 实例ip |
| t | True | Long | 当前系统时间戳 |
| 例如：<br>msg/springcloud/v1.0/environments/xismcmhycj4qgzlwsefj/services/hystrix-test-server/instances/dashboard?start=1604112137377&end=1604113937377&instance_ip=10.125.42.175&t=1604113937413 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如<br>{<br>"data": {<br>"name": null,<br>"chars": {<br>"gcHistogram": {<br>"unit": "",<br>"values": {<br>"jvm_yong_gc_count": [{<br>"timestamp": 1602743049,<br>"value": "0.017"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0.017"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0.017"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0.017"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0.026"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0.017"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0.017"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0.017"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0.026"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0.009"<br>}],<br>"jvm_old_gc_count": [{<br>"timestamp": 1602743049,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0"<br>}],<br>"jvm_old_gc_time": [{<br>"timestamp": 1602743049,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0"<br>}],<br>"jvm_yong_gc_time": [{<br>"timestamp": 1602743049,<br>"value": "0.02"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0.013"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0.016"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0.01"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0.01"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0.01"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0.008"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0.009"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0.016"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0.024"<br>}]<br>}<br>},<br>"hystrixBreakCount": {<br>"unit": null,<br>"values": {<br>"test": [{<br>"timestamp": 1602743049,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0"<br>}]<br>}<br>},<br>"hystrixBreakStatus": {<br>"unit": null,<br>"values": {<br>"test": [{<br>"timestamp": 1602743049,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744249,<br>"value": "0"<br>}]<br>}<br>},<br>"requestVolume": {<br>"unit": "ops",<br>"values": {<br>"200": [{<br>"timestamp": 1602743049,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0"<br>}]<br>}<br>},<br>"responseMatrix": {<br>"unit": "s",<br>"valueList": [{<br>"timestamp": 1602743049,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0"<br>}]<br>},<br>"jvmHistogram": {<br>"unit": "MB",<br>"values": {<br>"jvm_memory_used": [{<br>"timestamp": 1602743049,<br>"value": "143.765"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "147.363"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "151.039"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "154.395"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "142.587"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "146.026"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "149.615"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "153.256"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "141.753"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "150.51"<br>},<br>{<br>"timestamp": 1602744249,<br>"value": "150.51"<br>}],<br>"jvm_buffer_memory": [{<br>"timestamp": 1602743049,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "0.078"<br>},<br>{<br>"timestamp": 1602744249,<br>"value": "0.078"<br>}],<br>"jvm_memory_committed": [{<br>"timestamp": 1602743049,<br>"value": "162.609"<br>},<br>{<br>"timestamp": 1602743169,<br>"value": "162.672"<br>},<br>{<br>"timestamp": 1602743289,<br>"value": "162.734"<br>},<br>{<br>"timestamp": 1602743409,<br>"value": "162.797"<br>},<br>{<br>"timestamp": 1602743529,<br>"value": "162.797"<br>},<br>{<br>"timestamp": 1602743649,<br>"value": "162.797"<br>},<br>{<br>"timestamp": 1602743769,<br>"value": "162.797"<br>},<br>{<br>"timestamp": 1602743889,<br>"value": "162.859"<br>},<br>{<br>"timestamp": 1602744009,<br>"value": "162.984"<br>},<br>{<br>"timestamp": 1602744129,<br>"value": "162.984"<br>},<br>{<br>"timestamp": 1602744249,<br>"value": "162.984"<br>}]<br>}<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 接口列表查询

##### 参数说明

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{engine_id}/services/{service_name\} | JSON | GET | 查询服务接口列表 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engine_id | true | String | 引擎ID |
| service_name | true | String | 服务名 |
| instance_ip | true | string | 实例ip |
| t | True | Long | 当前系统时间戳 |
| 例如：<br>msg/springcloud/v1.0/environments/evaizfg0e1rnfuss8son/services/book-user?instance_ip=10.242.1.199&t=1604115026748 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [<br>"GET/test-env",<br>"GET/**"<br>],<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 接口监控查询

##### 参数说明

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{engine_id}/services/{service_name}/methods/dashboard | JSON | GET | 查询接口监控详情 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engine_id | true | String | 引擎ID |
| service_name | true | String | 服务名 |
| start | True | Long | 开始时间 |
| end | True | Long | 结束时间 |
| instance_ip | true | string | 实例ip |
| t | True | Long | 当前系统时间戳 |
| 例如：<br>api/msg/springcloud/v1.0/environments/h7zfppzciqwddh29xhff/services/<br>comment-error/methods/dashboard?start=1635581560294&end=1635583360294<br>&instance_ip=10.243.0.137&method=GET/comments/{typeId}&t=1635583360774 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如<br>{<br>"data": {<br>"name": null,<br>"chars": {<br>"requestCount": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1602495483,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495603,<br>"value": "0"<br>}]<br>},<br>"avgResponseTime": {<br>"unit": "ms",<br>"valueList": [{<br>"timestamp": 1602495483,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495603,<br>"value": "0"<br>}]<br>},<br>"successRate": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1602495483,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495603,<br>"value": "0"<br>}]<br>},<br>"responseTime": {<br>"unit": "s",<br>"values": {<br>"80th": [{<br>"timestamp": 1602495483,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495603,<br>"value": "0"<br>}],<br>"50th": [{<br>"timestamp": 1602495483,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495603,<br>"value": "0"<br>}],<br>"95th": [{<br>"timestamp": 1602495483,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495603,<br>"value": "0"<br>}],<br>"99th": [{<br>"timestamp": 1602495483,<br>"value": "0"<br>},<br>{<br>"timestamp": 1602495603,<br>"value": "0"<br>}]<br>}<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 查询配置中心配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ environments/{env_id}/configurations | JSON | GET | 查询配置信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| configValue | false | String | 配置名称 |
| serviceName | false | String | 服务名称 |
| version | false | String | 环境 |
| configKey | false | String | 配置key |
| 例如：<br>api/msg/springcloud/v1.0/environments/h7zfppzciqwddh29xhff/configurations?<br>t=1635583622850&size=10&page=1&serviceName=xx&version=xx&configKey=xx&configValue=xx |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"id": "jk6rqwp7gu2abajjkeyy",<br>"name": "color",<br>"envId": "w7qow3ofuwkytwsuvs0q",<br>"application": "library-bookinfo-springcloud",<br>"profile": "pro",<br>"label": "v1",<br>"configKey": "color",<br>"configValue": "red",<br>"configType": "common",<br>"createTime": 1635153857667,<br>"updateTime": 1635153857667,<br>"states": 0<br>}],<br>"code": "200",<br>"msg": null,<br>"total": 1<br>\} |  |  |  |

#### 添加配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ environments/{env_id}/configurations | JSON | POST | 新增配置信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| configKey | true | String | 配置名称 |
| configValue | true | String | 配置值 |
| application | true | String | 应用配置服务名称 |
| 例如：<br>{<br>"name":"test-config-name",<br>"configKey":"test-config",<br>"configValue":"test-config",<br>"application":"test-config-application",<br>"profile":"test-config-profile",<br>"label":"test-config-label"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": "a71830ac-2209-47e6-9c2b-1a6e43cc3d77",<br>"name": "test-config-name",<br>"configKey": "test-config",<br>"configValue": "test-config",<br>"application": "test-config-application",<br>"profile": "test-config-profile",<br>"label": "test-config-label",<br>"updateTime": 1560157438087<br>},<br>"code": "",<br>"msg": "",<br>"total": null<br>\} |  |  |  |

#### 添加配置(文件形式)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ environments/{env_id}/configurations/ file | JSON | POST | 添加配置信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| userinfo | true | String | 用户信息 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| configFile | true | MultipartFile | 配置文件 |
| 例如：<br>文件上传 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>“#fileName”：“xxxx”<br>},<br>"code": "",<br>"msg": "",<br>"total": null<br>\} |  |  |  |

#### 编辑配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ environments/{env_id}/configurations/{config_id\} | JSON | PUT | 编辑配置信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| configKey | true | String | 配置名称 |
| configValue | true | String | 配置值 |
| config_id | true | String | 配置ID |
| 例如：<br>{<br>"name": "xxx",<br>"configKey": "xx",<br>"configValue": "xx",<br>"application": "xx",<br>"profile": "xx",<br>"label": "xx"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": "vcw1neofjbm9i9pbzgio",<br>"name": "xx",<br>"envId": "h7zfppzciqwddh29xhff",<br>"application": "xx",<br>"profile": "xx",<br>"label": "xx",<br>"configKey": "xx",<br>"configValue": "xx",<br>"configType": null,<br>"createTime": 1635578764290,<br>"updateTime": 1635578764290,<br>"states": 0<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 删除配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ environments/{env_id}/configurations/{config_id\} | JSON | DELETE | 从配置中心中删除配置信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| config_id | true | String | 配置ID |
| 例如：<br>无 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null<br>\} |  |  |  |

#### 批量删除配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/ environments/{env_id}/configurations | JSON | DELETE | 从配置中心中删除配置信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| 例如：<br>[<br>"1",<br>"2"<br>] |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null<br>\} |  |  |  |

#### 网关路由处查询微服务引擎服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/discovers/<br>environments/{envId}?t=1635584559055 | JSON | GET | 查询服务信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| envId | true | String | 环境ID |
| 例如：<br>无 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"name": "SERVICE-F",<br>"instance": [{<br>"instanceId": "10.125.104.96:23123",<br>"hostName": "10.125.104.96",<br>"app": "SERVICE-F",<br>"ipAddr": "10.125.104.96",<br>"status": "UP",<br>"overriddenStatus": "UNKNOWN",<br>"port": {<br>"$": 23123,<br>"@enabled": "true"<br>},<br>"securePort": {<br>"$": 443,<br>"@enabled": "false"<br>},<br>"countryId": 1,<br>"dataCenterInfo": {<br>"@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",<br>"name": "MyOwn"<br>},<br>"leaseInfo": {<br>"renewalIntervalInSecs": 10,<br>"durationInSecs": 30,<br>"registrationTimestamp": 1635579580984,<br>"lastRenewalTimestamp": 1635579620870,<br>"evictionTimestamp": 0,<br>"serviceUpTimestamp": 1635579580475<br>},<br>"metadata": {<br>"management.port": "23123",<br>"version": "default"<br>},<br>"homePageUrl": "http://10.125.104.96:23123/",<br>"statusPageUrl": "http://10.125.104.96:23123/actuator/info",<br>"healthCheckUrl": "http://10.125.104.96:23123/actuator/health",<br>"vipAddress": "service-f",<br>"secureVipAddress": "service-f",<br>"isCoordinatingDiscoveryServer": "false",<br>"lastUpdatedTimestamp": "1635579580984",<br>"lastDirtyTimestamp": "1635584505747",<br>"actionType": "ADDED"<br>}]<br>}],<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

##### 错误返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | bingo.serviceChain.insertion.modFailed | 修改失败 |
| msg | String | cloudos.serviceChain.modFailed | 修改服务链失败 |
| data | Object | null | 返回空 |
| total | Integer | 0 | 返回0个 |
| 例如：<br>{<br>"code": " springcloud.10012 ",<br>"msg": "非法参数，请重新输入参数",<br>"data": null,<br>"total": 0<br>\} |  |  |  |

#### 添加网关路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/routes | JSON | POST | 添加服务对应的网关路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| envId | true | String | 环境ID |
| url | true | String | 路由URL |
| serverName | true | String | 服务名称 |
| 例如：<br>{<br>"envId": "1e0d91d2-3d4d-4d4c-befc-28f8012a4db0", 环境ID<br>"url": "/gg/**", 路由URL，格式必须为/xxx/**<br>"serverName": "a-server"，服务名称<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": "ab0pelqk216ag6ibceai",<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 编辑网关路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/routes | JSON | PUT | 编辑服务对应的网关路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| envId | true | String | 环境ID |
| url | true | String | 路由URL |
| serverName | true | String | 服务名称 |
| 例如：<br>{<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"routerId": "jz82spoxtyfmc2lvtvz1",<br>"url": "/service-f/yyy/**",<br>"serverName": "SERVICE-F"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": "ab0pelqk216ag6ibceai",<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 查询网关路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>routes/environments/{envId\} | JSON | GET | 获取环境下所有的网关路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| envId | true | String | 环境ID |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| type | false | String | 查询类型 |
| key | false | String | 查询值 |
| 例如：<br>api/msg/springcloud/v1.0/routes/environments/cn16xd7xqkfgj0vrovo0<br>?t=1635584869412&size=10&page=1&type=serverName&key=xx |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"msg": "",<br>"total": 3,<br>"code": "",<br>"data": [{<br>"envId": "w7qow3ofuwkytwsuvs0q",<br>"routerId": "01yf8pmrfxfstn8iztmb",<br>"url": "/library-bookinfo-springcloud/books/**",<br>"serverName": "LIBRARY-BOOKINFO-SPRINGCLOUD",<br>"createTime": 1635143983608,<br>"subsidiaryDtoList": []<br>}, {<br>"envId": "w7qow3ofuwkytwsuvs0q",<br>"routerId": "hy1kf34mef8mzmtvad7b",<br>"url": "/library-comment-springcloud-svc/comments/1/**",<br>"serverName": "LIBRARY-COMMENT-SPRINGCLOUD-SVC",<br>"createTime": 1635144304753,<br>"subsidiaryDtoList": [{<br>"id": "rovsedzhqcvuwkgx95cy",<br>"routeId": null,<br>"name": "Hystrix",<br>"args": {<br>"circuitBreaker_errorThresholdPercentage": "50",<br>"circuitBreaker_forceOpen": "false",<br>"circuitBreaker_sleepWindowInMilliseconds": "10000",<br>"name": "BBB",<br>"circuitBreaker_requestVolumeThreshold": "2",<br>"circuitBreaker_enabled": "false"<br>},<br>"envId": "w7qow3ofuwkytwsuvs0q",<br>"serviceName": null,<br>"url": null,<br>"type": "filter",<br>"createTime": 1635149756908,<br>"updateTime": 1635149914198<br>}]<br>}, {<br>"envId": "w7qow3ofuwkytwsuvs0q",<br>"routerId": "vhhlprhxjexctdw3ziax",<br>"url": "/library-userinfo-springcloud-svc/xxx/**",<br>"serverName": "LIBRARY-USERINFO-SPRINGCLOUD-SVC",<br>"createTime": 1635402861103,<br>"subsidiaryDtoList": []<br>}]<br>\} |  |  |  |

#### 删除网关路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>routes/{id\} | JSON | DELETE | 根据ID删除服务路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 路由ID |
| env_id | true | String | 环境ID |
| 例如：<br>api/msg/springcloud/v1.0/routes/jegelqvau40uht0onznd/?env_id=cn16xd7xqkfgj0vrovo0 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 查询熔断规则列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{env_id}/governance/curcuitbreaker/strategies | JSON | GET | 查询spring cloud环境env_id中所有熔断规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| page | false | int | 页码 |
| size | false | int | 页面大小 |
| type | false | String | 服务名称 |
| key | false | String | 环境 |
| 例如：<br>/api/msg/springcloud/v1.0/environments/1/governance/curcuitbreaker/strategies |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"envId": null,<br>"application": "SERVICE-F",<br>"profile": "xx",<br>"label": "xx",<br>"configType": null,<br>"trigger": "auto",<br>"sleepWindow": "10",<br>"errorPercentage": "11",<br>"requestCount": "11",<br>"createTime": 1635580253427<br>}],<br>"code": "200",<br>"msg": "查询成功！",<br>"total": 1<br>\} |  |  |  |

#### 新增熔断规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>/environments/{env_id}/governance/curcuitbreaker/strategy | JSON | POST | 新增熔断规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| application | true | String | 服务名称 |
| trigger | true | String | 触发条件 |
| profile | true | String | 环境 |
| sleepWindow | true | String | 熔断时间窗 |
| label | true | String | 版本 |
| errorPercentage | true | String | 失败率 |
| requestCount | true | String | 最小请求数 |
| 例如：<br>{<br>"application": "2",<br>"trigger": "auto",<br>"profile": "pro",<br>"label": "v3",<br>"sleepWindow": "5000",<br>"errorPercentage": "50",<br>"requestCount": "20"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"application": "SERVICE-F",<br>"profile": "xx",<br>"label": "xx",<br>"configType": null,<br>"trigger": "auto",<br>"sleepWindow": "10",<br>"errorPercentage": "11",<br>"requestCount": "11",<br>"createTime": null<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 编辑熔断规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>/environments/{env_id}/governance/curcuitbreaker/strategy | JSON | PUT | 编辑熔断规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| application | true | String | 服务名称 |
| trigger | true | String | 触发条件 |
| profile | true | String | 环境 |
| sleepWindow | true | String | 熔断时间窗 |
| label | true | String | 版本 |
| errorPercentage | true | String | 失败率 |
| requestCount | true | String | 最小请求数 |
| 例如：<br>{<br>"application": "2",<br>"trigger": "auto",<br>"profile": "pro",<br>"label": "v3",<br>"sleepWindow": "5000",<br>"errorPercentage": "50",<br>"requestCount": "20"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"envId": "w7qow3ofuwkytwsuvs0q",<br>"application": "LIBRARY-USERINFO-SPRINGCLOUD-SVC",<br>"profile": "xx",<br>"label": "xx",<br>"configType": null,<br>"trigger": "auto",<br>"sleepWindow": "111",<br>"errorPercentage": "11",<br>"requestCount": "11",<br>"createTime": null<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 删除（批量）熔断规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{env_id}/governance/curcuitbreaker/strategy | JSON | DELETE | 删除熔断规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| application | true | String | 服务名称 |
| trigger | true | String | 触发条件 |
| profile | true | String | 环境 |
| label | true | String | 版本 |
| 例如：<br>[<br>{<br>"application": "demoa",<br>"trigger": "manual",<br>"profile": "dev",<br>"label": "v1"<br>},<br>{<br>"application": "demob",<br>"trigger": "manual",<br>"profile": "dev",<br>"label": "v1"<br>}<br>] |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null<br>\} |  |  |  |

#### 查询限流规则列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{env_id}/governance/ratelimit/strategies | JSON | GET | 查询spring cloud环境env_id中所有限流规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| type | false | String | 服务名称 |
| key | false | String | 环境 |
| 例如：<br>/api/msg/springcloud/v1.0/environments/1/governance/ratelimit/strategies |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"envId": null,<br>"application": "SERVICE-F",<br>"profile": "xx",<br>"label": "xx",<br>"configType": null,<br>"trigger": "THREAD",<br>"maxConcurrentRequests": "",<br>"coreSize": "11",<br>"queueSizeRejectionThreshold": "11",<br>"maxQueueSize": null,<br>"createTime": 1635580655477<br>}],<br>"code": "200",<br>"msg": "查询成功！",<br>"total": 1<br>\} |  |  |  |

#### 新增限流规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{env_id}/governance/ratelimit/strategy | JSON | POST | 新增限流规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| trigger | true | String | 限流策略 |
| coreSize | true | int | 线程数 |
| application | true | String | 服务名称 |
| profile | true | String | 环境 |
| label | true | String | 版本 |
| maxQueueSize | true | String | 请求等待队列 |
| maxConcurrentRequests | true | String | 最大请求数 |
| 例如：<br>{<br>"application": "test1",<br>"trigger": "THREAD",<br>"profile": "dev",<br>"label": "v1.0",<br>"coreSize": "5000",<br>"maxQueueSize": "50",<br>"maxConcurrentRequests": ""<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"application": "SERVICE-F",<br>"profile": "xx",<br>"label": "xx",<br>"configType": null,<br>"trigger": "THREAD",<br>"maxConcurrentRequests": "-1",<br>"coreSize": "11",<br>"queueSizeRejectionThreshold": "11",<br>"maxQueueSize": "-1",<br>"createTime": null<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 编辑限流规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{env_id}/governance/ratelimit/strategy | JSON | PUT | 编辑限流规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| userinfo | true | String | 用户信息 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| trigger | true | String | 限流策略 |
| coreSize | true | int | 线程数 |
| application | true | String | 服务名称 |
| profile | true | String | 环境 |
| label | true | String | 版本 |
| maxQueueSize | true | String | 请求等待队列 |
| maxConcurrentRequests | true | String | 最大请求数 |
| 例如：<br>{<br>"application": "test1",<br>"trigger": "THREAD",<br>"profile": "dev",<br>"label": "v1.0",<br>"coreSize": "5000",<br>"maxQueueSize": "50",<br>"maxConcurrentRequests": ""<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"application": "SERVICE-F",<br>"profile": "xx",<br>"label": "xx",<br>"configType": null,<br>"trigger": "THREAD",<br>"maxConcurrentRequests": "-1",<br>"coreSize": "11",<br>"queueSizeRejectionThreshold": "11",<br>"maxQueueSize": "-1",<br>"createTime": null<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 删除（批量）限流规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>environments/{env_id}/governance/ratelimit/strategy | JSON | DELETE | 删除限流规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境ID |
| trigger | true | int | 限流策略 |
| coreSize | true | int | 线程数 |
| application | true | String | 服务名称 |
| profile | true | String | 环境 |
| label | true | String | 版本 |
| 例如：<br>[<br>{<br>"application": "demoa",<br>"trigger": "manual",<br>"profile": "dev",<br>"label": "v1"<br>},<br>{<br>"application": "demob",<br>"trigger": "manual",<br>"profile": "dev",<br>"label": "v1"<br>}<br>] |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"succNum": 1,<br>"failNum": 0<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 下载文件（SDK、demo、框架）

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>files/{file_name\} | JSON | GET | 下载文件 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| userinfo | true | String | 用户信息 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| file_name | true | String | 文件名称 |
| 例如：<br>/api/msg/springcloud/v1.0/files/springcloud-sdk.zip |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>200 |  |  |  |

#### 查询限流路径

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/routes/<br>environments/{envId}/services/{svc\} | JSON | POST | 查询限流路径 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| envId | true | String | 集群ID |
| svc | true | String | 服务名 |
| 例如： |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"msg": "",<br>"total": 0,<br>"code": "",<br>"data": [{<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"routerId": "jz82spoxtyfmc2lvtvz1",<br>"url": "/service-f/yyy/**",<br>"serverName": "SERVICE-F",<br>"createTime": 1635580105597,<br>"subsidiaryDtoList": []<br>}]<br>\} |  |  |  |

#### 新建网关路由熔断，限流

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/subsidiaries | JSON | POST | 新建网关路由熔断，限流 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
|  | true | String |  |
| 例如：<br>熔断参数：<br>{<br>"routeId": "klydgrido3di1o72k3af",<br>"envId": "74c9e5e7-2dfe-4642-8b34-e4529c19a1a7",<br>"args": {<br>"circuitBreaker_enabled": true   //是否开启熔断<br>"circuitBreaker_forceOpen": "false", //手动熔断：true，其它false<br>"circuitBreaker_errorThresholdPercentage": "50%",//失败率<br>"circuitBreaker_requestVolumeThreshold": "30", //请求数<br>"circuitBreaker_sleepWindowInMilliseconds": "1000", //熔断时间（ms）<br>"fallbackUri": "forward:/refresh",<br>"name": "hystrixCommandTest"<br>},<br>"name": "Hystrix",<br>"serviceName": "hystrix-test-server",//服务名<br>"type": "filter"<br>}<br>限流参数：<br>{<br>"routeId": "klydgrido3di1o72k3af", //路由ID<br>"envId": "74c9e5e7-2dfe-4642-8b34-e4529c19a1a7", //环境id<br>"name": "RequestRateLimiter", //限流类别<br>"serviceName": "hystrix-test-server",<br>"type": "filter",<br>"args": {<br>"redis-rate-limiter.burstCapacity": 2,//最大请求量（s）<br>"redis-rate-limiter.replenishRate": 2, //每秒请求率<br>"name":"testLimter"  //该规则的名字<br>}<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": "6bz7msbxdgb1l30mnum8",<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 删除网关熔断、限流配置配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/<br>/subsidiaries/{id\} | JSON | DELETE | 删除网关治理策略 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| route_id | true | String | 关联路由id |
| env_id | true | String | 环境ID |
| id | true | String | 删除的治理策略id |
| 例如：<br>/api/msg/springcloud/v1.0/subsidiary/1 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": "delete success !",<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

##### 错误返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | bingo.serviceChain.insertion.modFailed | 修改失败 |
| msg | String | cloudos.serviceChain.modFailed | 修改服务链失败 |
| data | Object | null | 返回空 |
| total | Integer | 0 | 返回0个 |
| 例如：<br>{<br>"code": " springcloud.10012 ",<br>"msg": "非法参数，请重新输入参数",<br>"data": null,<br>"total": 0<br>\} |  |  |  |

#### 查询当前环境的所有治理策略（熔断，限流）

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/subsidiaries/types/{name\} | JSON | GET | 查询当前环境上的所有网关治理策略 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| env_id | true | String | 环境id |
| name | true | String | 策略类型当前包括RequestRateLimiter（限流），Hystrix：（熔断），当前只有filte |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| key | true | String | serviceName或url  服务名或url对应的值 |
| value | true | String | Key对应的值 |
| 例如：<br>/api/msg/springcloud/v1.0/environments/1/subsidiary/types/gg?page=1&size=10 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>限流返回：<br>{<br>"data": [<br>{<br>"id": "jndyojzekce8ylw9rmrn",<br>"routeId": "klydgrido3di1o72k3af", //路由id<br>"name": "RequestRateLimiter", //限流<br>"args": {<br>"redis-rate-limiter.burstCapacity": "2", //最大请求量<br>"redis-rate-limiter.replenishRate": "2" //每秒请求率<br>"name":"testLimter"<br>},<br>"envId": "kbuv55h37zeopjguaq93",<br>"serviceName": "hystrix-test-server",<br>"url": "test",<br>"type": "filter",<br>"createTime": 1569209588713,<br>"updateTime": 1569209588713<br>}<br>],<br>"code": "",<br>"msg": "",<br>"total": 1<br>}<br>熔断返回：<br>{<br>"data": [<br>{<br>"id": "jbauvf0rbtdwyap0bljp",<br>"routeId": "aa8iljohghlahihhdy3v", //路由id<br>"name": "Hystrix", //熔断<br>"args": {<br>"circuitBreaker_enabled": true   //是否开启熔断<br>"circuitBreaker_forceOpen": "false",   // 是否是手动熔断<br>"circuitBreaker_errorThresholdPercentage": "50%", //错误率<br>"circuitBreaker_requestVolumeThreshold": "30",  //请求量<br>"circuitBreaker_sleepWindowInMilliseconds": "1000", //熔断时间窗口<br>"name": "hystrixCommandTest"  //当前规则的名称<br>},<br>"envId": "kbuv55h37zeopjguaq93",<br>"serviceName": "hystrix-test-server",<br>"url": "test",<br>"type": "filter",<br>"createTime": 1569210104958,<br>"updateTime": 1569210104958<br>}<br>],<br>"code": "",<br>"msg": "",<br>"total": 1<br>\} |  |  |  |

##### 错误返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | bingo.serviceChain.insertion.modFailed | 修改失败 |
| msg | String | cloudos.serviceChain.modFailed | 修改服务链失败 |
| data | Object | null | 返回空 |
| total | Integer | 0 | 返回0个 |
| 例如：<br>{<br>"code": " springcloud.10012 ",<br>"msg": "非法参数，请重新输入参数",<br>"data": null,<br>"total": 0<br>\} |  |  |  |

#### 编辑网关熔断限流规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/subsidiaries | JSON | PUT | 编辑限流或熔断规则 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
|  | true | String |  |
| 例如：<br>熔断：只能编辑args里面的参数，其它参数不可编辑。<br>{<br>"id": "jbauvf0rbtdwyap0bljp",<br>"routeId": "aa8iljohghlahihhdy3v"，<br>"args": {<br>"circuitBreaker_enabled": true   //是否开启熔断<br>"circuitBreaker_forceOpen": "false",   // 是否是手动熔断<br>"circuitBreaker_errorThresholdPercentage": "50%", //错误率<br>"circuitBreaker_requestVolumeThreshold": "30",  //请求量<br>"circuitBreaker_sleepWindowInMilliseconds": "1000", //熔断时间窗口<br>},<br>"envId": "kbuv55h37zeopjguaq93",<br>"serviceName": "hystrix-test-server",<br>"url": "/test",<br>}<br>限流：只可编辑agrs部分<br>{<br>"id": "hvuwxfh9eokzylmoyeog",<br>"routeId": "aa8iljohghlahihhdy3v",<br>"args": {<br>"redis-rate-limiter.burstCapacity": "5",<br>"redis-rate-limiter.replenishRate": "5"<br>"name": "hystrixCommandTest2"<br>},<br>"envId": "kbuv55h37zeopjguaq93",<br>"serviceName": "hystrix-test-server",<br>"url": "/test",<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": "edit success !",<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 查询认证路径

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/routes/environments/{envId\} | JSON | POST | 查询认证路径 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
|  | true | String |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [{<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"routerId": "jz82spoxtyfmc2lvtvz1",<br>"url": "/service-f/yyy/**",<br>"serverName": "SERVICE-F",<br>"createTime": 1635580399401,<br>"subsidiaryDtoList": [{<br>"id": "r9w2aqlerakbsqbtsvnk",<br>"routeId": null,<br>"name": "Hystrix",<br>"args": {<br>"circuitBreaker_errorThresholdPercentage": "11",<br>"circuitBreaker_forceOpen": "false",<br>"circuitBreaker_sleepWindowInMilliseconds": "11",<br>"name": "cccc",<br>"circuitBreaker_requestVolumeThreshold": "11",<br>"circuitBreaker_enabled": "true"<br>},<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"serviceName": null,<br>"url": null,<br>"type": "filter",<br>"createTime": 1635581657405,<br>"updateTime": 1635581690895<br>}]<br>}]<br>\} |  |  |  |

#### 查询网关AK

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/gateway/ak | JSON | POST | 查询网关AK |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
|  | true | String |  |
| 无 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"ak": "FB0185875108463C9C08968C12518BA6",<br>"sk": "B454C77B5F6C4C2DA91D226D89CD6B5C"<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 添加认证鉴权

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/gateway/jw | JSON | POST | 添加网关AK |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 如下 | true | String |  |
| 例如：<br>{<br>"id": "",<br>"sk": "05FF35D8FA774B1CA6CE3397A8A46F1D",<br>"ak": "07E2C27794A84ABD939ACF25B0DA9FA9",<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"routeList": ["/service-f/yyy/**"]<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": "",<br>"ak": "07E2C27794A84ABD939ACF25B0DA9FA9",<br>"sk": "05FF35D8FA774B1CA6CE3397A8A46F1D",<br>"routeList": ["/service-f/yyy/**"],<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"createTime": 1635582129133<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 获得网关认证授权

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/gateway/jwtList/{envId\} | JSON | POST | 查询网关AK |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 如下 | true | String |  |
| 无 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"msg": "",<br>"total": 1,<br>"code": "",<br>"data": [{<br>"id": "ypvotv6ocq0fonby0hvx",<br>"ak": "07E2C27794A84ABD939ACF25B0DA9FA9",<br>"sk": "05FF35D8FA774B1CA6CE3397A8A46F1D",<br>"routeList": ["/service-f/yyy/**"],<br>"envId": "cn16xd7xqkfgj0vrovo0",<br>"createTime": 1635582129154<br>}]<br>\} |  |  |  |

#### 删除认证鉴权

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/springcloud/v1.0/gateway/jwt/{envId}/{id\} | JSON | DELETE | 删除网关AK |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 如下 | true | String |  |
| 无 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": "ypvotv6ocq0fonby0hvx",<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 查询Dubbo引擎组件信息详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/svcgov/v1.0/engines/{envId\} | JSON | GET | 查询Dubbo引擎组件信息详情 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 如下 | true | String |  |
| 无 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": "ff14cdfd19ad4d0c988a",<br>"name": "sddd",<br>"description": "",<br>"namespace": "dubbo8bb7c125",<br>"clusterId": "123456",<br>"clusterType": 1,<br>"zone": "default",<br>"version": "2.7.3",<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"engineType": 3,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"engineIp": "172.25.50.10",<br>"zoneId": "",<br>"middlewareCustomType": 0,<br>"needApproval": false,<br>"components": [{<br>"id": "4e5d3bdcd8014d9bbd05",<br>"component": "dubbo-zookeeper",<br>"version": "v3.5.6",<br>"duplicate": 3,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": 2181,<br>"nodePort": 32670,<br>"userName": "",<br>"password": "",<br>"engineId": "ff14cdfd19ad4d0c988a",<br>"type": 6,<br>"customType": 0,<br>"createTime": 1635419962285,<br>"status": "running",<br>"clusterAddr": "zookeeper://dubbo-zookeeper.dubbo8bb7c125:2181",<br>"publicAddr": "zookeeper://172.25.50.10:32670"<br>}, {<br>"id": "b552d6b539374dc8a064",<br>"component": "dubbo-admin",<br>"version": "2.7.3",<br>"duplicate": 1,<br>"reqCPU": "500m",<br>"reqMemory": "1G",<br>"limitCPU": "2",<br>"limitMemory": "2G",<br>"clusterPort": 8080,<br>"nodePort": 23586,<br>"userName": "",<br>"password": "",<br>"engineId": "ff14cdfd19ad4d0c988a",<br>"type": 7,<br>"customType": 0,<br>"createTime": 1635419962288,<br>"status": "running",<br>"clusterAddr": "",<br>"publicAddr": ""<br>}],<br>"middleWares": null,<br>"status": "running",<br>"createTime": 1635419962284,<br>"updateTime": 1635419962284,<br>"componentsList": "paas.microservice.engine.register,paas.microservice.engine.datacenter"<br>},<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 查询Dubbo引擎服务列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/msg/svcgov/v1.0/engines/{envId}/services | JSON | GET | 查询Dubbo引擎服务列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | Integer | 分页参数 |
| Size | true | Integer | 分页参数 |
| envId | true | String | 环境ID |
| tyeNum，pattern，filter | true | String | tyeNum，pattern一般固定<br>filter 服务名称 |
| 例如：<br>api/msg/svcgov/v1.0/engines/c2f41e8a657b466ebf5f/services?<br>tyeNum=3&pattern=service&t=1635587868628&page=1&size=10&<br>filter=io.seata.samples.springboot.service.AssetService |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"appName": "seata-springboot-app",<br>"group": "",<br>"version": "",<br>"providers": [{<br>"address": "10.244.0.183:20999",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "B90A57BD8220DEE6",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "anyhost=true\u0026application=seata-springboot-app\u0026dubbo=2.6.0\u0026generic=false\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=9\u0026side=provider\u0026timeout=10000\u0026timestamp=1635384504860",<br>"registry": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"url": "dubbo://10.244.0.183:20999/io.seata.samples.springboot.service.AssetService",<br>"username": null,<br>"weight": 100<br>}, {<br>"address": "10.243.0.115:20999",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "8C7313A8415009E3",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "anyhost=true\u0026application=seata-springboot-app\u0026dubbo=2.6.0\u0026generic=false\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=8\u0026side=provider\u0026timeout=10000\u0026timestamp=1635384485461",<br>"registry": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"url": "dubbo://10.243.0.115:20999/io.seata.samples.springboot.service.AssetService",<br>"username": null,<br>"weight": 100<br>}, {<br>"address": "10.243.0.25:20999",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "86BD0835B4CB0189",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "anyhost=true\u0026application=seata-springboot-app\u0026dubbo=2.6.0\u0026generic=false\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=10\u0026side=provider\u0026timeout=10000\u0026timestamp=1635320717454",<br>"registry": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"url": "dubbo://10.243.0.25:20999/io.seata.samples.springboot.service.AssetService",<br>"username": null,<br>"weight": 100<br>}, {<br>"address": "10.244.0.182:20999",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "FC6FCCE25A7289FE",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "anyhost=true\u0026application=seata-springboot-app\u0026dubbo=2.6.0\u0026generic=false\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=8\u0026side=provider\u0026timeout=10000\u0026timestamp=1635384505747",<br>"registry": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"url": "dubbo://10.244.0.182:20999/io.seata.samples.springboot.service.AssetService",<br>"username": null,<br>"weight": 100<br>}, {<br>"address": "10.244.0.29:20999",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "89967D59E36FE3D4",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "anyhost=true\u0026application=seata-springboot-app\u0026dubbo=2.6.0\u0026generic=false\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=8\u0026side=provider\u0026timeout=10000\u0026timestamp=1635211277665",<br>"registry": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"url": "dubbo://10.244.0.29:20999/io.seata.samples.springboot.service.AssetService",<br>"username": null,<br>"weight": 100<br>}, {<br>"address": "10.243.0.121:20999",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "7065C0C7D4E12058",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "anyhost=true\u0026application=seata-springboot-app\u0026dubbo=2.6.0\u0026generic=false\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=9\u0026side=provider\u0026timeout=10000\u0026timestamp=1635384695354",<br>"registry": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"url": "dubbo://10.243.0.121:20999/io.seata.samples.springboot.service.AssetService",<br>"username": null,<br>"weight": 100<br>}, {<br>"address": "10.244.0.184:20999",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "9382C868D7463EB5",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "anyhost=true\u0026application=seata-springboot-app\u0026dubbo=2.6.0\u0026generic=false\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=9\u0026side=provider\u0026timeout=10000\u0026timestamp=1635384714661",<br>"registry": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"url": "dubbo://10.244.0.184:20999/io.seata.samples.springboot.service.AssetService",<br>"username": null,<br>"weight": 100<br>}],<br>"consumers": [{<br>"address": "10.243.0.121",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "733B3569A7DF86C9",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "application=seata-springboot-app\u0026category=consumers\u0026check=false\u0026dubbo=2.6.0\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=9\u0026side=consumer\u0026timestamp=1635384688265",<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"statistics": null,<br>"username": null<br>}, {<br>"address": "10.243.0.115",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "EE546B2A34AB80B6",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "application=seata-springboot-app\u0026category=consumers\u0026check=false\u0026dubbo=2.6.0\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=8\u0026side=consumer\u0026timestamp=1635384478663",<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"statistics": null,<br>"username": null<br>}, {<br>"address": "10.244.0.184",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "DD5BFD7ACB748F50",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "application=seata-springboot-app\u0026category=consumers\u0026check=false\u0026dubbo=2.6.0\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=9\u0026side=consumer\u0026timestamp=1635384704957",<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"statistics": null,<br>"username": null<br>}, {<br>"address": "10.243.0.25",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "EF4ED1C93C617E56",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "application=seata-springboot-app\u0026category=consumers\u0026check=false\u0026dubbo=2.6.0\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=10\u0026side=consumer\u0026timestamp=1635320710565",<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"statistics": null,<br>"username": null<br>}, {<br>"address": "10.244.0.183",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "4D71B6A02B30FE9A",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "application=seata-springboot-app\u0026category=consumers\u0026check=false\u0026dubbo=2.6.0\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=9\u0026side=consumer\u0026timestamp=1635384495361",<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"statistics": null,<br>"username": null<br>}, {<br>"address": "10.244.0.182",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "48DD38F980BE2851",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "application=seata-springboot-app\u0026category=consumers\u0026check=false\u0026dubbo=2.6.0\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=8\u0026side=consumer\u0026timestamp=1635384496453",<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"statistics": null,<br>"username": null<br>}, {<br>"address": "10.244.0.29",<br>"alived": 0,<br>"application": "seata-springboot-app",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "8DE78AA0E9F24B05",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"parameters": "application=seata-springboot-app\u0026category=consumers\u0026check=false\u0026dubbo=2.6.0\u0026interface=io.seata.samples.springboot.service.AssetService\u0026methods=increase\u0026pid=8\u0026side=consumer\u0026timestamp=1635211269469",<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "io.seata.samples.springboot.service.AssetService",<br>"statistics": null,<br>"username": null<br>}]<br>}],<br>"code": 200,<br>"msg": "",<br>"total": 1,<br>"status": true<br>\} |  |  |  |

#### 获取Spring Cloud应用部署参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/springcloud | JSON | GET | 获取springcloud应用部署参数 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
|  | true | String |  |
| 例如：<br>无 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"EUREKA_URL": "10.125.30.100:51305",<br>"CONFIG_URL": "10.125.30.100:19818"<br>},<br>"code": "",<br>"msg": "",<br>"total": 0<br>\} |  |  |  |

#### 新建灰度策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/gray/engine/{engineId}/policy | JSON | POST | 新建灰度策略 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engineId | true | String | 引擎id |
| headerName | true | String | 策略的HeaderName |
| headerValue | true | String | 策略的HeaderValue |
| name | true | String | 策略名称 |
| 例如：<br>{"name":"xxx","headerName":"xxx","headerValue":"x"\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回新建的结果 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": 3,<br>"name": "CASD",<br>"headerName": "CSAD",<br>"headerValue": "CSDA",<br>"operator": null<br>},<br>"code": "",<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

#### 编辑灰度策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/gray/engine/{engineId}/policy/{policyId\} | JSON | PUT | 新建灰度策略 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engineId | true | String | 引擎id |
| policyId | true | String | 策略的id |
| headerName | true | String | 策略的HeaderName |
| headerValue | true | String | 策略的HeaderValue |
| name | true | String | 策略名称 |
| 例如：<br>{"name":"xxx","headerName":"xxx","headerValue":"x"\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体编辑后的结果体 | 具体编辑后的结果体 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": 3,<br>"name": "CASD",<br>"headerName": "CSAD",<br>"headerValue": "CSDA",<br>"operator": null<br>},<br>"code": "",<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

#### 删除灰度策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/gray/engine/{engineId}/policy/{policyId\} | JSON | DELETE | 删除灰度策略 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engineId | true | String | 引擎id |
| policyId | true | String | 策略的id |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | -- | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{"data":null,"code":"","msg":null,"total":0\} |  |  |  |

#### 查询灰度策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/gray/engine/{engineId}/policy | JSON | GET | 查询灰度策略 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engineId | true | String | 引擎id |
| page | true | Int | 页数 |
| size | true | Int | 每页数量 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 查询结果体 | 查询结果 |
| total | Integer | 总数 | 总数 |
| 例如：<br>{<br>"data": [<br>{<br>"id": 3,<br>"name": "CASD",<br>"headerName": "CSAD",<br>"headerValue": "CSDA",<br>"operator": "admin"<br>}<br>],<br>"code": "",<br>"msg": null,<br>"total": 1<br>\} |  |  |  |

#### 查询灰度规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/gray/engine/{engineId}/bind/page | JSON | GET | 查询灰度规则 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engineId | true | String | 引擎id |
| page | true | Int | 页数 |
| size | true | Int | 每页数量 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 查询结果 | 查询结果 |
| total | Integer | 总数 | 总数 |
| 例如：<br>{<br>"data": [<br>{<br>"method": "RATE",<br>"envId": "9y9y1jn2plazuys4li3d",<br>"serviceName": "service-b",<br>"version": "gray_v3",<br>"alternateWeight": 10,<br>"policyId": 3,<br>"createTime": 1646288616529,<br>"updateTime": 1646288616529<br>}<br>],<br>"code": "",<br>"msg": null,<br>"total": 1<br>\} |  |  |  |

#### 新建灰度规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/gray/engine/{ engineId }/bind | JSON | POST | 新建灰度规则 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engineId | true | String | 引擎id |
| alternateWeight | true | Int | 规则权重 |
| envId | true | String | 引擎id |
| method | true | String | 灰度类型，RATE或TRACE |
| policyId | true | String | 策略id |
| serviceName | true | String | 服务名称 |
| version | true | String | 版本 |
| 例如：<br>{<br>"envId": "9y9y1jn2plazuys4li3d",<br>"serviceName": "service-b",<br>"version": "gray_v2",<br>"policyId": 3,<br>"alternateWeight": 10,<br>"method": "TRACE"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回新建的结果 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"method": "TRACE",<br>"envId": "9y9y1jn2plazuys4li3d",<br>"serviceName": "service-b",<br>"version": "gray_v2",<br>"alternateWeight": 10,<br>"policyId": 3,<br>"createTime": 0,<br>"updateTime": 0<br>},<br>"code": "",<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

#### 编辑灰度规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/gray/engine/{engineId}/bind/{serviceName\} | JSON | PUT | 编辑灰度规则 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engineId | true | String | 引擎id |
| alternateWeight | true | Int | 规则权重 |
| envId | true | String | 引擎id |
| method | true | String | 灰度类型，RATE或TRACE |
| policyId | true | String | 策略id |
| serviceName | true | String | 服务名称 |
| version | true | String | 版本 |
| 例如：<br>{<br>"envId": "9y9y1jn2plazuys4li3d",<br>"serviceName": "service-x",<br>"version": "gray_v1",<br>"policyId": 3,<br>"alternateWeight": 10,<br>"method": "RATE"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回编辑的结果 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"method": "TRACE",<br>"envId": "9y9y1jn2plazuys4li3d",<br>"serviceName": "service-b",<br>"version": "gray_v2",<br>"alternateWeight": 10,<br>"policyId": 3,<br>"createTime": 0,<br>"updateTime": 0<br>},<br>"code": "",<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

#### 删除灰度规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/springcloud/v1.0/gray/engine/{engineId}/bind/{serviceName\} | JSON | DELETE | 删除灰度规则 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engineId | true | String | 引擎id |
| serviceName | true | String | 服务名称 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回删除的结果 |
| total | Integer | 0 | 0 |
| 例如：<br>{"data":"service-b","code":"","msg":null,"total":0\} |  |  |  |

### Istio接口文档

#### 根据组织ID查询集群

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/apps/{orgId}/clusters | JSON | GET | 根据组织ID查询集群 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| orgId | true | String | 组织ID |
| 例如：<br>api/cloud/app/msg/v1.0/apps/9eeef206-0fd9-406d-8bd7-9b18aeb5ef70/clusters |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": [{<br>"name": "Default",<br>"id": "123456",<br>"support": "yes"<br>}],<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 获取引擎服务列表和服务信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/clusters/{cluster-id}/apps/{namespace\} | JSON | GET | 查询服务列表 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| cluster-id | true | String | 集群ID |
| namespace | true | String | 命名空间 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>/api/cloud/app/msg/v1.0/clusters/123456/apps/spacee3j8dvcw?t=1635411191017&page=1&size=10 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": [{<br>"outerUrl": ["10.125.31.185:32109"],<br>"name": "jar-doc-0ywrx-svc",<br>"pods": "1/1",<br>"innerUrl": ["jar-doc-0ywrx-svc.spacee3j8dvcw:6687"],<br>"status": "OK"<br>}],<br>"code": "200",<br>"msg": "search success!",<br>"total": 1,<br>"status": true<br>\} |  |  |  |

#### 根据命名空间筛选服务拓扑

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/clusters/{cluster-id}/namespaces/{namespace}/app/graph | JSON | GET | 根据空间类型查询所有服务的拓扑关系 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| cluster-id | true | String | 集群ID |
| namespace | true | String | 命名空间 |

| 请求参数 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| duration | true | String | 回溯时间（持续时间），秒（如 60 秒） |
| 例如：<br>/api/cloud/app/msg/v1.0/clusters/123456/namespaces/spacee3j8dvcw/app/graph?duration=1800&t=1635409614160 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"edges": [{<br>"sourceNodeId": "e853292ee71a8f474d043e67bdcb7836",<br>"targetNodeId": "5f38993b31f797cf0e48647d35f653cd",<br>"traffic": {<br>"protocol": "http",<br>"rates": {<br>"http": "37.01",<br>"httpPercentReq": "100.0"<br>},<br>"responses": {<br>"200": {<br>"-": "100.0"<br>}<br>}<br>},<br>"responseTime": "5",<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "b30b0078325bf2e1adb4d57c4c0c2665",<br>"targetNodeId": "1b83f3de482b18497e51423768b51239",<br>"traffic": {<br>"protocol": "http"<br>},<br>"responseTime": null,<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "b30b0078325bf2e1adb4d57c4c0c2665",<br>"targetNodeId": "a405aa835b8283df3468483d8373ca3b",<br>"traffic": {<br>"protocol": "http",<br>"rates": {<br>"http": "37.01",<br>"httpPercentReq": "11.1"<br>},<br>"responses": {<br>"200": {<br>"-": "100.0"<br>}<br>}<br>},<br>"responseTime": null,<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "3b21cd5bc670c80a07bd68aa822fadbb",<br>"targetNodeId": "e853292ee71a8f474d043e67bdcb7836",<br>"traffic": {<br>"protocol": "http",<br>"rates": {<br>"http": "37.01",<br>"httpPercentReq": "100.0"<br>},<br>"responses": {<br>"200": {<br>"-": "100.0"<br>}<br>}<br>},<br>"responseTime": null,<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "1b83f3de482b18497e51423768b51239",<br>"targetNodeId": "3b1e1d159a4b3cd6b56b7d2578ca9edc",<br>"traffic": {<br>"protocol": "http"<br>},<br>"responseTime": null,<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "43d885021d20d7445a93930da1ba212a",<br>"targetNodeId": "d015f77bd17abe175b6a83bf63d1d405",<br>"traffic": {<br>"protocol": "http",<br>"rates": {<br>"http": "74.02",<br>"httpPercentReq": "100.0"<br>},<br>"responses": {<br>"200": {<br>"-": "100.0"<br>}<br>}<br>},<br>"responseTime": null,<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "b30b0078325bf2e1adb4d57c4c0c2665",<br>"targetNodeId": "02b4b40e0606ce1dc7e98c7586bbef65",<br>"traffic": {<br>"protocol": "http",<br>"rates": {<br>"http": "296.11",<br>"httpPercentReq": "88.9"<br>},<br>"responses": {<br>"200": {<br>"-": "100.0"<br>}<br>}<br>},<br>"responseTime": null,<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "02b4b40e0606ce1dc7e98c7586bbef65",<br>"targetNodeId": "43d885021d20d7445a93930da1ba212a",<br>"traffic": {<br>"protocol": "http",<br>"rates": {<br>"http": "296.11",<br>"httpPercentReq": "100.0"<br>},<br>"responses": {<br>"200": {<br>"-": "100.0"<br>}<br>}<br>},<br>"responseTime": "79",<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "a405aa835b8283df3468483d8373ca3b",<br>"targetNodeId": "a8d983769575930d2e0c2fa7162a830e",<br>"traffic": {<br>"protocol": "http",<br>"rates": {<br>"http": "37.01",<br>"httpPercentReq": "100.0"<br>},<br>"responses": {<br>"200": {<br>"-": "100.0"<br>}<br>}<br>},<br>"responseTime": "5",<br>"labelsMap": {}<br>}, {<br>"sourceNodeId": "d015f77bd17abe175b6a83bf63d1d405",<br>"targetNodeId": "3b21cd5bc670c80a07bd68aa822fadbb",<br>"traffic": {<br>"protocol": "http",<br>"rates": {<br>"http": "74.02",<br>"httpPercentReq": "100.0"<br>},<br>"responses": {<br>"200": {<br>"-": "100.0"<br>}<br>}<br>},<br>"responseTime": "7",<br>"labelsMap": {}<br>}],<br>"nodes": [{<br>"nodeId": "ab8ee49837db8a246f8d104b0899ed42",<br>"nodeName": "front",<br>"type": "app",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": [{<br>"nodeId": "1b83f3de482b18497e51423768b51239",<br>"nodeName": "front-fdg2b-svc",<br>"type": "service",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": null<br>}, {<br>"nodeId": "3b1e1d159a4b3cd6b56b7d2578ca9edc",<br>"nodeName": "front",<br>"type": "pod",<br>"level": null,<br>"version": "unknown",<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": null<br>}],<br>"traffic": null<br>}, {<br>"nodeId": "b30b0078325bf2e1adb4d57c4c0c2665",<br>"nodeName": "outer",<br>"type": "outer",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpOut": "333.13"<br>}<br>}]<br>}, {<br>"nodeId": "ccfaa5d445623fc0cc39cc5b626e3484",<br>"nodeName": "library-log-istio-svc",<br>"type": "app",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": [{<br>"nodeId": "e853292ee71a8f474d043e67bdcb7836",<br>"nodeName": "library-log-istio-svc",<br>"type": "service",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpIn": "37.01",<br>"httpOut": "37.01"<br>}<br>}]<br>}, {<br>"nodeId": "5f38993b31f797cf0e48647d35f653cd",<br>"nodeName": "library-log-istio-svc",<br>"type": "pod",<br>"level": null,<br>"version": "unknown",<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpIn": "37.01"<br>}<br>}, {<br>"protocol": "tcp",<br>"rates": {<br>"tcpOut": "623.26"<br>}<br>}]<br>}],<br>"traffic": null<br>}, {<br>"nodeId": "7c817f0eff10665d56ab3180a3f74f1f",<br>"nodeName": "library-userinfo-istio-svc",<br>"type": "app",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": [{<br>"nodeId": "a405aa835b8283df3468483d8373ca3b",<br>"nodeName": "library-userinfo-istio-svc-uxg95-svc",<br>"type": "service",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpIn": "37.01",<br>"httpOut": "37.01"<br>}<br>}]<br>}, {<br>"nodeId": "a8d983769575930d2e0c2fa7162a830e",<br>"nodeName": "library-userinfo-istio-svc",<br>"type": "pod",<br>"level": null,<br>"version": "unknown",<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpIn": "37.01"<br>}<br>}, {<br>"protocol": "tcp",<br>"rates": {<br>"tcpOut": "720.40"<br>}<br>}]<br>}],<br>"traffic": null<br>}, {<br>"nodeId": "0bceb9c2481daa2bc88653b3b4203bbc",<br>"nodeName": "library-comment-istio-svc",<br>"type": "app",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": [{<br>"nodeId": "d015f77bd17abe175b6a83bf63d1d405",<br>"nodeName": "library-comment-istio-svc",<br>"type": "service",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpIn": "74.02",<br>"httpOut": "74.02"<br>}<br>}]<br>}, {<br>"nodeId": "3b21cd5bc670c80a07bd68aa822fadbb",<br>"nodeName": "library-comment-istio-svc",<br>"type": "pod",<br>"level": null,<br>"version": "unknown",<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpIn": "74.02",<br>"httpOut": "37.01"<br>}<br>}, {<br>"protocol": "tcp",<br>"rates": {<br>"tcpOut": "767.54"<br>}<br>}]<br>}],<br>"traffic": null<br>}, {<br>"nodeId": "e6e75b6586b3b100c94a8ef9b23971ac",<br>"nodeName": "bookinfo-system",<br>"type": "app",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": [{<br>"nodeId": "02b4b40e0606ce1dc7e98c7586bbef65",<br>"nodeName": "bookinfo-system-9pzsn-svc",<br>"type": "service",<br>"level": null,<br>"version": null,<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpIn": "296.11",<br>"httpOut": "296.11"<br>}<br>}]<br>}, {<br>"nodeId": "43d885021d20d7445a93930da1ba212a",<br>"nodeName": "bookinfo-system",<br>"type": "pod",<br>"level": null,<br>"version": "unknown",<br>"status": null,<br>"labelMap": {},<br>"children": null,<br>"traffic": [{<br>"protocol": "http",<br>"rates": {<br>"httpIn": "296.11",<br>"httpOut": "74.02"<br>}<br>}, {<br>"protocol": "tcp",<br>"rates": {<br>"tcpOut": "391.90"<br>}<br>}]<br>}],<br>"traffic": null<br>}],<br>"timeStamp": 1635422365<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 查询http及tcp流量信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/{ cluster-id }/namespaces/{ namespace }/services/all/traffic | JSON | GET | 查询当前命名空间下制定时间段内的tcp及http的流量信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间 |
| cluster-id | true | String | 引擎ID |
| duration | true | String | 回溯时间（持续时间），秒（如 60 秒） |
| 例如：<br>/api/cloud/app/msg/v1.0/clusters/123456/namespaces/spacebchtkmjy/services/all/traffic<br>?queryTime=1635421523&duration=1800&t=1635421523353 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"name": null,<br>"chars": {<br>"request_count": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1635419700,<br>"value": "923"<br>}, {<br>"timestamp": 1635419760,<br>"value": "772.311"<br>}, {<br>"timestamp": 1635419820,<br>"value": "282.711"<br>}, {<br>"timestamp": 1635419880,<br>"value": "283.978"<br>}, {<br>"timestamp": 1635419940,<br>"value": "278.711"<br>}, {<br>"timestamp": 1635420000,<br>"value": "766.067"<br>}, {<br>"timestamp": 1635420060,<br>"value": "936.289"<br>}, {<br>"timestamp": 1635420120,<br>"value": "284.289"<br>}, {<br>"timestamp": 1635420180,<br>"value": "287.622"<br>}, {<br>"timestamp": 1635420240,<br>"value": "286.667"<br>}, {<br>"timestamp": 1635420300,<br>"value": "701.756"<br>}, {<br>"timestamp": 1635420360,<br>"value": "1088.689"<br>}, {<br>"timestamp": 1635420420,<br>"value": "288.4"<br>}, {<br>"timestamp": 1635420480,<br>"value": "283.911"<br>}, {<br>"timestamp": 1635420540,<br>"value": "287.156"<br>}, {<br>"timestamp": 1635420600,<br>"value": "631.644"<br>}, {<br>"timestamp": 1635420660,<br>"value": "1190.022"<br>}, {<br>"timestamp": 1635420720,<br>"value": "968.8"<br>}, {<br>"timestamp": 1635420780,<br>"value": "1029.6"<br>}, {<br>"timestamp": 1635420840,<br>"value": "1055.289"<br>}, {<br>"timestamp": 1635420900,<br>"value": "1181.644"<br>}, {<br>"timestamp": 1635420960,<br>"value": "249.2"<br>}, {<br>"timestamp": 1635421020,<br>"value": "241.889"<br>}, {<br>"timestamp": 1635421080,<br>"value": "248.133"<br>}, {<br>"timestamp": 1635421140,<br>"value": "625.844"<br>}, {<br>"timestamp": 1635421200,<br>"value": "1093.467"<br>}, {<br>"timestamp": 1635421260,<br>"value": "289.933"<br>}, {<br>"timestamp": 1635421320,<br>"value": "284.578"<br>}, {<br>"timestamp": 1635421380,<br>"value": "269.4"<br>}, {<br>"timestamp": 1635421440,<br>"value": "468.4"<br>}, {<br>"timestamp": 1635421500,<br>"value": "976.556"<br>}]<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 根据服务名称查询监控指标接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/{clusterId}<br>/namespaces/{namespace}/services/{service}/metrics | JSON | GET | 根据服务名查询服务的http及tcp的流量统计 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间 |
| clusterId | true | String | 集群ID |
| duration | true | String | 回溯时间（持续时间），秒（如 60 秒） |
| service | true | String | 服务名 |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/123456/namespaces/space2xiqyw7p/services/service-customer/metrics?queryTime=1635493034&duration=43200&t=1635497792024 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data":{<br>"name":"service_charts",<br>"chars":{<br>"request_count":{<br>"unit":null,<br>"valueList":[<br>{<br>"timestamp":1635497220,<br>"value":"186.911"<br>},<br>{<br>"timestamp":1635497280,<br>"value":"163.844"<br>},<br>{<br>"timestamp":1635497340,<br>"value":"139.067"<br>},<br>{<br>"timestamp":1635497400,<br>"value":"54.822"<br>},<br>{<br>"timestamp":1635497460,<br>"value":"44.111"<br>},<br>{<br>"timestamp":1635497520,<br>"value":"41.556"<br>},<br>{<br>"timestamp":1635497580,<br>"value":"94.6"<br>},<br>{<br>"timestamp":1635497640,<br>"value":"140.556"<br>},<br>{<br>"timestamp":1635497700,<br>"value":"82.622"<br>},<br>{<br>"timestamp":1635497760,<br>"value":"52.178"<br>},<br>{<br>"timestamp":1635497820,<br>"value":"48.244"<br>},<br>{<br>"timestamp":1635497880,<br>"value":"61.2"<br>},<br>{<br>"timestamp":1635497940,<br>"value":"154.267"<br>},<br>{<br>"timestamp":1635498000,<br>"value":"96.022"<br>},<br>{<br>"timestamp":1635498060,<br>"value":"51.422"<br>},<br>{<br>"timestamp":1635498120,<br>"value":"51.2"<br>},<br>{<br>"timestamp":1635498180,<br>"value":"51.244"<br>},<br>{<br>"timestamp":1635498240,<br>"value":"158"<br>},<br>{<br>"timestamp":1635498300,<br>"value":"96.356"<br>},<br>{<br>"timestamp":1635498360,<br>"value":"52.911"<br>},<br>{<br>"timestamp":1635498420,<br>"value":"52.133"<br>},<br>{<br>"timestamp":1635498480,<br>"value":"52.156"<br>},<br>{<br>"timestamp":1635498540,<br>"value":"145.156"<br>},<br>{<br>"timestamp":1635498600,<br>"value":"109.356"<br>},<br>{<br>"timestamp":1635498660,<br>"value":"54.444"<br>},<br>{<br>"timestamp":1635498720,<br>"value":"51.733"<br>},<br>{<br>"timestamp":1635498780,<br>"value":"53.289"<br>},<br>{<br>"timestamp":1635498840,<br>"value":"135.956"<br>},<br>{<br>"timestamp":1635498900,<br>"value":"155.178"<br>},<br>{<br>"timestamp":1635498960,<br>"value":"158.289"<br>},<br>{<br>"timestamp":1635499020,<br>"value":"122.444"<br>}<br>]<br>}<br>}<br>},<br>"code":"",<br>"msg":"",<br>"total":null,<br>"status":true<br>\} |  |  |  |

#### 根据应用名称查询监控指标接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/{clusterId}<br>/namespaces/{namespace}/apps/{service}/metrics | JSON | GET | 根据应用名查询服务的http及tcp的流量统计 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间 |
| clusterId | true | String | 集群ID |
| duration | true | String | 回溯时间（持续时间），秒（如 60 秒） |
| service | true | String | 应用名 |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/123456/namespaces/space2xiqyw7p/apps/service-customer/metrics?queryTime=1635493034&duration=43200&t=1635499290951 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"name": null,<br>"chars": {<br>"request_count_in": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1635472800,<br>"value": "0.207"<br>}, {<br>"timestamp": 1635474240,<br>"value": "0.253"<br>}, {<br>"timestamp": 1635475680,<br>"value": "0.15"<br>}, {<br>"timestamp": 1635482880,<br>"value": "0.205"<br>}, {<br>"timestamp": 1635484320,<br>"value": "0"<br>}]<br>},<br>"request_count_out": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1635472800,<br>"value": "0.207"<br>}, {<br>"timestamp": 1635474240,<br>"value": "0.253"<br>}, {<br>"timestamp": 1635475680,<br>"value": "0.15"<br>}, {<br>"timestamp": 1635482880,<br>"value": "0.205"<br>}, {<br>"timestamp": 1635484320,<br>"value": "0"<br>}]<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 根据工作节点查询监控指标接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/{clusterId}<br>/namespaces/{namespace}/workloads/{service}/metrics | JSON | GET | 根据工作节点名查询服务的http及tcp的流量统计 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间 |
| clusterId | true | String | 集群ID |
| duration | true | String | 回溯时间（持续时间），秒（如 60 秒） |
| service | true | String | 工作节点名 |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/123456/namespaces/space2xiqyw7p/workloads/service-customer/metrics?queryTime=1635493034&duration=43200&t=1635499653172 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"name": null,<br>"chars": {<br>"request_count_in": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1635472800,<br>"value": "0.207"<br>}, {<br>"timestamp": 1635474240,<br>"value": "0.253"<br>}, {<br>"timestamp": 1635475680,<br>"value": "0.15"<br>}, {<br>"timestamp": 1635482880,<br>"value": "0.205"<br>}, {<br>"timestamp": 1635484320,<br>"value": "0"<br>}]<br>},<br>"request_count_out": {<br>"unit": null,<br>"valueList": [{<br>"timestamp": 1635472800,<br>"value": "0.207"<br>}, {<br>"timestamp": 1635474240,<br>"value": "0.253"<br>}, {<br>"timestamp": 1635475680,<br>"value": "0.15"<br>}, {<br>"timestamp": 1635482880,<br>"value": "0.205"<br>}, {<br>"timestamp": 1635484320,<br>"value": "0"<br>}]<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 根据工作节点边框查询监控指标接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/{clusterId}<br>/namespaces/{namespace}/ workloadedges/{service}/metrics | JSON | GET | 根据工作节点名边框查询统计数据 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间 |
| clusterId | true | String | 集群ID |
| duration | true | String | 回溯时间（持续时间），秒（如 60 秒） |
| service | true | String | 工作节点边框名 |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/123456/namespaces/space2xiqyw7p/workloadedges/service-customer/metrics?queryTime=1635493034&duration=43200&t=1635499787971 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"name": null,<br>"chars": {<br>"request_duration": {<br>"unit": "ms",<br>"values": {<br>"_0.5": [],<br>"_0.95": [],<br>"_0.99": [],<br>"_avg": []<br>}<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 根据服务边框查询监控指标接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/{clusterId}<br>/namespaces/{namespace}/ serviceedges/{service}/metrics | JSON | GET | 根据服务边框查询统计数据 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间 |
| clusterId | true | String | 集群ID |
| duration | true | String | 回溯时间（持续时间），秒（如 60 秒） |
| service | true | String | 服务边框名 |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/123456/namespaces/space2xiqyw7p/serviceedges/service-customer/metrics?queryTime=1635553311&duration=1800&t=1635559206417 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"name": null,<br>"chars": {<br>"request_duration": {<br>"unit": "ms",<br>"values": {<br>"_0.5": [],<br>"_0.95": [],<br>"_0.99": [],<br>"_avg": []<br>}<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 提交服务治理信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/clusters/{clusterId}/namespaces/{namespace}/services/{service}/governance | JSON | POST | 新建服务治理信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间 |
| service | true | String | 服务名称 |
| request | true | GovernanceDto | 设置服务治理规则参数 Request Body |
| 例如：<br>/api/cloud/app/msg/v1.0/clusters/123456/namespaces/space2xiqyw7p/services/service-a-znvy6-svc/governance<br>请求GovernanceDto:<br>{<br>"loadBalancer": "RANDOM",<br>"maxConnections": "1",<br>"http1MaxPendingRequests": "1",<br>"maxRequestsPerConnection": "1",<br>"consecutiveErrors": "1",<br>"interval": "1",<br>"baseEjectionTime": "1",<br>"maxEjectionPercent": "1",<br>"faultInjectionType": "delay",<br>"percent": "11",<br>"fixedDelay": "11",<br>"httpStatus": "",<br>"timeout": "12",<br>"attempts": null,<br>"perTryTimeout": null<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"loadBalancer": null,<br>"maxConnections": null,<br>"http1MaxPendingRequests": null,<br>"maxRequestsPerConnection": null,<br>"consecutiveErrors": null,<br>"interval": null,<br>"baseEjectionTime": null,<br>"maxEjectionPercent": null,<br>"faultInjectionType": null,<br>"percent": null,<br>"fixedDelay": null,<br>"httpStatus": null,<br>"timeout": null,<br>"attempts": null,<br>"perTryTimeout": null<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 查询服务治理信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/clusters/{clusterId}/namespaces/{namespace}/services/{service}/governance | JSON | GET | 查询服务治理信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间 |
| service | true | String | 服务名称 |
| 例如：<br>/api/cloud/app/msg/v1.0/clusters/123456/namespaces/space2xiqyw7p/services/service-a-znvy6-svc/governance |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"loadBalancer": "RANDOM",<br>"maxConnections": "1",<br>"http1MaxPendingRequests": "1",<br>"maxRequestsPerConnection": "1",<br>"consecutiveErrors": "1",<br>"interval": "1",<br>"baseEjectionTime": "1",<br>"maxEjectionPercent": "1",<br>"faultInjectionType": "delay",<br>"percent": "11",<br>"fixedDelay": "11",<br>"httpStatus": null,<br>"timeout": "12",<br>"attempts": null,<br>"perTryTimeout": null<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 查询链路服务接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/{clusterId}<br>/namespaces/{namespace}/traceservice | JSON | GET | 查询链路服务 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间 |
| clusterId | true | String |  |
| 例如：<br>/api/cloud/app/msg/v1.0/clusters/123456/namespaces/space52m34q9f/traceservice?t=1635422615926 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": ["library-book-istio-svc", "library-comment-istio-svc", "library-userinfo-istio-svc"],<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 链路信息查询接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/clusters/{clusterId}<br>namespaces/{namespace}/services/{service}/traces | JSON | GET | 根据时间，排序等参数查找链路信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String |  |
| namespace | true | String | 命名空间 |
| service | true | String | 服务名 |
| start | true | String | 查询起始时间 |
| end | true | String | 查询结束时间 |
| limit | true | String | 查询条数限制 |
| order | true | String | 排序规则,只有两个值recent（时间最近），longest（耗时最长） |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/123456/namespaces/space52m34q9f/services/library-comment-istio-svc/traces?t=1635422465038&start=1635336059257&end=1635422459257&limit=20&order=recent |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": [{<br>"traceID": "a2bf6ee43519e97e",<br>"spans": [{<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "a2bf6ee43519e97e",<br>"flags": 1,<br>"operationName": "milkShopInfo",<br>"references": [],<br>"startTime": 1552356309147000,<br>"duration": 2392168,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 500<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "sampler.type",<br>"type": "string",<br>"value": "const"<br>},<br>{<br>"key": "sampler.param",<br>"type": "bool",<br>"value": true<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://172.25.18.247:5164/"<br>},<br>{<br>"key": "error",<br>"type": "bool",<br>"value": true<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356309241000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.front.FrontController.milkShopInfo() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "FrontController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "milkShopInfo"<br>}]<br>},<br>{<br>"timestamp": 1552356311536000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.front.FrontController.milkShopInfo() throws java.lang.Exception"<br>}]<br>},<br>{<br>"timestamp": 1552356311539000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "error"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "500 Internal Server Error"<br>},<br>{<br>"key": "stack",<br>"type": "string",<br>"value": "org.springframework.web.util.NestedServletException: Request processing failed; nested exception is org.springframework.web.client.HttpServerErrorException$InternalServerError: 500 Internal Server Error\n\tat org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006)\n\tat org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:890)\n\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:634)\n\tat org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:875)\n\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:741)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:231)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:53)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:99)\n\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:92)\n\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.springframework.web.filter.HiddenHttpMethodFilter.doFilterInternal(HiddenHttpMethodFilter.java:93)\n\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:200)\n\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat io.opentracing.contrib.web.servlet.filter.TracingFilter.doFilter(TracingFilter.java:174)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:199)\n\tat org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:96)\n\tat org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:490)\n\tat org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:139)\n\tat org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:92)\n\tat org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74)\n\tat org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:343)\n\tat org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:408)\n\tat org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:66)\n\tat org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:770)\n\tat org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1415)\n\tat org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49)\n\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)\n\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)\n\tat org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)\n\tat java.lang.Thread.run(Thread.java:748)\nCaused by: org.springframework.web.client.HttpServerErrorException$InternalServerError: 500 Internal Server Error\n\tat org.springframework.web.client.HttpServerErrorException.create(HttpServerErrorException.java:79)\n\tat org.springframework.web.client.DefaultResponseErrorHandler.handleError(DefaultResponseErrorHandler.java:99)\n\tat org.springframework.web.client.DefaultResponseErrorHandler.handleError(DefaultResponseErrorHandler.java:79)\n\tat org.springframework.web.client.ResponseErrorHandler.handleError(ResponseErrorHandler.java:63)\n\tat org.springframework.web.client.RestTemplate.handleResponse(RestTemplate.java:777)\n\tat org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:735)\n\tat org.springframework.web.client.RestTemplate.execute(RestTemplate.java:669)\n\tat org.springframework.web.client.RestTemplate.exchange(RestTemplate.java:578)\n\tat com.h3c.demo.front.FrontController.milkShopInfo(FrontController.java:36)\n\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n\tat java.lang.reflect.Method.invoke(Method.java:498)\n\tat org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:215)\n\tat org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:142)\n\tat org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:102)\n\tat org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895)\n\tat org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:800)\n\tat org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)\n\tat org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1038)\n\tat org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:942)\n\tat org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:998)\n\t... 44 more\n"<br>}]<br>},<br>{<br>"timestamp": 1552356311539000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: a2bf6ee43519e97e:a2bf6ee43519e97e:0:1 - milkShopInfo"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32088-exec-1"<br>}]<br>}],<br>"processID": "p2",<br>"warnings": null<br>},<br>{<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "4a846b159fa69df1",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "a2bf6ee43519e97e"<br>}],<br>"startTime": 1552356309853000,<br>"duration": 1606752,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 500<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://milk-brands-server:32066/brands"<br>},<br>{<br>"key": "peer.port",<br>"type": "int64",<br>"value": 32066<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356311460000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: a2bf6ee43519e97e:4a846b159fa69df1:a2bf6ee43519e97e:1 - GET"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32088-exec-1"<br>}]<br>}],<br>"processID": "p2",<br>"warnings": null<br>},<br>{<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "f1af680e0611b386",<br>"flags": 1,<br>"operationName": "brandsInfo",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "4a846b159fa69df1"<br>}],<br>"startTime": 1552356310636000,<br>"duration": 491005,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 500<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://milk-brands-server:32066/brands"<br>},<br>{<br>"key": "error",<br>"type": "bool",<br>"value": true<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356310738000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.brands.BrandController.brandsInfo() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "BrandController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "brandsInfo"<br>}]<br>},<br>{<br>"timestamp": 1552356311124000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.brands.BrandController.brandsInfo() throws java.lang.Exception"<br>}]<br>},<br>{<br>"timestamp": 1552356311126000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "error"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "503 Service Unavailable"<br>},<br>{<br>"key": "stack",<br>"type": "string",<br>"value": "org.springframework.web.util.NestedServletException: Request processing failed; nested exception is org.springframework.web.client.HttpServerErrorException$ServiceUnavailable: 503 Service Unavailable\n\tat org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006)\n\tat org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:890)\n\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:634)\n\tat org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:875)\n\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:741)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:231)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:53)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:99)\n\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:92)\n\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.springframework.web.filter.HiddenHttpMethodFilter.doFilterInternal(HiddenHttpMethodFilter.java:93)\n\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:200)\n\tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:107)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat io.opentracing.contrib.web.servlet.filter.TracingFilter.doFilter(TracingFilter.java:174)\n\tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193)\n\tat org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)\n\tat org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:199)\n\tat org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:96)\n\tat org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:490)\n\tat org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:139)\n\tat org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:92)\n\tat org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74)\n\tat org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:343)\n\tat org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:408)\n\tat org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:66)\n\tat org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:770)\n\tat org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1415)\n\tat org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49)\n\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)\n\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)\n\tat org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)\n\tat java.lang.Thread.run(Thread.java:748)\nCaused by: org.springframework.web.client.HttpServerErrorException$ServiceUnavailable: 503 Service Unavailable\n\tat org.springframework.web.client.HttpServerErrorException.create(HttpServerErrorException.java:85)\n\tat org.springframework.web.client.DefaultResponseErrorHandler.handleError(DefaultResponseErrorHandler.java:99)\n\tat org.springframework.web.client.DefaultResponseErrorHandler.handleError(DefaultResponseErrorHandler.java:79)\n\tat org.springframework.web.client.ResponseErrorHandler.handleError(ResponseErrorHandler.java:63)\n\tat org.springframework.web.client.RestTemplate.handleResponse(RestTemplate.java:777)\n\tat org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:735)\n\tat org.springframework.web.client.RestTemplate.execute(RestTemplate.java:669)\n\tat org.springframework.web.client.RestTemplate.exchange(RestTemplate.java:578)\n\tat com.h3c.demo.brands.BrandController.brandsInfo(BrandController.java:39)\n\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n\tat java.lang.reflect.Method.invoke(Method.java:498)\n\tat org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:215)\n\tat org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:142)\n\tat org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:102)\n\tat org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:895)\n\tat org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:800)\n\tat org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)\n\tat org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1038)\n\tat org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:942)\n\tat org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:998)\n\t... 44 more\n"<br>}]<br>},<br>{<br>"timestamp": 1552356311127000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: a2bf6ee43519e97e:f1af680e0611b386:4a846b159fa69df1:1 - brandsInfo"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-1"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>},<br>{<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "a53aac76d3ac4cd0",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "f1af680e0611b386"<br>}],<br>"startTime": 1552356310922000,<br>"duration": 109635,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 503<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://h2-database:32099/brands"<br>},<br>{<br>"key": "peer.port",<br>"type": "int64",<br>"value": 32099<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356311032000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: a2bf6ee43519e97e:a53aac76d3ac4cd0:f1af680e0611b386:1 - GET"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-1"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>},<br>{<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "7ea9688ab3748550",<br>"flags": 1,<br>"operationName": "error",<br>"references": [{<br>"refType": "FOLLOWS_FROM",<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "f1af680e0611b386"<br>}],<br>"startTime": 1552356311265000,<br>"duration": 254629,<br>"tags": [],<br>"logs": [{<br>"timestamp": 1552356311266000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public org.springframework.http.ResponseEntity<java.util.Map<java.lang.String, java.lang.Object>> org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController.error(javax.servlet.http.HttpServletRequest)"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "BasicErrorController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "error"<br>}]<br>},<br>{<br>"timestamp": 1552356311520000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public org.springframework.http.ResponseEntity<java.util.Map<java.lang.String, java.lang.Object>> org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController.error(javax.servlet.http.HttpServletRequest)"<br>}]<br>},<br>{<br>"timestamp": 1552356311520000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: a2bf6ee43519e97e:7ea9688ab3748550:f1af680e0611b386:1 - error"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-1"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>},<br>{<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "b078ee04c1bf304f",<br>"flags": 1,<br>"operationName": "errorHtml",<br>"references": [{<br>"refType": "FOLLOWS_FROM",<br>"traceID": "a2bf6ee43519e97e",<br>"spanID": "a2bf6ee43519e97e"<br>}],<br>"startTime": 1552356311577000,<br>"duration": 156317,<br>"tags": [],<br>"logs": [{<br>"timestamp": 1552356311577000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public org.springframework.web.servlet.ModelAndView org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController.errorHtml(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "BasicErrorController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "errorHtml"<br>}]<br>},<br>{<br>"timestamp": 1552356311734000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public org.springframework.web.servlet.ModelAndView org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController.errorHtml(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)"<br>}]<br>},<br>{<br>"timestamp": 1552356311734000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: a2bf6ee43519e97e:b078ee04c1bf304f:a2bf6ee43519e97e:1 - errorHtml"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32088-exec-1"<br>}]<br>}],<br>"processID": "p2",<br>"warnings": null<br>}],<br>"processes": {<br>"p1": {<br>"serviceName": "spacedb33fadd:milk-brands-server",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "milk-brands-server-85797b5797-nb25z"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.12.111"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>},<br>"p2": {<br>"serviceName": "spacedb33fadd:milk-shop-front-server",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "milk-shop-front-server-56b479b4ff-lzj2q"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.12.110"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>}<br>},<br>"warnings": null,<br>"startTime": 1552356309147000,<br>"serviceName": "spacedb33fadd:milk-shop-front-server",<br>"operationName": "milkShopInfo",<br>"totalSpans": 6,<br>"duration": 2586317<br>},<br>{<br>"traceID": "8fb7dad69603ee4c",<br>"spans": [{<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "8fb7dad69603ee4c",<br>"flags": 1,<br>"operationName": "milkShopInfo",<br>"references": [],<br>"startTime": 1552356375272000,<br>"duration": 3948607,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "sampler.type",<br>"type": "string",<br>"value": "const"<br>},<br>{<br>"key": "sampler.param",<br>"type": "bool",<br>"value": true<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://172.25.18.247:5164/"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356375274000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.front.FrontController.milkShopInfo() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "FrontController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "milkShopInfo"<br>}]<br>},<br>{<br>"timestamp": 1552356379220000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.front.FrontController.milkShopInfo() throws java.lang.Exception"<br>}]<br>}],<br>"processID": "p2",<br>"warnings": null<br>},<br>{<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "dabe709c45b845db",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "8fb7dad69603ee4c"<br>}],<br>"startTime": 1552356375318000,<br>"duration": 3761857,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://milk-brands-server:32066/brands"<br>},<br>{<br>"key": "peer.port",<br>"type": "int64",<br>"value": 32066<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [],<br>"processID": "p2",<br>"warnings": null<br>},<br>{<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "23c20ed35b2e1d33",<br>"flags": 1,<br>"operationName": "brandsInfo",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "dabe709c45b845db"<br>}],<br>"startTime": 1552356375420000,<br>"duration": 3658662,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://milk-brands-server:32066/brands"<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356375421000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.brands.BrandController.brandsInfo() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "BrandController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "brandsInfo"<br>}]<br>},<br>{<br>"timestamp": 1552356379072000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "BrandController.class"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "finished  getting brandsInfo !"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-2"<br>}]<br>},<br>{<br>"timestamp": 1552356379079000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.brands.BrandController.brandsInfo() throws java.lang.Exception"<br>}]<br>},<br>{<br>"timestamp": 1552356379079000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 8fb7dad69603ee4c:23c20ed35b2e1d33:dabe709c45b845db:1 - brandsInfo"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-2"<br>}]<br>}],<br>"processID": "p3",<br>"warnings": null<br>},<br>{<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "2363e98c2d2fcd3a",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "23c20ed35b2e1d33"<br>}],<br>"startTime": 1552356375422000,<br>"duration": 3496609,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://h2-database:32099/brands"<br>},<br>{<br>"key": "peer.port",<br>"type": "int64",<br>"value": 32099<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356378919000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 8fb7dad69603ee4c:2363e98c2d2fcd3a:23c20ed35b2e1d33:1 - GET"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-2"<br>}]<br>}],<br>"processID": "p3",<br>"warnings": null<br>},<br>{<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "e22309128e266dc",<br>"flags": 1,<br>"operationName": "brands",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "2363e98c2d2fcd3a"<br>}],<br>"startTime": 1552356375778134,<br>"duration": 2784340,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://h2-database:32099/brands"<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356375909134,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.database.controller.DatabaseController.brands() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "DatabaseController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "brands"<br>}]<br>},<br>{<br>"timestamp": 1552356376575134,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "org.hibernate.hql.internal.QueryTranslatorFactoryInitiator"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "HHH000397: Using ASTQueryTranslatorFactory"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32099-exec-1"<br>}]<br>},<br>{<br>"timestamp": 1552356378560134,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.database.controller.DatabaseController.brands() throws java.lang.Exception"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>},<br>{<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "175f4d789d506ff5",<br>"flags": 1,<br>"operationName": "Query",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "8fb7dad69603ee4c",<br>"spanID": "e22309128e266dc"<br>}],<br>"startTime": 1552356377885134,<br>"duration": 37492,<br>"tags": [{<br>"key": "component",<br>"type": "string",<br>"value": "java-jdbc"<br>},<br>{<br>"key": "db.type",<br>"type": "string",<br>"value": "h2"<br>},<br>{<br>"key": "db.user",<br>"type": "string",<br>"value": "SA"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "db.statement",<br>"type": "string",<br>"value": "select milkbrand0_.id as id1_0_, milkbrand0_.age as age2_0_, milkbrand0_.brand_name as brand_na3_0_, milkbrand0_.starts as starts4_0_ from milk_brand milkbrand0_"<br>}],<br>"logs": [],<br>"processID": "p1",<br>"warnings": null<br>}],<br>"processes": {<br>"p1": {<br>"serviceName": "spacedb33fadd:h2-database",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "database-server-5f7b95d9b4-2gtfb"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.11.51"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>},<br>"p2": {<br>"serviceName": "spacedb33fadd:milk-shop-front-server",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "milk-shop-front-server-56b479b4ff-lzj2q"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.12.110"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>},<br>"p3": {<br>"serviceName": "spacedb33fadd:milk-brands-server",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "milk-brands-server-85797b5797-nb25z"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.12.111"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>}<br>},<br>"warnings": null,<br>"startTime": 1552356375272000,<br>"serviceName": "spacedb33fadd:milk-shop-front-server",<br>"operationName": "milkShopInfo",<br>"totalSpans": 6,<br>"duration": 3948607<br>},<br>{<br>"traceID": "99ec560d7b4cb9cc",<br>"spans": [{<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "99ec560d7b4cb9cc",<br>"flags": 1,<br>"operationName": "milkShopInfo",<br>"references": [],<br>"startTime": 1552356382081000,<br>"duration": 338012,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "sampler.type",<br>"type": "string",<br>"value": "const"<br>},<br>{<br>"key": "sampler.param",<br>"type": "bool",<br>"value": true<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://172.25.18.247:5164/"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382083000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.front.FrontController.milkShopInfo() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "FrontController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "milkShopInfo"<br>}]<br>},<br>{<br>"timestamp": 1552356382419000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.front.FrontController.milkShopInfo() throws java.lang.Exception"<br>}]<br>},<br>{<br>"timestamp": 1552356382419000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 99ec560d7b4cb9cc:99ec560d7b4cb9cc:0:1 - milkShopInfo"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32088-exec-5"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>},<br>{<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "bd49d9b87705d19c",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "99ec560d7b4cb9cc"<br>}],<br>"startTime": 1552356382083000,<br>"duration": 273005,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://milk-brands-server:32066/brands"<br>},<br>{<br>"key": "peer.port",<br>"type": "int64",<br>"value": 32066<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382356000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 99ec560d7b4cb9cc:bd49d9b87705d19c:99ec560d7b4cb9cc:1 - GET"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32088-exec-5"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>},<br>{<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "6d9d9a2f9534ab00",<br>"flags": 1,<br>"operationName": "brandsInfo",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "bd49d9b87705d19c"<br>}],<br>"startTime": 1552356382219000,<br>"duration": 134912,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://milk-brands-server:32066/brands"<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382220000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.brands.BrandController.brandsInfo() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "BrandController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "brandsInfo"<br>}]<br>},<br>{<br>"timestamp": 1552356382352000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "BrandController.class"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "finished  getting brandsInfo !"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-4"<br>}]<br>},<br>{<br>"timestamp": 1552356382354000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.brands.BrandController.brandsInfo() throws java.lang.Exception"<br>}]<br>},<br>{<br>"timestamp": 1552356382354000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 99ec560d7b4cb9cc:6d9d9a2f9534ab00:bd49d9b87705d19c:1 - brandsInfo"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-4"<br>}]<br>}],<br>"processID": "p2",<br>"warnings": null<br>},<br>{<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "cad5e6de007b9708",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "6d9d9a2f9534ab00"<br>}],<br>"startTime": 1552356382221000,<br>"duration": 129718,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://h2-database:32099/brands"<br>},<br>{<br>"key": "peer.port",<br>"type": "int64",<br>"value": 32099<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382351000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 99ec560d7b4cb9cc:cad5e6de007b9708:6d9d9a2f9534ab00:1 - GET"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-4"<br>}]<br>}],<br>"processID": "p2",<br>"warnings": null<br>},<br>{<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "63efbcb0b8e356b3",<br>"flags": 1,<br>"operationName": "brands",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "cad5e6de007b9708"<br>}],<br>"startTime": 1552356382329000,<br>"duration": 15554,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://h2-database:32099/brands"<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382330000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.database.controller.DatabaseController.brands() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "DatabaseController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "brands"<br>}]<br>},<br>{<br>"timestamp": 1552356382344000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.database.controller.DatabaseController.brands() throws java.lang.Exception"<br>}]<br>}],<br>"processID": "p3",<br>"warnings": null<br>},<br>{<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "83a39952a7c27d60",<br>"flags": 1,<br>"operationName": "Query",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "63efbcb0b8e356b3"<br>}],<br>"startTime": 1552356382332000,<br>"duration": 126,<br>"tags": [{<br>"key": "component",<br>"type": "string",<br>"value": "java-jdbc"<br>},<br>{<br>"key": "db.type",<br>"type": "string",<br>"value": "h2"<br>},<br>{<br>"key": "db.user",<br>"type": "string",<br>"value": "SA"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "db.statement",<br>"type": "string",<br>"value": "select milkbrand0_.id as id1_0_, milkbrand0_.age as age2_0_, milkbrand0_.brand_name as brand_na3_0_, milkbrand0_.starts as starts4_0_ from milk_brand milkbrand0_"<br>}],<br>"logs": [],<br>"processID": "p3",<br>"warnings": null<br>}],<br>"processes": {<br>"p1": {<br>"serviceName": "spacedb33fadd:milk-shop-front-server",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "milk-shop-front-server-56b479b4ff-lzj2q"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.12.110"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>},<br>"p2": {<br>"serviceName": "spacedb33fadd:milk-brands-server",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "milk-brands-server-85797b5797-nb25z"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.12.111"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>},<br>"p3": {<br>"serviceName": "spacedb33fadd:h2-database",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "database-server-5f7b95d9b4-2gtfb"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.11.51"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>}<br>},<br>"warnings": null,<br>"startTime": 1552356382081000,<br>"serviceName": "spacedb33fadd:milk-shop-front-server",<br>"operationName": "milkShopInfo",<br>"totalSpans": 6,<br>"duration": 338012<br>}],<br>"code": "",<br>"msg": ""<br>\} |  |  |  |

#### 链路详情信息查询接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/<br>all/traces/{traceId\} | JSON | GET | 根据参数查找具体链路信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| traceId | true | String | 链路id |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/all/traces/85df23912950a3fe |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"traceID": "99ec560d7b4cb9cc",<br>"processes": {<br>"p1": {<br>"serviceName": "spacedb33fadd:milk-shop-front-server",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "milk-shop-front-server-56b479b4ff-lzj2q"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.12.110"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>},<br>"p2": {<br>"serviceName": "spacedb33fadd:milk-brands-server",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "milk-brands-server-85797b5797-nb25z"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.12.111"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>},<br>"p3": {<br>"serviceName": "spacedb33fadd:h2-database",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "database-server-5f7b95d9b4-2gtfb"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.101.11.51"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "spacedb33fadd"<br>}]<br>}<br>},<br>"warnings": null,<br>"startTime": 1552356382081000,<br>"serviceName": "spacedb33fadd:milk-shop-front-server",<br>"operationName": "milkShopInfo",<br>"totalSpans": 6,<br>"duration": 338012,<br>"children": [{<br>"processes": "spacedb33fadd:milk-shop-front-server",<br>"spanId": "99ec560d7b4cb9cc",<br>"span": {<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "99ec560d7b4cb9cc",<br>"flags": 1,<br>"operationName": "milkShopInfo",<br>"references": [],<br>"startTime": 1552356382081000,<br>"duration": 338012,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "sampler.type",<br>"type": "string",<br>"value": "const"<br>},<br>{<br>"key": "sampler.param",<br>"type": "bool",<br>"value": true<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://172.25.18.247:5164/"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382083000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.front.FrontController.milkShopInfo() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "FrontController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "milkShopInfo"<br>}]<br>},<br>{<br>"timestamp": 1552356382419000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.front.FrontController.milkShopInfo() throws java.lang.Exception"<br>}]<br>},<br>{<br>"timestamp": 1552356382419000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 99ec560d7b4cb9cc:99ec560d7b4cb9cc:0:1 - milkShopInfo"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32088-exec-5"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>},<br>"startTime": 1552356382081000,<br>"depth": 1,<br>"children": [{<br>"processes": "spacedb33fadd:milk-shop-front-server",<br>"spanId": "bd49d9b87705d19c",<br>"span": {<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "bd49d9b87705d19c",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "99ec560d7b4cb9cc"<br>}],<br>"startTime": 1552356382083000,<br>"duration": 273005,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://milk-brands-server:32066/brands"<br>},<br>{<br>"key": "peer.port",<br>"type": "int64",<br>"value": 32066<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382356000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 99ec560d7b4cb9cc:bd49d9b87705d19c:99ec560d7b4cb9cc:1 - GET"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32088-exec-5"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>},<br>"startTime": 1552356382083000,<br>"depth": 2,<br>"children": [{<br>"processes": "spacedb33fadd:milk-brands-server",<br>"spanId": "6d9d9a2f9534ab00",<br>"span": {<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "6d9d9a2f9534ab00",<br>"flags": 1,<br>"operationName": "brandsInfo",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "bd49d9b87705d19c"<br>}],<br>"startTime": 1552356382219000,<br>"duration": 134912,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://milk-brands-server:32066/brands"<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382220000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.brands.BrandController.brandsInfo() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "BrandController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "brandsInfo"<br>}]<br>},<br>{<br>"timestamp": 1552356382352000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "BrandController.class"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "finished  getting brandsInfo !"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-4"<br>}]<br>},<br>{<br>"timestamp": 1552356382354000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.brands.BrandController.brandsInfo() throws java.lang.Exception"<br>}]<br>},<br>{<br>"timestamp": 1552356382354000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 99ec560d7b4cb9cc:6d9d9a2f9534ab00:bd49d9b87705d19c:1 - brandsInfo"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-4"<br>}]<br>}],<br>"processID": "p2",<br>"warnings": null<br>},<br>"startTime": 1552356382219000,<br>"depth": 3,<br>"children": [{<br>"processes": "spacedb33fadd:milk-brands-server",<br>"spanId": "cad5e6de007b9708",<br>"span": {<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "cad5e6de007b9708",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "6d9d9a2f9534ab00"<br>}],<br>"startTime": 1552356382221000,<br>"duration": 129718,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://h2-database:32099/brands"<br>},<br>{<br>"key": "peer.port",<br>"type": "int64",<br>"value": 32099<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382351000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "INFO"<br>},<br>{<br>"key": "logger",<br>"type": "string",<br>"value": "io.jaegertracing.internal.reporters.LoggingReporter"<br>},<br>{<br>"key": "message",<br>"type": "string",<br>"value": "Span reported: 99ec560d7b4cb9cc:cad5e6de007b9708:6d9d9a2f9534ab00:1 - GET"<br>},<br>{<br>"key": "thread",<br>"type": "string",<br>"value": "http-nio-32066-exec-4"<br>}]<br>}],<br>"processID": "p2",<br>"warnings": null<br>},<br>"startTime": 1552356382221000,<br>"depth": 4,<br>"children": [{<br>"processes": "spacedb33fadd:h2-database",<br>"spanId": "63efbcb0b8e356b3",<br>"span": {<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "63efbcb0b8e356b3",<br>"flags": 1,<br>"operationName": "brands",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "cad5e6de007b9708"<br>}],<br>"startTime": 1552356382329000,<br>"duration": 15554,<br>"tags": [{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://h2-database:32099/brands"<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}],<br>"logs": [{<br>"timestamp": 1552356382330000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "preHandle"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.database.controller.DatabaseController.brands() throws java.lang.Exception"<br>},<br>{<br>"key": "handler.class_simple_name",<br>"type": "string",<br>"value": "DatabaseController"<br>},<br>{<br>"key": "handler.method_name",<br>"type": "string",<br>"value": "brands"<br>}]<br>},<br>{<br>"timestamp": 1552356382344000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "afterCompletion"<br>},<br>{<br>"key": "handler",<br>"type": "string",<br>"value": "public java.lang.Object com.h3c.demo.database.controller.DatabaseController.brands() throws java.lang.Exception"<br>}]<br>}],<br>"processID": "p3",<br>"warnings": null<br>},<br>"startTime": 1552356382329000,<br>"depth": 5,<br>"children": [{<br>"processes": "spacedb33fadd:h2-database",<br>"spanId": "83a39952a7c27d60",<br>"span": {<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "83a39952a7c27d60",<br>"flags": 1,<br>"operationName": "Query",<br>"references": [{<br>"refType": "CHILD_OF",<br>"traceID": "99ec560d7b4cb9cc",<br>"spanID": "63efbcb0b8e356b3"<br>}],<br>"startTime": 1552356382332000,<br>"duration": 126,<br>"tags": [{<br>"key": "component",<br>"type": "string",<br>"value": "java-jdbc"<br>},<br>{<br>"key": "db.type",<br>"type": "string",<br>"value": "h2"<br>},<br>{<br>"key": "db.user",<br>"type": "string",<br>"value": "SA"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>},<br>{<br>"key": "db.statement",<br>"type": "string",<br>"value": "select milkbrand0_.id as id1_0_, milkbrand0_.age as age2_0_, milkbrand0_.brand_name as brand_na3_0_, milkbrand0_.starts as starts4_0_ from milk_brand milkbrand0_"<br>}],<br>"logs": [],<br>"processID": "p3",<br>"warnings": null<br>},<br>"startTime": 1552356382332000,<br>"depth": 6,<br>"children": []<br>}]<br>}]<br>}]<br>}]<br>}]<br>}]<br>},<br>"code": "",<br>"msg": ""<br>\} |  |  |  |

#### 获取外部可访问服务策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/clusters/{clusterId}/namespaces/{namespace}/outaccess | JSON | GET | 指定应用空间，获取该应用空间下外部可访问服务策略。 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间名称 |
| clusterId | true | String | 集群ID |
| 例如：<br>/api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p/outaccess?t=1635561652765 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": ["service-a-znvy6-svc", "service-customer", "service-producer"],<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 获取应用空间Istio认证信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/istio/auth/clusters/<br>{clusterId}/namespaces/{namespace\} | JSON | GET | 指定应用空间，获取该应用空间Istio认证信息 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间 |
| 例如：<br>/api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"policy": true,<br>"outAccessServices": ["service-a-znvy6-svc", "service-customer", "service-producer"],<br>"rbac": false,<br>"namespaces": ["space2xiqyw7p"],<br>"authServices": [],<br>"accessServices": null,<br>"headers": []<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 开启流量加密

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/istio/auth/clusters/<br>{clusterId}/namespaces/{namespace\} | JSON | POST | 指定应用空间，开启该应用空间Istio认证 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间 |
| 例如：<br>/api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"rule": {<br>"apiVersion": "networking.istio.io/v1alpha3",<br>"kind": "DestinationRule",<br>"metadata": {<br>"creationTimestamp": "2021-10-29T05:47:11Z",<br>"generation": 1,<br>"name": "default",<br>"namespace": "space2xiqyw7p",<br>"resourceVersion": "17558958",<br>"selfLink": "/apis/networking.istio.io/v1alpha3/namespaces/space2xiqyw7p/destinationrules/default",<br>"uid": "a9a42392-387b-11ec-98e5-0cda411d616d"<br>},<br>"spec": {<br>"host": "*.space2xiqyw7p.svc.cluster.local",<br>"trafficPolicy": {<br>"tls": {<br>"mode": "ISTIO_MUTUAL"<br>}<br>}<br>}<br>},<br>"policy": {<br>"apiVersion": "authentication.istio.io/v1alpha1",<br>"kind": "Policy",<br>"metadata": {<br>"creationTimestamp": "2021-10-29T05:47:11Z",<br>"generation": 1,<br>"name": "default",<br>"namespace": "space2xiqyw7p",<br>"resourceVersion": "17558956",<br>"selfLink": "/apis/authentication.istio.io/v1alpha1/namespaces/space2xiqyw7p/policies/default",<br>"uid": "a99f8370-387b-11ec-98e5-0cda411d616d"<br>},<br>"spec": {<br>"peers": [{<br>"mtls": {}<br>}]<br>}<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 关闭流量加密

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/istio/auth/clusters/<br>{clusterId}/namespaces/{namespace\} | JSON | DELETE | 指定应用空间，关闭该应用空间Istio认证 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间 |
| 例如：<br>/api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 开启访问授权

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/istio/auth/clusters/<br>{clusterId}/namespaces/{namespace}/rbac | JSON | POST | 指定应用空间，开启访问授权 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间 |
| 例如：<br>/api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p/rbac |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": "space2xiqyw7p",<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 关闭访问授权

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/istio/auth//clusters<br>/{clusterId}/namespaces/{namespace}/rbac | JSON | DELETE | 关闭访问授权 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间 |
| 例如：<br>/api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p/rbac |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 批量添加外部可访问服务策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/istio/<br>auth//clusters/{clusterId}/<br>namespaces/{namespace}/outaccess | JSON | POST | 批量添加外部可访问服务策略。 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间名称 |
| clusterId | true | String | 集群ID |
| 例如：<br>api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p/outaccess?t=1635564529897<br>body:<br>["service-a-znvy6-svc", "service-customer"] |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": null,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 删除外部可访问服务策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>istio/auth/clusters/{clusterId}<br>/namespaces/{namespace}/outaccess/{service\} | JSON | DELETE | 指定应用空间，根据服务名称删除该应用空间下某服务外部可访问服务策略。 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间名称 |
| clusterId | true | String | 集群ID |
| service |  |  | 服务名称 |
| 例如：<br>api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p/outaccess/service-customer?t=1635564708159 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 添加应用空间下被授权访问的空间

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>istio/auth//clusters/{clusterId}/<br>namespaces/{namespace}/servicerolebinding/namespace/{space\} | JSON | POST | 添加允许访问的应用空间。 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 当前命名空间名称 |
| space | true | String | 添加命名空间名称 |
| clusterId | true | String | 集群ID |
| 例如：<br>api/cloud/app/msg/v1.0/istio/auth/clusters/123456/<br>namespaces/space2xiqyw7p/servicerolebinding/namespace/space2xiqyw7p |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 移除允许访问的应用空间

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>istio/auth/clusters/{clusterId}/namespaces/<br>{namespace}/servicerolebinding/namespace/{space\} | JSON | DELETE | 移除允许访问的应用空间 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间名称 |
| space | true | String | 允许访问的命名空间名称 |
| clusterId | true | String | 集群ID |
| 例如：<br>api/cloud/app/msg/v1.0/istio/auth/clusters/123456/<br>namespaces/space2xiqyw7p/servicerolebinding/namespace/space2xiqyw7p?t=1635565428129 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 添加应用空间下被授权访问的服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>istio/auth//clusters/{clusterId}<br>/namespaces/{namespace}/servicerole/{service\} | JSON | POST | 添加应用空间下被授权访问的服务。 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 当前命名空间名称 |
| service | true | String | 服务名称 |
| clusterId | true | String | 集群ID |
| 例如：<br>api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p/servicerole/service-a-znvy6-svc?t=1635574616219 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"apiVersion": "rbac.istio.io/v1alpha1",<br>"kind": "ServiceRole",<br>"metadata": {<br>"creationTimestamp": "2021-10-30T02:18:47Z",<br>"generation": 1,<br>"name": "space2xiqyw7p-service-role",<br>"namespace": "space2xiqyw7p",<br>"resourceVersion": "17801564",<br>"selfLink": "/apis/rbac.istio.io/v1alpha1/namespaces/space2xiqyw7p/serviceroles/space2xiqyw7p-service-role",<br>"uid": "b6f49a87-3927-11ec-98e5-0cda411d616d"<br>},<br>"spec": {<br>"rules": [{<br>"methods": ["*"],<br>"services": ["*", "service-a-znvy6-svc.space2xiqyw7p.svc.cluster.local"]<br>}]<br>}<br>},<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 移除允许访问的服务

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>istio/auth//clusters/{clusterId}/<br>namespaces/{namespace}/servicerole/{service\} | JSON | DELETE | 移除允许访问的服务 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| namespace | true | String | 命名空间名称 |
| service | true | String | 服务名称 |
| clusterId | true | String | 集群ID |
| 例如：<br>api/cloud/app/msg/v1.0/istio/auth/clusters/123456/namespaces/space2xiqyw7p/servicerole/service-a-znvy6-svc?t=1635574732926 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 设置访问header验证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>istio/auth/clusters/{clusterId}/servicerolebinding/headers | JSON | POST | 设置访问header验证。 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| userinfo | true | String | 用户信息 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间名称 |
| header | true | String | Header hey |
| value | true | String | Header value |
| 例如：<br>/api/cloud/app/msg/v1.0/istio/auth/clusters/123456/servicerolebinding/headers<br>Body<br>{"header":"ffaa23","value":"34141c","namespace":"space2xiqyw7p"\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": "ffaa23",<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 移除访问header验证

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>istio/auth/clusters/{clusterId}/servicerolebinding/headers | JSON | PUT | 移除访问header验证。 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| clusterId | true | String | 引擎ID |
| namespace | true | String | 命名空间名称 |
| header | true | String | Header hey |
| value | true | String | Header value |
| 例如：<br>/api/cloud/app/msg/v1.0/istio/auth/clusters/123456/servicerolebinding/headers<br>{"header":"ddd32","value":"erew2","namespace":"space2xiqyw7p"\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 子集群部署Istio组件

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>istio/install/file | JSON | POST | 供容器云调用，在多集群环境中，向子集群部署Istio组件 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| ip | true | String | 集群VIP |
| 例如：<br>{<br>"ip": "10.125.31.211"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": true,<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 查询所有链路服务名称(链路追踪)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>clusters/all/traces/services/all | JSON | GET | 查询所有链路服务名称 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/all/traces/services/all?t=1635576375206 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": ["space2xiqyw7p:service-b"],<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 查询链路接口类型(链路追踪)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| api/cloud/app/msg/v1.0/clusters/all/<br>services/{service}/operations | JSON | GET | 查询链路接口类型 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| service | true | string | 服务 |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/all/services/space2xiqyw7p:service-b/operations?t=1635576530710 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": ["GET"],<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

#### 查询所有链路

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>clusters/all/traces/services/{service\} | JSON | GET | 查询所有链路 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| service | true | String | 服务名称 |
| end | true | long | 查询结束时间 |
| limit | true | int | 查询的条数限制查询的条数限制 |
| maxDuration | false | String | 最大持续时间（2us，2ms，2s）可以不填 |
| minDuration | false | String | 最小持续时间可以不填 |
| start | true | long | 开始时间 |
| tag | false | String | 链路的tag可以不填 不可和持续时间同时存在 |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/all/traces/services/space2xiqyw7p:service-b?t=1635576711649&end=1635576706096&start=1632984706096&limit=1&operation=all&tag= |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": [{<br>"traceID": "3f8b75200d6adbe9",<br>"spans": [{<br>"traceID": "3f8b75200d6adbe9",<br>"spanID": "3f8b75200d6adbe9",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [],<br>"startTime": 1635553212598000,<br>"duration": 300,<br>"tags": [{<br>"key": "component",<br>"type": "string",<br>"value": "java-spring-rest-template"<br>}, {<br>"key": "span.kind",<br>"type": "string",<br>"value": "client"<br>}, {<br>"key": "sampler.type",<br>"type": "string",<br>"value": "const"<br>}, {<br>"key": "sampler.param",<br>"type": "bool",<br>"value": true<br>}, {<br>"key": "http.url",<br>"type": "string",<br>"value": "http://h3c-gray-Server/gray/v2/all?serviceId=service-b&instanceId=10.240.1.21:23123"<br>}, {<br>"key": "error",<br>"type": "bool",<br>"value": true<br>}, {<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}, {<br>"key": "internal.span.format",<br>"type": "string",<br>"value": "proto"<br>}],<br>"logs": [{<br>"timestamp": 1635553212599000,<br>"fields": [{<br>"key": "level",<br>"type": "string",<br>"value": "DEBUG"<br>}, {<br>"key": "logger",<br>"type": "string",<br>"value": "cn.springcloud.gray.choose.DefaultServerChooser"<br>}, {<br>"key": "message",<br>"type": "string",<br>"value": "h3c-gray-Server 服务没有相关灰度策略, 从serverList列表挑选, serverList.size=0"<br>}, {<br>"key": "thread",<br>"type": "string",<br>"value": "main"<br>}]<br>}, {<br>"timestamp": 1635553212599000,<br>"fields": [{<br>"key": "event",<br>"type": "string",<br>"value": "error"<br>}, {<br>"key": "error.object",<br>"type": "string",<br>"value": "java.lang.IllegalStateException: No instances available for h3c-gray-Server"<br>}]<br>}],<br>"processID": "p1",<br>"warnings": null<br>}],<br>"processes": {<br>"p1": {<br>"serviceName": "space2xiqyw7p:service-b",<br>"tags": [{<br>"key": "hostname",<br>"type": "string",<br>"value": "service-a-59747697dc-kd7vj"<br>}, {<br>"key": "ip",<br>"type": "string",<br>"value": "10.240.1.21"<br>}, {<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>}, {<br>"key": "nameSpace",<br>"type": "string",<br>"value": "space2xiqyw7p"<br>}]<br>}<br>},<br>"warnings": null,<br>"startTime": 1635553212598000,<br>"serviceName": "space2xiqyw7p:service-b",<br>"operationName": "GET",<br>"totalSpans": 1,<br>"duration": 300<br>}],<br>"code": "",<br>"msg": "",<br>"total": null,<br>"status": true<br>\} |  |  |  |

##### 错误返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | bingo.serviceChain.insertion.modFailed | 修改失败 |
| msg | String | cloudos.serviceChain.modFailed | 修改服务链失败 |
| data | Object | null | 返回空 |
| total | Integer | 0 | 返回0个 |
| 例如：<br>{<br>"code": " msg.10010 ",<br>"msg": "参数不正确",<br>"data": null,<br>"total": 0<br>\} |  |  |  |

#### 根据traceid查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/cloud/app/msg/v1.0/<br>clusters/all/traces/{trace_id\} | JSON | GET | 查询链路 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| trace_id | true | String | 链路id |
| 例如：<br>api/cloud/app/msg/v1.0/clusters/all/traces/3f8b75200d6adbe9?t=1635576841753 |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 返回信息 | 返回信息 |
| data | Object | 具体返回结果体 | 返回结果 |
| status | Boolean | true | true |
| total | Integer | null | null |
| 例如：<br>{<br>"data": {<br>"traceID": "ebe0aed09cb713f7",<br>"processes": {<br>"p1": {<br>"serviceName": "space7e7a24e5:library-comment-springcloud-svc",<br>"tags": [<br>{<br>"key": "hostname",<br>"type": "string",<br>"value": "library-comment-springcloud-697bd7cfcf-zw2fc"<br>},<br>{<br>"key": "ip",<br>"type": "string",<br>"value": "10.240.0.194"<br>},<br>{<br>"key": "jaeger.version",<br>"type": "string",<br>"value": "Java-0.32.0"<br>},<br>{<br>"key": "nameSpace",<br>"type": "string",<br>"value": "space7e7a24e5"<br>}<br>]<br>}<br>},<br>"warnings": null,<br>"startTime": 1567664342907000,<br>"serviceName": "space7e7a24e5:library-comment-springcloud-svc",<br>"operationName": "GET",<br>"totalSpans": 1,<br>"duration": 19362,<br>"children": [<br>{<br>"processes": "space7e7a24e5:library-comment-springcloud-svc",<br>"spanId": "ebe0aed09cb713f7",<br>"span": {<br>"traceID": "ebe0aed09cb713f7",<br>"spanID": "ebe0aed09cb713f7",<br>"flags": 1,<br>"operationName": "GET",<br>"references": [],<br>"startTime": 1567664342907000,<br>"duration": 19362,<br>"tags": [<br>{<br>"key": "http.status_code",<br>"type": "int64",<br>"value": 200<br>},<br>{<br>"key": "component",<br>"type": "string",<br>"value": "java-web-servlet"<br>},<br>{<br>"key": "span.kind",<br>"type": "string",<br>"value": "server"<br>},<br>{<br>"key": "sampler.type",<br>"type": "string",<br>"value": "const"<br>},<br>{<br>"key": "sampler.param",<br>"type": "bool",<br>"value": true<br>},<br>{<br>"key": "http.url",<br>"type": "string",<br>"value": "http://10.240.0.194:32101/actuator/prometheus"<br>},<br>{<br>"key": "http.method",<br>"type": "string",<br>"value": "GET"<br>}<br>],<br>"logs": [],<br>"processID": "p1",<br>"warnings": null<br>},<br>"startTime": 1567664342907000,<br>"depth": 1,<br>"children": []<br>}<br>]<br>},<br>"code": "",<br>"msg": ""<br>\} |  |  |  |

##### 错误返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | bingo.serviceChain.insertion.modFailed | 修改失败 |
| msg | String | cloudos.serviceChain.modFailed | 修改服务链失败 |
| data | Object | null | 返回空 |
| total | Integer | 0 | 返回0个 |
| 例如：<br>{<br>"code": " msg.10010 ",<br>"msg": "参数不正确",<br>"data": null,<br>"total": 0<br>\} |  |  |  |

### dubbo接口文档

#### dubbo引擎创建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /msg/svcgov/v1.0/engines | JSON | POST | 创建dubbo环境，并部署dubbo组件 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| userinfo | true | String | 用户信息 |

| 请求体Body参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| orgId | true | String | 组织ID |
| name | true | String | 环境名称 |
| description | true | String | 环境描述 |
| version | true | String | 环境版本 |
| zone | true | String | 可用域 |
| zoneId | true | String | 可用域ID |
| engineType | true | String | 引擎类型 |
| platMiddle | true | int | 中间件类型 |
| components | true | json | 组件配置信息 |
| middleWares | true | json | 中间件配置信息 |
| middlewareCustomType | true | int | 中间件类型 |
| needApproval | false | boolean | 是否是流程申请，true申请，false创建 |
| clusterType | true | int | 集群类型:1 共享集群 2kaas集群 |
| 例如：<br>1.部署dubbo类型引擎zookeeper注册中心nacos配置中心：<br>{<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "xxxtest",<br>"description": "",<br>"version": "2.7.3",<br>"engineIp": "",<br>"zone": "jmzone",<br>"zoneId": "1f767766-a",<br>"clusterType": 1,<br>"engineType": 3,<br>"middlewareCustomType": 0,<br>"components": [{<br>"version": "v3.5.6",<br>"duplicate": 3,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"storageClass": "dubbo-storageclass",<br>"clusterPort": 0,<br>"nodePort": 0,<br>"component": "dubbo-zookeeper",<br>"type": 6<br>}, {<br>"component": "nacos",<br>"version": "2.0.1",<br>"duplicate": 1,<br>"reqCPU": "1",<br>"reqMemory": "1024Mi",<br>"limitCPU": "2",<br>"limitMemory": "2048Mi",<br>"clusterPort": 0,<br>"nodePort": 0,<br>"type": 10<br>}],<br>"middleWares": [],<br>"needApproval": false,<br>"clusterId": "123456"<br>}<br>2.部署dubbo类型引擎nacos注册中心nacos配置中心：<br>{<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "test-canshu",<br>"description": "",<br>"version": "2.7.3",<br>"engineIp": "",<br>"zone": "主集群工作节点",<br>"zoneId": "929f1d01-f",<br>"clusterType": 1,<br>"engineType": 3,<br>"middlewareCustomType": 0,<br>"components": [<br>{<br>"version": "2.0.1",<br>"duplicate": 1,<br>"reqCPU": "1",<br>"reqMemory": "512Mi",<br>"limitCPU": "2",<br>"limitMemory": "1024Mi",<br>"storageClass": "ads",<br>"clusterPort": 0,<br>"nodePort": 0,<br>"component": "nacos",<br>"type": 10<br>},<br>{<br>"version": "2.0.1",<br>"duplicate": 1,<br>"reqCPU": "1",<br>"reqMemory": "512Mi",<br>"limitCPU": "2",<br>"limitMemory": "1024Mi",<br>"storageClass": "ads",<br>"clusterPort": 0,<br>"nodePort": 0,<br>"component": "nacos",<br>"type": 10<br>}<br>],<br>"middleWares": [],<br>"needApproval": false,<br>"componentType": "nacos",<br>"clusterId": "123456"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": "024f4017435b4df58f75",<br>"name": "xxxtest",<br>"description": "",<br>"namespace": "dubboab4b3a29",<br>"clusterId": "123456",<br>"clusterType": 1,<br>"zone": "jmzone",<br>"version": "2.7.3",<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"engineType": 3,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"engineIp": "172.25.16.60",<br>"zoneId": "1f767766-a",<br>"middlewareCustomType": 0,<br>"needApproval": false,<br>"components": [{<br>"id": "f9c032f541a0410b9cea",<br>"component": "dubbo-zookeeper",<br>"version": "v3.5.6",<br>"duplicate": 3,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": 2181,<br>"nodePort": 31577,<br>"storageClass": "dubbo-storageclass",<br>"userName": "",<br>"password": "",<br>"engineId": "024f4017435b4df58f75",<br>"type": 6,<br>"customType": 0,<br>"createTime": 0,<br>"status": "",<br>"clusterAddr": "",<br>"publicAddr": ""<br>}, {<br>"id": "e9e8b69eefd141da9243",<br>"component": "nacos",<br>"version": "2.0.1",<br>"duplicate": 1,<br>"reqCPU": "1",<br>"reqMemory": "1024Mi",<br>"limitCPU": "2",<br>"limitMemory": "2048Mi",<br>"clusterPort": 8848,<br>"nodePort": 32419,<br>"userName": "",<br>"password": "",<br>"engineId": "024f4017435b4df58f75",<br>"type": 10,<br>"customType": 0,<br>"createTime": 0,<br>"status": "",<br>"clusterAddr": "",<br>"publicAddr": ""<br>}, {<br>"id": "afb9911e33794698be1b",<br>"component": "dubbo-admin",<br>"version": "2.7.3",<br>"duplicate": 1,<br>"reqCPU": "500m",<br>"reqMemory": "1G",<br>"limitCPU": "2",<br>"limitMemory": "2G",<br>"clusterPort": 8080,<br>"nodePort": 30276,<br>"userName": "",<br>"password": "",<br>"engineId": "024f4017435b4df58f75",<br>"type": 7,<br>"customType": 0,<br>"createTime": 0,<br>"status": "",<br>"clusterAddr": "",<br>"publicAddr": ""<br>}],<br>"middleWares": [],<br>"status": "",<br>"createTime": 0,<br>"updateTime": 0,<br>"componentsList": ""<br>},<br>"code": 0,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### dubbo引擎详情查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /msg/svcgov/v1.0/engines/{engine_id\} | JSON | GET | 查询dubbo详情 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| userinfo | true | String | 用户信息 |

| 请求参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engine_id | true | String | 引擎Id |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": {<br>"id": "d680b7a020a048b89c2b",<br>"name": "dubbo-sub",<br>"description": "",<br>"namespace": "dubboe764ff86",<br>"clusterId": "9D87C370-AE3A-48C1-956",<br>"clusterType": 1,<br>"zone": "sub-zone",<br>"version": "2.7.3",<br>"orgId": "9052ae85792143ff955c35c52e8e41bd",<br>"engineType": 3,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"engineIp": "172.25.19.70",<br>"zoneId": "",<br>"middlewareCustomType": 0,<br>"needApproval": false,<br>"components": [{<br>"id": "0e9736987c044adb8e2a",<br>"component": "dubbo-admin",<br>"version": "2.7.3",<br>"duplicate": 1,<br>"reqCPU": "500m",<br>"reqMemory": "1G",<br>"limitCPU": "2",<br>"limitMemory": "2G",<br>"clusterPort": 8080,<br>"nodePort": 65206,<br>"userName": "",<br>"password": "",<br>"engineId": "d680b7a020a048b89c2b",<br>"type": 7,<br>"customType": 0,<br>"createTime": 1635219776856,<br>"status": "running",<br>"clusterAddr": "",<br>"publicAddr": ""<br>}, {<br>"id": "4ba82518bb3f44ff8b9e",<br>"component": "dubbo-zookeeper",<br>"version": "v3.5.6",<br>"duplicate": 3,<br>"reqCPU": "0.5",<br>"reqMemory": "512Mi",<br>"limitCPU": "1",<br>"limitMemory": "1024Mi",<br>"clusterPort": 2181,<br>"nodePort": 32159,<br>"userName": "",<br>"password": "",<br>"engineId": "d680b7a020a048b89c2b",<br>"type": 6,<br>"customType": 0,<br>"createTime": 1635219776854,<br>"status": "",<br>"clusterAddr": "zookeeper://dubbo-zookeeper.dubboe764ff86:2181",<br>"publicAddr": "zookeeper://172.25.19.70:32159"<br>}, {<br>"id": "60e6b3081ce04027b21d",<br>"component": "nacos",<br>"version": "2.0.1",<br>"duplicate": 3,<br>"reqCPU": "1",<br>"reqMemory": "1024Mi",<br>"limitCPU": "2",<br>"limitMemory": "2048Mi",<br>"clusterPort": 8848,<br>"nodePort": 31987,<br>"userName": "",<br>"password": "",<br>"engineId": "d680b7a020a048b89c2b",<br>"type": 10,<br>"customType": 0,<br>"createTime": 1635219776855,<br>"status": "running",<br>"clusterAddr": "nacos.dubboe764ff86:8848",<br>"publicAddr": "172.25.19.70:31987"<br>}],<br>"middleWares": null,<br>"status": "error",<br>"createTime": 1635219776854,<br>"updateTime": 1635219776854,<br>"componentsList": "paas.microservice.engine.register,paas.microservice.engine.datacenter,paas.microservice.engine.configcenter"<br>},<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### dubbo引擎删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /msg/svcgov/v1.0/engines/{engine_id\} | JSON | DELETE | 删除dubbo |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| userinfo | true | String | 用户信息 |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engine_id | true | String | 引擎Id |

##### 常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 30001,<br>"msg": "delete engine error: search engine error ",<br>"total": 0,<br>"status": false<br>\} |  |  |  |

#### dubbo服务查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /msg/svcgov/v1.0/ /engines/{engine_id}/services | JSON | GET | 查询dubbo详情 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |
| userinfo | true | String | 用户信息 |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| engine_id | true | String | 引擎Id |
| page | true | int | 页码数 |
| size | true | int | 页面尺寸》0 |
| pattern | true | string | 服务类型（service/application） |
| filter | false | string | 过滤条件如服务名称 |

##### 常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"service": "com.h3c.commentapi.CommentService",<br>"appName": "library-comment",<br>"group": "",<br>"version": "1.0.0",<br>"providers": [{<br>"address": "10.244.0.110:32201",<br>"alived": 0,<br>"application": "library-comment",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "CC412772E7171569",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"registry": null,<br>"service": "com.h3c.commentapi.CommentService:1.0.0",<br>"url": "dubbo://10.244.0.110:32201/com.h3c.commentapi.CommentService",<br>"username": null,<br>"weight": 100<br>}],<br>"consumers": []<br>},<br>{<br>"service": "com.h3c.log.LogService",<br>"appName": "library-log",<br>"group": "",<br>"version": "1.0.0",<br>"providers": [{<br>"address": "10.244.0.104:32203",<br>"alived": 0,<br>"application": "library-log",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "6C854A0141AE096B",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"registry": null,<br>"service": "com.h3c.log.LogService:1.0.0",<br>"url": "dubbo://10.244.0.104:32203/com.h3c.log.LogService",<br>"username": null,<br>"weight": 100<br>}],<br>"consumers": [{<br>"address": "10.244.0.110",<br>"alived": 0,<br>"application": "library-comment",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "8AEEE25F31D6E0EF",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "com.h3c.log.LogService:1.0.0",<br>"statistics": null,<br>"username": null<br>}]<br>}],<br>"code": 200,<br>"msg": "",<br>"total": 2,<br>"status": true<br>\} |  |  |  |

#### 5.3.5   新建服务降级

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/demotion | JSON | POST | 新建服务降级 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| fail | true | String | 触发服务降级时返回值 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| 例如：<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"group": "",<br>"version": "1.0.0",<br>"fail": "return null"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.6  编辑服务降级

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/demotion | JSON | PUT | 编辑服务降级 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| fail | true | String | 触发服务降级时返回值 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| 例如：<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"group": "",<br>"version": "1.0.0",<br>"fail": "return null"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.7   删除服务降级

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/demotion ?service=<br>{serviceName}&group={group}&version={version\} | JSON | DELETE | 编辑服务权重 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.8   查询服务降级

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/demotion? page=1&size=10&scope={service\} | JSON | GET | 查询服务降级 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| scope | false | String | 查询范围 |
| 例如：<br>{<br>"scope": "service",<br>"page": "1",<br>"size": "10"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"application": "",<br>"group": "",<br>"version": "1.0.0",<br>"force": "",<br>"fail": " return null",<br>"createTime": 0,<br>"updateTime": 0<br>}<br>],<br>"code": 200,<br>"msg": "",<br>"total": 1,<br>"status": true<br>\} |  |  |  |

#### 5.3.9   新建服务权重

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/weight | JSON | POST | 新建服务权重 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| weight | true | String | 服务权重值 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| address | true | String[] | 服务消费者地址 |
| 例如：<br>{<br>"group": "",<br>"version": "1.0.0",<br>"service": "com.dubbo.example.common.service.HelloService",<br>"addresses": [<br>"10.125.104.146:20881"<br>],<br>"weight": 1<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.10 编辑服务权重

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/weight | JSON | PUT | 编辑服务权重 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| weight | true | String | 服务权重值 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| address | true | String[] | 服务消费者地址 |
| 例如：<br>{<br>"group": "",<br>"version": "1.0.0",<br>"service": "com.dubbo.example.common.service.HelloService",<br>"addresses": [<br>"10.125.104.146:20881"<br>],<br>"weight": 12<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.11 删除服务权重

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/weight ?service=<br>{serviceName}&group={group}&version={version\} | JSON | DELETE | 删除服务降级 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.12 查询服务权重

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/weight? page=1&size=10&scope={service\} | JSON | GET | 查询服务降级 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| scope | false | String | 查询范围 |
| 例如：<br>{<br>"scope": "service",<br>"page": "1",<br>"size": "10"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"application": "",<br>"group": "",<br>"version": "1.0.0",<br>"addresses": [<br>"10.125.104.146:20881"<br>],<br>"weight": 1,<br>"createTime": 0,<br>"updateTime": 0<br>}<br>],<br>"code": 200,<br>"msg": "",<br>"total": 1,<br>"status": true<br>\} |  |  |  |

#### 5.3.13 新建负载均衡

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/balancer | JSON | POST | 新建负载均 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| methodName | true | String | 方法名 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| strategy | true | String | 策略 |
| 例如：<br>{<br>"group": "",<br>"version": "1.0.0",<br>"service": "com.dubbo.example.common.service.HelloService",<br>"methodName": "*",<br>"strategy": "random"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.14 编辑负载均衡

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/balancer | JSON | PUT | 编辑负载均衡 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| methodName | true | String | 方法名 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| strategy | true | String[] | 策略 |
| 例如：<br>{<br>"group": "",<br>"version": "1.0.0",<br>"service": "com.dubbo.example.common.service.HelloService",<br>"methodName": "*",<br>"strategy": "random"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.15 删除负载均衡

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/balancer ?service=<br>{serviceName}&group={group}&version={version\} | JSON | DELETE | 删除负载均衡 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.16 查询负载均衡

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/balancer? page=1&size=10&scope={service\} | JSON | GET | 查询负载均衡 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| scope | false | String | 查询范围 |
| 例如：<br>{<br>"scope": "service",<br>"page": "1",<br>"size": "10"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"application": "",<br>"group": "",<br>"version": "1.0.0",<br>"id": "",<br>"methodName": "*",<br>"strategy": "random",<br>"createTime": 0,<br>"updateTime": 0<br>}<br>],<br>"code": 200,<br>"msg": "",<br>"total": 1,<br>"status": true<br>\} |  |  |  |

#### 5.3.17 新建服务禁用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/prohibition | JSON | POST | 新建服务禁用 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| disable | true | boolean | 是否开启 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| address | true | String[] | 服务提供者地址 |
| 例如：<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"addresses": [<br>"10.125.104.146:20881"<br>],<br>"disabled": true,<br>"group": "",<br>"version": "1.0.0"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.18 编辑服务禁用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/prohibition | JSON | PUT | 编辑服务禁用 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| disable | true | boolean | 是否开启 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| address | true | String[] | 服务提供者地址 |
| 例如：<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"addresses": [<br>"10.125.104.146:20881"<br>],<br>"disabled": true,<br>"group": "",<br>"version": "1.0.0"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.19 删除服务禁用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/prohibition?service=<br>{serviceName}&group={group}&version={version\} | JSON | DELETE | 删除服务禁用 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.20 查询服务禁用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/prohibition? page=1&size=10&scope={service\} | JSON | GET | 查询服务禁用 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 |  | 说明 |  |
| --- | --- | --- | --- | --- | --- |
| page |  | true | int |  | 页码 |
| size |  | true | int |  | 页面大小 |
| scope |  | false | String |  | 查询类型 |
| 例如：<br>{<br>"scope": "service",<br>"page": "1",<br>"size": "10"<br>\} |  |  |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"application": "",<br>"group": "",<br>"version": "1.0.0",<br>"disabled": false,<br>"addresses": [<br>"10.125.104.146:20881"<br>],<br>"createTime": 0,<br>"updateTime": 0<br>}<br>],<br>"code": 200,<br>"msg": "",<br>"total": 1,<br>"status": true<br>\} |  |  |  |

#### 5.3.21 新建条件路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/route | JSON | POST | 新建条件路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| type | true | String | 路由类型 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| conditions | true | String[] | 路由规则 |
| 例如：<br>{<br>"type": "condition",<br>"group": "",<br>"version": "1.0.0",<br>"service": "com.dubbo.example.common.service.HelloService",<br>"conditions": [<br>"application=app1 => address=*:20880"<br>]<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.22 编辑条件路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/route | JSON | PUT | 编辑条件路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| type | true | String | 路由类型 |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| conditions | true | String[] | 路由规则 |
| 例如：<br>{<br>"type": "condition",<br>"group": "",<br>"version": "1.0.0",<br>"service": "com.dubbo.example.common.service.HelloService",<br>"conditions": [<br>"application=app1 => address=*:20880"<br>]<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.23 删除条件路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/route?service=<br>{serviceName}&group={group}&version={version\} | JSON | DELETE | 删除条件路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.24 查询条件路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/route? page=1&size=10&scope={service}&type={tpye\} | JSON | GET | 查询服务禁用 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| scope | false | String | 查询范围 |
| tpye | true | String | 类型 |
| 例如：<br>{<br>"scope": "service",<br>"page": "1",<br>"size": "10"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [<br>{<br>"service": "com.dubbo.example.common.service.HelloService",<br>"application": "",<br>"group": "",<br>"version": "1.0.0",<br>"type": "",<br>"enabled": true,<br>"runTime": false,<br>"force": true,<br>"conditions": [<br>"application=app1 => address=*:20880"<br>],<br>"priority": 0,<br>"tags": null,<br>"key": "",<br>"createTime": 0,<br>"updateTime": 0<br>}<br>],<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.25 新建标签路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/route | JSON | POST | 新建条件路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| type | true | String | 路由类型 |
| application | true | String | 应用 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| tags | true | String[] | 路由规则 |
| 例如：<br>{<br>"type": "tag",<br>"group": "",<br>"version": "1.0.0",<br>"application": "zk-dubbo-provider",<br>"tags": [<br>{<br>"name": " tag1",<br>"addresses": [<br>"192.168.0.1:20881"<br>]<br>}<br>]<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.26 编辑标签路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/route | JSON | PUT | 编辑条件路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| type | true | String | 路由类型 |
| application | true | String | 应用 |
| service | true | String | 服务名 |
| version | true | String | 版本 |
| tags | true | String[] | 路由规则 |
| 例如：<br>{<br>"type": "tag",<br>"group": "",<br>"version": "1.0.0",<br>"application": "zk-dubbo-provider",<br>"tags": [<br>{<br>"name": " tag1",<br>"addresses": [<br>"192.168.0.1:20881"<br>]<br>}<br>]<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.27 删除标签路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/route?service=<br>{serviceName}&group={group}&version={version\} | JSON | DELETE | 删除标签路由 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| group | true | String | 服务所在组 |
| service | true | String | 服务名 |
| version | true | String | 版本 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": null,<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

#### 5.3.28 查询标签路由

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/msg/svcgov/v1.0/engines/{engine_id}/rules/route? page=1&size=10&route_type=tag&scope=application | JSON | GET | 查询服务禁用 |

##### 参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| X-Auth-Token | true | String | 用户的Token |

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | true | int | 页码 |
| size | true | int | 页面大小 |
| scope | false | String | 查询范围 |
| type | True | String | 类型 |
| 例如：<br>{<br>"type": "tag",<br>"scope": "application",<br>"page": "1",<br>"size": "10"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [<br>{<br>"service": "",<br>"application": "zk-dubbo-provider",<br>"group": "",<br>"version": "",<br>"type": "",<br>"enabled": true,<br>"runTime": false,<br>"force": true,<br>"conditions": null,<br>"priority": 0,<br>"tags": [<br>{<br>"name": "tag1",<br>"addresses": [<br>"asd"<br>]<br>}<br>],<br>"key": "",<br>"createTime": 0,<br>"updateTime": 0<br>}<br>],<br>"code": 200,<br>"msg": "",<br>"total": 0,<br>"status": true<br>\} |  |  |  |

##### 常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回空 |
| total | Integer | 0 | 0 |
| 例如：<br>{<br>"data": [{<br>"service": "com.h3c.commentapi.CommentService",<br>"appName": "library-comment",<br>"group": "",<br>"version": "1.0.0",<br>"providers": [{<br>"address": "10.244.0.110:32201",<br>"alived": 0,<br>"application": "library-comment",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "CC412772E7171569",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"registry": null,<br>"service": "com.h3c.commentapi.CommentService:1.0.0",<br>"url": "dubbo://10.244.0.110:32201/com.h3c.commentapi.CommentService",<br>"username": null,<br>"weight": 100<br>}],<br>"consumers": []<br>},<br>{<br>"service": "com.h3c.log.LogService",<br>"appName": "library-log",<br>"group": "",<br>"version": "1.0.0",<br>"providers": [{<br>"address": "10.244.0.104:32203",<br>"alived": 0,<br>"application": "library-log",<br>"created": null,<br>"dynamic": true,<br>"enabled": true,<br>"expired": null,<br>"hash": "6C854A0141AE096B",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"registry": null,<br>"service": "com.h3c.log.LogService:1.0.0",<br>"url": "dubbo://10.244.0.104:32203/com.h3c.log.LogService",<br>"username": null,<br>"weight": 100<br>}],<br>"consumers": [{<br>"address": "10.244.0.110",<br>"alived": 0,<br>"application": "library-comment",<br>"collected": null,<br>"conditionRoutes": null,<br>"created": null,<br>"expired": null,<br>"hash": "8AEEE25F31D6E0EF",<br>"id": null,<br>"ids": null,<br>"miss": false,<br>"modified": null,<br>"now": null,<br>"operator": null,<br>"operatorAddress": null,<br>"override": null,<br>"overrides": null,<br>"providers": null,<br>"registry": null,<br>"result": null,<br>"service": "com.h3c.log.LogService:1.0.0",<br>"statistics": null,<br>"username": null<br>}]<br>}],<br>"code": 200,<br>"msg": "",<br>"total": 2,<br>"status": true<br>\} |  |  |  |
