package com.logistics.api.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Complaint {
    private Long id;
    private String complaintNo;
    /** 关联订单主键（API 正常返回；用户端界面请用 {@link #orderNo} 展示为「订单号」） */
    private Long orderId;
    /** 订单业务单号（列表/详情便于直接展示，与 order 表一致） */
    private String orderNo;
    /** 投诉人用户 ID，对应表 complaint.user_id */
    private Long userId;
    /**
     * 被投诉配送员 ID，对应表 complaint.courier_id。
     * API 请求体可传 respondentId（与 App 一致）。
     */
    @JsonAlias({ "respondentId", "courierId" })
    private Long courierId;

    private String type;
    private String title;
    private String content;
    /** 对应表 complaint.images（证据 JSON 等） */
    private String images;
    private String status;
    private String result;
    private String resultContent;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime handleTime;

    /**
     * 提交投诉后用于站内通知路由（user/courier/admin），不落库。
     */
    @JsonIgnore
    private String complainantNotifyType;

    @JsonProperty("complainantId")
    public Long getComplainantId() {
        return userId;
    }

    @JsonProperty("respondentId")
    public Long getRespondentId() {
        return courierId;
    }

    @JsonProperty("evidenceUrls")
    public String getEvidenceUrls() {
        return images;
    }
}
