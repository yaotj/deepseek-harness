# CloudOS 5.0 容器镜像仓库管理 API

> 本节选自 [CloudOS 5.0_PaaS_API_E7501.md](CloudOS%205.0_PaaS_API_E7501.md) §容器云，接口定义与原文一致；完整文档见同目录 PaaS 全文 Markdown。

**路径前缀**：下表路径均为**相对路径**，完整 URL = `http://{平台IP}:11000` + `/api/cloud/hcr/v1.0` + 下表路径（原文 §容器云 开头注明「镜像服务api前缀：/api/cloud/hcr/v1.0」）。以 `/harbor/` 开头的路径原文未单独说明归属，联调时 MUST 实测确认。

**注意**：本章各接口的请求头示例里 `X-Auth-Token` 填的是一串加密后的用户信息样例（`gAAAAAB...`），属文档示例值，不是可用凭据。

## 接口索引

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 镜像类型查询 | GET | `/types` | 镜像类型查询 |
| 镜像类型创建 | POST | `/types` | 镜像类型创建 |
| 镜像类型修改 | PUT | `/types/{uuid}` | 镜像类型修改 |
| 镜像类型删除 | DELETE | `/types/{uuid}` | 镜像类型删除 |
| 上传镜像 | POST | `/images/load` | 上传镜像 |
| 镜像列表查询 | GET | `/images/versions` | 镜像列表查询，<br>/images/versions? harborProjectName=library&t=163 |
| 详情镜像查询 | GET | `/image/versions` | 镜像详情查询<br>/image/versions?name=nginxtestpublic&type=public&t |
| 镜像构建 | POST | `/images/build` | 镜像构建 |
| 查看镜像扫描漏洞概览 | GET | `/harbor/images/scan/{imageUrl}` | 例imageUrl=<br>os-harbor-svc.default.svc.cloudos:443/user/1df |
| 查看镜像扫描漏洞详情 | GET | `/harbor/images/scanDetail/{imageUrl}` | 例imageUrl=<br>os-harbor-svc.default.svc.cloudos:443/user/1df |
| 镜像扫描 | POST | `/harbor/images/scan` | 镜像扫描 |
| 镜像版本特性编辑 | POST | `/images/updateImage` | 镜像版本特性编辑 |
| 镜像下载 | GET | `/images/expose/{imageUrl}` | 根据镜像Url下载镜像tar包 |
| 镜像仓库删除 | POST | `/images/versions` | 镜像仓库的删除<br>/images/versions |
| 单一镜像删除 | POST | `/images/deleteOneImage` | 单一镜像删除<br>/images/deleteOneImage |
| 镜像批量删除 | POST | `/images/delete` | 镜像批量删除<br>/images/delete |
| 镜像发布到公有仓库 | POST | `/images/public?apply=false` | 私有镜像发布到公有仓库,apply=false非申请流程，apply=true为普通用户的申请流程 |
| 镜像公共信息编辑 | POST | `/images/updateImageGroup` | 镜像公共信息编辑 |
| 未使用镜像查询 | GET | `/image/unused` | 查询未使用状态的镜像 |
| 未使用镜像清理 | POST | `/images/v2/unused/clear` | 未使用镜像清理 |
| 镜像仓库容量查询 | GET | `/harbor/v2/systeminfo/volumes` | 镜像仓库容量查询 |
| 镜像仓库自动清理查询 | GET | `/retains` | 镜像仓库自动清理查询 |
| 镜像仓库自动清理设置 | POST | `/retains` | 镜像仓库自动清理设置 |
| 已存在镜像校验 | POST | `/images/checkImgExisted` | 查询镜像是否已经存在 |
| 已存在镜像类型校验 | POST | `/types/checkName` | 查询镜像类型是否已经存在 |
| 远端地址下载镜像 | POST | `/images/remoteLoad` | 从第三方获取镜像 |
| 创建项目 | POST | `/harbor/projects` | 创建新的项目 |
| 检查项目是否存在 | GET | `/harbor/projects/check/{projectName}` | 检查项目名是否存在 |
| 项目列表 | GET | `/projects` | 项目列表：/projects? page=1&size=10&name=&type=0 |
| 项目删除 | DELETE | `/projects/{project_id}` | project_id：要删除的项目id |
| 上传镜像-远端上传 | POST | `/images/remoteLoad` | 上传镜像 |
| 镜像下发到集群查询 | GET | `/image/distribution` | 请求url:/image/distribution?imgUrl= |
| 获取镜像扫描状态 | GET | `/harbor/images/scan/{imgUrl}` | imgUrl:镜像全 |
| 校验用户名是否存在 | GET | `/harbor/user/check/{userName}` | userName:用户名 |
| 添加用户 | POST | `/harbor/addHarborUser` | 新增用户 |
| 用户列表 | GET | `/harbor/users` | 获取用户列表：<br>/harbor/users?page=1&size=10&username=&ownerName= |
| 重置用户密码 | PUT | `/harbor/users/password/{user_id}` | user_id:用户编号 |
| 删除用户 | DELETE | `/harbor/users/{uuid}/{harborId}` | uuid：用户数据id<br>harborId：用户harbor上的id |
| 项目成员列表 | GET | `/projects/{project_id}/members` | 获取用户列表：<br>/projects/{project_id}/members? page=1&size=10&en |
| 绑定项目成员 | POST | `/projects/{project_id}/members` | project_id：项目id |
| 解绑项目成员 | DELETE | `/projects/{project_id}/members/{user_id}` | project_id：项目id<br>user_id: 成员id |
| 修改项目成员信息 | POST | `/harbor/project/updateProjectUser` | 修改项目中成员的权限 |
| 访问凭证下载 | GET | `/harbor/systeminfo/getcert` | 下载访问凭证 |

