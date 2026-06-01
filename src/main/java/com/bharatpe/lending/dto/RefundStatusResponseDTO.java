package com.bharatpe.lending.dto;

import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
// @JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundStatusResponseDTO {

	private boolean success;
	private String message;
	private Data data;

	public RefundStatusResponseDTO(long merchantId, Map<Long, List<RefundData>> refundDataMap, BankAccountDetails bankDetailsDto) {
		this.data = new Data();
		this.data.setMerchantId(merchantId);
		this.data.setRefundDataMap(refundDataMap);
		RefundSummary  refundSummary = calculateRefundSummary(refundDataMap);
		this.data.setNetRefundAmount(refundSummary.getNetRefundAmount());
		this.data.setPendingRefundAmount(refundSummary.getPendingRefundAmount());
		this.data.setRefundedAmount(refundSummary.getRefundedAmount());
		this.data.setBankDetailsDto(bankDetailsDto);
		this.success = true;
		this.message = "SUCCESS";
	}

	private RefundSummary calculateRefundSummary(Map<Long, List<RefundData>> refundDataMap) {
		if (CollectionUtils.isEmpty(refundDataMap)) {
			return buildZeroRefundSummary();
		}

		double netRefundAmount = 0.0;
		double pendingRefundAmount = 0.0;
		double refundedAmount = 0.0;
		for (Map.Entry<Long, List<RefundData>> entry : refundDataMap.entrySet()) {
			List<RefundData> refundList = entry.getValue();
			if (CollectionUtils.isEmpty(refundList)) continue;

			for (RefundData _refund : refundList) {
				netRefundAmount += _refund.getRefundAmount() != null ? _refund.getRefundAmount() : 0.0;
				if (_refund.refundInitiated) {
					refundedAmount += _refund.getRefundAmount() != null ? _refund.getRefundAmount() : 0.0;
				}
			}
		}

		if (netRefundAmount < 0.0) netRefundAmount = 0.0;
		if (refundedAmount < 0.0) refundedAmount = 0.0;

		pendingRefundAmount = Math.round(netRefundAmount - refundedAmount);
		if (pendingRefundAmount < 0.0) pendingRefundAmount = 0.0;

		return RefundSummary.builder()
				.netRefundAmount(netRefundAmount)
				.pendingRefundAmount(pendingRefundAmount)
				.refundedAmount(refundedAmount)
				.build();
	}



	public RefundStatusResponseDTO buildEmptySuccessResponse(long merchantId) {
		this.success = true;
		this.message = "SUCCESS";
		this.data = new Data();
		this.data.setMerchantId(merchantId);
		this.data.setRefundDataMap(new HashMap<>());
		return this;
	}

	@Builder
	@lombok.Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RefundSummary {
		private double netRefundAmount;
		private double pendingRefundAmount;
		private double refundedAmount;
	}

	private RefundSummary buildZeroRefundSummary() {
		return RefundSummary.builder()
				.netRefundAmount(0)
				.pendingRefundAmount(0)
				.refundedAmount(0)
				.build();
	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	// @JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	@lombok.Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Data { 
		private long merchantId;
		private double netRefundAmount;
		private double pendingRefundAmount;
		private double refundedAmount;
		private Map<Long, List<RefundData>> refundDataMap;
		private BankAccountDetails bankDetailsDto;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	// @JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	@lombok.Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class RefundData {
		// loan field
		private long loanId;
		private long merchantId;
		private String loanStatus;
		private String lender;


		// refund field
		// bp data
		private Double refundAmount;
		private Double orderAmount;
		private String mode;
		private String bankRefNo;
		private String source;
		private String refundUtrNo;
		private String status;
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Kolkata")
		private Date orderDate;
		private String terminalOrderId;


		// lender data
		private boolean refundInitiated;
		private String remarks;
		private String lenderRemarks;
		private Double transferAmount;
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Kolkata")
		private Date transferDate;


	}
}