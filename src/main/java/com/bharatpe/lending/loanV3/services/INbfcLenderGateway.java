package com.bharatpe.lending.loanV3.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.bpnewmaster.dao.DocKycDetailsDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocKycDetailsMaster;
import com.bharatpe.lending.common.dao.LendingEkycDao;
import com.bharatpe.lending.common.entity.LendingEkyc;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


@Slf4j
public abstract class INbfcLenderGateway {

    public abstract BreApiResponseDto invokeBre(BreApiRequestDto breRequestDto);

    public abstract SanctionWrapperApiResponse invokeSanction(SanctionWrapperApiRequestDto sanctionWrapperApiRequestDto);

    public abstract KycApiResponseDto invokeKyc(KycRequestApiDto kycRequestDto);

    public abstract DocUploadApiResponse invokeDocUpload(DocUploadApiRequestDto docUploadApiRequestDto);

    public abstract RegulatoryApiResponseDto invokeRegDataUpload(RegulatoryApiRequestDto regulatoryApiRequestDto);

    public abstract DigitalDataUploadResponse invokeDigitalDataUpload(DigitalDataUploadRequest digitalDataUploadRequest);

    public abstract ForeClosureAmountResponse fetchDueForeclosureAmount(ForeclosureAmountRequest foreclosureAmountRequest);

    public abstract AbflDigiSignResponseDTO invokeDigiSign(AbflDigiSignRequestDTO abflDigiSignRequestDTO);

}
