package com.logistics.api.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Notification {
    private Long id;
    private Long userId;
    private String userType;
    private String type;
    private String title;
    private String content;
    private Long relatedId;
    private String relatedType;
    private Integer isRead;
    private LocalDateTime readTime;
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
