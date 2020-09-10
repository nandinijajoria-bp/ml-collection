package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class InitiatePaymentResponseDTO {

	private boolean success;
	private String message;
	private Data data;
	
	public InitiatePaymentResponseDTO() {
		
	}
	
	public InitiatePaymentResponseDTO(String message) {
		this.message = message;
	}
	
	public InitiatePaymentResponseDTO(InitiatePaymentResponseDTO.Data data) {
		this.success = true;
		this.data = data;
	}

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
		private String vpa;
		private String intent;
		private String paymentLink;
		private List<String> psps;
		private String orderId;

		public Data() {
			
		}
		
		public Data(String vpa, String intent, String paymentLink, String orderId) {
			this.vpa = vpa;
			this.intent = intent;
			this.paymentLink = paymentLink;
			this.orderId = orderId;
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

		public List<String> getPsps() {
			return psps;
		}

		public void setPsps(List<String> psps) {
			this.psps = psps;
		}

		public String getOrderId() {
			return orderId;
		}

		public void setOrderId(String orderId) {
			this.orderId = orderId;
		}

		@Override
		public String toString() {
			return "Data [vpa=" + vpa + ", intent=" + intent + ", paymentLink=" + paymentLink + "]";
		}
	}
}
