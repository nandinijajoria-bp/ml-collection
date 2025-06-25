package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUUpdateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUUpdateLeadResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Slf4j
@Service
public class PayUUpdateLeadService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    MerchantService merchantService;

    @Transactional
    public Boolean invokeUpdateAddress(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            log.info("Payu inside updateAddress {} {}", lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getDocUploadStatus(),lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadStatus());
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lenderAssociationDetailsRequestDto.getLendingApplication().getId(), lenderAssociationDetailsRequestDto.getLendingApplication().getLender());

            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.UPDATE_ADDRESS.name());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_ADDRESS_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

            NBFCRequestDTO updateAddressRequestDto = getUpdateAddressPayload(lenderAssociationDetailsRequestDto);

            if (Objects.isNull(updateAddressRequestDto)) {

                log.info("error in update address payload of PayU for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_ADDRESS_PAYLOAD_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return false;
            }
            log.info("PAYU: UPDATE ADDRESS REQUEST PAYLOAD", updateAddressRequestDto.getPayload().toString());

            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(updateAddressRequestDto, LenderAssociationStages.UPDATE_LEAD);

            log.info("update address response of PayU from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("update address request of payU success for {}", lenderAssociationDetailsRequestDto.getApplicationId());

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                PayUUpdateLeadResponseDTO updateLeadResponseDTO = objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayUUpdateLeadResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus()) && StringUtils.isNotBlank(updateLeadResponseDTO.getApplicationId())) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_ADDRESS_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while pushing update lead of PayU for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_ADDRESS_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO getUpdateAddressPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {

        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();

        log.info("PayU: LeadId {}", lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId());
        try {

            PayUUpdateLeadRequestDTO payload = PayUUpdateLeadRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                    .applicantDetails(getApplicantDetails(lenderAssociationDetailsDto))
                    .updatedAddress(true)
                    .build();

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(payload)
                    .build();

        } catch (Exception e) {
            log.info("exception occurred while creating request payload for updateLead of PayU for applicationId: {}, {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private List<PayUUpdateLeadRequestDTO.ApplicantDetailsDTO> getApplicantDetails(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {

        List<PayUUpdateLeadRequestDTO.ApplicantDetailsDTO> applicantDataList = new ArrayList<>();

        PayUUpdateLeadRequestDTO.ApplicantDetailsDTO applicantDetails;
        LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());

        applicantDetails = PayUUpdateLeadRequestDTO.ApplicantDetailsDTO.builder()
                .address(getAddress(lenderAssociationDetailsRequestDto, lendingApplicationDetails))
                .residenceAddressSameAsPermanentAddress(lendingApplicationDetails.getCurrentAddressSameAsPermanentAddress())
                .build();

        applicantDataList.add(applicantDetails);
        return applicantDataList;

    }

    private List<PayUUpdateLeadRequestDTO.AddressDTO> getAddress(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, LendingApplicationDetails lendingApplicationDetails) {

        List<PayUUpdateLeadRequestDTO.AddressDTO> addressDataList = new ArrayList<>();
        PayUUpdateLeadRequestDTO.AddressDTO currentAddress = null;

        LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();

        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());

        currentAddress = PayUUpdateLeadRequestDTO.AddressDTO.builder()
                .line1(lendingGstDetail.getAddress1())
                .line2(lendingGstDetail.getAddress2())
                .city(lendingGstDetail.getCity())
                .state(lendingGstDetail.getState())
                .locality(null)
                .pincode(lendingGstDetail.getPincode())
                .ownershipIndicator("owned")
                .addressType(lendingApplicationDetails.getCurrentAddressSameAsPermanentAddress() ? "PERMANENT" : "RESIDENCE")
                .build();

        addressDataList.add(currentAddress);

        return addressDataList;
    }

    public Boolean invokeBankAccountUpdation(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            log.info("Payu inside bankAccountUpdation {} {}", lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getDocUploadStatus(),lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadStatus());
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lenderAssociationDetailsRequest.getLendingApplication().getId(), lenderAssociationDetailsRequest.getLendingApplication().getLender());

            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.UPDATE_BANK_DETAILS.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.BANK_UPDATION_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);

            NBFCRequestDTO bankDetailsUpdationRequest = getUpdateBankDetailsPayload(lenderAssociationDetailsRequest);
            if (ObjectUtils.isEmpty(bankDetailsUpdationRequest)) {
                log.info("error in bank updation payload of PayU for applicationId: {}", lenderAssociationDetailsRequest.getApplicationId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.BANK_UPDATION_PAYLOAD_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return false;
            }
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(bankDetailsUpdationRequest, LenderAssociationStages.UPDATE_LEAD);
            log.info("Bank updation response of payU from nbfc: {} with applicationId: {}", nbfcResponseDTO, lenderAssociationDetailsRequest.getApplicationId());

            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {

                log.info("bank details updation request of payU success for {}", lenderAssociationDetailsRequest.getApplicationId());

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDTO.getData(), PayUCommonResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus())) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.BANK_UPDATION_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequest);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while pushing bank details updation request of PayU for  {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.BANK_UPDATION_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequest);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO getUpdateBankDetailsPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {

        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {

            final MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                    Constants.MerchantUtil.Scope.BANK_DETAIL,
                    Constants.MerchantUtil.Scope.MERCHANT_USER
            ));
            if (ObjectUtils.isEmpty(merchantDetailsDto)) {
                log.info("merchant bank details not found for application {} with merchantId {}", lendingApplication.getId(), lendingApplication.getMerchantId());
                throw new RuntimeException("merchant bank details not found for application " + lendingApplication.getId());
            }

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(PayUUpdateLeadRequestDTO.builder()
                            .applicationId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .updatedBankDetails(PayUUpdateLeadRequestDTO.BankDetailsDTO.builder()
                                    .accountTypeId("CURRENT".equalsIgnoreCase(merchantDetailsDto.getBankDetail().getAccountType()) ? "CURRENT" : "SAVINGS")
                                    .bankAccountName(merchantDetailsDto.getBankDetail().getBeneficiaryName())
                                    .accountNumber(merchantDetailsDto.getBankDetail().getAccountNumber())
                                    .bankName(merchantDetailsDto.getBankDetail().getBankName())
                                    .ifscCode(merchantDetailsDto.getBankDetail().getIfsc())
                                    .build())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("exception occurred while creating request payload for updateBankDetails of PayU for applicationId: {}, {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

}
