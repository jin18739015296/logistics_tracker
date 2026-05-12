package com.logistics.api.service;

import com.logistics.api.model.DistancePricingRule;
import com.logistics.api.model.PricingRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface PricingService {

    List<PricingRule> getAllRules();

    PricingRule getRuleById(Long id);

    PricingRule createRule(PricingRule rule);

    PricingRule updateRule(PricingRule rule);

    void deleteRule(Long id);

    List<DistancePricingRule> getDistanceRules();

    Map<String, Object> calculatePrice(Integer goodsTypeId, BigDecimal weight, BigDecimal distance);

    BigDecimal calculateOrderPrice(Integer goodsTypeId, BigDecimal weight, BigDecimal distance, String orderType);

    BigDecimal calculateDistance(BigDecimal senderLat, BigDecimal senderLng, 
                                 BigDecimal receiverLat, BigDecimal receiverLng);
}