---


## 容器镜像

镜像服务api前缀：/api/cloud/hcr/v1.0

### 镜像类型查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types | JSON | GET | 镜像类型查询 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| Uuid | false | String | 查询参数，镜像类型ID |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常成功情况下，code为” imgType.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为”ok” |
| data | Object | 具体返回结果体 | 响应数据：（uuid：类型id，name：类型名称，createTime：创建时间，userId：创建者id，tentantId：组织id） |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"status": true,<br>"code": "imgType.0",<br>"msg": "ok",<br>"data": [<br>{<br>"uuid": "20e68cab-c634-468d-8d0c-56cb6096",<br>"name": "未分组",<br>"createTime": 1546950030000,<br>“userId”: "1df71957d770436493a84f24d9344853",<br>“tentantId”: "9052ae85792143ff955c35c52e8e41bd"<br>},<br>{<br>"uuid": "f1ee6eb5-a913-4d3c-972e-88ceef6d",<br>"name": "ctest",<br>"createTime": 1550197851000,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"tentantId": "78235b23a2c24b6aac2682cebc11b081"<br>}<br>],<br>"httpCode": 200<br>\} |  |  |  |

### 镜像类型创建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types | JSON | POST | 镜像类型创建 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 镜像类型名称 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”imgType.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：（uuid：类型id，name：类型名称，createTime：创建时间，userId：创建者id，tentantId：组织id） |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像类型名称 | 镜像类型名称 |
| 例如：<br>{<br>"status": true,<br>"code": "imgType.0",<br>"msg": "ok",<br>"data": {<br>"uuid": "6657ff7d-1f4c-4bf4-b1be-f6d41e45",<br>"name": "testType",<br>"createTime": 1550200294701,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"tentantId": "78235b23a2c24b6aac2682cebc11b081"<br>},<br>"httpCode": 200<br>"resources": [<br>"abc"<br>]<br>\} |  |  |  |

### 镜像类型修改

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types/{uuid\} | JSON | PUT | 镜像类型修改 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 镜像类型名称，规格：1-32位字符，英文字母（a-z,A-Z）、数字(0-9)以及括号中的特殊字符（.-_@），不能以特殊字符开头 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”imgType.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：（uuid：类型id，name：类型名称，createTime：创建时间，userId：创建者id，tentantId：组织id） |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像类型名称 | 镜像类型名称 |
| 例如：<br>{<br>"status": true,<br>"code": "imgType.0",<br>"msg": "ok",<br>"data": {<br>"uuid": "6657ff7d-1f4c-4bf4-b1be-f6d41e45",<br>"name": "testType",<br>"createTime": 1550200294701,<br>"userId": "1df71957d770436493a84f24d9344853",<br>"tentantId": "78235b23a2c24b6aac2682cebc11b081"<br>},<br>"httpCode": 200<br>"resources": [<br>"edit"<br>]<br>\} |  |  |  |

