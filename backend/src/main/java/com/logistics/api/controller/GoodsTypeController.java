package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.GoodsType;
import com.logistics.api.service.GoodsTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/goods-types")
public class GoodsTypeController {

    @Autowired
    private GoodsTypeService goodsTypeService;

    @GetMapping
    public ResponseEntity<Result<List<GoodsType>>> getAllGoodsTypes() {
        List<GoodsType> goodsTypes = goodsTypeService.getAllGoodsTypes();
        return ResponseEntity.ok(Result.success(goodsTypes));
    }

    @GetMapping("/active")
    public ResponseEntity<Result<List<GoodsType>>> getActiveGoodsTypes() {
        List<GoodsType> goodsTypes = goodsTypeService.getActiveGoodsTypes();
        return ResponseEntity.ok(Result.success(goodsTypes));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<GoodsType>> getGoodsTypeById(@PathVariable Integer id) {
        GoodsType goodsType = goodsTypeService.getGoodsTypeById(id);
        if (goodsType == null) {
            return ResponseEntity.status(404).body(Result.error(90001, "物品类型不存在"));
        }
        return ResponseEntity.ok(Result.success(goodsType));
    }

    @PostMapping
    public ResponseEntity<Result<GoodsType>> createGoodsType(@RequestBody GoodsType goodsType) {
        try {
            GoodsType created = goodsTypeService.createGoodsType(goodsType);
            return ResponseEntity.ok(Result.success("创建成功", created));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(90002, e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<GoodsType>> updateGoodsType(@PathVariable Integer id, @RequestBody GoodsType goodsType) {
        try {
            goodsType.setId(id);
            GoodsType updated = goodsTypeService.updateGoodsType(goodsType);
            return ResponseEntity.ok(Result.success("更新成功", updated));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(90002, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<String>> deleteGoodsType(@PathVariable Integer id) {
        try {
            goodsTypeService.deleteGoodsType(id);
            return ResponseEntity.ok(Result.success("删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(90002, e.getMessage()));
        }
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<Result<String>> enableGoodsType(@PathVariable Integer id) {
        try {
            goodsTypeService.toggleGoodsTypeStatus(id, true);
            return ResponseEntity.ok(Result.success("启用成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(90002, e.getMessage()));
        }
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<Result<String>> disableGoodsType(@PathVariable Integer id) {
        try {
            goodsTypeService.toggleGoodsTypeStatus(id, false);
            return ResponseEntity.ok(Result.success("禁用成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(90002, e.getMessage()));
        }
    }
}
