# Complete Repository Breakdown into Microservices

## 1. APPLICATION MANAGEMENT SERVICE

### Controllers
- `LendingApplicationController.java`
- `LendingApplicationControllerV2.java`
- `LoanDetailsController.java`
- `LoanSurveyController.java`
- `PreBookController.java`
- `HomeController.java`
- `TestController.java`
- `HandshakeController.java`

### Services
- `LendingApplicationService.java`
- `LendingApplicationServiceV2.java`
- `LendingApplicationServiceV3Impl.java`
- `LendingApplicationServiceV3Base.java`
- `LoanDetailsService.java`
- `LoanDetailsServiceV2.java`
- `LoanDetailsV3Service.java`
- `LoanUtil.java`
- `LoanUtilV3.java`
- `EasyLoanUtil.java`
- `PincodeVerificationServices.java`
- `PreBookService.java`
- `CancelApplicationService.java`
- `RefundService.java`

### DAOs
- `LendingApplicationDao.java`
- `LendingApplicationDaoSlave.java`
- `LendingApplicationDetailsDao.java`
- `LendingApplicationPriorityDao.java`
- `LendingApplicationKycDetailsDao.java`
- `LendingApplicationLenderDetailsDao.java`
- `LendingMerchantDetailsDao.java`
- `LendingResubmitTaskDao.java`
- `LendingResubmitReasonCountDao.java`
- `LendingCitiesDao.java`
- `LendingRedCitiesDao.java`

### Entities
- `LendingApplication.java`
- `LendingApplicationDetails.java`
- `LendingApplicationKycDetails.java`
- `LendingApplicationLenderDetails.java`
- `LendingApplicationPriority.java`
- `LendingMerchantDetails.java`
- `LendingResubmitTask.java`
- `LendingResubmitReasonCount.java`
- `LendingCities.java`
- `LendingRedCities.java`

---

## 2. LENDER ASSOCIATION SERVICE

### Controllers
- `LenderAssignController.java`
- `LendingPartnerController.java`

### Services
- `LenderAssignService.java`
- `LenderMappingService.java`
- `WorkflowManager.java`
- `WorkflowFactory.java`
- `WorkflowRegistryFactory.java`
- `LoanCreationService.java`

### Workflow Registries
- `TrillionWorkflowRegistry.java`
- `CreditSaisonWorkflowRegistry.java`
- `OxyzoWorkflowRegistry.java`
- `WorkflowRegistry.java` (interface)

### Workflow Implementations
- `CreateLeadWorkflow.java`
- `KYCDocumentWorkflow.java`
- `KYCWorkflow.java`
- `BreWorkflow.java`
- `DisbursalWorkflow.java`
- `LoanDocumentWorkflow.java`
- `NachWorkflow.java`
- `PennyDropWorkflow.java`
- `LoanDocumentDownloadWorkflow.java`

### DAOs
- `LendingLenderQuotaDao.java`
- `LenderAssignmentRulesDao.java`
- `LendingLenderPricingDao.java`
- `LendingLenderDetailsDao.java`

### Entities
- `LendingLenderQuota.java`
- `LenderAssignmentRules.java`
- `LendingLenderPricing.java`
- `LendingLenderDetails.java`

---

## 3. LENDER EVALUATION SERVICE

### Controllers
- `ExperianController.java`
- `CrifController.java`
- `IneligibleController.java`
- `CreditApplicationController.java`
- `FosController.java`

### Services
- `LoanEligibleService.java`
- `EligibilityComputationService.java`
- `EligibilityV3Service.java`
- `FosService.java`
- `CreditApplicationService.java`
- `LoanAndRTEEligibilityComputeService.java`

### DAOs
- `LendingEligibleLoanDao.java`
- `LendingRiskVariablesDao.java`
- `LendingRiskVariablesSnapshotDao.java`
- `ExperianDao.java`
- `ExperianDaoSlave.java`
- `ExperianDummyDao.java`
- `LendingCategoryDao.java`

### Entities
- `LendingEligibleLoan.java`
- `LendingRiskVariables.java`
- `LendingRiskVariablesSnapshot.java`
- `Experian.java`
- `ExperianDummy.java`
- `LendingCategory.java`

---

## 4. KYC & DOCUMENT MANAGEMENT SERVICE

### Controllers
- `EkycController.java`
- `MerchantDetailsController.java`
- `GenericOTPVerifyController.java`
- `CallingLeadNimbusController.java`

