package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.write.LendingRiskVariablesSnapshotWriteDto;

public interface ILendingRiskVariablesSnapshotService {

    ApiResponse<?> searchDynamic(SearchRequestDTO request, boolean useStrongConsistency);

    ApiResponse<Boolean> saveRiskVariablesSnapshot(LendingRiskVariablesSnapshotWriteDto writeDto);
}
