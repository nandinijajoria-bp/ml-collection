package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class RepaymentHistoryDTO {
    private List<Map<String, Object>> repaymentHistory;
    private Double excessCollectionAmount;
    private Double excessCollectionAdjusted;
    private Double excessCollectionBalance;
}
