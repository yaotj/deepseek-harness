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
| X-Auth-Token | true | String | 调用云平台各API时，请求消息头中需含有的经过认证的令牌。可通过6.30描述的api获取令牌。 |

### 2.2请求错误返回响应体

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 错误码 |
| msg | String | 错误信息 | 错误信息 |
| data | Object | null | 返回空 |
| total | Integer | 0 | 返回0个 |
| 例如：<br>{<br>"code": " ",<br>"msg": "",<br>"data": null,<br>"total": 0<br>\} |  |  |  |

## 系统备份

### 3.1立即备份

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/backup-recovery/api/backup_now | JSON | GET | 立即备份 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 |  |
| data | Object | 具体返回结果体 | 返回备份文件文件名 |
| 例如：<br>{<br>"code":"x",<br>"msg":"xxx",<br>"data":”xxx” #此次生成的备份文件名<br>\} |  |  |  |

### 3.2操作备份文件

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/backup-recovery/api/backup_file | JSON | POST | 操作备份文件 |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| data | true | Json | 备份文件信息 |
| data请求体 |  |  |  |
| filename | true | Array | 备份文件名 |
| action | true | String | 操作类型，目前支持download、delete、list。<br>download：下载备份文件<br>delete：删除备份文件<br>list：获取备份文件列表。获取备份文件列表时可不指定filename。 |

例如：

```json
{
"data":
{
"filename":["x", "y",…,"z"],
"action":"x" #目前支持 download delete list
}
}
```

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回操作结果信息 |
| 例如：<br>返回备份文件列表<br>{<br>"msg": "x",<br>"code": "x",<br>"data": [<br>{<br>"finish_time": "x", #可直接展示<br>"size": "8.0M", #可直接展示<br>"filename": "x" #可直接展示<br>}<br>]<br>}<br>下载备份文件<br>以流形式传输文件。支持批量打包下载。<br>删除备份文件<br>{<br>"msg": "x",<br>"code": "x"<br>}<br>支持批量删除。 |  |  |  |

### 3.3远程备份服务器校验

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/backup-recovery/api/remote_check | JSON | POST | 校验远端备份策略的服务器配置 |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| data | true | Json | 远程备份服务器信息 |
| data请求体 |  |  |  |
| protocol | true | String | 远程备份协议，支持FTP、OSS。 |
| ssl | false | String | OSS远端备份加密协议 |
| ip | true | String | 远程备份服务器IP |
| port | true | Integer | 远程备份服务器端口，FTP默认21，OSS默认8082。 |
| user | true | String | 远程备份服务器用户名 |
| password | true | String | 远程备份服务器密码，须经过base64加密。 |
| 例如：<br>{<br>"data":{<br>"protocol":"x", #可选ftp oss<br>"ssl":"x", #可选True False<br>"ip":"x",<br>"port":21,<br>"user": "x",<br>"password": "x" #须经过base64加密<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回校验结果信息 |
| 例如：<br>校验错误时返回<br>{<br>"code":" sysbackup.1",<br>"msg":" FTP check failed."<br>\} |  |  |  |

### 3.4获取备份策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/backup-recovery/api/backup_policy | JSON | GET | 获取备份策略 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Object | 具体返回结果体 | 返回备份策略信息 |
| data返回值 |  |  |  |
| type | String | 备份策略类型 | 分为本地备份和远程备份 |
| max-copies | String | 备份文件最大保留数 | 仅用于本地备份 |
| frequency | String | 备份频率 |  |
| time | String | 备份时间 |  |
| switch | String | 备份策略开关 | 为“on”时表示配置有备份策略 |
| protocol | String | 远程备份协议，支持FTP、OSS。 | 仅用于远程备份 |
| ip | String | 远程备份服务器IP | 仅用于远程备份 |
| port | Integer | 远程备份服务器端口，FTP默认21,OSS默认8082。 | 仅用于远程备份 |
| user | String | 远程备份服务器用户名 | 仅用于远程备份 |
| password | String | 远程备份服务器密码，经过base64加密。 | 仅用于远程备份 |
| dir | String | 远端备份目录 | 仅用于远程备份 |
| ssl | String | OSS远端备份加密协议 | 仅用于远程备份 |
| 例如：<br>未设置备份策略时<br>{<br>"code":"",<br>"msg":"",<br>"data":" {"switch":"off"}"<br>}<br>设置了本地或远程备份策略<br>{<br>"code":"",<br>"msg":"",<br>"data":{<br>"type":"remote", #分为remote和local，对应远程或本地策略。<br>"protocol":"x",<br>"ip":"x",<br>"port":21,<br>"dir":x,#ftp协议时为ftp目录，oss协议时为桶名称<br>"ssl":x,#oss协议的加密协议，True或False<br>"user": "x",<br>"password": "x",<br>"frequency":"x",<br>"time":"hh:mm",<br>"switch":"on"<br>}<br>\} |  |  |  |

### 3.5设置备份策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/backup-recovery/api/backup_policy | JSON | POST | 设置备份策略 |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| data | true | Json | 远程备份服务器信息 |
| data请求体 |  |  |  |
| protocol | false | String | 远程备份协议，支持FTP、OSS。 |
| ip | false | String | 远程备份服务器IP |
| port | false | Integer | 远程备份服务器端口，FTP默认21，OSS默认8082。 |
| user | true | String | 远程备份服务器用户名 |
| password | false | String | 远程备份服务器密码，须经过base64加密。 |
| type | true | String | 备份策略类型 |
| max-copies | true | String | 备份文件最大保留数 |
| frequency | true | String | 备份频率 |
| time | true | String | 备份时间 |
| switch | true | String | 备份策略开关 |
| dir | false | String | 远端备份目录 |
| ssl | false | String | OSS远端备份加密协议 |
| 例如：<br>设置本地备份策略<br>{<br>"data":{<br>"type":"local",  #固定<br>"max-copies":x, #int类型<br>"user":"", 本地备份时为空值<br>"frequency":"x", #可选one-time daily weekly monthly<br>"time":"", #格式 hh:mm<br>"switch":"x" #定时备份开关，可选 on off<br>}<br>}<br>设置远程备份策略<br>{<br>"data":{<br>"type":"remote", #固定<br>"protocol":"ftp", #目前支持ftp、oss<br>"ip":"x”,<br>"port":x, #int类型<br>"dir":x,#ftp协议时为ftp目录，oss协议时为桶名称<br>"ssl":x,#oss协议的加密协议，可选True False<br>"user": "x”,<br>"password": "x", #需base64加密<br>"frequency":"x", #可选one-time daily weekly monthly<br>"time":"", #格式 hh:mm<br>"switch":"x" #定时备份开关，可选 on off<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |

## syslog远程地址配置

### 4.1获取syslog远程地址

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/syslog/api/remoteips | JSON | GET | 获取syslog远程地址 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Array | syslog远程地址 |  |
| 例如：<br>{<br>"data": ["x.x.x.x","x.x.x.x"],<br>"code":"",<br>"msg":""<br>\} |  |  |  |

### 4.2设置syslog远程地址

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/syslog/api/remoteips | JSON | PUT | 设置syslog远程地址 |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| data | true | Array | syslog远程地址 |
| 例如：<br>{"data": ["x.x.x.x","x.x.x.x"]\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 | 正常成功情况下，code为空串 |
| msg | String | 错误信息 | 正常成功情况下，msg为空串 |
| data | Array | syslog远程地址 |  |
| 例如：<br>{<br>"data": ["x.x.x.x","x.x.x.x"],<br>"code":"",<br>"msg":""<br>\} |  |  |  |

## 告警中心

### 5.1获取模板列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/resource | JSON | GET | 获取模板列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| id | Integer | 资源ID |  |
| zh | String | 中文描述 |  |
| en | String | 英文描述 |  |
| expr | String | 模板表达式 | 见 prometheus-operator 设计文档 |
| dateExpr | String | 数据表达式 | 见 prometheus-operator 设计文档 |
| kind | String | 资源类型，阈值表达式，事件表达式 |  |
| desc | String | 原样设置alert 的annotations.message |  |
| params | Object | 用于替换expr中参数，条件输入 |  |
| params返回值 |  |  |  |
| name | String | 名称 |  |
| cmp | String | 操作符 |  |
| value | String | 值 |  |
| desc | String | 表达式说明 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"id": 0,<br>"zh": "string",<br>"en": "string",<br>"expr": "string",<br>"dateExpr": "string",<br>"kind": "string",<br>"desc": "string",<br>"params": [<br>{<br>"name": "string",<br>"cmp": "string",<br>"value": "string",<br>"desc": "string"<br>}<br>]<br>}<br>]<br>\} |  |  |  |

### 5.2查询用户标签

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/resource/template/userlabel | JSON | GET | 查询用户标签 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| additionalProp1 | String | 用户标签 |  |
| additionalProp2 | String | 用户标签 |  |
| additionalProp3 | String | 用户标签 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>\} |  |  |  |

### 5.3删除用户标签

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/resource/template/userlabel | JSON | DELETE | 删除用户标签 |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| additionalProp1 | true | String | 用户标签 |
| additionalProp2 | true | String | 用户标签 |
| additionalProp3 | true | String | 用户标签 |
| 例如：<br>{<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object | 模板列表 |  |
| data返回值 |  |  |  |
| additionalProp1 | String | 用户标签 |  |
| additionalProp2 | String | 用户标签 |  |
| additionalProp3 | String | 用户标签 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>\} |  |  |  |

### 5.4查询模板时序数据(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/resource/template/data | JSON | POST | 查询模板时序数据 |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| querytime | false | String | 时间范围 |
| res | false | Object |  |
| res请求体 |  |  |  |
| id | false | Integer | 资源ID |
| zh | false | String | 中文描述 |
| en | false | String | 英文描述 |
| expr | false | String | 模板表达式，见 prometheus-operator 设计文档 |
| dateExpr | false | String | 数据表达式，见 prometheus-operator 设计文档 |
| kind | false | String | 资源类型，阈值表达式，事件表达式 |
| desc | false | String | 原样设置alert 的annotations.message |
| params | false | Object | 用于替换expr中参数，条件输入 |
| params返回值 |  |  |  |
| name | false | String | 名称 |
| cmp | false | String | 操作符 |
| value | false | String | 值 |
| desc | false | String | 表达式说明 |
| 例如：<br>{<br>"res": {<br>"id": 0,<br>"zh": "string",<br>"en": "string",<br>"expr": "string",<br>"dateExpr": "string",<br>"kind": "string",<br>"desc": "string",<br>"params": [<br>{<br>"name": "string",<br>"cmp": "string",<br>"value": "string",<br>"desc": "string"<br>}<br>]<br>},<br>"querytime": "string"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | String | 模板列表 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": "string"<br>\} |  |  |  |

### 5.5获取模板告警规则列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/resource/template/confalert | JSON | GET | 获取模板告警规则列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| id | Integer | 资源ID |  |
| zh | String | 中文描述 |  |
| en | String | 英文描述 |  |
| expr | String | 模板表达式 | 见 prometheus-operator 设计文档 |
| dateExpr | String | 数据表达式 | 见 prometheus-operator 设计文档 |
| kind | String | 资源类型，阈值表达式，事件表达式 |  |
| desc | String | 原样设置alert 的annotations.message |  |
| params | Object | 用于替换expr中参数，条件输入 |  |
| for | String |  |  |
| cmp | String | 操作符 | 值可以是其中之一 [">","<","!=","==","<=",">="] |
| threshold | String | 阈值设定 |  |
| level | String | 告警等级 |  |
| userlabel | Object | 告警用户标签 |  |
| params返回值 |  |  |  |
| name | String | 名称 |  |
| cmp | String | 操作符 |  |
| value | String | 值 |  |
| desc | String | 表达式说明 |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | String | 用户标签 |  |
| additionalProp2 | String | 用户标签 |  |
| additionalProp3 | String | 用户标签 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"res": {<br>"id": 0,<br>"zh": "string",<br>"en": "string",<br>"expr": "string",<br>"dateExpr": "string",<br>"kind": "string",<br>"desc": "string",<br>"params": [<br>{<br>"name": "string",<br>"cmp": "string",<br>"value": "string",<br>"desc": "string"<br>}<br>]<br>},<br>"for": "string",<br>"cmp": "string",<br>"threshold": "string",<br>"level": "string",<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>}<br>]<br>\} |  |  |  |

### 5.6删除告警规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/resource/template/confalert | JSON | DELETE | 删除告警规则 |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 名称 |
| res | flase | Object |  |
| res请求体 |  |  |  |
| id | flase | Integer | 资源ID |
| zh | flase | String | 中文描述 |
| en | flase | String | 英文描述 |
| expr | flase | String | 模板表达式，见 prometheus-operator 设计文档 |
| dateExpr | flase | String | 数据表达式，见 prometheus-operator 设计文档 |
| kind | flase | String | 资源类型，阈值表达式，事件表达式 |
| desc | flase | String | 原样设置alert 的annotations.message |
| params | flase | Object | 用于替换expr中参数，条件输入 |
| for | flase | String |  |
| cmp | flase | String | 操作符，值可以是其中之一 [">","<","!=","==","<=",">="] |
| threshold | flase | String | 阈值设定 |
| level | flase | String | 告警等级 |
| userlabel | flase | Object | 告警用户标签 |
| params返回值 |  |  |  |
| name | flase | String | 名称 |
| cmp | flase | String | 操作符 |
| value | flase | String | 值 |
| desc | flase | String | 表达式说明 |
| userlabel返回值 |  |  |  |
| additionalProp1 | flase | String | 用户标签 |
| additionalProp2 | flase | String | 用户标签 |
| additionalProp3 | flase | String | 用户标签 |
| 例如：<br>{<br>"name": "string",<br>"res": {<br>"id": 0,<br>"zh": "string",<br>"en": "string",<br>"expr": "string",<br>"dateExpr": "string",<br>"kind": "string",<br>"desc": "string",<br>"params": [<br>{<br>"name": "string",<br>"cmp": "string",<br>"value": "string",<br>"desc": "string"<br>}<br>]<br>},<br>"for": "string",<br>"cmp": "string",<br>"threshold": "string",<br>"level": "string",<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 名称 |  |
| res | Object |  |  |
| res返回值 |  |  |  |
| id | Integer | 资源ID |  |
| zh | String | 中文描述 |  |
| en | String | 英文描述 |  |
| expr | String | 模板表达式 | 见 prometheus-operator 设计文档 |
| dateExpr | String | 数据表达式 | 见 prometheus-operator 设计文档 |
| kind | String | 资源类型，阈值表达式，事件表达式 |  |
| desc | String | 原样设置alert 的annotations.message |  |
| params | Object | 用于替换expr中参数，条件输入 |  |
| for |  |  |  |
| cmp |  | 操作符 | 值可以是其中之一 [">","<","!=","==","<=",">="] |
| threshold |  | 阈值设定 |  |
| level |  | 告警等级 |  |
| userlabel | Object | 告警用户标签 |  |
| params返回值 |  |  |  |
| name | String | 名称 |  |
| cmp | String | 操作符 |  |
| value | String | 值 |  |
| desc | String | 表达式说明 |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | String | 用户标签 |  |
| additionalProp2 | String | 用户标签 |  |
| additionalProp3 | String | 用户标签 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"res": {<br>"id": 0,<br>"zh": "string",<br>"en": "string",<br>"expr": "string",<br>"dateExpr": "string",<br>"kind": "string",<br>"desc": "string",<br>"params": [<br>{<br>"name": "string",<br>"cmp": "string",<br>"value": "string",<br>"desc": "string"<br>}<br>]<br>},<br>"for": "string",<br>"cmp": "string",<br>"threshold": "string",<br>"level": "string",<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>}<br>]<br>\} |  |  |  |

### 5.7通过模板设置告警规则

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/resource/template/confalert | JSON | POST | 通过模板设置告警规则 |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 名称 |
| res | true | Object |  |
| res请求体 |  |  |  |
| id | true | Integer | 资源ID |
| zh | true | String | 中文描述 |
| en | true | String | 英文描述 |
| expr | true | String | 模板表达式，见 prometheus-operator 设计文档 |
| dateExpr | true | String | 数据表达式，见 prometheus-operator 设计文档 |
| kind | true | String | 资源类型，阈值表达式，事件表达式 |
| desc | true | String | 原样设置alert 的annotations.message |
| params | true | Object | 用于替换expr中参数，条件输入 |
| for | true | String |  |
| cmp | true | String | 操作符，值可以是其中之一 [">","<","!=","==","<=",">="] |
| threshold | true | String | 阈值设定 |
| level | true | String | 告警等级 |
| userlabel | true | Object | 告警用户标签 |
| params返回值 |  |  |  |
| name | true | String | 名称 |
| cmp | true | String | 操作符 |
| value | true | String | 值 |
| desc | true | String | 表达式说明 |
| userlabel返回值 |  |  |  |
| additionalProp1 | true | String | 用户标签 |
| additionalProp2 | true | String | 用户标签 |
| additionalProp3 | true | String | 用户标签 |
| 例如：<br>{<br>"name": "string",<br>"res": {<br>"id": 0,<br>"zh": "string",<br>"en": "string",<br>"expr": "string",<br>"dateExpr": "string",<br>"kind": "string",<br>"desc": "string",<br>"params": [<br>{<br>"name": "string",<br>"cmp": "string",<br>"value": "string",<br>"desc": "string"<br>}<br>]<br>},<br>"for": "string",<br>"cmp": "string",<br>"threshold": "string",<br>"level": "string",<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 名称 |  |
| res | Object |  |  |
| res返回值 |  |  |  |
| id | Integer | 资源ID |  |
| zh | String | 中文描述 |  |
| en | String | 英文描述 |  |
| expr | String | 模板表达式 | 见 prometheus-operator 设计文档 |
| dateExpr | String | 数据表达式 | 见 prometheus-operator 设计文档 |
| kind | String | 资源类型，阈值表达式，事件表达式 |  |
| desc | String | 原样设置alert 的annotations.message |  |
| params | Object | 用于替换expr中参数，条件输入 |  |
| for | String |  |  |
| cmp | String | 操作符 | 值可以是其中之一 [">","<","!=","==","<=",">="] |
| threshold | String | 阈值设定 |  |
| level | String | 告警等级 |  |
| userlabel | Object | 告警用户标签 |  |
| params返回值 |  |  |  |
| name | String | 名称 |  |
| cmp | String | 操作符 |  |
| value | String | 值 |  |
| desc | String | 表达式说明 |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | String | 用户标签 |  |
| additionalProp2 | String | 用户标签 |  |
| additionalProp3 | String | 用户标签 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"res": {<br>"id": 0,<br>"zh": "string",<br>"en": "string",<br>"expr": "string",<br>"dateExpr": "string",<br>"kind": "string",<br>"desc": "string",<br>"params": [<br>{<br>"name": "string",<br>"cmp": "string",<br>"value": "string",<br>"desc": "string"<br>}<br>]<br>},<br>"for": "string",<br>"cmp": "string",<br>"threshold": "string",<br>"level": "string",<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>}<br>]<br>\} |  |  |  |

### 5.8获取支持的抓取类型(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/exporter/crawl | JSON | GET | 获取支持的抓取类型 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| group | String | 组 |  |
| name | String | 名称 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"group": "string",<br>"name": "string"<br>}<br>]<br>\} |  |  |  |

