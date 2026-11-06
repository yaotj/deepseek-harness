## 使用该模块

在业务服务的源码中直接引用该模块。

### maven依赖

pom.xml 文件：

```
	<parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.6</version>
        <relativePath />
    </parent>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <java.version>21</java.version>
    </properties>

    <dependencies>
    
    	<dependency>
            <groupId>com.chinasofti.huateng.micro</groupId>
            <artifactId>web</artifactId>
            <version>2.0</version>
        </dependency>

        <dependency>
            <groupId>com.chinasofti.huateng.micro</groupId>
            <artifactId>mybatis-adaptor</artifactId>
            <version>2.0</version>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```



------

### 开启模块

在启动类上注解@EnableDefaultMybatisAutoConfig

------

### 配置文件

application.properties文件

sql模板语句的xml配置文件默认所在路径：classpath\***:/mappers/**\*.xml

如果需要自定义路径使用以下格式配置：

```
mybatis.mapper-locations=classpath*:/mappers/*.xml
```

单数据源配置：

```
other.sql.host=localhost:3306
other.sql.type=dm
other.sql.page.type=dm
other.sql.database=test
other.sql.username=test
other.sql.password=test
```

双数据源配置：

```
other.sql.double-datasource=true
spring.datasource.druid.database1.page.type=mysql
spring.datasource.druid.database1.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.druid.database1.url=jdbc:mysql://localhost:3306/test
spring.datasource.druid.database1.username=test
spring.datasource.druid.database1.password=test
spring.datasource.druid.database2.page.type=mysql
spring.datasource.druid.database2.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.druid.database2.url=jdbc:mysql://localhost:3306/test
spring.datasource.druid.database2.username=test
spring.datasource.druid.database2.password=test
```



------

### Mapper注解

在接口类上注解（interface），mybatis框架能注册该对象为一个spring的bean，优点是代码更加简洁。

```
@Mapper
public interface TestMapper {
    List<Test> findAllByModel(Test test);
}
```

#### 组件扫描路径

在启动类上注解@MapperScan，定义Mapper组件的扫描包路径以及sqlSessionTemplateRef目标。

如果是单数据源，可以注解一次@MapperScan，basePackages为所有Mapper组件的公共包路径：

@MapperScan(basePackages="com.micro.client.dao")

如果是双数据源，可以注解两次@MapperScan，分别指定各自的basePackages和sqlSessionTemplateRef：

@MapperScan(basePackages="com.micro.client.dao.db1",sqlSessionTemplateRef="sqlSessionTemplate1")

@MapperScan(basePackages="com.micro.client.dao.db2",sqlSessionTemplateRef="sqlSessionTemplate2")

#### 接口方法和xml中sql语句的绑定

命名空间为接口类的全类名，有内在的关联关系。

```
<mapper namespace="com.micro.client.dao.TestDMapper">
```



------

### Service注解

在实现类上注解（class），表示该对象是一个spring的bean。优点是可以在方法中实现sql入参的校验。

如果是单数据源，SqlSessionTemplate对象在spring容器中只有一个，可以不用特意声明该bean的名称。如果是双数据源，需要指定bean的名称，取值sqlSessionTemplate1和sqlSessionTemplate2，分别表示数据源1和数据源2。

```
@Service
public class TestDao extends SqlSessionDaoSupport {
    public static Logger log = LoggerFactory.getLogger(TestDao.class);


    public TestDao(@Qualifier("sqlSessionTemplate") SqlSessionTemplate sqlSessionTemplate) {
        super.setSqlSessionTemplate(sqlSessionTemplate);
    }

    public List<Test> findAllByModle(Test test) {
        return getSqlSession().selectList("com.micro.client.dao.TestDDao.findAllByModle", test);
    }

}
```

#### 组件扫描路径

在启动类上注解@ComponentScan。

@ComponentScan("com.micro.client.dao")

#### 实现类方法和xml中sql语句的绑定

命名空间的取值是任意字符串，无关联关系。需要在实现类中getSqlSession()对象的方法中传入statement的名称，取值是xml中的命名空间+方法名

```
<mapper namespace="com.micro.client.dao.TestDDao">
```



------

### 分页插件

执行sql前，调用以下代码：

```
PageHelper.startPage(0, 11);
testMapper.select(new QueryWrapper(test));
```



------

### 事务管理器

在service的方法上注解

```
@Transactional(transactionManager = "transactionManager2", rollbackFor = Exception.class)
```

transactionManager的取值：单数据源时为transactionManager，双数据源时，第一个为transactionManager1，第二个为transactionManager2。

------

