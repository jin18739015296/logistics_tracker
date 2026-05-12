package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.dto.GoodsTypePricingRequest;
import com.logistics.api.dto.GoodsTypeWithPricingDTO;
import com.logistics.api.service.GoodsTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员物品类型管理Controller（包含价格规则）
 */
@Slf4j
@RestController
@RequestMapping("/admin/goods-types")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminGoodsTypeController {

    @Autowired
    private GoodsTypeService goodsTypeService;

    /**
     * 获取所有物品类型及其价格规则
     */
    @GetMapping
    public ResponseEntity<Result<List<GoodsTypeWithPricingDTO>>> getAllGoodsTypesWithPricing() {
        List<GoodsTypeWithPricingDTO> list = goodsTypeService.getAllGoodsTypesWithPricing();
        return ResponseEntity.ok(Result.success(list));
    }

    /**
     * 根据ID获取物品类型及其价格规则
     */
    @GetMapping("/{id}")
    public ResponseEntity<Result<GoodsTypeWithPricingDTO>> getGoodsTypeWithPricingById(@PathVariable Integer id) {
        GoodsTypeWithPricingDTO dto = goodsTypeService.getGoodsTypeWithPricingById(id);
        if (dto == null) {
            return ResponseEntity.status(404).body(Result.error(100001, "物品类型不存在"));
        }
        return ResponseEntity.ok(Result.success(dto));
    }

    /**
     * 创建物品类型并设置价格规则
     */
    @PostMapping
    public ResponseEntity<Result<GoodsTypeWithPricingDTO>> createGoodsTypeWithPricing(
            @RequestBody GoodsTypePricingRequest request) {
        try {
            GoodsTypeWithPricingDTO created = goodsTypeService.createGoodsTypeWithPricing(request);
            return ResponseEntity.ok(Result.success("创建成功", created));
        } catch (RuntimeException e) {
            log.error("创建物品类型失败", e);
            return ResponseEntity.badRequest().body(Result.error(100002, e.getMessage()));
        }
    }

    /**
     * 更新物品类型及其价格规则
     */
    @PutMapping("/{id}")
    public ResponseEntity<Result<GoodsTypeWithPricingDTO>> updateGoodsTypeWithPricing(
            @PathVariable Integer id,
            @RequestBody GoodsTypePricingRequest request) {
        try {
            request.setId(id);
            GoodsTypeWithPricingDTO updated = goodsTypeService.updateGoodsTypeWithPricing(request);
            return ResponseEntity.ok(Result.success("更新成功", updated));
        } catch (RuntimeException e) {
            log.error("更新物品类型失败: id={}", id, e);
            return ResponseEntity.badRequest().body(Result.error(100003, e.getMessage()));
        }
    }

    /**
     * 删除物品类型及其价格规则
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<String>> deleteGoodsTypeWithPricing(@PathVariable Integer id) {
        try {
            goodsTypeService.deleteGoodsTypeWithPricing(id);
            return ResponseEntity.ok(Result.success("删除成功"));
        } catch (RuntimeException e) {
            log.error("删除物品类型失败: id={}", id, e);
            return ResponseEntity.badRequest().body(Result.error(100004, e.getMessage()));
        }
    }
}
