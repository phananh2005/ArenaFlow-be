package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.CostCustomItemRequest;

import java.util.List;

/**
 * Các field của một thoả thuận chi phí (kèo luồng A, thương lượng luồng B) để hai request dùng chung một validator
 * quan hệ {@code costType} ↔ số tiền ↔ {@code costSplit}.
 */
public interface CostAgreementFields {

    String costType();

    String costSplit();

    Long venueAmount();

    Long drinksAmount();

    List<CostCustomItemRequest> customItems();
}
