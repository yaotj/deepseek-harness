package com.chinasofti.huateng.micro.mybatis.adaptor;

import com.github.pagehelper.PageInterceptor;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicMybatisConfiguration implements EnvironmentAware, BeanDefinitionRegistryPostProcessor {

    public static Logger log = LoggerFactory.getLogger(DynamicMybatisConfiguration.class);

    private static Environment env;

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (env.getProperty("other.sql.double-datasource", "false").equals("true")) {
            List<String> dataSourceNames = extractDataSourceNames();
            if (dataSourceNames.size() > 0) {
                log.info("dynamic register {} mybatis config for datasource", dataSourceNames.size());
            }
            for (String dsName : dataSourceNames) {
                registerSqlSessionFactoryBean(registry, dsName);
                registerTransactionManagerBean(registry, dsName);
                registerSqlSessionTemplateBean(registry, dsName);
            }
        }
    }

    private void registerSqlSessionFactoryBean(BeanDefinitionRegistry registry, String dsName) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(SqlSessionFactoryBean.class);

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("dataSource", new org.springframework.beans.factory.config.RuntimeBeanReference(dsName));
        try {
            String mapperLocations = env.getProperty("mybatis.mapper-locations", "classpath*:/mappers/*.xml");
            values.add("mapperLocations", new PathMatchingResourcePatternResolver().getResources(mapperLocations));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // PageInterceptor
        PageInterceptor interceptor = new PageInterceptor();
        Properties props = new Properties();
        props.setProperty("helperDialect", env.getProperty("spring.datasource.druid." + dsName + ".page-type", "mysql"));
        props.setProperty("reasonable", "true");
        interceptor.setProperties(props);
        values.add("plugins", interceptor);

        beanDefinition.setPropertyValues(values);
        String beanName = "sqlSessionFactory" + capitalize(dsName);
        registry.registerBeanDefinition(beanName, beanDefinition);
        log.info("register SqlSessionFactory {}", beanName);
    }

    private void registerTransactionManagerBean(BeanDefinitionRegistry registry, String dsName) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(DataSourceTransactionManager.class);
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("dataSource", new org.springframework.beans.factory.config.RuntimeBeanReference(dsName));
        beanDefinition.setPropertyValues(values);
        String beanName = "transactionManager" + capitalize(dsName);
        registry.registerBeanDefinition(beanName, beanDefinition);
        log.info("register transactionManager {}", beanName);
    }

    private void registerSqlSessionTemplateBean(BeanDefinitionRegistry registry, String dsName) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(SqlSessionTemplate.class);

        ConstructorArgumentValues args = new ConstructorArgumentValues();
        args.addIndexedArgumentValue(0, new RuntimeBeanReference("sqlSessionFactory" + capitalize(dsName)));
        beanDefinition.setConstructorArgumentValues(args);
        String beanName = "sqlSessionTemplate" + capitalize(dsName);
        registry.registerBeanDefinition(beanName, beanDefinition);
        log.info("register sqlSessionTemplate {}", beanName);
    }


    private List<String> extractDataSourceNames() {
        Set<String> uniqueNames = new HashSet<>();
        Pattern pattern = Pattern.compile("^spring\\.datasource\\.druid\\.(\\w+)\\.url$");
        for (Iterator<org.springframework.core.env.PropertySource<?>> it = ((AbstractEnvironment) env).getPropertySources().stream().iterator(); it.hasNext(); ) {
            Object propertySource = it.next();
            if (propertySource instanceof EnumerablePropertySource) {
                for (String propertyName : ((EnumerablePropertySource<?>) propertySource).getPropertyNames()) {
                    Matcher matcher = pattern.matcher(propertyName);
                    if (matcher.matches()) {
                        uniqueNames.add(matcher.group(1));
                    }
                }
            }
        }
        return new ArrayList<>(uniqueNames);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

}
