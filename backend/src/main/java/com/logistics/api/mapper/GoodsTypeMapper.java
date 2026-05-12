package com.logistics.api.mapper;

import com.logistics.api.model.GoodsType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsTypeMapper {

    GoodsType selectById(Integer id);

    GoodsType selectByCode(String code);

    List<GoodsType> selectAll();

    List<GoodsType> selectActive();

    int insert(GoodsType goodsType);

    int updateById(GoodsType goodsType);

    int deleteById(Integer id);

    int updateStatus(@Param("id") Integer id, @Param("isActive") Integer isActive);
}
