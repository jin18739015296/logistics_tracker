package com.logistics.api.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    PENDING("pending", "待支付"),
    PAID("paid", "已支付"),
    AWAITING_COURIER_CONFIRM("awaiting_courier_confirm", "配送员待确认"),
    AWAITING_PICKUP("awaiting_pickup", "待揽件"),
    PICKED_UP("picked_up", "已揽件"),
    IN_TRANSIT("in_transit", "运输中"),
    DELIVERED("delivered", "已送达"),
    COMPLETED("completed", "已收货"),
    CANCELLED("cancelled", "已取消"),
    /**
     * 运单异常冻结，需管理员/客服处理。用户 App 不透出「异常」字样，仅作状态机与后台展示。
     */
    EXCEPTION("exception", "异常待处理");

    private final String code;
    private final String description;

    OrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + code);
    }

    public static String getDescByCode(String code) {
        for (OrderStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status.getDescription();
            }
        }
        return "未知状态";
    }

    public static boolean canCancel(String status) {
        return PENDING.getCode().equals(status)
            || PAID.getCode().equals(status);
    }

    public static boolean isValidTransition(String fromCode, String toCode) {
        OrderStatus from = fromCode(fromCode);
        OrderStatus to = fromCode(toCode);
        return from.canTransitionTo(to);
    }

    /**
     * 状态流转规则：
     * 1. pending -> paid (支付完成)
     * 2. paid -> awaiting_courier_confirm (自动分配成功，等待配送员确认)
     * 3. paid -> awaiting_pickup (进入抢单池，配送员抢单成功)
     * 4. awaiting_courier_confirm -> awaiting_pickup (配送员确认接单)
     * 5. awaiting_courier_confirm -> paid (配送员拒绝，回到已支付，重新进入抢单池)
     * 6. awaiting_pickup -> picked_up (揽件)
     * 7. picked_up -> in_transit (开始送往收件地)
     * 8. in_transit -> delivered (确认送达)
     * 9. delivered -> completed (收货者确认收货)
     * 10. 若干状态 -> cancelled
     * 11. 配送中（paid ~ in_transit）-> exception（上报/核验）；exception -> 由管理员恢复至合理状态或取消
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == PAID || target == CANCELLED;
            case PAID -> target == AWAITING_COURIER_CONFIRM
                       || target == AWAITING_PICKUP
                       || target == CANCELLED;
            case AWAITING_COURIER_CONFIRM -> target == AWAITING_PICKUP
                    || target == PAID
                    || target == EXCEPTION;
            case AWAITING_PICKUP -> target == PICKED_UP
                                  || target == EXCEPTION;
            case PICKED_UP -> target == IN_TRANSIT || target == EXCEPTION;
            case IN_TRANSIT -> target == DELIVERED || target == EXCEPTION;
            case DELIVERED -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
            case EXCEPTION -> target == CANCELLED
                    || target == PAID
                    || target == PICKED_UP;
        };
    }
}
