package com.bharatpe.lending.loanV3.revamp.util;

import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Slf4j
public class DateUtils {

    private static SimpleDateFormat DD_MMM_YY = new SimpleDateFormat("dd MMM yy");
    private static SimpleDateFormat HH_mm_a = new SimpleDateFormat("HH:mm a");
    private static final SimpleDateFormat YYYY_MM_DD_HH_mm_ss_S = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";


    /*
    convert date object in format "yyyy-MM-dd HH:mm" to desired result
     */
    public static String formatDate_DD_MMM_YY_HH_mm_a(Date inputDate){
            return DD_MMM_YY.format(inputDate);
    }

    public static String formatDateTime_DYYYY_MM_DD_HH_mm_ss_S(Date inputDate){
        if(inputDate == null){
            return null;
        }
        return YYYY_MM_DD_HH_mm_ss_S.format(inputDate);
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
    public static long calculateAgeInDays(Date birthDate, Date currentDate)
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

    public static boolean isSameYear(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date1);
        int date1Year = calendar.get(Calendar.YEAR);
        calendar.setTime(date2);
        int date2Year = calendar.get(Calendar.YEAR);
        log.info("date1Year {} - date2Year {}", date1Year, date2Year);
        return (date1Year == date2Year);
    }
    public static int getAge(Date dob){
        if(dob == null){
            return 0;
        }
        return Period.between(convertToLocalDate(dob), LocalDate.now()).getYears();
    }

    private static LocalDate convertToLocalDate(@NotNull Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    public static Date parseDob(String dob){
        List<String> dateFormats = Arrays.asList("dd/MM/yyyy","dd-MM-yyyy");
        for(String dateFormat : dateFormats){
            try{
                return new SimpleDateFormat(dateFormat).parse(dob);
            }
            catch(ParseException e){
                log.info("Failed to parse dob: {}, with format : {}", dob, dateFormat);
            }
        }
        return null;
    }

    public static Date parseDate(String stringDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS);
            return sdf.parse(stringDate);
        } catch (Exception e) {
            log.info("Exception occurred while parsing date for string : {}", stringDate);
        }
        return null;
    }
}
