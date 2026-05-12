package com.logistics.api.enums;

import lombok.Getter;

import java.util.Collections;
import java.util.Set;

/**
 * 运单异常分类（写入 orders.exception_type）。
 * <p>
 * 设计原则：根据物品所在位置和异常场景决定恢复方式。
 * <ul>
 *   <li>物品未交接（在寄件人处）：可改派（reassign）或取消（cancel）</li>
 *   <li>物品已交接（在配送员手上）：只能继续配送（continue）或取消（cancel）</li>
 *   <li>灭失/损毁/不可抗力：不可恢复，只能取消并赔偿</li>
 * </ul>
 * <p>
 * 恢复动作说明：
 * <ul>
 *   <li>reassign — 改派：物品还在寄件人处，清除原配送员，回到 paid 重新分配</li>
 *   <li>continue — 继续配送：物品已在配送员手上，回到异常前状态继续完成</li>
 *   <li>cancel — 取消订单：关闭订单，自动退款到用户钱包</li>
 * </ul>
 */
@Getter
public enum OrderExceptionType {

    // ========== 可恢复：物品未交接（在寄件人处）==========

    /** 配送员长时间未确认接单或失联（物品还在寄件人处，可改派） */
    COURIER_NOT_RESPONDING("courier_not_responding", "配送员未响应",
            "配送员未确认接单或失联。物品仍在寄件人处，建议改派或取消订单。",
            true,
            Set.of("reassign", "cancel")),

    /** 待揽件时遇到问题（物品还在寄件人处，可重新分配或取消） */
    PICKUP_PENDING_ISSUE("pickup_pending_issue", "揽件受阻",
            "待揽件时遇到问题。物品仍在寄件人处，建议重新分配配送员或取消订单。",
            true,
            Set.of("reassign", "cancel")),

    // ========== 可恢复：物品已交接（在配送员手上）==========

    /** 上门揽件时发现问题（物品不符、包装破损、地址错误等） */
    PICKUP_ISSUE("pickup_issue", "揽件异常",
            "上门揽件时发现问题。物品已在配送员手上，核实后可继续配送，或取消退回寄件人。",
            true,
            Set.of("continue", "cancel")),

    /** 运输途中遇到问题（车辆故障、路况、临时封控等） */
    TRANSIT_ISSUE("transit_issue", "运输异常",
            "运输途中遇到问题。物品已在配送员手上，请关闭订单并为用户办理退款/赔付。",
            false,
            Set.of("cancel")),

    // ========== 不可恢复 ==========

    /** 快件灭失/无法找回 */
    GOODS_LOST("goods_lost", "快件灭失",
            "快件灭失或无法找回。不可恢复，请关闭订单并为用户办理退款/赔付。",
            false,
            Set.of("cancel")),

    /** 货品损毁无法交接 */
    GOODS_DAMAGED("goods_damaged", "货品损毁",
            "货品在运输过程中损毁，无法正常交接。不可恢复，请关闭订单并为用户办理退款/赔付。",
            false,
            Set.of("cancel")),

    /** 不可抗力（自然灾害、政策封控等） */
    FORCE_MAJEURE("force_majeure", "不可抗力",
            "因不可抗力无法完成配送。不可恢复，请关闭订单并协商退款或免单。",
            false,
            Set.of("cancel"));

    private final String code;
    private final String title;
    private final String adminHint;
    /** 是否允许恢复（false 则仅允许取消关闭） */
    private final boolean recoverable;
    /** 允许的恢复动作集合：reassign-改派 / continue-继续配送 / cancel-取消订单 */
    private final Set<String> allowedActions;

    OrderExceptionType(String code, String title, String adminHint, boolean recoverable, Set<String> allowedActions) {
        this.code = code;
        this.title = title;
        this.adminHint = adminHint;
        this.recoverable = recoverable;
        this.allowedActions = allowedActions != null ? Collections.unmodifiableSet(allowedActions) : Collections.emptySet();
    }

    public static OrderExceptionType fromCodeOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (OrderExceptionType t : values()) {
            if (t.code.equals(code.trim())) {
                return t;
            }
        }
        return null;
    }

    /**
     * 是否允许「解除异常」回到配送状态机。未匹配到枚举时默认 true，避免历史自定义类型把订单卡死。
     */
    public static boolean isRecoverableByExceptionTypeCode(String exceptionTypeCode) {
        if (exceptionTypeCode == null || exceptionTypeCode.isBlank()) {
            return true;
        }
        OrderExceptionType t = fromCodeOrNull(exceptionTypeCode);
        if (t == null) {
            return true;
        }
        return t.isRecoverable();
    }

    /**
     * 检查指定动作是否被允许。
     */
    public static boolean isActionAllowed(String exceptionTypeCode, String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        String a = action.trim().toLowerCase();
        if ("cancel".equals(a)) {
            return true; // 所有异常都允许取消
        }
        OrderExceptionType t = fromCodeOrNull(exceptionTypeCode);
        if (t == null) {
            return true; // 未知类型默认允许（兼容历史数据）
        }
        return t.allowedActions.contains(a);
    }

    /**
     * 获取指定异常类型允许的恢复动作列表。
     */
    public static Set<String> getAllowedActions(String exceptionTypeCode) {
        OrderExceptionType t = fromCodeOrNull(exceptionTypeCode);
        if (t == null) {
            return Set.of("reassign", "continue", "cancel");
        }
        return t.allowedActions;
    }
}
