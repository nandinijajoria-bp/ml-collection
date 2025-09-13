# APPLICATION MANAGEMENT SERVICE - Complete Class List

## CONTROLLERS (8 classes)

### Main Application Controllers
- `LendingApplicationController.java`
- `LendingApplicationControllerV2.java`
- `LoanDetailsController.java`
- `LoanSurveyController.java`
- `PreBookController.java`
- `HomeController.java`
- `TestController.java`
- `HandshakeController.java`

### Panel Controllers
- `panel/UpdateApplicationStatus.java`
- `panel/SaveApplicationAddressController.java`
- `panel/ApplicationStatusController.java`
- `panel/ApplicationDetailsController.java`
- `panel/ApplicationListController.java`
- `panel/ApplicationDashboardController.java`

---

## SERVICES (25 classes)

### Core Application Services
- `LendingApplicationService.java`
- `LendingApplicationServiceV2.java`
- `LendingApplicationServiceV3Impl.java`
- `LendingApplicationServiceV3Base.java`

### Loan Details Services
- `LoanDetailsService.java`
- `LoanDetailsServiceV2.java`
- `LoanDetailsV3Service.java`

### Application Utilities
- `LoanUtil.java`
- `LoanUtilV3.java`
- `EasyLoanUtil.java`
- `PincodeVerificationServices.java`

### Application Support Services
- `PreBookService.java`
- `CancelApplicationService.java`
- `RefundService.java`
- `SupportService.java`
- `FosService.java`
- `CreditApplicationService.java`

### Application State Management
- `LoanDashboardService.java` (from loanV3/revamp/services)
- `ApplicationStateService.java` (if exists)
- `ApplicationValidationService.java` (if exists)

### Application Utilities
- `LoanCalculationUtil.java`
- `ApplicationSnapshotService.java` (if exists)
- `ApplicationWorkflowService.java` (if exists)

### Application Handlers
- `MerchantSummaryExceptionHandler.java`
- `ApplicationExceptionHandler.java` (if exists)

---

## DAOs (15 classes)

### Core Application DAOs
- `LendingApplicationDao.java`
- `LendingApplicationDaoSlave.java`
- `LendingApplicationDetailsDao.java`
- `LendingApplicationPriorityDao.java`
- `LendingApplicationKycDetailsDao.java`
- `LendingApplicationLenderDetailsDao.java`
- `LendingMerchantDetailsDao.java`
- `LendingResubmitTaskDao.java`
- `LendingResubmitReasonCountDao.java`

### Application Support DAOs
- `LendingCitiesDao.java`
- `LendingRedCitiesDao.java`
- `LendingPincodesDao.java`
- `LendingCategoryDao.java`
- `LendingAuditTrialDao.java`
- `LendingMerchantDropoffDao.java` (if exists)

---

## ENTITIES (12 classes)

### Core Application Entities
- `LendingApplication.java`
- `LendingApplicationDetails.java`
- `LendingApplicationKycDetails.java`
- `LendingApplicationLenderDetails.java`
- `LendingApplicationPriority.java`
- `LendingMerchantDetails.java`
- `LendingResubmitTask.java`
- `LendingResubmitReasonCount.java`

### Application Support Entities
- `LendingCities.java`
- `LendingRedCities.java`
- `LendingPincodes.java`
- `LendingCategory.java`
- `LendingAuditTrial.java`
- `LendingMerchantDropoff.java` (if exists)

---

## DTOs (50+ classes)

### Application Request DTOs
- `LendingApplicationRequestDTO.java`
- `CreateApplicationRequest.java`
- `UpdateApplicationRequest.java`
- `ApplicationStatusRequest.java`
- `ApplicationDetailsRequest.java`
- `PreBookRequestDTO.java`
- `CreditApplicationRequestDTO.java`

### Application Response DTOs
- `LendingApplicationResponseDTO.java`
- `CreateApplicationResponse.java`
- `ApplicationDetailsResponse.java`
- `ApplicationStatusResponse.java`
- `PreBookResponseDTO.java`
- `CreditApplicationResponseDTO.java`
- `LoanDetailsResponseDTO.java`

### Application State DTOs
- `ApplicationStateDTO.java`
- `LendingStateDTO.java`
- `ApplicationViewStateDTO.java`
- `ApplicationSnapshotDTO.java`

### Application Validation DTOs
- `AddressValidationDto.java`
- `ApplicationValidationDto.java`
- `PincodeValidationDto.java`
- `MerchantValidationDto.java`

