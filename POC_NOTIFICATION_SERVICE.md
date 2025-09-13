# Proof of Concept: Notification Service Implementation

## Overview
The Notification Service will be the first microservice extracted from the monolithic application. It's chosen because:
- Low risk (non-critical business logic)
- Clear boundaries
- Minimal dependencies on other services
- Easy to test and validate

## Current Notification Components Analysis

### Existing Notification Logic in Monolith:
1. **SMS Notifications** - CleverTap integration
2. **Email Notifications** - Various email templates
3. **Push Notifications** - Mobile app notifications
4. **Event Publishing** - Kafka event publishing
5. **OTP Services** - Generic OTP verification

### Key Classes to Extract:
- `GenericOTPVerifyController`
- CleverTap integration services
- Email notification handlers
- SMS notification services
- Event publishing utilities

## Service Architecture

### 1. Service Structure
```
notification-service/
├── src/main/java/com/bharatpe/notification/
│   ├── controller/
│   │   ├── NotificationController.java
│   │   ├── OTPController.java
│   │   └── EventController.java
│   ├── service/
│   │   ├── NotificationService.java
│   │   ├── SMSService.java
│   │   ├── EmailService.java
│   │   ├── CleverTapService.java
│   │   └── OTPService.java
│   ├── dto/
│   │   ├── NotificationRequest.java
│   │   ├── NotificationResponse.java
│   │   └── OTPRequest.java
│   ├── entity/
│   │   ├── NotificationLog.java
│   │   └── OTPRecord.java
│   ├── repository/
│   │   ├── NotificationLogRepository.java
│   │   └── OTPRecordRepository.java
│   └── config/
│       ├── NotificationConfig.java
│       └── KafkaConfig.java
├── src/main/resources/
│   ├── application.yml
│   ├── templates/
│   │   ├── email/
│   │   └── sms/
│   └── logback-spring.xml
└── pom.xml
```

### 2. API Design

#### Notification API
```yaml
POST /api/v1/notifications/send
Content-Type: application/json

{
  "merchantId": 12345,
  "type": "SMS|EMAIL|PUSH",
  "template": "LOAN_APPROVED",
  "recipient": {
    "phone": "+919876543210",
    "email": "merchant@example.com"
  },
  "data": {
    "loanAmount": 50000,
    "approvalDate": "2024-01-15"
  },
  "priority": "HIGH|MEDIUM|LOW"
}

Response:
{
  "success": true,
  "messageId": "notif_12345",
  "status": "SENT|PENDING|FAILED",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### OTP API
```yaml
POST /api/v1/otp/generate
{
  "merchantId": 12345,
  "phone": "+919876543210",
  "purpose": "LOAN_APPLICATION|PAYMENT|KYC"
}

POST /api/v1/otp/verify
{
  "merchantId": 12345,
  "phone": "+919876543210",
  "otp": "123456",
  "purpose": "LOAN_APPLICATION|PAYMENT|KYC"
}
```

### 3. Database Schema

#### Notification Log Table
```sql
CREATE TABLE notification_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL, -- SMS, EMAIL, PUSH
    template VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    message TEXT,
    status VARCHAR(20) NOT NULL, -- SENT, PENDING, FAILED, DELIVERED
    external_id VARCHAR(100), -- CleverTap ID, SMS provider ID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);
```

#### OTP Record Table
```sql
CREATE TABLE otp_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    phone VARCHAR(15) NOT NULL,
    otp VARCHAR(10) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL, -- GENERATED, VERIFIED, EXPIRED
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_phone (merchant_id, phone),
    INDEX idx_expires_at (expires_at)
);
```

## Implementation Steps

### Phase 1: Service Setup (Week 1)

#### 1.1 Create Service Structure
```bash
# Create new Spring Boot project
mkdir notification-service
cd notification-service

# Initialize Maven project
mvn archetype:generate -DgroupId=com.bharatpe.notification \
  -DartifactId=notification-service \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false
```

#### 1.2 Dependencies (pom.xml)
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
</dependencies>
```

#### 1.3 Application Configuration
```yaml
# application.yml
server:
  port: 8081

spring:
  application:
    name: notification-service
  datasource:
    url: jdbc:mysql://localhost:3306/notification_db
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

kafka:
  bootstrap-servers: localhost:9092
  consumer:
    group-id: notification-service

notification:
  sms:
    provider: clever-tap
    api-key: ${SMS_API_KEY}
  email:
    provider: sendgrid
    api-key: ${EMAIL_API_KEY}
  otp:
    expiry-minutes: 5
    length: 6
```

### Phase 2: Core Implementation (Week 2)

#### 2.1 Entity Classes
```java
@Entity
@Table(name = "notification_log")
public class NotificationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "merchant_id")
    private Long merchantId;
    
    @Enumerated(EnumType.STRING)
    private NotificationType type;
    
    private String template;
    private String recipient;
    private String message;
    
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;
    
    @Column(name = "external_id")
    private String externalId;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // Getters and setters
}
```

