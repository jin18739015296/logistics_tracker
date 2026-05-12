package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.PaymentRecord;
import com.logistics.api.model.User;
import com.logistics.api.service.UserService;
import com.logistics.api.mapper.PaymentRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/payment-records")
public class PaymentRecordController {

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户的支付记录
     */
    @GetMapping
    public ResponseEntity<Result<List<PaymentRecord>>> getMyPaymentRecords(
            Principal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = userService.getUserByUsername(principal.getName());
        int offset = (page - 1) * size;

        List<PaymentRecord> records;
        if (status != null && !status.isEmpty()) {
            records = paymentRecordMapper.selectByUserIdAndStatus(user.getId(), status, size, offset);
        } else {
            records = paymentRecordMapper.selectByUserId(user.getId(), size, offset);
        }

        return ResponseEntity.ok(Result.success(records));
    }
}