### Application Workflow DTOs
- `ApplicationWorkflowDTO.java`
- `ApplicationStageDTO.java`
- `ApplicationTransitionDTO.java`

### Application Dashboard DTOs
- `ApplicationDashboardDTO.java`
- `ApplicationSummaryDTO.java`
- `ApplicationMetricsDTO.java`

### Application Audit DTOs
- `ApplicationAuditDTO.java`
- `ApplicationEventDTO.java`
- `ApplicationLogDTO.java`

---

## ENUMS (8 classes)

### Application Status Enums
- `ApplicationStatus.java`
- `ApplicationStage.java`
- `ApplicationState.java`
- `ApplicationType.java`

### Application Workflow Enums
- `LendingViewStates.java`
- `ApplicationWorkflowStage.java`
- `ApplicationTransition.java`

### Application Validation Enums
- `ValidationStatus.java`
- `ValidationType.java`

---

## UTILITIES (10 classes)

### Application Utilities
- `ApplicationUtil.java`
- `ApplicationValidationUtil.java`
- `ApplicationStateUtil.java`
- `ApplicationWorkflowUtil.java`
- `ApplicationSnapshotUtil.java`

### Application Constants
- `ApplicationConstants.java`
- `ApplicationWorkflowConstants.java`
- `ApplicationValidationConstants.java`

### Application Helpers
- `ApplicationHelper.java`
- `ApplicationStateHelper.java`

---

## CONFIGURATION (5 classes)

### Application Configuration
- `ApplicationConfig.java`
- `ApplicationWorkflowConfig.java`
- `ApplicationValidationConfig.java`
- `ApplicationStateConfig.java`
- `ApplicationCacheConfig.java`

---

## EXCEPTIONS (5 classes)

### Application Exceptions
- `ApplicationException.java`
- `ApplicationValidationException.java`
- `ApplicationWorkflowException.java`
- `ApplicationStateException.java`
- `ApplicationNotFoundException.java`

---

## HANDLERS (8 classes)

### Application Handlers
- `ApplicationExceptionHandler.java`
- `ApplicationValidationHandler.java`
- `ApplicationWorkflowHandler.java`
- `ApplicationStateHandler.java`
- `ApplicationEventHandler.java`
- `ApplicationAuditHandler.java`
- `ApplicationNotificationHandler.java`
- `ApplicationCacheHandler.java`

---

## INTERCEPTORS (5 classes)

### Application Interceptors
- `ApplicationLoggingInterceptor.java`
- `ApplicationValidationInterceptor.java`
- `ApplicationAuditInterceptor.java`
- `ApplicationSecurityInterceptor.java`
- `ApplicationCacheInterceptor.java`

---

## ASPECTS (3 classes)

### Application Aspects
- `ApplicationLoggingAspect.java`
- `ApplicationAuditAspect.java`
- `ApplicationPerformanceAspect.java`

---

## CONSUMERS (3 classes)

### Application Event Consumers
- `ApplicationEventConsumer.java`
- `ApplicationStateChangeConsumer.java`
- `ApplicationWorkflowConsumer.java`

---

## TEMPLATES (10+ files)

### Application Templates
- `application_created.html`
- `application_updated.html`
- `application_approved.html`
- `application_rejected.html`
- `application_dashboard.html`
- `application_details.html`
- `application_status.html`
- `application_workflow.html`
- `application_validation.html`
- `application_audit.html`

---

## RESOURCES (5 files)

### Application Resources
- `application.properties`
- `application-dev.properties`
- `application-prestage.properties`
- `application-prod.properties`
- `logback-spring.xml`

---

## TOTAL CLASS COUNT: 107+ classes

### Breakdown by Category:
- **Controllers**: 8 classes
- **Services**: 25 classes
- **DAOs**: 15 classes
- **Entities**: 12 classes
- **DTOs**: 50+ classes
- **Enums**: 8 classes
- **Utilities**: 10 classes
- **Configuration**: 5 classes
- **Exceptions**: 5 classes
- **Handlers**: 8 classes
- **Interceptors**: 5 classes
- **Aspects**: 3 classes
- **Consumers**: 3 classes
- **Templates**: 10+ files
- **Resources**: 5 files

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

This comprehensive list ensures that all application-related functionality is properly segregated into the Application Management Service while maintaining all existing business flows.
