package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreditLimitWidget {
	
	private Double limit;
	private Long usedLimit;
	private Long availableLimit;
	
	public Double getLimit() {
		return limit;
	}
	public void setLimit(Double limit) {
		this.limit = limit;
	}
	public Long getUsedLimit() {
		return usedLimit;
	}
	public void setUsedLimit(Long usedLimit) {
		this.usedLimit = usedLimit;
	}
	public Long getAvailableLimit() {
		return availableLimit;
	}
	public void setAvailableLimit(Long availableLimit) {
		this.availableLimit = availableLimit;
	}
	@Override
	public String toString() {
		return "CreditLimitWidget [limit=" + limit + ", usedLimit=" + usedLimit + ", availableLimit=" + availableLimit
				+ "]";
	}
	
}
