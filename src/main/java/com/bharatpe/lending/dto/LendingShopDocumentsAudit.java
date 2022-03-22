package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.entity.LendingShopDocuments;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LendingShopDocumentsAudit {
    LendingShopDocuments lendingShopDocuments;
    Boolean resubmittedDoc;
}
