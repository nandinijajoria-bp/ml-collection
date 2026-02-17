package com.bharatpe.lending.dto.underwriting.read;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class LendingPancardReadDTO {
    private Long merchantId;
    private String pancardNumber;
    private String name;
    private String gstNumber;
    private String response;
    private Long id;
    private Date createdAt;
    private Date updatedAt;
}
