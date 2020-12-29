package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class EdiScheduleDTO {

    @JsonProperty("sl_no")
    private int serialNumber;

    @JsonProperty("EDI_amount")
    private int ediAmount;

    private double principal;

    private double interest;
}
