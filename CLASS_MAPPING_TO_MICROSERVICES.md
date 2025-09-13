# Class and Function Mapping to Microservices

Based on the comprehensive analysis of the codebase, here's the detailed mapping of existing classes and functions to each microservice:

## 1. **Application Management Service**

### **Primary Responsibilities:**
- Loan application lifecycle management
- Application state management
- Application validation and address verification
- Application dashboard and status tracking

### **Core Classes to Extract:**

#### **Services:**
```java
// Main application services
- LendingApplicationService.java
- LendingApplicationServiceV2.java
- LendingApplicationServiceV3Impl.java
- LendingApplicationServiceV3Base.java

// Application state management
- LoanDetailsV3Service.java
- LoanDetailsService.java
- LoanDetailsServiceV2.java

// Application utilities
- LoanUtil.java
- LoanUtilV3.java
- EasyLoanUtil.java

// Application validation
- PincodeVerificationServices.java
- AddressValidationService.java (if exists)
```

#### **Controllers:**
```java
- LendingApplicationController.java
- LoanDetailsController.java
- LoanSurveyController.java
- PreBookController.java
- HomeController.java
- TestController.java
```

#### **DAOs:**
```java
- LendingApplicationDao.java
- LendingApplicationDaoSlave.java
- LendingApplicationDetailsDao.java
- LendingApplicationPriorityDao.java
- LendingApplicationKycDetailsDao.java
- LendingApplicationLenderDetailsDao.java
- LendingMerchantDetailsDao.java
- LendingResubmitTaskDao.java
- LendingResubmitReasonCountDao.java
```

#### **Entities:**
```java
- LendingApplication.java
- LendingApplicationDetails.java
- LendingApplicationKycDetails.java
- LendingApplicationLenderDetails.java
- LendingApplicationPriority.java
- LendingMerchantDetails.java
- LendingResubmitTask.java
- LendingResubmitReasonCount.java
```

#### **Key Functions:**
```java
// Application creation and management
- createApplication()
- updateApplication()
- getApplicationDetails()
- getApplicationStatus()
- validateApplication()
- saveLendingApplication()

// Application state management
- processCurrentStage()
- fetchScopedData()
- saveApplicationViewState()
- getApplicationState()

// Application validation
- checkLoanRequestPinCodeForLoanEligibilty()
- validateAddress()
- validateMerchantDetails()
- checkApplicationEligibility()
```

---

## 2. **Lender Association Service**

### **Primary Responsibilities:**
- Lender assignment and mapping
- Workflow orchestration
- Lender-specific workflow management
- Association status tracking

### **Core Classes to Extract:**

#### **Services:**
```java
// Lender assignment
- LenderAssignService.java
- LenderMappingService.java

// Workflow management
- WorkflowManager.java
- WorkflowFactory.java
- WorkflowRegistryFactory.java
- LoanCreationService.java

// Lender-specific workflows
- TrillionWorkflowRegistry.java
- CreditSaisonWorkflowRegistry.java
- OxyzoWorkflowRegistry.java
- WorkflowRegistry.java (interface)

// Workflow implementations
- CreateLeadWorkflow.java
- KYCDocumentWorkflow.java
- KYCWorkflow.java
- BreWorkflow.java
- DisbursalWorkflow.java
- LoanDocumentWorkflow.java
- NachWorkflow.java
- PennyDropWorkflow.java
```

#### **Controllers:**
```java
- LenderAssignController.java
- LendingPartnerController.java
```

#### **DAOs:**
```java
- LendingLenderQuotaDao.java
- LenderAssignmentRulesDao.java
- LendingApplicationLenderDetailsDao.java
- LendingLenderPricingDao.java
- LendingLenderDetailsDao.java
```

#### **Entities:**
```java
- LendingLenderQuota.java
- LenderAssignmentRules.java
- LendingApplicationLenderDetails.java
- LendingLenderPricing.java
- LendingLenderDetails.java
```

