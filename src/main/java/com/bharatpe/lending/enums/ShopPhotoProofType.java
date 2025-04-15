package com.bharatpe.lending.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ShopPhotoProofType {
    FRONT("shop-front"),
    STOCK("shop-stock");
    private final String value;
}
