package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.entity.LendingPincodes;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.ObjectUtils;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LendingPincodesResponseDTO {

    private Integer pincode;
    private String city;
    private String state;
    private PincodeColor color;

    public static LendingPincodesResponseDTO from(LendingPincodes lendingPincodes) {
        if (ObjectUtils.isEmpty(lendingPincodes)) {
            return  null;
        }

        LendingPincodesResponseDTO lendingPincodesResponseDTO = LendingPincodesResponseDTO.builder()
          .pincode(lendingPincodes.getPincode())
          .city(lendingPincodes.getCity())
          .state(lendingPincodes.getState())
          .color(lendingPincodes.getColor())
          .build();

        return lendingPincodesResponseDTO;
    }
}
