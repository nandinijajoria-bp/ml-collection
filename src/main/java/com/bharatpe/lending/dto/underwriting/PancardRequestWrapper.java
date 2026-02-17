package com.bharatpe.lending.dto.underwriting;

import com.bharatpe.lending.dto.underwriting.write.LendingPancardWriteDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PancardRequestWrapper {
    private LendingPancardWriteDto lendingPancardWriteDto;
    private SearchRequestDTO searchRequestDTO;
}

