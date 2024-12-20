package com.bharatpe.lending.loanV3.revamp.util;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

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

    public static String addDays(Date date, int daysCount)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, daysCount);
        return new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
    }

    public static Date addDaysWithTime(Date date, int daysCount)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, daysCount);
        return calendar.getTime();
    }
    public static long calculateAgeOfMerchantInDays(Date birthDate,Date currentDate)
    {
        long age = 0;
        if ((birthDate != null) && (currentDate != null)) {
            age = ChronoUnit.DAYS.between(
                    birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                    currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
        return age;
    }

    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date1);
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }








}
