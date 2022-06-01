package com.bharatpe.lending.service.merchant.constants;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Constants {

    @Value("${merchant.service.host}")
    public String MERCHANT_HOST;

    @Value("${merchant.service.secret}")
    public String MERCHANT_CLIENT_SECRET;

    @Value("${merchant.service.client}")
    public String MERCHANT_CLIENT_NAME;

    public static String HASH = "hash";
    public static String CLIENT = "client-name";
    public static String TOKEN = "token";


    public interface MerchantUtil {
        String MERCHANT_ID = "merchantid";

        String MERCHANT_TOKEN_VERIFY_API = "/merchant/v1/verifytoken";
        String MERCHANT_INFO = "/merchant/v1/internalclient/merchantinfo";
        String PARTIAL_UPDATE = "/merchant/v1/partialupdate";

        interface Scope {
            String BANK_DETAIL = "bankdetail";
            String ADDRESS = "address";
            String VPA = "vpa";
            String MERCHANT_USER = "merchantUser";
        }

        interface Operation {
            String SET = "set";
        }

        interface PartialUpdateKey {

            interface MerchantAddressKeys {
                String ADDRESS = "address";
                String ZIPCODE = "zipcode";
                String LONGITUDE = "longitude";
                String LATITUDE = "latitude";
                String ADDRESS_TYPE = "addresstype";
                String STATE = "state";
                String CITY = "city";
            }

            interface MerchantKeys {
                String ADDRESS = "merchantaddress";
                String ZIPCODE = "merchantzipcode";
                String LONGITUDE = "merchantlongitude";
                String LATITUDE = "merchantlatitude";
                String STATE = "merchantstate";
                String CITY = "merchantcity";
                String ENTITY = "companytype";
                String SHOP_NAME = "bussinessName";
                String KYC_TYPE = "kyctype";
            }
        }

    }
}

