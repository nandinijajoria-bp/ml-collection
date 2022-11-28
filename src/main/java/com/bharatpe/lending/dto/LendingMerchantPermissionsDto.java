package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.ToString;

import javax.print.DocFlavor;
import java.util.Date;
@ToString
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
public class LendingMerchantPermissionsDto {

    private Boolean smsPermissionIsActive;
    private Boolean locationPermissionIsActive;
    private String smsPermissionDate;
    private String locationPermissionDate;
}
