package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class EdiScheduleV2DTO {

    @JsonProperty("sl_no")
    private int serialNumber;

    @JsonProperty("EDI_amount")
    private int ediAmount;

    private double principal;

    private double interest;

    private Date date;

    private double balance;
}
