package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.Address;
import com.logistics.api.service.AddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<Result<List<Address>>> getUserAddresses(Principal principal) {
        String username = principal.getName();
        List<Address> addresses = addressService.getUserAddresses(username);
        return ResponseEntity.ok(Result.success(addresses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<Address>> getAddressById(@PathVariable Long id, Principal principal) {
        try {
            String username = principal.getName();
            Address address = addressService.getAddressById(id, username);
            return ResponseEntity.ok(Result.success(address));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Result.error(60001, "地址不存在"));
        }
    }

    @GetMapping("/default")
    public ResponseEntity<Result<Address>> getDefaultAddress(Principal principal) {
        String username = principal.getName();
        Address address = addressService.getDefaultAddress(username);
        if (address == null) {
            return ResponseEntity.ok(Result.success("暂无默认地址", null));
        }
        return ResponseEntity.ok(Result.success(address));
    }

    @PostMapping
    public ResponseEntity<Result<Address>> addAddress(@RequestBody Address address, Principal principal) {
        try {
            String username = principal.getName();
            Address savedAddress = addressService.addAddress(address, username);
            return ResponseEntity.ok(Result.success("地址添加成功", savedAddress));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(60002, e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<Address>> updateAddress(@PathVariable Long id, @RequestBody Address address, Principal principal) {
        try {
            String username = principal.getName();
            Address updatedAddress = addressService.updateAddress(id, address, username);
            return ResponseEntity.ok(Result.success("地址更新成功", updatedAddress));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Result.error(60001, "地址不存在"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<String>> deleteAddress(@PathVariable Long id, Principal principal) {
        try {
            String username = principal.getName();
            addressService.deleteAddress(id, username);
            return ResponseEntity.ok(Result.success("地址删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Result.error(60001, "地址不存在"));
        }
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<Result<String>> setDefaultAddress(@PathVariable Long id, Principal principal) {
        try {
            String username = principal.getName();
            addressService.setDefaultAddress(id, username);
            return ResponseEntity.ok(Result.success("默认地址设置成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Result.error(60001, "地址不存在"));
        }
    }

    @GetMapping("/count")
    public ResponseEntity<Result<Map<String, Integer>>> getAddressCount(Principal principal) {
        String username = principal.getName();
        int count = addressService.getAddressCount(username);
        return ResponseEntity.ok(Result.success(Map.of("count", count)));
    }
}
