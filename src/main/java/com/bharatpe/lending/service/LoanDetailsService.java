package com.bharatpe.lending.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.dao.LoanDetailsDao;
import com.bharatpe.common.dao.MerchantAddressDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.entities.Agent;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.BankList;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingAuditTrial;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantAddress;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.lending.dao.AgentDao;
import com.bharatpe.lending.dao.BankListDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.DocumentDTO;
import com.bharatpe.lending.dto.LabelDTO;
import com.bharatpe.lending.dto.LoanApplicationDTO;
import com.bharatpe.lending.dto.LoanDetailsRequestDTO;
import com.bharatpe.lending.dto.LoanDetailsResponseDTO;
import com.bharatpe.lending.dto.LoanDetailsResponseDTO.LoanDetailsDTO;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.dto.LoanHistoryDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.SelectedLoanDTO;
import com.bharatpe.lending.dto.ShopDetailsDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;
import com.bharatpe.common.enums.MerchantCategory;
import com.bharatpe.common.enums.Status.GeneralStatus;
import com.bharatpe.common.enums.Status.LendingStatus;

@Service
public class LoanDetailsService {
	private Logger logger = LoggerFactory.getLogger(LoanDetailsService.class);
	
	List<String> validAgentCities = Arrays.asList("Bangalore","Hyderabad","Pune","Delhi","Noida","Gurgaon","Mumbai");
	List<String> validDIYCities = Arrays.asList("Bengaluru","Pune","Delhi","Noida","Gurgaon","Faridabad","Ghaziabad","Thane","Mumbai","Hyderabad");
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	BankListDao bankListDao;
	
	@Autowired
	MerchantSummaryDao merchantSummaryDao;
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;
	
	@Autowired
	LoanDetailsDao loanDetailsDao;
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;
	
	@Autowired
	AgentDao agentDao;
	
	@Autowired
	MerchantAddressDao merchantAddressDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;

