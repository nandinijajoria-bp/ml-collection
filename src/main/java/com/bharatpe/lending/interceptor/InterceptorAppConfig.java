package com.bharatpe.lending.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class InterceptorAppConfig implements WebMvcConfigurer {

	@Autowired
	ValidateTokenInterceptor validateTokenInterceptor;

	@Autowired
	InternalClientHmacInterceptor clientHmacInterceptor;

	@Autowired
	HmacForMIDAndInternalClientInterceptor midInterceptor;

	@Autowired
	HmacForMIDInterceptorWithObject hmacForMIDInterceptorWithObject;

	@Autowired
	CommonInterceptor commonInterceptor;

	@Autowired
	LiquiloanInterceptor liquiloanInterceptor;

	@Autowired
	RequestLoggingInterceptor requestLoggingInterceptor;
//	@Autowired
//	ExternalClientHmacInterceptor externalClientHmacInterceptor;

	@Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(validateTokenInterceptor)
				.excludePathPatterns("/lending/internal/**", "/lending/loanDetails/v2", "/lending/loanDetails", "/lending/merchant_loans","/support/fetchBulkContacts/**","/support/showBulkContacts",
				"/lending/push_lead_response",
				"/lending/csPanel/**",
				"/lending/handshake/**", "/lending/common/**", "/lending/liquiloan/**", "/lending/payment/callback","/lending/credit_line" +
				"/application_status_update","/lending/credit_line/vpa/update", "/lending/credit_line/bpb/check_status", "/lending/credit_line/bpb/refund",
				"/partner/details", "/lending/active_loans", "/lending/offers","/lending/allow_bankaccount_change", "/lending/derog_application","/lending" +
				"/fos/**","/fos/**", "/lending/adhaar_mask", "/lending/edi_schedule", "/lending/edi_schedule/v2", "/support/loan","/support/lenderchange",
				"/lending/nach_refund","/support/lender","/lending/processing_fee_refund","/lending/payment/callback/**","/error","/","/actuator/prometheus",
				"/enach/bulkNach","/lending/due_amount","/lending/payment/loan_settlement","/lending/payment/refund", "/support/fldg/**","/support" +
				"/createAgreement/**","/lending/application/resubmit", "/support/nbfcRetry/**","/lending/getLatestLoanDetails", "/lending/pullPayment",
				"/lending/pullPayment/**", "/experian","/experian/update","/experian/insert", "/lending/application", "/lending/first_loan_status",
						"/lending/check_loan_status", "/support/cancelApplication",  "/lending/getLoanDashboardDetails", "/lending/nbfc/mamta/decision/callback",
						"/assign/lender","/assign/rules", "/assign/limit","/assign/update-rule","/assign/update-limit","/support/computeEligibility", "/lending/payment/ledger_entry",
						"/lending/v3/modify/application", "/lending/v3/callback/bre", "/lending/v3/callback/kyc");


        registry.addInterceptor(clientHmacInterceptor).addPathPatterns("/lending/internal/**","/lending/first_loan_status", "/lending/check_loan_status", "/lending/pullPayment",
		"/lending/pullPayment/**", "/support/fetchBulkContacts/**","/support/cancelApplication",
		"/support/showBulkContacts","/lending/liquiloan/approveLoan", "/lending/liquiloan/postPayoutStatusUpdate", "/lending/credit_line/application_status_update",
				"/lending/credit_line/bpb/check_status", "/lending/credit_line/bpb/refund", "/lending/active_loans", "/lending/offers", "/lending/derog_application",
				"/fos/**","/lending/fos/**", "/lending/adhaar_mask","/lending/allow_bankaccount_change", "/lending/edi_schedule", "/lending/edi_schedule/v2",
				"/support/loan", "/lending/nach_refund","/support/lenderchange","/support/lender","/lending/processing_fee_refund","/enach/bulkNach",
				"/lending/common/merchant", "/lending/payment/loan_settlement","/lending/payment/refund", "/support/fldg/**","/lending/application/resubmit",
				"/support/nbfcRetry/**","/lending/getLatestLoanDetails", "/experian","/experian/update","/experian/insert", "/lending/common/lending_cities/active",
				"/lending/common/lending_pincode" , "/lending/common/lending_pancard", "/lending/application", "/lending/liquiloan/postPayout/callback",
		"/lending/liquiloan/postPayout/callback", "/lending/nbfc/mamta/decision/callback","/assign/rules","/assign/limit","/assign/update-rule","/assign/update-limit",
				"/support/computeEligibility", "/lending/payment/ledger_entry");


        registry.addInterceptor(midInterceptor).addPathPatterns( "/lending/payment/callback").addPathPatterns("/lending/credit_line/vpa/update");

        registry.addInterceptor(hmacForMIDInterceptorWithObject).addPathPatterns("/lending/payment/callback/v2");

		registry.addInterceptor(commonInterceptor).addPathPatterns("/lending/due_amount","/lending/merchant_loans", "/lending/loanDetails/v2",
				"/lending/loanDetails", "/lending/getLoanDashboardDetails", "/lending/v3/callback/bre", "/lending/v3/callback/kyc");

//		registry.addInterceptor(externalClientHmacInterceptor).addPathPatterns("/lending/push_lead_response");

		registry.addInterceptor(requestLoggingInterceptor).addPathPatterns("/lending/loanDetails/v2");

		registry.addInterceptor(liquiloanInterceptor).addPathPatterns("/lending/liquiloan/nbfc/postPayout/callback", "/lending/liquiloan/p2p/postPayout/callback","/lending/liquiloan/p2p_of/postPayout/callback");
    }
}
