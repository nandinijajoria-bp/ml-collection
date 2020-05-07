package com.bharatpe.lending.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class InterceptorAppConfig implements WebMvcConfigurer {

	@Autowired
    private ValidateTokenInterceptor validateTokenInterceptor;
	
	@Autowired
	private ExternalClientHmacInterceptor externalClientHmacInterceptor;
	
	@Autowired
	private InternalClientHmacInterceptor clientHmacInterceptor;
	
	@Autowired
	private HmacForMIDAndInternalClientInterceptor midInterceptor;
	
	@Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(validateTokenInterceptor)
				.excludePathPatterns("/lending/csPanel/**", "/lending/handshake/**", "/lending/common/**", "/lending/liquiloan/**", "/lending/payment/callback", "/partner/details");
        
        registry.addInterceptor(externalClientHmacInterceptor).addPathPatterns("/lending/liquiloan/approveLoan","/lending/liquiloan/settlement");
        
        registry.addInterceptor(clientHmacInterceptor).addPathPatterns("/lending/liquiloan/postPayoutStatusUpdate");
        
        registry.addInterceptor(midInterceptor).addPathPatterns( "/lending/payment/callback");
        
    }
}