	public LoanDetailsResponseDTO fetchLoanDetails(Merchant merchant, RequestDTO<LoanDetailsRequestDTO> requestDTO) {
		LoanDetailsResponseDTO response = new LoanDetailsResponseDTO();
		try {
			boolean eligibleFlag = true;
			
			List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findByMerchantIdOrderByIdDesc(merchant.getId());

			LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);

			List<LendingApplication> lendingApplicationList = lendingApplicationDao.fetchLatestOpenApplication(merchant);
			
			LendingApplication lendingApplication = null;
			if(lendingApplicationList != null && !lendingApplicationList.isEmpty()) {
				lendingApplication = lendingApplicationList.get(0);
			}

			List<LoanHistoryDTO> loanHistoryDTOs = fetchLoanHistory(lendingApplication, lendingPaymentScheduleList, activeLoan);
			LoanApplicationDTO loanApplicationDTO = fetchLoanApplication(merchant, lendingApplication);
			List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();

			if(activeLoan != null) {
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setEligibility(loanEligibilityDTOs);
				loanDetailsDTO.setHistory(loanHistoryDTOs);
				loanDetailsDTO.setEligible(false);
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}

			if(lendingApplication != null) {
				if("rejected".equals(lendingApplication.getStatus())) {
					LendingAuditTrial auditTrial = lendingAuditTrialDao.findByMerchantIdAndApplicationIdAndNewStatus(lendingApplication.getMerchant().getId(), lendingApplication.getId(), "REJECTED");
					if("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil()) || rejectedInLastNDays(auditTrial, 7)) {
						eligibleFlag = false;
						loanHistoryDTOs = null;
						loanApplicationDTO.setStatusTitle("'Verification Failed!'");
						if("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
							loanApplicationDTO.setStatusMessage("We regret to inform you that we are unable to process your application as it does not meet the guidelines for document assessment. Please write to us on  support@bharatpe.com to apply again.");
						} else {
							Date rejecetdAt = auditTrial.getCreatedAt();
							Calendar calender = Calendar.getInstance();
							calender.setTime(rejecetdAt);
							calender.add(Calendar.DATE, 7);
							loanApplicationDTO.setStatusMessage("Please revisit the page after " + new SimpleDateFormat("dd-MM-yyyy").format(calender.getTime()) + " to check your eligibility and apply again.");
							
						}
					}

				} else if ("approved".equals(lendingApplication.getStatus()) && !"disbursed".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
					eligibleFlag = false;
					loanApplicationDTO.setStatusTitle("Application submitted successfully!");
					loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification. Please keep a cheque of total loan amount & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.");
				} else if ("pending_verification".equals(lendingApplication.getStatus())) {
					eligibleFlag = false;
					loanHistoryDTOs = null;
					loanApplicationDTO.setStatusTitle("Application submitted successfully!");
					loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification. Please keep a cheque of total loan amount & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.");
				} else if("draft".equals(lendingApplication.getStatus())) {
					eligibleFlag = false;
					loanHistoryDTOs = null;
				}

				if(!eligibleFlag) {
					LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
					loanDetailsDTO.setEligibility(loanEligibilityDTOs);
					loanDetailsDTO.setHistory(loanHistoryDTOs);
					loanDetailsDTO.setLoanApplication(loanApplicationDTO);
					loanDetailsDTO.setEligible(true);
					response.setDetails(loanDetailsDTO);
					response.setSuccess(true);
					return response;
				}

				
			}

			if((isValidFOSMerchant(merchant.getReferalCode()) || isValidDIYMerchant(merchant)) && !isPaymentBank(merchant)) {
				MerchantSummary merchantSummary = merchantSummaryDao.findActiveMerchantSummary(merchant.getId());
				if(merchantSummary != null) {
					loanEligibilityDTOs.addAll(fetchEligibleLoans(merchantSummary.getLoanType(), merchant));
					
//					String toBeEligibleLoanType = merchantSummary.getLoanType().equals("SUBSEQUENT") ? "SUBSEQUENT_TO_BE_ELIGIBLE" : "FIRST_TO_BE_ELIGIBLE";
//
//					loanEligibilityDTOs.addAll(fetchEligibleLoans(toBeEligibleLoanType, merchant));
				} else {
					logger.error("Merchant summary is empty for merchant ID  {}", merchant.getId());
				}
			}
			
			if("rejected".equals(lendingApplication.getStatus()) && !"REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
				loanApplicationDTO.setShowReapply(true);
//				loanHistoryDTOs = null;
				loanApplicationDTO = null;
			}
			
			LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
			loanDetailsDTO.setEligibility(loanEligibilityDTOs);
			loanDetailsDTO.setHistory(loanHistoryDTOs);
			loanDetailsDTO.setLoanApplication(loanApplicationDTO);
			loanDetailsDTO.setEligible(loanEligibilityDTOs.size() > 0 ? true : false);
			
			
			response.setDetails(loanDetailsDTO);
			response.setSuccess(true);
			
		} catch(Exception ex) {
			logger.error("Exception while checking loan details for merchant id {}, Exception is {}", merchant.getId(), ex);
			return createFailureResponse();
		}
		return response;
	}
	
	private LoanDetailsResponseDTO createFailureResponse() {
		LoanDetailsResponseDTO response = new LoanDetailsResponseDTO();
		LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
		List<LoanHistoryDTO> loanHistoryDTOs = new ArrayList<>();
		LoanApplicationDTO loanApplicationDTO = new LoanApplicationDTO();
		List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();
		loanDetailsDTO.setEligibility(loanEligibilityDTOs);
		loanDetailsDTO.setHistory(loanHistoryDTOs);
		loanDetailsDTO.setLoanApplication(loanApplicationDTO);
		loanDetailsDTO.setEligible( false);
		return response;
	}

	private boolean rejectedInLastNDays(LendingAuditTrial auditTrial, int nDays) {
		try {
			if(auditTrial == null) {
				return false;
			}
			
			Date rejectedTimestamp = auditTrial.getCreatedAt();
			Date nDaysBeforeTimestamp = new Date(System.currentTimeMillis() - Long.valueOf(nDays) * 24 * 3600 * 1000);
			
			if(rejectedTimestamp.compareTo(nDaysBeforeTimestamp) > 0) {
				logger.info("Application with id {} has been rejected in last {} days", auditTrial.getApplicationId(), nDays);
				return true;
			}
			
		} catch(Exception ex) {
			logger.error("Exception while checking if rejected in n days for application id {}, Exception is {}", auditTrial.getApplicationId(), ex);
		}
		
		return false;
	}

	private Boolean isValidFOSMerchant(String referalCode) {
		Boolean responseFlag = false;
		
		if(!referalCode.isEmpty()) {
			Agent agent = agentDao.fetchByReferalCode(referalCode);
			if(agent != null && validAgentCities.contains(agent.getCity())) {
				responseFlag = true;
			} else {
				logger.error("Not valid FOS Merchant with referral code {}, returning false.", referalCode);
			}
		}
		
		return responseFlag;
	}
	
	private Boolean isValidDIYMerchant(Merchant merchant) {
		Boolean responseFlag = false;
		
		MerchantAddress merchantAddress = merchantAddressDao.findBymerchantIdAndType(merchant.getId(), "SELF");
		if(merchantAddress != null && validDIYCities.contains(merchantAddress.getCity()) && isInvalidAgentReferalCode(merchant.getReferalCode())) {
			responseFlag = true;
		} else {
			logger.error("Not valid DIY Merchant with merchant id {}, returning false.", merchant.getId());
		}
		
		return responseFlag;
	}
	
	private Boolean isInvalidAgentReferalCode(String agentReferalCode) {
		Boolean flag = false;
		
		Agent agent = agentDao.fetchByReferalCode(agentReferalCode);
		if(agent != null) {
			flag = true;
		}
		
		return flag;
	}
	
	
