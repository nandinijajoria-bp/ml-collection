package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Target {

    public int milestone_no;
    public int cashback;
    public String reward;

    public int duration;
    public double total_tpv;
    public int unq_payer;
    public int active_days;
    public int no_txn;
    public int per_txn_value;
}
