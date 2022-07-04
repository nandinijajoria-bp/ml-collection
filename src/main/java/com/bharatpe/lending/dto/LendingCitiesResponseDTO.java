package com.bharatpe.lending.dto;

import com.bharatpe.common.entities.LendingCities;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.ObjectUtils;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LendingCitiesResponseDTO {

    private Integer pincode;

    private String city;

    private String state;

    private String area;

    private Boolean agency;

    private Boolean salesTeam;

    private String lockdownEndDate;

    private String categoriesAllowed;

    private Boolean ntcAllowed;

    private Boolean cpvMandatory;

    public static LendingCitiesResponseDTO from(LendingCities lendingCities) {
        if (ObjectUtils.isEmpty(lendingCities)) {
            return  null;
        }

        LendingCitiesResponseDTO lendingCitiesResponseDTO = LendingCitiesResponseDTO.builder()
          .pincode(lendingCities.getPincode())
          .city(lendingCities.getCity())
          .state(lendingCities.getState())
          .area(lendingCities.getArea())
          .agency(lendingCities.getAgency())
          .salesTeam(lendingCities.getSalesTeam())
          .lockdownEndDate(lendingCities.getLockdownEndDate())
          .categoriesAllowed(lendingCities.getCategoriesAllowed())
          .ntcAllowed(lendingCities.getNtcAllowed())
          .cpvMandatory(lendingCities.getCpvMandatory())
          .build();

        return lendingCitiesResponseDTO;

    }
}