//	private Map<String, Object> checkLastPaymentSchedule(Long merchantId) {
//		Map<String, Object> previousLoanDetails = new LinkedHashMap<>();
//		previousLoanDetails.put("isSubsequentLoan", false);
//		previousLoanDetails.put("prevTenure", "");
//		
//		LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchantId);
//		if(lendingPaymentSchedule != null) {
//			String status = lendingPaymentSchedule.getStatus();
//			if(status.equals("CLOSED")) {
//				previousLoanDetails.put("isSubsequentLoan", true);
//				LendingApplication lendingApplication = lendingApplicationDao.findByApplicationId(lendingPaymentSchedule.getApplicationId());
//				if(lendingApplication != null) {
//					previousLoanDetails.put("prevTenure", lendingApplication.getTenure());
//				}
//			}
//		}
//		return previousLoanDetails;
//	}
	
	private List<LoanEligibilityDTO> fetchEligibleLoans(String loanType, Merchant merchant) {
		
		List<LoanEligibilityDTO> availableLoanDTOList = new ArrayList<>();
		
		List<AvailableLoan> availableLoanList = null;
		if(merchant.getId() % 2 == 0) {
			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_2");
		} else {
			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_3");
		}
		
		if(availableLoanList == null || availableLoanList.isEmpty()) {
			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_1");
			
			if(availableLoanList != null && !availableLoanList.isEmpty()) {
				availableLoanList = sort(availableLoanList);
			}
		}
		
		if(availableLoanList == null || availableLoanList.isEmpty()) {
			logger.error("No available loan found for merchant id {}", merchant.getId());
			return availableLoanDTOList;
		}
		
		List<LendingCategories> lendingCategoriesList = lendingCategoryDao.findByStatus(GeneralStatus.ACTIVE.toString());
		for(AvailableLoan availableLoan : availableLoanList) {
			LendingCategories lendingCategoryDetail = fetchCategoryDetails(lendingCategoriesList, availableLoan.getCategory());
			if(lendingCategoryDetail != null) {
				LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
				LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategoryDetail);
				
				loanEligibilityDTO.setProcessingFee(breakup.getProcessingFee());
				loanEligibilityDTO.setInterestRate(breakup.getEffectiveInterestRate());
				loanEligibilityDTO.setAmount(availableLoan.getAmount().intValue());
				loanEligibilityDTO.setCategory(lendingCategoryDetail.getCategory());
				loanEligibilityDTO.setInterestAmount(breakup.getTotalInterestAmount());
				loanEligibilityDTO.setRepayment(breakup.getRepayment());
				loanEligibilityDTO.setDisbursementAmount(breakup.getDisbursementAmount());
				loanEligibilityDTO.setTenure(lendingCategoryDetail.getPayableConverter().replace("Months", "").replace("Month", "").trim());
				loanEligibilityDTO.setConstruct(availableLoan.getLoanConstruct());
				loanEligibilityDTO.setList(prepareLabels(breakup));
				loanEligibilityDTO.setType(breakup.getType());
				loanEligibilityDTO.setOptionEnable(true);

				availableLoanDTOList.add(loanEligibilityDTO);
			} else {
				logger.error("No lending category found for merchant {} and category {}", merchant.getId(), availableLoan.getCategory());
			}
		}
		return availableLoanDTOList;
	}
	
	private List<LabelDTO> prepareLabels(LoanBreakupDetail breakup) {
		List<LabelDTO> list = new ArrayList<>();
		
		if("CONSTRUCT_1".equals(breakup.getConstruct())) {
			list.add(new LabelDTO("Daily Installment", "₹" + breakup.getEdi() + "/day"));
			list.add(new LabelDTO("No Installment on", "Sundays"));
			list.add(new LabelDTO("Repayment Amount", String.valueOf(breakup.getRepayment())));
		} else if("CONSTRUCT_2".equals(breakup.getConstruct())) {
			list.add(new LabelDTO("EDI for 1st Month", "ZERO"));
			list.add(new LabelDTO("EDI for Next " + breakup.getPrincipleEdiTenure() + " Month", "₹" + breakup.getEdi() + "/day"));
			list.add(new LabelDTO("No EDI on", "Sundays"));
			list.add(new LabelDTO("Repayment Amount", String.valueOf(breakup.getRepayment())));
		} else if("CONSTRUCT_3".equals(breakup.getConstruct())) {
			list.add(new LabelDTO("EDI for 1st Month", "₹" + breakup.getIoEdi() + "/day"));
			list.add(new LabelDTO("EDI for Next " + breakup.getPrincipleEdiTenure() + " Month", "₹" + breakup.getEdi() + "/day"));
			list.add(new LabelDTO("No EDI on", "Sundays"));
			list.add(new LabelDTO("Repayment Amount", String.valueOf(breakup.getRepayment())));
		} else {
			logger.error("Construct {} not defined, throwing Exception", breakup.getConstruct());
			throw new RuntimeException("Construct not defined.");
		}
		return list;
	}

	private List<AvailableLoan> sort(List<AvailableLoan> availableLoanList) {
		List<AvailableLoan> sortedAvailableLoans = new ArrayList<>();
		try {
			String maxCategory = null;
			
//			1st loan in the list is with the maximum amount
			for(AvailableLoan current : availableLoanList) {
				if(maxCategory == null) {
					if(MerchantCategory.AA3.toString().equals(current.getCategory()) || MerchantCategory.AA1.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.AA.toString();
						break;
					} else if (MerchantCategory.BB6.toString().equals(current.getCategory()) || MerchantCategory.BB3.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.BB.toString();
						break;
					} else if (MerchantCategory.CC12.toString().equals(current.getCategory()) || MerchantCategory.CC6.toString().equals(current.getCategory()) || MerchantCategory.CC3.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.CC.toString();
						break;
					}
				}
			}
			
			if(maxCategory == null) {
				logger.info("Max category is null, returning all loans.");
				return availableLoanList;
			}
			
			for(AvailableLoan current : availableLoanList) {
				if(current.getCategory().startsWith(maxCategory)) {
					sortedAvailableLoans.add(current);
				}
			}
			
			return sortedAvailableLoans;
		} catch(Exception ex) {
			logger.error("Exception while soring available loans returning all, Exception is {}", ex);
			return availableLoanList;
		}
		
	}

	private LendingCategories fetchCategoryDetails(List<LendingCategories> lendingCategoriesList, String loanCategory) {
		LendingCategories lendingCategoryDetails = null;
		
		if(lendingCategoriesList.size() > 0) {
			for(LendingCategories categoryDetails : lendingCategoriesList) {
				if(categoryDetails.getCategory().equalsIgnoreCase(loanCategory)) {
					lendingCategoryDetails = categoryDetails;
					break;
				}
			}
		}
		
		return lendingCategoryDetails;
	}
	
	private List<LoanHistoryDTO> fetchLoanHistory(LendingApplication application, List<LendingPaymentSchedule> lendingPaymentScheduleList, LendingPaymentSchedule activeLoan) {
		List<LoanHistoryDTO> loanHistoryList = new ArrayList<>();

		if(activeLoan == null && "approved".equals(application.getStatus()) && !"disbursed".equalsIgnoreCase(application.getLoanDisbursalStatus())) {
			LoanHistoryDTO history = new LoanHistoryDTO();

			history.setId(application.getId());
			history.setAmount(application.getLoanAmount());
			history.setStartDate(null);
			history.setEndDate(null);
			history.setStatus("INTRANSFER");
			history.setLoanStatusTitle("Loan Approved");
			history.setLoanStatusMessage("The amount will reflect in your bank account within 48 hours.");
			history.setRepaid(0D);
			history.setDue(application.getRepayment());

			loanHistoryList.add(history);
		}

		for(LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
			LoanHistoryDTO history = new LoanHistoryDTO();
			history.setId(lendingPaymentSchedule.getId());
			history.setAmount(lendingPaymentSchedule.getLoanAmount());
			history.setStartDate(lendingPaymentSchedule.getStartDate());
			history.setStatus(lendingPaymentSchedule.getStatus());
			history.setLoanStatusTitle("");
			history.setLoanStatusMessage("");
			if(LendingStatus.CLOSED.toString().equals(lendingPaymentSchedule.getStatus())) {
				history.setEndDate(lendingPaymentSchedule.getUpdatedAt());
			} else {
				history.setEndDate(null);
			}
			history.setRepaid(lendingPaymentSchedule.getPaidAmount());
			history.setDue(lendingPaymentSchedule.getTotalPayableAmount());
			loanHistoryList.add(history);
		}

		return loanHistoryList;
	}

	private LoanApplicationDTO fetchLoanApplication(Merchant merchant, LendingApplication application) {
		LoanApplicationDTO loanApplicationDTO = new LoanApplicationDTO();
	    if(application != null) {
	        ShopDetailsDTO shopDetails = LoanUtil.prepareShopDetailsDTO(application);
	        SelectedLoanDTO selectedLoan = LoanUtil.prepareSelectedLoanDTO(application);
	        List<DocumentDTO> documents = fetchDocuments(application, merchant);
	        
	        loanApplicationDTO.setApplicationId(String.valueOf(application.getId()));
	        loanApplicationDTO.setShopDetails(shopDetails);
	        loanApplicationDTO.setSelectedLoan(selectedLoan);
	        loanApplicationDTO.setDocuments(documents);
	        loanApplicationDTO.setApplicationStatus(application.getStatus());

	    } else {
	        logger.info("No open lending application found for merchant id {}", merchant.getId());
	    }
	    return loanApplicationDTO;
	}
	
	private LendingPaymentSchedule getActiveLoan(List<LendingPaymentSchedule> lendingPaymentScheduleList) {
		if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.size() == 0) {
			return null;
		}
		
		for(LendingPaymentSchedule schedule : lendingPaymentScheduleList) {
			if(LendingStatus.ACTIVE.toString().equals(schedule.getStatus())) {
				return schedule;
			}
		}
		return null;
	}

