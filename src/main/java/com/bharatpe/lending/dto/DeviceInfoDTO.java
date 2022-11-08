package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceInfoDTO {
	

	private String os;

	private String manufacturer;

	private String device;

	@JsonProperty("is_virtual")
	private Boolean isVirtual;

	@JsonProperty("app_version")
	private String appVersion;

	public Boolean getVirtual() {
		return isVirtual;
	}

	public void setVirtual(Boolean virtual) {
		isVirtual = virtual;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public void setAppVersion(String appVersion) {
		this.appVersion = appVersion;
	}

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
		return "{\"os\":" + os +
						  "\"manufacturer\":" + manufacturer +
						  "\"device\":" + device +
						  "\"is_virtual\":" + isVirtual + "}";
	}

}
