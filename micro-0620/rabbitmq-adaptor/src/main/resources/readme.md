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
            <artifactId>rabbitmq-adaptor</artifactId>
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

在启动类上注解@EnableDefaultRabbitmqAutoConfig

------

### 配置文件

application.properties文件

以下为默认值（需要更改值时，才需配置）：

```
spring.rabbitmq.host=130.251.235.200
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
spring.rabbitmq.publisherReturns=true
spring.rabbitmq.publisherConfirmType=CORRELATED
spring.rabbitmq.listener.simple.acknowledgeMode=MANUAL
spring.rabbitmq.listener.simple.prefetch=1
spring.rabbitmq.listener.simple.concurrency=1
spring.rabbitmq.listener.simple.max-concurrency=10
spring.rabbitmq.listener.simple.retry.enabled=false
spring.rabbitmq.listener.simple.retry.max-attempts=0
spring.rabbitmq.template.reply-timeout=200
spring.rabbitmq.template.retry.enabled=false
spring.rabbitmq.virtual-host=/
```



------

### 消费者

从队列hello中接收消息，并且回复确认消费成功。

```

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class HelloReceiver {

    public static Logger log = LoggerFactory.getLogger(HelloReceiver.class);

    @RabbitListener(queues = "hello")
    @RabbitHandler
    public void process(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        String mess = new String(message.getBody());
        log.info(mess);
        channel.basicAck(tag, false);
    }

}
```



------

### 生产者

自动注入工具类，示例：

```
@Autowired
SimpleProducer simpleProducer;
```

发送消息，并等待mq服务器确认消息生产成功，示例：

```
boolean b = simpleProducer.convertSendAndReceive("", "hello", "ssss");
System.out.println(b);
```

仅发送消息，不等待返回结果，示例：

```
simpleProducer.send("", "hello", "1111");
```

参数说明：

exchange，交换器

routingKey，路由键

msg，消息



------

