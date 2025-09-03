package com.bharatpe.lending.lendingplatform.lending.util;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.utils.NotificationUtil;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.RedisNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class LendingUtil {

	@Autowired
	private LendingApplicationDao lendingApplicationDao;

	@Autowired
	private LendingAuditTrialDao lendingAuditTrialDao;

	@Autowired
	private RedisNotificationService redisNotificationService;

	@Autowired
	private LendingNotificationService lendingNotificationService;

	@Autowired
	private MerchantService merchantService;

	@Autowired
	private NotificationUtil notificationUtil;

	@Autowired
	private KycHandler kycHandler;

	@Autowired
	private KycUtils kycUtils;

	@Autowired
	@Qualifier("ConfluentKafkaTemplate")
	KafkaTemplate<String, Object> confluentKafkaTemplate;

	@Autowired
	@Qualifier("LoanJourneyKafkaTemplate")
	KafkaTemplate<String, Object> loanJourneyKafkaTemplate;

	@Value("${kafka.topic.postChecks:lending_post_application_submission_checks}")
	String kafkaTopicPostChecks;

	@Async
	public void createAuditEvent(
			LendingApplication lendingApplication,
			BasicDetailsDto basicDetailsDto,
			String oldStatus,
			String newStatus,
			String type) {
		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setApplicationId(lendingApplication.getId());
		lendingAuditTrial.setMerchantId(basicDetailsDto.getId());
		lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setOldStatus(oldStatus);
		lendingAuditTrial.setNewStatus(newStatus);
		lendingAuditTrial.setType(type);

		lendingAuditTrialDao.save(lendingAuditTrial);
	}

	@Async
	public void redisNotification(LendingApplication lendingApplication, BasicDetailsDto basicDetailsDto) {
		redisNotificationService.sendPendingEnachNotification(basicDetailsDto, lendingApplication);
	}

	@Async
	public void sendNotification(LendingApplication lendingApplication, BasicDetailsDto merchant) {

		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		if (merchantBankDetail == null) {
			return;
		}

		Double loanAmount = lendingApplication.getLoanAmount();
		String identifier;

		String deeplink = notificationUtil.getDeeplink(merchant.getSettlementType(), "LOAN_DASHBOARD");
		Map<String, Object> templateParams = new HashMap<>();
		NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();

		if (Objects.nonNull(lendingApplication.getNachStatus())) {
			identifier = "LENDING_NEW_APPLICATION_RECEIVED_PUSH";
			templateParams.put("expected_days", "3-5");
			notificationPayloadDto.setTemplateIdentifier(identifier);
			notificationPayloadDto.setTemplateParams(templateParams);
			notificationPayloadDto.setMobile(merchant.getMobile());
			notificationPayloadDto.setPushDeepLink(deeplink);
			notificationPayloadDto.setPushTitle("Loan Application " + lendingApplication.getExternalLoanId() + " Under Review!");
			notificationPayloadDto.setClientName("LENDING");
			lendingNotificationService.notify(notificationPayloadDto);
		} else {
			identifier = "LENDING_NEW_APPLICATION_RECEIVED_2_PUSH";
			templateParams.put("loan_amount", loanAmount);
			notificationPayloadDto.setTemplateIdentifier(identifier);
			notificationPayloadDto.setTemplateParams(templateParams);
			notificationPayloadDto.setMobile(merchant.getMobile());
			notificationPayloadDto.setPushDeepLink(deeplink);
			notificationPayloadDto.setPushTitle("You are one step away from Loan Transfer!");
			notificationPayloadDto.setClientName("LENDING");
			lendingNotificationService.notify(notificationPayloadDto);
		}

		identifier = "LENDING_AGENT_SMS";
		notificationPayloadDto.setTemplateIdentifier(identifier);
		lendingNotificationService.notify(notificationPayloadDto);
	}

	public void updateKycStatus(LendingApplication lendingApplication) {
		log.info("Lending application status for application: {}, : {} and ckycId is: {} and ckyc status: {}",
				lendingApplication.getId(), lendingApplication.getStatus(), lendingApplication.getCkycId(), lendingApplication.getCkycStatus());

		try {
			KycStatusDTO kycStatus = Boolean.TRUE.equals(kycUtils.isEligibleForLenderKyc(
					lendingApplication.getLender(),
					lendingApplication.getMerchantId(),
					LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))) ?
					kycHandler.getKycStatusForLenderKycOrSkipKycPipe(lendingApplication.getMerchantId()) :
					kycHandler.getKycStatus(lendingApplication.getMerchantId());
			log.info("kyc status:{} for application:{}", kycStatus, lendingApplication.getId());
			lendingApplication.setCkycStatus(kycStatus.getKycStatus().name());
			lendingApplication.setCkycDate(new Date());
			if (kycStatus.getKycStatus().equals(KycStatus.REJECTED)) {
				lendingApplication.setCkycRejectionReason(kycStatus.getRemarks());
				lendingApplication.setStatus(KycStatus.REJECTED.name().toLowerCase());
				lendingApplicationDao.save(lendingApplication);
			}
			lendingApplicationDao.save(lendingApplication);
			log.info("Lending application status after kyc for application: {}, : {} and ckycId is: {} and ckyc status: {}",
					lendingApplication.getId(), lendingApplication.getStatus(), lendingApplication.getCkycId(), lendingApplication.getCkycStatus());

		} catch (Exception e) {
			log.error("Exception in updateKycStatus for application:{}", lendingApplication.getId());
		}
	}

	@Async
	public void sendLatLong(Long merchantId, Long applicationId) {
		try {
			Map<String, Long> detailMap = new HashMap<String, Long>() {{
				put("merchantId", merchantId);
				put("applicationId", applicationId);
			}};
			loanJourneyKafkaTemplate.send("find_lat_long", merchantId.toString(), detailMap);
			log.info("Pushed " + detailMap + " to topic find_lat_long");
		} catch (Exception e) {
			log.error("Error occured while pushing to topic find_lat_long", e);
		}
	}

	@Async
	public void sendDetailsForContactsVerification(Long merchantId, Long applicationId) {
		try {
			Map<String, Long> detailMap = new HashMap<>();
			detailMap.put("merchantId", merchantId);
			detailMap.put("applicationId", applicationId);
			loanJourneyKafkaTemplate.send(kafkaTopicPostChecks, merchantId.toString(), detailMap);
			log.info("Pushed {} to topic verify_contacts_for_application", detailMap);
		} catch (Exception e) {
			log.error("Error occured while pushing to topic verify_contacts_for_application", e);
		}
	}

	@Async
	public void sendDuplicatePancardCheck(Long merchantId, Long applicationId) {
		try {
			Map<String, Long> detailMap = new HashMap<String, Long>() {{
				put("merchantId", merchantId);
				put("applicationId", applicationId);
			}};
			loanJourneyKafkaTemplate.send("check_duplicate_pancard", merchantId.toString(), detailMap);
			log.info("Pushed " + detailMap + " to topic check_duplicate_pancard");
		} catch (Exception e) {
			log.error("Error occured while pushing to topic check_duplicate_pancard", e);
		}
	}
}
