package com.bharatpe.lending.loanV3.dto.response.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriForeclosureResponseDTO {
    Integer officeId;
    Integer clientId;
    Integer loanId;
    Long resourceId;
    Changes changes;
    AdditionalResponseData additionalResponseData;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalResponseData {

        @JsonProperty("sub-status")
        Integer subStatus;
        Integer status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Changes{
        List<Integer> transactionDate;
        String receiptNumber;
        List<Integer> transactions;
        Long eventAmount;
        String note;
    }
}
