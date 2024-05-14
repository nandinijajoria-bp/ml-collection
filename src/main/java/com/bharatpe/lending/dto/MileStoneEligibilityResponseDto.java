package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MileStoneEligibilityResponseDto {
    private Boolean milStoneEligibility;
    private Boolean enrollState;
    private Double graphData;
    private String deepLinkUrl;
    private String weekCount;
    private Boolean showSplashBanner;
    private Boolean showHomeWidgets;
    private Boolean showRTELoansFlow;
    ProgramActiveData programActiveData;
    ProgramEligibleData programEligibleData;
    private Boolean isMileStoneExpiry;
    private String dsErrorMessage;
    private Integer pinCode;
    private String panCard;
    private Boolean isEligibleForReapply;
    private String programType;

    @Data
    public static class ProgramActiveData {
        private String stripHeading;
        private String progressText;
        private String minorHeading;
        private String majorHeading;
        private String subHeading;
        private String buttonText;
        private String progressPercentage;
        private String buttonActionDeeplink;

    }

    @Data
    public static class ProgramEligibleData {
        private String stripHeading;
        private String heading;
        private String subHeading;
        private String buttonText;
        private String bannerImage;
        private String buttonActionDeeplink;
    }

}
