package com.logistics.api.service.support;

import com.logistics.api.enums.OrderStatus;
import com.logistics.api.model.DeliveryTask;
import com.logistics.api.model.Order;

/**
 * 下单人（寄件）与收件人视角：列表/详情可见范围、确认收货与评价主体。
 */
public final class OrderViewerPolicy {

    private OrderViewerPolicy() {
    }

    /**
     * 去掉首尾空白与常见分隔符，便于与订单里填写的收件电话比对。
     */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String s = phone.trim();
        return s.replaceAll("[\\s\\-–—]", "");
    }

    public static boolean phonesMatch(String a, String b) {
        String na = normalizePhone(a);
        String nb = normalizePhone(b);
        return !na.isEmpty() && na.equals(nb);
    }

    /** 收件人在「揽件完成」之前不应看到订单（取消单除非已完成揽件） */
    public static boolean isPrePickupHiddenForReceiver(String status) {
        if (status == null) {
            return true;
        }
        return OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.PAID.getCode().equals(status)
                || OrderStatus.AWAITING_COURIER_CONFIRM.getCode().equals(status)
                || OrderStatus.AWAITING_PICKUP.getCode().equals(status);
    }

    public static boolean receiverCanViewOrder(Order order, DeliveryTask task) {
        if (order == null) {
            return false;
        }
        String st = order.getStatus();
        if (OrderStatus.CANCELLED.getCode().equals(st)) {
            return task != null && task.getPickupTime() != null;
        }
        return !isPrePickupHiddenForReceiver(st);
    }
}
