package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.enums.Lender;
import lombok.Data;


@Data
public class AutoPayUPIMandatePgRequestDto {
	private String mandateId;
	private Lender lender;

}
