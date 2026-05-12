package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.service.DeliveryTaskService;
import com.logistics.api.service.OrderService;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin/stats")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminStatsController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private DeliveryTaskService deliveryTaskService;


    @GetMapping("/dashboard")
    public ResponseEntity<Result<Map<String, Object>>> getDashboardStats() {
        Map<String, Object> dashboard = new HashMap<>();
        
        Map<String, Object> orderStats = orderService.getOrderStatistics();
        dashboard.put("orders", orderStats);
        
        Map<String, Object> userStats = userService.getUserStatistics();
        dashboard.put("users", userStats);
        
        Map<String, Object> todayStats = orderService.getTodayOrderStats();
        dashboard.put("today", todayStats);
        
        return ResponseEntity.ok(Result.success(dashboard));
    }

    @GetMapping("/orders")
    public ResponseEntity<Result<Map<String, Object>>> getOrderStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "day") String groupBy) {
        
        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime start = startDate != null ? 
                LocalDate.parse(startDate).atStartOfDay() : 
                LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = endDate != null ? 
                LocalDate.parse(endDate).atTime(23, 59, 59) : 
                LocalDateTime.now();
        
        List<Map<String, Object>> trendData = orderService.getOrderTrend(start, end, groupBy);
        stats.put("trend", trendData);
        
        Map<String, Long> statusDistribution = orderService.getOrderStatusDistribution();
        stats.put("statusDistribution", statusDistribution);
        
        return ResponseEntity.ok(Result.success(stats));
    }

    @GetMapping("/revenue")
    public ResponseEntity<Result<Map<String, Object>>> getRevenueStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime start = startDate != null ? 
                LocalDate.parse(startDate).atStartOfDay() : 
                LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = endDate != null ? 
                LocalDate.parse(endDate).atTime(23, 59, 59) : 
                LocalDateTime.now();
        
        BigDecimal totalRevenue = orderService.getTotalRevenue(start, end);
        stats.put("totalRevenue", totalRevenue);
        
        List<Map<String, Object>> revenueTrend = orderService.getRevenueTrend(start, end);
        stats.put("trend", revenueTrend);
        
        return ResponseEntity.ok(Result.success(stats));
    }



    @GetMapping("/realtime")
    public ResponseEntity<Result<Map<String, Object>>> getRealtimeStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Map<String, Object> todayStats = orderService.getTodayOrderStats();
        stats.put("todayOrders", todayStats.get("totalOrders"));
        stats.put("todayRevenue", todayStats.get("totalRevenue"));
        stats.put("pendingOrders", todayStats.get("pendingOrders"));
        stats.put("completedToday", todayStats.get("completedOrders"));
        
        long onlineCouriers = userService.countOnlineCouriers();
        stats.put("onlineCouriers", onlineCouriers);
        
        long activeUsers = userService.countActiveUsersToday();
        stats.put("activeUsers", activeUsers);
        
        stats.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(Result.success(stats));
    }
}
