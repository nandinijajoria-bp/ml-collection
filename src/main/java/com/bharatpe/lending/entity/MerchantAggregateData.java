package com.bharatpe.lending.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "merchant_aggregate_data_new")
@TypeAlias("MerchantAggregateDataNew")
@ToString(callSuper = true)
public class MerchantAggregateData {

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;

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