#### 2.2 Service Implementation
```java
@Service
@Slf4j
public class NotificationService {
    
    @Autowired
    private NotificationLogRepository notificationLogRepository;
    
    @Autowired
    private SMSService smsService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private CleverTapService cleverTapService;
    
    public NotificationResponse sendNotification(NotificationRequest request) {
        try {
            // Log notification attempt
            NotificationLog log = createNotificationLog(request);
            
            // Send based on type
            String externalId = null;
            switch (request.getType()) {
                case SMS:
                    externalId = smsService.sendSMS(request);
                    break;
                case EMAIL:
                    externalId = emailService.sendEmail(request);
                    break;
                case PUSH:
                    externalId = cleverTapService.sendPushNotification(request);
                    break;
            }
            
            // Update log with result
            log.setExternalId(externalId);
            log.setStatus(NotificationStatus.SENT);
            notificationLogRepository.save(log);
            
            return NotificationResponse.builder()
                .success(true)
                .messageId(log.getId().toString())
                .status(NotificationStatus.SENT)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage());
            return NotificationResponse.builder()
                .success(false)
                .status(NotificationStatus.FAILED)
                .timestamp(LocalDateTime.now())
                .build();
        }
    }
}
```

### Phase 3: Integration (Week 3)

#### 3.1 Event-Driven Communication
```java
@Component
@Slf4j
public class NotificationEventListener {
    
    @Autowired
    private NotificationService notificationService;
    
    @KafkaListener(topics = "loan-application-events")
    public void handleLoanApplicationEvent(LoanApplicationEvent event) {
        log.info("Received loan application event: {}", event);
        
        // Send appropriate notification based on event type
        switch (event.getEventType()) {
            case APPLICATION_CREATED:
                sendApplicationCreatedNotification(event);
                break;
            case APPLICATION_APPROVED:
                sendApplicationApprovedNotification(event);
                break;
            case APPLICATION_REJECTED:
                sendApplicationRejectedNotification(event);
                break;
        }
    }
    
    private void sendApplicationCreatedNotification(LoanApplicationEvent event) {
        NotificationRequest request = NotificationRequest.builder()
            .merchantId(event.getMerchantId())
            .type(NotificationType.SMS)
            .template("APPLICATION_CREATED")
            .recipient(Recipient.builder()
                .phone(event.getPhoneNumber())
                .build())
            .data(event.getData())
            .build();
            
        notificationService.sendNotification(request);
    }
}
```

#### 3.2 API Gateway Integration
```yaml
# API Gateway routes
routes:
  - id: notification-service
    uri: lb://notification-service
    predicates:
      - Path=/api/v1/notifications/**
    filters:
      - StripPrefix=2
```

### Phase 4: Testing & Validation (Week 4)

#### 4.1 Unit Tests
```java
@SpringBootTest
class NotificationServiceTest {
    
    @Autowired
    private NotificationService notificationService;
    
    @MockBean
    private SMSService smsService;
    
    @Test
    void testSendSMSNotification() {
        // Given
        NotificationRequest request = createTestNotificationRequest();
        when(smsService.sendSMS(any())).thenReturn("sms_12345");
        
        // When
        NotificationResponse response = notificationService.sendNotification(request);
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.SENT);
    }
}
```

#### 4.2 Integration Tests
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class NotificationIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testNotificationAPI() {
        // Test notification sending
        NotificationRequest request = createTestRequest();
        ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
            "/api/v1/notifications/send", request, NotificationResponse.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
    }
}
```

## Migration from Monolith

### Step 1: Extract Notification Logic
1. Identify all notification-related code in the monolith
2. Create interfaces for notification services
3. Implement new notification service
4. Update monolith to call notification service via HTTP

### Step 2: Update Monolith Integration
```java
// In the monolith, replace direct notification calls with service calls
@Service
public class LendingApplicationService {
    
    @Autowired
    private NotificationServiceClient notificationServiceClient;
    
    public void createApplication(CreateApplicationRequest request) {
        // Existing application creation logic
        
        // Send notification via service
        NotificationRequest notificationRequest = NotificationRequest.builder()
            .merchantId(merchant.getId())
            .type(NotificationType.SMS)
            .template("APPLICATION_CREATED")
            .recipient(Recipient.builder().phone(merchant.getPhone()).build())
            .data(applicationData)
            .build();
            
        notificationServiceClient.sendNotification(notificationRequest);
    }
}
```

### Step 3: Gradual Migration
1. **Week 1**: Deploy notification service alongside monolith
2. **Week 2**: Update monolith to use notification service for new notifications
3. **Week 3**: Migrate existing notification flows
4. **Week 4**: Remove notification code from monolith

## Success Metrics

### Technical Metrics
- **Response Time**: < 200ms for notification API calls
- **Availability**: 99.9% uptime
- **Error Rate**: < 0.1% failure rate
- **Throughput**: Handle 1000+ notifications per minute

### Business Metrics
- **Notification Delivery Rate**: > 95%
- **OTP Verification Success Rate**: > 98%
- **User Satisfaction**: Improved notification reliability
- **Development Velocity**: Faster notification feature development

## Rollback Plan

### Immediate Rollback (if critical issues)
1. Route API Gateway back to monolith notification endpoints
2. Disable notification service
3. Revert monolith changes

### Gradual Rollback (if performance issues)
1. Reduce traffic to notification service
2. Investigate and fix issues
3. Gradually increase traffic back

## Next Steps After PoC

1. **Validate PoC Results**
   - Measure performance metrics
   - Gather team feedback
   - Document lessons learned

2. **Plan Next Service**
   - Choose next service to extract (likely Agreement & Documentation)
   - Apply lessons from notification service
   - Refine migration process

3. **Scale Infrastructure**
   - Set up monitoring and alerting
   - Implement CI/CD pipelines
   - Prepare for larger service extraction

This PoC will validate the microservices approach and provide a template for extracting the remaining services.
