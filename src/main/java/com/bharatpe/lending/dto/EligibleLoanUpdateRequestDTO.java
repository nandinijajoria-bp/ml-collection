package com.bharatpe.lending.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for eligible loan update requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EligibleLoanUpdateRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String category;

    private Double amount;

    private Integer tenure;

    private Integer edi;

    @JsonProperty("io_edi")
    private Integer ioEdi;

    @JsonProperty("io_edi_days")
    private Integer ioEdiDays;

    @JsonProperty("edi_free_days")
    private Integer ediFreeDays;

    private Integer repayment;

    @JsonProperty("edi_days")
    private Integer ediDays;

    private String lender;
}