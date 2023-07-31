package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.lending.common.dao.LendingMerchantPermissionsDao;
import com.bharatpe.lending.common.entity.LendingMerchantPermissions;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.PermissionStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Component
@Slf4j
public class PermissionStageDataService implements IStageDataService<PermissionStateDTO>{

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingMerchantPermissionsDao lendingMerchantPermissionsDao;

    @Override
    public LendingStateDTO<PermissionStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        LendingStateDTO<PermissionStateDTO> lendingStateDTO = fetchScopedData(scopeDataArgs);
        lendingStateDTO.setLendingViewStates(LendingViewStates.OFFER_PAGE);
        return lendingStateDTO;
    }

    @Override
    public LendingStateDTO<PermissionStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        PermissionStateDTO permissionStateDTO = new PermissionStateDTO();
        try {
            permissionStateDTO.setDummyMerchant(easyLoanUtil.isDummyMerchant(scopeDataArgs.getMerchant().getId()));
            LendingMerchantPermissions lendingMerchantPermissions = lendingMerchantPermissionsDao.findByMerchantId(scopeDataArgs.getMerchant().getId());
            if (null != lendingMerchantPermissions){
                permissionStateDTO.setSmsPermissionIsActive(lendingMerchantPermissions.getSmsPermissionActive());
                permissionStateDTO.setLocationPermissionIsActive(lendingMerchantPermissions.getLocationPermissionActive());
                permissionStateDTO.setLocationPermissionDate(lendingMerchantPermissions.getLocationPermissionDate());
            }
            permissionStateDTO.setSuccess(true);
        } catch (Exception e) {
            log.error("error in getting reference stage data for {} : {}, {}", scopeDataArgs.getMerchant().getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            permissionStateDTO.setErrorString(e.getMessage());
        }
        return new LendingStateDTO<>(permissionStateDTO , LendingViewStates.PERMISSIONS_PAGE, LendingViewStates.PERMISSIONS_PAGE);
    }
}
