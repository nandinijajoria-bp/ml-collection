package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class CSCustomerAdditionalData {
    private String type;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String country;
    private String pinCode;
    private String mobile;
    private Integer priority;
}
