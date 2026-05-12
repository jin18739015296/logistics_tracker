package com.logistics.api.service;

import com.logistics.api.dto.GoodsTypePricingRequest;
import com.logistics.api.dto.GoodsTypeWithPricingDTO;
import com.logistics.api.model.GoodsType;

import java.util.List;

/**
 * 物品类型服务接口
 */
public interface GoodsTypeService {

    /**
     * 获取所有物品类型
     */
    List<GoodsType> getAllGoodsTypes();

    /**
     * 获取启用的物品类型
     */
    List<GoodsType> getActiveGoodsTypes();

    /**
     * 根据ID获取物品类型
     */
    GoodsType getGoodsTypeById(Integer id);

    /**
     * 根据编码获取物品类型
     */
    GoodsType getGoodsTypeByCode(String code);

    /**
     * 创建物品类型
     */
    GoodsType createGoodsType(GoodsType goodsType);

    /**
     * 更新物品类型
     */
    GoodsType updateGoodsType(GoodsType goodsType);

    /**
     * 删除物品类型
     */
    void deleteGoodsType(Integer id);

    /**
     * 启用/禁用物品类型
     */
    void toggleGoodsTypeStatus(Integer id, boolean active);

    // ==================== 物品类型与价格组合操作 ====================

    /**
     * 获取所有物品类型及其价格规则
     */
    List<GoodsTypeWithPricingDTO> getAllGoodsTypesWithPricing();

    /**
     * 根据ID获取物品类型及其价格规则
     */
    GoodsTypeWithPricingDTO getGoodsTypeWithPricingById(Integer id);

    /**
     * 创建物品类型并设置价格规则
     */
    GoodsTypeWithPricingDTO createGoodsTypeWithPricing(GoodsTypePricingRequest request);

    /**
     * 更新物品类型及其价格规则
     */
    GoodsTypeWithPricingDTO updateGoodsTypeWithPricing(GoodsTypePricingRequest request);

    /**
     * 删除物品类型及其价格规则
     */
    void deleteGoodsTypeWithPricing(Integer id);
}