### 5.9获取已设置的抓取类型

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/exporter/crawl/vcenter | JSON | GET | 获取已设置的抓取类型 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 名称 |  |
| hostip | String | 主机IP |  |
| user | String | 用户名 |  |
| password | String | 密码 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"hostip": "string",<br>"user": "string",<br>"password": "string"<br>}<br>]<br>\} |  |  |  |

### 5.10删除抓取类型

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/exporter/crawl/vcenter | JSON | DELETE | 删除抓取类型 |

###### 请求体(parameter)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| conf | true | String | 来自GET请求获取的name字段 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 名称 |  |
| hostip | String | 主机IP |  |
| user | String | 用户名 |  |
| password | String | 密码 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"hostip": "string",<br>"user": "string",<br>"password": "string"<br>}<br>]<br>\} |  |  |  |

### 5.11设置抓取类型

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/exporter/crawl/vcenter | JSON | POST | 设置抓取类型 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 名称 |
| hostip | true | String | 主机IP |
| user | true | String | 用户名 |
| password | true | String | 密码 |
| 例如：<br>{<br>"name": "string",<br>"hostip": "string",<br>"user": "string",<br>"password": "string"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 名称 |  |
| hostip | String | 主机IP |  |
| user | String | 用户名 |  |
| password | String | 密码 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"hostip": "string",<br>"user": "string",<br>"password": "string"<br>}<br>]<br>\} |  |  |  |

### 5.12更新抓取类型

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/exporter/crawl/vcenter | JSON | PUT | 更新抓取类型 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 名称 |
| hostip | true | String | 主机IP |
| user | true | String | 用户名 |
| password | true | String | 密码 |
| 例如：<br>{<br>"name": "string",<br>"hostip": "string",<br>"user": "string",<br>"password": "string"<br>\} |  |  |  |

###### 请求体(parameter)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 来自GET请求获取的name字段 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 名称 |  |
| hostip | String | 主机IP |  |
| user | String | 用户名 |  |
| password | String | 密码 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"hostip": "string",<br>"user": "string",<br>"password": "string"<br>}<br>]<br>\} |  |  |  |

### 5.13获取告警列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert | JSON | GET | 获取告警列表 |

###### 请求体(parameter)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| page | false | Integer | 页数，从1计数 |
| pagesize | false | Integer | 页数总数 |
| filetekeys | false | String | 过滤告警用户标签 keys，逗号分隔 |
| filetevalues | false | String | 过滤告警用户标签 values，逗号分隔 |
| status | false | String | 过滤告警状态，默认是所有状态 |
| name | false | String | 过滤告警名字，默认是所有名字 |
| level | false | String | 过滤告警级别，默认是所有级别 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| total | Integer | 告警总数 |  |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 告警名称 |  |
| from | String | 告警来源 |  |
| state | String | 告警状态 | active（未恢复），inactive（已恢复），ensure（已确认） |
| level | String | 告警级别 |  |
| startAt | String | 告警开始时间 |  |
| endAt | String | 告警恢复时间 |  |
| attr | Object |  |  |
| userlabel | Object |  |  |
| msg | String | 告警信息 |  |
| attr返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"total": 0,<br>"data": [<br>{<br>"name": "string",<br>"from": "string",<br>"state": "active",<br>"level": "string",<br>"startAt": "string",<br>"endAt": "string",<br>"attr": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"msg": "string"<br>}<br>]<br>\} |  |  |  |

### 5.14确认告警(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert | JSON | POST | 确认告警 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 告警名称 |
| from | true | String | 告警来源 |
| state | true | String | 告警状态 |
| level | true | String | 告警级别 |
| startAt | true | String | 告警开始时间 |
| endAt | true | String | 告警恢复时间 |
| attr | true | Object |  |
| userlabel | true | Object |  |
| msg | true | String | 告警信息 |
| attr返回值 |  |  |  |
| additionalProp1 | true | String |  |
| additionalProp2 | true | String |  |
| additionalProp3 | true | String |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | true | String |  |
| additionalProp2 | true | String |  |
| additionalProp3 | true | String |  |
| 例如：<br>{<br>"name": "string",<br>"from": "string",<br>"state": "active",<br>"level": "string",<br>"startAt": "string",<br>"endAt": "string",<br>"attr": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"msg": "string"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| total | Integer | 告警总数 |  |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 告警名称 |  |
| from | String | 告警来源 |  |
| state | String | 告警状态 | active（未恢复），inactive（已恢复），ensure（已确认） |
| level | String | 告警级别 |  |
| startAt | String | 告警开始时间 |  |
| endAt | String | 告警恢复时间 |  |
| attr | Object |  |  |
| userlabel | Object |  |  |
| msg | String | 告警信息 |  |
| attr返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"total": 0,<br>"data": [<br>{<br>"name": "string",<br>"from": "string",<br>"state": "active",<br>"level": "string",<br>"startAt": "string",<br>"endAt": "string",<br>"attr": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"msg": "string"<br>}<br>]<br>\} |  |  |  |

### 5.15删除告警(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert | JSON | DELETE | 删除告警 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 告警名称 |
| from | true | String | 告警来源 |
| state | true | String | 告警状态 |
| level | true | String | 告警级别 |
| startAt | true | String | 告警开始时间 |
| endAt | true | String | 告警恢复时间 |
| attr | true | Object |  |
| userlabel | true | Object |  |
| msg | true | String | 告警信息 |
| attr返回值 |  |  |  |
| additionalProp1 | true | String |  |
| additionalProp2 | true | String |  |
| additionalProp3 | true | String |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | true | String |  |
| additionalProp2 | true | String |  |
| additionalProp3 | true | String |  |
| 例如：<br>[<br>{<br>"name": "string",<br>"from": "string",<br>"state": "active",<br>"level": "string",<br>"startAt": "string",<br>"endAt": "string",<br>"attr": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"msg": "string"<br>}<br>] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | String |  |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": "string"<br>\} |  |  |  |

### 5.16查询新告警(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert/havenew | JSON | GET | 查询新告警 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| total | Integer | 告警总数 |  |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 告警名称 |  |
| from | String | 告警来源 |  |
| state | String | 告警状态 | active（未恢复），inactive（已恢复），ensure（已确认） |
| level | String | 告警级别 |  |
| startAt | String | 告警开始时间 |  |
| endAt | String | 告警恢复时间 |  |
| attr | Object |  |  |
| userlabel | Object |  |  |
| msg | String | 告警信息 |  |
| attr返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"total": 0,<br>"data": [<br>{<br>"name": "string",<br>"from": "string",<br>"state": "active",<br>"level": "string",<br>"startAt": "string",<br>"endAt": "string",<br>"attr": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"msg": "string"<br>}<br>]<br>\} |  |  |  |

### 5.17alertmanger webhook实现(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert/webhook | JSON | POST | alertmanger webhook实现 |

###### 请求体(parameter)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| data | true | String | 数据，具体类型查看alertmanager notify impl |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| total | Integer | 告警总数 |  |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 告警名称 |  |
| from | String | 告警来源 |  |
| state | String | 告警状态 | active（未恢复），inactive（已恢复），ensure（已确认） |
| level | String | 告警级别 |  |
| startAt | String | 告警开始时间 |  |
| endAt | String | 告警恢复时间 |  |
| attr | Object |  |  |
| userlabel | Object |  |  |
| msg | String | 告警信息 |  |
| attr返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"total": 0,<br>"data": [<br>{<br>"name": "string",<br>"from": "string",<br>"state": "active",<br>"level": "string",<br>"startAt": "string",<br>"endAt": "string",<br>"attr": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>},<br>"msg": "string"<br>}<br>]<br>\} |  |  |  |

### 5.18获取告警配置(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert/confalert | JSON | GET | 获取告警配置 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 名称 |  |
| res | Object |  |  |
| res返回值 |  |  |  |
| id | Integer | 资源ID |  |
| zh | String | 中文描述 |  |
| en | String | 英文描述 |  |
| expr | String | 模板表达式 | 见 prometheus-operator 设计文档 |
| dateExpr | String | 数据表达式 | 见 prometheus-operator 设计文档 |
| kind | String | 资源类型，阈值表达式，事件表达式 |  |
| desc | String | 原样设置alert 的annotations.message |  |
| params | Object | 用于替换expr中参数，条件输入 |  |
| for | String |  |  |
| cmp | String | 操作符 | 值可以是其中之一 [">","<","!=","==","<=",">="] |
| threshold | String | 阈值设定 |  |
| level | String | 告警等级 |  |
| userlabel | Object | 告警用户标签 |  |
| params返回值 |  |  |  |
| name | String | 名称 |  |
| cmp | String | 操作符 |  |
| value | String | 值 |  |
| desc | String | 表达式说明 |  |
| userlabel返回值 |  |  |  |
| additionalProp1 | String | 用户标签 |  |
| additionalProp2 | String | 用户标签 |  |
| additionalProp3 | String | 用户标签 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"res": {<br>"id": 0,<br>"zh": "string",<br>"en": "string",<br>"expr": "string",<br>"dateExpr": "string",<br>"kind": "string",<br>"desc": "string",<br>"params": [<br>{<br>"name": "string",<br>"cmp": "string",<br>"value": "string",<br>"desc": "string"<br>}<br>]<br>},<br>"for": "string",<br>"cmp": "string",<br>"threshold": "string",<br>"level": "string",<br>"userlabel": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>}<br>]<br>\} |  |  |  |

### 5.19获取指定名字的receiver信息(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert/receiver | JSON | GET | 获取指定名字的receiver信息 |

###### 请求体(parameter)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 获取指定rcv，若没有则返回全部 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 通知名字 |  |
| to | String | 通知的邮件名，逗号分隔 |  |
| labels | Object | 过滤标签，只有符合该labels的告警才会调用 |  |
| labels返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"to": "string",<br>"labels": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>}<br>]<br>\} |  |  |  |

### 5.20删除receiver信息(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert/receiver | JSON | DELETE | 删除receiver信息 |

###### 请求体(parameter)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 删除指定rcv |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 通知名字 |  |
| to | String | 通知的邮件名，逗号分隔 |  |
| labels | Object | 过滤标签，只有符合该labels的告警才会调用 |  |
| labels返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"to": "string",<br>"labels": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>}<br>]<br>\} |  |  |  |

### 5.21创建receiver信息(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert/receiver | JSON | POST | 创建receiver信息 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | true | String | 通知名字 |
| to | true | String | 通知的邮件名，逗号分隔 |
| labels | true | Object | 过滤标签，只有符合该labels的告警才会调用 |
| labels返回值 |  |  |  |
| additionalProp1 | true | String |  |
| additionalProp2 | true | String |  |
| additionalProp3 | true | String |  |
| 例如：<br>{<br>"name": "string",<br>"to": "string",<br>"labels": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| name | String | 通知名字 |  |
| to | String | 通知的邮件名，逗号分隔 |  |
| labels | Object | 过滤标签，只有符合该labels的告警才会调用 |  |
| labels返回值 |  |  |  |
| additionalProp1 | String |  |  |
| additionalProp2 | String |  |  |
| additionalProp3 | String |  |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"name": "string",<br>"to": "string",<br>"labels": {<br>"additionalProp1": "string",<br>"additionalProp2": "string",<br>"additionalProp3": "string"<br>}<br>}<br>]<br>\} |  |  |  |

### 5.22获取Email服务器信息(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert/notify/email | JSON | GET | 获取Email服务器信息 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| addr | String | SMTP服务器地址 |  |
| tls | Boolean | 是否启用tls |  |
| user | String | 邮件服务器的用户 |  |
| from | String | 发送的用户 |  |
| password | String | 登录邮件服务器的密码 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"addr": "127.0.0.1:465",<br>"tls": false,<br>"user": null,<br>"from": null,<br>"password": null<br>}<br>]<br>\} |  |  |  |

### 5.23创建Email服务器信息(已废弃)

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/alert/notify/email | JSON | POST | 创建Email服务器信息 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| addr | true | String | SMTP服务器地址 |
| tls | true | Boolean | 是否启用tls |
| user | true | String | 邮件服务器的用户 |
| from | true | String | 发送的用户 |
| password | true | String | 登录邮件服务器的密码 |
| 例如：<br>{<br>"addr": "127.0.0.1:465",<br>"tls": false,<br>"user": null,<br>"from": null,<br>"password": null<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| code | String | 错误码 |  |
| status | Boolean | 操作结果 | true为成功，false为失败 |
| data | Object |  |  |
| data返回值 |  |  |  |
| addr | String | SMTP服务器地址 |  |
| tls | Boolean | 是否启用tls |  |
| user | String | 邮件服务器的用户 |  |
| from | String | 发送的用户 |  |
| password | String | 登录邮件服务器的密码 |  |
| 例如：<br>{<br>"code": "string",<br>"status": true,<br>"data": [<br>{<br>"addr": "127.0.0.1:465",<br>"tls": false,<br>"user": null,<br>"from": null,<br>"password": null<br>}<br>]<br>\} |  |  |  |

### 5.24健康检查

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/alertmgt/api/v1/health | JSON | GET | 健康检查 |

###### 正常返回结果

健康时HTTP状态码为200，不健康时HTTP状态码为400。

## 用户组织角色

外部调用：请求形如：http://{ ip address}:{port}/{URI}，各部分的含义如下：

- {ip address}：部署云平台的指定的IP地址。
- {port}：部署云平台的指定的Web端口地址，缺省为11000。
- {URI}：API对应的URI。
- 用户接口API内部调用：
- 可以直接使用keystoneAPI，
- 形式如 http://os-keystone-svc:5000/xxx/xxx，
- 相关文档不在此列举，可以参考：https://docs.openstack.org/api-ref/identity/v3/
- 可以内部调用  http://os-svcmgt-svc: 11021/xxx/xxx
- 相关api仍可参考如下api详细描述，/sys/oapi作为转发baseurl 在此场景不用携带。

### 6.1获取角色列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/roles | JSON | GET | 获取角色列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | String | 角色id |  |
| name | String | 角色name |  |
| scope | String | 角色scope | project或system或user |
| description | String | 描述 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": [{id: "041b67f3dc5342c98292a3b82dc4e229", name: "role-38683", scope: "project", description: ""}],<br>"status": true<br>\} |  |  |  |

### 6.2获取角色详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/roles/{id\} | JSON | GET | 获取角色详情 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | String | 角色id |  |
| name | String | 角色name |  |
| scope | String | 角色scope | project或system或user |
| description | String | 描述 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"role": {<br>"id": "023dc10ef230459e903c7ff81c6fcc81",<br>"name": "tian-org",<br>"internal": false,<br>"scope": "project",<br>"description": "没有权限"<br>}<br>},<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 6.3组织管理员获取角色列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/project/roles | JSON | GET | 组织管理员获取角色列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | String | 角色id |  |
| name | String | 角色name |  |
| scope | String | 角色scope | project或system或user |
| description | String | 描述 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": [{id: "041b67f3dc5342c98292a3b82dc4e229", name: "role-38683", scope: "project", description: ""}],<br>"status": true<br>\} |  |  |  |

### 6.4新建组织

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/projects | JSON | POST | 新建组织 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| parent_id | 是 | String | 已有的组织id | 父组织id |
| name | 是 | String | 1-64个字符 | 组织名称 |
| description | 否 | String | 0-128个字符 | 组织描述 |
| 例如：<br>{<br>"project": {<br>"description": "string",<br>"parent_id": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "string"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 状态 | true为正常，false为异常 |
| id | String | 组织id |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"project": {<br>"parent_id": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "string1",<br>"description": "",<br>"id": "ac0dcb1ce4bf474f8c0d999adcf28c13"<br>}<br>},<br>"msg": null,<br>"operRes": "string1"<br>\} |  |  |  |

### 6.5删除组织

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/deleteProject/{projectId\} | JSON | DELETE | 删除组织 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| resources | List | 包含的资源 | 存在资源无法删除组织 |
| exceptionServices | List | 异常的服务 | 存在异常服务无法删除组织 |
| status | Boolean | 状态 | 是否可以删除 |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {<br>"resources": [{<br>"type": "网络",<br>"type_en": "Network",<br>"dataList": null<br>}],<br>"exceptionServices": [],<br>"status": false,<br>"id": null<br>},<br>"status": false<br>\} |  |  |  |

