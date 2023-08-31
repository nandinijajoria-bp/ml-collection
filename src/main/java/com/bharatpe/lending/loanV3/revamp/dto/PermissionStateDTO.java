package com.bharatpe.lending.loanV3.revamp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Date;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PermissionStateDTO {

    private boolean success;
    private String errorString;
    private Boolean smsPermissionIsActive;
    private Boolean locationPermissionIsActive;
    private Date locationPermissionDate;
    private Boolean dummyMerchant;

}
