package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingConsent;
import com.bharatpe.lending.common.entity.LendingLoanInsurance;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.dto.LoanInsuranceDTO;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.dto.AgreementResponse;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.PInsuranceRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.PInsuranceResponseDTO;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.revamp.dto.AgreementStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.services.gateway.PiramalApiGateway;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class AgreementStageDataService implements IStageDataService<AgreementStateDTO> {

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    LendingLoanInsuranceDao lendingLoanInsuranceDao;

    @Autowired
    LendingConsentDao lendingConsentDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PiramalApiGateway piramalApiGateway;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Value("${piramal.insurance.rollout.percent:0}")
    Integer piramalInsuranceRolloutPercent;

    private static final String SELECTED = "SELECTED";

    @Override
    public LendingStateDTO<AgreementStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<AgreementStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        lendingStateDTO.setLendingViewStates(LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<AgreementStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {

        // get ApplicationId from frontEnd (mandatory)
        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(), scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }

        Boolean isInsured = scopeDataArgs.getLoanDetailsV3Request().getIsInsured();
        List<LoanInsuranceDTO> insurances = scopeDataArgs.getLoanDetailsV3Request().getLoanInsurances();

        List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
        if (lendingApplication.getLender().equalsIgnoreCase(Lender.PIRAMAL.name())
                && !topupLoans.contains(lendingApplication.getLoanType())
                && easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), piramalInsuranceRolloutPercent)) {
            List<LoanInsuranceDTO> oldInsurances = isInsuredBefore(lendingApplication);
            if (!ObjectUtils.isEmpty(oldInsurances) && ObjectUtils.isEmpty(insurances) && ObjectUtils.isEmpty(isInsured)) {
                insurances = oldInsurances;
                isInsured = true;
            } else {
                if (Objects.isNull(isInsured)) {
                    insurances = getInsurancePremiums(lendingApplication);
                }
                updateLoanDisbursalAmount(lendingApplication, isInsured, insurances);
                saveInsurancePremiums(lendingApplication, insurances, isInsured);
                updateConsent(lendingApplication, isInsured);
            }
        }

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender());

        AgreementStateDTO agreementResponseV3 = AgreementStateDTO.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .loanAmount(lendingApplication.getLoanAmount())
                .interestRate(lendingApplication.getInterestRate())
                .annualRoi(getAnnualRoi(lendingApplicationLenderDetails, lendingApplication))
                .arrangerFee(lendingApplication.getProcessingFee().intValue())
                .disbursalAmount(lendingApplication.getDisbursalAmount())
                .tenure(lendingApplication.getTenure())
                .ediAmount(lendingApplication.getEdi().intValue())
                .ediCount(lendingApplication.getPayableDays().intValue())
                .ediModelModified(!ObjectUtils.isEmpty(lendingApplicationDetails) && Optional.ofNullable(lendingApplicationDetails.getEdiModelModified()).orElse(false))
                .repayment(AgreementResponse.Repayment.builder()
                        .principal(lendingApplication.getLoanAmount())
                        .interest(lendingApplication.getRepayment() - lendingApplication.getLoanAmount())
                        .total(lendingApplication.getRepayment())
                        .build())
                .accountDetails(loanUtil.getAccountDetails(lendingApplication.getMerchantId()))
                .enachBank(loanUtil.isEnachBank(lendingApplication.getMerchantId()))
                .loanInsurances(insurances)
                .isInsured(isInsured)
                .apr(updateApr(lendingApplication, isInsured))
                .externalLoanId(lendingApplication.getExternalLoanId()).build();
        if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))agreementResponseV3.setTopup(true);

        return new LendingStateDTO<>(agreementResponseV3 , LendingViewStates.AGREEMENT_PAGE, LendingViewStates.AGREEMENT_PAGE);
    }

    public void updateLoanDisbursalAmount(LendingApplication lendingApplication, Boolean isInsured, List<LoanInsuranceDTO> insurances) {
        if (Objects.nonNull(isInsured) && isInsured) {
            Double insurancePremium = insurances.stream()
                    .filter(LoanInsuranceDTO::getIsSelected)
                    .findFirst().get()
                    .getInsurancePremium();
            lendingApplication.setDisbursalAmount(lendingApplication.getDisbursalAmount() - insurancePremium);
        } else {
            LendingLoanInsurance loanInsurance = loanUtil.getInsuranceDetails(
                    lendingApplication.getId(), lendingApplication.getLender(), SELECTED);
            if (!ObjectUtils.isEmpty(loanInsurance)) {
                lendingApplication.setDisbursalAmount(lendingApplication.getDisbursalAmount() + loanInsurance.getInsurancePremium());
            }
        }
        lendingApplicationDao.save(lendingApplication);
    }

    public Double updateApr(LendingApplication lendingApplication, Boolean isInsured) {
        Double apr = lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount() - lendingApplication.getProcessingFee(), LoanUtil.getEdiModal(lendingApplication).getNoOfEdiDaysInAWeek(), lendingApplication.getLender());
        if (lendingApplication.getLender().equalsIgnoreCase(Lender.PIRAMAL.name()) && Objects.nonNull(isInsured) && isInsured) {
            LendingLoanInsurance loanInsurance = loanUtil.getInsuranceDetails(
                    lendingApplication.getId(), lendingApplication.getLender(), SELECTED);
            apr = lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount() - lendingApplication.getProcessingFee() - loanInsurance.getInsurancePremium(), LoanUtil.getEdiModal(lendingApplication).getNoOfEdiDaysInAWeek(), lendingApplication.getLender());
        }
        apr = Double.valueOf(String.format("%.2f", apr));
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        if (!ObjectUtils.isEmpty(lendingKfs)) {
            lendingKfs.setApr(apr);
            lendingKfsDao.save(lendingKfs);
        }
        return apr;
    }

    public List<LoanInsuranceDTO> getInsurancePremiums(LendingApplication lendingApplication) {
        List<LoanInsuranceDTO> insurances = null;
        NbfcRequestDto nbfcRequestDto = createInsuranceRequest(lendingApplication);
        if (Objects.nonNull(nbfcRequestDto)) {
            int retry = 0;
            while (retry++ < 3) {
                NbfcResponseDto nbfcResponseDto = piramalApiGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.PiramalAssociationStages.INSURANCE_PREMIUM);
                log.info("Insurance premium response from nbfc: {} with applicationId: {}", nbfcResponseDto, lendingApplication.getId());
                if (Objects.nonNull(nbfcResponseDto) && Objects.nonNull(nbfcResponseDto.getData())) {
                    PInsuranceResponseDTO pInsuranceResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PInsuranceResponseDTO.class);
                    if (nbfcResponseDto.getSuccess() && !pInsuranceResponseDTO.getPremiums().isEmpty()) {
                        insurances = pInsuranceResponseDTO.getPremiums().stream()
                                .map(premiumDetails -> LoanInsuranceDTO.builder()
                                        .insurancePremium(premiumDetails.getPremiumAmount())
                                        .sumInsured(premiumDetails.getSumInsured())
                                        .policyTermsInMonths(premiumDetails.getPolicyTermInMonths())
                                        .product(premiumDetails.getProduct())
                                        .provider(premiumDetails.getProvider())
                                        .isSelected(false)
                                        .build())
                                .collect(Collectors.toList());
                        break;
                    } else {
                        log.error("Error while calling getInsurancePremium API");
                        for (String error : pInsuranceResponseDTO.getErrors()) {
                            log.error(error);
                        }
                    }
                }
            }
        } else {
            log.error("Insurance Premiums not available");
        }
        return insurances;
    }

    public List<LoanInsuranceDTO> isInsuredBefore(LendingApplication lendingApplication) {
        LendingLoanInsurance loanInsurance = loanUtil.getInsuranceDetails(
                lendingApplication.getId(), lendingApplication.getLender(), SELECTED);

        if (ObjectUtils.isEmpty(loanInsurance)) {
            return null;
        } else {
            if (loanInsurance.getSumInsured().equals(lendingApplication.getLoanAmount())
                    && Math.floor((double) loanInsurance.getPolicyTermsInMonths() / 12) == Math.ceil((double) lendingApplication.getTenureInMonths() / 12)) {

                return Collections.singletonList(LoanInsuranceDTO.builder()
                        .insurancePremium(loanInsurance.getInsurancePremium())
                        .sumInsured(loanInsurance.getSumInsured())
                        .policyTermsInMonths(loanInsurance.getPolicyTermsInMonths())
                        .isSelected(true)
                        .provider(loanInsurance.getProvider())
                        .product(loanInsurance.getProduct())
                        .build());

            } else {
                loanInsurance.setStatus("INVALID");
                lendingLoanInsuranceDao.save(loanInsurance);
                return null;
            }
        }
    }

    public NbfcRequestDto createInsuranceRequest(LendingApplication lendingApplication) {

        NbfcRequestDto nbfcRequestDto = null;
        List<String> leadIds = lendingApplicationLenderDetailsDao.findLeadIdByApplicationId(lendingApplication.getId(), lendingApplication.getLender());
        if (leadIds.size() != 1) {
            log.info("Unable to find LeadId for Application ID: {}", lendingApplication.getId());
            return nbfcRequestDto;
        }
        if (lendingApplication.getLender().equalsIgnoreCase(Lender.PIRAMAL.name())) {
            nbfcRequestDto = NbfcRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .payload(PInsuranceRequestDTO.builder()
                            .leadId(leadIds.get(0))
                            .loanAmount(lendingApplication.getLoanAmount())
                            .loanTenureInMonths(lendingApplication.getTenureInMonths())
                            .build())
                    .build();
            return nbfcRequestDto;
        }
        return nbfcRequestDto;
    }

    public void saveInsurancePremiums(LendingApplication lendingApplication, List<LoanInsuranceDTO> insurances, Boolean isInsured) {

        List<LendingLoanInsurance> loanInsurances = lendingLoanInsuranceDao.findAllByApplicationIdAndLender(
                lendingApplication.getId(), lendingApplication.getLender());

        if ((Objects.isNull(isInsured) || !isInsured) && Objects.nonNull(loanInsurances)) {
            loanInsurances.forEach(lendingLoanInsurance -> {
                lendingLoanInsurance.setStatus("INVALID");
                lendingLoanInsuranceDao.save(lendingLoanInsurance);
            });
        }
        if (Objects.nonNull(isInsured) && isInsured) {
            for (LoanInsuranceDTO insurance : insurances) {
                LendingLoanInsurance loanInsurance = LendingLoanInsurance.builder()
                        .applicationId(lendingApplication.getId())
                        .lender(lendingApplication.getLender())
                        .insurancePremium(insurance.getInsurancePremium())
                        .sumInsured(insurance.getSumInsured())
                        .policyTermsInMonths(insurance.getPolicyTermsInMonths())
                        .provider(insurance.getProvider())
                        .product(insurance.getProduct())
                        .build();
                if (insurance.getIsSelected())
                    loanInsurance.setStatus("SELECTED");
                else {
                    loanInsurance.setStatus("VALID");
                }
                lendingLoanInsuranceDao.save(loanInsurance);
            }
        }
    }

    public void updateConsent(LendingApplication lendingApplication, Boolean isInsured) {
        LendingConsent lendingConsent = lendingConsentDao.findLendingConsentByApplicationIdAndMerchantIdAndConsentType(
                lendingApplication.getId(), lendingApplication.getMerchantId(), "INSURANCE");

        if (Objects.nonNull(isInsured)) {
            if (Objects.nonNull(lendingConsent)) {
                lendingConsent.setIsAccepted(isInsured);
                lendingConsentDao.save(lendingConsent);
            } else {
                lendingConsent = LendingConsent.builder()
                        .applicationId(lendingApplication.getId())
                        .merchantId(lendingApplication.getMerchantId())
                        .consentType("INSURANCE")
                        .isAccepted(isInsured)
                        .build();
                lendingConsentDao.save(lendingConsent);
            }
        } else {
            if (Objects.nonNull(lendingConsent)) {
                lendingConsent.setIsAccepted(false);
                lendingConsentDao.save(lendingConsent);
            }
        }
    }

    public Double getAnnualRoi(LendingApplicationLenderDetails lendingApplicationLenderDetails, LendingApplication lendingApplication){

        // for old lenders
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
            lendingApplicationLenderDetails.setApplicationId(lendingApplication.getId());
            lendingApplicationLenderDetails.setLender(lendingApplication.getLender());
            lendingApplicationLenderDetails.setStatus(Status.ACTIVE.name());
            lendingApplicationLenderDetails.setBreStatus(LenderAssociationStatus.OLD_MODEL.name());
            lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.OLD_MODEL.name());
            lendingApplicationLenderDetails.setStage(LenderAssociationStages.COMPLETED.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        }

        if(lendingApplicationLenderDetails.getAnnualRoi() == null){

            DecimalFormat df = new DecimalFormat("#.##");
            df.setRoundingMode(RoundingMode.DOWN);

            Double annualRoi = Double.valueOf(df.format(
                    lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getLoanAmount(),
                            LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.getLender())));

            lendingApplicationLenderDetails.setAnnualRoi(annualRoi);
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);

            log.info("Calculated AnnualRoi {}", annualRoi);

            return annualRoi;

        }

        return lendingApplicationLenderDetails.getAnnualRoi();
    }
}
