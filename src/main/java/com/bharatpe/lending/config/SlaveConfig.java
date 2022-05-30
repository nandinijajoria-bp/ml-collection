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
@EnableJpaRepositories(entityManagerFactoryRef = "slaveEntityManagerFactory",
        transactionManagerRef = "slaveTransactionManager",
        basePackages = {"com.bharatpe.lending.slave.dao", "com.bharatpe.lending.common.slave.dao"}
)
public class SlaveConfig {

    @Autowired
    @Bean(name = "slaveJdbc")
    public JdbcTemplate getJdbcTemplate(@Qualifier("slaveDB") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "slaveDB")
    @ConfigurationProperties(prefix = "spring.slave")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "slaveEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean replicaEntityManagerFactory(
            EntityManagerFactoryBuilder builder, @Qualifier("slaveDB") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.bharatpe.lending.common.slave.entity")
                .persistenceUnit("slave")
                .build();
    }

    @Bean(name = "slaveTransactionManager")
    public PlatformTransactionManager replicaTransactionManager(
            @Qualifier("slaveEntityManagerFactory") EntityManagerFactory barEntityManagerFactory) {
        return new JpaTransactionManager(barEntityManagerFactory);
    }

}
