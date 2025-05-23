package com.bharatpe.lending.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DSMileStoneAchievementResponse {

    public Total total;

    public ArrayList<Achievement> achievement;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Achievement {
        public int milestone_no;
        public ArrayList<Object> active_days_daily;
        public ArrayList<Object> unq_payer_daily;
        public int tpv;
        public int txn_cnt;
        public int unq_payer;
        public int active_days;
        public Date milestone_end_time;
        public Date milestone_start_time;
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Total {
        public int tpv;
        public int txn_cnt;
        public int unq_payer;
        public int active_days;
    }
}




