package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceInfoDTO {

	private String os;

	private String manufacturer;

	private String device;

	@JsonProperty("is_virtual")
	private Boolean isVirtual;

	public String getOs() {
		return os;
	}

	public void setOs(String os) {
		this.os = os;
	}

	public String getManufacturer() {
		return manufacturer;
	}

	public void setManufacturer(String manufacturer) {
		this.manufacturer = manufacturer;
	}

	public String getDevice() {
		return device;
	}

	public void setDevice(String device) {
		this.device = device;
	}

	public Boolean getIsVirtual() {
		return isVirtual;
	}

	public void setIsVirtual(Boolean isVirtual) {
		this.isVirtual = isVirtual;
	}
	
	@Override
	public String toString() {
		String response = "DeviceInfo { os : " + os +
						  " manufacturer : " + manufacturer +
						  " device : " + device +
						  " is_virtual : " + isVirtual + "}";
		return response;
	}

}
