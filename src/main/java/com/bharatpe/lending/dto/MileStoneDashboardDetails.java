package com.bharatpe.lending.dto;


import lombok.Data;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

@Data
public class MileStoneDashboardDetails {

    private List<MileStoneDashboardData> mapList;
    private Date mileStoneCreatedAt;
    private int achievementUniquePayer;
    private int achievementActiveDays;
    private int targetUniquePayer;
    private int targetActiveDays;

}