#### **Key Functions:**
```java
// Lender assignment
- assignLender()
- getLender()
- getLenderList()
- updateLenderLimits()
- saveLenderChangeAudit()

// Workflow management
- initiateLoanCreationWorkflow()
- invokeWorkflows()
- getStageWorkflow()
- processWorkflowStage()

// Lender mapping
- getLenderMapping()
- updateLenderMapping()
- validateLenderRules()
```

---

## 3. **Lender Evaluation Service**

### **Primary Responsibilities:**
- Risk assessment and underwriting
- Eligibility computation
- Bureau data integration
- Credit scoring and risk variables

### **Core Classes to Extract:**

#### **Services:**
```java
// Eligibility and risk assessment
- LoanEligibleService.java
- EligibilityComputationService.java
- EligibilityV3Service.java

// Risk assessment
- RiskAssessmentService.java (if exists)
- LendingRiskVariablesService.java (if exists)

// Bureau integration
- ExperianService.java (if exists)
- CrifService.java (if exists)
- BureauIntegrationService.java (if exists)

// Underwriting
- UnderwritingService.java (if exists)
- CreditScoringService.java (if exists)
```

#### **Controllers:**
```java
- ExperianController.java
- CrifController.java
- IneligibleController.java
- CreditApplicationController.java
```

#### **DAOs:**
```java
- LendingEligibleLoanDao.java
- LendingRiskVariablesDao.java
- LendingRiskVariablesSnapshotDao.java
- ExperianDao.java
- ExperianDaoSlave.java
- ExperianDummyDao.java
- LendingCategoryDao.java
- LendingLenderPricingDao.java
```

#### **Entities:**
```java
- LendingEligibleLoan.java
- LendingRiskVariables.java
- LendingRiskVariablesSnapshot.java
- Experian.java
- ExperianDummy.java
- LendingCategory.java
- LendingLenderPricing.java
```

#### **Key Functions:**
```java
// Eligibility computation
- computeEligibility()
- getEligibleOffers()
- calculateLoanAmount()
- calculateTenure()
- calculateInterestRate()

// Risk assessment
- assessRisk()
- calculateRiskScore()
- getRiskSegment()
- getRiskGroup()

// Bureau integration
- callExperian()
- callCrif()
- processBureauResponse()
- validateBureauData()

// Underwriting
- performUnderwriting()
- evaluateCreditworthiness()
- makeUnderwritingDecision()
```

---

## 4. **KYC & Document Management Service**

### **Primary Responsibilities:**
- KYC document handling and verification
- Document upload and management
- VKYC integration
- Business document management

### **Core Classes to Extract:**

#### **Services:**
```java
// KYC handling
- KycHandler.java
- KycService.java (if exists)
- VKycService.java

// Document management
- UploadDocumentService.java
- DocumentVerificationService.java (if exists)
- DocumentManagementService.java (if exists)

// KYC utilities
- KycUtils.java
- KycValidationService.java (if exists)
```

#### **Controllers:**
```java
- EkycController.java
- MerchantDetailsController.java
- GenericOTPVerifyController.java
```

#### **DAOs:**
```java
- LendingEkycDao.java
- LendingShopDocumentsDao.java
- LendingGstDao.java
- LendingPancardDetailsDao.java
- LendingMerchantReferencesDao.java
- DocKycDetailsDaoMaster.java
- DocumentsIdProofDaoMaster.java
```

#### **Entities:**
```java
- LendingEkyc.java
- LendingShopDocuments.java
- LendingGstDetail.java
- LendingPancardDetails.java
- LendingMerchantReferences.java
- DocKycDetailsMaster.java
- DocumentsIdProofMaster.java
```

#### **Key Functions:**
```java
// KYC processing
- processKYC()
- verifyKycDocuments()
- getKycDocs()
- updateKycStatus()
- validateKycData()

// Document management
- uploadDocument()
- downloadDocument()
- verifyDocument()
- getDocumentStatus()
- processDocumentVerification()

// VKYC integration
- initiateVKyc()
- checkVKycStatus()
- processVKycResponse()
```

---

## 5. **Payment & Disbursement Service**

