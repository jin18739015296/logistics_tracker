package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.model.DistancePricingRule;
import com.logistics.api.model.PricingRule;
import com.logistics.api.service.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/pricing")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminPricingController {

    @Autowired
    private PricingService pricingService;

    @GetMapping("/rules")
    public ResponseEntity<Result<List<PricingRule>>> getAllRules() {
        List<PricingRule> rules = pricingService.getAllRules();
        return ResponseEntity.ok(Result.success(rules));
    }



    @PostMapping("/rules")
    public ResponseEntity<Result<PricingRule>> createRule(@RequestBody PricingRule rule) {
        try {
            // 检查该物品类型是否已存在价格规则
            List<PricingRule> existingRules = pricingService.getAllRules();
            boolean exists = existingRules.stream()
                    .anyMatch(r -> r.getGoodsTypeId().equals(rule.getGoodsTypeId()));
            if (exists) {
                return ResponseEntity.badRequest()
                        .body(Result.error(100010, "该物品类型已存在价格规则，请勿重复创建"));
            }
            
            PricingRule created = pricingService.createRule(rule);
            return ResponseEntity.ok(Result.success("规则创建成功", created));
        } catch (Exception e) {
            log.error("创建规则失败", e);
            return ResponseEntity.badRequest().body(Result.error(100002, e.getMessage()));
        }
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<Result<PricingRule>> updateRule(
            @PathVariable Long id,
            @RequestBody PricingRule rule) {
        try {
            rule.setId(id);
            PricingRule updated = pricingService.updateRule(rule);
            return ResponseEntity.ok(Result.success("规则更新成功", updated));
        } catch (Exception e) {
            log.error("更新规则失败: id={}", id, e);
            return ResponseEntity.badRequest().body(Result.error(100003, e.getMessage()));
        }
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Result<String>> deleteRule(@PathVariable Long id) {
        try {
            pricingService.deleteRule(id);
            return ResponseEntity.ok(Result.success("规则删除成功"));
        } catch (Exception e) {
            log.error("删除规则失败: id={}", id, e);
            return ResponseEntity.badRequest().body(Result.error(100004, e.getMessage()));
        }
    }


}
