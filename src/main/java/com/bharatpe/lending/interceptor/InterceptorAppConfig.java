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

//	@Autowired
//	ExternalClientHmacInterceptor externalClientHmacInterceptor;
	
	@Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(validateTokenInterceptor)
				.excludePathPatterns("/lending/push_lead_response","/lending/csPanel/**", "/lending/handshake/**", "/lending/common/**", "/lending/liquiloan/**", "/lending/payment/callback","/lending/credit_line/application_status_update","/lending/credit_line/vpa/update", "/lending/credit_line/bpb/check_status", "/lending/credit_line/bpb/refund", "/partner/details", "/lending/active_loans", "/lending/offers","/lending/allow_bankaccount_change", "/lending/derog_application","/lending/fos/**","/fos/**", "/lending/adhaar_mask", "/lending/edi_schedule", "/lending/edi_schedule/v2", "/support/loan","/support/lenderchange", "/lending/nach_refund","/support/lender","/lending/processing_fee_refund","/lending/payment/callback/**","/error","/","/actuator/prometheus","/enach/bulkNach","/lending/due_amount","/lending/payment/loan_settlement","/lending/payment/refund", "/support/fldg/**","/support/createAgreement/**","/lending/application/resubmit", "/support/nbfcRetry/**");

        registry.addInterceptor(clientHmacInterceptor).addPathPatterns("/lending/liquiloan/approveLoan", "/lending/liquiloan/postPayoutStatusUpdate", "/lending/credit_line/application_status_update", "/lending/credit_line/bpb/check_status", "/lending/credit_line/bpb/refund", "/lending/active_loans", "/lending/offers", "/lending/derog_application","/fos/**","/lending/fos/**", "/lending/adhaar_mask","/lending/allow_bankaccount_change", "/lending/edi_schedule", "/lending/edi_schedule/v2", "/support/loan", "/lending/nach_refund","/support/lenderchange","/support/lender","/lending/processing_fee_refund","/enach/bulkNach", "/lending/common/merchant", "/lending/payment/loan_settlement","/lending/payment/refund", "/support/fldg/**","/support/createAgreement/**","/lending/application/resubmit", "/support/nbfcRetry/**");

        registry.addInterceptor(midInterceptor).addPathPatterns( "/lending/payment/callback").addPathPatterns("/lending/credit_line/vpa/update");

        registry.addInterceptor(hmacForMIDInterceptorWithObject).addPathPatterns("/lending/payment/callback/v2");

		registry.addInterceptor(commonInterceptor).addPathPatterns("/lending/due_amount");

//		registry.addInterceptor(externalClientHmacInterceptor).addPathPatterns("/lending/push_lead_response");
    }
}
