package com.logistics.api.mapper;

import com.logistics.api.model.DistancePricingRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DistancePricingRuleMapper {

    DistancePricingRule selectById(Long id);
    
    List<DistancePricingRule> selectAll();
    
    List<DistancePricingRule> selectActive();
    
    DistancePricingRule selectByDistance(@Param("distance") BigDecimal distance);
}
