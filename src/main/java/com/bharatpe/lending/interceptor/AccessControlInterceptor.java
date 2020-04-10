package com.bharatpe.lending.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

public class AccessControlInterceptor extends HandlerInterceptorAdapter {
	
	@Override
	 public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		  response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
		 response.setHeader("Access-Control-Allow-Origin", "*");
	      response.setHeader("Access-Control-Allow-Headers", "x-requested-with, Content-Type, token, origin, authorization, accept, client-security-token");
	      response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
		  return true;
	 }
}
