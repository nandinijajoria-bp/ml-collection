package com.bharatpe.lending.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditLineHistoryResponseDto {
	
	private Boolean success=true;
	
	private String message="";
	
	private Double availableBalance;
	
	private Double totalPayable;
	
	private List<Loan> TL;
	
	private List<Loan> CL;
	
	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	public static class Loan{
		
			private Long id;
			
			private Double amount;
			
			private Double edi;
			
			private Integer tenure;
			
			private Date date;
			
			private String spendMode;
			
			private String icon;

			public Long getId() {
				return id;
			}

			public void setId(Long id) {
				this.id = id;
			}

			public Double getAmount() {
				return amount;
			}

			public void setAmount(Double amount) {
				this.amount = amount;
			}

			public Double getEdi() {
				return edi;
			}

			public void setEdi(Double edi) {
				this.edi = edi;
			}

			public Integer getTenure() {
				return tenure;
			}

			public void setTenure(Integer tenure) {
				this.tenure = tenure;
			}

			public Date getDate() {
				return date;
			}

			public void setDate(Date date) {
				this.date = date;
			}

			public String getSpendMode() {
				return spendMode;
			}

			public void setSpendMode(String spendMode) {
				this.spendMode = spendMode;
			}

			public String getIcon() {
				return icon;
			}

			public void setIcon(String icon) {
				this.icon = icon;
			}

		}

	public Boolean getSuccess() {
		return success;
	}

	public void setSuccess(Boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Double getAvailableBalance() {
		return availableBalance;
	}

	public void setAvailableBalance(Double availableBalance) {
		this.availableBalance = availableBalance;
	}

	public Double getTotalPayable() {
		return totalPayable;
	}

	public void setTotalPayable(Double totalPayable) {
		this.totalPayable = totalPayable;
	}

	public List<Loan> getTL() {
		return TL;
	}

	public void setTL(List<Loan> tL) {
		TL = tL;
	}

	public List<Loan> getCL() {
		return CL;
	}

	public void setCL(List<Loan> cL) {
		CL = cL;
	}
		
	}