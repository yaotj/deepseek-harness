# springMVC helper

## 请求参数获取

URL地址占位符参数

```
@GetMapping(value = "/place/{name}")
public String pathVariable(@PathVariable String name) {
	return name;
}
```

------

获取n个非body参数

```
@RequestMapping(value = "/requestParam", method = {RequestMethod.GET, RequestMethod.POST})
public String requestParam(@RequestParam String name) {
	return name;
}
```

获取一组非body参数

```
@RequestMapping(value = "/requestForm", method = {RequestMethod.GET, RequestMethod.POST})
public Map<String, String> requestForm(@RequestParam Map<String, String> map) {
	return map;
}
```

获取body参数映射到map集合或者自定义Object

```
@PostMapping
public Map<String, String> health(@RequestBody Map<String, String> map) {
	return map;
}
```



## 请求头获取

获取http请求的header头数据

```
public Map<String, String> header(@RequestHeader("test") String test) {
	Map map = new HashMap();
	map.put("test", test);
	return map;
}
```



## 流

get请求，无输入，输出json数据

```
@GetMapping
public Map<String, String> health() {
	Map map = new HashMap();
	map.put("msg", "ok");
	return map;
}
```

请求/响应流

```
@PostMapping(value = "/stream")
public int stream(HttpServletResponse response, HttpServletRequest request) {
	response.setStatus(200);
	return 200;
}
```

文件上传

```
@PostMapping("/upload")
public Map<String, String> upload(String type, @RequestPart("file") MultipartFile file) {
	Map<String, String> map = new HashMap<>();
	map.put("type", type);
	if (file != null) {
	map.put("file", file.getOriginalFilename());
	map.put("size", String.valueOf(file.getSize()));
	}
	return map;
}
```

## 数据校验

```
import jakarta.validation.Valid;

@PostMapping
public Map<String, String> health(@Valid @RequestBody Person map) {
	return null;
}
```

