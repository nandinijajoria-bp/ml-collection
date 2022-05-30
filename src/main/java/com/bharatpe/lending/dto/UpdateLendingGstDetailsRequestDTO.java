package com.bharatpe.lending.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;


@Data
@ToString
@Builder
public class UpdateLendingGstDetailsRequestDTO {

    Double arrivedScore;
}