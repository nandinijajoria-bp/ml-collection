package com.bharatpe.lending.lendingplatform.nbfc.service.builder.pojo;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.CSCustomerAdditionalData;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class CSCustomerAddressDataBuilder {

    @Autowired
    KycUtils kycUtils;
    @Autowired
    CreditSaisonConfig csConfig;

    public CSCustomerAdditionalData buildCSCustomerAdditipnalData(CKycResponseDto cKycResponseDto, Long applicationId, Long merchantId) {
        CKycResponseDto poa = kycUtils.parsePoaXML(cKycResponseDto.getPoaString(), merchantId, cKycResponseDto, applicationId);

        String mobile = ObjectUtils.isEmpty(cKycResponseDto.getBureauMobile()) ? kycUtils.getMobileFromKycData(cKycResponseDto) : cKycResponseDto.getBureauMobile();

        String address = Stream.of(
                        poa.getHouse(),
                        poa.getStreet(),
                        poa.getLoc(),
                        poa.getLm(),
                        poa.getPo(),
                        poa.getSubdist(),
                        poa.getDist()
                )
                .filter(value -> value != null && !value.isEmpty())
                .collect(Collectors.joining(","));

        int addressSize = address.length();
        String address1 = address.replaceFirst("^(C/O|S/O|D/O|W/O)\\s*", ""), address2 = null;
        if (addressSize > csConfig.getMaxLengthAddressLine1()) {
            address1 = address1.substring(csConfig.getMinLengthAddressLine1(), csConfig.getMaxLengthAddressLine1());
            address2 = address1.substring(csConfig.getMaxLengthAddressLine1(), csConfig.getMaxLengthAddressLine2());
        }
        return CSCustomerAdditionalData.builder()
                .line1(address1)
                .line2(address2)
                .city(poa.getCity())
                .state(csConfig.getState(cKycResponseDto.getState()))
                .country(csConfig.getCountry())
                .pinCode(cKycResponseDto.getPincode())
                .mobile(mobile)
                .build();
    }
}
