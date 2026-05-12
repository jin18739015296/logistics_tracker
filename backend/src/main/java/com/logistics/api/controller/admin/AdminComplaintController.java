package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.model.Complaint;
import com.logistics.api.model.User;
import com.logistics.api.service.ComplaintService;
import com.logistics.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/complaints")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminComplaintController {

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private UserService userService;

    /**
     * 获取所有投诉列表
     */
    @GetMapping
    public ResponseEntity<Result<List<Complaint>>> getAllComplaints(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Complaint> complaints = complaintService.getAllComplaints(page, size);
        return ResponseEntity.ok(Result.success(complaints));
    }

    /**
     * 获取待处理投诉列表
     */
    @GetMapping("/pending")
    public ResponseEntity<Result<List<Complaint>>> getPendingComplaints(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Complaint> complaints = complaintService.getPendingComplaints(page, size);
        return ResponseEntity.ok(Result.success(complaints));
    }

    /**
     * 获取待处理投诉数量
     */
    @GetMapping("/pending-count")
    public ResponseEntity<Result<Map<String, Integer>>> getPendingCount() {
        int count = complaintService.getPendingCount();
        Map<String, Integer> result = new HashMap<>();
        result.put("pendingCount", count);
        return ResponseEntity.ok(Result.success(result));
    }

    /**
     * 处理投诉
     */
    @PostMapping("/{id}/handle")
    public ResponseEntity<Result<String>> handleComplaint(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Principal principal) {
        User admin = userService.getUserByUsername(principal.getName());
        String status = request.get("status");
        String result = request.get("result");
        String resultContent = request.get("resultContent");

        complaintService.handleComplaint(id, status, result, resultContent, admin.getId(), admin.getName());
        return ResponseEntity.ok(Result.success("处理成功"));
    }

    /**
     * 获取投诉详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Result<Complaint>> getComplaintDetail(@PathVariable Long id) {
        Complaint complaint = complaintService.getComplaintById(id);
        return ResponseEntity.ok(Result.success(complaint));
    }
}
