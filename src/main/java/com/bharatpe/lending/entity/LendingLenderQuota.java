package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "lending_lender_quota")
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LendingLenderQuota extends BaseEntity {

    @Column(name = "lender")
    private String lender;

    @Column(name = "total_weekly_amount")
    private Double totalWeeklyAmount;

    @Column(name = "remaining_balance")
    private Double remainingBalance;

    @Column(name = "assigned_amount")
    private Double assignedAmount;

    @Column(name = "edi_model")
    private String ediModel;

    /*
    @Column(name = "classification")
    private String classification;

    public enum Classification {
        DEFAULT, WILDCARD, REGULAR
    }
    */

}