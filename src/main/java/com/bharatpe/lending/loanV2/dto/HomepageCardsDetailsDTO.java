package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.enums.HomePageCardsState;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class HomepageCardsDetailsDTO {
    @JsonIgnore
    private HomePageCardsState state;  //internal use only
    private String cardType;
    private String statusText;
    private String ediAmount;
    private String actionText;
    private String cardState;
    private String journeyText;
    private String loanAmount;
    private String deeplink;
}