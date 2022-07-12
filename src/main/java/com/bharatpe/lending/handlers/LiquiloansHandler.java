//package com.bharatpe.lending.handlers;
//
//import org.springframework.stereotype.Component;
//
//@Component
//public class LiquiloansHandler {
//
////	private final Logger logger = LoggerFactory.getLogger(LiquiloansHandler.class);
////
////    @Autowired
////    RestTemplate restTemplate;
////
////    @Autowired
////	LendingHmacCalculator lendingHmacCalculator;
////
////    @Autowired
////	LendingBankDisburseDao lendingBankDisburseDao;
////
////    @Autowired
////	LendingPaymentScheduleDao lendingPaymentScheduleDao;
////
////    @Autowired
////	LiquiloansDirectDisbursalRawResponseDao liquiloansDirectDisbursalRawResponseDao;
////
////    @Autowired
////    Environment env;
////
////    @Autowired
////	ObjectMapper mapper;
////
////    private static String secretKey;
////    private static String SID;
//
////	public void notifyEDISchedule(LendingPaymentSchedule lendingPaymentSchedule, List<LendingEDISchedule> ediSchedules, LendingTlDetails lendingTlDetails) {
////		try {
////			Thread.sleep(5000);// waiting to return approve loan callback
////			if(!"LIQUILOANS".equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) || StringUtils.isEmpty(lendingTlDetails.getNbfcId())) {
////				logger.info("Not auto disbursal, skipping. Loan ID {}", lendingPaymentSchedule.getId());
////				return;
////			}
////
////			if(lendingPaymentSchedule.getLenderDisbursalNotify() != null && "SUCCESS".equalsIgnoreCase(lendingPaymentSchedule.getLenderDisbursalNotify())) {
////				logger.info("Notify Lender for Disbursal already success for loan ID {}, returning", lendingPaymentSchedule.getId());
////				return;
////			}
////
////			LendingBankDisburse lendingBankDisburse = lendingBankDisburseDao.findByLendingApplication(lendingTlDetails.getLendingClTransaction().getId());
////			if(lendingBankDisburse == null) {
////				logger.error("LendingBankDisburse not found, skipping. Loan ID {}", lendingPaymentSchedule.getId());
////				return;
////			}
////
////			Map<String, Object> request = new LinkedHashMap<>();
////
////			request.put("SID", getSID());
////			request.put("urn", lendingTlDetails.getExternalLoanId());
////			request.put("loan_id", Integer.valueOf(lendingTlDetails.getNbfcId()));
////			request.put("loan_amount", String.valueOf(lendingPaymentSchedule.getLoanAmount()));
////			request.put("utr_number", lendingBankDisburse.getBankReferenceNo());
////			request.put("mode_of_transfer", lendingBankDisburse.getSettlementMode());
////			request.put("repayment_frequency", "Daily");
////			request.put("disbursal_date", format(lendingPaymentSchedule.getCreatedAt()));
////			if (lendingPaymentSchedule.getInterestOnlyStartDate() != null) {
////				request.put("start_date", format(lendingPaymentSchedule.getInterestOnlyStartDate()));
////			} else {
////				request.put("start_date", format(lendingPaymentSchedule.getStartDate()));
////			}
////			request.put("loan_closure_date", format(lendingPaymentSchedule.getTentativeClosingDate()));
////			request.put("disbursal_amount", String.valueOf(lendingPaymentSchedule.getLoanAmount()));
////			request.put("principle_due", String.valueOf(lendingPaymentSchedule.getLoanAmount()));
////			request.put("interest_due", String.valueOf(lendingPaymentSchedule.getTotalPayableAmount() - lendingPaymentSchedule.getLoanAmount()));
////			request.put("other_charges_due", String.valueOf(lendingPaymentSchedule.getOtherCharges()));
////			request.put("total_amount_due", String.valueOf(lendingPaymentSchedule.getTotalPayableAmount()));
////
////			List<Map<String, Object>> scheduleList = new ArrayList<>();
////			for(LendingEDISchedule ediSchedule : ediSchedules) {
////				Map<String, Object> schedule = new LinkedHashMap<>();
////				schedule.put("date", format(ediSchedule.getDate()));
////				schedule.put("principle", String.valueOf(ediSchedule.getPrinciple()));
////				schedule.put("interest", String.valueOf(ediSchedule.getInterest()));
////				schedule.put("other_charges", String.valueOf(ediSchedule.getOtherCharges()));
////				schedule.put("total_amount", String.valueOf(ediSchedule.getTotalEdi()));
////
////				scheduleList.add(schedule);
////			}
////			request.put("payment_schedule", scheduleList);
////
////			String checksumString = getChecksumString(request) + getSecretKey();
////
////			logger.info("Checksum String is {}", checksumString);
////			request.put("Checksum", lendingHmacCalculator.calculateHMACHexEncoded(checksumString, getSecretKey()));
////
////			HttpHeaders headers = new HttpHeaders();
////			headers.setContentType(MediaType.APPLICATION_JSON);
////			headers.setCacheControl(CacheControl.noCache());
////
////			String requestString = mapper.writeValueAsString(request);
////
////			logger.info("Request to liquiloans : {}", requestString);
////
////			LiquiloansDirectDisbursalRawResponse bean = new LiquiloansDirectDisbursalRawResponse();
////			bean.setMerchantId(lendingPaymentSchedule.getMerchantId());
////			if(lendingPaymentSchedule.getMerchantStoreId() != null) {
////				bean.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
////			}
////			bean.setApiName("DISBURSAL");
////			bean.setRequest(requestString);
////			bean.setApplicationId(lendingTlDetails.getId());
////			bean.setLiquiloanId(lendingTlDetails.getNbfcId());
////			bean.setLoanId(lendingTlDetails.getExternalLoanId());
////			bean.setStatus("PENDING");
////			bean  = liquiloansDirectDisbursalRawResponseDao.save(bean);
////
////			HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request, headers);
////			String responseString;
////			Map<String, Object> responseMap = null;
////			boolean isSuccess = false;
////			try {
////				Long startTime = System.currentTimeMillis();
////				responseMap = restTemplate.postForObject(env.getProperty("liquiloans.disbursal.api"), requestEntity, Map.class);
////				logger.info("Response form Liquiloans : {}" , responseMap);
////				logger.info("Liquiloans txn notify response time : {}ms, response {}", System.currentTimeMillis() - startTime, responseMap);
////				responseString = mapper.writeValueAsString(responseMap);
////				isSuccess = true;
////			} catch (HttpClientErrorException e) {
////				logger.info("Response form Liquiloans : {}" , e.getResponseBodyAsString());
////				responseString = e.getResponseBodyAsString();
////				logger.error("Error in api call in liquiloans txn notify for {}, {}", request, e);
////			} catch (Exception ex) {
////				responseString = ex.getMessage();
////				logger.error("Error in api call in liquiloans txn notify for {}, {}", request, ex);
////			}
////			bean.setResponse(responseString);
////			if(isSuccess) {
////				if(responseMap != null && responseMap.get("status") == null && !"true".equalsIgnoreCase(responseMap.get("status").toString())) {
////					isSuccess = false;
////				}
////			}
////			bean.setStatus(isSuccess ? "SUCCESS" : "FAILED");
////			lendingPaymentSchedule.setLenderDisbursalNotify(bean.getStatus());
////			lendingPaymentSchedule = lendingPaymentScheduleDao.save(lendingPaymentSchedule);
////			liquiloansDirectDisbursalRawResponseDao.save(bean);
////		} catch(Exception ex) {
////			logger.error("Error while notifying Liquiloans for EDI Schedule with Loan ID {}, Exception is {}", lendingPaymentSchedule.getId(), ex);
////		}
////	}
//
////	private String format(Date date) {
////		return new SimpleDateFormat("yyyy-MM-dd").format(date);
////	}
////
////	private String getChecksumString(Map<String, Object> request) throws IOException {
////		Map<String, Object> map = new LinkedHashMap<>();
////		for (Map.Entry<String, Object> entry : request.entrySet()) {
////			if(request.get(entry.getKey()) == null) {
////				continue;
////			}
////			if(request.get(entry.getKey()) instanceof List || request.get(entry.getKey()) instanceof Map) {
////				map.put(entry.getKey(), mapper.writeValueAsString(request.get(entry.getKey())));
////			} else {
////				map.put(entry.getKey(), request.get(entry.getKey()));
////			}
////		}
////		map.values().removeIf(Objects::isNull);
////        return StringUtils.collectionToDelimitedString(map.values(), "||");
////	}
////
////    private String getSecretKey() {
////        if (secretKey == null) {
////            secretKey = env.getProperty("liquiloan.secret");
////        }
////        return secretKey;
////    }
////
////    private String getSID() {
////    	if (SID == null) {
////            SID = env.getProperty("liquiloan.sid");
////        }
////        return SID;
////    }
//}
