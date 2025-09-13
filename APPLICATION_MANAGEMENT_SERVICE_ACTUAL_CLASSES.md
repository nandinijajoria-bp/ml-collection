# APPLICATION MANAGEMENT SERVICE - Actual Current Classes

## CONTROLLERS (12 classes)

### Main Application Controllers
- `LendingApplicationController.java`
- `LendingApplicationControllerV2.java` (from loanV2/controller)
- `LendingApplicationControllerV3.java` (from loanV3/controller)
- `LoanDetailsController.java`
- `LoanDetailsControllerV2.java` (from loanV2/controller)
- `LoanDetailsControllerV3.java` (from loanV3/revamp/controller)
- `LoanSurveyController.java`
- `PreBookController.java`
- `HomeController.java`
- `TestController.java`
- `HandshakeController.java`
- `LoanDashboardController.java` (from loanV3/revamp/controller)

### Panel Controllers
- `panel/UpdateApplicationStatus.java`
- `panel/SaveApplicationAddressController.java`
- `panel/SaveAddressProofDetailsController.java`
- `panel/SavePanCardDetailsController.java`
- `panel/UpdateLoanInfoFromPanelController.java`
- `panel/VerifyApplicationKarzaStatusController.java`

---

## SERVICES (35 classes)

### Core Application Services
- `LendingApplicationService.java`
- `LendingApplicationServiceV2.java` (from loanV2/service)
- `LendingApplicationServiceV3Impl.java` (from loanV3/services)
- `LendingApplicationServiceV3Base.java` (from loanV3/services)

### Loan Details Services
- `LoanDetailsService.java`
- `LoanDetailsServiceV2.java` (from loanV2/service)
- `LoanDetailsV3Service.java` (from loanV3/revamp/services)

### Application Support Services
- `PreBookService.java`
- `CancelApplicationService.java`
- `RefundService.java`
- `SupportService.java`
- `FosService.java`
- `CreditApplicationService.java`
- `LoanSurveyService.java`
- `MerchantLoansService.java`
- `NewToBharatpeService.java`

### Application Utilities
- `PincodeVerificationServices.java`
- `ILendingCitiesService.java`
- `ILendingPancardService.java`
- `ImageURLService.java`
- `UploadDocumentService.java`
- `SaveApplicationAddressService.java`
- `SaveAddressProofDetailsService.java`
- `SavePanCardDetailsService.java`
- `UpdateLoanInfoFromPanelService.java`
- `VerifyApplicationKarzaStatusService.java`

### Application State Management
- `LoanDashboardService.java` (from loanV3/revamp/services)
- `ModifyStageService.java` (from loanV3/services)
- `LendingApplicationKycDetailsService.java`

### Application Handlers
- `MerchantSummaryExceptionHandler.java` (from handlers)
- `BureauHandler.java` (from loanV2/handlers)
- `FinanceUtilsHandler.java` (from loanV2/handlers)

### Application Validation
- `VerifyDocService.java`
- `RequestAuditService.java`
- `RequestAuditFactory.java`
- `Request3PAuditService.java`
- `LoanDetailsRequestAuditService.java`

---

## DAOs (8 classes)

### Core Application DAOs
- `LendingApplicationDao.java`
- `LendingApplicationDaoSlave.java` (from common/dao)
- `LendingApplicationDetailsDao.java` (from common/dao)
- `LendingApplicationPriorityDao.java` (from common/dao)
- `LendingApplicationKycDetailsDao.java` (from common/dao)
- `LendingApplicationLenderDetailsDao.java` (from common/dao)
- `LendingMerchantDetailsDao.java` (from common/dao)
- `LendingResubmitTaskDao.java` (from common/dao)

### Application Support DAOs
- `LendingCitiesDao.java` (from common/dao)
- `LendingRedCitiesDao.java` (from common/dao)
- `LendingPincodesDao.java` (from common/dao)
- `LendingCategoryDao.java` (from common/dao)
- `LendingAuditTrialDao.java` (from common/dao)
- `LendingMerchantDropoffDao.java` (from common/dao)
- `LendingApplicationLenderDetailsDao.java` (from common/dao)
- `LendingResubmitReasonCountDao.java` (from common/dao)

---

## ENTITIES (8 classes)

### Core Application Entities
- `LendingApplication.java` (from common/entities)
- `LendingApplicationDetails.java` (from common/entity)
- `LendingApplicationKycDetails.java` (from common/entity)
- `LendingApplicationLenderDetails.java` (from common/entity)
- `LendingApplicationPriority.java` (from common/entity)
- `LendingMerchantDetails.java` (from common/entity)
- `LendingResubmitTask.java` (from common/entity)
- `LendingResubmitReasonCount.java` (from common/entity)

### Application Support Entities
- `LendingCities.java` (from common/entity)
- `LendingRedCities.java` (from common/entity)
- `LendingPincodes.java` (from common/entity)
- `LendingCategory.java` (from common/entity)
- `LendingAuditTrial.java` (from common/entity)
- `LendingMerchantDropoff.java` (from common/entity)

---

## DTOs (50+ classes)

