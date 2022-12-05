package com.bharatpe.lending.dto.payout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.ObjectUtils;

/**
 * @author dhvl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeneficiaryInfoDTO {
    private String ifsc;
    private String accountNo;
    private String beneficiaryName;
    private String mobile;
    private String bankCode;
    private String vpa;

    public static BeneficiaryInfoDTO from(String accountNo, String ifsc, String beneficiaryName,String bankCode) {
        return BeneficiaryInfoDTO.builder()
                .accountNo(accountNo)
                .ifsc(ifsc)
                .beneficiaryName(!ObjectUtils.isEmpty(beneficiaryName) && beneficiaryName.length() > 50 ?
                        beneficiaryName.substring(0, 49) : beneficiaryName)
                .bankCode(bankCode)
                .build();
    }
}
