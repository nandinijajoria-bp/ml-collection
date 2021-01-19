package com.bharatpe.lending.dto;

public class TncDto {
	
	private boolean success=true;
    private String message;
	private String htmlString;
	private boolean isSwipe = false;
	public boolean isSuccess() {
		return success;
	}
	public void setSuccess(boolean success) {
		this.success = success;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getHtmlString() {
		return htmlString;
	}
	public void setHtmlString(String htmlString) {
		this.htmlString = htmlString;
	}

	public boolean isSwipe() {
		return isSwipe;
	}

	public void setSwipe(boolean swipe) {
		isSwipe = swipe;
	}
}
