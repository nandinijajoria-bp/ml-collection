package com.bharatpe.lending.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(entityManagerFactoryRef = "queryEntityManagerFactory",
  transactionManagerRef = "queryTransactionManager", basePackages = {"com.bharatpe.lending.common.query.dao"}
)
public class QueryDbConfig {

    @Autowired
    @Bean(name = "queryJdbc")
    public JdbcTemplate getJdbcTemplate(@Qualifier("queryDB") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "queryDB")
    @ConfigurationProperties(prefix = "spring.query")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "queryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean replicaEntityManagerFactory(
            EntityManagerFactoryBuilder builder, @Qualifier("queryDB") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.bharatpe.lending.common.query.entity")
                .persistenceUnit("query")
                .build();
    }

    @Bean(name = "queryTransactionManager")
    public PlatformTransactionManager replicaTransactionManager(
            @Qualifier("queryEntityManagerFactory") EntityManagerFactory barEntityManagerFactory) {
        return new JpaTransactionManager(barEntityManagerFactory);
    }

}