//	private Map<String, Object> fetchShopAndSelectedLoanDetails(LendingApplication lendingApplication) {
//		Map<String, Object> shopAndSelectedLoanDetails = new LinkedHashMap<>();
//		shopAndSelectedLoanDetails.put("shopDetails", LoanUtil.prepareShopDetailsForClient(lendingApplication));
//		shopAndSelectedLoanDetails.put("selectedLoan", LoanUtil.prepareSelectedLoanForClient(lendingApplication));
//		return shopAndSelectedLoanDetails;
//	}
	
	private List<DocumentDTO> fetchDocuments(LendingApplication lendingApplication, Merchant merchant) {
		List<DocumentDTO> documents = new ArrayList<>();
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant, lendingApplication);
		for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
			DocumentDTO document = new DocumentDTO();
			document.setId(documentsIdProof.getId());
			document.setProofType(documentsIdProof.getProofType());
			document.setSinglePageDocument(documentsIdProof.getSinglePage() == 0 ? false : true);
		}
		return documents;
	}
	
//	private Map<String, Object> prepareResponse(List<Map<String, Object>> eligibility, Map<String, Object> loanDetails, Boolean eligibleFlag) {
//		Map<String, Object> response = new LinkedHashMap<> ();
//		Map<String, Object> details = new LinkedHashMap<> ();
//
//		response.put("success", true);
//
//		details.put("eligible", eligibleFlag);
//		if(loanDetails.get("loanHistory") != null) {
//			details.put("loan_history", loanDetails.get("loanHistory"));
//		}else {
//			details.put("loan_history", new ArrayList());
//		}
//		details.put("eligibility", eligibility);
//
//		Map<String, Object> loanApplication = prepareLoanApplication(loanDetails);
//		details.put("loan_application", loanApplication);
//
//		response.put("details", details);
//		return response;
//	}
	