### 镜像类型删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types/{uuid\} | JSON | DELETE | 镜像类型删除 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”imgType.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：删除的镜像分类的uuid |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像类型名称 | 镜像类型名称 |
| 例如：<br>{<br>"status": true,<br>"code": "imgType.0",<br>"msg": "ok",<br>"data": {<br>"b9566a3f-0f59-4c46-8c8b-4d4ba8d6"<br>},<br>"httpCode": 200<br>"resources": [<br>"abc"<br>]<br>\} |  |  |  |

### 上传镜像

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/load | JSON | POST | 上传镜像 |

##### 请求头参数说明

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

##### 请求体参数说明

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

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：镜像名 |
| httpCode | Integer | http码 | 200 |

### 镜像列表查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/versions | JSON | GET | 镜像列表查询，<br>/images/versions? harborProjectName=library&t=1635736224698&page=1&size=10&type=public&imgAlias=qh&imgType=0455117a-4d35-4dde-b6a6-1b572322 |

##### 请求头参数说明

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

##### 正常返回结果

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

### 详情镜像查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /image/versions | JSON | GET | 镜像详情查询<br>/image/versions?name=nginxtestpublic&type=public&tentantId=9052ae85792143ff955c35c52e8e41bd&imgType=20e68cab-c634-468d-8d0c-56cb6096&harborProjectName=user |

##### 请求头参数说明

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

##### 正常返回结果

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

### 镜像构建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/build | JSON | POST | 镜像构建 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

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

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 错误码, 正常成功情况下为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据，build:镜像构建日志，push:镜像推送日志 |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像名称：版本 | 镜像名称：版本 |
| 例如：<br>{<br>"status":true,<br>"code":"img.0",<br>"msg":"ok",<br>"data":{<br>"build":[<br>{<br>"stream":"Step 1/1 : FROM 10.125.31.185:30002/test-1119/1df71957d770436493a84f24d9344853/test001:test001\n"<br>},<br>{<br>"stream":" ---> 41c2669da537\n"<br>},<br>{<br>"stream":"Successfully built 41c2669da537\n"<br>}<br>],<br>"push":[<br>{<br>"status":"The push refers to a repository [10.125.31.185:30002/testdelete/1df71957d770436493a84f24d9344853/test1122gjjx]"<br>},<br>{<br>"status":"Preparing",<br>"progressDetail":{},<br>"id":"25e448890d3f"<br>},<br>{<br>"status":"Preparing",<br>"progressDetail":{},<br>"id":"ceaf9e1ebef5"<br>},<br>{<br>"status":"Preparing",<br>"progressDetail":{},<br>"id":"9b9b7f3d56a0"<br>},<br>{<br>"status":"Preparing",<br>"progressDetail":{},<br>"id":"f1b5933fe4b5"<br>},<br>{<br>"status":"Mounted from test12/1df71957d770436493a84f24d9344853/testa",<br>"progressDetail":{},<br>"id":"9b9b7f3d56a0"<br>},<br>{<br>"status":"Mounted from test12/1df71957d770436493a84f24d9344853/testa",<br>"progressDetail":{},<br>"id":"25e448890d3f"<br>},<br>{<br>"status":"Mounted from test12/1df71957d770436493a84f24d9344853/testa",<br>"progressDetail":{},<br>"id":"ceaf9e1ebef5"<br>},<br>{<br>"status":"Mounted from test12/1df71957d770436493a84f24d9344853/testa",<br>"progressDetail":{},<br>"id":"f1b5933fe4b5"<br>},<br>{<br>"status":"test1122gjjx: digest: sha256:6ffe76a781c55c14b0cb27459bb05026b415097f9e49943bcb6ac9f7f3fe05dd size: 1159"<br>},<br>{<br>"progressDetail":{},<br>"aux":{<br>"Tag":"test1122gjjx",<br>"Digest":"sha256:6ffe76a781c55c14b0cb27459bb05026b415097f9e49943bcb6ac9f7f3fe05dd",<br>"Size":1159<br>}<br>}<br>]<br>},<br>"httpCode":200,<br>"resources":[<br>"test1122gjjx:test1122gjjx"<br>]<br>\} |  |  |  |

