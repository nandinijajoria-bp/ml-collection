package com.bharatpe.lending.dto.underwriting.write;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperianWriteDto {
    @NotNull(message = "merchantId cannot be null")
    private Long merchantId;
    private Boolean rejected;
    private String reason;
    private String category;
    private String color;
    private Double eligibleAmount;
    private String loanType;
    private Date rejectedDate;
    private Date reportDate;
    private Double bpScore;
    private Double experianScore;
}