### **Primary Responsibilities:**
- Payment processing and management
- Loan disbursement
- NACH mandate management
- Payment schedule management

### **Core Classes to Extract:**

#### **Services:**
```java
// Payment processing
- PaymentService.java
- PaymentProcessingService.java (if exists)

// Disbursement
- LiquiloansService.java
- LiquiloansAsyncService.java
- DisbursalService.java (if exists)

// NACH management
- ENachService.java
- NachMandateService.java (if exists)

// Payment utilities
- PaymentUtil.java (if exists)
- DisbursalUtil.java (if exists)
```

#### **Controllers:**
```java
- PaymentController.java
- PaymentLinkController.java
- ENachController.java
- BPEnachController.java
- CreditEnachController.java
- AutoPayUPIController.java
- LendingPullPaymentController.java
```

#### **DAOs:**
```java
- LendingPaymentScheduleDao.java
- LendingPaymentScheduleDaoSlave.java
- LendingLedgerDao.java
- LendingLedgerSlaveDao.java
- LendingPrepaymentDao.java
- LendingDisbursalStageDao.java
- LendingBulkDisbursalDao.java
- LendingBulkDisbursalRawDataDao.java
- LendingAutoDisbursalDao.java
- LoanPaymentOrderDao.java
- LoanPaymentOrderSlaveDao.java
```

#### **Entities:**
```java
- LendingPaymentSchedule.java
- LendingLedger.java
- LendingPrepayment.java
- LendingDisbursalStage.java
- LendingBulkDisbursal.java
- LendingBulkDisbursalRawData.java
- LendingAutoDisbursal.java
- LoanPaymentOrder.java
```

#### **Key Functions:**
```java
// Payment processing
- processPayment()
- initiatePayment()
- validatePayment()
- processRefund()
- updatePaymentStatus()

// Disbursement
- processDisbursement()
- initiateDisbursement()
- validateDisbursement()
- updateDisbursementStatus()

// NACH management
- registerNach()
- updateNach()
- cancelNach()
- validateNach()

// Payment schedule
- createPaymentSchedule()
- updatePaymentSchedule()
- getPaymentSchedule()
- calculatePaymentAmount()
```

---

## 6. **Collection & Recovery Service**

### **Primary Responsibilities:**
- Loan collections and recovery
- Payment adjustments
- Foreclosure handling
- Collection analytics

### **Core Classes to Extract:**

#### **Services:**
```java
// Collection services
- LoanPaymentServiceImpl.java (from collection package)
- LoanStatusServiceImpl.java (from collection package)
- CollectionService.java (if exists)

// Recovery services
- RecoveryService.java (if exists)
- ForeclosureService.java (if exists)

// Collection utilities
- LoanPaymentUtil.java (from collection package)
- CollectionUtil.java (if exists)
```

#### **Controllers:**
```java
- SupportLoanController.java
- LendingPullPaymentController.java
```

#### **DAOs:**
```java
- LendingCollectionAuditDao.java
- LendingCollectionExcessDao.java
- LendingPrepaymentDao.java
- LoanDpdDao.java
- LoanDpdDaoSlave.java
- LendingNocDetailsDao.java
```

#### **Entities:**
```java
- LendingCollectionAudit.java
- LendingCollectionExcess.java
- LendingPrepayment.java
- LoanDpd.java
- LendingNocDetails.java
```

#### **Key Functions:**
```java
// Collection processing
- processCollection()
- handleOverdueLoan()
- sendCollectionNotification()
- updateCollectionStatus()

// Payment adjustments
- adjustPayment()
- processPaymentAdjustment()
- handlePaymentDispute()

// Foreclosure
- processForeclosure()
- calculateForeclosureAmount()
- handleForeclosureRequest()

// Recovery
- initiateRecovery()
- processRecovery()
- updateRecoveryStatus()
```

---

## 7. **Agreement & Documentation Service**

### **Primary Responsibilities:**
- Loan agreement generation and management
- Document templates and PDF generation
- Legal compliance workflows
- Key Factor Statement (KFS) generation

### **Core Classes to Extract:**