### 6.6根据组织ID获取组织

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/getProjectById/{projectId\} | JSON | GET | 根据组织ID获取组织 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | String | 组织id |  |
| name | String | 组织名称 |  |
| parent_id | String | 父组织id |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"project": {<br>"id": "9052ae85792143ff955c35c52e8e41bd",<br>"parent_id": "default" ,<br>"name": "root",<br>"description": "Bootstrap project for initializing the cloud."<br>}<br>},<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 6.7获取组织列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/getProjects | JSON | GET | 获取组织列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| children | List | 子组织信息 |  |
| id | String | 组织id |  |
| name | String | 组织名称 |  |
| parent_id | String | 父组织id |  |
| description | String | 描述 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": [<br>{<br>"children": [<br>{}<br>],<br>"id": "9052ae85792143ff955c35c52e8e41bd",<br>"parent_id": "default",<br>"name": "root",<br>"description": "Bootstrap project for initializing the cloud."<br>}<br>],<br>"status": false<br>\} |  |  |  |

### 6.8根据组织ID查询组织成员

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/getUsersByProjectId/{projectId\} | JSON | GET | 根据组织ID查询组织成员 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| default_project_id | string | 组织id |  |
| email | string | 用户邮箱 |  |
| enabled | Boolean | 用户启用状态 |  |
| id | string | 用户id |  |
| nickname | string | 用户昵称 |  |
| name | string | 用户name |  |
| project_name | string | 组织名称 |  |
| role_id | string | 角色id |  |
| role_name | string | 角色名称 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": [<br>{<br>"address": "string",<br>"default_project_id": "string",<br>"description": "string",<br>"domain_id": "string",<br>"email": "string",<br>"enabled": true,<br>"id": "string",<br>"name": "string",<br>"nickname": "string",<br>"phone": "string",<br>"project_name": "string",<br>"projects_and_roles": [<br>{<br>"project_id": "string",<br>"project_name": "string",<br>"role_id": "string",<br>"role_name": "string",<br>"role_type": "string"<br>}<br>],<br>"role_id": "string",<br>"role_name": "string"<br>}<br>],<br>"status": false<br>\} |  |  |  |

