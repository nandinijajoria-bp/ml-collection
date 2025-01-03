package com.bharatpe.lending.loanV3.services;

import com.bharatpe.lending.loanV3.dto.*;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class INbfcLenderGateway {

    public abstract BreApiResponseDto invokeBre(BreApiRequestDto breRequestDto);

    public abstract SanctionWrapperApiResponse invokeSanction(SanctionWrapperApiRequestDto sanctionWrapperApiRequestDto);

    public abstract KycApiResponseDto invokeKyc(KycRequestApiDto kycRequestDto);

    public abstract DocUploadApiResponse invokeDocUpload(DocUploadApiRequestDto docUploadApiRequestDto);

    public abstract RegulatoryApiResponseDto invokeRegDataUpload(RegulatoryApiRequestDto regulatoryApiRequestDto);

    public abstract DigitalDataUploadResponse invokeDigitalDataUpload(DigitalDataUploadRequest digitalDataUploadRequest);

    public abstract ForeClosureAmountResponse fetchDueForeclosureAmount(ForeclosureAmountRequest foreclosureAmountRequest);
    public abstract AbflRpsResponseDTO fetchRepaymentSchedule(AbflRpsRequestDTO abflRpsRequestDTO);
    public abstract AbflDigiSignResponseDTO invokeDigiSign(AbflDigiSignRequestDTO abflDigiSignRequestDTO);

    public abstract ABFLPennyDropResponseDTO invokePennyDrop(ABFLPennyDropRequestDTO foreclosureAmountRequest);

    public abstract EKycApiResponseDto invokeEKyc(EKycRequestApiDto eKycRequestApiDto);

    public abstract EKycCallbackResponseDto invokeEKycStatusCheck(EKycStatusCheckRequestApiDto eKycStatusCheckRequest);

    public abstract KycValidityApiResponseDto invokeKycValidity(KycValidityRequestApiDto kycValidityRequest);


    }
