package com.bharatpe.lending.dto;

import lombok.Data;

import java.math.BigInteger;

@Data
public class PushDataToHomepageCarouselDto {
    private String event_id;
    private String client;
    private String store_id;
    private BigInteger merchant_id;
    private String heading;
    private String sub_heading;
    private String cta;
    private Integer banner_image_id;
    private String deeplink;
    private Long start_time;
    private Long end_time;
    private Integer priority;
    private String event_type;

}
