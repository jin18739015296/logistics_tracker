package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.mapper.DistancePricingRuleMapper;
import com.logistics.api.mapper.PricingRuleMapper;
import com.logistics.api.model.DistancePricingRule;
import com.logistics.api.model.PricingRule;
import com.logistics.api.service.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PricingServiceImpl implements PricingService {

    private static final double EARTH_RADIUS = 6371.0;
    private static final BigDecimal DEFAULT_BASE_FEE = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_PRICE_PER_KG = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_PRICE_PER_KM = new BigDecimal("1.50");

    @Autowired
    private PricingRuleMapper pricingRuleMapper;

    @Autowired
    private DistancePricingRuleMapper distancePricingRuleMapper;

    @Override
    public List<PricingRule> getAllRules() {
        return pricingRuleMapper.selectAll();
    }

    @Override
    public PricingRule getRuleById(Long id) {
        return pricingRuleMapper.selectById(id);
    }

    @Override
    public PricingRule createRule(PricingRule rule) {
        if (rule.getIsActive() == null) {
            rule.setIsActive(1);
        }
        pricingRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public PricingRule updateRule(PricingRule rule) {
        pricingRuleMapper.updateById(rule);
        return pricingRuleMapper.selectById(rule.getId());
    }

    @Override
    public void deleteRule(Long id) {
        pricingRuleMapper.deleteById(id);
    }

    @Override
    public List<DistancePricingRule> getDistanceRules() {
        return distancePricingRuleMapper.selectActive();
    }

    @Override
    public Map<String, Object> calculatePrice(Integer goodsTypeId, BigDecimal weight, BigDecimal distance) {
        Map<String, Object> result = new HashMap<>();

        // 重量定价 - 简化版: 基础价 + 每公斤价格
        BigDecimal weightBaseFee = DEFAULT_BASE_FEE;
        BigDecimal pricePerKg = DEFAULT_PRICE_PER_KG;
        PricingRule weightRule = null;

        if (goodsTypeId != null) {
            weightRule = pricingRuleMapper.selectByGoodsTypeId(goodsTypeId);
            if (weightRule != null) {
                weightBaseFee = weightRule.getBaseFee() != null ? weightRule.getBaseFee() : weightBaseFee;
                pricePerKg = weightRule.getPricePerKg() != null ? weightRule.getPricePerKg() : pricePerKg;
            }
        }

        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            weight = BigDecimal.ONE;
        }

        // 计算重量费用: 基础费 + 重量 * 每公斤价格
        BigDecimal weightFee = weight.multiply(pricePerKg);
        BigDecimal weightTotal = weightBaseFee.add(weightFee);

        // 距离定价
        BigDecimal distanceBaseFee = DEFAULT_BASE_FEE;
        BigDecimal pricePerKm = DEFAULT_PRICE_PER_KM;
        DistancePricingRule distanceRule = null;

        if (distance != null && distance.compareTo(BigDecimal.ZERO) > 0) {
            distanceRule = distancePricingRuleMapper.selectByDistance(distance);
            if (distanceRule != null) {
                distanceBaseFee = distanceRule.getBaseFee() != null ? distanceRule.getBaseFee() : distanceBaseFee;
                pricePerKm = distanceRule.getPricePerKm() != null ? distanceRule.getPricePerKm() : pricePerKm;
            }
        } else {
            distance = BigDecimal.ZERO;
        }

        BigDecimal distanceFee = distance.multiply(pricePerKm);
        BigDecimal distanceTotal = distanceBaseFee.add(distanceFee);

        // 总价 = (重量费用 + 距离费用) / 2
        BigDecimal totalAmount = weightTotal.add(distanceTotal)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        result.put("weightBaseFee", weightBaseFee);
        result.put("pricePerKg", pricePerKg);
        result.put("weight", weight);
        result.put("weightFee", weightFee);
        result.put("weightTotal", weightTotal);
        result.put("distanceBaseFee", distanceBaseFee);
        result.put("pricePerKm", pricePerKm);
        result.put("distance", distance);
        result.put("distanceFee", distanceFee);
        result.put("distanceTotal", distanceTotal);
        result.put("totalAmount", totalAmount);
        result.put("weightRule", weightRule);
        result.put("distanceRule", distanceRule);

        return result;
    }

    @Override
    public BigDecimal calculateOrderPrice(Integer goodsTypeId, BigDecimal weight, 
                                          BigDecimal distance, String orderType) {
        Map<String, Object> priceDetail = calculatePrice(goodsTypeId, weight, distance);
        BigDecimal totalAmount = (BigDecimal) priceDetail.get("totalAmount");

        if ("express".equals(orderType)) {
            totalAmount = totalAmount.multiply(new BigDecimal("1.5"));
        } else if ("same_day".equals(orderType)) {
            totalAmount = totalAmount.multiply(new BigDecimal("2.0"));
        }

        return totalAmount.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateDistance(BigDecimal senderLat, BigDecimal senderLng,
                                        BigDecimal receiverLat, BigDecimal receiverLng) {
        if (senderLat == null || senderLng == null || receiverLat == null || receiverLng == null) {
            return BigDecimal.ZERO;
        }

        double lat1 = Math.toRadians(senderLat.doubleValue());
        double lat2 = Math.toRadians(receiverLat.doubleValue());
        double deltaLat = Math.toRadians(receiverLat.doubleValue() - senderLat.doubleValue());
        double deltaLng = Math.toRadians(receiverLng.doubleValue() - senderLng.doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distanceKm = EARTH_RADIUS * c;

        return BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
    }
}
