package com.bharatpe.lending.loanV3.dto.response.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriDisbursalCallbackResponseDTO {
     Double insurance;
     Double processingFees;
     String phonenumber;
     Double preEmi;
     String lanID;
     String branchbankname;
     String acHoldername;
     Double disbursalAmount;
     String disbtype;
     String utr;
     Integer loanApplicationId;
     String firstRepaymentDate;
     LocalDateTime disbdate;
     String accountno;
     Double netDisbursement;
     String ifsc;
     String status;
     Integer loanId;
}
