package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.PricingRule;
import com.logistics.api.mapper.PricingRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pricing-rules")
public class PricingRuleController {

    @Autowired
    private PricingRuleMapper pricingRuleMapper;

    @GetMapping
    public ResponseEntity<Result<List<PricingRule>>> getAllPricingRules() {
        List<PricingRule> rules = pricingRuleMapper.selectAll();
        return ResponseEntity.ok(Result.success(rules));
    }

    @GetMapping("/goods-type/{goodsTypeId}")
    public ResponseEntity<Result<PricingRule>> getPricingRuleByGoodsTypeId(@PathVariable Integer goodsTypeId) {
        PricingRule rule = pricingRuleMapper.selectByGoodsTypeId(goodsTypeId);
        if (rule == null) {
            return ResponseEntity.status(404).body(Result.error(100001, "未找到对应的价格规则"));
        }
        return ResponseEntity.ok(Result.success(rule));
    }

    @GetMapping("/calculate")
    public ResponseEntity<Result<Map<String, Object>>> calculatePrice(
            @RequestParam Integer goodsTypeId,
            @RequestParam BigDecimal weight) {
        
        PricingRule rule = pricingRuleMapper.selectByGoodsTypeId(goodsTypeId);
        
        if (rule == null) {
            return ResponseEntity.status(404).body(Result.error(100001, "未找到对应的价格规则"));
        }

        BigDecimal baseFee = rule.getBaseFee();
        BigDecimal pricePerKg = rule.getPricePerKg();
        BigDecimal weightFee = weight.multiply(pricePerKg);
        BigDecimal totalAmount = baseFee.add(weightFee);

        Map<String, Object> result = new HashMap<>();
        result.put("goodsTypeId", goodsTypeId);
        result.put("weight", weight);
        result.put("baseFee", baseFee);
        result.put("pricePerKg", pricePerKg);
        result.put("weightFee", weightFee);
        result.put("totalAmount", totalAmount.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("rule", rule);

        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<PricingRule>> getPricingRuleById(@PathVariable Long id) {
        PricingRule rule = pricingRuleMapper.selectById(id);
        if (rule == null) {
            return ResponseEntity.status(404).body(Result.error(100002, "价格规则不存在"));
        }
        return ResponseEntity.ok(Result.success(rule));
    }

    @PostMapping
    public ResponseEntity<Result<PricingRule>> createPricingRule(@RequestBody PricingRule pricingRule) {
        try {
            if (pricingRule.getIsActive() == null) {
                pricingRule.setIsActive(1);
            }
            
            pricingRuleMapper.insert(pricingRule);
            return ResponseEntity.ok(Result.success("创建成功", pricingRule));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(100003, "创建价格规则失败: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<PricingRule>> updatePricingRule(@PathVariable Long id, @RequestBody PricingRule pricingRule) {
        try {
            pricingRule.setId(id);
            pricingRuleMapper.updateById(pricingRule);
            
            PricingRule updated = pricingRuleMapper.selectById(id);
            return ResponseEntity.ok(Result.success("更新成功", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(100003, "更新价格规则失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<String>> deletePricingRule(@PathVariable Long id) {
        try {
            pricingRuleMapper.deleteById(id);
            return ResponseEntity.ok(Result.success("删除成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(100003, "删除价格规则失败: " + e.getMessage()));
        }
    }
}
