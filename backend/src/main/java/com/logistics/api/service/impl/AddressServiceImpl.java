package com.logistics.api.service.impl;

import com.logistics.api.mapper.AddressMapper;
import com.logistics.api.mapper.UserMapper;
import com.logistics.api.model.Address;
import com.logistics.api.model.User;
import com.logistics.api.service.AddressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AddressServiceImpl implements AddressService {

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<Address> getUserAddresses(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return addressMapper.selectByUserId(user.getId());
    }

    @Override
    public Address getAddressById(Long addressId, String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(user.getId())) {
            throw new RuntimeException("地址不存在");
        }
        return address;
    }

    @Override
    public Address getDefaultAddress(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return addressMapper.selectDefaultByUserId(user.getId());
    }

    @Override
    public Address addAddress(Address address, String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        address.setUserId(user.getId());
        
        // 如果设置为默认地址，先取消其他默认地址
        // isDefault: 0=否, 1=是
        if (Integer.valueOf(1).equals(address.getIsDefault())) {
            addressMapper.clearDefaultByUserId(user.getId());
        }
        
        addressMapper.insert(address);
        log.info("添加地址成功: userId={}, addressId={}", user.getId(), address.getId());
        return address;
    }

    @Override
    public Address updateAddress(Long addressId, Address addressUpdates, String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(user.getId())) {
            throw new RuntimeException("地址不存在");
        }
        
        // 更新字段
        if (addressUpdates.getContactName() != null) {
            address.setContactName(addressUpdates.getContactName());
        }
        if (addressUpdates.getContactPhone() != null) {
            address.setContactPhone(addressUpdates.getContactPhone());
        }
        if (addressUpdates.getProvince() != null) {
            address.setProvince(addressUpdates.getProvince());
        }
        if (addressUpdates.getCity() != null) {
            address.setCity(addressUpdates.getCity());
        }
        if (addressUpdates.getDistrict() != null) {
            address.setDistrict(addressUpdates.getDistrict());
        }
        if (addressUpdates.getDetailAddress() != null) {
            address.setDetailAddress(addressUpdates.getDetailAddress());
        }
        if (addressUpdates.getLatitude() != null) {
            address.setLatitude(addressUpdates.getLatitude());
        }
        if (addressUpdates.getLongitude() != null) {
            address.setLongitude(addressUpdates.getLongitude());
        }
        if (addressUpdates.getTag() != null) {
            address.setTag(addressUpdates.getTag());
        }
        
        // 如果设置为默认地址，先取消其他默认地址
        // isDefault: 0=否, 1=是
        if (Integer.valueOf(1).equals(addressUpdates.getIsDefault()) && !Integer.valueOf(1).equals(address.getIsDefault())) {
            addressMapper.clearDefaultByUserId(user.getId());
            address.setIsDefault(1);
        }
        
        addressMapper.updateById(address);
        log.info("更新地址成功: addressId={}", addressId);
        return address;
    }

    @Override
    public void deleteAddress(Long addressId, String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(user.getId())) {
            throw new RuntimeException("地址不存在");
        }
        
        addressMapper.deleteById(addressId, user.getId());
        log.info("删除地址成功: addressId={}", addressId);
    }

    @Override
    public void setDefaultAddress(Long addressId, String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(user.getId())) {
            throw new RuntimeException("地址不存在");
        }
        
        // 先取消其他默认地址
        addressMapper.clearDefaultByUserId(user.getId());
        
        // 设置当前地址为默认
        address.setIsDefault(1);
        addressMapper.updateById(address);
        log.info("设置默认地址成功: addressId={}", addressId);
    }

    @Override
    public int getAddressCount(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return addressMapper.countByUserId(user.getId());
    }
}
