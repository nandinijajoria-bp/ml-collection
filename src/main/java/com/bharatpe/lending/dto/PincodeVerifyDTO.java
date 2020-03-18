package com.bharatpe.lending.dto;

public class PincodeVerifyDTO {
	private Boolean eligible;
	private String city;
	private String state;
	public Boolean getEligible() {
		return eligible;
	}
	public void setEligible(Boolean eligible) {
		this.eligible = eligible;
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
	@Override
	public String toString() {
		return "PincodeVerifyDTO [eligible=" + eligible + ", city=" + city + ", state=" + state + "]";
	}
	
	
}
