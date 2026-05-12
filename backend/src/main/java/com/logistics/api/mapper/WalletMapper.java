package com.logistics.api.mapper;

import com.logistics.api.model.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface WalletMapper {

    Wallet selectByUserId(@Param("userId") Long userId);

    int insert(Wallet wallet);

    int insertIgnore(Wallet wallet);

    int updateBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    int updateFrozenAmount(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    int addIncome(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