### 6.9修改授权

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/modifyUserRole | JSON | POST | 修改授权 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| projectId | 是 | string | 组织id | 组织id |
| userId | 是 | string | 用户id | 用户id |
| roleIdOld | 是 | string | 角色id | 老角色id |
| roleIdNew | 是 | string | 角色id | 新角色id |
| 例如：<br>{<br>"projectId":"string",<br>"userId":"string",<br>"roleIdOld":"string",<br>"roleIdNew":"string"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 | true成功，false失败 |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": false,<br>"status": false<br>\} |  |  |  |

### 6.10查询组织管理员

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/queryProjectManager/{projectId\} | JSON | GET | 查询组织管理员 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | List | 组织管理员名称列表 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": ["u1","orgtest"],<br>"status": false<br>\} |  |  |  |

### 6.11修改组织

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/updateProject/{projectId\} | JSON | PATCH | 修改组织 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| name | 是 | string | 1-64个字符 | 组织名称 |
| description | 否 | string | 0-128个字符 | 描述 |
| 例如：<br>{<br>"project": {<br>"description": "string",<br>"name": "string"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 状态 | 修改是否成功 |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {<br>"project": {<br>"domain_id": "default",<br>"code": null,<br>"is_domain": false,<br>"parent_id": "9052ae85792143ff955c35c52e8e41bd",<br>"extra": {<br>"code": null<br>},<br>"name": "liwei11",<br>"description": "",<br>"id": "0d442cdcba164789adc79f39cc20302b",<br>"enabled": true<br>}<br>},<br>"status": false<br>\} |  |  |  |

### 6.12查询组织管理员管理的组织和用户数量

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/projects/{projectId}/users | JSON | GET |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| project_count | int | 管理的组织数量 |  |
| user_count | int | 管理的用户数量 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {<br>"project_count": 17,<br>"user_count": 13<br>},<br>"status": false<br>\} |  |  |  |

### 6.13用户修改密码

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/user/changePassword/{userId\} | JSON | POST | 用户修改密码 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| oldPassword | 是 | string | 旧密码 | base64编码 |
| newPassword | 是 | string | 跟当前密码策略保持一致 | base64编码 |
| 例如：<br>{<br>"newPassword": "string",<br>"oldPassword": "string"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": false,<br>"status": false<br>\} |  |  |  |

### 6.14管理员修改密码

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/user/changePasswordByAdmin/{id\} | JSON | PATCH | 管理员修改密码 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| password | 是 | string | 符合密码策略 | 新密码，base64编码 |
| 例如：<br>{<br>"user": {<br>"password": "string"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": false,<br>"status": false<br>\} |  |  |  |

### 6.15检查用户是否可新建

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/user/check/{username\} | JSON | POST | 检查用户是否可新建 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 可否新建 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": false,<br>"status": false<br>\} |  |  |  |

### 6.16用户登录

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/double_factor/login | JSON | POST | 用户登录 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| username | 是 | string | 用户名 | 用户名 |
| password | 是 | string | 密码 | 密码base64加密 |
| domain | 是 | String | 域名称 | 本地用户为default，ldap用户为ldap域名称 |
| pictureCode | 否 | String | 图片验证码 | 配置了认证需要图片验证码后必传 |
| pictureCodeId | 否 | String | 图片验证码id | 配置了认证需要图片验证码后必传 |
| 例如：<br>{"username":"admin","password":"MTIyMw==","domain":"default","userId":""}<br>或<br>{"username":"admin","password":"MTIz","pictureCode":"123","pictureCodeId":"ba2acfd7-5eea-4c1b-a3ae-9d1605dfcb02","domain":"default","userId":""\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| default_project_id | string | 组织id |  |
| email | string | 用户邮箱 |  |
| enabled | Boolean | 用户启用状态 |  |
| id | string | 用户id |  |
| nickname | string | 用户昵称 |  |
| name | string | 用户name |  |
| project_name | string | 组织名称 |  |
| role_id | string | 角色id |  |
| role_name | string | 角色名称 |  |
| token | string | token |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"remindTime": 86361,<br>"display": false,<br>"isRemind": true,<br>"expireAt": "Fri Jul 03 03:33:49 CDT 2020",<br>"user": {<br>"id": "1df71957d770436493a84f24d9344853",<br>"enabled": true,<br>"description": "预置超级管理员",<br>"name": "admin",<br>"password": null,<br>"email": "h3c@h3c.com",<br>"phone": "123456789",<br>"address": "新华三云计算技术有限公司",<br>"nickname": "超级管理员",<br>"idnumber": "123456789",<br>"default_project_id": "78235b23a2c24b6aac2682cebc11b081",<br>"domain_id": "default",<br>"IDNumber": "123456789",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"projects_and_roles": [<br>{<br>"project_id": "78235b23a2c24b6aac2682cebc11b081",<br>"project_name": "root",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"role_type": null<br>}<br>],<br>"project_name": "root"<br>},<br>"token": "iH55dYBbBK6v5RK9XGrvC_hGhJLs51e7AglusBBV52k"<br>},<br>"msg": null<br>\} |  |  |  |

### 6.17用户登出

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/user/logout | JSON | DELETE | 用户登出 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": false,<br>"status": false<br>\} |  |  |  |

### 6.18增加用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users | JSON | POST | 增加用户 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| email | 是 | string | 邮箱格式 | 邮箱 |
| name | 是 | string | 可包含英文字母a-zA-Z，数字0-9以及特殊字符“.”，“-”，“_”，不能以特殊字符开头，长度1-32个字符 | 用户名 |
| password | 是 | string | 跟当前密码策略保持一致 | 密码 |
| nickname | 是 | string | 1-32字符 | 昵称 |
| role_id | 是 | string | 角色id | 角色id |
| default_project_id | 是 | string | 组织id | 组织id |
| enabled | 是 | boolean | true，false | 是否禁用 |
| domain_id | 是 | string | 固定default | default为本地用户 |
| 例如：<br>{<br>"user": {<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "yyttyytt1",<br>"nickname": "yyttyytt1",<br>"password": "cloudos123#",<br>"role_id": "19304d8d537c42c69aa19f5d9d41df76",<br>"email": "testkaas@h3c.com",<br>"domain_id": "default",<br>"enabled": true<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | string | 用户id |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {<br>"user": {<br>"IDNumber": "string",<br>"address": "string",<br>"default_project_id": "string",<br>"description": "string",<br>"domain_id": "string",<br>"email": "string",<br>"enabled": true,<br>"id": "string",<br>"idnumber": "string",<br>"name": "string",<br>"nickname": "string",<br>"phone": "string",<br>"project_name": "string",<br>"projects_and_roles": [<br>{<br>"project_id": "string",<br>"project_name": "string",<br>"role_id": "string",<br>"role_name": "string",<br>"role_type": "string"<br>}<br>],<br>"role_id": "string",<br>"role_name": "string"<br>}<br>},<br>"status": false<br>\} |  |  |  |

### 6.19获取用户列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users | JSON | GET | 获取用户列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| default_project_id | string | 组织id |  |
| email | string | 用户邮箱 |  |
| enabled | Boolean | 用户启用状态 |  |
| id | string | 用户id |  |
| nickname | string | 用户昵称 |  |
| name | string | 用户name |  |
| project_name | string | 组织名称 |  |
| role_id | string | 角色id |  |
| role_name | string | 角色名称 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {<br>"users": [<br>{<br>"IDNumber": "string",<br>"address": "string",<br>"default_project_id": "string",<br>"description": "string",<br>"domain_id": "string",<br>"email": "string",<br>"enabled": true,<br>"id": "string",<br>"idnumber": "string",<br>"name": "string",<br>"nickname": "string",<br>"phone": "string",<br>"project_name": "string",<br>"projects_and_roles": [<br>{<br>"project_id": "string",<br>"project_name": "string",<br>"role_id": "string",<br>"role_name": "string",<br>"role_type": "string"<br>}<br>],<br>"role_id": "string",<br>"role_name": "string"<br>}<br>]<br>},<br>"status": false<br>\} |  |  |  |

### 6.20根据ID列表获取用户列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users/ids | JSON | POST | 根据ID列表获取用户列表 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 用户id数组 | 是 | List | id列表 |
| 例如：<br>[<br>"1df71957d770436493a84f24d9344853"，<br>"9198fce99f8ed372c848baeff29654c07c80b13cbe250557611a189dd20b714f"<br>] |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| default_project_id | string | 组织id |  |
| email | string | 用户邮箱 |  |
| enabled | Boolean | 用户启用状态 |  |
| id | string | 用户id |  |
| nickname | string | 用户昵称 |  |
| name | string | 用户name |  |
| project_name | string | 组织名称 |  |
| role_id | string | 角色id |  |
| role_name | string | 角色名称 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": [<br>{<br>"id": "1df71957d770436493a84f24d9344853",<br>"enabled": true,<br>"description": "预置系统管理员",<br>"name": "admin",<br>"password": null,<br>"email": "h3c@h3c.com",<br>"phone": null,<br>"address": "新华三云计算技术有限公司",<br>"nickname": "系统管理员",<br>"idnumber": null,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"domain_id": "default",<br>"IDNumber": null,<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"projects_and_roles": [<br>{<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"project_name": "root",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"role_type": null<br>}<br>],<br>"project_name": "admin"<br>},<br>{<br>"id": "9198fce99f8ed372c848baeff29654c07c80b13cbe250557611a189dd20b714f",<br>"enabled": null,<br>"description": "测试",<br>"name": "jun3",<br>"password": null,<br>"email": "jun3@h3c.com",<br>"phone": null,<br>"address": null,<br>"nickname": "jun3",<br>"idnumber": null,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"domain_id": "72aa08e99256466fb15afb634e830513",<br>"IDNumber": null,<br>"role_id": "19304d8d537c42c69aa19f5d9d41df76",<br>"role_name": "user",<br>"projects_and_roles": null,<br>"project_name": "admin"<br>}<br>],<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 6.21获取用户详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users/{id\} | JSON | GET | 获取用户详情 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| default_project_id | string | 组织id |  |
| email | string | 用户邮箱 |  |
| enabled | Boolean | 用户启用状态 |  |
| id | string | 用户id |  |
| nickname | string | 用户昵称 |  |
| name | string | 用户name |  |
| project_name | string | 组织名称 |  |
| role_id | string | 角色id |  |
| role_name | string | 角色名称 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {<br>"user": {<br>"IDNumber": "string",<br>"address": "string",<br>"default_project_id": "string",<br>"description": "string",<br>"domain_id": "string",<br>"email": "string",<br>"enabled": true,<br>"id": "string",<br>"idnumber": "string",<br>"name": "string",<br>"nickname": "string",<br>"phone": "string",<br>"project_name": "string",<br>"projects_and_roles": [<br>{<br>"project_id": "string",<br>"project_name": "string",<br>"role_id": "string",<br>"role_name": "string",<br>"role_type": "string"<br>}<br>],<br>"role_id": "string",<br>"role_name": "string"<br>}<br>},<br>"status": false<br>\} |  |  |  |

### 6.22删除用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users/{id\} | JSON | DELETE | 删除用户 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| resources | List | 包含的资源 |  |
| exceptionServices | List | 异常的服务 |  |
| status | Boolean | 状态 | 是否删除 |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {"resources": [{<br>"type": "网络",<br>"type_en": "Network",<br>"dataList": null<br>}],<br>"exceptionServices": [],<br>"status": false,<br>"id": null<br>},<br>"status": false<br>\} |  |  |  |

### 6.23修改用户信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users/{id\} | JSON | PATCH | 修改用户信息 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| default_project_id | 是 | string | 组织id | 组织id |
| email | 是 | string | 邮箱格式 | 邮箱 |
| enabled | 是 | Boolean | 是否启用 | 是否启用 |
| id | 是 | string | id | 用户id |
| nickname | 是 | string | 1-32位 | 昵称 |
| name | 是 | string | 可包含英文字母a-zA-Z，数字0-9以及特殊字符“.”，“-”，“_”，“@”，不能以特殊字符开头，长度1-32个字符 | 用户名称 |
| project_name | 是 | string | 组织名称 | 组织名称 |
| role_id | 是 | string | 角色id | 角色id |
| 例如：<br>{<br>"user": {<br>"IDNumber": "string",<br>"address": "string",<br>"default_project_id": "string",<br>"description": "string",<br>"email": "string",<br>"enabled": true,<br>"idnumber": "string",<br>"nickname": "string",<br>"phone": "string",<br>"project_name": "string",<br>"projects_and_roles": [<br>{<br>"project_id": "string",<br>"project_name": "string",<br>"role_id": "string",<br>"role_name": "string",<br>"role_type": "string"<br>}<br>],<br>"role_id": "string",<br>"role_name": "string"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {<br>"user": {<br>"IDNumber": "string",<br>"address": "string",<br>"default_project_id": "string",<br>"description": "string",<br>"email": "string",<br>"enabled": true,<br>"idnumber": "string",<br>"nickname": "string",<br>"phone": "string",<br>"project_name": "string",<br>"projects_and_roles": [<br>{<br>"project_id": "string",<br>"project_name": "string",<br>"role_id": "string",<br>"role_name": "string",<br>"role_type": "string"<br>}<br>],<br>"role_id": "string",<br>"role_name": "string"<br>}<br>},<br>"status": false<br>\} |  |  |  |

### 6.24批量删除用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users | JSON | PUT | 批量删除用户 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| 用户id数组 | 是 | List | id列表 | id列表 |
| 例如：<br>[<br>"1df71957d770436493a84f24d9344853"<br>] |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| resources | List | 包含的资源 |  |
| exceptionServices | List | 异常的服务 |  |
| status | Boolean | 状态 | 是否可以删除 |
| id | string | 用户id | 不可删除的用户id |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res": {<br>"resources": [{<br>"type": "网络",<br>"type_en": "Network",<br>"dataList": null<br>}],<br>"exceptionServices": [],<br>"status": false,<br>"id": null<br>},<br>"status": false<br>\} |  |  |  |

### 6.25批量启用禁用用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users | JSON | PATCH | 批量启用禁用用户 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| userIds | 是 | List | 用户id列表 | id列表 |
| status | 是 | Boolean | 启用状态 | 启用(true)还是禁用(false) |
| 例如：<br>{<br>"status":false,<br>"userIds":["5ea0d1092dc54185adedc8df70f9349b"]<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"auth": false,<br>"code": "string",<br>"msg": "string",<br>"res":false,<br>"status": false<br>\} |  |  |  |

### 6.26批量导入用户

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/users/template | JSON | POST | 批量导入用户 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| file | 是 | MultipartFile |  | csv文件 |
| 登录名称 | 是 | String | 可包含英文字母a-zA-Z，数字0-9以及特殊字符“.”，“-”，“_”，不能以特殊字符开头，长度1-32个字符 |  |
| 密码 | 是 | String | 符合当前密码策略 |  |
| 组织 | 是 | String | 当前已有的组织 | 组织名称 |
| 角色 | 是 | String | 当前已有的角色 |  |
| 用户姓名 | 是 | String | 1-32字符 | 昵称 |
| 电子邮箱 | 是 | String | 邮箱格式 | 邮箱 |
| 例如：<br>登录名称(必填) / 密码(必填) / 组织(必填) / 角色(必填) / 用户姓名(必填) / 电子邮箱(必填) / 电话号码 / 通讯地址<br>a / cloud1 / root / user / 测试用户 / testNewUser@h3c.com |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| sucessUsers | 数组 | 成功的用户列表 |  |
| errorUsers | 数组 | 失败的用户列表 | errorInfo对应了失败的原因 |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"errorUsers": [{<br>"id": null,<br>"enabled": true,<br>"description": null,<br>"name": "testNewUser",<br>"password": "cloudOS123@",<br>"email": "testNewUser@xxx.com",<br>"phone": null,<br>"address": null,<br>"nickname": "测试用户",<br>"errorInfo": "template.createUser.name.conflict",<br>"idnumber": null,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"domain_id": "default",<br>"IDNumber": null,<br>"role_id": "19304d8d537c42c69aa19f5d9d41df76",<br>"role_name": "user",<br>"projects_and_roles": [{<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"project_name": "root-3",<br>"role_id": "19304d8d537c42c69aa19f5d9d41df76",<br>"role_name": "user",<br>"role_type": null<br>}],<br>"project_name": "root-3"<br>}],<br>"sucessUsers": []<br>},<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 6.27用户列表分页

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v2/users | JSON | GET | 用户列表分页（本地用户） |

###### 查询参数(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | 否 | String | 名称 |
| project_name | 否 | String | 组织名称 |
| role_name | 否 | String | 角色名称 |
| enabled | 否 | Boolean | 是否启用 |
| nickname | 否 | String | 昵称 |
| default_project_id | 否 | String | 组织id |
| role_id | 否 | String | 角色id |
| page | 否 | int | 第几页，默认1 |
| pagesize | 否 | int | 每页显示数据条数，默认10条 |
| order | 否 | String | 支持name和created_at，默认按name排序 |
| desc | 否 | Boolean | 是否降序，默认为否 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| res | List | 用户列表 |  |
| total | int | 用户总数 |  |
| default_project_id | string | 组织id |  |
| email | string | 用户邮箱 |  |
| enabled | Boolean | 用户启用状态 |  |
| id | string | 用户id |  |
| nickname | string | 用户昵称 |  |
| name | string | 用户name |  |
| project_name | string | 组织名称 |  |
| role_id | string | 角色id |  |
| role_name | string | 角色名称 |  |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"total": 2,<br>"res": [<br>{<br>"id": "1df71957d770436493a84f24d9344853",<br>"enabled": true,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2017-12-12 12:24:24",<br>"description": "预置系统管理员",<br>"name": "admin",<br>"email": "h3c@h3c.com",<br>"address": "新华三云计算技术有限公司",<br>"nickname": "系统管理员",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"project_name": "root",<br>"domain_id": "default",<br>"projects_and_roles": [<br>{<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"project_name": "root",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"role_type": null<br>}<br>]<br>},<br>{<br>"id": "d622d86efb634c2eab9ebf2583bfecf7",<br>"enabled": true,<br>"default_project_id": "69e49870206c420796582fd0056713eb",<br>"created_at": "2020-03-06 02:54:15",<br>"name": "qqq",<br>"email": "w@qq.com",<br>"nickname": "qqq",<br>"role_id": "586a6bc847854223ba9fe582afec0c6e",<br>"role_name": "org_admin",<br>"project_name": "test",<br>"domain_id": "default",<br>"projects_and_roles": [<br>{<br>"project_id": "69e49870206c420796582fd0056713eb",<br>"project_name": "test",<br>"role_id": "586a6bc847854223ba9fe582afec0c6e",<br>"role_name": "org_admin",<br>"role_type": null<br>}<br>]<br>}<br>]<br>},<br>"msg": ""<br>\} |  |  |  |

### 6.28组织列表分页

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v2/projects | JSON | GET | 组织列表分页 |

###### 查询参数(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | 否 | String | 名称 |
| enabled | 否 | Boolean | 是否启用 |
| page | 否 | int | 第几页，默认1 |
| pagesize | 否 | int | 每页显示数据条数，默认10条 |
| order | 否 | String | 支持name，默认按name排序 |
| desc | 否 | Boolean | 是否降序，默认为否 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| res | List | 组织列表 |  |
| total | int | 组织总数 |  |
| enabled | Boolean | 组织启用状态 |  |
| id | string | 组织id |  |
| name | string | 组织name |  |
| parent_id | string | 父组织id |  |
| domain_id | string | 域id |  |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"total": 2,<br>"res": [<br>{<br>"id": "9052ae85792143ff955c35c52e8e41bd",<br>"parent_id": "default",<br>"domain_id": "default",<br>"is_domain": false,<br>"name": "root",<br>"description": "Bootstrap project for initializing the cloud.",<br>"enabled": true,<br>"label": "root"<br>},<br>{<br>"id": "69e49870206c420796582fd0056713eb",<br>"parent_id": "9052ae85792143ff955c35c52e8e41bd",<br>"domain_id": "default",<br>"is_domain": false,<br>"name": "test",<br>"description": "",<br>"enabled": true,<br>"label": "test"<br>}<br>]<br>},<br>"msg": ""<br>\} |  |  |  |

### 6.29查询组织下的成员

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v2/projects/{projectId}/users | JSON | GET |  |

###### 查询参数(query-string)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | 否 | String | 名称 |
| page | 否 | int | 第几页，默认1 |
| pagesize | 否 | int | 每页显示数据条数，默认10条 |
| order | 否 | String | 支持name，默认按name排序 |
| desc | 否 | Boolean | 是否降序，默认为否 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| total | int | 成员总数量 |  |
| res | list | 用户列表 |  |
| 例如：<br>{<br>"code": "",<br>"data": {<br>"total": 5,<br>"res": [<br>{<br>"id": "1df71957d770436493a84f24d9344853",<br>"enabled": true,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2017-12-12 12:24:24",<br>"description": "预置系统管理员",<br>"name": "admin",<br>"email": "admin@test.com",<br>"phone": "12311122222",<br>"nickname": "系统管理员",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"project_name": "root",<br>"domain_id": "default",<br>"projects_and_roles": [<br>{<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"project_name": "root",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"role_type": null<br>}<br>]<br>},<br>{<br>"id": "355c5291e4b443028e36a3525f111784",<br>"enabled": true,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2020-03-28 01:42:17",<br>"name": "lyorgadmin",<br>"email": "1@1.com",<br>"nickname": "lyorgadmin",<br>"role_id": "586a6bc847854223ba9fe582afec0c6e",<br>"role_name": "org_admin",<br>"project_name": "root",<br>"domain_id": "default",<br>"projects_and_roles": [<br>{<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"project_name": "root",<br>"role_id": "586a6bc847854223ba9fe582afec0c6e",<br>"role_name": "org_admin",<br>"role_type": null<br>}<br>]<br>},<br>{<br>"id": "e0561cd289434279a29d0273e28da4ed",<br>"enabled": true,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2020-03-28 01:51:22",<br>"name": "lytest",<br>"email": "1@1.com",<br>"nickname": "lytest",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"project_name": "root",<br>"domain_id": "default",<br>"projects_and_roles": [<br>{<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"project_name": "root",<br>"role_id": "bc446e28f2314ded942e6c38be0cc85a",<br>"role_name": "admin",<br>"role_type": null<br>}<br>]<br>},<br>{<br>"id": "ca099f26b6b94b81ac2a484419250ae1",<br>"enabled": true,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2020-03-28 01:41:48",<br>"name": "lyuser",<br>"email": "1@1.com",<br>"nickname": "lyuser",<br>"role_id": "19304d8d537c42c69aa19f5d9d41df76",<br>"role_name": "user",<br>"project_name": "root",<br>"domain_id": "default",<br>"projects_and_roles": [<br>{<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"project_name": "root",<br>"role_id": "19304d8d537c42c69aa19f5d9d41df76",<br>"role_name": "user",<br>"role_type": null<br>}<br>]<br>},<br>{<br>"id": "7f8c4b7063494ffab5364acb59361ee7",<br>"enabled": true,<br>"default_project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"created_at": "2020-03-30 02:14:51",<br>"name": "ttwar",<br>"email": "1@1.com",<br>"nickname": "ttwar",<br>"role_id": "19304d8d537c42c69aa19f5d9d41df76",<br>"role_name": "user",<br>"project_name": "root",<br>"domain_id": "default",<br>"projects_and_roles": [<br>{<br>"project_id": "9052ae85792143ff955c35c52e8e41bd",<br>"project_name": "root",<br>"role_id": "19304d8d537c42c69aa19f5d9d41df76",<br>"role_name": "user",<br>"role_type": null<br>}<br>]<br>}<br>]<br>},<br>"msg": ""<br>\} |  |  |  |

### 6.30获取token接口

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| http://ip:5000/v3/auth/tokens | JSON | POST | Keystone提供的获取token接口 |

###### 参数

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| name | 是 | String | 用户名 |
| password | 是 | String | 密码 |
| domain.name | 是 | String | 域名称，本地用户默认default，ldap用户填写ldap的域名称 |
| 其他 |  |  | 保持不变 |

###### 请求体

例如：

```json
{
"auth": {
"identity": {
"methods": [
"password"
],
"password": {
"user": {
"name": "admin",
"domain": {
"name": "default"
},
"password": "cloudos"
}
}
}
}
}
```

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| token | String | 登录令牌 |  |
| user | Object | 用户信息 |  |
| 例如：<br>{<br>"token": {<br>"is_domain": false,<br>"methods": [<br>"password"<br>],<br>"roles": [<br>{<br>"id": "bc446e28f2314ded942e6c38be0cc85a",<br>"name": "admin"<br>}<br>],<br>"expires_at": "2020-06-10T18:06:25.000000Z",<br>"project": {<br>"domain": {<br>"id": "default",<br>"name": "default"<br>},<br>"id": "9052ae85792143ff955c35c52e8e41bd",<br>"name": "admin"<br>},<br>"catalog": [],<br>"user": {<br>"password_expires_at": null,<br>"domain": {<br>"id": "default",<br>"name": "default"<br>},<br>"id": "1df71957d770436493a84f24d9344853",<br>"name": "admin"<br>},<br>"audit_ids": [<br>"SdJrfpIzRwOeeFTGIbI97g"<br>],<br>"issued_at": "2020-06-10T10:06:25.000000Z"<br>}<br>\} |  |  |  |

## 网关

### 7.1健康检查配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/kong/healthcheck/service/{serviceId\} | JSON | PUT | 健康检查配置 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"activeCheck": true,<br>"passiveCheck": true<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "",<br>"data": {},<br>"msg": ""<br>\} |  |  |  |

### 7.2健康检查配置情况

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/kong/services/{serviceId}/healthcheck | JSON | GET | 健康检查配置情况 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"data": {<br>"activeHealthcheckStatus": true,<br>"passiveHealthcheckStatus": true<br>},<br>"mgs": "string"<br>\} |  |  |  |

### 7.3consumers列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/kong/consumers | JSON | GET | consumers列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"data": [<br>{<br>"created_at": 0,<br>"custom_id": "string",<br>"id": "string",<br>"username": "string"<br>}<br>],<br>"msg": "string"<br>\} |  |  |  |

### 7.4新建consumers

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /api/sys/oapi/v1/kong/consumers | JSON | POST | 新建consumers |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"custom_id": "string",<br>"username": "string"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"auth": true,<br>"code": "string",<br>"res": {<br>"created_at": 0,<br>"custom_id": "string",<br>"id": "string",<br>"username": "string"<br>},<br>"status": true<br>\} |  |  |  |

### 7.5特定consumer下是basic-auth列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/kong/consumers/{consumerId}/basic-auth | JSON | GET | 特定consumer下是basic-auth列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"data": [<br>{<br>"consumer": {<br>"id": "string"<br>},<br>"created_at": 0,<br>"id": "string",<br>"password": "string",<br>"username": "string"<br>}<br>],<br>"msg": "string"<br>\} |  |  |  |

### 7.6特定consumer下是key-auth列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/kong/consumers/{consumerId}/key-auth | JSON | GET | 特定consumer下是key-auth列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"data": [<br>{<br>"consumer": {<br>"id": "string"<br>},<br>"created_at": 0,<br>"id": "string",<br>"key": "string"<br>}<br>],<br>"msg": "string"<br>\} |  |  |  |

### 7.7修改consumers

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/kong/consumers/{id\} | JSON | PATCH | 修改consumers |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"custom_id": "string",<br>"username": "string"<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"auth": true,<br>"code": "string",<br>"res": {<br>"created_at": 0,<br>"custom_id": "string",<br>"id": "string",<br>"username": "string"<br>},<br>"status": true<br>\} |  |  |  |

### 7.8services列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/kong/services | JSON | GET | services列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"data": [<br>{<br>"connect_timeout": "string",<br>"created_at": 0,<br>"healthStatus": "string",<br>"host": "string",<br>"id": "string",<br>"name": "string",<br>"port": "string",<br>"protocol": "string",<br>"read_timeout": "string",<br>"retries": 0,<br>"routeNum": 0,<br>"updated_at": 0,<br>"write_timeout": "string"<br>}<br>],<br>"msg": ""<br>\} |  |  |  |

### 7.9特定service下的routes列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/kong/services/{serviceId}/routes | JSON | GET | 特定service下的routes列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"data": [<br>{<br>"created_at": 0,<br>"id": "string",<br>"paths": [<br>"string"<br>],<br>"preserve_host": false,<br>"protocols": [<br>"string"<br>],<br>"regex_priority": 0,<br>"service": {<br>"id": "string"<br>},<br>"strip_path": true,<br>"updated_at": 0<br>}<br>],<br>"msg": "string"<br>\} |  |  |  |

### 7.10路由认证配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/authorization/configuration/route/{routeId\} | JSON | PUT | 路由认证配置 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"basicAuth": false,<br>"keyAuth": false,<br>"keyName": "string"    //head of auth<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"data": {},<br>"msg": "string"<br>\} |  |  |  |

## 白名单

### 8.1白名单列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/database | JSON | GET | 白名单列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| res | List | 白名单列表 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": [<br>"172.25.18.234/32",<br>"172.25.18.235/32",<br>"172.25.18.233/32",<br>"172.25.0.173/32",<br>"172.25.0.174/32"<br>],<br>"msg": null<br>\} |  |  |  |

### 8.2新增白名单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/database | JSON | POST | 新增白名单 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
|  | 是 | List | ip列表 | 新增白名单列表 |
| 例如：<br>[<br>"172.25.0.173",<br>"172.25.0.174"<br>] |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": true,<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 8.3删除白名单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/database | JSON | PUT | 删除白名单 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
|  | 是 | List | ip列表 | 删除白名单列表 |
| 例如：<br>[<br>"172.25.0.173",<br>"172.25.0.174"<br>] |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": true,<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 8.4IP段添加白名单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/database/ipsegment | JSON | POST | IP段添加白名单 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| ipSegment | 是 | string | ip段 | ip段 |
| 例如：<br>{<br>"ipSegment":"172.25.7.11/24"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": true,<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 8.5校验数据库白名单IP是否存在

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/database/{ip}/exist | JSON | GET | 校验数据库白名单IP是否存在 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 是否存在 | false为存在 |
| 例如：<br>{<br>"status": false,<br>"auth": true,<br>"code": "svcmgt.whiteIpExist",<br>"res": false,<br>"msg": "ip exist",<br>"operRes": null<br>\} |  |  |  |

### 8.6IP段校验数据库白名单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/database/ipsegment/exist | JSON | POST | IP段校验数据库白名单 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| ipSegment | 是 | string | ip段 | ip段 |
| 例如：<br>{<br>"ipSegment":"172.25.7.11/24"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 是否存在 | false为存在 |
| 例如：<br>{<br>"status": false,<br>"auth": true,<br>"code": "svcmgt.whiteIpExist",<br>"res":false,<br>"msg": "ip exist",<br>"operRes": null<br>\} |  |  |  |

## 双因子

### 9.1查询双因子配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/double_factor | JSON | GET | 查询双因子配置 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| authMethod | string | 双因子校验方式 |  |
| enabled | Boolean | 是否开启 |  |
| validationTime | string | 验证码有效期 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"id": "505d146b-18ce-41bd-a604-89ddb73b2420",<br>"authMethod": "EMAIL",<br>"enabled": true,<br>"validationTime": "1",<br>"code": null<br>},<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 9.2修改双因子配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/double_factor | JSON | POST | 修改双因子配置 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| authMethod | 否 | string | EMAIL/SYS | 校验方式 |
| validationTime | 否 | string | 1/2/5 | 有效期 |
| enabled | 否 | Boolean | 开启/关闭 | 开启/关闭 |
| code | 是 | string | 验证码 | 验证码 |
| 例如：<br>{<br>"authMethod":"EMAIL",<br>"validationTime":"1",<br>"enabled":true,<br>"code":"xt90se"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": "",<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 9.3发送验证码

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/double_factor/verification/{userId\} | JSON | POST | 发送验证码 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| authMethod | 是 | string | EMAIL/SYS | 校验方式 |
| validationTime | 是 | string | 1/2/5 | 有效期 |
| 例如：<br>{<br>"authMethod":"EMAIL",<br>"validationTime":1<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Boolean | 成功or失败 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": "",<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

## LDAP

### 获取域列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/domains | JSON | GET | 获取域列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | string | 域id |  |
| name | string | 域名 |  |
| enabled | Boolean | 是否开启 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": [<br>{<br>"description": "Domain test",<br>"id": "ae116ce94c2c4fa1b56fd4edc9816ca3",<br>"name": "ldap_test",<br>"enabled": true<br>},<br>{<br>"description": "The default domain",<br>"id": "default",<br>"name": "default",<br>"enabled": true<br>}<br>],<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 获取某个ldap域及配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/domains/{id\} | JSON | GET | 获取某个ldap域及配置 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| id | string | 域id |  |
| name | string | 域名 |  |
| enabled | Boolean | 是否开启 |  |
| url | string | ldap服务器地址 |  |
| suffix | string | 服务后缀 |  |
| password | string | 服务器密码 |  |
| query_scope | string | 查询范围 |  |
| user | string | 管理员dn |  |
| user_objectclass | string | 用户类别 |  |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"domain": {<br>"description": "Domain test",<br>"id": "ae116ce94c2c4fa1b56fd4edc9816ca3",<br>"name": "ldap_test",<br>"enabled": true<br>},<br>"ldap": {<br>"url": "ldap://10.125.30.231",<br>"suffix": "dc=openstack,dc=org",<br>"password": null,<br>"query_scope": "sub",<br>"user": "dc=Manager,dc=openstack,dc=org",<br>"user_id_attribute": "cn",<br>"user_name_attribute": "cn",<br>"user_objectclass": "person"<br>}<br>},<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

### 新增ldap配置

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/oapi/v1/domains | JSON | POST | 新增ldap配置 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| name | 是 | string | 1-64字符 | 域名称 |
| enabled | 否 | Boolean | true/false | 是否开启 |
| url | 是 | string | ldap://ip | ldap服务器地址 |
| suffix | 是 | string | 服务后缀 | 服务后缀 |
| password | 是 | string | 服务器密码 | 服务器密码 |
| query_scope | 是 | string | sub/one | 查询范围 |
| user | 是 | string | 管理员dn | 管理员dn |
| user_objectclass | 是 | string | 用户类别 | 用户类别 |
| 例如：<br>{<br>"domain": {<br>"description": "Domain new9",<br>"name": "ldap_new9"<br>},<br>"ldap": {<br>"url": "ldap://10.125.30.231",<br>"suffix": "dc=openstack,dc=org",<br>"password": "cloudos",<br>"query_scope": "sub",<br>"user_tree_dn": "ou=Users,dc=openstack,dc=org",<br>"user": "dc=Manager,dc=openstack,dc=org",<br>"user_id_attribute": "cn",<br>"user_name_attribute": "cn",<br>"user_objectclass": "person"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"domain": {<br>"description": "Domain domian modify",<br>"id": null,<br>"name": "ldap_new12",<br>"enabled": true<br>},<br>"ldap": {<br>"url": "ldap://10.125.30.213",<br>"suffix": "dc=openstack,dc=org",<br>"password": "cloudos",<br>"query_scope": "sub",<br>"user": "dc=Manager,dc=openstack,dc=org",<br>"user_id_attribute": "cn",<br>"user_name_attribute": "cn",<br>"user_objectclass": "person",<br>"user_tree_dn": "ou=Users,dc=openstack,dc=org"<br>}<br>},<br>"msg": null,<br>"operRes": null<br>\} |  |  |  |

## 租期

### 查询资源租期数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/restenancy | JSON | GET | 查询资源租期数据 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| serUuid | false | string | 有租期的服务uuid | 指定服务uuid |
| resUuid | false | string | 有租期的资源uuid | 指定资源uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resourceTenancys": [<br>{<br>"uuid": "02a01373-ef5f-4947-be4f-8ade8d45b484",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "ladwin7-virus-telency",<br>"startTime": 1531970914000,<br>"endTime": 1532057306000,<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": 10,<br>"deleted": false,<br>"dueAction": "email_inform",<br>"serName": "cloudos.resourceServer.host"<br>}<br>]<br>\} |  |  |  |

### 续期指定的资源租期

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/restenancy/{uuid\} | JSON | PUT | 续期指定的资源租期 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | true | string | 有租期的资源uuid | 资源租期uuid |
| tenancyUuid | true | string | 存在的租期uuid | 增加的租期时长对应的uuid |
| resName | false | string | 修改的资源名称 | 修改的资源名称 |
| description | false | string | —— | 修改的资源租期的描述 |
| 例如：<br>{<br>"resourceTenancy": {<br>"uuid": "6cfb7b88-ee21-41de-aa2b-87dd9bb9e67c",<br>"tenancyUuid": "23d539d1-d7b0-493c-9486-de439618efc1",<br>"resName": "vm-1",<br>"description": "this is resource description"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resourceTenancy": {<br>"uuid": "02a01373-ef5f-4947-be4f-8ade8d45b484",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "vm-1",<br>"startTime": 1531970914000,<br>"endTime": 1532057306000,<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": 10,<br>"deleted": false<br>}<br>\} |  |  |  |

### 查询指定的资源租期数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/restenancy/{uuid\} | JSON | GET | 查询指定的资源租期数据 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | true | string | 存在的资源租期uuid | (path)指定资源租期uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resourceTenancy": {<br>"uuid": "02a01373-ef5f-4947-be4f-8ade8d45b484",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "vm-1",<br>"startTime": 1531970914000,<br>"endTime": 1532057306000,<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": 10,<br>"deleted": false,<br>"description": "this is a resource description",<br>"dueAction": "email_inform"<br>}<br>\} |  |  |  |

### 添加资源租期

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/restenancy/{uuid\} | JSON | POST | 添加资源租期 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | true | string | 资源的租户uuid | (path) 资源的租户uuid，即tenantUuid |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| userUuid | true | string | 资源租期相关资源的创建用户uuid | 资源租期相关资源的创建用户uuid |
| userName | false | string | 资源租期相关资源的创建用户名 | 资源租期相关资源的创建用户名 |
| serUuid | true | string | 资源租期相关资源的服务uuid | 资源租期相关资源的服务uuid |
| resUuid | true | string | 资源租期相关资源的uuid | 资源租期相关资源的uuid |
| resName | true | string | 资源租期相关资源的名称 | 资源租期相关资源的名称 |
| tenancyEndTime | false | string | 当前时间-9999-12-31 23:59:59 | 资源租期到期时间 |
| tenancyUuid | true | string | 存在的租期uuid | 资源选择的租期的uuid |
| 例如：<br>{<br>"resourceTenancy": {<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "ladwin7-virus-telency",<br>"tenancyEndTime": "2018-01-01T10:15:16:50.000+08:00",<br>"tenancyUuid": "9052ae85792143ff955c35c52e8e41bd"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resourceTenancy": {<br>"uuid": "02a01373-ef5f-4947-be4f-8ade8d45b484",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "vm-1",<br>"startTime": 1531970914000,<br>"endTime": 1532057306000,<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": 10,<br>"deleted": false<br>}<br>\} |  |  |  |

### 删除指定的资源租期数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/restenancy/{uuid\} | JSON | DELETE | 删除指定的资源租期数据 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | true | string | 存在的资源租期uuid | (path) 指定资源租期uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resourceTenancy": {<br>"uuid": "02a01373-ef5f-4947-be4f-8ade8d45b484",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "vm-1",<br>"startTime": 1531970914000,<br>"endTime": 1532057306000,<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": 10,<br>"deleted": false,<br>"description": "this is a resource description",<br>"dueAction": "email_inform"<br>}<br>\} |  |  |  |

### 查询指定服务下的资源租期

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/{serviceUuid}/restenancy | JSON | PUT | 查询指定服务下的资源租期 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| serviceUuid | true | string | 存在租期的服务uuid | (path)资源所属的服务id |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| resource | true | string | 存在的资源uuid | 批量查询资源租期的数据 |
| 例如：<br>{<br>"resource": [<br>"4d8e5419-63cc-489d-bab6-6beb62d67a6d"<br>]<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resourceTenancys": [<br>{<br>"uuid": "02a01373-ef5f-4947-be4f-8ade8d45b484",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "vm-1",<br>"startTime": 1531970914000,<br>"endTime": 1532057306000,<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": 10,<br>"deleted": false<br>}<br>]<br>\} |  |  |  |

