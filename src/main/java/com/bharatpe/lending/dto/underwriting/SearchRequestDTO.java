package com.bharatpe.lending.dto.underwriting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchRequestDTO {
    private List<SearchCriteriaDTO> criteriaList;
    private Integer limit;
    private String orderBy;
    private String orderDir;

    // New field for aggregate queries
    private List<AggregateConditionDTO> aggregateConditions;
}
