package com.bharatpe.lending.dto;


import lombok.Data;

@Data
public class Target {

    public int milestone_no;

    public String reward;

    public int duration;
    public double total_tpv;
    public int unq_payer;
    public int active_days;
    public int no_txn;
    public int per_txn_value;
}
