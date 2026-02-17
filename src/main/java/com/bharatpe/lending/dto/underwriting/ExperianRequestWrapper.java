package com.bharatpe.lending.dto.underwriting;

import com.bharatpe.lending.dto.underwriting.write.ExperianWriteDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExperianRequestWrapper {
    private ExperianWriteDto experianWriteDto;
    private SearchRequestDTO searchRequestDTO;
}
