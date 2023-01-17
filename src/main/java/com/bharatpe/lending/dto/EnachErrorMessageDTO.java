package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class EnachErrorMessageDTO {

    private String header;
    private String icon;
    private String message;
    private Boolean showPopup;
    private Boolean skipEnach;

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getShowPopup() {
        return showPopup;
    }

    public void setShowPopup(Boolean showPopup) {
        this.showPopup = showPopup;
    }

    public Boolean getSkipEnach() {
        return skipEnach;
    }

    public void setSkipEnach(Boolean skipEnach) {
        this.skipEnach = skipEnach;
    }
}
