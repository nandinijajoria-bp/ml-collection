package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.dao.LenderAssignmentRulesDao;
import com.bharatpe.lending.dao.LenderDisbursalLimitsDao;
import com.bharatpe.lending.entity.LenderAssignmentRules;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.ExtractedRulesAndLendersDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Component
@Slf4j
public class AssignmentRuleUtils {

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LenderAssignmentRulesDao lenderAssignmentRulesDao;

    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;

    public ExtractedRulesAndLendersDTO extractRules(Long merchantId, Map<String, String> args) {
        ExtractedRulesAndLendersDTO extractedRulesAndLendersDTO = new ExtractedRulesAndLendersDTO();
        Boolean extractModelOnly = Boolean.TRUE;
        ExtractedRulesAndLendersDTO.RiskParamsDTO riskParamsDTO = getRiskParams(merchantId);
        extractedRulesAndLendersDTO.setRiskParamsDTO(riskParamsDTO);
        extractedRulesAndLendersDTO.setRunModelAssignmentOnly(extractModelOnly);
        extractedRulesAndLendersDTO.setMerchantId(merchantId);
        Integer tenureInMonths = null;
        Double amount = null;
        if (null != args) {
            tenureInMonths = Integer.valueOf(args.get("tenureInMonths"));
            amount = Double.parseDouble(args.get("loanAmount"));
            extractModelOnly = Boolean.parseBoolean(args.get("extractModelOnly"));
            riskParamsDTO.setAmount(amount);
            String tenure = "%" + tenureInMonths + "%";
            riskParamsDTO.setTenureInMonths(tenure);
            extractedRulesAndLendersDTO.setRunModelAssignmentOnly(extractModelOnly);
        }
        try {
            log.info("Lender assignment parameters -> bureau:{}, loanType:{}, tenure:{}, loanAmount:{}, riskGroup:{}, pincodeColor:{}", riskParamsDTO.getBureauScore(), riskParamsDTO.getRiskSegment(), riskParamsDTO.getTenureInMonths(),
                    riskParamsDTO.getAmount(), riskParamsDTO.getRiskGroupLike(), riskParamsDTO.getPincodeColor());
            List<LenderAssignmentRules> derivedRuleList = lenderAssignmentRulesDao.fetchEligibleRules(riskParamsDTO.getAmount(), riskParamsDTO.getBureauScore(), riskParamsDTO.getRiskSegment(), riskParamsDTO.getTenureInMonths(), riskParamsDTO.getRiskGroupLike(), riskParamsDTO.getPincodeColor());
            log.info("fetched rule list {}", derivedRuleList);
            // TODO: 14/04/23 disable later
            derivedRuleList = lenderAssignmentRulesDao.fetchEligibleRulesForInternal(riskParamsDTO.getAmount(), riskParamsDTO.getBureauScore(), riskParamsDTO.getRiskSegment(), riskParamsDTO.getTenureInMonths(), riskParamsDTO.getRiskGroupLike(), riskParamsDTO.getPincodeColor());
            log.info("tweaked rule list {}", derivedRuleList);
            extractedRulesAndLendersDTO.setLenderAssignmentRules(Optional.ofNullable(derivedRuleList).orElse(new ArrayList<>()));
        } catch (Exception ex) {
            log.error("Exception occurred while fetching rules : {}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return extractedRulesAndLendersDTO;
    }

    public ExtractedRulesAndLendersDTO.RiskParamsDTO getRiskParams(Long merchantId) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
        return ExtractedRulesAndLendersDTO.RiskParamsDTO.builder().bureauScore(Objects.nonNull(lendingRiskVariables.getBureauScore()) ? lendingRiskVariables.getBureauScore() : 0D)
                .riskSegment(Objects.nonNull(lendingRiskVariables.getRiskSegment()) ? "%" + lendingRiskVariables.getRiskSegment() + "%" : "")
                .riskGroupLike(Objects.nonNull(lendingRiskVariables.getRiskGroup()) ? "%" + lendingRiskVariables.getRiskGroup() + "%" : "")
                .pincodeColor(Objects.nonNull(lendingRiskVariables.getPincodeColor()) ? "%" + lendingRiskVariables.getPincodeColor().name() + "%" : "")
                .isGstOffer(Objects.nonNull(lendingRiskVariables.getGstAffectedOffer()) ? lendingRiskVariables.getGstAffectedOffer() : Boolean.FALSE)
                .amount(Objects.nonNull(lendingRiskVariables.getFinalOffer()) ? lendingRiskVariables.getFinalOffer() : 0D)
                .tenureInMonths("%" + (Objects.nonNull(lendingRiskVariables.getTenure()) ? lendingRiskVariables.getTenure() : 0) + "%")
                .build();
    }

    public Set<EdiModel> extractEdiModelFromFilteredLenders(ExtractedRulesAndLendersDTO extractedRulesAndLendersDTO) {
        Set<EdiModel> uniqueEdiModels = new LinkedHashSet<>();
        extractedRulesAndLendersDTO.getFilteredLenders().forEach(lender -> uniqueEdiModels.add(LenderOffDays.valueOf(lender).getEdiModel()));
        return uniqueEdiModels;
    }

    public void filterLenders(ExtractedRulesAndLendersDTO extractedRulesAndLendersDTO) {
        List<String> eligibleLenders = new ArrayList<>();
        extractedRulesAndLendersDTO.getLenderAssignmentRules().forEach(rules -> eligibleLenders.add(rules.getLender()));
        extractedRulesAndLendersDTO.setFilteredLenders(eligibleLenders);
        filterByDisbursalLimits(extractedRulesAndLendersDTO);
        // run further refined checks if needed
    }

    public String fetchFallbackLender() {
        LendingLenderQuota fallbackLender = lenderDisbursalLimitsDao.findByEdiModelIsNull();
        return ObjectUtils.isEmpty(fallbackLender) ? Lender.LDC.name() : fallbackLender.getLender();
    }

    public void filterByDisbursalLimits(ExtractedRulesAndLendersDTO extractedRulesAndLendersDTO) {
        List<String> filteredLenders = new ArrayList<>();
        List<LendingLenderQuota> lendingLenderQuotas = new ArrayList<>();
        if(!ObjectUtils.isEmpty(extractedRulesAndLendersDTO.getFilteredLenders())){
            lendingLenderQuotas = lenderDisbursalLimitsDao.fetchEligibleLenderLimits(extractedRulesAndLendersDTO.getFilteredLenders(), extractedRulesAndLendersDTO.getRiskParamsDTO().getAmount());
        }
        lendingLenderQuotas.forEach(lendingLenderQuota -> filteredLenders.add(lendingLenderQuota.getLender()));
        extractedRulesAndLendersDTO.setFilteredLenders(filteredLenders);
    }
}
