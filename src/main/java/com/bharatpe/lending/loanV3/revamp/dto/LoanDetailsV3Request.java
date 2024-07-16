package com.bharatpe.lending.loanV3.revamp.dto;

import com.bharatpe.lending.dto.LoanInsuranceDTO;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class LoanDetailsV3Request {
    private String scope;
    private String pancard;
    private String pincode;
    private Integer appVersion;
    private Long applicationId;

    private String mappedMobile;
    private String stageOneHitId;
    private String stageTwoHitId;
    private Boolean skipBureau;
    private Boolean skipMaskedMobileException;

    private String resubmitReason;
    private boolean isIOS;
    private Boolean rteFlag;

    private Boolean isInsured;
    private List<LoanInsuranceDTO> loanInsurances;

//    private boolean isIOS = false;
}