### 查看镜像扫描漏洞概览

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/images/scan/{imageUrl\} | JSON | GET | 例imageUrl=<br>os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/testbuild:1.0 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 错误码, 正常成功情况下为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据 |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"code": "img.0",<br>"data": {<br>“detail”: "{\"duration\":11,\"severity\":\"Critical\",\"summary\":{\"summary\":{\"High\":18,\"Low\":2,\"Medium\":15,\"Critical\":1},\"total\":36,\"fixable\":36},\"start_time\":\"2022-01-06T08:21:35.000Z\",\"scan_status\":\"Success\",\"report_id\":\"6d00eb57-4b6f-4660-bedc-805118e80f35\",\"complete_percent\":100,\"end_time\":\"2022-01-06T08:21:46.000Z\",\"scanner\":{\"vendor\":\"Aqua Security\",\"name\":\"Trivy\",\"version\":\"v0.16.0\"}}"<br>“imgName”: "1df71957d770436493a84f24d9344853/testbuild"<br>“projectName”: "user"<br>“scanStatus”: "success"<br>“version”: "1.0"<br>},<br>"httpCode": 200,<br>"msg": "ok",<br>"status": true<br>\} |  |  |  |

### 查看镜像扫描漏洞详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/images/scanDetail/{imageUrl\} | JSON | GET | 例imageUrl=<br>os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/testbuild:1.0 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 错误码, 正常成功情况下为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据 |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"code": "img.0",<br>"data": {<br>“detail”:<br>"{\"application/vnd.scanner.adapter.vuln.report.harbor+json; version=1.0\":{\"generated_at\":\"2022-01-06T08:21:42.716829816Z\",\"scanner\":{\"name\":\"Trivy\",\"vendor\":\"Aqua Security\",\"version\":\"v0.16.0\"},\"severity\":\"Critical\",\"vulnerabilities\":[{\"id\":\"CVE-2021-36159\",\"package\":\"apk-tools\",….}]}”<br>“imgName”: "1df71957d770436493a84f24d9344853/testbuild"<br>“projectName”: "user"<br>“scanStatus”: "success"<br>“version”: "1.0"<br>},<br>"httpCode": 200,<br>"msg": "ok",<br>"status": true<br>\} |  |  |  |

### 镜像扫描

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/images/scan | JSON | POST | 镜像扫描 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgName | true | String | 镜像名称 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 错误码,正常为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据，scanStatus:扫描状态，scanning为扫描中，imgName:镜像名称，version: 镜像版本 |
| httpCode | Integer | http码 | 200 |
| resources | List<String> | 镜像名称：版本 | 镜像名称：版本 |
| 例如：<br>{<br>"status": true,<br>"code": "img.0",<br>"msg": "ok",<br>"data": {<br>"scanStatus": "scanning",<br>"imgName": "tomcat-0730",<br>"version": "v1.0"<br>},<br>"httpCode": 200,<br>"resources": [<br>"tomcat-0730:v1.0"<br>]<br>\} |  |  |  |

### 镜像版本特性编辑

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/updateImage | JSON | POST | 镜像版本特性编辑 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 镜像id |
| featureInfo | false | String | 镜像版本特性 |
| type | true | String | 镜像仓库类型 |
| tentantID | true | String | 组织id |
| userID | true | String | 用户Id |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true为正常 |
| code | String | 错误码 | 正常成功情况下，code为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| resources | String | 具体返回结果体 | 镜像名称:镜像版本 |
| httpCode | Integer | 200 |  |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","httpCode":200,"resources":["testimage:2.0"]\} |  |  |  |

### 镜像下载

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/expose/{imageUrl\} | JSON | GET | 根据镜像Url下载镜像tar包 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>二进制文件流 |  |  |  |