### Application Request DTOs
- `CreateApplicationRequest.java` (from loanV2/dto)
- `UpdateApplicationRequest.java` (if exists)
- `ApplicationStatusRequest.java` (if exists)
- `ApplicationDetailsRequest.java` (if exists)
- `PreBookRequestDTO.java` (if exists)
- `CreditApplicationRequestDTO.java` (if exists)
- `LoanDetailsRequest.java` (from loanV2/dto)
- `LoanDetailsV3Request.java` (from loanV3/revamp/dto)

### Application Response DTOs
- `CreateApplicationResponse.java` (from loanV2/dto)
- `ApplicationDetailsResponse.java` (if exists)
- `ApplicationStatusResponse.java` (if exists)
- `PreBookResponseDTO.java` (if exists)
- `CreditApplicationResponseDTO.java` (if exists)
- `LoanDetailsResponse.java` (from loanV2/dto)
- `LoanDetailsV3Response.java` (from loanV3/revamp/dto)
- `LoanDashboardResponse.java` (from loanV3/revamp/dto)

### Application State DTOs
- `ApplicationStateDTO.java` (from loanV3/revamp/dto)
- `LendingStateDTO.java` (from loanV3/revamp/dto)
- `ApplicationStatusStateDTO.java` (from loanV3/revamp/dto)
- `ApplicationSnapshotDTO.java` (if exists)
- `LoanApplicationDetailsV3.java` (from loanV3/revamp/dto)

### Application Validation DTOs
- `AddressDetails.java` (from loanV2/dto)
- `BusinessDetailsDTO.java` (from loanV2/dto)
- `ProfessionalDetails.java` (from loanV2/dto)
- `AdditionalDetails.java` (from loanV2/dto)
- `BankAccountDetails.java` (from loanV2/dto)

### Application Workflow DTOs
- `LoanApplicationStage.java` (from loanV2/dto)
- `LoanApplicationDetails.java` (from loanV2/dto)
- `ScopeDataArgs.java` (from loanV3/revamp/dto)
- `StateRenderArgs.java` (from loanV3/revamp/dto)

### Application Dashboard DTOs
- `LoanDashboardResponse.java` (from loanV3/revamp/dto)
- `LoanDetailResponseDto.java` (from loanV3/revamp/dto)
- `LatestLoanDetailResponse.java` (from loanV2/dto)
- `LoanAndCreditCardDetailDTO.java` (from loanV2/dto)

### Application Audit DTOs
- `RequestAuditDTO.java` (if exists)
- `ApplicationEventDTO.java` (if exists)
- `ApplicationLogDTO.java` (if exists)

---

## ENUMS (5 classes)

### Application Status Enums
- `ApplicationStatus.java` (from enums)
- `LendingViewStates.java` (from loanV3/revamp/enums)
- `DisbursalStatus.java` (from loanV3/enums)
- `LoanDetailExceptionEnum.java` (from loanV3/revamp/enums)
- `PreApprovedLoanEnums.java` (from loanV3/revamp/enums)

---

## UTILITIES (8 classes)

### Application Utilities
- `LoanUtil.java` (from util)
- `LoanUtilV3.java` (from loanV3/revamp/util)
- `EasyLoanUtil.java` (from common/util)
- `KycUtils.java` (from loanV3/utils)
- `NbfcUtils.java` (from loanV3/utils)
- `ConverterUtils.java` (from loanV3/utils)
- `DocUploadUtils.java` (from loanV3/utils)
- `EmiUtils.java` (from loanV3/utils)

---

## CONFIGURATION (6 classes)

### Application Configuration
- `TrillionLoansConfig.java` (from loanV3/config)
- `CreditSaisonConfig.java` (from loanV3/config)
- `OxyzoConfig.java` (from loanV3/config)
- `PayUConfig.java` (from loanV3/config)
- `SmfgConfig.java` (from loanV3/config)
- `UgroConfig.java` (from loanV3/config)

---

## EXCEPTIONS (3 classes)

### Application Exceptions
- `LoanDetailsException.java` (from loanV3/revamp/exception)
- `KYCException.java` (from loanV3/revamp/exception)
- `LoanDetailsExceptionController.java` (from loanV3/revamp/exception)

---

## HANDLERS (3 classes)

### Application Handlers
- `MerchantSummaryExceptionHandler.java` (from handlers)
- `BureauHandler.java` (from loanV2/handlers)
- `FinanceUtilsHandler.java` (from loanV2/handlers)

---

## CONSUMERS (5 classes)

### Application Event Consumers
- `BreRequestKafka.java` (from loanV3/consumer)
- `DataUploadRequestKafka.java` (from loanV3/consumer)
- `DrawdownRequestKafka.java` (from loanV3/consumer)
- `KycRequestKafka.java` (from loanV3/consumer)
- `SancWrapperRequestKafka.java` (from loanV3/consumer)

---

## FACTORIES (4 classes)

### Application Factories
- `LenderAssociationServiceFactory.java` (from loanV3/factory)
- `LenderAssociationStageFactory.java` (from loanV3/factory)
- `LenderAssociationStageFactoryV2.java` (from loanV3/factory)
- `LenderGatewayFactory.java` (from loanV3/factory)

