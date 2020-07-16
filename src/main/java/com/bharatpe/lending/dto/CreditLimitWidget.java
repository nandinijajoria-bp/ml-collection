package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreditLimitWidget {
	
	private Double limit;
	private Double usedLimit;
	private Double availableLimit;
	
	public Double getLimit() {
		return limit;
	}
	public void setLimit(Double limit) {
		this.limit = limit;
	}
	public Double getUsedLimit() {
		return usedLimit;
	}
	public void setUsedLimit(Double usedLimit) {
		this.usedLimit = usedLimit;
	}
	public Double getAvailableLimit() {
		return availableLimit;
	}
	public void setAvailableLimit(Double availableLimit) {
		this.availableLimit = availableLimit;
	}
	
	@Override
	public String toString() {
		return "CreditLimitWidget [limit=" + limit + ", usedLimit=" + usedLimit + ", availableLimit=" + availableLimit
				+ "]";
	}	
	
}
