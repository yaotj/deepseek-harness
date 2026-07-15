# 修复 fep-alipay-server 编译错误

## 问题概述
fep-alipay-server 编译失败，错误信息：`程序包lombok不存在`。新建的 Model/DTO 类使用了 Lombok 注解（`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`），但 `pom.xml` 中缺少 Lombok 依赖。

## 当前状态分析
- `fep-alipay-server/pom.xml` 当前包含：web、rpc、model、fastjson2、spring-boot-starter-test、junit
- 同项目其他模块（如 `acc-security-server`、`acc-es-server`）已在 `pom.xml` 中引入 Lombok
- 新建的 Model 类全部依赖 Lombok 注解生成 getter/setter/constructor

## 拟修复内容

### 1. 添加 Lombok 依赖
**文件：** `fep-alipay-server/pom.xml`
**操作：** 在 `<dependencies>` 中添加 Lombok 依赖，与同项目其他模块保持一致

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

## 验证步骤
1. 执行 `mvn compile` 验证编译通过
2. 确认 `target/generated-sources` 下生成 Lombok 增强类
3. 启动服务验证无运行时异常

## 假设与决策
- Lombok 版本由 Spring Boot 3.2.6 parent 管理，无需显式指定版本
- 使用 `<optional>true</optional>` 与现有模块保持一致
