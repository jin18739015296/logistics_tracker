package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsType {
    private Integer id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private Integer sortOrder;
    private Integer isActive;
    private LocalDateTime createTime;
}
