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
@EnableJpaRepositories(
  basePackages = {"com.bharatpe.lending.common.bpnewmaster.dao"},
  transactionManagerRef = "bpNewMasterTransactionManager",
  entityManagerFactoryRef = "bpNewMasterEntityManagerFactory"
)
public class BPNewMasterDbConfig {

    @Autowired
    @Bean(name = "bpNewMasterJdbc")
    public JdbcTemplate getJdbcTemplate(@Qualifier("bpNewMasterDB") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "bpNewMasterDB")
    @ConfigurationProperties(prefix = "spring.bpnewmaster")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "bpNewMasterEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
      EntityManagerFactoryBuilder builder, @Qualifier("bpNewMasterDB") DataSource dataSource) {
        return builder.dataSource(dataSource).packages("com.bharatpe.lending.common.bpnewmaster.entity").persistenceUnit("bpNewMaster")
          .build();
    }

    @Bean(name = "bpNewMasterTransactionManager")
    public PlatformTransactionManager transactionManager(
      @Qualifier("bpNewMasterEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
