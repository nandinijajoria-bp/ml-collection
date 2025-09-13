# Detailed Phased Migration Guide

## Phase 1: Foundation & Preparation (Weeks 1-4)

### Week 1: Infrastructure Setup

#### 1.1 API Gateway Configuration
```yaml
# Spring Cloud Gateway Configuration
spring:
  cloud:
    gateway:
      routes:
        - id: lending-application
          uri: lb://lending-application-service
          predicates:
            - Path=/api/v1/applications/**
          filters:
            - StripPrefix=2
        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/v1/notifications/**
          filters:
            - StripPrefix=2
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods: "*"
            allowedHeaders: "*"
```

#### 1.2 Service Discovery Setup
```yaml
# Eureka Server Configuration
eureka:
  server:
    enable-self-preservation: false
  client:
    register-with-eureka: false
    fetch-registry: false
```

#### 1.3 Database Migration Strategy
```sql
-- Create separate databases for each service
CREATE DATABASE notification_db;
CREATE DATABASE application_management_db;
CREATE DATABASE lender_association_db;
CREATE DATABASE lender_evaluation_db;
CREATE DATABASE kyc_document_db;
CREATE DATABASE payment_disbursement_db;
CREATE DATABASE collection_recovery_db;
CREATE DATABASE agreement_documentation_db;

-- Create shared database for cross-service data
CREATE DATABASE lending_shared_db;
```

### Week 2: Monitoring & Observability

#### 2.1 Centralized Logging
```yaml
# Logback configuration for all services
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{traceId},%X{spanId}] %logger{36} - %msg%n"
  level:
    com.bharatpe: DEBUG
```

#### 2.2 Distributed Tracing
```java
// Zipkin configuration
@Configuration
public class TracingConfig {
    
    @Bean
    public Sampler alwaysSampler() {
        return Sampler.create(1.0f);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
            .interceptors(new TracingRestTemplateInterceptor())
            .build();
    }
}
```

#### 2.3 Health Checks
```java
@Component
public class CustomHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        // Check database connectivity
        // Check external service dependencies
        // Check message queue connectivity
        
        return Health.up()
            .withDetail("database", "UP")
            .withDetail("kafka", "UP")
            .build();
    }
}
```

### Week 3: Security Implementation

#### 3.1 JWT Token Management
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

#### 3.2 Service-to-Service Authentication
```java
@Service
public class ServiceAuthenticationService {
    
    public String generateServiceToken(String serviceName) {
        return Jwts.builder()
            .setSubject(serviceName)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
            .signWith(SignatureAlgorithm.HS512, secretKey)
            .compact();
    }
}
```

### Week 4: Testing Framework

#### 4.1 Contract Testing
```java
// Pact contract testing
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "lending-application-service")
class ApplicationServiceContractTest {
    
    @Pact(consumer = "notification-service")
    public RequestResponsePact createPact(PactDslWithProvider builder) {
        return builder
            .given("application exists")
            .uponReceiving("get application request")
            .path("/api/v1/applications/123")
            .method("GET")
            .willRespondWith()
            .status(200)
            .body("{\"id\":123,\"merchantId\":456}")
            .toPact();
    }
}
```

## Phase 2: Extract Non-Critical Services (Weeks 5-8)

### Week 5-6: Notification Service (Already detailed in PoC)

### Week 7-8: Agreement & Documentation Service

#### Service Structure
```
agreement-documentation-service/
├── src/main/java/com/bharatpe/agreement/
│   ├── controller/
│   │   ├── AgreementController.java
│   │   ├── DocumentController.java
│   │   └── TemplateController.java
│   ├── service/
│   │   ├── AgreementService.java
│   │   ├── DocumentGenerationService.java
│   │   ├── PDFService.java
│   │   └── TemplateService.java
│   ├── entity/
│   │   ├── LoanAgreement.java
│   │   ├── DocumentTemplate.java
│   │   └── GeneratedDocument.java
│   └── repository/
│       ├── AgreementRepository.java
│       └── TemplateRepository.java
```

#### Key Features
- PDF generation using iText
- Template management
- Digital signature integration
- Document versioning
- Agreement status tracking

