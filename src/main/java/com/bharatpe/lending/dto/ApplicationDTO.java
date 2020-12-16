package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Date;

public class ApplicationDTO {

	private String text;

	private String comment;

	private String status;

	@JsonProperty(value = "date")
	private DateDTO dateDTO;

//	@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
	public static class DateDTO{
		private String day;
		private String time;

		public String getDay() {
			return day;
		}

		public void setDay(String day) {
			this.day = day;
		}

		public String getTime() {
			return time;
		}

		public void setTime(String time) {
			this.time = time;
		}
	}


//	private  String buttonContext;
	@JsonProperty(value = "button_context")
	private ButtonContextDTO buttonContextDTO;

	public static class ButtonContextDTO{
		private String text;
		private String action;
		private String deeplink;

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public String getAction() {
			return action;
		}

		public void setAction(String action) {
			this.action = action;
		}

		public String getDeeplink() {
			return deeplink;
		}

		public void setDeeplink(String deeplink) {
			this.deeplink = deeplink;
		}
	}

	private boolean disabled;

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public DateDTO getDateDTO() {
		return dateDTO;
	}

	public void setDateDTO(DateDTO dateDTO) {
		this.dateDTO = dateDTO;
	}

	public ButtonContextDTO getButtonContextDTO() {
		return buttonContextDTO;
	}

	public void setButtonContextDTO(ButtonContextDTO buttonContextDTO) {
		this.buttonContextDTO = buttonContextDTO;
	}

	public boolean isDisabled() {
		return disabled;
	}

	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
}
