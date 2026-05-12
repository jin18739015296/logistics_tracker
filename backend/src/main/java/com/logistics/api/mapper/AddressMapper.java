package com.logistics.api.mapper;

import com.logistics.api.model.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AddressMapper {

    Address selectById(Long id);

    List<Address> selectByUserId(Long userId);

    Address selectDefaultByUserId(Long userId);

    int insert(Address address);

    int updateById(Address address);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    int clearDefaultByUserId(Long userId);

    int setDefaultById(@Param("id") Long id, @Param("userId") Long userId);

    int countByUserId(Long userId);
}