### 删除指定的服务的资源租期数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/{serviceUuid}/restenancy/{resUuid\} | JSON | DELETE | 删除指定的服务的资源租期数据 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| serviceUuid | true | string | 存在租期的服务uuid | (path)指定服务uuid |
| resUuid | true | string | 存在的资源uuid | (path)指定资源uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resourceTenancy": {<br>"uuid": "02a01373-ef5f-4947-be4f-8ade8d45b484",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "vm-1",<br>"startTime": 1531970914000,<br>"endTime": 1532057306000,<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": 10,<br>"deleted": false<br>}<br>\} |  |  |  |

### 查询租期策略数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/restenancy_policy | JSON | GET | 查询租期策略数据 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| serviceUuid | false | string | 存在租期的服务uuid | 指定服务uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"policys": [<br>{<br>"uuid": "15e107fb-7a4c-4e9a-ac11-fa3c8a8f59f2",<br>"action": "email_inform",<br>"service_uuid": "eaf07537-2b38-4a57-90a8-ad1a6cc5bed6",<br>"remind_type": "email",<br>"notice_day": 1<br>}<br>]<br>\} |  |  |  |

### 查询指定的租期策略数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/restenancy_policy/{uuid\} | JSON | GET | 查询指定的租期策略数据 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | true | string | 存在的租期策略uuid | (path)指定策略uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"policy": {<br>"uuid": "15e107fb-7a4c-4e9a-ac11-fa3c8a8f59f2",<br>"action": "email_inform",<br>"service_uuid": "eaf07537-2b38-4a57-90a8-ad1a6cc5bed6",<br>"remind_type": "email",<br>"notice_day": 1<br>}<br>\} |  |  |  |

### 修改指定的租期策略数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/restenancy_policy/{uuid\} | JSON | PUT | 修改指定的租期策略数据 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | true | string | 存在的租期策略uuid | (path)指定策略uuid |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| action | false | string | Enum:[email_inform,close, destroy ] | 资源租期到期操作（邮件通知，自动关闭，自动销毁） |
| notice_day | false | integer | 1-365的整数 | 到期提醒天数 |
| remind_type | false | string | Enum:<br>[email, message] | 提醒方式 |
| 例如：<br>{<br>"policy": {<br>"action": "email_inform",<br>"remind_type": "email",<br>"notice_day": 1<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resourceTenancys": [<br>{<br>"uuid": "02a01373-ef5f-4947-be4f-8ade8d45b484",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "4d8e5419-63cc-489d-bab6-6beb62d67a6d",<br>"resName": "vm-1",<br>"startTime": 1531970914000,<br>"endTime": 1532057306000,<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": 10,<br>"deleted": false<br>}<br>]<br>\} |  |  |  |

### 查询租期数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/tenancys | JSON | GET | 查询租期数据 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| uuid | string | 租期uuid |  |
| tenancy | integer | 租期时长 | 租期时长，-1 代表永久 |
| calculateUnit | string | 租期单位 | Enum:<br>[tenancy.day,tenancy.week,tenancy.month, tenancy.quarter,tenancy.year,tenancy.forever ] |
| 例如：<br>{<br>"tenancys": [<br>{<br>"uuid": "23d539d1-d7b0-493c-9486-de439618efc1",<br>"tenancy": 1,<br>"calculateUnit": "tenancy.day"<br>}<br>]<br>\} |  |  |  |

### 删除指定的租期

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/tenancys | JSON | PUT | 删除指定的租期 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
|  | True | string | 存在的租期id | 删除的租期id |
| 例如：<br>[<br>"4d8e5419-63cc-489d-bab6-6beb62d67a6d"<br>] |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |

### 添加租期

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/tenancys | JSON | POST | 添加租期 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| tenancy | 是 | String | -1- 999999999 | 租期时长，-1 代表永久 |
| calculateUnit | 是 | String | Enum:<br>[tenancy.day,tenancy.week,tenancy.month, tenancy.quarter,tenancy.year,tenancy.forever ] | 租期单位 |
| 例如：<br>{<br>"tenancy": {<br>"tenancy": 1,<br>"calculateUnit": "tenancy.day"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |

### 查询指定的租期数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/tenancys/{tenancyUuid\} | JSON | GET | 查询指定的租期数据 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| tenancyUuid | true | string | 存在的租期uuid | (path) 租期的uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"tenancy": {<br>"uuid": "23d539d1-d7b0-493c-9486-de439618efc1",<br>"tenancy": 1,<br>"calculateUnit": "tenancy.day"<br>}<br>\} |  |  |  |

### 删除指定的租期数据

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/cloudos/tenancys/{tenancyUuid\} | JSON | PUT | 删除指定的租期数据 |

###### 参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| tenancyUuid | true | string | 存在的租期uuid | 租期uuid |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如： |  |  |  |

### 查询租期列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/tenancy/findTenancyList | JSON | GET | 查询租期列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| uuid | string | 租期uuid |  |
| tenancy | string | 租期时长 | 租期时长，-1 代表永久 |
| calculateUnit | string | 租期单位 | Enum:<br>[tenancy.day,tenancy.week,tenancy.month, tenancy.quarter,tenancy.year,tenancy.forever ] |
| 例如：<br>{<br>"code": "",<br>"data": [<br>{<br>"id": "23d539d1-d7b0-493c-9486-de439618efc1",<br>"tenancy": "1",<br>"calculateUnit": "tenancy.day",<br>"tenancyInfo": "1tenancy.day"<br>},<br>{<br>"id": "494c3e40-01c5-4173-aa95-04350659d6ac",<br>"tenancy": "1",<br>"calculateUnit": "tenancy.week",<br>"tenancyInfo": "1tenancy.week"<br>},<br>{<br>"id": "ee120318-0500-4678-9782-0113e5375f8f",<br>"tenancy": "1",<br>"calculateUnit": "tenancy.month",<br>"tenancyInfo": "1tenancy.month"<br>},<br>{<br>"id": "8bf4a75f-4c9e-4d2f-936f-18fe2711ee65",<br>"tenancy": "1",<br>"calculateUnit": "tenancy.quarter",<br>"tenancyInfo": "1tenancy.quarter"<br>},<br>{<br>"id": "d7f93e3d-e4d5-4d96-999a-8cd864bd9c58",<br>"tenancy": "1",<br>"calculateUnit": "tenancy.year",<br>"tenancyInfo": "1tenancy.year"<br>},<br>{<br>"id": "7b0a70c0-a480-42cd-a5ea-c95cf77af89a",<br>"tenancy": "-1",<br>"calculateUnit": "tenancy.forever",<br>"tenancyInfo": "tenancy.forever"<br>}<br>]<br>\} |  |  |  |

### 删除租期

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/tenancy/deletetenancy | JSON | POST | 删除租期 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| tenancys | true | String | 存在的租期uuid | 删除的租期信息，如：e8e011af-360e-4420-a016-a31e97a85e4c*56tenancy.day |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "",<br>"failedNum": 0,<br>"successNum": 1<br>\} |  |  |  |

### 添加租期

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/tenancy/addtenancy | JSON | POST | 添加租期 |

###### 请求体（form-data）

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| tenancy | 是 | String | -1- 999999999的整数 | 租期时长，-1 代表永久 |
| calculateUnit | 是 | String | Enum:<br>[tenancy.day,tenancy.week,tenancy.month, tenancy.quarter,tenancy.year,tenancy.forever ] | 租期单位 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| {<br>"code": "",<br>"message": "",<br>"data": ""<br>\} |  |  |  |

### 查询租期策略列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/tenancy/findPolicyList | JSON | GET | 查询租期策略列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "",<br>"data": [<br>{<br>"uuid": "90111c4c-428a-4954-aeb4-8636dc956f63",<br>"serviceUuid": "370bc1f2-ea2b-46ae-9d25-be0c0e32b1ec",<br>"serviceName": "cloudos.service.ironic",<br>"remindType": "email",<br>"remindTypeForDis": "cloudos.leaseManage.emailReminder",<br>"action": "close",<br>"actionForDis": "close",<br>"noticeDay": 1<br>}<br>]<br>\} |  |  |  |

### 修改租期策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/tenancy/modifypolicytenancy | JSON | POST | 修改租期策略 |

###### 请求体（form-data）

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | true | string | 存在的租期策略uuid | 租期策略uuid |
| serviceUuid | false | string | 租期策略所属的服务uuid | 服务类型uuid |
| remindType | false | string | Enum:<br>[email, message] | 提醒类型（电子邮件，短信） |
| remindTypeForDis | false | string |  |  |
| action | false | string | Enum:<br>[email_inform,close, destroy ] | 资源租期到期操作（邮件通知，自动关闭，自动销毁） |
| actionForDis | false | string |  |  |
| noticeDay | true | string | 1-365的整数 | 到期提醒天数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "",<br>"message": "",<br>"data": ""<br>\} |  |  |  |

### 查询资源租期列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/tenancy/findAllRestenancyList | JSON | GET | 查询资源租期列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "",<br>"data": [<br>{<br>"uuid": "0fbda75e-f0d8-408e-bbf6-e5d23e45efa2",<br>"userUuid": "1df71957d770436493a84f24d9344853",<br>"userName": "admin",<br>"serUuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"resUuid": "58bb37aa-ad80-4e8c-89c6-ac1fe4b8d306",<br>"resName": "server-zxf-user",<br>"startTime": "2020-03-12 09:42:07",<br>"endTime": "9999-12-31 23:59:59",<br>"due": false,<br>"tenantId": "9052ae85792143ff955c35c52e8e41bd",<br>"tenancyDay": "-1",<br>"deleted": false,<br>"serName": "cloudos.resourceServer.host"<br>}<br>]<br>\} |  |  |  |

### 续期指定的资源租期

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/gemini/v1/tenancy/reletRestenancy | JSON | POST | 续期指定的资源租期 |

###### 查询参数

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | true | string | 存在的资源租期uuid | 资源租期uuid |
| tenancyUuid | false | string | 存在的租期uuid | 增加的租期时长对应的uuid；不续租时传空字符串，需传入resName |
| resName | false | string | 与资源命名规范一致 | 修改的资源名称；不修改名称时空字符串，需传入tenancyUuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "",<br>"message": "",<br>"data": ""<br>\} |  |  |  |

## 授权

### 查询已有的授权状态

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/statistics | JSON | GET | 查询已有的授权状态 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| {<br>"licenses": [<br>{<br>"feature": "MaxMoNum",<br>"status": "ENABLE",<br>"type": "FUNCTION",<br>"count": 0<br>}<br>]<br>\} |  |  |  |

### 查看授权管理配置信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/server/configuration | JSON | GET | 查看授权管理配置信息 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"config": {<br>"host": "10.153.194.21",<br>"port": 5555,<br>"client_name": "h3c",<br>"password": "cloudos"<br>}<br>\} |  |  |  |

### 配置授权管理参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/server/configuration | JSON | POST | 配置授权管理参数 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| host | true | string | 存在的sever的ip地址 | ip地址 |
| port | true | integer | 端口范围(1025-65535) | sever的端口 |
| client_name | true | string | 长度1-31字符 | 配置的客户端名称 |
| password | true | string | 长度8-30字符 | 密码 |
| 例如：<br>{<br>"config": {<br>"host": "10.153.194.21",<br>"port": 5555,<br>"client_name": "h3c",<br>"password": "cloudos"<br>}<br>\} |  |  |  |  |

