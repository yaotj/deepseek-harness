------

### 各个数据库的jdbc驱动

版本示例：

```
<ojdbc6.version>12.1.0.1-atlassian-hosted</ojdbc6.version>
<db2jcc.version>db2jcc4</db2jcc.version>
<mysql.version>8.0.33</mysql.version>
<dm.version>8.1.3.140</dm.version>
```

```
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>${mysql.version}</version>
</dependency>
```

```
<dependency>
    <groupId>com.ibm.db2.jcc</groupId>
    <artifactId>db2jcc</artifactId>
    <version>${db2jcc.version}</version>
</dependency>
```

```
<dependency>
    <groupId>com.oracle</groupId>
    <artifactId>ojdbc6</artifactId>
    <version>${ojdbc6.version}</version>
</dependency>
```

```
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>DmJdbcDriver18</artifactId>
    <version>${dm.version}</version>
</dependency>
```



------

