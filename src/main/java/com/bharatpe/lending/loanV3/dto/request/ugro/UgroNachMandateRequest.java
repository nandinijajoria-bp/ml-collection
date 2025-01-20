package com.bharatpe.lending.loanV3.dto.request.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroNachMandateRequest {
    private String leadId;
    private String accountNumber;
    private String ifsc;
    private String name;
    private String mandateCreationDate;
    private String startDate;
    private String endDate;
    private Double maxAmount;
    private String authorisationMode;
    @JsonProperty("UMRNNumber")
    private String UMRNNumber;
    private String nachMode;
    private String nachVendor;
    private String vendorRequestId;
    private String status;
}
