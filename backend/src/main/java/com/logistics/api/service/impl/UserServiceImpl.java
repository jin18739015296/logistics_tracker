package com.logistics.api.service.impl;

import com.logistics.api.enums.OrderStatus;
import com.logistics.api.mapper.CourierApplicationMapper;
import com.logistics.api.mapper.CourierReviewMapper;
import com.logistics.api.mapper.CourierStatusMapper;
import com.logistics.api.mapper.DeliveryTaskMapper;
import com.logistics.api.mapper.OrderAddressMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.mapper.UserMapper;
import com.logistics.api.model.CourierApplication;
import com.logistics.api.model.CourierStatus;
import com.logistics.api.model.DeliveryTask;
import com.logistics.api.model.Order;
import com.logistics.api.model.OrderAddress;
import com.logistics.api.model.User;
import com.logistics.api.service.UserService;
import com.logistics.api.service.support.OrderViewerPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    /** 与移动端「进行中」Tab 一致：已支付起至配送环节 */
    private static final Set<String> ORDER_IN_TRANSIT_TAB = Set.of(
            "paid", "awaiting_courier_confirm", "awaiting_pickup",
            "picked_up", "in_transit", "exception");

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final DeliveryTaskMapper deliveryTaskMapper;
    private final PasswordEncoder passwordEncoder;
    private final CourierStatusMapper courierStatusMapper;
    private final CourierReviewMapper courierReviewMapper;
    private final CourierApplicationMapper courierApplicationMapper;
    private final OrderAddressMapper orderAddressMapper;

    public UserServiceImpl(UserMapper userMapper, OrderMapper orderMapper,
                          DeliveryTaskMapper deliveryTaskMapper,
                          PasswordEncoder passwordEncoder,
                          CourierStatusMapper courierStatusMapper,
                          CourierReviewMapper courierReviewMapper,
                          CourierApplicationMapper courierApplicationMapper,
                          OrderAddressMapper orderAddressMapper) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.deliveryTaskMapper = deliveryTaskMapper;
        this.passwordEncoder = passwordEncoder;
        this.courierStatusMapper = courierStatusMapper;
        this.courierReviewMapper = courierReviewMapper;
        this.courierApplicationMapper = courierApplicationMapper;
        this.orderAddressMapper = orderAddressMapper;
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public User getUserByPhone(String phone) {
        return userMapper.selectByPhone(phone);
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }

    @Override
    public User updateUser(String username, Map<String, String> updates) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (updates.containsKey("name")) {
            user.setName(updates.get("name"));
        }
        if (updates.containsKey("phone")) {
            user.setPhone(updates.get("phone"));
        }

        userMapper.updateById(user);
        return user;
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码长度至少为6位");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Override
    public void deleteUser(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> getUserStats(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        long totalOrders = orderMapper.countByUserId(user.getId());
        List<Map<String, Object>> byStatus = orderMapper.selectOrderCountGroupByStatusForUser(user.getId());
        long pendingOrders = 0L;
        long inTransitOrders = 0L;
        long deliveredOrders = 0L;
        long completedOrders = 0L;
        for (Map<String, Object> row : byStatus) {
            String st = (String) row.get("s");
            long c = ((Number) row.get("c")).longValue();
            if ("pending".equals(st)) {
                pendingOrders += c;
            }
            if (st != null && ORDER_IN_TRANSIT_TAB.contains(st)) {
                inTransitOrders += c;
            }
            if ("delivered".equals(st)) {
                deliveredOrders += c;
            }
            if ("completed".equals(st)) {
                completedOrders += c;
            }
        }

        // 与 OrderServiceImpl#getUserOrders 一致：收件电话关联、且揽件后可见的订单计入统计（非下单人）
        String pnorm = OrderViewerPolicy.normalizePhone(user.getPhone());
        if (!pnorm.isEmpty()) {
            for (Order o : orderMapper.selectByReceiverNormalizedPhone(pnorm)) {
                if (user.getId().equals(o.getUserId())) {
                    continue;
                }
                DeliveryTask t = deliveryTaskMapper.selectByOrderId(o.getId());
                if (!OrderViewerPolicy.receiverCanViewOrder(o, t)) {
                    continue;
                }
                totalOrders++;
                String st = o.getStatus();
                if ("pending".equals(st)) {
                    pendingOrders++;
                }
                if (st != null && ORDER_IN_TRANSIT_TAB.contains(st)) {
                    inTransitOrders++;
                }
                if ("delivered".equals(st)) {
                    deliveredOrders++;
                }
                if ("completed".equals(st)) {
                    completedOrders++;
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", totalOrders);
        stats.put("pendingOrders", pendingOrders);
        stats.put("inTransitOrders", inTransitOrders);
        stats.put("deliveredOrders", deliveredOrders);
        stats.put("completedOrders", completedOrders);
        stats.put("toReviewOrders", countPendingCourierReview(user));

        return stats;
    }

    /**
     * 收件人视角下尚未评价配送员的订单数（与列表里「评价配送员」入口一致：已送达/已完成、已分配骑手、无评价记录）。
     */
    private long countPendingCourierReview(User user) {
        Map<Long, Order> merged = new LinkedHashMap<>();
        for (Order o : orderMapper.selectByUserId(user.getId())) {
            merged.put(o.getId(), o);
        }
        String pnorm = OrderViewerPolicy.normalizePhone(user.getPhone());
        if (!pnorm.isEmpty()) {
            for (Order o : orderMapper.selectByReceiverNormalizedPhone(pnorm)) {
                if (merged.containsKey(o.getId())) {
                    continue;
                }
                DeliveryTask t = deliveryTaskMapper.selectByOrderId(o.getId());
                if (!OrderViewerPolicy.receiverCanViewOrder(o, t)) {
                    continue;
                }
                merged.put(o.getId(), o);
            }
        }
        long n = 0L;
        for (Order o : merged.values()) {
            if (o.getCourierId() == null) {
                continue;
            }
            String st = o.getStatus();
            if (!OrderStatus.DELIVERED.getCode().equals(st) && !OrderStatus.COMPLETED.getCode().equals(st)) {
                continue;
            }
            OrderAddress receiver = orderAddressMapper.selectByOrderIdAndType(o.getId(), "receiver");
            if (receiver == null || !OrderViewerPolicy.phonesMatch(user.getPhone(), receiver.getContactPhone())) {
                continue;
            }
            if (courierReviewMapper.selectByOrderId(o.getId()) != null) {
                continue;
            }
            n++;
        }
        return n;
    }

    @Override
    public Page<User> getUsersWithFilter(String keyword, String role, String status, Pageable pageable) {
        long total = userMapper.countAdminListUsers(keyword, role, status);
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        List<User> paged = userMapper.selectAdminListUsers(keyword, role, status, offset, limit);
        return new PageImpl<>(paged, pageable, total);
    }

    @Override
    public void updateUserStatus(Long id, String status, String reason) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        Integer userStatus = "active".equals(status) ? 1 : 0;
        user.setStatus(userStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        if ("delivery".equals(user.getRole())) {
            CourierStatus courierStatus = courierStatusMapper.selectById(id);
            if (courierStatus == null) {
                courierStatus = new CourierStatus();
                courierStatus.setCourierId(id);
                courierStatus.setStatus(status);
                courierStatusMapper.insert(courierStatus);
            } else {
                courierStatus.setStatus(status);
                courierStatusMapper.update(courierStatus);
            }
        }
    }

    @Override
    public void updateUserRole(Long id, String role) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setRole(role);
        userMapper.updateById(user);
    }

    @Override
    public Map<String, Object> getUserStatistics() {
        long totalUsers = userMapper.countTotalUsers();
        long userCount = userMapper.countByRoleValue("user");
        long courierCount = userMapper.countByRoleValue("delivery");
        long adminCount = userMapper.countByRoleValue("admin");

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long newUsersToday = userMapper.countUsersCreatedOnOrAfter(todayStart);

        long onlineCouriers = courierStatusMapper.countByStatuses(List.of("online", "idle", "busy"));

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("userCount", userCount);
        stats.put("courierCount", courierCount);
        stats.put("adminCount", adminCount);
        stats.put("newUsersToday", newUsersToday);
        stats.put("onlineCouriers", onlineCouriers);
        
        return stats;
    }

    @Override
    public List<User> getCouriersWithFilter(String status, String keyword) {
        return userMapper.selectByRole("delivery").stream()
                .filter(user -> {
                    if (keyword != null && !keyword.isEmpty()) {
                        String kw = keyword.toLowerCase();
                        boolean match = (user.getUsername() != null && user.getUsername().toLowerCase().contains(kw))
                                || (user.getName() != null && user.getName().toLowerCase().contains(kw))
                                || (user.getPhone() != null && user.getPhone().contains(kw));
                        if (!match) return false;
                    }
                    if (status != null && !status.isEmpty()) {
                        CourierStatus courierStatus = courierStatusMapper.selectById(user.getId());
                        String courierWorkStatus = courierStatus != null ? courierStatus.getStatus() : "offline";
                        if (!status.equals(courierWorkStatus)) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public long countOnlineCouriers() {
        return courierStatusMapper.countByStatuses(List.of("idle", "busy"));
    }

    @Override
    public long countActiveUsersToday() {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        return orderMapper.countDistinctUsersByCreateTimeBetween(start, end);
    }

    @Override
    public void adjustUserBalance(Long userId, BigDecimal amount, String type, String remark) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
    }

    @Override
    public List<User> getCouriersByStatus(String status) {
        if ("pending_review".equals(status)) {
            return userMapper.selectByRoleAndStatus("delivery", 2);
        }
        List<CourierStatus> courierStatuses = courierStatusMapper.selectByStatus(status);
        return courierStatuses.stream()
                .map(cs -> userMapper.selectById(cs.getCourierId()))
                .filter(u -> u != null)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getCourierReviewDetail(Long courierId) {
        User courier = userMapper.selectById(courierId);
        if (courier == null) {
            return null;
        }
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", courier.getId());
        detail.put("username", courier.getUsername());
        detail.put("name", courier.getName());
        detail.put("phone", courier.getPhone());
        detail.put("createdAt", courier.getCreatedAt());
        
        CourierStatus courierStatus = courierStatusMapper.selectById(courierId);
        detail.put("workStatus", courierStatus != null ? courierStatus.getStatus() : "offline");
        
        Double avgRating = courierReviewMapper.selectAverageRatingByCourierId(courierId);
        detail.put("rating", avgRating != null ? BigDecimal.valueOf(avgRating) : new BigDecimal("5.0"));
        
        int reviewCount = courierReviewMapper.selectCountByCourierId(courierId);
        detail.put("reviewCount", reviewCount);
        
        List<Order> orders = orderMapper.selectByCourierId(courierId);
        int completedCount = 0;
        int rejectedCount = 0;
        BigDecimal totalEarnings = BigDecimal.ZERO;
        
        for (Order order : orders) {
            if ("delivered".equals(order.getStatus()) || "completed".equals(order.getStatus())) {
                completedCount++;
                if (order.getActualAmount() != null) {
                    totalEarnings = totalEarnings.add(order.getActualAmount());
                }
            }
            if ("rejected".equals(order.getStatus())) {
                rejectedCount++;
            }
        }
        
        detail.put("completedOrders", completedCount);
        detail.put("rejectedOrders", rejectedCount);
        detail.put("totalEarnings", totalEarnings);
        
        return detail;
    }

    @Override
    @Transactional
    public void approveCourier(Long courierId, String remark) {
        User courier = userMapper.selectById(courierId);
        if (courier == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找该用户的配送员申请
        CourierApplication application = courierApplicationMapper.selectByUserId(courierId);
        if (application == null) {
            throw new RuntimeException("该用户没有配送员申请记录");
        }
        
        if (!"pending".equals(application.getStatus())) {
            throw new RuntimeException("该申请已被处理，无法重复审核");
        }
        
        // 1. 更新用户状态为正常（审核通过）
        userMapper.updateStatus(courierId, 1); // 1-正常

        // 2. 更新申请状态为已通过
        application.setStatus("approved");
        application.setReviewTime(LocalDateTime.now());
        courierApplicationMapper.updateStatus(application.getId(), "approved", null, null);
        
        // 3. 初始化配送员状态
        CourierStatus courierStatus = courierStatusMapper.selectById(courierId);
        if (courierStatus == null) {
            courierStatus = new CourierStatus();
            courierStatus.setCourierId(courierId);
            courierStatus.setStatus("offline");
            courierStatus.setCurrentOrderCount(0);
            courierStatusMapper.insert(courierStatus);
        }
    }

    @Override
    @Transactional
    public void rejectCourier(Long courierId, String reason) {
        User courier = userMapper.selectById(courierId);
        if (courier == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 查找该用户的配送员申请
        CourierApplication application = courierApplicationMapper.selectByUserId(courierId);
        if (application == null) {
            throw new RuntimeException("该用户没有配送员申请记录");
        }
        
        if (!"pending".equals(application.getStatus())) {
            throw new RuntimeException("该申请已被处理，无法重复审核");
        }
        
        // 更新申请状态为已拒绝
        application.setStatus("rejected");
        application.setReviewTime(LocalDateTime.now());
        application.setRejectReason(reason);
        courierApplicationMapper.updateStatus(application.getId(), "rejected", null, reason);
    }

    @Override
    public Map<String, Object> getCourierComplaints(Long courierId, int page, int size) {
        List<Order> orders = orderMapper.selectByCourierId(courierId);
        
        List<Map<String, Object>> complaints = new java.util.ArrayList<>();
        for (Order order : orders) {
            if (order.getIsException() != null && order.getIsException() == 1) {
                Map<String, Object> complaint = new HashMap<>();
                complaint.put("orderId", order.getId());
                complaint.put("orderNo", order.getOrderNo());
                complaint.put("exceptionType", order.getExceptionType());
                complaint.put("createTime", order.getCreateTime());
                complaints.add(complaint);
            }
        }
        
        int total = complaints.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        
        List<Map<String, Object>> pagedList = start < total ? 
            complaints.subList(start, end) : List.of();
        
        Map<String, Object> result = new HashMap<>();
        result.put("items", pagedList);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        
        return result;
    }
    
    @Override
    public void initCourierStatus(Long courierId) {
        CourierStatus courierStatus = courierStatusMapper.selectById(courierId);
        if (courierStatus == null) {
            courierStatus = new CourierStatus();
            courierStatus.setCourierId(courierId);
            courierStatus.setStatus("offline");
            courierStatus.setCurrentOrderCount(0);
            courierStatusMapper.insert(courierStatus);
        }
    }
    
    @Override
    public User updateAvatar(String username, String avatarUrl) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        return user;
    }
}
