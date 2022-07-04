package com.bharatpe.lending.dto;


import com.bharatpe.common.entities.Experian;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.ObjectUtils;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExperianResponseDTO {

    private Long id;

    private Date createdAt;

    private Date updatedAt;

    private Long merchantId;

    private String ip;

    private Double latitude;

    private Double longitude;

    private String response;

    private String merchantName;

    private String email;

    private Boolean rejected;

    private String reason;

    private Integer requestedLoanAmount;

    private String pancardNumber;

    private Boolean tnc;

    private Double bpScore;

    private Double experianScore;

    private String category;

    private String color;

    private Integer retryCount;

    private boolean skip;

    private Integer pincode;

    private Date rejectedDate;

    private Date reportDate;

    private Double eligibleAmount;

    private String eligibleTenure;

    private String loanType;

    private String source;

    private String bureau;

    private String hitId;

    public static ExperianResponseDTO from(Experian experian) {
        if (ObjectUtils.isEmpty(experian)) {
            return  null;
        }

        ExperianResponseDTO experianResponseDTO = ExperianResponseDTO.builder()
          .id(experian.getId())
          .createdAt(experian.getCreatedAt())
          .updatedAt(experian.getUpdatedAt())
          .merchantId(experian.getMerchantId())
          .ip(experian.getIp())
          .latitude(experian.getLatitude())
          .longitude(experian.getLongitude())
          .response(experian.getResponse())
          .merchantName(experian.getMerchantName())
          .email(experian.getEmail())
          .rejected(experian.getRejected())
          .reason(experian.getReason())
          .requestedLoanAmount(experian.getRequestedLoanAmount())
          .pancardNumber(experian.getPancardNumber())
          .tnc(experian.getTnc())
          .bpScore(experian.getBpScore())
          .experianScore(experian.getExperianScore())
          .category(experian.getCategory())
          .color(experian.getColor())
          .retryCount(experian.getRetryCount())
          .skip(experian.isSkip())
          .pincode(experian.getPincode())
          .rejectedDate(experian.getRejectedDate())
          .reportDate(experian.getReportDate())
          .eligibleAmount(experian.getEligibleAmount())
          .eligibleTenure(experian.getEligibleTenure())
          .loanType(experian.getLoanType())
          .source(experian.getSource())
          .bureau(experian.getBureau())
          .hitId(experian.getHitId())
          .build();

        return experianResponseDTO;
    }
}
