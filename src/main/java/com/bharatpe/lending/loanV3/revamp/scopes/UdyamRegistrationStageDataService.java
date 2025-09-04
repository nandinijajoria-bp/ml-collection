package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.dto.UdyamStatusCheckResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.revamp.dto.*;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Component
@Slf4j
public class UdyamRegistrationStageDataService implements IStageDataService<UdyamRegistrationStateDTO>{
    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Override
    public LendingStateDTO<UdyamRegistrationStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }

    @Override
    public LendingStateDTO<UdyamRegistrationStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        UdyamRegistrationStateDTO udyamRegistrationStateDTO = new UdyamRegistrationStateDTO();
        try {
            LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("Application not found for merchant {}", scopeDataArgs.getMerchant().getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }

            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("Lender details not found of {} for application {}", lendingApplication.getLender(), lendingApplication.getId());
                throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
            }
            loanDetailsV3Service.saveApplicationViewState(null, scopeDataArgs.getApplicationId(), LendingViewStates.UDYAM_REGISTRATION_PAGE);
            udyamRegistrationStateDTO.setMerchantId(lendingApplication.getMerchantId());
            udyamRegistrationStateDTO.setApplicationId(lendingApplication.getId());
            udyamRegistrationStateDTO.setLender(lendingApplication.getLender());
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId()).merchantId(lendingApplication.getMerchantId())
                    .lendingApplication(lendingApplication).lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(Boolean.TRUE).build();
            UdyamStatusCheckResponseDTO statusCheckResponse = associationServiceUtil.invokeUdyamStatusCheckService(lendingApplication.getLender(), lenderAssociationDetailsRequestDto);
            udyamRegistrationStateDTO.setIsUdyamRequired(statusCheckResponse.getIsUdyamRequired());
            udyamRegistrationStateDTO.setUdyamStatus(statusCheckResponse.getUdyamStatus());
            if(udyamRegistrationStateDTO.getIsUdyamRequired()){
                String udyamURL = associationServiceUtil.invokeUdyamFlowService(lenderAssociationDetailsRequestDto.getLendingApplication().getLender(), lenderAssociationDetailsRequestDto);
                if(ObjectUtils.isEmpty(udyamURL)){
                    log.info("unable to fetch udyam url of {} for application {}", lendingApplication.getLender(), lendingApplication.getId());
                    throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
                }
                udyamRegistrationStateDTO.setUdyamRegistrationLink(udyamURL);
            }
        } catch (Exception e) {
            log.error("Exception in fetch scope data of udyam registration stage for merchant:{}, {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
        return new LendingStateDTO<>(udyamRegistrationStateDTO , LendingViewStates.KEY_FACTOR_STATEMENT_PAGE, LendingViewStates.UDYAM_REGISTRATION_PAGE);
    }
}