### 镜像仓库删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/versions | JSON | POST | 镜像仓库的删除<br>/images/versions |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imageGroupId | true | String | 镜像仓库id |
| imageName | true | String | 镜像仓库名 |
| images | false | String | 镜像版本<br>[{“id”:”hyeoc9ds”,”imgUrl”:”os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/testbuild:1.0” }] |
| imgType | true | String | 镜像分类 |
| projectName | true | String | 项目名 |
| tagCount | true | String | 仓库版本数 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| data | Object | 响应体 | 其中success为删除成功的镜像，failed为删除失败的镜像，notFound为未找到的镜像，notAllow为不允许删除的镜像，delAll是否该镜像所有版本都删除完 |
| httpCode | Integer | http状态码 | 200 |
| resources | List<String> | 删除的镜像名数组 | ["gg:v1"] |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":{"success":["v1"],"failed":[],"notFound":[],"notAllow":[],"delAll":true},"httpCode":200,"resources":["gg:v1"]\} |  |  |  |

### 单一镜像删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/deleteOneImage | JSON | POST | 单一镜像删除<br>/images/deleteOneImage |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| id | true | String | 镜像id |
| imgUrl | true | String | 镜像url |
| tentantID | true | String | 组织id |
| userID | true | String | 用户id |
| 例如<br>{"id":"igirui1o","tentantID":"9052ae85792143ff955c35c52e8e41bd","userID":"1df71957d770436493a84f24d9344853","imgType":"fb321900-4d4f-4f1a-afc1-fa6f53c8"\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| data | Boolean | 响应体 | 成功为true，否则为false |
| httpCode | Integer | http状态码 | 200 |
| resources | List<String> | 删除的镜像名数组 | ["tomcat:8.1"] |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":true,"httpCode":200,"resources":["tomcat:8.1"]\} |  |  |  |

### 镜像批量删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/delete | JSON | POST | 镜像批量删除<br>/images/delete |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| images | true | String | 镜像id的数组 |
| imgType | true | String | 镜像分类的id |
| 例如<br>{<br>"images":[{"id":"sboky4ao",”imgUrl”:” os-harbor-svc.default.svc.cloudos:443/fff/testdel:2.0”}],<br>"imgType":"20e68cab-c634-468d-8d0c-56cb6096"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| data | Object | 响应体 | 其中success为删除成功的镜像，failed为删除失败的镜像，notFound为未找到的镜像，notAllow为不允许删除的镜像，delAll是否该镜像所有版本都删除完 |
| httpCode | Integer | http状态码 | 200 |
| 例如：<br>{<br>"status":true,<br>"code":"img.0",<br>"msg":"ok",<br>"data":{<br>"success":["2.0"],<br>"failed":[],<br>"notFound":[],<br>"notAllow":[],<br>"delAll":true<br>},<br>"httpCode":200,<br>"resources":[" testimage:2.0"]<br>\} |  |  |  |

### 镜像发布到公有仓库

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/public?apply=false | JSON | POST | 私有镜像发布到公有仓库,apply=false非申请流程，apply=true为普通用户的申请流程 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

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

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":false,"httpCode":200,"resources":["test"]\} |  |  |  |

### 镜像公共信息编辑

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/updateImageGroup | JSON | POST | 镜像公共信息编辑 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

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

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| resoucres | List<String> | 编辑镜像组名称 | 编辑镜像组名称 |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","httpCode":200, "resources":[“abc”]\} |  |  |  |

### 未使用镜像查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /image/unused | JSON | GET | 查询未使用状态的镜像 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| data | Object | 返回结果 | 查询结果，镜像信息 |
| 例如：<br>{<br>"status": true,<br>"code": "img.0",<br>"msg": "ok",<br>"data": [<br>{<br>"uuid": "pmpdqhy7",<br>"imgName": "liwei2",<br>"version": "v1",<br>"updateTime": "2020-07-17 15:41:47",<br>"type": "public",<br>"imgType": "20e68cab-c634-468d-8d0c-56cb6096",<br>"imgSize": "46.26",<br>"userName": "liweitest",<br>"imgUrl": "os-harbor-svc.default.svc.cloudos:443/library/a2fd40ac47eb4efc9a2921d73afff465/liwei2:v1"<br>},<br>{<br>"uuid": "cuu0uknl",<br>"imgName": "testtt",<br>"version": "123",<br>"updateTime": "2020-08-04 15:29:30",<br>"type": "public",<br>"imgType": "20e68cab-c634-468d-8d0c-56cb6096",<br>"imgSize": "46.26",<br>"userName": "admin",<br>"imgUrl": "os-harbor-svc.default.svc.cloudos:443/library/1df71957d770436493a84f24d9344853/testtt:123"<br>}<br>],<br>"httpCode": 200<br>\} |  |  |  |

### 未使用镜像清理

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/v2/unused/clear | JSON | POST | 未使用镜像清理 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgs | true | List | 清理镜像，imgUrl:清理镜像url，uuid：清理镜像ID |
| 例如：<br>{<br>Imgs:[<br>{“imgUrl”: “os-harbor-svc.default.svc.cloudos:443/library/2kdjvnckl12j3134132k/abc:test”, “uuid”: “tm4jzj3”},<br>{“imgUrl”: “os-harbor-svc.default.svc.cloudos:443/library/ccm165jcccswk5:cba:1”, “uuid”: “5mmkl1”}<br>]<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| resoucres | List<String> | 清理镜像 | 编辑镜像名称：版本 |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","httpCode":200, "resources":[“abc:1”]\} |  |  |  |

### 镜像仓库容量查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/v2/systeminfo/volumes | JSON | GET | 镜像仓库容量查询 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | false | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| data | Object | 返回查询结果 | 查询结果，total:总容量, used:已使用容量， usedRate:已使用比例（%） |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","httpCode":200, "data":{“total”: “49.1 GB”, “used”: “788.93MB”, “usedRate”: “15.1%”}\} |  |  |  |

### 镜像仓库自动清理查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /retains | JSON | GET | 镜像仓库自动清理查询 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | fasle | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| data | Object | 返回查询结果 | 查询结果，inherit:是否继承, enabled:是否启用， retainDays:保留时长，projectId：<br>组织id，parentId：父组织id |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":{"inherit":false,"enabled":false,"retainDays":15,<br>"projectId":"9052ae85792143ff955c35c52e8e41bd","parentId":"default"},"httpCode":200\} |  |  |  |

### 镜像仓库自动清理设置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /retains | JSON | POST | 镜像仓库自动清理设置 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| enabled | true | True/false | 是否开启自动清理 |
| inherit | true | True/false | 是否继承父组织 |
| projectId | true | String | 组织id |
| retainDays | true | Int | 保留时长 |
| 例如：<br>{<br>"enabled": true,<br>"inherit": false,<br>"projectId": "9052ae85792143ff955c35c52e8e41bd",<br>"retainDays": 1<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | 正常true，异常false |
| code | String | 错误码 | 正常成功情况下，code为img.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为ok |
| httpCode | Integer | http状态码 | 200 |
| 例如：<br>{"status":true,"code":"img.0","msg":"ok","data":{"uuid":"root-auto-clean","enabled":1,"inherit":0,"projectId":"9052ae85792143ff955c35c52e8e41bd","parentId":"default","ttl":180,<br>"createdAt":1629455604000,"createBy":"root","lastModifiedAt":1635745378863,"lastModifiedBy":"root"},"httpCode":200\} |  |  |  |

### 已存在镜像校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/checkImgExisted | JSON | POST | 查询镜像是否已经存在 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| imgName | true | String | 镜像名称 |
| version | true | List<String> | 镜像版本列表 |
| type | true | String | 目标镜像仓库类型：公有（public）或私有（private） |
| 例如<br>{<br>"imgName": "existed-test-admin-public",<br>"version": [<br>"1"<br>],<br>"type": "public",<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：（success：不存在镜像版本列表 ，failed：已存在镜像版本列表） |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"status": true,<br>"code": "img.0",<br>"msg": "ok",<br>"data": {<br>"success": [<br>"1"<br>],<br>"failed": []<br>},<br>"httpCode": 200<br>\} |  |  |  |

### 已存在镜像类型校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /types/checkName | JSON | POST | 查询镜像类型是否已经存在 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 镜像类型名称 |
| uuid | 创建镜像类型 false<br>编辑镜像类型 true | String | 镜像类型ID |
| 例如<br>{<br>"name": "abc123",<br>"uuid": "41c5db78-1226-4ce2-8721-3e2f7f9c"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：imgType.0 |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：false，镜像类型不存在；true，镜像类型已存在 |
| httpCode | Integer | http码 | 200 |
| 例如：<br>{<br>"status": true,<br>"code": "",<br>"msg": "ok",<br>"data": false,<br>"httpCode": 200<br>\} |  |  |  |

### 远端地址下载镜像

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/remoteLoad | JSON | POST | 从第三方获取镜像 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

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

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data为镜像ID |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 | 操作对象，正常情况下为“镜像名:版本” |
| 例如：<br>{<br>"code": "string",<br>"data": "string",<br>"httpCode": int,<br>"msg": "string",<br>"resources": ["string"],<br>\} |  |  |  |

### 创建项目

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/projects | JSON | POST | 创建新的项目 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectName | true | String | 项目名称 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data为true |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 | 操作对象，正常情况下为“项目名称” |
| 例如：<br>{<br>"code": "string",<br>"data": "string",<br>"httpCode": int,<br>"msg": "string",<br>"resources": ["string"],<br>\} |  |  |  |

### 检查项目是否存在

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/projects/ check/{projectName\} | JSON | GET | 检查项目名是否存在 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中exist的值为是否存在 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "{\"exist\":false}",<br>"httpCode": 200<br>\} |  |  |  |

### 项目列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects | JSON | GET | 项目列表：/projects? page=1&size=10&name=&type=0 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| page | false | String | 页数 |
| size | false | String | 每页大小 |
| name | false | String | 项目名称 |
| type | true | String | 项目类型：公有私有 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": {<br>"total": 1,<br>"data": [<br>{<br>"creation_time": "2021-11-22T18:09:15.000+08:00",<br>"current_user_role_id": 1,<br>"metadata": {<br>"public": "false"<br>},<br>"update_time": "2021-11-22T18:09:15.000+08:00",<br>"owner_name": "admin",<br>"project_id": 68,<br>"owner_id": 1,<br>"name": "tass1234",<br>"current_user_role_ids": [<br>1<br>],<br>"cve_allowlist": {<br>"creation_time": "2021-11-22T18:09:15.000+08:00",<br>"update_time": "2021-11-22T18:09:15.000+08:00",<br>"project_id": 68,<br>"id": 60,<br>"items": []<br>},<br>"repo_count": 1<br>}<br>]<br>},<br>"httpCode": 200<br>\} |  |  |  |

### 项目删除

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects /{project_id\} | JSON | DELETE | project_id：要删除的项目id |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": 200,<br>"httpCode": 200,<br>"resources": [<br>"String"<br>]<br>\} |  |  |  |

### 上传镜像-远端上传

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /images/ remoteLoad | JSON | POST | 上传镜像 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | multipart/form-data |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

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

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | boolean | 状态 | true：正常，false：异常 |
| code | String | 错误码 | 正常情况：”img.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | Object | 具体返回结果体 | 响应数据：镜像名 |
| httpCode | Integer | http码 | 200 |

### 镜像下发到集群查询

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /image/distribution | JSON | GET | 请求url:/image/distribution?imgUrl= |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| imgUrl | true | String | 要下发的镜像 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": 200,<br>"httpCode": 200,<br>"resources": [<br>"String"<br>]<br>\} |  |  |  |

### 获取镜像扫描状态

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/images/scan/{imgUrl\} | JSON | GET | imgUrl:镜像全 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": {<br>"scanStatus": "success",<br>"detail": "",<br>"imgName": "test",<br>"version": "v2",<br>"projectName": "test"<br>},<br>"httpCode": 200,<br>\} |  |  |  |

### 校验用户名是否存在

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/user/check/{userName\} | JSON | GET | userName:用户名 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "{\"exist\":false}",<br>"httpCode": 200<br>\} |  |  |  |

### 添加用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/ addHarborUser | JSON | POST | 新增用户 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| userName | true | String | 用户名 |
| password | true | String | 密码 |
| realName | true | String | 真实姓名 |
| email | true | String | 邮箱 |
| comment | False | String | 备注信息 |
| 例如：<br>{<br>"userName":"test",<br>"password":"test@h3c",<br>"realName":"test",<br>"email":"test@h3c.com",<br>"comment":""<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>"test"<br>]<br>\} |  |  |  |

### 用户列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/users | JSON | GET | 获取用户列表：<br>/harbor/users?page=1&size=10&username=&ownerName= |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-<br>jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| page | false | String | 页数 |
| size | false | String | 每页大小 |
| username | false | String | 用户名，用于条件过滤 |
| ownerName | false | String | 项目拥有者 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": {<br>"total": 2,<br>"data": [<br>{<br>"uuid": "22d5fffb-416e-45ed-9c6f-06286347",<br>"name": "9052ae85792143ff955c35c52e8e41bd",<br>"realName": "9052ae85792143ff955c35c52e8e41bd",<br>"email": "9052ae85792143ff955c35c52e8e41bd@ccr.com",<br>"comment": "",<br>"tentantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tentantName": "root",<br>"creatorId": "1df71957d770436493a84f24d9344853",<br>"creatorName": "admin",<br>"createTime": 1638526737000,<br>"harborId": "13"<br>}<br>]<br>},<br>"httpCode": 200<br>\} |  |  |  |

### 重置用户密码

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/ users/password/{user_id\} | JSON | PUT | user_id:用户编号 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| oldPassword | true | String | 原密码 |
| newPassword | true | String | 新密码 |
| 例如：<br>{<br>oldPassword：原密码<br>newPassword：新密码<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>"9"<br>]<br>\} |  |  |  |

### 删除用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/ users/{uuid}/{harborId\} | JSON | DELETE | uuid：用户数据id<br>harborId：用户harbor上的id |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>""<br>]<br>\} |  |  |  |

### 项目成员列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects/{project_id}/members | JSON | GET | 获取用户列表：<br>/projects/{project_id}/members? page=1&size=10&entityname=m |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-<br>jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |
| project_id | true | String | 项目 |
| page | false | String | 页数 |
| size | false | String | 每页大小 |
| entityname | false | String | 模糊查询entityname的项目 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": {<br>"total": 2,<br>"data": [<br>]<br>},<br>"httpCode": 200<br>\} |  |  |  |

### 绑定项目成员

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects/{project_id}/members | JSON | POST | project_id：项目id |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| role_id | true | String | 角色 |
| username | true | String | 绑定的用户名 |
| 例如：<br>{<br>"role_id": 1,<br>"member_user": {<br>"username": "9052ae85792143ff955c35c52e8e41bd_guest"<br>}<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>""<br>]<br>\} |  |  |  |

