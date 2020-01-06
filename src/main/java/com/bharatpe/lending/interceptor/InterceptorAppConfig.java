package com.bharatpe.lending.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class InterceptorAppConfig implements WebMvcConfigurer {

	@Autowired
    private ValidateTokenInterceptor validateTokenInterceptor;
	
	@Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(validateTokenInterceptor)
        .excludePathPatterns("/lending/csPanel/**")
        .excludePathPatterns("/lending/ping");
        
//        registry.addInterceptor(new AccessControlInterceptor());
    }
}
