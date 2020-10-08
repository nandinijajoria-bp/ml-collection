package com.bharatpe.lending.dto;

public class CtaDto {

    private String message;
	private String deeplink;

	public CtaDto() {
	}

	public CtaDto(String message, String deeplink) {
		this.message = message;
		this.deeplink = deeplink;
	}

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getDeeplink() {
		return this.deeplink;
	}

	public void setDeeplink(String deeplink) {
		this.deeplink = deeplink;
	}

	@Override
	public String toString() {
		return "CTADto{" +
			" message='" + getMessage() + "'" +
			", deeplink='" + getDeeplink() + "'" +
			"}";
	}

}
