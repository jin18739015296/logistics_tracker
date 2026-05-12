package com.logistics.api.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING("pending", "待支付"),
    PROCESSING("processing", "支付中"),
    SUCCESS("success", "支付成功"),
    FAILED("failed", "支付失败"),
    TIMEOUT("timeout", "支付超时"),
    REFUNDED("refunded", "已退款");

    private final String code;
    private final String description;

    PaymentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static PaymentStatus fromCode(String code) {
        for (PaymentStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment status: " + code);
    }
}
