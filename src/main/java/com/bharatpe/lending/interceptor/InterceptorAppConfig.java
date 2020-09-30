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
	
	@Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(validateTokenInterceptor)
				.excludePathPatterns("/lending/csPanel/**", "/lending/handshake/**", "/lending/common/**", "/lending/liquiloan/**", "/lending/payment/callback","/lending/credit_line/application_status_update","/lending/credit_line/vpa/update", "/lending/credit_line/bpb/check_status", "/lending/credit_line/bpb/refund", "/partner/details", "/lending/active_loans", "/lending/offers");

        registry.addInterceptor(clientHmacInterceptor).addPathPatterns("/lending/liquiloan/approveLoan", "/lending/liquiloan/postPayoutStatusUpdate", "/lending/credit_line/application_status_update", "/lending/credit_line/bpb/check_status", "/lending/credit_line/bpb/refund", "/lending/active_loans", "/lending/offers");

        registry.addInterceptor(midInterceptor).addPathPatterns( "/lending/payment/callback").addPathPatterns("/lending/credit_line/vpa/update");
    }
}