```
import jakarta.validation.constraints.NotEmpty;

public class Person {
    @NotEmpty
    String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

常用的注解

Spring Validation（JSR 标准）

| 注解               | 作用                                           | 适用类型                             | 示例                                                         |
| ------------------ | ---------------------------------------------- | ------------------------------------ | ------------------------------------------------------------ |
| `@NotNull`         | 校验对象不为`null`（但可以是空字符串、空集合） | 任意类型                             | `@NotNull(message = "ID不能为空")`                           |
| `@NotBlank`        | 校验字符串不为`null`且去除首尾空格后长度 > 0   | 字符串                               | `@NotBlank(message = "名称不能为空")`                        |
| `@NotEmpty`        | 校验字符串 / 集合 / 数组不为`null`且长度 > 0   | 字符串、集合、数组                   | `@NotEmpty(message = "列表不能为空")`                        |
| `@Size`            | 校验字符串 / 集合 / 数组的长度范围             | 字符串、集合、数组                   | `@Size(min=2, max=10, message = "长度必须2-10")`             |
| `@Min`             | 校验数字（包括整数 / 浮点数）最小值            | 数字类型（int、long、BigDecimal 等） | `@Min(value=18, message = "年龄不能小于18")`                 |
| `@Max`             | 校验数字最大值                                 | 数字类型                             | `@Max(value=120, message = "年龄不能大于120")`               |
| `@DecimalMin`      | 校验数字最小值（支持小数，字符串形式表示）     | 数字类型、字符串                     | `@DecimalMin(value="0.01", message = "金额不能小于0.01")`    |
| `@DecimalMax`      | 校验数字最大值（支持小数）                     | 数字类型、字符串                     | `@DecimalMax(value="10000.00")`                              |
| `@Digits`          | 校验数字的整数位和小数位位数                   | 数字类型、字符串                     | `@Digits(integer=3, fraction=2, message = "最多3位整数，2位小数")` |
| `@Pattern`         | 校验字符串是否匹配正则表达式                   | 字符串                               | `@Pattern(regexp="^[0-9]{6}$", message = "必须是6位数字")`   |
| `@Email`           | 校验字符串是否为合法邮箱格式（宽松校验）       | 字符串                               | `@Email(message = "邮箱格式错误")`                           |
| `@Future`          | 校验日期 / 时间在当前时间之后                  | 日期类型（Date、LocalDateTime 等）   | `@Future(message = "时间必须在未来")`                        |
| `@FutureOrPresent` | 校验日期 / 时间在当前时间或之后                | 日期类型                             | `@FutureOrPresent()`                                         |
| `@Past`            | 校验日期 / 时间在当前时间之前                  | 日期类型                             | `@Past(message = "时间必须在过去")`                          |
| `@PastOrPresent`   | 校验日期 / 时间在当前时间或之前                | 日期类型                             | `@PastOrPresent()`                                           |

Hibernate Validator 扩展注解

| 注解                | 作用                                         | 适用类型             | 示例                                                         |
| ------------------- | -------------------------------------------- | -------------------- | ------------------------------------------------------------ |
| `@Length`           | 同`@Size`，专门用于字符串长度校验            | 字符串               | `@Length(min=2, max=10)`                                     |
| `@Range`            | 校验数字在指定范围内（支持整数 / 小数）      | 数字类型、字符串     | `@Range(min=1, max=100, message = "值必须1-100")`            |
| `@URL`              | 校验字符串是否为合法 URL                     | 字符串               | `@URL(message = "URL格式错误")`                              |
| `@CreditCardNumber` | 校验信用卡号（简单格式校验）                 | 字符串               | `@CreditCardNumber()`                                        |
| `@ScriptAssert`     | 通过脚本（如 EL 表达式）自定义校验逻辑       | 实体类（类级别注解） | `@ScriptAssert(lang="javascript", script="(_this.age > 18 && _this.idCard != null)", message = "成年必须填写身份证")` |
| `@NotEmptyList`     | 校验集合非空且至少有一个元素（Hibernate 6+） | 集合                 | `@NotEmptyList()`                                            |



------

# httpclient helper

## 代码示例

如果服务A需要http访问服务B，需要在服务A中定义，示例如下：

```
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.micro.web.global.ASimpleResultVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class ClientBByWebClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(ClientBWebClientDemo.class);

    public ClientBByWebClient(@Value("${other.service.b.url}") String baseUrl,
                                @Value("${other.service.b.openLogger}") boolean openLogger,
                                WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    public Map someRestCall() {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("userName", "tom");
        requestBody.put("userPwd", "123456");
        return postJsonAndGetResponse("/health", requestBody, Map.class);
    }

    public SimpleResultVo callOne() {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("userName", "tom");
        requestBody.put("userPwd", "123456");
        return postJsonAndGetResponse("/health", requestBody, SimpleResultVo.class);
    }

}
```



------

## 说明

父类有ProxyWebClient、ProxyOkHttp可用，建议统一使用ProxyWebClient。



构造方法的说明：

baseUrl：形如 http://localhost:8082 

openLogger：是否开启该组件的日志输出。

以上2个参数，均通过注解@Value("${}") 的方式从application.properties配置文件中获取。



postJsonAndGetResponse方法的入参：

requestBody是请求体，类型不仅限于Map。

------

# 定时任务

代码示例如下：

```
@Component
public class SimpleJob {

    public static Logger log = LoggerFactory.getLogger(SimpleJob.class);

//    @Scheduled(cron = "${job.a.cron}")
    @Scheduled(fixedDelay = 1000)
    @Async
    public void run() {
    
    }
}    
```

其中，@Scheduled指定触发规则，可以使用如下两种写法：

@Scheduled(fixedDelay = 1000) 表示间隔1秒执行一次任务。

@Scheduled(cron = "${job.a.cron}") 表示从配置文件的job.a.cron中获取值，值形如 0/10 * * * * ?

cron 比较灵活建议使用。fixedDelay 是固定间隔时间执行。它们都是到达触发条件就开启新的任务（不管上一个任务是否执行完毕）。

如果需要任务挨个顺序执行，可以在该类中添加属性作为任务完成状态的标记，每次任务开始的第一行代码都判断这个标记。

