package com.logistics.api.service.impl;

import com.logistics.api.dto.GoodsTypePricingRequest;
import com.logistics.api.dto.GoodsTypeWithPricingDTO;
import com.logistics.api.mapper.GoodsTypeMapper;
import com.logistics.api.mapper.PricingRuleMapper;
import com.logistics.api.model.GoodsType;
import com.logistics.api.model.PricingRule;
import com.logistics.api.service.GoodsTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GoodsTypeServiceImpl implements GoodsTypeService {

    @Autowired
    private GoodsTypeMapper goodsTypeMapper;

    @Autowired
    private PricingRuleMapper pricingRuleMapper;

    @Override
    public List<GoodsType> getAllGoodsTypes() {
        return goodsTypeMapper.selectAll();
    }

    @Override
    public List<GoodsType> getActiveGoodsTypes() {
        return goodsTypeMapper.selectActive();
    }

    @Override
    public GoodsType getGoodsTypeById(Integer id) {
        return goodsTypeMapper.selectById(id);
    }

    @Override
    public GoodsType getGoodsTypeByCode(String code) {
        return goodsTypeMapper.selectByCode(code);
    }

    @Override
    public GoodsType createGoodsType(GoodsType goodsType) {
        GoodsType existing = goodsTypeMapper.selectByCode(goodsType.getCode());
        if (existing != null) {
            throw new RuntimeException("物品类型编码已存在");
        }
        
        goodsType.setIsActive(1);
        goodsTypeMapper.insert(goodsType);
        log.info("创建物品类型: id={}, name={}", goodsType.getId(), goodsType.getName());
        return goodsType;
    }

    @Override
    public GoodsType updateGoodsType(GoodsType goodsType) {
        GoodsType existing = goodsTypeMapper.selectById(goodsType.getId());
        if (existing == null) {
            throw new RuntimeException("物品类型不存在");
        }
        
        goodsTypeMapper.updateById(goodsType);
        log.info("更新物品类型: id={}, name={}", goodsType.getId(), goodsType.getName());
        return goodsType;
    }

    @Override
    public void deleteGoodsType(Integer id) {
        GoodsType existing = goodsTypeMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("物品类型不存在");
        }
        
        goodsTypeMapper.deleteById(id);
        log.info("删除物品类型: id={}", id);
    }

    @Override
    public void toggleGoodsTypeStatus(Integer id, boolean active) {
        GoodsType existing = goodsTypeMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("物品类型不存在");
        }
        
        goodsTypeMapper.updateStatus(id, active ? 1 : 0);
        log.info("更新物品类型状态: id={}, active={}", id, active);
    }

    // ==================== 物品类型与价格组合操作 ====================

    @Override
    public List<GoodsTypeWithPricingDTO> getAllGoodsTypesWithPricing() {
        List<GoodsType> goodsTypes = goodsTypeMapper.selectAll();
        List<GoodsTypeWithPricingDTO> result = new ArrayList<>();
        
        for (GoodsType goodsType : goodsTypes) {
            GoodsTypeWithPricingDTO dto = convertToDTO(goodsType);
            result.add(dto);
        }
        
        return result;
    }

    @Override
    public GoodsTypeWithPricingDTO getGoodsTypeWithPricingById(Integer id) {
        GoodsType goodsType = goodsTypeMapper.selectById(id);
        if (goodsType == null) {
            return null;
        }
        return convertToDTO(goodsType);
    }

    @Override
    @Transactional
    public GoodsTypeWithPricingDTO createGoodsTypeWithPricing(GoodsTypePricingRequest request) {
        // 1. 检查编码是否已存在
        GoodsType existing = goodsTypeMapper.selectByCode(request.getCode());
        if (existing != null) {
            throw new RuntimeException("物品类型编码已存在");
        }
        
        // 2. 创建物品类型
        GoodsType goodsType = new GoodsType();
        goodsType.setCode(request.getCode());
        goodsType.setName(request.getName());
        goodsType.setDescription(request.getDescription());
        goodsType.setIcon(request.getIcon());
        goodsType.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        goodsType.setIsActive(request.getIsActive() != null ? request.getIsActive() : 1);
        
        goodsTypeMapper.insert(goodsType);
        log.info("创建物品类型: id={}, name={}", goodsType.getId(), goodsType.getName());
        
        // 3. 创建价格规则（如果提供了价格信息）
        if (request.getBaseFee() != null && request.getPricePerKg() != null) {
            PricingRule pricingRule = new PricingRule();
            pricingRule.setGoodsTypeId(goodsType.getId());
            pricingRule.setBaseFee(request.getBaseFee());
            pricingRule.setPricePerKg(request.getPricePerKg());
            pricingRule.setIsActive(request.getPricingIsActive() != null ? request.getPricingIsActive() : 1);
            
            pricingRuleMapper.insert(pricingRule);
            log.info("创建价格规则: id={}, goodsTypeId={}, baseFee={}, pricePerKg={}", 
                    pricingRule.getId(), goodsType.getId(), request.getBaseFee(), request.getPricePerKg());
        }
        
        return getGoodsTypeWithPricingById(goodsType.getId());
    }

    @Override
    @Transactional
    public GoodsTypeWithPricingDTO updateGoodsTypeWithPricing(GoodsTypePricingRequest request) {
        if (request.getId() == null) {
            throw new RuntimeException("物品类型ID不能为空");
        }
        
        // 1. 检查物品类型是否存在
        GoodsType existing = goodsTypeMapper.selectById(request.getId());
        if (existing == null) {
            throw new RuntimeException("物品类型不存在");
        }
        
        // 2. 更新物品类型
        GoodsType goodsType = new GoodsType();
        goodsType.setId(request.getId());
        goodsType.setCode(request.getCode());
        goodsType.setName(request.getName());
        goodsType.setDescription(request.getDescription());
        goodsType.setIcon(request.getIcon());
        goodsType.setSortOrder(request.getSortOrder());
        goodsType.setIsActive(request.getIsActive());
        
        goodsTypeMapper.updateById(goodsType);
        log.info("更新物品类型: id={}, name={}", goodsType.getId(), goodsType.getName());
        
        // 3. 更新或创建价格规则
        if (request.getBaseFee() != null && request.getPricePerKg() != null) {
            PricingRule existingRule = pricingRuleMapper.selectByGoodsTypeId(request.getId());
            
            if (existingRule != null) {
                // 更新现有规则
                existingRule.setBaseFee(request.getBaseFee());
                existingRule.setPricePerKg(request.getPricePerKg());
                if (request.getPricingIsActive() != null) {
                    existingRule.setIsActive(request.getPricingIsActive());
                }
                pricingRuleMapper.updateById(existingRule);
                log.info("更新价格规则: id={}, baseFee={}, pricePerKg={}", 
                        existingRule.getId(), request.getBaseFee(), request.getPricePerKg());
            } else {
                // 创建新规则
                PricingRule pricingRule = new PricingRule();
                pricingRule.setGoodsTypeId(request.getId());
                pricingRule.setBaseFee(request.getBaseFee());
                pricingRule.setPricePerKg(request.getPricePerKg());
                pricingRule.setIsActive(request.getPricingIsActive() != null ? request.getPricingIsActive() : 1);
                
                pricingRuleMapper.insert(pricingRule);
                log.info("创建价格规则: id={}, goodsTypeId={}, baseFee={}, pricePerKg={}", 
                        pricingRule.getId(), request.getId(), request.getBaseFee(), request.getPricePerKg());
            }
        }
        
        return getGoodsTypeWithPricingById(request.getId());
    }

    @Override
    @Transactional
    public void deleteGoodsTypeWithPricing(Integer id) {
        // 1. 检查物品类型是否存在
        GoodsType existing = goodsTypeMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("物品类型不存在");
        }
        
        // 2. 删除关联的价格规则
        PricingRule pricingRule = pricingRuleMapper.selectByGoodsTypeId(id);
        if (pricingRule != null) {
            pricingRuleMapper.deleteById(pricingRule.getId());
            log.info("删除价格规则: id={}", pricingRule.getId());
        }
        
        // 3. 删除物品类型
        goodsTypeMapper.deleteById(id);
        log.info("删除物品类型: id={}", id);
    }

    /**
     * 将GoodsType转换为GoodsTypeWithPricingDTO
     */
    private GoodsTypeWithPricingDTO convertToDTO(GoodsType goodsType) {
        GoodsTypeWithPricingDTO dto = new GoodsTypeWithPricingDTO();
        dto.setId(goodsType.getId());
        dto.setCode(goodsType.getCode());
        dto.setName(goodsType.getName());
        dto.setDescription(goodsType.getDescription());
        dto.setIcon(goodsType.getIcon());
        dto.setSortOrder(goodsType.getSortOrder());
        dto.setIsActive(goodsType.getIsActive());
        dto.setCreateTime(goodsType.getCreateTime());
        
        // 查询价格规则
        PricingRule pricingRule = pricingRuleMapper.selectByGoodsTypeId(goodsType.getId());
        if (pricingRule != null) {
            dto.setPricingRuleId(pricingRule.getId());
            dto.setBaseFee(pricingRule.getBaseFee());
            dto.setPricePerKg(pricingRule.getPricePerKg());
            dto.setPricingIsActive(pricingRule.getIsActive());
            dto.setPricingUpdateTime(pricingRule.getUpdateTime());
        }
        
        return dto;
    }
}
