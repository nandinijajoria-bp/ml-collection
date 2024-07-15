package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;


@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class ABFLPennyDropRequestDTO {
    String lender;
    String productName;
    Long applicationId;
    boolean topup;
    ABFLPennyDropRequestDTO.Payload payload;

    @Data
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class Payload {
        public String accountId;
        public String accountNumber;
        @JsonProperty("IFSCCode")
        public String iFSCCode;
        public String bankName;
        public String bankBranch;
        public String email;
        public String accountHolderName;
        public String nameMatchType;
        public String useCombinedSolution;
        public Boolean allowPartialMatch;
        public ClientData clientData;


        @Data
        @ToString
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Builder
        public static class ClientData{
            public String caseId;
        }
    }
}