## Phase 3: Extract Core Business Services (Weeks 9-16)

### Week 9-10: KYC & Document Management Service

#### Service Architecture
```java
@Service
public class KYCService {
    
    public KYCResponse processKYC(KYCRequest request) {
        // 1. Validate documents
        // 2. Run verification checks
        // 3. Update KYC status
        // 4. Trigger next workflow step
    }
    
    public DocumentVerificationResponse verifyDocument(DocumentVerificationRequest request) {
        // 1. Extract data from document
        // 2. Validate against external sources
        // 3. Update verification status
    }
}
```

#### Integration Points
- Document storage service (S3)
- External KYC providers
- Lender-specific KYC requirements
- Compliance reporting

### Week 11-12: Lender Association Service

#### Workflow Orchestration
```java
@Service
public class LenderAssociationService {
    
    @Autowired
    private WorkflowRegistryFactory workflowRegistryFactory;
    
    public void initiateLenderAssociation(Long applicationId, String lender) {
        WorkflowRegistry registry = workflowRegistryFactory.getWorkflowRegistry(Lender.valueOf(lender));
        List<Workflow> workflows = registry.getStageWorkflow(CREATE_LEAD);
        
        for (Workflow workflow : workflows) {
            workflow.invoke(applicationId.toString());
        }
    }
}
```

#### Key Components
- Lender-specific workflow management
- Association status tracking
- Workflow state persistence
- Error handling and retry logic

### Week 13-14: Lender Evaluation Service

#### Risk Assessment Engine
```java
@Service
public class RiskAssessmentService {
    
    public RiskProfile assessRisk(RiskAssessmentRequest request) {
        // 1. Bureau score analysis
        // 2. Credit history evaluation
        // 3. Business profile assessment
        // 4. Risk score calculation
    }
    
    public EligibilityResponse checkEligibility(EligibilityRequest request) {
        // 1. Loan amount eligibility
        // 2. Tenure eligibility
        // 3. Interest rate calculation
        // 4. Lender-specific rules
    }
}
```

#### Integration Points
- Bureau data providers (Experian, CRIF)
- Risk scoring algorithms
- Lender-specific evaluation rules
- Real-time eligibility computation

### Week 15-16: Testing & Integration

#### End-to-End Testing
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class EndToEndIntegrationTest {
    
    @Test
    void testCompleteLoanApplicationFlow() {
        // 1. Create application
        // 2. Assign lender
        // 3. Evaluate eligibility
        // 4. Process KYC
        // 5. Generate agreement
        // 6. Send notifications
    }
}
```

## Phase 4: Extract Critical Services (Weeks 17-24)

### Week 17-18: Payment & Disbursement Service

#### Financial Transaction Management
```java
@Service
@Transactional
public class PaymentService {
    
    public DisbursementResponse processDisbursement(DisbursementRequest request) {
        // 1. Validate disbursement amount
        // 2. Check lender limits
        // 3. Process payment
        // 4. Update loan status
        // 5. Create payment schedule
    }
    
    public PaymentResponse processPayment(PaymentRequest request) {
        // 1. Validate payment
        // 2. Process payment
        // 3. Update ledger
        // 4. Send confirmation
    }
}
```

#### Key Features
- Payment gateway integration
- NACH mandate management
- Payment schedule generation
- Financial reconciliation
- Audit trail maintenance

### Week 19-20: Collection & Recovery Service

#### Collection Workflow Management
```java
@Service
public class CollectionService {
    
    public void processCollection(CollectionRequest request) {
        // 1. Calculate due amounts
        // 2. Send collection notifications
        // 3. Process payments
        // 4. Update collection status
    }
    
    public void handleOverdueLoan(LendingPaymentSchedule loan) {
        // 1. Escalate collection
        // 2. Apply penalties
        // 3. Update risk profile
        // 4. Trigger recovery process
    }
}
```

#### Key Features
- Automated collection workflows
- Recovery agent management
- Payment adjustment processing
- Foreclosure handling
- Collection analytics

### Week 21-22: Data Migration & Synchronization

#### Database Migration Scripts
```sql
-- Migrate application data
INSERT INTO application_management_db.lending_application
SELECT * FROM lending_db.lending_application;

