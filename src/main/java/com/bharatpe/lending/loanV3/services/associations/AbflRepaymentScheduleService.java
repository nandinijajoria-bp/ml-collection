package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.AbflTopupRpsRequestDTO;
import com.bharatpe.lending.loanV3.dto.AbflTopupRpsResponseDTO;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AbflRepaymentScheduleService implements ILenderAssociationService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Override
    public AbflTopupRpsResponseDTO invoke(Long applicationId, Map args) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                log.info("no lending application record found for {}", applicationId);
                return null;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, Status.ACTIVE.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("no lender assc record found for {}", applicationId);
                return null;
            }
            AbflTopupRpsRequestDTO rpsRequestDTO = createPayload(lendingApplication.get().getId());
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(rpsRequestDTO.getLender());
            AbflTopupRpsResponseDTO responseDTO = apiGatewayV3.fetchRepaymentSchedule(rpsRequestDTO);
            if (ObjectUtils.isEmpty(responseDTO)) {
                log.info("error while fetching repayment schedule for applicationId {}", applicationId);
            }
            return responseDTO;
        } catch (Exception e) {
            log.info("error while fetching repayment schedule for applicationId {} {} {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return null;
        }
    }

    private AbflTopupRpsRequestDTO createPayload(Long applicationId) {
        return AbflTopupRpsRequestDTO.builder()
                .applicationId(applicationId)
                .productName("LENDING")
                .lender("ABFL")
                .topup(true)
                .payload(AbflTopupRpsRequestDTO.Payload.builder().applicationId(applicationId.toString()).build())
                .build();
    }

}