### 连接License Server

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/server/connect | JSON | PUT | 连接License Server |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| {<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Bool | true操作成功 |  |
| code | 字符串 | 0 无异常 |  |
| {<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": ""<br>\} |  |  |  |

### 断开License Server

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/server/disconnect | JSON | PUT | 断开License Server |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| {<br>\} |  |  |  |  |

### 查看License Server连接状态

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/server/status | JSON | GET | 查看License Server连接状态 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| status | Bool | True操作成功 |  |
| code | String | 0无异常 |  |
| connect_status 中status | String | NOT_CONFIG：未配置未连接<br>NOT_CONNEC：已配置未连接<br>IN_CONNECTTING：连接中<br>RE_CONNECTING：重新连接中<br>CONNECT_OK：已连接<br>CONNECT_FAILED：连接失败<br>CONNECT_FAIL_AUTH：认证失败 | 连接授权服务的状态描述 |
| {<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"connect_status": {<br>"status": "NOT_CONNECT",<br>"description": "已配置未连接"<br>}<br>}<br>\} |  |  |  |

### 发送授权同步请求

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/server/sync | JSON | PUT | 发送授权同步请求 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| {<br>\} |  |  |  |  |

### 向License Server申请授权

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/server/ apply | JSON | POST | 向License Server申请授权 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| feature | true | String | 存在的授权名称 | 授权名称 |
| apply_num | true | Integer | 1~2147483647之间的数字 | 数量型申请授权数量，all为true时，此数字无效。 |
| all | true | boolean | —— | 数量型申请全部授权。设true时apply_num无效，代表申请全部数量 |
| function | true | boolean | —— | 功能型申请。true代表申请功能,false代表不申请 |
| license_type | true | String | Enum: [FUNCTION,NUMBER ,STRING] | 授权类型，FUNCTION 功能型，NUMBER 数量型,STRING字符串型 |
| {<br>"license": {<br>"feature": "MaxMoNum",<br>"apply_num": 400,<br>"all": true,<br>"function": false,<br>"license_type": "FUNCTION"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| {<br>"license": {<br>"feature": "MaxMoNum",<br>"apply_num": 400,<br>"all": true,<br>"function": false,<br>"license_type": "FUNCTION",<br>"status": false<br>}<br>\} |  |  |  |

### 查询所有的详细授权信息列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/license/v1/permit/detail | JSON | GET | 查询所有的详细授权信息列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"products": [<br>{<br>"product": "OS",<br>"details": [<br>{<br>"feature": "MaxMoNum",<br>"enabled": true,<br>"description": "运营版软件授权",<br>"version": "OFFICIAL",<br>"effective_days": 29,<br>"expiry_date": "2017-10-01",<br>"perpetuity": false,<br>"last_connect_time": "2017-01-01T10:15:16:50.000+08:00",<br>"apply_number": {<br>"total": 550,<br>"permitted": 45,<br>"number_unit": "MO",<br>"apply_num": 400,<br>"all": true<br>},<br>"apply_function": {<br>"apply_value": false,<br>"function": false<br>},<br>"license_type": "FUNCTION",<br>"default_temporary_type": "NONE",<br>"default_apply_num": "all",<br>"base": [<br>{<br>"product": "H3C",<br>"feature": "H3Cloud"<br>}<br>]<br>}<br>]<br>}<br>]<br>\} |  |  |  |

## 计费管理

### 获取计费策略列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudoKitty/findAllKittyFrees | JSON | GET | 获取计费策略列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>[<br>{<br>"uuid": "eae6f6dd-1b9f-469e-b10e-bd34c12a80fb",<br>"name": "测试计费策略1",<br>"freeTime": 0,<br>"description": "这是一个描述",<br>"status": "cloudos.kitty.noUse"<br>}<br>] |  |  |  |

### 查询某个计费策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudoKitty/findKittyDtetail | JSON | GET | 根据uuid查询计费策略 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | true | String | 计费策略uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"commonMessage": {<br>"uuid": "eae6f6dd-1b9f-469e-b10e-bd34c12a80fb",<br>"name": "测试计费策略1",<br>"freeTime": 0,<br>"currency": "CNY",<br>"description": "这是一个描述",<br>"status": "notUse",<br>}<br>"serviceFreeParent": [<br>{<br>"checkShow": true,<br>"resource": "云主机",<br>"subName": "cloudos.norm.serverType",<br>"uuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"subList": [{<br>"advance": [{<br>"initPrice": 0,<br>"normAdvContent": "cloudDesktop.res.cpu:1cloudDesktop.res.cpu.core\|cloudDesktop.res.mem:anyG\|cloudos.database.add.disk:anyG\|cloudos.serverTemplate.storageType:cloudos.volume.add.normal\|cloudos.cloudkitty.vgpu.type:111\|cloudos.cloudkitty.vgpu.number:1",<br>"normUuid": "1e7181c6-0c77-4dad-8672-0e50af6bc390",<br>"resTypeUuid": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"normUuid": "1e7181c6-0c77-4dad-8672-0e50af6bc390",<br>"unitPrice": 0,<br>"uuid": "210a7754-93ec-49fa-a17d-d12ee7b088b4"<br>}],<br>"basic": [{<br>"initPrice": 0,<br>"initUnit": "G",<br>"normName": "cloudDesktop.res.mem(G)",<br>"normValue": "1",<br>"resTypeUuid": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"initUnit": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"stepFlag": false,<br>"unitPrice": 0,<br>"uuid": "210a7754-93ec-49fa-a17d-d12ee7b088b4"<br>}]<br>"resValue": [{<br>"charging": true,<br>"subNormName": "cloudos.serverTemplate.storageType",<br>"subNormUuid": "7e2df446-4497-11e9-8a6a-02420a654e1d"<br>}]<br>"resource": "cloudos.normConfig.commonHostName",<br>"serviceUuid": "38e42432-06a5-47a3-b47d-f078ed03795d",<br>"uuid": "d155eef2-c6c4-4da3-9981-7bdbb10b7a67",<br>}]<br>\} |  |  |  |

### 修改计费策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudoKitty/updateKityUuid | JSON | POST | 修改计费策略 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"commonMessage": {<br>"uuid": "eae6f6dd-1b9f-469e-b10e-bd34c12a80fb",<br>"name": "测试计费策略1",<br>"freeTime": 0,<br>"currency": "CNY",<br>"description": "这是一个描述",<br>"status": "notUse",<br>}<br>"serviceFreeParent": [<br>{<br>"checkShow": true,<br>"resource": "云主机",<br>"subName": "cloudos.norm.serverType",<br>"uuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"subList": [{<br>"advance": [{<br>"initPrice": 0,<br>"normAdvContent": "cloudDesktop.res.cpu:1cloudDesktop.res.cpu.core\|cloudDesktop.res.mem:anyG\|cloudos.database.add.disk:anyG\|cloudos.serverTemplate.storageType:cloudos.volume.add.normal\|cloudos.cloudkitty.vgpu.type:111\|cloudos.cloudkitty.vgpu.number:1",<br>"normUuid": "1e7181c6-0c77-4dad-8672-0e50af6bc390",<br>"resTypeUuid": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"normUuid": "1e7181c6-0c77-4dad-8672-0e50af6bc390",<br>"unitPrice": 0,<br>"uuid": "210a7754-93ec-49fa-a17d-d12ee7b088b4"<br>}],<br>"basic": [{<br>"initPrice": 0,<br>"initUnit": "G",<br>"normName": "cloudDesktop.res.mem(G)",<br>"normValue": "1",<br>"resTypeUuid": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"initUnit": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"stepFlag": false,<br>"unitPrice": 0,<br>"uuid": "210a7754-93ec-49fa-a17d-d12ee7b088b4"<br>}]<br>"resValue": [{<br>"charging": true,<br>"subNormName": "cloudos.serverTemplate.storageType",<br>"subNormUuid": "7e2df446-4497-11e9-8a6a-02420a654e1d"<br>}]<br>"resource": "cloudos.normConfig.commonHostName",<br>"serviceUuid": "38e42432-06a5-47a3-b47d-f078ed03795d",<br>"uuid": "d155eef2-c6c4-4da3-9981-7bdbb10b7a67",<br>}]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "success",<br>"message": "cloudos.kitty.msg.updateSucc"<br>\} |  |  |  |

### 删除某个计费策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudoKitty/deleteKittyBuUuid | JSON | DELETE | 删除某个计费策略 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| uuid | true | String | 计费策略uuid*计费策略名称name |
| checkedLen | true | number | 删除几条计费策略 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |

### 新建计费策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudoKitty/creatCloudKity | JSON | POST | 修改计费策略 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"commonMessage": {<br>"uuid": "eae6f6dd-1b9f-469e-b10e-bd34c12a80fb",<br>"name": "测试计费策略1",<br>"freeTime": 0,<br>"currency": "CNY",<br>"description": "这是一个描述",<br>"status": "notUse",<br>}<br>"serviceFreeParent": [<br>{<br>"checkShow": true,<br>"resource": "云主机",<br>"subName": "cloudos.norm.serverType",<br>"uuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"subList": [{<br>"advance": [{<br>"initPrice": 0,<br>"normAdvContent": "cloudDesktop.res.cpu:1cloudDesktop.res.cpu.core\|cloudDesktop.res.mem:anyG\|cloudos.database.add.disk:anyG\|cloudos.serverTemplate.storageType:cloudos.volume.add.normal\|cloudos.cloudkitty.vgpu.type:111\|cloudos.cloudkitty.vgpu.number:1",<br>"normUuid": "1e7181c6-0c77-4dad-8672-0e50af6bc390",<br>"resTypeUuid": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"normUuid": "1e7181c6-0c77-4dad-8672-0e50af6bc390",<br>"unitPrice": 0,<br>"uuid": "210a7754-93ec-49fa-a17d-d12ee7b088b4"<br>}],<br>"basic": [{<br>"initPrice": 0,<br>"initUnit": "G",<br>"normName": "cloudDesktop.res.mem(G)",<br>"normValue": "1",<br>"resTypeUuid": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"initUnit": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"stepFlag": false,<br>"unitPrice": 0,<br>"uuid": "210a7754-93ec-49fa-a17d-d12ee7b088b4"<br>}]<br>"resValue": [{<br>"charging": true,<br>"subNormName": "cloudos.serverTemplate.storageType",<br>"subNormUuid": "7e2df446-4497-11e9-8a6a-02420a654e1d"<br>}]<br>"resource": "cloudos.normConfig.commonHostName",<br>"serviceUuid": "38e42432-06a5-47a3-b47d-f078ed03795d",<br>"uuid": "d155eef2-c6c4-4da3-9981-7bdbb10b7a67",<br>}]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "success",<br>"message": "cloudos.cloudkitty.createSuccess"<br>\} |  |  |  |

### 查询正在使用的计费策略

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudos/charging/chargeInUse | JSON | GET | 查询正在使用的计费策略 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"commonMessage": {<br>"uuid": "eae6f6dd-1b9f-469e-b10e-bd34c12a80fb",<br>"name": "测试计费策略1",<br>"freeTime": 0,<br>"currency": "CNY",<br>"description": "这是一个描述",<br>"status": "using",<br>}<br>"serviceFreeParent": [<br>{<br>"checkShow": true,<br>"resource": "云主机",<br>"subName": "cloudos.norm.serverType",<br>"uuid": "17037211-2c0c-4703-98e8-81d0202a46e7",<br>"subList": [{<br>"advance": [{<br>"initPrice": 0,<br>"normAdvContent": "cloudDesktop.res.cpu:1cloudDesktop.res.cpu.core\|cloudDesktop.res.mem:anyG\|cloudos.database.add.disk:anyG\|cloudos.serverTemplate.storageType:cloudos.volume.add.normal\|cloudos.cloudkitty.vgpu.type:111\|cloudos.cloudkitty.vgpu.number:1",<br>"normUuid": "1e7181c6-0c77-4dad-8672-0e50af6bc390",<br>"resTypeUuid": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"normUuid": "1e7181c6-0c77-4dad-8672-0e50af6bc390",<br>"unitPrice": 0,<br>"uuid": "210a7754-93ec-49fa-a17d-d12ee7b088b4"<br>}],<br>"basic": [{<br>"initPrice": 0,<br>"initUnit": "G",<br>"normName": "cloudDesktop.res.mem(G)",<br>"normValue": "1",<br>"resTypeUuid": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"initUnit": "d838cc5b-4181-48ee-a3dc-5e91185ca09e",<br>"stepFlag": false,<br>"unitPrice": 0,<br>"uuid": "210a7754-93ec-49fa-a17d-d12ee7b088b4"<br>}]<br>"resValue": [{<br>"charging": true,<br>"subNormName": "cloudos.serverTemplate.storageType",<br>"subNormUuid": "7e2df446-4497-11e9-8a6a-02420a654e1d"<br>}]<br>"resource": "cloudos.normConfig.commonHostName",<br>"serviceUuid": "38e42432-06a5-47a3-b47d-f078ed03795d",<br>"uuid": "d155eef2-c6c4-4da3-9981-7bdbb10b7a67",<br>}]<br>\} |  |  |  |

### 查询规格费用

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudos/charging/charge/fee/{service_uuid\} | JSON | GET | 查询规格费用 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| serviceUuid | true | String | 资源服务uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>[<br>{<br>"uuid": "f4471434-2a2f-4172-8852-268b8294dddd",<br>"serviceUuid": "c5f6a6d2-e5b8-475c-a9f2-4e257e92aaaa",<br>"normName": "ObjectStorageNorm",<br>"model": "[\\\"basic\\\"]",<br>"description": "",<br>"currency": "CNY",<br>"cycle": 1,<br>"basics": [<br>{<br>"uuid": "e23aa357-f71e-4dbb-ae19-066e45c7eeee",<br>"normUuid": "f4471434-2a2f-4172-8852-268b8294dddd",<br>"subNormUuid": "string",<br>"subNormName": "容器容量",<br>"subNormValue": 1,<br>"subNormContent": {<br>"valueType": "region",<br>"min": 1,<br>"max": 10,<br>"step": 1,<br>"normValue": "普通"<br>},<br>"initPrice": 1.2,<br>"unitPrice": 2.1,<br>"unit": "G",<br>}<br>],<br>"advance": [<br>{<br>"uuid": "e23aa357-f71e-4dbb-ae19-066e45c7eeee",<br>"normUuid": "f4471434-2a2f-4172-8852-268b8294dddd",<br>"normAdvContent": "cloudDesktop.res.cpu:1cloudDesktop.res.cpu.core\|cloudDesktop.res.mem:anyG\|cloudos.database.add.disk:anyG\|cloudos.serverTemplate.storageType:cloudos.volume.add.normal\|cloudos.cloudkitty.vgpu.type:111\|cloudos.cloudkitty.vgpu.number:1",<br>"subNorms": [{<br>"uuid": "e23aa357-f71e-4dbb-ae19-066e45c7eeee"<br>}]<br>}<br>]<br>}<br>] |  |  |  |

### 查询指定服务费用账单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudos/cloudkitty/{service_uuid}/bills | JSON | GET | 查询费用账单 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| serviceUuid | true | String | 资源服务uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resources": [<br>{<br>"uuid": "ccf33874-697c-4920-94d0-6ed9ad964189",<br>"name": "主机1",<br>"tenant_id": "9052ae85792143ff955c35c52e8e41bd",<br>"price": 1,<br>"finished": false,<br>"deleted": false,<br>"detail": [<br>{<br>"begin": "2018-07-23T11:26:00",<br>"end": "2018-07-23T15:09:36.429",<br>"total": 213123,<br>"currency": "CNY",<br>"begin_comment": "资源已创建，开始计费",<br>"end_comment": "资源已销毁，停止计费",<br>"begin_at": 1545356857,<br>"end_at": 1545363489,<br>"price": 1.23<br>}<br>]<br>}<br>]<br>\} |  |  |  |

### 查询指定资源费用账单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/cloudkitty/v1/cloudos/cloudkitty/{service_uuid}/bills/{resource_uuid\} | JSON | GET | 查询指定资源费用账单 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| serviceUuid | true | String | 资源服务uuid |
| resourceUuid | true | String | 资源uuid |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"resources": [<br>{<br>"uuid": "ccf33874-697c-4920-94d0-6ed9ad964189",<br>"name": "主机1",<br>"tenant_id": "9052ae85792143ff955c35c52e8e41bd",<br>"price": 1,<br>"finished": false,<br>"deleted": false,<br>"detail": [<br>{<br>"begin": "2018-07-23T11:26:00",<br>"end": "2018-07-23T15:09:36.429",<br>"total": 213123,<br>"currency": "CNY",<br>"begin_comment": "资源已创建，开始计费",<br>"end_comment": "资源已销毁，停止计费",<br>"begin_at": 1545356857,<br>"end_at": 1545363489,<br>"price": 1.23<br>}<br>]<br>}<br>]<br>\} |  |  |  |

## 流程

### 获取流程列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes | JSON | GET | 获取流程列表 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| status | false | eaum: PROCESSING | 待审批状态 |
| nextCandidateUser | false | String: uuid | 审批人uuid |
| 例如：<br>http://10.125.32.144:11000/sys/bpm/processes?<br>status=PROCESSING&nextCandidateUser=1df71957d770436493a84f24d9344853 |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"id": "3ec42f4bacac4467bf340349cb6d92a6",<br>"name": "申请硬盘-b",<br>"description": null,<br>"status": "PROCESSING",<br>"project": "9052ae85792143ff955c35c52e8e41bd",<br>"template_id": "320e93684cca4b46b51f7ce66b2cb313",<br>"create_user": {<br>"id": "167f9c7a67cc49b9b46ac8172b073e48",<br>"name": "putong"<br>},<br>"resource_action_key": "cinder_volume_apply",<br>"level_total": 1,<br>"level_now": 1,<br>"create_time": "2020-01-02 15:06:54",<br>"update_time": "2020-01-02 15:08:18",<br>"next_candidate_users": [<br>{<br>"id": "1df71957d770436493a84f24d9344853",<br>"name": "admin"<br>}<br>]<br>}<br>],<br>"msg": null,<br>"total": 1<br>\} |  |  |  |

### 获取流程详情

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id\} | JSON | GET | 获取流程详情 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":  {<br>"id": "6a4b1486-729f-44a3-bd79-43b101bb7def",<br>"name": "流程名",<br>"template_id": "487e6a4fd1bc4d42bc4672f9a3dd0dd2",<br>"description": "流程描述",<br>"create_user": {<br>"id": "5163dcae-a406-4b3c-9862-e6923119b2c9",<br>"name": "lhw"<br>},<br>"resource_action_key": "nova_host_create",<br>"level_total": 3,<br>"level_now": 2,<br>"variables": [<br>{<br>"key": "name",<br>"labelName": "cpu",<br>"value": "4核",<br>"render": true<br>}<br>],<br>"extra": {<br>"key1": "value1",<br>"key2": "value2"<br>},<br>"next_candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "admin"<br>}<br>],<br>"last_approve_user": {<br>"id": "3b6458248afb446d99b103ad8db6e828",<br>"name": "lhw"<br>},<br>"status": "PROCESSING",<br>"result": "PASS",<br>"logs": [<br>{<br>"level": 1,<br>"user": "lhw",<br>"type": "PASS",<br>"description": "同意申请",<br>"args": "ok",<br>"time": "2018-03-28T17:45:23.346+08:00"<br>}<br>],<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 创建流程

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes | JSON | POST | 创建流程 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| 例如：<br>{<br>"process": {<br>"name": "流程名",<br>"description": "流程描述",<br>"create_user": {<br>"id": "5163dcae-a406-4b3c-9862-e6923119b2c9",<br>"name": "lhw"<br>},<br>"resource_action_key": "nova_host_create",<br>"variables": [<br>{<br>"key": "name",<br>"labelName": "cpu",<br>"value": "4核",<br>"render": true<br>}<br>],<br>"extra": {<br>"key1": "value1",<br>"key2": "value2"<br>}<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":  {<br>"id": "6a4b1486-729f-44a3-bd79-43b101bb7def",<br>"name": "流程名",<br>"template_id": "487e6a4fd1bc4d42bc4672f9a3dd0dd2",<br>"description": "流程描述",<br>"create_user": {<br>"id": "5163dcae-a406-4b3c-9862-e6923119b2c9",<br>"name": "lhw"<br>},<br>"resource_action_key": "nova_host_create",<br>"level_total": 3,<br>"level_now": 2,<br>"variables": [<br>{<br>"key": "name",<br>"labelName": "cpu",<br>"value": "4核",<br>"render": true<br>}<br>],<br>"extra": {<br>"key1": "value1",<br>"key2": "value2"<br>},<br>"next_candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "admin"<br>}<br>],<br>"last_approve_user": {<br>"id": "3b6458248afb446d99b103ad8db6e828",<br>"name": "lhw"<br>},<br>"status": "PROCESSING",<br>"result": "PASS",<br>"logs": [<br>{<br>"level": 1,<br>"user": "lhw",<br>"type": "PASS",<br>"description": "同意申请",<br>"args": "ok",<br>"time": "2018-03-28T17:45:23.346+08:00"<br>}<br>],<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 删除流程

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id\} | JSON | DELETE | 删除流程 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": "string"<br>\} |  |  |  |

