package com.bharatpe.lending.util;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.MerchantReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class ValidationUtil {

    private final CommonUtil commonUtil;

    public ValidationUtil(CommonUtil commonUtil) {
        this.commonUtil = commonUtil;
    }


    public Pair<Boolean,String> isMerchantReferenceValid(MerchantReference reference, BasicDetailsDto merchant, String merchantName) {
        String name = reference.getName();
        if (StringUtils.isEmpty(name)) {
            log.info("Reference name is Empty!");
            return Pair.of(false,"Reference name cannot be empty.");
        }
        String strippedName = name.replaceAll(" ", "");

        // Rule 1: Name cannot be the same as the merchant's name
        if (!StringUtils.isEmpty(merchantName) && name.equalsIgnoreCase(merchant.getName())) {
            log.info("reference name matches with merchant name, {}", name);
            return Pair.of(false,"Reference name matches with merchant name : " + name);
        }

        // Rule 2: Must have at least 3 consecutive characters
        if (!commonUtil.hasAtLeastThreeConsecutiveChars(name)) {
            log.info("reference name is not having atleast three consecutive chars, {}", name);
            return Pair.of(false,"Reference name lacks at least three consecutive characters : " + name );
        }

        // Rule 3: Must not contain any numerical or special characters
        if (!strippedName.matches("[a-zA-Z]+")) {
            log.info("reference name is having special or numeric chars, {}", name);
            return Pair.of(false,"Reference name is having special or numeric chars : " + name);
        }

        // Rule 4: Must not be entirely consecutive letters
        if (commonUtil.isAllConsecutiveLetters(strippedName)) {
            log.info("reference name having all consecutive letters, {}", name);
            return Pair.of(false,"Reference name having all consecutive letters : " + name);
        }

        String merchantMobile = merchant.getMobile();
        String referenceMobile = reference.getPhoneNumber();
        if (StringUtils.isEmpty(merchantMobile) || StringUtils.isEmpty(referenceMobile) || referenceMobile.length() < 10) {
            log.info("reference mobile is empty or length is less than 10");
            return Pair.of(false,"reference mobile is empty or length is less than 10 : " + referenceMobile);
        }
        merchantMobile = merchantMobile.length() == 12 ? merchantMobile.substring(2) : merchantMobile;
        referenceMobile = referenceMobile.length() == 12 ? referenceMobile.substring(2) : referenceMobile;

        // Rule 1: Mobile number cannot be the same as the merchant's number
        if (referenceMobile.equals(merchantMobile)) {
            log.info("merchant mobile matches with reference mobile, {}", referenceMobile);
            return Pair.of(false,"merchant mobile matches with reference mobile : " + referenceMobile);
        }

        // Rule 2: Consecutive numbers for more than 4 values are not allowed
        if (commonUtil.hasMoreThanFourConsecutiveNumbers(referenceMobile)) {
            log.info("reference mobile having more than 4 consecutive numbers, {}", referenceMobile);
            return Pair.of(false,"reference mobile having more than 4 consecutive numbers : " + referenceMobile);
        }

        // Rule 3: The same digit repeated more than 4 times is not allowed
        if (commonUtil.hasMoreThanFourSameDigits(referenceMobile)) {
            log.info("reference mobile having same digit more than 4 times, {}", referenceMobile);
            return Pair.of(false,"reference mobile having same digit more than 4 times : " + referenceMobile);
        }
        return Pair.of(true,"");
    }
}
