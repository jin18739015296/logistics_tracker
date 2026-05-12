package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.enums.OrderStatus;
import com.logistics.api.mapper.OrderAddressMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.model.*;
import com.logistics.api.dto.CourierTaskDTO;
import com.logistics.api.service.CourierOrderService;
import com.logistics.api.service.CourierStatusService;
import com.logistics.api.service.WalletService;
import com.logistics.api.service.DeliveryTaskService;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/courier")
public class CourierController {

    private static final Set<String> PENDING_TASK_STATUSES = Set.of(
            "awaiting_courier_confirm", "awaiting_pickup");
    private static final Set<String> DELIVERING_TASK_STATUSES = Set.of(
            "picked_up", "in_transit");

    @Autowired
    private WalletService walletService;

    @Autowired
    private DeliveryTaskService deliveryTaskService;

    @Autowired
    private UserService userService;

    @Autowired
    private CourierOrderService courierOrderService;

    @Autowired
    private CourierStatusService courierStatusService;

    @Autowired
    private com.logistics.api.mapper.CourierReviewMapper courierReviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderAddressMapper orderAddressMapper;

    @Autowired
    private com.logistics.api.mapper.WalletTransactionMapper walletTransactionMapper;

    private static String formatOrderAddress(OrderAddress a) {
        if (a == null) {
            return "";
        }
        String p = a.getProvince() != null ? a.getProvince() : "";
        String c = a.getCity() != null ? a.getCity() : "";
        String d = a.getDistrict() != null ? a.getDistrict() : "";
        String det = a.getDetailAddress() != null ? a.getDetailAddress() : "";
        return p + c + d + det;
    }

    private String getLevelName(Integer level) {
        switch (level) {
            case 1:
                return "初级骑手";
            case 2:
                return "中级骑手";
            case 3:
                return "高级骑手";
            case 4:
                return "金牌骑手";
            case 5:
                return "钻石骑手";
            default:
                return "初级骑手";
        }
    }

    private Integer calculateLevel(int totalDeliveries, BigDecimal rating) {
        if (totalDeliveries >= 1000 && rating.compareTo(new BigDecimal("4.8")) >= 0) {
            return 5;
        } else if (totalDeliveries >= 500 && rating.compareTo(new BigDecimal("4.5")) >= 0) {
            return 4;
        } else if (totalDeliveries >= 200 && rating.compareTo(new BigDecimal("4.2")) >= 0) {
            return 3;
        } else if (totalDeliveries >= 50 && rating.compareTo(new BigDecimal("4.0")) >= 0) {
            return 2;
        } else {
            return 1;
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Result<Map<String, Object>>> getStats(Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
        }

        Long courierId = user.getId();

        List<DeliveryTask> allTasks = deliveryTaskService.getTasksByCourierId(courierId);
        List<DeliveryTask> monthTasks = deliveryTaskService.getTasksByCourierIdAndMonth(courierId, LocalDateTime.now());

        int totalDeliveries = (int) allTasks.stream().filter(t -> "delivered".equals(t.getStatus())).count();
        int monthDeliveries = (int) monthTasks.stream().filter(t -> "delivered".equals(t.getStatus())).count();

        long completedTasks = allTasks.stream()
                .filter(t -> "delivered".equals(t.getStatus()))
                .count();
        BigDecimal onTimeRate = completedTasks > 0
                ? new BigDecimal("98.5")
                : new BigDecimal("100.00");

        Double avgRating = courierReviewMapper.selectAverageRatingByCourierId(courierId);
        BigDecimal rating = avgRating != null
                ? BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("5.0");
        int reviewCount = courierReviewMapper.selectCountByCourierId(courierId);

        com.logistics.api.model.Wallet wallet = walletService.getOrCreateWallet(courierId);
        BigDecimal totalIncome = wallet.getTotalIncome();

        // 本月收入：通过钱包流水表统计本月收入入账金额
        LocalDateTime monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime monthEnd = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);
        BigDecimal monthIncome = walletTransactionMapper.sumIncomeByUserIdAndTimeRange(
                courierId, monthStart, monthEnd);
        if (monthIncome == null) {
            monthIncome = BigDecimal.ZERO;
        }

        Integer level = calculateLevel(totalDeliveries, rating);

        Map<String, Object> result = new HashMap<>();
        result.put("totalDeliveries", totalDeliveries);
        result.put("monthDeliveries", monthDeliveries);
        result.put("onTimeRate", onTimeRate);
        result.put("rating", rating);
        result.put("reviewCount", reviewCount);
        result.put("monthIncome", monthIncome);
        result.put("totalIncome", totalIncome);
        result.put("monthWorkHours", monthDeliveries * 2);
        result.put("level", level);
        result.put("levelName", getLevelName(level));

        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Result<Map<String, Object>>> getDashboard(Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
        }

        Long courierId = user.getId();

        List<DeliveryTask> allTasks = deliveryTaskService.getTasksByCourierId(courierId);
        com.logistics.api.model.Wallet wallet = walletService.getOrCreateWallet(courierId);

        int totalDeliveries = (int) allTasks.stream().filter(t -> "delivered".equals(t.getStatus())).count();
        BigDecimal rating;
        Double avgRating = courierReviewMapper.selectAverageRatingByCourierId(courierId);
        if (avgRating != null) {
            rating = BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP);
        } else {
            rating = new BigDecimal("5.0");
        }
        BigDecimal onTimeRate = totalDeliveries > 0 ? new BigDecimal("98.5") : new BigDecimal("100.00");
        Integer level = calculateLevel(totalDeliveries, rating);

