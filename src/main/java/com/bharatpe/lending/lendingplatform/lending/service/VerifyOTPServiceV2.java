package com.bharatpe.lending.lendingplatform.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.CleverTapEvents;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.lendingplatform.lending.util.LendingUtil;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistry;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.LoanDocumentWorkflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.WorkflowManager;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.service.CleverTapEventService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class VerifyOTPServiceV2 {

	@Autowired
	private LendingApplicationDao lendingApplicationDao;

	@Autowired
	private BharatPeOtpHandler bharatPeOtpHandler;

	@Autowired
	private LendingApplicationLenderDetailsDao laldDao;

	@Autowired
	private LendingPaymentScheduleDao lpsDao;

	@Autowired
	private DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

	@Autowired
	private LendingShopDocumentsDao lendingShopDocumentsDao;

	@Autowired
	private LoanUtil loanUtil;

	@Autowired
	private MerchantService merchantService;

	@Autowired
	private LoanDashboardService loanDashboardService;

	@Autowired
	private FunnelService funnelService;

	private ExecutorService executorService = Executors.newFixedThreadPool(10);

	@Autowired
	private CleverTapEventService cleverTapEventService;

	@Lazy
	@Autowired
	private LendingApplicationServiceV2 lendingApplicationServiceV2;

	@Autowired
	private LoanDetailsV3Service loanDetailsV3Service;

	@Autowired
	private LoanDocumentWorkflow loanDocumentWorkflow;

	@Autowired
	private WorkflowRegistryFactory workflowRegistryFactory;

	@Autowired
	private LendingUtil lendingUtil;


	public Map<String, Object> verifyOTP(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {

		Long applicationId = commonAPIRequest.getPayload().get("application_id") != null ?
				Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
		String otp = commonAPIRequest.getPayload().get("otp") != null ?
				commonAPIRequest.getPayload().get("otp").toString() : null;
		String uuid = commonAPIRequest.getPayload().get("uuid") != null ?
				commonAPIRequest.getPayload().get("uuid").toString() : null;

		LendingApplication lendingApplication = getDraftLendingApplication(applicationId, merchant);
		if (ObjectUtils.isEmpty(lendingApplication) || StringUtils.isEmpty(otp)) {
			log.error("No Application Found in draft for application_id: {} or OTP is empty: {}", applicationId, otp);
			returnFailedResponse();
		}

		return verifyOtpAndUpdateApplication(lendingApplication, otp, uuid, merchant, commonAPIRequest.getMeta());
	}

	private Map<String, Object> verifyOtpAndUpdateApplication(LendingApplication lendingApplication, String otp, String uuid, BasicDetailsDto merchant, Meta meta) {

		if (!verifyOTP(lendingApplication, merchant, otp, uuid)
				|| activeLoanPresent(merchant)
				|| documentNotAvailable(lendingApplication)) {
			return returnFailedResponse();
		}
		boolean isNachSkippable = loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender());
		MerchantNachDetailsResponseDTO enachSuccess = getSuccessEnach(lendingApplication, isNachSkippable);

		updateApplication(lendingApplication, merchant, meta, enachSuccess, isNachSkippable);
		sendClevertapEvents(lendingApplication, merchant);

		if (!generateDocuments(lendingApplication, merchant)) {
			return returnFailedResponse();
		}
		if ("APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus())) {
			invokeDocUploadAndNachWorflow(lendingApplication);
		}

		lendingApplication.setStatus("pending_verification");
		lendingApplicationDao.save(lendingApplication);

		lendingUtil.createAuditEvent(
				lendingApplication, merchant, "draft", "pending_verification", "APP_STATUS");

		if(loanUtil.isEligibleForUpiAutopayDedicatedScreen(lendingApplication) && !LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
			loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.UPI_AUTOPAY_PAGE);
			log.info("Saving Lending Application Details for application: {} with view state: {}", lendingApplication.getId(), LendingViewStates.UPI_AUTOPAY_PAGE);
		} else {
			loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.ENACH_PAGE);
			log.info("Saving Lending Application Details for application: {} with view state: {}", lendingApplication.getId(), LendingViewStates.ENACH_PAGE);
		}

		lendingUtil.redisNotification(lendingApplication, merchant);
		lendingUtil.sendNotification(lendingApplication, merchant);
		lendingUtil.updateKycStatus(lendingApplication);

		lendingUtil.sendLatLong(merchant.getId(), lendingApplication.getId());
		if (!loanUtil.isInternalMerchant(lendingApplication.getMerchantId())) {
			if (Objects.nonNull(enachSuccess) || isNachSkippable) {
				log.info("entered before sending to topic for post checks for application_id :  {}", lendingApplication.getId());
				lendingUtil.sendDetailsForContactsVerification(merchant.getId(), lendingApplication.getId());
			}
		}
		lendingUtil.sendDuplicatePancardCheck(merchant.getId(), lendingApplication.getId());
		loanUtil.publishApplicationEvent(lendingApplication);

		return returnSuccessResponse();
	}


	private void invokeDocUploadAndNachWorflow(LendingApplication lendingApplication) {
		log.info("NACH Approved for application: {}", lendingApplication.getId());

		LendingApplicationLenderDetails lald =
				laldDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
						lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
		if (!ObjectUtils.isEmpty(lald)) {
			WorkflowStage nextStage = WorkflowManager.getNextWorkflowStage(lendingApplication.getLender(), lald.getLeadStatus());
			WorkflowRegistry workflowRegistry = workflowRegistryFactory
					.getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender()));
			List<Workflow> workflows = workflowRegistry.getStageWorkflow(nextStage);
			WorkflowUtil.invokeWorkflows(workflows, lendingApplication.getId());
		}
	}

	private boolean generateDocuments(LendingApplication lendingApplication, BasicDetailsDto merchant) {
		try {
			// TODO: add log for how much time it took to generate docs
			lendingApplicationServiceV2.storeApplicationDocs(lendingApplication.getId(), lendingApplication, merchant);
		} catch (Exception e) {
			loanDetailsV3Service.saveApplicationViewState(
					null, lendingApplication.getId(), LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
			log.error("Exception in storing Loan documents for applicationId : {}, {}, {}",
					lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
			return false;
		}
		return true;
	}

	private boolean documentNotAvailable(LendingApplication lendingApplication) {
		Long merchantId = lendingApplication.getMerchantId();
		Long applicationId = lendingApplication.getId();
		List<DocumentsIdProofMaster> documentsIdProofList = documentsIdProofDaoMaster.findByMerchantIdAndLendingApplicationId(merchantId, applicationId);
		List<LendingShopDocuments> shopDocuments = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
		if ((ObjectUtils.isEmpty(lendingApplication.getCkycId())) && (ObjectUtils.isEmpty(documentsIdProofList) || ObjectUtils.isEmpty(shopDocuments))) {
			log.error("documents not found for application:{}", applicationId);
			return true;
		}
		return false;
	}

	private void updateApplication(
			LendingApplication lendingApplication,
			BasicDetailsDto merchant,
			Meta meta,
			MerchantNachDetailsResponseDTO enachSuccess,
			boolean isNachSkippable) {

		updateAgreementAndLocation(lendingApplication, meta);
		if (isNachSkippable || !ObjectUtils.isEmpty(enachSuccess)) {
			updateNachDetails(lendingApplication, enachSuccess, isNachSkippable);
		}
	}

	private void sendClevertapEvents(LendingApplication lendingApplication, BasicDetailsDto merchant) {
		LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(lendingApplication.getMerchantId(), lendingApplication);
		if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
			funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
					FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
			Map<String, String> cleverTapEvtData = new HashMap<String, String>() {{
				put("loanAmount", lendingApplication.getLoanAmount().toString());
				put("beneficiaryName", lendingApplication.getMerchantName());
				put("businessName", lendingApplication.getBusinessName());
				put("loanType", lendingApplication.getLoanType());
			}};
			executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_APPLICATION_COMPLETED_BE.name(), cleverTapEvtData, merchant.getMid()));
		}
		else{
			funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
					FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
		}
	}

	private void updateNachDetails(
			LendingApplication lendingApplication, MerchantNachDetailsResponseDTO enachSuccess, boolean isNachSkippable) {

		lendingApplication.setNachType("ENACH");
		if((!ObjectUtils.isEmpty(enachSuccess) && !ObjectUtils.isEmpty(enachSuccess.getNachLender())) || isNachSkippable) {
			lendingApplication.setNachLender(ObjectUtils.isEmpty(enachSuccess) ?
					loanUtil.enachServiceLenderMapper(lendingApplication.getLender()) : enachSuccess.getNachLender());
		} else {
			lendingApplication.setNachLender("BHARATPE");
		}
		lendingApplication.setNachReferenceNumber(ObjectUtils.isEmpty(enachSuccess) ? null : enachSuccess.getReferenceNumber());
		lendingApplication.setNachStatus("APPROVED");
	}

	private MerchantNachDetailsResponseDTO getSuccessEnach(LendingApplication lendingApplication, boolean isNachSkippable) {
		MerchantNachDetailsResponseDTO enachSuccess =
				loanUtil.getSuccessNach(lendingApplication.getMerchantId(), lendingApplication.getId());

		if(ObjectUtils.isEmpty(enachSuccess) && isNachSkippable){
			enachSuccess = loanUtil.getSuccessNach(lendingApplication.getMerchantId(), lendingApplication.getLender());
		}

		final Optional<BankDetailsDto> bankDetailsDtoOptional =
				merchantService.fetchMerchantBankDetails(lendingApplication.getMerchantId());
		if (bankDetailsDtoOptional.isPresent()) {
			BankDetailsDto merchantBankDetail = bankDetailsDtoOptional.get();
			if (!ObjectUtils.isEmpty(enachSuccess) && !ObjectUtils.isEmpty(merchantBankDetail)
					&& !ObjectUtils.isEmpty(enachSuccess.getAccountNumber())
					&& !enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
				enachSuccess = null;
			}
		}
		log.info("enach success status: {} for application:{}", enachSuccess, lendingApplication.getId());
		return enachSuccess;
	}

	private void updateAgreementAndLocation(LendingApplication lendingApplication, Meta meta) {
		String loanId = "BPL" + DateTimeUtil.getDateInFormat(new Date(), "ddMMyy") + lendingApplication.getId();
		lendingApplication.setAgreementAt(new Date());
		lendingApplication.setAgreement(1);
		lendingApplication.setIp(meta.getIp());
		lendingApplication.setExternalLoanId(ObjectUtils.isEmpty(lendingApplication.getExternalLoanId()) ?
				loanId : lendingApplication.getExternalLoanId());

		if (!ObjectUtils.isEmpty(meta)) {
			if (validLatitudeLongitude(meta.getLatitude())) {
				lendingApplication.setLatitude(meta.getLatitude());
			}
			if (validLatitudeLongitude(meta.getLongitude())) {
				lendingApplication.setLongitude(meta.getLongitude());
			}
		}
	}

	private boolean validLatitudeLongitude(String value) {
		return !StringUtils.isEmpty(value)
				&& !value.trim().equalsIgnoreCase("undefined")
				&& !value.trim().equalsIgnoreCase("null")
				&& !value.trim().equalsIgnoreCase("");
	}

	private boolean activeLoanPresent(BasicDetailsDto merchant) {
		LendingApplication openApplication = lendingApplicationDao.findOpenApplication(merchant.getId());
		LendingPaymentSchedule activeLoan = lpsDao.getOldestActiveLoan(merchant.getId());
		if (!ObjectUtils.isEmpty(openApplication) || !ObjectUtils.isEmpty(activeLoan)) {
			log.error("Active loan present for merchant: {}", merchant.getId());
			return true;
		}
		return false;
	}

	private boolean verifyOTP(LendingApplication lendingApplication, BasicDetailsDto merchant, String otp, String uuid) {
		if (merchant.getMobile().length() == 12) {
			boolean isOTPVerified = bharatPeOtpHandler.verifyOtp(merchant, otp, uuid);
			if (isOTPVerified) {
				LendingApplicationLenderDetails lendingApplicationLenderDetails =
						laldDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
								lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
				lendingApplicationLenderDetails.setAgreementOtp(otp);
				laldDao.save(lendingApplicationLenderDetails);
				return true;
			}
		}
		return false;
	}

	private LendingApplication getDraftLendingApplication(Long applicationId, BasicDetailsDto merchant) {
		if (ObjectUtils.isEmpty(applicationId) || applicationId <= 0L) {
			log.error("Application ID is null: {}", applicationId);
			return null;
		}
		return lendingApplicationDao.findByIdAndMerchantIdAndStatus(
				applicationId, merchant.getId(), "draft");
	}

	public Map<String, Object> returnSuccessResponse() {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		response.put("agreement_verified", true);
		return response;
	}

	public Map<String, Object> returnFailedResponse() {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", false);
		response.put("agreement_verified", false);
		return response;
	}

}
