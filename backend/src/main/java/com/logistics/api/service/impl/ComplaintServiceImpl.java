package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.mapper.ComplaintMapper;
import com.logistics.api.mapper.DeliveryTaskMapper;
import com.logistics.api.mapper.OrderAddressMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.mapper.UserMapper;
import com.logistics.api.model.Complaint;
import com.logistics.api.model.DeliveryTask;
import com.logistics.api.model.Order;
import com.logistics.api.model.OrderAddress;
import com.logistics.api.model.User;
import com.logistics.api.service.ComplaintService;
import com.logistics.api.service.NotificationService;
import com.logistics.api.service.support.NotificationCopy;
import com.logistics.api.service.support.OrderViewerPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ComplaintServiceImpl implements ComplaintService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    @Autowired
    private ComplaintMapper complaintMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderAddressMapper orderAddressMapper;

    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired
    private NotificationService notificationService;

    private String generateComplaintNo() {
        return "CP" + LocalDateTime.now().format(NO_TIME) + String.format("%04d", RANDOM.nextInt(10000));
    }

    @Override
    public Complaint submitComplaint(Complaint complaint) {
        Order order = null;
        if (complaint.getOrderId() != null) {
            order = orderMapper.selectById(complaint.getOrderId());
        } else if (StringUtils.hasText(complaint.getOrderNo())) {
            order = orderMapper.selectByOrderNo(complaint.getOrderNo().trim());
        }
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }

        User complainant = userMapper.selectById(complaint.getUserId());
        if (complainant == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        assertUserCanComplaintOnOrder(complainant, order);

        // 检查该用户是否已对该订单投诉过
        Complaint existing = complaintMapper.selectByOrderIdAndUserId(order.getId(), complainant.getId());
        if (existing != null) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "您已对该订单提交过投诉");
        }

        String st = order.getStatus();
        if (!"delivered".equals(st) && !"completed".equals(st)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "当前订单状态不可投诉");
        }

        if (complaint.getCourierId() != null && order.getCourierId() != null
                && !complaint.getCourierId().equals(order.getCourierId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投诉对象与订单配送员不一致");
        }

        complaint.setOrderId(order.getId());
        complaint.setOrderNo(order.getOrderNo());
        complaint.setComplaintNo(generateComplaintNo());
        complaint.setStatus("pending");
        if (!StringUtils.hasText(complaint.getComplainantNotifyType())) {
            complaint.setComplainantNotifyType("user");
        }
        complaintMapper.insert(complaint);

        notificationService.sendNotification(
                complaint.getUserId(),
                complaint.getComplainantNotifyType(),
                "system",
                NotificationCopy.complaintSubmittedUserTitle(),
                NotificationCopy.complaintSubmittedUserBody()
        );

        // 投诉受理阶段不向骑手发站内信：避免与管理员工单重复、且在核实前打扰骑手；骑手在「处理完成」时会收到结论通知

        notificationService.sendNotification(
                1L,
                "admin",
                "system",
                NotificationCopy.complaintSubmittedAdminTitle(),
                NotificationCopy.complaintSubmittedAdminBody(order.getOrderNo())
        );

        log.info("提交投诉成功, orderNo: {}, userId: {}", order.getOrderNo(), complaint.getUserId());
        return complaint;
    }

    private void assertUserCanComplaintOnOrder(User complainant, Order order) {
        if (complainant.getId().equals(order.getUserId())) {
            return;
        }
        OrderAddress recv = orderAddressMapper.selectByOrderIdAndType(order.getId(), "receiver");
        if (recv == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权投诉该订单");
        }
        if (!OrderViewerPolicy.phonesMatch(complainant.getPhone(), recv.getContactPhone())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权投诉该订单");
        }
        DeliveryTask task = deliveryTaskMapper.selectByOrderId(order.getId());
        if (!OrderViewerPolicy.receiverCanViewOrder(order, task)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权投诉该订单");
        }
    }

    @Override
    public Complaint getComplaintById(Long id) {
        Complaint complaint = complaintMapper.selectById(id);
        if (complaint == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "投诉不存在");
        }
        enrichOrderNumber(complaint);
        return complaint;
    }

    @Override
    public List<Complaint> getComplaintsByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return List.of();
        }
        Order order = orderMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            return List.of();
        }
        List<Complaint> list = complaintMapper.selectByOrderId(order.getId());
        enrichOrderNumbers(list);
        return list;
    }

    @Override
    public List<Complaint> getComplaintsByOrderId(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        List<Complaint> list = complaintMapper.selectByOrderId(orderId);
        enrichOrderNumbers(list);
        return list;
    }

    @Override
    public Complaint getComplaintByOrderIdAndUserId(Long orderId, Long userId) {
        if (orderId == null || userId == null) {
            return null;
        }
        Complaint complaint = complaintMapper.selectByOrderIdAndUserId(orderId, userId);
        if (complaint != null) {
            enrichOrderNumber(complaint);
        }
        return complaint;
    }

    @Override
    public List<Complaint> getMyComplaints(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Complaint> list = complaintMapper.selectByUserIdPaged(userId, size, offset);
        enrichOrderNumbers(list);
        return list;
    }

    @Override
    public List<Complaint> getComplaintsAgainstMe(Long userId, String userType, int page, int size) {
        if (!"courier".equals(userType)) {
            return List.of();
        }
        int offset = (page - 1) * size;
        List<Complaint> list = complaintMapper.selectByCourierIdPaged(userId, size, offset);
        enrichOrderNumbers(list);
        return list;
    }

    @Override
    public List<Complaint> getPendingComplaints(int page, int size) {
        int offset = (page - 1) * size;
        List<Complaint> list = complaintMapper.selectByStatus("pending", size, offset);
        enrichOrderNumbers(list);
        return list;
    }

    @Override
    public List<Complaint> getAllComplaints(int page, int size) {
        int offset = (page - 1) * size;
        List<Complaint> list = complaintMapper.selectAll(size, offset);
        enrichOrderNumbers(list);
        return list;
    }

    @Override
    public void handleComplaint(Long complaintId, String status, String result, String resultContent, Long handlerId, String handlerName) {
        Complaint complaint = getComplaintById(complaintId);

        complaint.setStatus(status);
        complaint.setResult(result);
        complaint.setResultContent(resultContent);
        complaint.setHandlerId(handlerId);
        complaint.setHandlerName(handlerName);

        complaintMapper.updateHandleResult(complaint);

        String notifyType = resolveComplainantNotifyType(complaint.getUserId());
        String orderLabel = complaint.getOrderNo() != null ? complaint.getOrderNo() : "";

        boolean resolved = "resolved".equals(status);
        String title = NotificationCopy.complaintResultTitleForComplainant(resolved);
        String resultText = resolved ? NotificationCopy.complaintResultLabelEstablished()
                : NotificationCopy.complaintResultLabelNotEstablished();
        StringBuilder complainantMsg = new StringBuilder();
        complainantMsg.append(NotificationCopy.complaintResultBodyIntroForComplainant());
        complainantMsg.append("【投诉主题】").append(complaint.getTitle()).append("\n");
        complainantMsg.append("【处理结论】").append(resultText).append("\n");
        if (result != null && !result.isBlank()) {
            complainantMsg.append("【处理结果】").append(result.trim()).append("\n");
        }
        if (resultContent != null && !resultContent.isBlank()) {
            complainantMsg.append("【详细说明】").append(resultContent.trim()).append("\n");
        }
        complainantMsg.append(NotificationCopy.complaintResultFooterForComplainant());
        sendComplaintResultNotifySafe(
                complaint.getUserId(),
                notifyType,
                title,
                complainantMsg.toString(),
                complaint.getOrderId(),
                complaint.getId()
        );

        if (complaint.getCourierId() != null) {
            String respondentTitle = NotificationCopy.complaintResultTitleForCourier(resolved);
            StringBuilder respondentContent = new StringBuilder();
            respondentContent.append(NotificationCopy.complaintResultIntroForCourier());
            respondentContent.append("【订单号】").append(orderLabel).append("\n");
            if (resolved) {
                respondentContent.append("【结论】投诉成立，请对照平台规则与服务标准自查履约过程。\n");
            } else {
                respondentContent.append("【结论】投诉未成立。\n");
            }
            if (result != null && !result.isBlank()) {
                respondentContent.append("【处理结果】").append(result.trim()).append("\n");
            }
            if (resultContent != null && !resultContent.isBlank()) {
                respondentContent.append("【补充说明】").append(resultContent.trim()).append("\n");
            }
            respondentContent.append(NotificationCopy.complaintResultFooterForCourier());
            sendComplaintResultNotifySafe(
                    complaint.getCourierId(),
                    "courier",
                    respondentTitle,
                    respondentContent.toString(),
                    complaint.getOrderId(),
                    complaint.getId()
            );
        }

        log.info("处理投诉成功, complaintId: {}, status: {}", complaintId, status);
    }



    @Override
    public int getPendingCount() {
        return complaintMapper.countByStatus("pending");
    }

    @Override
    public void cancelComplaint(Long complaintId, Long userId) {
        Complaint complaint = complaintMapper.selectById(complaintId);
        if (complaint == null) {
            throw new RuntimeException("投诉不存在");
        }
        if (!complaint.getUserId().equals(userId)) {
            throw new RuntimeException("只能撤销自己发起的投诉");
        }
        if (!"pending".equals(complaint.getStatus())) {
            throw new RuntimeException("只有待处理状态的投诉才能撤销");
        }
        complaint.setStatus("cancelled");
        complaint.setUpdateTime(LocalDateTime.now());
        complaintMapper.updateById(complaint);
        log.info("撤销投诉成功, complaintId: {}", complaintId);
    }

    private void enrichOrderNumbers(List<Complaint> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream()
                .map(Complaint::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        List<Order> orders = orderMapper.selectByIds(ids);
        Map<Long, String> map = orders.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Order::getId, Order::getOrderNo, (a, b) -> a));
        for (Complaint c : list) {
            if (c.getOrderId() != null) {
                c.setOrderNo(map.get(c.getOrderId()));
            }
        }
    }

    private void enrichOrderNumber(Complaint c) {
        if (c == null || c.getOrderId() == null) {
            return;
        }
        Order o = orderMapper.selectById(c.getOrderId());
        if (o != null) {
            c.setOrderNo(o.getOrderNo());
        }
    }

    private void sendComplaintResultNotifySafe(Long userId, String userType, String title, String content,
                                               Long orderId, Long complaintId) {
        try {
            notificationService.sendComplaintResultNotification(userId, userType, title, content, orderId, complaintId);
        } catch (Exception e) {
            log.warn("投诉结果通知发送失败 userId={} complaintId={} title={}", userId, complaintId, title, e);
        }
    }

    private String resolveComplainantNotifyType(Long userId) {
        if (userId == null) {
            return "user";
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return "user";
        }
        if ("delivery".equals(u.getRole())) {
            return "courier";
        }
        if ("admin".equals(u.getRole())) {
            return "admin";
        }
        return "user";
    }
}
