package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanDetailsAndStatementDTO {
    LendingPaymentScheduleDetailsDTO loanDetails;
    List<LendingPaymentScheduleDaoSlave.LoanStatementDTO> loanStatement;
}
