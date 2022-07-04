package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.LendingPancard;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.ObjectUtils;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LendingPancardResponseDTO {

    private Long id;

    private Date createdAt;

    private Date updatedAt;

    private Long merchantId;

    private String pancardNumber;

    private String name;

    private String gstNumber;

    private String response;

    public static LendingPancardResponseDTO from (LendingPancard lendingPancard) {
        if (ObjectUtils.isEmpty(lendingPancard)) {
            return  null;
        }

        LendingPancardResponseDTO lendingPancardResponseDTO = LendingPancardResponseDTO.builder()
          .id(lendingPancard.getId())
          .createdAt(lendingPancard.getCreatedAt())
          .updatedAt(lendingPancard.getUpdatedAt())
          .merchantId(lendingPancard.getMerchantId())
          .pancardNumber(lendingPancard.getPancardNumber())
          .name(lendingPancard.getName())
          .gstNumber(lendingPancard.getGstNumber())
          .response(lendingPancard.getResponse())
          .build();

        return lendingPancardResponseDTO;
    }
}
