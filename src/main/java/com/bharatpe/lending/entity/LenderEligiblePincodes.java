package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lender_eligible_pincodes")
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LenderEligiblePincodes extends BaseEntity {

    private String lender;

    private Integer pincode;

    @Column(name = "pincode_color")
    private String pincodeColor;

    @Enumerated(EnumType.STRING)
    private LenderEligiblePincodesStatus status;

    public enum LenderEligiblePincodesStatus {
        ACTIVE, INACTIVE;
    }

}