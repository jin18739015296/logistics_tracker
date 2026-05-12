package com.logistics.api.service;

import com.logistics.api.model.Complaint;

import java.util.List;

public interface ComplaintService {

    /**
     * 提交投诉
     */
    Complaint submitComplaint(Complaint complaint);

    /**
     * 获取投诉详情
     */
    Complaint getComplaintById(Long id);

    List<Complaint> getComplaintsByOrderNo(String orderNo);

    List<Complaint> getComplaintsByOrderId(Long orderId);

    /**
     * 查询指定用户对指定订单的投诉
     */
    Complaint getComplaintByOrderIdAndUserId(Long orderId, Long userId);

    /**
     * 获取我发起的投诉
     */
    List<Complaint> getMyComplaints(Long userId, int page, int size);

    /**
     * 获取针对我的投诉
     */
    List<Complaint> getComplaintsAgainstMe(Long userId, String userType, int page, int size);

    /**
     * 获取待处理投诉列表（管理员）
     */
    List<Complaint> getPendingComplaints(int page, int size);

    /**
     * 获取所有投诉列表（管理员）
     */
    List<Complaint> getAllComplaints(int page, int size);

    /**
     * 处理投诉（管理员）
     */
    void handleComplaint(Long complaintId, String status, String result, String resultContent, Long handlerId, String handlerName);

    /**
     * 获取待处理投诉数量
     */
    int getPendingCount();

    /**
     * 撤销投诉（用户）
     */
    void cancelComplaint(Long complaintId, Long userId);
}
