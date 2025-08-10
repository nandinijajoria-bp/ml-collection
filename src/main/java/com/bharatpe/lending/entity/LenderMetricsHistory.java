package com.bharatpe.lending.entity;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import java.lang.reflect.Field;

@Entity
@Table(name = "lender_metrics_history")
@Data
@Slf4j
public class LenderMetricsHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lender")
    private String lender;

    @Column(name = "utilization_rate")
    private Double utilizationRate;

    @Column(name = "success_rate")
    private Double successRate;

    @Column(name = "capital_limit")
    private Double capitalLimit;

    @Column(name = "total_applications")
    private Integer totalApplications;

    @Column(name = "approved_applications")
    private Integer approvedApplications;

    @Column(name = "signed_agreements")
    private Integer signedAgreements;

    @Column(name = "is_lender_switched_off")
    private Boolean isLenderSwitchedOff;

    @Transient
    private Double interestRate;

    public Double getFieldValueAsDouble(String fieldName) {
        try {
            Field field = this.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            if ("interestRate".equalsIgnoreCase(fieldName)) {
                return interestRate != null ? interestRate : -1.0;
            }
            Object value = field.get(this);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            log.warn("Field {} is not a number", fieldName);
            return -1.0;
        } catch (Exception e) {
            throw new RuntimeException("Invalid field: " + fieldName, e);
        }
    }
}
