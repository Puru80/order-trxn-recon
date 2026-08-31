package com.projects.ordertrxnrecon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancySummaryDto {
    private String type;
    private String title;
    private int count;
    private BigDecimal totalValue;
    private BigDecimal moneyAtRisk;
    private String severity;
}