### Services
- `KycHandler.java`
- `VKycService.java`
- `UploadDocumentService.java`
- `ImageURLService.java`
- `KycUtils.java`
- `VerifyOTPService.java`
- `VerifyOTPServiceV2.java`
- `BharatPeOtpHandler.java`

### DAOs
- `LendingEkycDao.java`
- `LendingShopDocumentsDao.java`
- `LendingGstDao.java`
- `LendingPancardDetailsDao.java`
- `LendingMerchantReferencesDao.java`
- `DocKycDetailsDaoMaster.java`
- `DocumentsIdProofDaoMaster.java`
- `CallingLeadResponseNimbusDao.java`

### Entities
- `LendingEkyc.java`
- `LendingShopDocuments.java`
- `LendingGstDetail.java`
- `LendingPancardDetails.java`
- `LendingMerchantReferences.java`
- `DocKycDetailsMaster.java`
- `DocumentsIdProofMaster.java`
- `CallingLeadResponseNimbus.java`

---

## 5. PAYMENT & DISBURSEMENT SERVICE

### Controllers
- `PaymentController.java`
- `PaymentLinkController.java`
- `ENachController.java`
- `BPEnachController.java`
- `CreditEnachController.java`
- `AutoPayUPIController.java`
- `LendingPullPaymentController.java`
- `TopupController.java`

### Services
- `PaymentService.java`
- `LiquiloansService.java`
- `LiquiloansAsyncService.java`
- `ENachService.java`
- `TopupLoanEligibleService.java`
- `MerchantLoansService.java`

### DAOs
- `LendingPaymentScheduleDao.java`
- `LendingPaymentScheduleDaoSlave.java`
- `LendingLedgerDao.java`
- `LendingLedgerSlaveDao.java`
- `LendingPrepaymentDao.java`
- `LendingDisbursalStageDao.java`
- `LendingBulkDisbursalDao.java`
- `LendingBulkDisbursalRawDataDao.java`
- `LendingAutoDisbursalDao.java`
- `LoanPaymentOrderDao.java`
- `LoanPaymentOrderSlaveDao.java`
- `LendingIoHalfTopupDao.java`

### Entities
- `LendingPaymentSchedule.java`
- `LendingLedger.java`
- `LendingPrepayment.java`
- `LendingDisbursalStage.java`
- `LendingBulkDisbursal.java`
- `LendingBulkDisbursalRawData.java`
- `LendingAutoDisbursal.java`
- `LoanPaymentOrder.java`
- `LendingIoHalfTopup.java`

---

## 6. COLLECTION & RECOVERY SERVICE

### Controllers
- `SupportLoanController.java`
- `LendingPullPaymentController.java`

### Services
- `SupportService.java`
- `LoanPaymentServiceImpl.java` (from collection package)
- `LoanStatusServiceImpl.java` (from collection package)
- `LendingCollectionAuditService.java`
- `LendingEdiScheduleService.java`

### Collection Services (from collection package)
- All services in `collection/core/service/`
- All services in `collection/core/service/impl/`

### DAOs
- `LendingCollectionAuditDao.java`
- `LendingCollectionExcessDao.java`
- `LendingPrepaymentDao.java`
- `LoanDpdDao.java`
- `LoanDpdDaoSlave.java`
- `LendingNocDetailsDao.java`

### Entities
- `LendingCollectionAudit.java`
- `LendingCollectionExcess.java`
- `LendingPrepayment.java`
- `LoanDpd.java`
- `LendingNocDetails.java`

---

## 7. AGREEMENT & DOCUMENTATION SERVICE

### Controllers
- `CallLoanDetailService.java`

### Services
- `SignAgreementService.java`
- `LendingAgreementService.java`

### DAOs
- `LoanAgreementDao.java`
- `LendingKfsDao.java`
- `LendingNbfscsDao.java`
- `LendingNocDetailsDao.java`

### Entities
- `LoanAgreement.java`
- `LendingKfs.java`
- `LendingNbfscs.java`
- `LendingNocDetails.java`

---

## 8. NOTIFICATION & COMMUNICATION SERVICE

### Controllers
- `GenericOTPVerifyController.java`
- `CallingLeadNimbusController.java`

### Services
- `CleverTapEventService.java`
- `EmailHandler.java`
- `FunnelService.java`
- `VerifyOTPService.java`
- `VerifyOTPServiceV2.java`
- `BharatPeOtpHandler.java`

