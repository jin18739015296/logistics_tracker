package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    private Long id;
    private Long userId;
    private String contactName;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer isDefault;
    private String tag; // 地址标签：家/公司/学校/父母/朋友/自定义
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