//	private Map<String, Object> prepareLoanApplication(Map<String, Object> loanDetails) {
//		Map<String, Object> loanApplication = new LinkedHashMap<> ();
//		if(loanDetails.get("shopDetails") != null) {
//			loanApplication.put("shop_details",loanDetails.get("shopDetails"));
//		}else {
//			loanApplication.put("shop_details",new LinkedHashMap());
//		}
//		if(loanDetails.get("selectedLoan") != null) {
//			loanApplication.put("selected_loan",loanDetails.get("selectedLoan"));
//		}else {
//			loanApplication.put("selected_loan",new LinkedHashMap());
//		}
//		if(loanDetails.get("documents") != null) {
//			loanApplication.put("documents",loanDetails.get("documents"));
//		}else {
//			loanApplication.put("documents",new ArrayList());
//		}
//		if(loanDetails.get("applicationStatus") != null) {
//			loanApplication.put("application_status",loanDetails.get("applicationStatus"));
//		}else {
//			loanApplication.put("application_status","");
//		}
//		if(loanDetails.get("applicationId") != null) {
//			loanApplication.put("application_id",loanDetails.get("applicationId"));
//		}else {
//			loanApplication.put("application_id","");
//		}
//		if(loanDetails.get("statusTitle") != null) {
//			loanApplication.put("status_title",loanDetails.get("statusTitle"));
//		}else {
//			loanApplication.put("status_title","");
//		}
//		if(loanDetails.get("showReapply") != null && (Boolean)loanDetails.get("showReapply") == true) {
//			loanApplication.put("reapply", false);
//		}
//		if(loanDetails.get("statusMessage") != null) {
//			loanApplication.put("status_message",loanDetails.get("statusMessage"));
//		}else {
//			loanApplication.put("status_message","");
//		}
//
//		loanApplication.put("agreement","");
//		return loanApplication;
//	}
	
	private boolean isPaymentBank(Merchant merchant) {
		try {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			
			if(merchantBankDetail == null) {
				logger.error("No merchnat bank detail found for merchant id {}", merchant.getId());
				return true;
			}
			
			if(StringUtils.isEmpty(merchantBankDetail.getIfscCode())) {
				logger.error("IFSC is empty for merchant bank detail id {} and merchant ID {}", merchantBankDetail.getId(), merchant.getId());
				return true;
			}
			
			List<BankList> nonPaymentBankList = bankListDao.fetchNonPaymentBankList(merchantBankDetail.getIfscCode().substring(0,4));
				
			if (nonPaymentBankList == null || nonPaymentBankList.size() == 0) {
				return false;
			} else {
				logger.info("IFSC {} is of Payment bank, returning true", merchantBankDetail.getIfscCode());
				return true;
			}
		} catch(Exception ex) {
			logger.error("Exception while checking if merchant's bank is payment bank with merchant id {}, Exception is {}", merchant.getId(), ex);
		}
		return true;
	}
}
