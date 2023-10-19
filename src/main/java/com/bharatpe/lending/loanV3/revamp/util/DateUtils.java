package com.bharatpe.lending.loanV3.revamp.util;

import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDateTime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Slf4j
public class DateUtils {

    private static SimpleDateFormat DD_MMM_YY = new SimpleDateFormat("dd MMM yy");
    private static SimpleDateFormat HH_mm_a = new SimpleDateFormat("HH:mm a");



    /*
    convert date object in format "yyyy-MM-dd HH:mm" to desired result
     */
    public static String formatDate_DD_MMM_YY_HH_mm_a(Date inputDate){
            return DD_MMM_YY.format(inputDate);
    }

    public static String formatDate_HH_mm_a(Date inputDate){
        return HH_mm_a.format(inputDate);
    }






}
