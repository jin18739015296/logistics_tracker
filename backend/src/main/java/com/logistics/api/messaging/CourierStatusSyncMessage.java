package com.logistics.api.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配送员状态同步消息
 * 用于MQ异步同步配送员状态到数据库
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierStatusSyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配送员ID
     */
    private Long courierId;

    /**
     * 状态: idle/busy/offline
     */
    private String status;

    /**
     * 纬度
     */
    private Double latitude;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 当前订单数
     */
    private Integer currentOrderCount;

    /**
     * 消息类型: ONLINE(上线)/UPDATE_LOCATION(位置更新)/OFFLINE(下线)
     */
    private String messageType;

    /**
     * 消息时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 是否需要插入新记录（首次上线）
     */
    private Boolean isNewRecord;
}
