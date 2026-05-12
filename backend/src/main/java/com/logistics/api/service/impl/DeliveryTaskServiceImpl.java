package com.logistics.api.service.impl;

import com.logistics.api.mapper.DeliveryTaskMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.model.DeliveryTask;
import com.logistics.api.service.DeliveryTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class DeliveryTaskServiceImpl implements DeliveryTaskService {

    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public List<DeliveryTask> getTasksByCourierId(Long courierId) {
        return deliveryTaskMapper.selectByCourierId(courierId);
    }

    @Override
    public List<DeliveryTask> getTasksByCourierIdAndMonth(Long courierId, LocalDateTime date) {
        YearMonth yearMonth = YearMonth.from(date);
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        return deliveryTaskMapper.selectByCourierIdAndCreateTimeBetween(
                courierId, startOfMonth, endOfMonth);
    }

    @Override
    public DeliveryTask createTask(DeliveryTask task) {
        deliveryTaskMapper.insert(task);
        return task;
    }

    @Override
    public DeliveryTask updateTaskStatus(Long taskId, String status) {
        deliveryTaskMapper.updateStatus(taskId, status);

        // 同步更新订单表状态，避免两表状态不一致
        DeliveryTask task = deliveryTaskMapper.selectById(taskId);
        if (task != null) {
            orderMapper.updateStatus(task.getOrderId(), status);
        }

        if ("awaiting_pickup".equals(status)) {
            deliveryTaskMapper.updateAcceptTime(taskId);
        } else if ("picked_up".equals(status)) {
            deliveryTaskMapper.updatePickupTime(taskId);
        } else if ("delivered".equals(status)) {
            deliveryTaskMapper.updateDeliveryTime(taskId);
        }
        
        return deliveryTaskMapper.selectById(taskId);
    }

    @Override
    public List<DeliveryTask> getPendingTasks() {
        return deliveryTaskMapper.selectByStatus("awaiting_pickup");
    }

    @Override
    public List<DeliveryTask> getTasksByOrderId(Long orderId) {
        DeliveryTask task = deliveryTaskMapper.selectByOrderId(orderId);
        return task != null ? List.of(task) : List.of();
    }

    @Override
    public Map<String, Object> getDeliveryStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTasks", deliveryTaskMapper.countTotalTasks());
        stats.put("pendingTasks", deliveryTaskMapper.countByStatus("awaiting_pickup"));
        stats.put("inProgressTasks", deliveryTaskMapper.countByStatus("picked_up"));
        stats.put("completedTasks", deliveryTaskMapper.countByStatus("delivered"));
        return stats;
    }

    @Override
    public List<Map<String, Object>> getTopPerformers(int limit) {
        return deliveryTaskMapper.selectTopCouriersByDeliveredCount(limit);
    }

    @Override
    public Page<DeliveryTask> getTasksWithFilter(String status, Long courierId, Pageable pageable) {
        List<DeliveryTask> allTasks = deliveryTaskMapper.selectByCourierId(courierId);
        
        List<DeliveryTask> filtered = allTasks.stream()
            .filter(task -> status == null || status.isEmpty() || status.equals(task.getStatus()))
            .collect(Collectors.toList());
        
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<DeliveryTask> pageContent = start < filtered.size() ? filtered.subList(start, end) : List.of();
        
        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Override
    public BigDecimal calculateCourierIncome(Long courierId, LocalDateTime start, LocalDateTime end) {
        BigDecimal sum = orderMapper.sumActualByCourierCreateTimeAndStatus(
                courierId, start, end, "delivered");
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public long countCompletedTasksToday() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        return deliveryTaskMapper.countDeliveredByDeliveryTimeBetween(todayStart, todayEnd);
    }
}
