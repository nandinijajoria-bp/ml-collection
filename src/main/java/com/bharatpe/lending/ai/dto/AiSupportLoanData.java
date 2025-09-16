package com.bharatpe.lending.ai.dto;

import com.bharatpe.lending.dto.ApplicationDetailsDTO;
import com.bharatpe.lending.dto.LoanDetailsDTO;
import com.bharatpe.lending.dto.SupportApiResponseDto;
import com.bharatpe.lending.dto.SupportLoanResponseDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiSupportLoanData {
    private Long merchantId;
    private Boolean activeLoan;
    private Boolean eligible;
    private Boolean experian;
    private Boolean applied;
    private String message;
    private String conditionalMessage;
    private Boolean eNachDone;
    private String applicationStatus;
    private Boolean nachMandatory;
    private String beneficiaryName;
    private String businessName;
    private String city;
    private String bankAccount;
    private Boolean creditLineAccount;
    private AiLoanApplication loanApplication;
    private SupportLoanResponseDTO.Eligibility eligibility;
    private List<AiLoanDetails> loanDetailsList;
    private AiLoanArrangerFee loanArrangerFee;
    private AiSupportApiResponse supportApiResponseDto;
    private List<AiApplicationDetails> applicationHistory;
    private Boolean bsUploadEligibility;
    private Boolean AaEligibility;
    private Date nbfcSendDate;
    private Date nachCompletionDate;

    public AiSupportLoanData(SupportLoanResponseDTO dto) {
        this.merchantId = dto.getMerchantId();
        this.activeLoan = dto.getActiveLoan();
        this.eligible = dto.getEligible();
        this.experian = dto.getExperian();
        this.applied = dto.getApplied();
        this.message = dto.getMessage();
        this.conditionalMessage = dto.getConditionalMessage();
        this.eNachDone = dto.geteNachDone();
        this.applicationStatus = dto.getApplicationStatus();
        this.nachMandatory = dto.getNachMandatory();
        this.beneficiaryName = dto.getBeneficiaryName();
        this.businessName = dto.getBusinessName();
        this.city = dto.getCity();
        this.bankAccount = dto.getBankAccount();
        this.creditLineAccount = dto.getCreditLineAccount();
        this.loanApplication = new AiLoanApplication(dto.getLoanApplication());
        this.eligibility = dto.getEligibility();
        if(!CollectionUtils.isEmpty(dto.getLoanDetailsList())){
            this.loanDetailsList = dto.getLoanDetailsList().stream().map(AiLoanDetails::new).collect(Collectors.toList());
        }
        this.loanArrangerFee = new AiLoanArrangerFee(dto.getLoanArrangerFee());
        this.supportApiResponseDto = new AiSupportApiResponse(dto.getSupportApiResponseDto());
        if(!CollectionUtils.isEmpty(dto.getApplicationHistory())){
            this.applicationHistory = dto.getApplicationHistory().stream().map(AiApplicationDetails::new).collect(Collectors.toList());
        }
        this.bsUploadEligibility = dto.getBsUploadEligibility();
        this.AaEligibility = dto.getAaEligibility();
        this.nbfcSendDate = dto.getNbfcSendDate();
        this.nachCompletionDate = dto.getNachCompletionDate();
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AiLoanArrangerFee {
        private Double feeAmount;
        private Boolean arrangerFeeRefundEligible;
        private Boolean arrangerFeeRefunded;
        private Date timestamp;
        private String inEligibleReason;
        private String utr;
        private String refundType;

        public AiLoanArrangerFee(SupportLoanResponseDTO.LoanArrangerFee src) {
            if (src != null) {
                this.feeAmount = src.getFeeAmount();
                this.arrangerFeeRefundEligible = src.getArrangerFeeRefundEligible();
                this.arrangerFeeRefunded = src.getArrangerFeeRefunded();
                this.timestamp = src.getTimestamp();
                this.inEligibleReason = src.getInEligibleReason();
                this.utr = src.getUtr();
                this.refundType = src.getRefundType();
            }
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AiLoanApplication {
        private Date applicationSubmittedDate;
        private String loanId;
        private Double loanAmount;
        private Double ediAmount;
        private String tenure;
        private Double interestRate;
        private Double repayment;
        private Date applicationRecvDate;
        private Long applicationId;
        private Date processingDate;
        private Date relevantDate;
        private String lender;

        public AiLoanApplication(SupportLoanResponseDTO.LoanApplication src) {
            if (src != null) {
                this.applicationSubmittedDate = src.getApplicationSubmittedDate();
                this.loanId = src.getLoanId();
                this.loanAmount = src.getLoanAmount();
                this.ediAmount = src.getEdiAmount();
                this.tenure = src.getTenure();
                this.interestRate = src.getInterestRate();
                this.repayment = src.getRepayment();
                this.applicationRecvDate = src.getApplicationRecvDate();
                this.applicationId = src.getApplicationId();
                this.processingDate = src.getProcessingDate();
                this.relevantDate = src.getRelevantDate();
                this.lender = src.getLender();
            }
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AiSupportApiResponse {
        private Long merchantId;
        private String applicationStage;
        private Boolean experian;
        private Boolean eligible;
        private String ineligibleType;
        private Long reapplyTime;
        private Integer pincode;
        private String priority;
        private String applicationStatus;
        private String lender;
        private Integer remainingTat;
        private Integer tat;
        private Boolean applied;
        private Boolean tatBreached;
        private Boolean fiRequired;
        private Boolean eligibleToApplyAgain;
        private Boolean enachDone;
        private String currentStage;
        private String loanType;
        private Date agreementAt;
        private Double processingFee;
        private String processingType;
        private String applicationRejectReason;
        private String processingStage;
        private Boolean activeLoan;
        private Integer dpd;
        private Boolean eligibleForTopUp;
        private Boolean eligibleForRepeat;
        private Boolean pfRefunded;
        private Date closingDate;
        private String disbursalUtr;
        private List<AiLendingPaymentSchedule> closedLoans;
        private String clubStatus;
        private String stageCommunication;

        public AiSupportApiResponse(SupportApiResponseDto src) {
            if (src != null) {
                this.merchantId = src.getMerchantId();
                this.applicationStage = src.getApplicationStage();
                this.experian = src.getExperian();
                this.eligible = src.getEligible();
                this.ineligibleType = src.getIneligibleType();
                this.reapplyTime = src.getReapplyTime();
                this.pincode = src.getPincode();
                this.priority = src.getPriority();
                this.applicationStatus = src.getApplicationStatus();
                this.lender = src.getLender();
                this.remainingTat = src.getRemainingTat();
                this.tat = src.getTat();
                this.applied = src.getApplied();
                this.tatBreached = src.getTatBreached();
                this.fiRequired = src.getFiRequired();
                this.eligibleToApplyAgain = src.getEligibleToApplyAgain();
                this.enachDone = src.getEnachDone();
                this.currentStage = src.getCurrentStage();
                this.loanType = src.getLoanType();
                this.agreementAt = src.getAgreementAt();
                this.processingFee = src.getProcessingFee();
                this.processingType = src.getProcessingType();
                this.applicationRejectReason = src.getApplicationRejectReason();
                this.processingStage = src.getProcessingStage();
                this.activeLoan = src.getActiveLoan();
                this.dpd = src.getDpd();
                this.eligibleForTopUp = src.getEligibleForTopUp();
                this.eligibleForRepeat = src.getEligibleForRepeat();
                this.pfRefunded = src.getPfRefunded();
                this.closingDate = src.getClosingDate();
                this.disbursalUtr = src.getDisbursalUtr();
                if(!CollectionUtils.isEmpty(src.getClosedLoans())){
                    this.closedLoans = src.getClosedLoans().stream().map(AiLendingPaymentSchedule::new).collect(Collectors.toList());
                }
                this.clubStatus = src.getClubStatus();
                this.stageCommunication = src.getStageCommunication();
            }
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AiLoanDetails {
        private String externalLoanId;
        private Double loanAmount;
        private String tenure;
        private Date disbursalDate;
        private Double interestRate;
        private Double ediAmount;
        private Integer remainingEdiCount;
        private Date nextEdiDate;
        private Double paidAmount;
        private Date tentativeClosingDate;
        private Date closingDate;
        private Double repayment;
        private String status;
        private AiLoanArrangerFee loanArrangerFee;
        private Double processingFee;
        private String arrangerFeeStatus;
        private String nocUrl;
        private Double forClosureAmount;
        private String lender;
        private String disbursalUtr;
        private String ediModel;

        public AiLoanDetails(LoanDetailsDTO src) {
            if (src != null) {
                this.externalLoanId = src.getExternalLoanId();
                this.loanAmount = src.getLoanAmount();
                this.tenure = src.getTenure();
                this.disbursalDate = src.getDisbursalDate();
                this.interestRate = src.getInterestRate();
                this.ediAmount = src.getEdiAmount();
                this.remainingEdiCount = src.getRemainingEdiCount();
                this.nextEdiDate = src.getNextEdiDate();
                this.paidAmount = src.getPaidAmount();
                this.tentativeClosingDate = src.getTentativeClosingDate();
                this.closingDate = src.getClosingDate();
                this.repayment = src.getRepayment();
                this.status = src.getStatus();
                this.loanArrangerFee = src.getLoanArrangerFee() != null ? new AiLoanArrangerFee(src.getLoanArrangerFee()) : null;
                this.processingFee = src.getProcessingFee();
                this.arrangerFeeStatus = src.getArrangerFeeStatus();
                this.nocUrl = src.getNocUrl();
                this.forClosureAmount = src.getForClosureAmount();
                this.lender = src.getLender();
                this.disbursalUtr = src.getDisbursalUtr();
                this.ediModel = src.getEdiModel();
            }
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AiApplicationDetails {
        private String externalLoadId;
        private Date agreementDate;
        private Double loanAmount;
        private String status;
        private Date decisioningDate;
        private String reason;
        private Double interestRate;
        private String tenure;
        private AiLoanArrangerFee arrangerFee;
        private String sendToNbfc;
        private String lender;
        private Boolean isInsured;
        private Double insurancePremium;

        public AiApplicationDetails(ApplicationDetailsDTO src) {
            if (src != null) {
                this.externalLoadId = src.getExternalLoadId();
                this.agreementDate = src.getAgreementDate();
                this.loanAmount = src.getLoanAmount();
                this.status = src.getStatus();
                this.decisioningDate = src.getDecisioningDate();
                this.reason = src.getReason();
                this.interestRate = src.getInterestRate();
                this.tenure = src.getTenure();
                this.arrangerFee = src.getArrangerFee() != null ? new AiLoanArrangerFee(src.getArrangerFee()) : null;
                this.sendToNbfc = src.getSendToNbfc();
                this.lender = src.getLender();
                this.isInsured = src.getIsInsured();
                this.insurancePremium = src.getInsurancePremium();
            }
        }
    }
}
