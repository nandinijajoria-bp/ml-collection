package com.bharatpe.lending.dto;

public class CreditLineKycResponseDto {
	
	private String fullAddress;
	
	private String  city;
	
	private String state;
	
	private Long pincode;

	public String getFullAddress() {
		return fullAddress;
	}

	public void setFullAddress(String fullAddress) {
		this.fullAddress = fullAddress;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Long getPincode() {
		return pincode;
	}

	public void setPincode(Long pincode) {
		this.pincode = pincode;
	}

	@Override
	public String toString() {
		return "CreditLineKycResponseDto [fullAddress=" + fullAddress + ", city=" + city + ", state=" + state
				+ ", pincode=" + pincode + "]";
	}
	

}

 