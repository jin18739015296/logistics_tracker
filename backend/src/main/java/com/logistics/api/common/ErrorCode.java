package com.logistics.api.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0, "成功"),
    
    SYSTEM_ERROR(10000, "系统错误"),
    PARAM_ERROR(10001, "参数错误"),
    UNAUTHORIZED(10002, "未授权"),
    FORBIDDEN(10003, "暂无权限，请联系客服"),
    NOT_FOUND(10004, "信息不存在，请刷新后重试"),
    
    USER_NOT_FOUND(20001, "用户不存在"),
    USER_DISABLED(20002, "用户已禁用"),
    PASSWORD_ERROR(20003, "密码错误"),
    PHONE_EXISTS(20004, "手机号已注册"),
    
    ORDER_NOT_FOUND(30001, "订单不存在"),
    ORDER_STATUS_ERROR(30002, "订单状态不正确"),
    ORDER_ALREADY_PAID(30003, "订单已支付"),
    ORDER_ALREADY_CANCELLED(30004, "订单已取消"),
    ORDER_CANNOT_CANCEL(30005, "订单当前状态不允许取消"),
    ORDER_NOT_OWNER(30006, "非订单所有者"),
    ALREADY_EXISTS(30007, "记录已存在"),
    
    COURIER_NOT_FOUND(40001, "配送员不存在"),
    COURIER_OFFLINE(40002, "配送员暂时离线，正在重新安排"),
    COURIER_BUSY(40003, "配送员忙碌中，请稍后再试"),
    COURIER_NO_AVAILABLE(40004, "附近暂无配送员，正在扩大范围寻找"),
    
    PAYMENT_ERROR(50001, "支付未成功，请重试或更换支付方式"),
    
    ADDRESS_NOT_FOUND(60001, "地址不存在"),
    LOCATION_ERROR(60002, "位置信息异常"),
    BALANCE_NOT_ENOUGH(70001, "钱包余额不足");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