#### **Services:**
```java
// Agreement services
- SignAgreementService.java
- LendingAgreementService.java
- AgreementService.java (if exists)

// Document generation
- DocumentGenerationService.java (if exists)
- PDFService.java (if exists)
- TemplateService.java (if exists)

// KFS services
- KfsService.java (if exists)
- KfsGenerationService.java (if exists)
```

#### **Controllers:**
```java
- CallLoanDetailService.java
```

#### **DAOs:**
```java
- LoanAgreementDao.java
- LendingKfsDao.java
- LendingNbfscsDao.java
- LendingNocDetailsDao.java
```

#### **Entities:**
```java
- LoanAgreement.java
- LendingKfs.java
- LendingNbfscs.java
- LendingNocDetails.java
```

#### **Key Functions:**
```java
// Agreement management
- generateAgreement()
- signAgreement()
- getAgreementDetails()
- updateAgreementStatus()

// Document generation
- generatePDF()
- createDocument()
- updateDocument()
- getDocumentTemplate()

// KFS generation
- generateKFS()
- updateKFS()
- getKFSDetails()
- validateKFS()
```

---

## 8. **Notification & Communication Service**

### **Primary Responsibilities:**
- SMS and email notifications
- Push notifications
- Event publishing
- OTP services

### **Core Classes to Extract:**

#### **Services:**
```java
// Notification services
- CleverTapEventService.java
- EmailHandler.java
- SmsService.java (if exists)
- NotificationService.java (if exists)

// OTP services
- VerifyOTPService.java
- VerifyOTPServiceV2.java
- BharatPeOtpHandler.java

// Event services
- EventPublishingService.java (if exists)
- KafkaEventService.java (if exists)
```

#### **Controllers:**
```java
- GenericOTPVerifyController.java
- CallingLeadNimbusController.java
```

#### **DAOs:**
```java
- NotifyEligibleDao.java
- CallingLeadResponseNimbusDao.java
```

#### **Entities:**
```java
- NotifyEligible.java
- CallingLeadResponseNimbus.java
```

#### **Key Functions:**
```java
// Notification sending
- sendSMS()
- sendEmail()
- sendPushNotification()
- sendCleverTapEvent()

// OTP management
- generateOTP()
- verifyOTP()
- validateOTP()
- resendOTP()

// Event publishing
- publishEvent()
- sendKafkaEvent()
- processEvent()
```

---

## **Shared Components (Common to All Services)**

### **Utilities:**
```java
- DateTimeUtil.java
- AesEncryptionUtil.java
- LendingHmacCalculator.java
- BQPublisherUtil.java
- S3BucketHandler.java
- ImageURLService.java
```

### **Handlers:**
```java
- EnachHandler.java
- MerchantSummaryHandler.java
- BharatSwipeHandler.java
- PartnersApiHandler.java
```

### **Common Services:**
```java
- APIGatewayService.java
- MerchantService.java
- FunnelService.java
- LendingCache.java
```

### **Common DAOs:**
```java
- LendingAuditTrialDao.java
- LendingCitiesDao.java
- LendingRedCitiesDao.java
- LendingCategoryDao.java
```

### **Common Entities:**
```java
- LendingAuditTrial.java
- LendingCities.java
- LendingRedCities.java
- LendingCategory.java
```

---

## **Migration Strategy for Each Service**

### **Phase 1: Extract Dependencies**
1. Identify all dependencies for each service
2. Create service interfaces
3. Implement service clients for inter-service communication

### **Phase 2: Extract Core Logic**
1. Move service classes to new microservice
2. Move related DAOs and entities
3. Update database connections

### **Phase 3: Update Controllers**
1. Move controllers to new microservice
2. Update API endpoints
3. Implement service-to-service communication

### **Phase 4: Data Migration**
1. Create separate database for service
2. Migrate relevant data
3. Update data access patterns

### **Phase 5: Testing & Integration**
1. Unit testing for each service
2. Integration testing
3. End-to-end testing

This mapping provides a clear roadmap for breaking down the monolithic application into focused microservices while maintaining all existing functionality.