        LocalDate today = LocalDate.now();
        int pending = (int) allTasks.stream()
                .filter(t -> PENDING_TASK_STATUSES.contains(t.getStatus()))
                .count();
        int inTransit = (int) allTasks.stream()
                .filter(t -> DELIVERING_TASK_STATUSES.contains(t.getStatus()))
                .count();
        int completedToday = (int) allTasks.stream()
                .filter(t -> "delivered".equals(t.getStatus())
                        && t.getDeliveryTime() != null
                        && t.getDeliveryTime().toLocalDate().equals(today))
                .count();
        long todayTaskCount = allTasks.stream()
                .filter(t -> t.getCreateTime() != null 
                        && t.getCreateTime().toLocalDate().equals(today)
                        && !"rejected".equals(t.getStatus()))
                .count();

        // 今日收入：通过钱包流水表统计今日收入入账金额
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(23, 59, 59);
        BigDecimal todayIncome = walletTransactionMapper.sumIncomeByUserIdAndTimeRange(
                courierId, todayStart, todayEnd);
        if (todayIncome == null) {
            todayIncome = BigDecimal.ZERO;
        }

        // 本周收入：通过钱包流水表统计本周收入入账金额
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDateTime weekStart = startOfWeek.atStartOfDay();
        LocalDateTime weekEnd = endOfWeek.atTime(23, 59, 59);
        BigDecimal weekIncome = walletTransactionMapper.sumIncomeByUserIdAndTimeRange(
                courierId, weekStart, weekEnd);
        if (weekIncome == null) {
            weekIncome = BigDecimal.ZERO;
        }

        // 本月收入：通过钱包流水表统计本月收入入账金额
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        LocalDateTime monthStartTime = monthStart.atStartOfDay();
        LocalDateTime monthEndTime = monthEnd.atTime(23, 59, 59);
        BigDecimal monthIncome = walletTransactionMapper.sumIncomeByUserIdAndTimeRange(
                courierId, monthStartTime, monthEndTime);
        if (monthIncome == null) {
            monthIncome = BigDecimal.ZERO;
        }

        Map<String, Object> result = new HashMap<>();

        Map<String, Object> todayMap = new HashMap<>();
        todayMap.put("deliveries", todayTaskCount);
        todayMap.put("income", todayIncome);
        result.put("today", todayMap);

