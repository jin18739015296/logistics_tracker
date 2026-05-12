package com.logistics.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String username;
    private String password;
    private String role; // admin, delivery, user
    private String name;
    private String phone;
    private String email;
    private String avatar; // 头像URL
    
    @JsonIgnore
    private Integer status; // 0-禁用, 1-正常, 2-审核中（配送员申请审核中）
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @JsonProperty("status")
    public String getStatusString() {
        if (status == null) {
            return "active";
        }
        switch (status) {
            case 0:
                return "disabled";
            case 1:
                return "active";
            case 2:
                return "pending";
            default:
                return "active";
        }
    }
    
    public void setStatusString(String statusStr) {
        if ("disabled".equals(statusStr)) {
            this.status = 0;
        } else if ("active".equals(statusStr)) {
            this.status = 1;
        } else if ("pending".equals(statusStr)) {
            this.status = 2;
        } else {
            this.status = 1;
        }
    }
}