### 流程审批

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id}/approve | JSON | PUT | 流程审批 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| pass | 是 | boolen | true, false | 同意/驳回审批 |
| message | 是 | string | 0-128字符 | 处理意见 |
| 例如：<br>{<br>"approval": {<br>"message": "通过",<br>"pass": true<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": "string"<br>\} |  |  |  |

### 流程批量审批

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/ | JSON | POST | 流程批量审批 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| pass | 是 | boolen | true, false | 同意/驳回审批 |
| message | 是 | string | 0-128字符 | 处理意见 |
| processIds | 是 | String | 用逗号分隔 | 流程id |
| 例如：<br>{<br>"approval": {<br>"message": "通过",<br>"pass": true<br>}，<br>"processIds":"0b7683f9a44d4818bb613e50431d52b3,0b7683f9a44d4818bb613e50431d52b3"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": "string"<br>\} |  |  |  |

### 更改责任人

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id}/transfer | JSON | PUT | 更改责任人 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"transfer": {<br>"candidates": [<br>{<br>"id": "4740b438-d7fa-43e6-a31a-db8f52035d37",<br>"name": "lhw"<br>}<br>]<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": "string"<br>\} |  |  |  |

### 关闭流程

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id}/close | JSON | PUT | 关闭流程 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": "string"<br>\} |  |  |  |

### 根据id获取流程的参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id}/variables | JSON | GET | 根据id获取流程的参数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":  [<br>{<br>"key": "name",<br>"labelName": "cpu",<br>"value": "4核",<br>"render": true<br>}<br>],<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 更新流程参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id}/variables | JSON | POST | 更新流程参数 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"variables": [<br>{<br>"key": "name",<br>"labelName": "cpu",<br>"value": "4核",<br>"render": true<br>}<br>] ,<br>"extra": {}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 修改流程参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id}/variables | JSON | PUT | 修改流程参数 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"variables": [<br>{<br>"key": "name",<br>"labelName": "cpu",<br>"value": "4核",<br>"render": true<br>}<br>]<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 删除流程参数

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id}/variables/{key\} | JSON | DELETE | 删除流程参数 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 根据id获取流程的日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/{id}/logs | JSON | GET | 根据id获取流程的日志 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"level": 1,<br>"user": "lhw",<br>"type": "PASS",<br>"description": "同意申请",<br>"args": "ok",<br>"time": "2018-03-28T17:45:23.346+08:00"<br>}<br>],<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 获取流程模板列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/templates | JSON | GET | 获取流程模板列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "创建主机模板",<br>"description": "主机创建相关流程模板",<br>"level": 5,<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"status": "ACTIVE",<br>"display": true<br>}<br>],<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 根据id获取流程模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/templates/{id\} | JSON | GET | 根据id获取流程模板 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "创建主机模板",<br>"description": "主机创建相关流程模板",<br>"level": 5,<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"candidate_users": {<br>"level1": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level2": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level3": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level4": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level5": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>]<br>},<br>"resource_actions": [<br>"nova_host_create"<br>],<br>"display": true,<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"status": "ACTIVE"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 创建流程模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/templates | JSON | POST | 创建流程模板 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| name | 是 | string | 2-15个字符 | 名称 |
| level | 是 | string | 数字1-9 | 审批等级 |
| projects | 是 | object | 组织参数取值 | 组织信息 |
| candidate_users | 是 | object | 用户参数取值 | 各级审批人信息 |
| resource_actions | 是 | Array[string] | 资源操作取值 | 资源操作类型 |
| 例如：<br>{<br>"template": {<br>"name": "创建主机模板",<br>"description": "主机创建相关流程模板",<br>"level": 5,<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"candidate_users": {<br>"level1": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level2": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level3": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level4": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level5": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>]<br>},<br>"resource_actions": [<br>"nova_host_create"<br>],<br>"display": true<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "创建主机模板",<br>"description": "主机创建相关流程模板",<br>"level": 5,<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"candidate_users": {<br>"level1": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level2": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level3": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level4": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level5": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>]<br>},<br>"resource_actions": [<br>"nova_host_create"<br>],<br>"display": true,<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"status": "ACTIVE"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 修改流程模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/templates/{id\} | JSON | PUT | 修改流程模板 |

###### 请求体(from-data)

| 参数名 | 必选 |  | 类型及范围 |  | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| name | 是 |  | string |  | 2-15个字符 | 名称 |
| level | 是 |  | string |  | 数字1-9 | 审批等级 |
| projects | 是 |  | object |  | 组织参数取值 | 组织信息 |
| candidate_users | 是 |  | object |  | 用户参数取值 | 各级审批人信息 |
| resource_actions | 是 |  | Array[string] |  | 资源操作取值 | 资源操作类型 |
| 例如：<br>{<br>"template": {<br>"name": "主机模板",<br>"description": "主机创建相关流程模板",<br>"level": 5,<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"candidate_users": {<br>"level1": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level2": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level3": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level4": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level5": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>]<br>},<br>"resource_actions": [<br>"nova_host_create"<br>],<br>"display": true<br>}<br>\} |  |  |  |  |  |  |
| 参数名 |  | 必选 |  | 类型及范围 |  | 说明 |
| 例如：<br>{<br>"template": {<br>"name": "创建主机模板",<br>"description": "主机创建相关流程模板",<br>"level": 5,<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"candidate_users": {<br>"level1": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level2": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level3": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level4": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"level5": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>]<br>},<br>"resource_actions": [<br>"nova_host_create"<br>],<br>"display": true<br>}<br>\} |  |  |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机模板",<br>"description": "主机相关问题模板",<br>"start_group": "ROLE_USER",<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"categories": [<br>{<br>"id": "9fc5d650-c701-4ce3-94cc-f3050389b3ed",<br>"name": "主机相关问题"<br>}<br>],<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"status": "ACTIVE"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 删除流程模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/templates/{id\} | JSON | DELETE | 删除流程模板 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 激活模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/templates/{id}/active | JSON | PUT | 激活模板 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 挂起模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/templates/{id}/suspend | JSON | PUT | 挂起模板 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 获取资源操作类型（树状）

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/actionTree | JSON | GET | 获取资源操作类型（树状） |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"href": "allnodes",<br>"text": "所有类型",<br>"textEN": "virgo.process.all.nodes",<br>"state": {<br>"checked": false,<br>"midChecked": false,<br>"selectable": false<br>},<br>"nodes": [<br>{<br>"href": "资源",<br>"text": "资源",<br>"textEN": "cloudmp.resource.action.resource",<br>"state": {<br>"checked": false,<br>"midChecked": false,<br>"selectable": false<br>},<br>"nodes": [<br>{<br>"href": "resource_apply",<br>"text": "申请资源",<br>"textEN": "resource.apply",<br>"state": {<br>"checked": false,<br>"midChecked": false,<br>"selectable": false<br>},<br>"nodes": null<br>}<br>]<br>}<br>]<br>},<br>"msg": null<br>\} |  |  |  |

### 添加资源操作类型

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/actions | JSON | POST | 添加资源操作类型 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| 例如：<br>{<br>"key": "nova_host_create",<br>"name": "创建主机",<br>"is8n": "create nova host",<br>"resource": "云主机",<br>"is8nResource": "nova host"<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"key": "nova_host_create",<br>"name": "创建主机",<br>"is8n": "create nova host",<br>"resource": "云主机",<br>"is8n_resource": "nova host"<br>},<br>"msg": null<br>\} |  |  |  |

### 获取资源操作类型

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/processes/actions | JSON | GET | 获取资源操作类型 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"key": "nova_host_create",<br>"name": "创建主机",<br>"is8n": "sorb.action.nova.host.create",<br>"resource": "nova_host",<br>"is8n_resource": "sorb.action.nova.host"<br>}<br>],<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

## 工单

### 获取工单列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets | JSON | GET | 获取工单列表 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如： |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"worksheets": [<br>{<br>"id": "worksheet-1",<br>"worksheet_number": "2018041214518898",<br>"start_user": {<br>"id": "beb1d04d-6f8e-47fc-b121-013f59d927e2",<br>"name": "user-1"<br>},<br>"start_time": "2018-03-28T17:25:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"tenant_id": "f3693c6a-205a-440c-82e1-0f4c857da241",<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"assignee": {<br>"id": "59646c86-8ae5-4dd7-8c28-153ef883745b",<br>"name": "lxz"<br>},<br>"category": {<br>"id": "304da055-1085-45b2-8001-9c72fc1ebb0d",<br>"name": "主机相关问题"<br>},<br>"desciption": "申请不了主机",<br>"status": "SUBMITTING"<br>}<br>]<br>}<br>],<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 根据id获取工单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id\} | JSON | GET | 根据id获取工单 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"id": "worksheet-1",<br>"worksheet_number": "2018041214518898",<br>"start_user": {<br>"id": "beb1d04d-6f8e-47fc-b121-013f59d927e2",<br>"name": "user-1"<br>},<br>"start_time": "2018-03-28T17:25:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"tenant_id": "f3693c6a-205a-440c-82e1-0f4c857da241",<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"assignee": {<br>"id": "59646c86-8ae5-4dd7-8c28-153ef883745b",<br>"name": "lxz"<br>},<br>"category": {<br>"id": "304da055-1085-45b2-8001-9c72fc1ebb0d",<br>"name": "主机相关问题"<br>},<br>"description": "申请不了主机",<br>"status": "SUBMITTING",<br>"variables": [<br>{<br>"key": "name",<br>"value": "liaohaowei"<br>}<br>],<br>"messages": [<br>{<br>"text": "This is a message on the task.",<br>"author": "admin",<br>"time": "2014-07-13T13:13:52.232+08:00"<br>}<br>],<br>"comments": [<br>{<br>"text": "This is a comment on the task.",<br>"author": "lhw",<br>"score": 5,<br>"time": "2014-07-13T13:13:52.232+08:00"<br>}<br>],<br>"attachments": [<br>{<br>"attachment_id": "attachment-1",<br>"name": "Simple attachment",<br>"description": "Simple attachment description",<br>"time": "2014-07-13T13:13:52.232+08:00"<br>}<br>]<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 创建工单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets | JSON | POST | 创建工单 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| category | 是 | object | 问题类型参数 | 问题类型 |
| description | 是 | string | 0-128个字符 | 描述 |
| 例如：<br>{<br>"worksheet": {<br>"start_user": {<br>"id": "beb1d04d-6f8e-47fc-b121-013f59d927e2"<br>},<br>"category": {<br>"id": "304da055-1085-45b2-8001-9c72fc1ebb0d"<br>},<br>"description": "申请不了主机",<br>"variables": [<br>{<br>"key": "name",<br>"value": "liaohaowei"<br>}<br>]<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":{<br>"id": "worksheet-1",<br>"worksheet_number": "2018041214518898",<br>"start_user": {<br>"id": "beb1d04d-6f8e-47fc-b121-013f59d927e2",<br>"name": "user-1"<br>},<br>"start_time": "2018-03-28T17:25:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"tenant_id": "f3693c6a-205a-440c-82e1-0f4c857da241",<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"assignee": {<br>"id": "59646c86-8ae5-4dd7-8c28-153ef883745b",<br>"name": "lxz"<br>},<br>"category": {<br>"id": "304da055-1085-45b2-8001-9c72fc1ebb0d",<br>"name": "主机相关问题"<br>},<br>"description": "申请不了主机",<br>"status": "SUBMITTING",<br>"variables": [<br>{<br>"key": "name",<br>"value": "liaohaowei"<br>}<br>]<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 删除工单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id\} | JSON | DELETE | 删除工单 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 完成工单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/complete | JSON | PUT | 完成工单 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 撤销工单

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/rescind | JSON | PUT | 撤销工单 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 给工单添加答复

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/reply | JSON | PUT | 给工单添加答复 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| message | 是 | string | 0-500个字符 | 回复内容 |
| 例如：<br>{<br>"reply": {<br>"message": "This is a reply on the worksheet."<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 转发工单,更改责任人

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/transfer | JSON | PUT | 转发工单,更改责任人 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"transfer": {<br>"candidates": [<br>{<br>"id": "4740b438-d7fa-43e6-a31a-db8f52035d37",<br>"name": "lhw"<br>}<br>]<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 根据id获取工单的评论

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/comments | JSON | GET | 根据id获取工单的评论 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"text": "This is a comment on the task.",<br>"author": "lhw",<br>"score": 5,<br>"time": "2014-07-13T13:13:52.232+08:00"<br>}<br>]，<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 给工单添加评论

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/comments | JSON | POST | 给工单添加评论 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"comment": {<br>"text": "This is a comment on the task.",<br>"author": "lhw",<br>"score": 5<br>}<br>\} |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":{<br>"text": "This is a comment on the task.",<br>"author": "lhw",<br>"score": 5,<br>"time": "2014-07-13T13:13:52.232+08:00"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 给工单添加附件

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/attachments | JSON | POST | 给工单添加附件 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 说明 |
| --- | --- | --- | --- |
| file | true | file(formData) | 上传的文件 |
| description | true | string(formData) | 文件描述 |
| 例如： |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":{<br>"attachment_id": "attachment-1",<br>"name": "Simple attachment",<br>"description": "Simple attachment description",<br>"time": "2014-07-13T13:13:52.232+08:00"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 下载工单附件

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/attachments/{attachment_id\} | JSON | GET | 下载工单附件 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Content-Type | string | 类型为application/octet-stream | 类型为application/octet-stream |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 工单附件列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/attachments | JSON | GET | 工单附件列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Content-Type | string | 类型为application/octet-stream | 类型为application/octet-stream |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"name": "2019.xls",<br>"description": "file",<br>"time": null,<br>"attachment_id": "2519"<br>}<br>],<br>"msg": null<br>\} |  |  |  |

