package com.bharatpe.lending.loanV3.services.associationsV2.piramal.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;

public interface IValidationLayer<T> {
    boolean isInValidPayload(T data);
}
