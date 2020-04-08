package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class InitiatePaymentResponseDTO {

	private boolean success = true;
	private String message;
	private Data data = new Data();

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Data getData() {
		return data;
	}

	public void setData(Data data) {
		this.data = data;
	}
	
	@Override
	public String toString() {
		return "InitiatePaymentResponseDTO [success=" + success + ", message=" + message + ", data=" + data + "]";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	public static class Data {
		private String vpa = "BHARATPE.0853080088@icici";
		private String intent = "upi://pay?pa=BHARATPE.0853080088@icici&pn=CARD&cu=INR&am=1.0";
		private String paymentLink = "https://bharatpe.in/yHAVj";
		
		public Data() {
			
		}
		
		public Data(String vpa, String intent, String paymentLink) {
			this.vpa = vpa;
			this.intent = intent;
			this.paymentLink = paymentLink;
		}

		public String getVpa() {
			return vpa;
		}

		public void setVpa(String vpa) {
			this.vpa = vpa;
		}

		public String getIntent() {
			return intent;
		}

		public void setIntent(String intent) {
			this.intent = intent;
		}

		public String getPaymentLink() {
			return paymentLink;
		}

		public void setPaymentLink(String paymentLink) {
			this.paymentLink = paymentLink;
		}

		@Override
		public String toString() {
			return "Data [vpa=" + vpa + ", intent=" + intent + ", paymentLink=" + paymentLink + "]";
		}
	}
}