        Map<String, Object> weekMap = new HashMap<>();
        weekMap.put("income", weekIncome);
        result.put("week", weekMap);

        Map<String, Object> monthMap = new HashMap<>();
        monthMap.put("income", monthIncome);
        result.put("month", monthMap);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalDeliveries", totalDeliveries);
        statistics.put("pending", pending);
        statistics.put("inTransit", inTransit);
        statistics.put("completed", completedToday);
        statistics.put("total", todayTaskCount);
        statistics.put("rating", rating);
        statistics.put("onTimeRate", onTimeRate);
        statistics.put("balance", wallet.getBalance());
        result.put("statistics", statistics);

        Map<String, Object> levelInfo = new HashMap<>();
        levelInfo.put("current", level);
        levelInfo.put("name", getLevelName(level));
        result.put("level", levelInfo);

        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/tasks")
    public ResponseEntity<Result<List<CourierTaskDTO>>> getCurrentTasks(Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
        }

        List<CourierTaskDTO> tasks = courierOrderService.getCurrentTasks(user.getId());
        return ResponseEntity.ok(Result.success(tasks));
    }

    @GetMapping("/tasks/pending-confirm")
    public ResponseEntity<Result<List<CourierTaskDTO>>> getPendingConfirmTasks(Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
        }

        List<CourierTaskDTO> tasks = courierOrderService.getPendingConfirmTasks(user.getId());
        return ResponseEntity.ok(Result.success(tasks));
    }

    @PostMapping("/orders/{orderId}/pickup")
    public ResponseEntity<Result<String>> pickupOrder(@PathVariable Long orderId,
                                                    @RequestBody Map<String, Object> request,
                                                    Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            String location = (String) request.get("location");
            BigDecimal latitude = request.get("latitude") != null ?
                    new BigDecimal(request.get("latitude").toString()) : null;
            BigDecimal longitude = request.get("longitude") != null ?
                    new BigDecimal(request.get("longitude").toString()) : null;

            courierOrderService.pickupOrder(orderId, user.getId(), location, latitude, longitude);

            return ResponseEntity.ok(Result.success("揽件成功"));
        } catch (Exception e) {
            log.error("揽件失败, orderId: {}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(50001, e.getMessage()));
        }
    }

    @PostMapping("/orders/pickup-by-no")
    public ResponseEntity<Result<String>> pickupOrderByNo(@RequestBody Map<String, Object> request,
                                                          Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            String orderNo = (String) request.get("orderNo");
            if (orderNo == null || orderNo.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error(50002, "订单号不能为空"));
            }

            courierOrderService.pickupOrderByNo(orderNo.trim(), user.getId());

            return ResponseEntity.ok(Result.success("揽件成功"));
        } catch (Exception e) {
            log.error("根据订单号揽件失败", e);
            return ResponseEntity.badRequest().body(Result.error(50001, e.getMessage()));
        }
    }

    @PostMapping("/orders/{orderId}/in-transit")
    public ResponseEntity<Result<String>> updateInTransit(@PathVariable Long orderId,
                                                        @RequestBody Map<String, Object> request,
                                                        Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            String location = (String) request.get("location");
            String description = (String) request.get("description");

            courierOrderService.updateInTransit(orderId, user.getId(), location, description);

            return ResponseEntity.ok(Result.success("运输状态更新成功"));
        } catch (Exception e) {
            log.error("更新运输状态失败, orderId: {}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(50002, e.getMessage()));
        }
    }

    @PostMapping("/orders/{orderId}/start-delivery")
    public ResponseEntity<Result<String>> startDelivery(@PathVariable Long orderId,
                                                      @RequestBody Map<String, Object> request,
                                                      Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            String location = (String) request.get("location");

            courierOrderService.startDelivery(orderId, user.getId(), location);

            return ResponseEntity.ok(Result.success("开始运输"));
        } catch (Exception e) {
            log.error("开始运输失败, orderId: {}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(50003, e.getMessage()));
        }
    }

    @PostMapping("/orders/{orderId}/confirm-delivery")
    public ResponseEntity<Result<String>> confirmDelivery(@PathVariable Long orderId,
                                                        @RequestBody Map<String, Object> request,
                                                        Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            String location = (String) request.get("location");
            BigDecimal latitude = request.get("latitude") != null ?
                    new BigDecimal(request.get("latitude").toString()) : null;
            BigDecimal longitude = request.get("longitude") != null ?
                    new BigDecimal(request.get("longitude").toString()) : null;

            courierOrderService.confirmDelivery(orderId, user.getId(), location,
                    latitude, longitude);

            return ResponseEntity.ok(Result.success("送达确认成功"));
        } catch (Exception e) {
            log.error("确认送达失败, orderId: {}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(50004, e.getMessage()));
        }
    }

    @PostMapping("/orders/{orderId}/exception")
    public ResponseEntity<Result<String>> reportException(@PathVariable Long orderId,
                                                        @RequestBody Map<String, Object> request,
                                                        Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            String exceptionType = (String) request.get("exceptionType");
            String description = (String) request.get("description");

            courierOrderService.reportException(orderId, user.getId(), exceptionType, description);

            return ResponseEntity.ok(Result.success("异常上报成功"));
        } catch (Exception e) {
            log.error("上报异常失败, orderId: {}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(50005, e.getMessage()));
        }
    }

    @GetMapping("/orders/{orderId}/events")
    public ResponseEntity<Result<List<LogisticsEvent>>> getOrderEvents(@PathVariable Long orderId, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
        }

        List<LogisticsEvent> events = courierOrderService.getOrderEvents(orderId);
        return ResponseEntity.ok(Result.success(events));
    }

    @PostMapping("/online")
    public ResponseEntity<Result<String>> goOnline(@RequestBody Map<String, Object> request, Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            Double latitude = null;
            Double longitude = null;
            
            if (request.get("latitude") != null) {
                try {
                    latitude = Double.parseDouble(request.get("latitude").toString());
                } catch (NumberFormatException e) {
                    log.warn("纬度格式错误: {}", request.get("latitude"));
                }
            }
            
            if (request.get("longitude") != null) {
                try {
                    longitude = Double.parseDouble(request.get("longitude").toString());
                } catch (NumberFormatException e) {
                    log.warn("经度格式错误: {}", request.get("longitude"));
                }
            }

            if (latitude == null || longitude == null) {
                return ResponseEntity.badRequest().body(Result.error(50006,
                        "上线必须上报当前位置的经纬度（latitude、longitude）"));
            }

            String workCity = null;
            Object workCityRaw = request.get("workCity");
            if (workCityRaw != null) {
                String t = workCityRaw.toString().trim();
                if (!t.isEmpty()) {
                    workCity = t;
                }
            }
            if (workCity == null && request.get("city") != null) {
                String t = request.get("city").toString().trim();
                if (!t.isEmpty()) {
                    workCity = t;
                }
            }
            if (workCity == null || workCity.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error(50006,
                        "缺少市级名称：请根据当前坐标逆地理得到「市」级行政区名称，传入 workCity 或 city（建议与当地订单寄件地址中的市一致，否则自动派单可能匹配失败）。不提供默认城市。"));
            }

            log.debug("配送员上线: courierId={}, lat={}, lng={}, workCity={}",
                    user.getId(), latitude, longitude, workCity);

            courierStatusService.goOnline(user.getId(), latitude, longitude, workCity);

            return ResponseEntity.ok(Result.success("上线成功"));
        } catch (Exception e) {
            log.error("上线失败", e);
            return ResponseEntity.badRequest().body(Result.error(50006, e.getMessage()));
        }
    }

    @PostMapping("/offline")
    public ResponseEntity<Result<String>> goOffline(Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            courierStatusService.goOffline(user.getId());

            return ResponseEntity.ok(Result.success("下线成功"));
        } catch (com.logistics.api.common.BusinessException e) {
            // 业务异常，将错误信息返回给前端
            log.warn("配送员下线业务校验失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Result.error(50007, e.getMessage()));
        } catch (Exception e) {
            log.error("下线失败", e);
            return ResponseEntity.badRequest().body(Result.error(50007, "下线失败，请重试"));
        }
    }

    @PostMapping("/location")
    public ResponseEntity<Result<String>> updateLocation(@RequestBody Map<String, Object> request, Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            BigDecimal latitude = request.get("latitude") != null ?
                    new BigDecimal(request.get("latitude").toString()) : null;
            BigDecimal longitude = request.get("longitude") != null ?
                    new BigDecimal(request.get("longitude").toString()) : null;

            if (latitude == null || longitude == null) {
                return ResponseEntity.badRequest().body(Result.error(40003, "经纬度不能为空"));
            }

            courierStatusService.updateLocation(user.getId(),
                    latitude.doubleValue(),
                    longitude.doubleValue());

            return ResponseEntity.ok(Result.success("位置更新成功"));
        } catch (Exception e) {
            log.error("更新位置失败", e);
            return ResponseEntity.badRequest().body(Result.error(50008, e.getMessage()));
        }
    }

    /**
     * 获取配送员当前状态（数据库为主，Redis为辅）
     * 用于配送员首页状态查询，会自动同步Redis
     * @param latitude 可选，前端当前位置纬度
     * @param longitude 可选，前端当前位置经度
     */
    @GetMapping("/status")
    public ResponseEntity<Result<Map<String, Object>>> getCourierStatus(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            log.debug("获取配送员状态, courierId: {}, location: {}, {}", user.getId(), latitude, longitude);

            Map<String, Object> status = courierStatusService.getCourierCurrentStatus(
                    user.getId(), latitude, longitude);

            return ResponseEntity.ok(Result.success(status));
        } catch (Exception e) {
            log.error("获取配送员状态失败", e);
            return ResponseEntity.badRequest().body(Result.error(50016, e.getMessage()));
        }
    }

    @GetMapping("/reviews")
    public ResponseEntity<Result<Map<String, Object>>> getCourierReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            List<com.logistics.api.model.CourierReview> reviews =
                    courierReviewMapper.selectByCourierId(user.getId(), page * size, size);

            Double averageRating = courierReviewMapper.selectAverageRatingByCourierId(user.getId());
            int totalReviews = courierReviewMapper.selectCountByCourierId(user.getId());
            List<java.util.Map<String, Object>> distribution =
                    courierReviewMapper.selectRatingDistribution(user.getId());

            Map<String, Integer> ratingDistribution = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                ratingDistribution.put(String.valueOf(i), 0);
            }
            for (java.util.Map<String, Object> item : distribution) {
                Integer rating = (Integer) item.get("rating");
                Long count = (Long) item.get("count");
                ratingDistribution.put(String.valueOf(rating), count.intValue());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("reviews", reviews);
            result.put("summary", Map.of(
                    "averageRating", averageRating != null ? averageRating : 5.0,
                    "totalReviews", totalReviews,
                    "ratingDistribution", ratingDistribution
            ));

            return ResponseEntity.ok(Result.success(result));
        } catch (Exception e) {
            log.error("获取评价列表失败", e);
            return ResponseEntity.badRequest().body(Result.error(50009, e.getMessage()));
        }
    }

    @GetMapping("/delivery-history")
    public ResponseEntity<Result<Map<String, Object>>> getDeliveryHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            List<DeliveryTask> tasks = deliveryTaskService.getTasksByCourierId(user.getId());

            List<Map<String, Object>> historyList = tasks.stream()
                    .filter(task -> {
                        String s = task.getStatus();
                        return OrderStatus.DELIVERED.getCode().equals(s)
                                || OrderStatus.COMPLETED.getCode().equals(s)
                                || OrderStatus.CANCELLED.getCode().equals(s);
                    })
                    .map(task -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", task.getId());
                        item.put("orderId", task.getOrderId().toString());
                        Order order = orderMapper.selectById(task.getOrderId());
                        item.put("orderNo", order != null ? order.getOrderNo() : ("ORD" + task.getOrderId()));
                        if (order != null) {
                            item.put("status", order.getStatus());
                            item.put("statusDesc", OrderStatus.getDescByCode(order.getStatus()));
                        } else {
                            item.put("status", task.getStatus());
                            item.put("statusDesc", OrderStatus.getDescByCode(task.getStatus()));
                        }
                        OrderAddress recv = orderAddressMapper.selectByOrderIdAndType(task.getOrderId(), "receiver");
                        OrderAddress send = orderAddressMapper.selectByOrderIdAndType(task.getOrderId(), "sender");
                        item.put("receiverAddress", formatOrderAddress(recv));
                        item.put("senderAddress", formatOrderAddress(send));
                        item.put("type", "deliver");
                        item.put("pickupTime", task.getPickupTime());
                        item.put("deliveryTime", task.getDeliveryTime());
                        // 与 getCurrentTasks(CourierTaskDTO) 一致：列表「运费」用 deliveryFee/totalAmount
                        // 取消订单不显示收入
                        boolean isCancelled = OrderStatus.CANCELLED.getCode().equals(task.getStatus());
                        if (!isCancelled && order != null && order.getTotalAmount() != null) {
                            item.put("totalAmount", order.getTotalAmount());
                            BigDecimal fee = order.getTotalAmount().multiply(new BigDecimal("0.9"));
                            item.put("deliveryFee", fee);
                            item.put("income", fee);
                        } else {
                            item.put("totalAmount", order != null ? order.getTotalAmount() : null);
                            item.put("deliveryFee", null);
                            item.put("income", BigDecimal.ZERO);
                        }
                        return item;
                    })
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("content", historyList);
            result.put("totalElements", historyList.size());
            result.put("totalPages", (historyList.size() + size - 1) / size);

            return ResponseEntity.ok(Result.success(result));
        } catch (Exception e) {
            log.error("获取配送历史失败", e);
            return ResponseEntity.badRequest().body(Result.error(50010, e.getMessage()));
        }
    }

    @PostMapping("/orders/{orderId}/confirm")
    public ResponseEntity<Result<String>> confirmOrder(@PathVariable Long orderId, Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            boolean success = courierOrderService.confirmOrder(orderId, user.getId());

            if (success) {
                return ResponseEntity.ok(Result.success("订单确认成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.error(50011, "订单确认失败，请检查订单状态"));
            }
        } catch (Exception e) {
            log.error("确认订单失败, orderId: {}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(50012, e.getMessage()));
        }
    }

    @PostMapping("/orders/{orderId}/reject")
    public ResponseEntity<Result<String>> rejectOrder(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> request,
            Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            String reason = (String) request.get("reason");

            boolean success = courierOrderService.rejectOrder(orderId, user.getId(), reason);

            if (success) {
                return ResponseEntity.ok(Result.success("订单已拒绝"));
            } else {
                return ResponseEntity.badRequest().body(Result.error(50013, "拒绝订单失败，请检查订单状态"));
            }
        } catch (Exception e) {
            log.error("拒绝订单失败, orderId: {}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(50014, e.getMessage()));
        }
    }

    @PostMapping("/start-batch-delivery")
    public ResponseEntity<Result<String>> startBatchDelivery(Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            if (user == null) {
                return ResponseEntity.status(401).body(Result.error(10002, "未授权"));
            }

            courierOrderService.startBatchDelivery(user.getId());

            return ResponseEntity.ok(Result.success("开始配送成功"));
        } catch (Exception e) {
            log.error("开始配送失败", e);
            return ResponseEntity.badRequest().body(Result.error(50015, e.getMessage()));
        }
    }
}