### 解绑项目成员

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /projects/{project_id}/members/{user_id\} | JSON | DELETE | project_id：项目id<br>user_id: 成员id |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>]<br>\} |  |  |  |

### 修改项目成员信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/project/updateProjectUser | JSON | POST | 修改项目中成员的权限 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 请求体参数说明

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| projectId | true | String | 项目id |
| projectName | true | String | 项目名称 |
| memberId | true | String | 成员id |
| memberName | true | String | 成员名称 |
| roleId | true | String | 角色id，1：项目管理员 2：开发者 3：访客 |
| 例如：<br>{<br>"projectId":"58",<br>"projectName":"test",<br>"memberId":"109",<br>"memberName":"aaa",<br>"roleId":"3"<br>\} |  |  |  |

##### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为“project.0” |
| msg | String | 错误信息 | 正常成功情况下，msg为“ok” |
| data | String | 具体返回结果体 | 正常成功情况下，data中具体结果 |
| httpCode | Integer | HTTP请求响应码 | 正常情况下，httpCode为200 |
| resources | Array | 操作对象 |  |
| 例如：<br>{<br>"status": true,<br>"code": "project.0",<br>"msg": "ok",<br>"data": "",<br>"httpCode": 200,<br>"resources": [<br>]<br>\} |  |  |  |

### 访问凭证下载

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /harbor/systeminfo/getcert | JSON | GET | 下载访问凭证 |

##### 请求头参数说明

| Header参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| Content-Type | true | String | application/json |
| X-Auth-Token | true | String | 用户信息：<br>gAAAAABhfzIyEuOt9Ateg67J4u_hZ4IupK75n2ODuNBnoXzb4HV7BGOtLSKUTuaNa8ekXAOALvJvp-a9dgf1TfK8Bi3-UtlHEDATmR3_FpQTYkpJCBMg7lKUhxW56XS1WyuSGK-kx9RiyXqoJ5Ao-jHc2Cp9Jynz6WnXbNrO4bSa60GrwyiyPP4 |

##### 正常返回结果

| 正常返回二进制文件 |
| --- |
