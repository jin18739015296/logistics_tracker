package com.logistics.api.service;

import com.logistics.api.model.Address;

import java.util.List;

/**
 * 地址服务接口
 */
public interface AddressService {

    /**
     * 获取用户的所有地址
     */
    List<Address> getUserAddresses(String username);

    /**
     * 获取单个地址
     */
    Address getAddressById(Long addressId, String username);

    /**
     * 获取默认地址
     */
    Address getDefaultAddress(String username);

    /**
     * 添加地址
     */
    Address addAddress(Address address, String username);

    /**
     * 更新地址
     */
    Address updateAddress(Long addressId, Address addressUpdates, String username);

    /**
     * 删除地址
     */
    void deleteAddress(Long addressId, String username);

    /**
     * 设置默认地址
     */
    void setDefaultAddress(Long addressId, String username);

    /**
     * 获取地址数量
     */
    int getAddressCount(String username);
}
