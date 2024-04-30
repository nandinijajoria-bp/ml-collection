package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerifyPanCardResponseDto {
    private Boolean status=true;
    private String message;
    private Boolean maxCountReached;
    private Boolean isPanVerified;
    private Boolean isNameVerified;
    private Boolean isDobVerified;

    public VerifyPanCardResponseDto(Boolean status, String message, Boolean isPanVerified, Boolean isNameVerified, Boolean isDobVerified) {
        this.status = status;
        this.message = message;
        this.isPanVerified = isPanVerified;
        this.isNameVerified = isNameVerified;
        this.isDobVerified = isDobVerified;
    }

    public VerifyPanCardResponseDto(Boolean status, String message) {
        this.status = status;
        this.message = message;
    }

    public VerifyPanCardResponseDto(Boolean status, String message, Boolean maxCountReached) {
        this.status = status;
        this.message = message;
        this.maxCountReached = maxCountReached;
    }

    public VerifyPanCardResponseDto() {
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getIsNameVerified() {
        return isNameVerified;
    }

    public void setIsNameVerified(Boolean nameVerified) {
        isNameVerified = nameVerified;
    }

    public Boolean getIsDobVerified() {
        return isDobVerified;
    }

    public void setIsDobVerified(Boolean dobVerified) {
        isDobVerified = dobVerified;
    }

    public Boolean getIsPanVerified() {
        return isPanVerified;
    }

    public void setIsPanVerified(Boolean panVerified) {
        isPanVerified = panVerified;
    }

    public Boolean getMaxCountReached() {
        return maxCountReached;
    }

    public void setMaxCountReached(Boolean maxCountReached) {
        this.maxCountReached = maxCountReached;
    }

    @Override
    public String toString() {
        return "VerifyPanCardResponseDto{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", maxCountReached=" + maxCountReached +
                ", isPanVerified=" + isPanVerified +
                ", isNameVerified=" + isNameVerified +
                ", isDobVerified=" + isDobVerified +
                '}';
    }
}