-- Migrate lender data
INSERT INTO lender_association_db.lending_lender_quota
SELECT * FROM lending_db.lending_lender_quota;

-- Migrate payment data
INSERT INTO payment_disbursement_db.lending_payment_schedule
SELECT * FROM lending_db.lending_payment_schedule;
```

#### Data Synchronization
```java
@Component
public class DataSynchronizationService {
    
    @EventListener
    public void handleApplicationCreated(ApplicationCreatedEvent event) {
        // Synchronize data across services
        // Update shared cache
        // Trigger dependent processes
    }
}
```

### Week 23-24: Performance Optimization

#### Caching Strategy
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS));
        return cacheManager;
    }
}
```

#### Database Optimization
```sql
-- Add indexes for performance
CREATE INDEX idx_application_merchant_id ON lending_application(merchant_id);
CREATE INDEX idx_payment_schedule_status ON lending_payment_schedule(status);
CREATE INDEX idx_kyc_merchant_id ON lending_application_kyc_details(merchant_id);
```

## Phase 5: Extract Application Management (Weeks 25-28)

### Week 25-26: Application Lifecycle Management

#### State Machine Implementation
```java
@Component
public class ApplicationStateMachine {
    
    @Autowired
    private StateMachine<ApplicationState, ApplicationEvent> stateMachine;
    
    public void processEvent(ApplicationEvent event) {
        stateMachine.sendEvent(event);
    }
}
```

#### Key Features
- Application state management
- Workflow orchestration
- Status tracking
- Event handling

### Week 27-28: API Gateway & Service Mesh

#### Service Mesh Configuration
```yaml
# Istio service mesh configuration
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: lending-services
spec:
  http:
  - match:
    - uri:
        prefix: /api/v1/applications
    route:
    - destination:
        host: application-management-service
    timeout: 30s
    retries:
      attempts: 3
      perTryTimeout: 10s
```

## Phase 6: Optimization & Cleanup (Weeks 29-32)

### Week 29-30: Monitoring & Alerting

#### Comprehensive Monitoring
```yaml
# Prometheus monitoring configuration
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'lending-services'
    static_configs:
      - targets: ['application-management-service:8080']
      - targets: ['lender-association-service:8081']
      - targets: ['payment-disbursement-service:8082']
```

#### Alerting Rules
```yaml
# Alertmanager configuration
groups:
  - name: lending-services
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
```

### Week 31-32: Documentation & Training

#### API Documentation
```java
@Configuration
@EnableSwagger2
public class SwaggerConfig {
    
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
            .select()
            .apis(RequestHandlerSelectors.basePackage("com.bharatpe"))
            .paths(PathSelectors.any())
            .build()
            .apiInfo(apiInfo());
    }
}
```

#### Team Training Materials
- Service architecture overview
- API documentation
- Deployment procedures
- Troubleshooting guides
- Best practices documentation

## Risk Mitigation Strategies

### 1. Data Consistency
- Implement distributed transactions where needed
- Use event sourcing for audit trails
- Implement compensation patterns for rollbacks

### 2. Performance
- Load testing at each phase
- Performance monitoring and alerting
- Caching strategies
- Database optimization

### 3. Security
- Service-to-service authentication
- API rate limiting
- Data encryption
- Audit logging

### 4. Operational
- Comprehensive monitoring
- Automated deployment
- Rollback procedures
- Disaster recovery plans

## Success Metrics

### Technical Metrics
- **Response Time**: < 200ms for 95% of requests
- **Availability**: 99.9% uptime
- **Error Rate**: < 0.1% failure rate
- **Throughput**: Handle 10,000+ requests per minute

### Business Metrics
- **Feature Delivery**: 50% faster feature delivery
- **System Reliability**: 90% reduction in system-wide outages
- **Developer Productivity**: 40% improvement in development velocity
- **Cost Optimization**: 30% reduction in infrastructure costs

This phased approach ensures a smooth transition from monolith to microservices while maintaining business continuity and minimizing risks.
