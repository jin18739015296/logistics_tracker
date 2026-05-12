package com.logistics.api.mapper;

import com.logistics.api.model.PricingRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PricingRuleMapper {

    @Select("SELECT * FROM pricing_rules WHERE id = #{id}")
    PricingRule selectById(Long id);

    @Select("SELECT * FROM pricing_rules")
    List<PricingRule> selectAll();

    @Select("SELECT * FROM pricing_rules WHERE goods_type_id = #{goodsTypeId} AND is_active = 1")
    PricingRule selectByGoodsTypeId(Integer goodsTypeId);

    @Insert("INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time, update_time) " +
            "VALUES (#{goodsTypeId}, #{baseFee}, #{pricePerKg}, #{isActive}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PricingRule pricingRule);

    @Update("UPDATE pricing_rules SET goods_type_id = #{goodsTypeId}, base_fee = #{baseFee}, " +
            "price_per_kg = #{pricePerKg}, is_active = #{isActive}, update_time = NOW() WHERE id = #{id}")
    int updateById(PricingRule pricingRule);

    @Delete("DELETE FROM pricing_rules WHERE id = #{id}")
    int deleteById(Long id);
}
