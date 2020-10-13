package com.bharatpe.lending.dto;

public class PayloadDTO {
	private String op;
	private String key;
	private String value;

	public PayloadDTO() {
	}

	public PayloadDTO(String op, String key, String value) {
		this.op = op;
		this.key = key;
		this.value = value;
	}

	public String getOp() {
		return this.op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public String getKey() {
		return this.key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "{" + " 'op': '" + getOp() + "'" + ", 'key': '" + getKey() + "'" + ", 'value': '" + getValue() + "'"
				+ "}";
	}
}
