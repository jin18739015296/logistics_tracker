package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.Complaint;
import com.logistics.api.model.User;
import com.logistics.api.service.ComplaintService;
import com.logistics.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/complaints")
public class ComplaintController {

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private UserService userService;

    /**
     * 提交投诉
     */
    @PostMapping
    public ResponseEntity<Result<Complaint>> submitComplaint(@RequestBody Complaint complaint, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        complaint.setUserId(user.getId());
        complaint.setComplainantNotifyType(getUserType(user));
        Complaint result = complaintService.submitComplaint(complaint);
        return ResponseEntity.ok(Result.success("投诉提交成功", result));
    }

    /**
     * 获取投诉详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Result<Complaint>> getComplaintDetail(@PathVariable Long id) {
        Complaint complaint = complaintService.getComplaintById(id);
        return ResponseEntity.ok(Result.success(complaint));
    }

    /**
     * 获取我发起的投诉
     */
    @GetMapping("/my")
    public ResponseEntity<Result<List<Complaint>>> getMyComplaints(
            Principal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = userService.getUserByUsername(principal.getName());
        List<Complaint> complaints = complaintService.getMyComplaints(user.getId(), page, size);
        return ResponseEntity.ok(Result.success(complaints));
    }

    /**
     * 获取针对我的投诉
     */
    @GetMapping("/against-me")
    public ResponseEntity<Result<List<Complaint>>> getComplaintsAgainstMe(
            Principal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = userService.getUserByUsername(principal.getName());
        List<Complaint> complaints = complaintService.getComplaintsAgainstMe(user.getId(), getUserType(user), page, size);
        return ResponseEntity.ok(Result.success(complaints));
    }

    /**
     * 按订单主键获取该订单的投诉列表
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<Result<List<Complaint>>> getOrderComplaints(@PathVariable Long orderId) {
        List<Complaint> complaints = complaintService.getComplaintsByOrderId(orderId);
        return ResponseEntity.ok(Result.success(complaints));
    }

    /**
     * 按订单号（业务单号）获取该订单的投诉列表
     */
    @GetMapping("/order/by-no/{orderNo}")
    public ResponseEntity<Result<List<Complaint>>> getOrderComplaintsByOrderNo(@PathVariable String orderNo) {
        List<Complaint> complaints = complaintService.getComplaintsByOrderNo(orderNo);
        return ResponseEntity.ok(Result.success(complaints));
    }

    /**
     * 查询当前用户是否已对某订单投诉过
     */
    @GetMapping("/order/{orderId}/my")
    public ResponseEntity<Result<Complaint>> getMyComplaintByOrderId(@PathVariable Long orderId, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        Complaint complaint = complaintService.getComplaintByOrderIdAndUserId(orderId, user.getId());
        return ResponseEntity.ok(Result.success(complaint));
    }

    /**
     * 撤销投诉
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<Result<String>> cancelComplaint(@PathVariable Long id, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        complaintService.cancelComplaint(id, user.getId());
        return ResponseEntity.ok(Result.success("撤销成功"));
    }

    private String getUserType(User user) {
        if ("delivery".equals(user.getRole())) {
            return "courier";
        } else if ("admin".equals(user.getRole())) {
            return "admin";
        } else {
            return "user";
        }
    }
}
