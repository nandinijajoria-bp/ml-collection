package com.bharatpe.lending.loanV3.dto.response.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFForeclosureDetailsResponseDTO {


    private ResponseData data;
    private String error;
    private String statusCode;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseData {
        private Detail details;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Detail {
        private Double disbursalAmount;
        private String disbursalDate;
        private Due dues;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Due {
        private List<DueComponent> dueComponents;
        private Double totalDueAmount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DueComponent {
        private int chargeCode;
        private int parentChargeCode;
        private String chargeDescription;
        private String isCurrentMonth;
        private Double chargeAmount;
        private Double taxAmount;
        private Double effectiveChargeAmount;
        private Double waivedAmount;

    }
}
