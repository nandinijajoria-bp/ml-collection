package com.bharatpe.lending.dto.underwriting.write;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LendingPancardWriteDto {
    @NotNull(message = "merchantId cannot be null")
    private Long merchantId;
    private String pancardNumber;
    private String name;
}