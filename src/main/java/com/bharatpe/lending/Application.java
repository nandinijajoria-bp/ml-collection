package com.bharatpe.lending;

import com.bharatpe.common.service.LoyaltyService;
import io.sentry.Sentry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@ComponentScan(basePackages =  "com.bharatpe.*")
@EntityScan(basePackages = "com.bharatpe.*")
@EnableJpaRepositories(basePackages = "com.bharatpe.*")
@PropertySources({
		@PropertySource("file:/etc/bharatpe/key.properties"),
		@PropertySource("file:/etc/bharatpe/production.properties")
})
public class Application
{

	@Autowired
	Environment env;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public LoyaltyService loyaltyService() {
		return new LoyaltyService();
	}

	@PostConstruct
	public void init(){
		Sentry.init(env.getProperty("sentry.dsn"));
		TimeZone.setDefault(TimeZone.getTimeZone("IST"));
	}
}
