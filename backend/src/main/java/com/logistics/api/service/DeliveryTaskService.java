package com.logistics.api.service;

import com.logistics.api.model.DeliveryTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface DeliveryTaskService {

    List<DeliveryTask> getTasksByCourierId(Long courierId);

    List<DeliveryTask> getTasksByCourierIdAndMonth(Long courierId, LocalDateTime month);

    DeliveryTask createTask(DeliveryTask task);

    DeliveryTask updateTaskStatus(Long taskId, String status);

    List<DeliveryTask> getPendingTasks();

    List<DeliveryTask> getTasksByOrderId(Long orderId);

    Map<String, Object> getDeliveryStatistics();

    List<Map<String, Object>> getTopPerformers(int limit);

    Page<DeliveryTask> getTasksWithFilter(String status, Long courierId, Pageable pageable);

    BigDecimal calculateCourierIncome(Long courierId, LocalDateTime start, LocalDateTime end);

    long countCompletedTasksToday();
}