### DAOs
- `NotifyEligibleDao.java`
- `CallingLeadResponseNimbusDao.java`

### Entities
- `NotifyEligible.java`
- `CallingLeadResponseNimbus.java`

---

## 9. LMS (LOAN MANAGEMENT SYSTEM) SERVICE

### Controllers
- `LMSController.java`

### Services
- `LoanService.java` (from lendingplatform/lms)
- `LoanDisplayService.java`
- `LmsLoanCreationService.java`
- `LmsLoanStatusService.java`
- `LmsFieldValuesService.java`

### DAOs
- `LmsLoanStatusDao.java`
- `LmsFieldValuesDao.java`
- `LmsLoanCreationDao.java`

### Entities
- `LmsLoanStatus.java`
- `LmsFieldValues.java`
- `LmsLoanCreation.java`

---

## 10. CONSUMER SERVICE (Event Processing)

### Consumers
- `LoanAndRTEELigibilityComputeConsumer.java`
- `NBFCPayoutConsumer.java`
- `PennyDropPayoutConsumer.java`
- `BankStatementSessionCallbackConsumer.java`
- `NachMandateConsumer.java`
- `SaveSignedLoanDocsConsumer.java`
- `UpdateLendingGstDetailsConsumer.java`

### Consumer Services
- `NBFCPayoutService.java`
- `PennyDropService.java`
- `BankStatementService.java`
- `LoanAndRTEEligibilityComputeService.java`

---

## SHARED COMPONENTS (Common to All Services)

### Utilities
- `DateTimeUtil.java`
- `AesEncryptionUtil.java`
- `LendingHmacCalculator.java`
- `BQPublisherUtil.java`
- `S3BucketHandler.java`
- `ImageURLService.java`
- `DisbursalStageMapping.java`
- `EdiUtil.java`

### Handlers
- `EnachHandler.java`
- `MerchantSummaryHandler.java`
- `BharatSwipeHandler.java`
- `PartnersApiHandler.java`

### Common Services
- `APIGatewayService.java`
- `MerchantService.java`
- `LendingCache.java`
- `LendingAuditTrialService.java`

### Common DAOs
- `LendingAuditTrialDao.java`
- `LendingCategoryDao.java`
- `ValidateDao.java`
- `OglLoansDao.java`
- `TmpLoanGenerateDao.java`
- `DisbursalSettlementDao.java`

### Common Entities
- `LendingAuditTrial.java`
- `LendingCategory.java`
- `Validate.java`
- `OglLoans.java`
- `TmpLoanGenerate.java`
- `DisbursalSettlement.java`
- `MileStoneEntity.java`
- `MerchantAggregateData.java`
- `LanguageMapping.java`
- `LenderLanguageMapping.java`
- `MileStoneReward.java`

---

## CONFIGURATION & INFRASTRUCTURE

### Configuration Classes
- `Application.java`
- `AsyncConfig.java`
- `BeanConfigurations.java`
- `BPNewMasterDbConfig.java`
- `InsuranceConfig.java`
- `LendingDelayedQueueConfig.java`
- `MasterConfig.java`
- `QueryDbConfig.java`
- `ReceiverConfig.java`
- `RestTemplateConfig.java`
- `SlaveConfig.java`
- `VkycConfig.java`
- `LendingPlatformConfiguration.java`
- `LendingPlatformRestTemplateConfig.java`

### Interceptors & Aspects
- All classes in `interceptor/` package
- All classes in `aspect/` package
- All classes in `advices/` package
- All classes in `annotations/` package

### Exception Handling
- All classes in `exception/` package
- All classes in `exceptions/` package
- All classes in `handlers/` package

### Constants & Enums
- All classes in `constant/` package
- All classes in `enums/` package

### DTOs
- All classes in `dto/` package (distributed based on service usage)

---

## TEMPLATES & RESOURCES

### HTML Templates
- All files in `src/main/resources/templates/` (distributed based on service usage)

### Configuration Files
- `application.properties`
- `application-dev.properties`
- `application-prestage.properties`
- `application-prod.properties`
- `logback.xml`

### Merchant Lists
- All files in `src/main/resources/MerchantList/`

This breakdown ensures that every class in your repository is mapped to an appropriate microservice while maintaining all existing functionality and business flows.