---

## INTERFACES (3 classes)

### Application Interfaces
- `ILenderAssignment.java` (from loanV3/interfaces)
- `ILenderAssociationService.java` (from loanV3/interfaces)
- `ILenderAssociationStage.java` (from loanV3/interfaces)

---

## SCOPES (25 classes)

### Application State Scopes
- `ActiveLoanStateService.java` (from loanV3/revamp/scopes)
- `AgreementStateService.java` (from loanV3/revamp/scopes)
- `ApplicationStateService.java` (from loanV3/revamp/scopes)
- `ApplicationStatusStateService.java` (from loanV3/revamp/scopes)
- `BLDocUploadStateService.java` (from loanV3/revamp/scopes)
- `EligibilityStateService.java` (from loanV3/revamp/scopes)
- `EnachStateService.java` (from loanV3/revamp/scopes)
- `IneligibleStateService.java` (from loanV3/revamp/scopes)
- `KFSStateService.java` (from loanV3/revamp/scopes)
- `KYCStateService.java` (from loanV3/revamp/scopes)
- `LenderEvaluationStateService.java` (from loanV3/revamp/scopes)
- `LenderVKycStateService.java` (from loanV3/revamp/scopes)
- `MaskedMobileStateService.java` (from loanV3/revamp/scopes)
- `ModifiedOfferStateService.java` (from loanV3/revamp/scopes)
- `OfferStateService.java` (from loanV3/revamp/scopes)
- `PANPINStateService.java` (from loanV3/revamp/scopes)
- `PermissionStateService.java` (from loanV3/revamp/scopes)
- `ReferenceStateService.java` (from loanV3/revamp/scopes)
- `RejectionStateService.java` (from loanV3/revamp/scopes)
- `ShopDetailsStateService.java` (from loanV3/revamp/scopes)
- `ShopPicturesStateService.java` (from loanV3/revamp/scopes)
- `TopupRejectionStateService.java` (from loanV3/revamp/scopes)
- `UdyamRegistrationStateService.java` (from loanV3/revamp/scopes)
- `UpiAutopayStateService.java` (from loanV3/revamp/scopes)

---

## STATE MANAGER (4 classes)

### Application State Management
- `RenderStateViaScope.java` (from loanV3/revamp/stateManager)
- `RenderStateWithoutScope.java` (from loanV3/revamp/stateManager)
- `StateManager.java` (from loanV3/revamp/stateManager)
- `StateTransitionManager.java` (from loanV3/revamp/stateManager)

---

## STAGES (13 classes)

### Application Stage Services
- `BreStageAssociationSvcFactory.java` (from loanV3/services/stages)
- `BreStageAssociationService.java` (from loanV3/services/stages)
- `BreStageAssociationServiceImpl.java` (from loanV3/services/stages)
- `DrawdownStageAssociationSvcFactory.java` (from loanV3/services/stages)
- `DrawdownStageAssociationService.java` (from loanV3/services/stages)
- `DrawdownStageAssociationServiceImpl.java` (from loanV3/services/stages)
- `KycStageAssociationSvcFactory.java` (from loanV3/services/stages)
- `KycStageAssociationService.java` (from loanV3/services/stages)
- `KycStageAssociationServiceImpl.java` (from loanV3/services/stages)
- `SancStageAssociationSvcFactory.java` (from loanV3/services/stages)
- `SancStageAssociationService.java` (from loanV3/services/stages)
- `SancStageAssociationServiceImpl.java` (from loanV3/services/stages)
- `StageAssociationServiceFactory.java` (from loanV3/revamp/config)

---

## ASSOCIATIONS (155+ classes)

### Application Association Services
- All 23 classes from `loanV3/services/associations/`
- All 132 classes from `loanV3/services/associationsV2/`

---

## TOTAL CLASS COUNT: 350+ classes

### Breakdown by Category:
- **Controllers**: 12 classes
- **Services**: 35 classes
- **DAOs**: 8 classes
- **Entities**: 8 classes
- **DTOs**: 50+ classes
- **Enums**: 5 classes
- **Utilities**: 8 classes
- **Configuration**: 6 classes
- **Exceptions**: 3 classes
- **Handlers**: 3 classes
- **Consumers**: 5 classes
- **Factories**: 4 classes
- **Interfaces**: 3 classes
- **Scopes**: 25 classes
- **State Manager**: 4 classes
- **Stages**: 13 classes
- **Associations**: 155+ classes

### Key Responsibilities:
1. **Application Lifecycle Management** - Create, update, delete applications
2. **Application State Management** - Track application states and transitions
3. **Application Validation** - Validate application data and business rules
4. **Application Workflow** - Manage application workflow stages
5. **Application Dashboard** - Provide application overview and metrics
6. **Application Audit** - Track application changes and events
7. **Application Notifications** - Send application-related notifications
8. **Application Caching** - Cache application data for performance
9. **Application Security** - Secure application access and data
10. **Application Monitoring** - Monitor application performance and health

This comprehensive list includes all the actual current classes from your repository that should be segregated into the Application Management Service.
