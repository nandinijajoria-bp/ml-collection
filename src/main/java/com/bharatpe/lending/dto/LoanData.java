package com.bharatpe.lending.dto;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanData {
    private List<LoanDetailsDTO> loanDetailsList;
    private List<ApplicationDetailsDTO> applicationHistory;
}

