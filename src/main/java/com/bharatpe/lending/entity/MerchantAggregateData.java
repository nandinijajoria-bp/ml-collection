package com.bharatpe.lending.entity;

import lombok.*;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "merchant_aggregate_data_new")
@TypeAlias("MerchantAggregateDataNew")
@ToString(callSuper = true)
public class MerchantAggregateData {

    @Field("application_type")
    private String applicationType;

    @Field("merchant_id")
    private long merchantId;

    @Field("sources")
    private String sources;

    @Field("scienaptic_properties")
    private String scienapticProperties;

    @Field("aggregate_id")
    private String aggregateId;
}
