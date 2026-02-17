package com.bharatpe.lending.dto.underwriting;

import com.bharatpe.lending.dto.underwriting.write.LendingRiskVariablesSnapshotWriteDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LendingRiskVariablesSnapshotRequestWrapper {
    private LendingRiskVariablesSnapshotWriteDto lendingRiskVariablesSnapshotWriteDto;
    private SearchRequestDTO searchRequestDTO;
}
