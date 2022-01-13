package com.bharatpe.lending.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BusinessCategoryResponseDTO {
    List<String> businessCategory;
    Map<String, List<String>> businessSubCategory;
}