### 删除工单附件

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/{id}/attachments/{attachment_id\} | JSON | DELETE | 删除工单附件 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 获取工单问题分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/categories | JSON | GET | 获取工单问题分类 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"categories": [<br>{<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机相关问题",<br>"description": "主机相关问题",<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"display": "SHOW",<br>"extra": ""<br>}<br>]<br>}<br>],<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 根据id获取工单问题分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/categories/{id\} | JSON | GET | 根据id获取工单问题分类 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"category": {<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机相关问题",<br>"description": "主机相关问题",<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"display": "SHOW",<br>"extra": ""<br>}<br>}<br>],<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 添加工单问题分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/categories | JSON | POST | 添加工单问题分类 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| name | 是 | string | 2-15个字符 | 问题名称 |
| description | 是 | string | 0-128个字符 | 描述 |
| 例如：<br>{<br>"category": {<br>"name": "主机相关问题",<br>"description": "主机相关问题",<br>"extra": ""<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机相关问题",<br>"description": "主机相关问题",<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"display": "SHOW",<br>"extra": ""<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 修改工单问题分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/categories/{id\} | JSON | PUT | 修改工单问题分类 |

###### 请求体

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| name | 是 | string | 2-15个字符 | 问题名称 |
| description | 是 | string | 0-128个字符 | 描述 |
| 例如：<br>{<br>"category": {<br>"name": "主机相关问题",<br>"description": "主机相关问题",<br>"extra": ""<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机相关问题",<br>"description": "主机相关问题",<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"display": "SHOW",<br>"extra": ""<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 删除问题分类

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/categories/{id\} | JSON | DELETE | 删除问题分类 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 获取工单模板列表

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/templates | JSON | GET | 获取工单模板列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": [<br>{<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机模板",<br>"description": "主机相关问题模板",<br>"start_group": "ROLE_USER",<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"status": "ACTIVE"<br>}<br>],<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 根据id获取工单模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/templates/{id\} | JSON | GET | 根据id获取工单模板 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":{<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机模板",<br>"description": "主机相关问题模板",<br>"start_group": "ROLE_USER",<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"categories": [<br>{<br>"id": "9fc5d650-c701-4ce3-94cc-f3050389b3ed",<br>"name": "主机相关问题"<br>}<br>],<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"status": "ACTIVE"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 创建工单模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/templates | JSON | POST | 创建工单模板 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| name | 是 | string | 2-15个字符 | 模板名称 |
| description | 是 | string | 0-128个字符 | 描述 |
| start_group | 是 | string | enum: ROLE_ORG, ROLE_USER | 角色视图 |
| projects | 是 | Array[object] | 组织参数值 | 组织 |
| categories | 是 | Array[object] | 问题类型参数 | 问题类型 |
| candidate_users | 是 | Array[object] | 责任人参数 | 责任人 |
| 例如：<br>{<br>"template": {<br>"name": "主机模板",<br>"description": "主机相关问题模板",<br>"start_group": "ROLE_USER",<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"categories": [<br>{<br>"id": "9fc5d650-c701-4ce3-94cc-f3050389b3ed",<br>"name": "主机相关问题"<br>}<br>],<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>]<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":{<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机模板",<br>"description": "主机相关问题模板",<br>"start_group": "ROLE_USER",<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"categories": [<br>{<br>"id": "9fc5d650-c701-4ce3-94cc-f3050389b3ed",<br>"name": "主机相关问题"<br>}<br>],<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"status": "ACTIVE"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 修改工单模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/templates/{id\} | JSON | PUT | 修改工单模板 |

###### 请求体(from-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| name | 是 | string | 2-15个字符 | 模板名称 |
| description | 是 | string | 0-128个字符 | 描述 |
| start_group | 是 | string | enum: ROLE_ORG, ROLE_USER | 角色视图 |
| projects | 是 | Array[object] | 组织参数值 | 组织 |
| categories | 是 | Array[object] | 问题类型参数 | 问题类型 |
| candidate_users | 是 | Array[object] | 责任人参数 | 责任人 |
| 例如：<br>{<br>"template": {<br>"name": "主机模板",<br>"description": "主机相关问题模板",<br>"start_group": "ROLE_USER",<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"categories": [<br>{<br>"id": "9fc5d650-c701-4ce3-94cc-f3050389b3ed",<br>"name": "主机相关问题"<br>}<br>],<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>]<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data": {<br>"id": "4f390664-4bed-4db8-baeb-62a7d4b1f031",<br>"name": "主机模板",<br>"description": "主机相关问题模板",<br>"start_group": "ROLE_USER",<br>"projects": [<br>{<br>"id": "8293ba20-60f1-4d17-93c1-44cfc10e582f",<br>"name": "lhw-project"<br>}<br>],<br>"categories": [<br>{<br>"id": "9fc5d650-c701-4ce3-94cc-f3050389b3ed",<br>"name": "主机相关问题"<br>}<br>],<br>"candidate_users": [<br>{<br>"id": "b904e4b5-1d9f-5239-9846-0e9762ffd60c",<br>"name": "lhw"<br>}<br>],<br>"create_time": "2018-03-28T17:45:23.346+08:00",<br>"update_time": "2018-03-28T17:45:23.346+08:00",<br>"type": "INTERNAL",<br>"status": "ACTIVE"<br>},<br>"msg": null,<br>"total": 0<br>\} |  |  |  |

### 删除工单模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/templates/{id\} | JSON | DELETE | 删除工单模板 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 激活模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/templates/{id}/active | JSON | PUT | 激活模板 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 挂起模板

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/bpm/worksheets/templates/{id}/suspend | JSON | PUT | 挂起模板 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": "string",<br>"msg": "string",<br>"data": null<br>\} |  |  |  |

### 获取工单操作日志信息

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /worksheets/templates/logs/{id\} | JSON | GET | 获取工单操作日志信息 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"code": null,<br>"data":"{\"status\":true,\"auth\":true,\"code\":\"0\",\"res\":{\"total\":1,\"totalFailure\":0,\"totalSuccess\":1,\"datas\":[{\"uuid\":\"f0fa48d5-92f0-4c09-97f1-df6abb8f5876\",\"level\":\"INFO\",\"tags\":\"[\\\"\\\\u521B\\\\u5EFA\\\\u5DE5\\\\u5355\\\\u6A21\\\\u677F\\\"]\",\"action\":\"{\\\"result\\\":{\\\"message\\\":\\\"操作完成\\\",\\\"value\\\":\\\"SUCCESS\\\"},\\\"parameter\\\":{\\\"key\\\":\\\"value\\\"},\\\"description\\\":\\\"创建工单模板\\\",\\\"type\\\":\\\"createWorksheetTemplate\\\"}\",\"associateRes\":\"[{\\\"description\\\":\\\"test\\\",\\\"uuid\\\":\\\"a53eb3c53f004cbb92bf6651e5769509\\\"}]\",\"associateUser\":\"{\\\"name\\\":\\\"admin\\\",\\\"type\\\":\\\"ADMIN\\\",\\\"uuid\\\":\\\"1df71957d770436493a84f24d9344853\\\"}\",\"operatorId\":\"1df71957d770436493a84f24d9344853\",\"operatorName\":\"admin\",\"result\":\"SUCCESS\",\"actionDescription\":\"创建工单模板\",\"actionDescriptionEn\":\"创建工单模板\",\"timestamp\":\"2020-10-10T14:45:34.034+0800\",\"localIp\":\"10.125.108.93\"}]}}",<br>"msg": null<br>\} |  |  |  |

## 操作日志

### 查询日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/logging/v1/messages | JSON | GET | 查询日志列表 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| marker | 否 | string | 操作日志id | 从当前记录之前/后查询（order值判断，不含该日志） |
| limit | 否 | integer | 1-100000 | 获取的结果集的最大数量 |
| resource | 否 | string | 资源类型 | 查询相关资源的操作日志 |
| order | 否 | string | ASC,DESC | 排序方式，ASC表示升序，DESC表示降序。区分大小写 |
| 例如： |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"record_logs": [<br>{<br>"uuid": "baae5505-cc96-4cf1-ad5a-a87285af90ae",<br>"level": "INFO",<br>"tags": [<br>"sample"<br>],<br>"associate_res": [<br>{<br>"uuid": "baae5505-cc96-4cf1-ad5a-a87285af90ae",<br>"description": "一些描述",<br>"target": "../../hello",<br>"user_uuid": "ccae5505-cc96-4cf1-ad5a-a87285af90ae"<br>}<br>],<br>"associate_user": {<br>"uuid": "aaae5505-cc96-4cf1-ad5a-a87285af90ae",<br>"name": "admin",<br>"type": "PUBLIC"<br>},<br>"action": {<br>"type": "CREATE_IMAGE",<br>"description": "创建镜像",<br>"parameter": {<br>"key": "value"<br>},<br>"result": {<br>"value": "DONE",<br>"message": "操作完成",<br>"reference": "../../../hello"<br>}<br>},<br>"reference_log": [<br>"baae5505-cc96-4cf1-ad5a-a87285af90ae"<br>],<br>"local_ip": "192.0.0.1",<br>"timestamp": "2017-01-01T10:15:16:50.000+08:00"<br>}<br>]<br>\} |  |  |  |

### 查询指定日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/logging/v1/messages/{uuid\} | JSON | GET | 查询指定日志 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | 是 | string | 操作日志id | 指定日志uuid |
| 例如： |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"record_log": {<br>"uuid": "01a2b458-4301-467f-92f2-619c58f36a62",<br>"level": "INFO",<br>"tags": "[\"releasePackage\",\"SUCCESS\",\"application-server-operation\"]",<br>"action": "{\"result\":{\"message\":\"\",\"value\":\"SUCCESS\"},\"parameter\":{},\"description\":\"部署安装包\",\"type\":\"releasePackage\"}",<br>"actionType": "releasePackage",<br>"associateRes": "[{\"user_uuid\":\"1df71957d770436493a84f24d9344853\",\"description\":\"\",\"uuid\":\"\",\"target\":\"\"}]",<br>"associateUser": "{\"name\":\"admin\",\"type\":\"PUBLIC\",\"uuid\":\"1df71957d770436493a84f24d9344853\"}",<br>"operatorId": "1df71957d770436493a84f24d9344853",<br>"operatorName": "admin",<br>"result": "SUCCESS",<br>"actionDescription": "部署安装包",<br>"actionDescriptionEn": "部署安装包",<br>"referenceLog": "[]",<br>"timestamp": "2020-03-09T15:07:48.048+0800",<br>"localIp": "10.125.104.95"<br>}<br>\} |  |  |  |

### 删除指定日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/logging/v1/messages/{uuid\} | JSON | DELETE | 删除指定日志 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| uuid | 是 | string | 操作日志id | 指定日志uuid |

###### 正常返回结果

| 返回键 | 值 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| Status Code | 200 | OK |  |
| Status Code | 400 | 删除失败 |  |
| Status Code | 404 | 未找到指定的日志 |  |

### 查询所有日志

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/logging/v1/messages/all | JSON | GET | 查询素有日志列表 |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"record_logs": [<br>{<br>"uuid": "baae5505-cc96-4cf1-ad5a-a87285af90ae",<br>"level": "INFO",<br>"tags": [<br>"sample"<br>],<br>"associate_res": [<br>{<br>"uuid": "baae5505-cc96-4cf1-ad5a-a87285af90ae",<br>"description": "一些描述",<br>"target": "../../hello",<br>"user_uuid": "ccae5505-cc96-4cf1-ad5a-a87285af90ae"<br>}<br>],<br>"associate_user": {<br>"uuid": "aaae5505-cc96-4cf1-ad5a-a87285af90ae",<br>"name": "admin",<br>"type": "PUBLIC"<br>},<br>"action": {<br>"type": "CREATE_IMAGE",<br>"description": "创建镜像",<br>"parameter": {<br>"key": "value"<br>},<br>"result": {<br>"value": "DONE",<br>"message": "操作完成",<br>"reference": "../../../hello"<br>}<br>},<br>"reference_log": [<br>"baae5505-cc96-4cf1-ad5a-a87285af90ae"<br>],<br>"local_ip": "192.0.0.1",<br>"timestamp": "2017-01-01T10:15:16:50.000+08:00"<br>}<br>]<br>\} |  |  |  |

### 添加日志记录（仅限服务使用）

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/logging/v1/messages | JSON | POST | 添加日志记录（仅限服务使用） |

###### 请求体(form-data)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| level | 是 | string | Enum:INFO, WARN, ERROR | 日志级别 |
| action | 是 | object | type:操作类型（必填）<br>description:操作描述(必填)<br>result: SUCCESS, FAILURE, DONE（必填）<br>parameter:其他参数等 | 操作 |
| tags | 否 | Array[string] | 自定义标签值 | 日志的标签信息 |
| associate_user | 是 | object | "type" : ADMIN, INTERNAL, PUBLIC<br>"uuid" : 用户uuid,<br>"name" : 用户name | 日志关联操作者信息 |
| associate_res | 是 | Array[object] | "uuid":用户uuid,<br>"description" :资源描述， | 日志针对的资源,如无相关资源，数组为空即可 |
| reference_log | 否 | Array[string] | 日志uuid | 引用的日志 |
| local_ip | 是 | string | Ip地址格式 | 操作者的ip |
| timestamp | 是 | string | 时间格式 | 日志记录时间 |
| 例如：<br>{<br>"record_log" : {<br>"level" : "INFO",<br>"action" : {<br>"type" : "execute_process",<br>"description" : "virgo.bpm.apply.reply",<br>"parameter" : {<br>"operate" : "true",<br>"processName" : "默认流程.申请云主机-qq1111",<br>"processInstanceUUID" : "31d29a67-cc43-45b7-adb4-2735d578078a",<br>"taskUUID" : "1-1-task-2",<br>"value" : "QQ123",<br>"user" : null,<br>"opinion" : "1"<br>},<br>"result" : {<br>"value" : "SUCCESS",<br>"message" : "virgo.bpm.executeProcess.success"<br>}<br>},<br>"tags" : [ "execute", "process", "SUCCESS" ],<br>"associate_user" : {<br>"type" : "PUBLIC",<br>"uuid" : "a06f059a-1c62-4629-ac74-7ae64f8b5466",<br>"name" : "zm2o1"<br>},<br>"associate_res" : [ {<br>"uuid" : "31d29a67-cc43-45b7-adb4-2735d578078a",<br>"description" : "默认流程.申请云主机-qq1222"<br>} ],<br>"reference_log" : [ ],<br>"timestamp" : "2020-03-18 16:02:35",<br>"local_ip" : "10.125.104.148"<br>}<br>\} |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"record_log": {<br>"uuid": "a77afa02-bcae-4c46-8dad-a788a4d395c1",<br>"level": "INFO",<br>"tags": "[\"execute\",\"process\",\"SUCCESS\"]",<br>"action": "{\"type\":\"execute_process\",\"description\":\"virgo.bpm.apply.reply\",\"parameter\":{\"operate\":\"true\",\"processName\":\"默认流程.申请云主机-qq1111\",\"processInstanceUUID\":\"31d29a67-cc43-45b7-adb4-2735d578078a\",\"taskUUID\":\"1-1-task-2\",\"value\":\"QQ123\",\"user\":null,\"opinion\":\"1\"},\"result\":{\"value\":\"SUCCESS\",\"message\":\"virgo.bpm.executeProcess.success\"}}",<br>"actionType": "execute_process",<br>"associateRes": "[{\"uuid\":\"31d29a67-cc43-45b7-adb4-2735d578078a\",\"description\":\"默认流程.申请云主机-qq1222\"}]",<br>"associateUser": "{\"type\":\"PUBLIC\",\"uuid\":\"a06f059a-1c62-4629-ac74-7ae64f8b5466\",\"name\":\"zm2o1\"}",<br>"operatorId": "a06f059a-1c62-4629-ac74-7ae64f8b5466",<br>"operatorName": "zm2o1",<br>"result": "SUCCESS",<br>"actionDescription": "virgo.bpm.apply.reply",<br>"actionDescriptionEn": "virgo.bpm.apply.reply",<br>"referenceLog": "[]",<br>"timestamp": "2020-03-18T16:02:35.035+0800",<br>"localIp": "10.125.104.148"<br>}<br>}<br>\} |  |  |  |

### 查询日志（条件查询分页接口）

| URL | 支持格式 | HTTP请求方式 | 说明 |
| --- | --- | --- | --- |
| /sys/logging/v1/messages/page | JSON | GET | 查询日志列表 |

###### 查询参数(Params)

| 参数名 | 必选 | 类型及范围 | 参数取值范围 | 说明 |
| --- | --- | --- | --- | --- |
| order | 否 | string | ASC,DESC | 排序方式，ASC表示升序，DESC表示降序。区分大小写 |
| sortField | 否 | string | enum: operatorName,localIp,actionDescription, resourceName, timestamp,level, actionResult | 按该参数类型排序 |
| startRow | 否 | integer | 起始条数 | 起始条数 |
| pageSize | 否 | integer | 查询条数 | 查询条数 |
| operatorName | 否 | string | 用户name | 用户名 |
| localIp | 否 | string | Ip地址 | IP地址 |
| resourceName | 否 | string | 资源对象name | 资源对象 |
| actionDescription | 否 | string | 操作描述 | 操作 |
| level | 否 | string | enum:INFO, WARN, ERROR | 级别 |
| result | 否 | string | enum: SUCCESS, FAILURE, DONE | 结果 |
| startTime | 否 | string | 日期格式：2020-03-12 | 起始日期 |
| endTime | 否 | string | 日期格式：2020-03-13 | 结束日期 |
| 例如： |  |  |  |  |

###### 正常返回结果

| 返回键 | 类型 | 返回值含义 | 说明 |
| --- | --- | --- | --- |
| 例如：<br>{<br>"status": true,<br>"auth": true,<br>"code": "0",<br>"res": {<br>"total": 1,<br>"totalFailure": 0,<br>"totalSuccess": 1,<br>"datas": [<br>{<br>"uuid": "e026f072-4af5-4ba6-b00f-667c65c45456",<br>"level": "INFO",<br>"tags": "[\"doubleFactorCheck\",\"SUCCESS\",\"usr-mgt-operation\"]",<br>"action": "{\"result\":{\"message\":null,\"value\":\"SUCCESS\"},\"parameter\":{},\"description\":\"用户登录\",\"type\":\"doubleFactorCheck\"}",<br>"associateRes": "[{\"user_uuid\":\"1df71957d770436493a84f24d9344853\",\"description\":\"\",\"uuid\":\"\",\"target\":\"\"}]",<br>"associateUser": "{\"name\":\"admin\",\"type\":\"PUBLIC\",\"uuid\":\"1df71957d770436493a84f24d9344853\"}",<br>"operatorId": "1df71957d770436493a84f24d9344853",<br>"operatorName": "admin",<br>"result": "SUCCESS",<br>"actionDescription": "用户登录",<br>"actionDescriptionEn": "User Login",<br>"actionDescriptionLang": "{\"zh-CN\":{\"description\":\"用户登录\"},\"en\":{\"description\":\"User Login\"}}",<br>"referenceLog": "[]",<br>"timestamp": "2020-03-17T10:12:19.019+0800",<br>"localIp": "10.125.104.148"<br>}<br>]<br>}<br>\} |  |  |  |
