package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.lending.common.enums.EdiModel;
import org.springframework.stereotype.Component;

@Component
public class OfferUtils {

    public static int getEdiDays(int tenure, EdiModel ediModel) {
        switch (tenure) {
            case 0:
                return 0;
            case 1:
                return ediModel.getNoOfEdiDaysInAWeek() == 6 ? 26 : 30;
            case 2:
                return ediModel.getNoOfEdiDaysInAWeek() == 6 ? 51: 60;
            case 3:
                return ediModel.getNoOfEdiDaysInAWeek() == 6 ? 77: 90;
            case 6:
                return ediModel.getNoOfEdiDaysInAWeek() == 6 ? 155: 180;
            case 9:
                return ediModel.getNoOfEdiDaysInAWeek() == 6 ? 234: 270;
            case 12:
                return ediModel.getNoOfEdiDaysInAWeek() == 6 ? 311: 360;
            case 15:
                return ediModel.getNoOfEdiDaysInAWeek() == 6 ? 388 : 450;//15 months
            default:
                throw new RuntimeException("Edi days not added for given tenure");
        }
    }
}
