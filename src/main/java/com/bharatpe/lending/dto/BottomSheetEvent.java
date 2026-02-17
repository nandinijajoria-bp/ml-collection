package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigInteger;
import java.util.List;

@Data
public class BottomSheetEvent {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("event_type")
    private String eventType;

    private String client;

    private String heading;

    @JsonProperty("sub_headings")
    private List<String> subHeadings;

    @JsonProperty("submit_cta")
    private SubmitCta submitCta;

    private String image;

    private String label;

    @JsonProperty("label_icon")
    private String labelIcon;

    private Integer priority;

    @JsonProperty("start_time")
    private Long startTime;

    private Integer frequency;

    @JsonProperty("merchant_id")
    private BigInteger merchantId;

    @JsonProperty("store_id")
    private String storeId;

    @JsonProperty("event_tag")
    private String eventTag;
}
