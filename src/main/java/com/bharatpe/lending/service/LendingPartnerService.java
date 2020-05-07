package com.bharatpe.lending.service;

import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.lending.common.dao.LendingPartnerDetailsDao;
import com.bharatpe.lending.common.entity.LendingPartnerDetails;
import com.bharatpe.lending.dto.PartnerDetailsRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LendingPartnerService {

    Logger logger = LoggerFactory.getLogger(LendingPartnerService.class);

    @Autowired
    LendingPartnerDetailsDao lendingPartnerDetailsDao;

    @Autowired
    SmsServiceHandler smsServiceHandler;

    public void saveDetails(PartnerDetailsRequestDTO partnerDetailsRequestDTO) {
        List<LendingPartnerDetails> lendingPartnerDetails = lendingPartnerDetailsDao.findByUserMobileAndPartner(partnerDetailsRequestDTO.getMobile(), partnerDetailsRequestDTO.getPartner());
        if (lendingPartnerDetails == null || lendingPartnerDetails.isEmpty()) {
            String smsContent = "Thank you for registering for Zomato Loan. BharatPe needs your restaurant info:\n- Name\n- Address\n- Email\n\nWe'll call you in next 24 hours.";
            smsServiceHandler.sendSMS(new ArrayList<String>(){{add(partnerDetailsRequestDTO.getMobile());}}, smsContent, NotificationProvider.SMS.GUPSHUP);
        }
        lendingPartnerDetailsDao.save(new LendingPartnerDetails(partnerDetailsRequestDTO.getPartner(), partnerDetailsRequestDTO.getName(), partnerDetailsRequestDTO.getMobile()));
        logger.info("Successfully saved partner details for mobile: {}", partnerDetailsRequestDTO.getMobile());
    }
}
