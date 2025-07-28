package com.bharatpe.lending.loanV3.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@NoArgsConstructor
@Configuration
@ConfigurationProperties(prefix = PayUConfig.PREFIX)
public class PayUConfig {
    public static final String PREFIX = "payu";
    private Integer maxLoanTenureFor180DaysVintage = 10;
    private Long minVintageForMoreThan15MonthsLoans = 365L;
}
