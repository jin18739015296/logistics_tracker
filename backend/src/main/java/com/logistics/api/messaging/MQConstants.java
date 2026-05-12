package com.logistics.api.messaging;

/**
 * MQ 消息常量定义
 * 统一规范Topic命名和消息类型
 */
public final class MQConstants {

    private MQConstants() {
        // 工具类，禁止实例化
    }

    // ============================================
    // Exchange 定义
    // ============================================

    /**
     * 订单相关 Exchange
     */
    public static final String EXCHANGE_ORDER = "logistics.order";

    /**
     * 配送相关 Exchange
     */
    public static final String EXCHANGE_DELIVERY = "logistics.delivery";

    /**
     * 通知相关 Exchange
     */
    public static final String EXCHANGE_NOTIFICATION = "logistics.notification";

    /**
     * 物流轨迹 Exchange
     */
    public static final String EXCHANGE_TRACKING = "logistics.tracking";

    /**
     * 数据同步 Exchange
     */
    public static final String EXCHANGE_DATA_SYNC = "logistics.data.sync";

    // ============================================
    // Queue 定义 - 订单相关
    // ============================================

    /**
     * 支付成功队列
     */
    public static final String QUEUE_PAYMENT_SUCCESS = "order.payment.success";

    /**
     * 订单创建队列
     */
    public static final String QUEUE_ORDER_CREATED = "order.created";

    /**
     * 订单取消队列
     */
    public static final String QUEUE_ORDER_CANCELLED = "order.cancelled";

    /**
     * 订单完成队列
     */
    public static final String QUEUE_ORDER_COMPLETED = "order.completed";

    // ============================================
    // Queue 定义 - 配送相关
    // ============================================

    /**
     * 订单分配队列
     */
    public static final String QUEUE_ORDER_DISPATCH = "delivery.order.dispatch";

    /**
     * 配送员位置更新队列
     */
    public static final String QUEUE_COURIER_LOCATION = "delivery.courier.location";

    /**
     * 配送状态变更队列
     */
    public static final String QUEUE_DELIVERY_STATUS = "delivery.status.changed";

    // ============================================
    // Queue 定义 - 通知相关
    // ============================================

    /**
     * 用户通知队列
     */
    public static final String QUEUE_USER_NOTIFICATION = "notification.user";

    /**
     * 配送员通知队列
     */
    public static final String QUEUE_COURIER_NOTIFICATION = "notification.courier";



    // ============================================
    // Queue 定义 - 轨迹相关
    // ============================================

    /**
     * 轨迹上传队列（低频）
     */
    public static final String QUEUE_TRACK_UPLOAD = "tracking.upload";

    /**
     * 轨迹批量处理队列
     */
    public static final String QUEUE_TRACK_BATCH = "tracking.batch";

    // ============================================
    // Routing Key 模式定义
    // ============================================

    /**
     * 订单事件路由键模式
     * 格式: order.{eventType}.{orderId}
     */
    public static final String ROUTING_KEY_ORDER_PATTERN = "order.%s.%d";

    /**
     * 配送事件路由键模式
     * 格式: delivery.{eventType}.{courierId}
     */
    public static final String ROUTING_KEY_DELIVERY_PATTERN = "delivery.%s.%d";

    /**
     * 用户通知路由键模式
     * 格式: notification.user.{userId}
     */
    public static final String ROUTING_KEY_USER_NOTIFICATION_PATTERN = "notification.user.%d";

    /**
     * 配送员通知路由键模式
     * 格式: notification.courier.{courierId}
     */
    public static final String ROUTING_KEY_COURIER_NOTIFICATION_PATTERN = "notification.courier.%d";

    // ============================================
    // 消息类型定义 - 订单相关
    // ============================================

    /**
     * 订单支付成功
     */
    public static final String MSG_TYPE_ORDER_PAID = "ORDER_PAID";

    /**
     * 订单已创建
     */
    public static final String MSG_TYPE_ORDER_CREATED = "ORDER_CREATED";

    /**
     * 订单已取消
     */
    public static final String MSG_TYPE_ORDER_CANCELLED = "ORDER_CANCELLED";

    /**
     * 订单已完成
     */
    public static final String MSG_TYPE_ORDER_COMPLETED = "ORDER_COMPLETED";

    /**
     * 订单已分配
     */
    public static final String MSG_TYPE_ORDER_ASSIGNED = "ORDER_ASSIGNED";

    /**
     * 订单状态变更
     */
    public static final String MSG_TYPE_ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";

    // ============================================
    // 消息类型定义 - 配送相关
    // ============================================

    /**
     * 配送员上线
     */
    public static final String MSG_TYPE_COURIER_ONLINE = "COURIER_ONLINE";

    /**
     * 配送员下线
     */
    public static final String MSG_TYPE_COURIER_OFFLINE = "COURIER_OFFLINE";

    /**
     * 配送员位置更新
     */
    public static final String MSG_TYPE_COURIER_LOCATION_UPDATED = "COURIER_LOCATION_UPDATED";

    /**
     * 配送员状态变更（上线/下线/位置更新等）
     */
    public static final String MSG_TYPE_COURIER_STATUS = "COURIER_STATUS";

    /**
     * 配送任务已分配
     */
    public static final String MSG_TYPE_DELIVERY_ASSIGNED = "DELIVERY_ASSIGNED";

    /**
     * 配送状态变更
     */
    public static final String MSG_TYPE_DELIVERY_STATUS_CHANGED = "DELIVERY_STATUS_CHANGED";

    /**
     * 快件已揽收
     */
    public static final String MSG_TYPE_PACKAGE_PICKED_UP = "PACKAGE_PICKED_UP";

    /**
     * 快件已送达
     */
    public static final String MSG_TYPE_PACKAGE_DELIVERED = "PACKAGE_DELIVERED";

    // ============================================
    // 消息类型定义 - 通知相关
    // ============================================

    /**
     * 新订单通知（配送员）
     */
    public static final String MSG_TYPE_NOTIFY_NEW_ORDER = "NOTIFY_NEW_ORDER";

    /**
     * 订单状态通知（用户）
     */
    public static final String MSG_TYPE_NOTIFY_ORDER_STATUS = "NOTIFY_ORDER_STATUS";



    // ============================================
    // 消息类型定义 - 轨迹相关
    // ============================================

    /**
     * 轨迹上传
     */
    public static final String MSG_TYPE_TRACK_UPLOAD = "TRACK_UPLOAD";

    /**
     * 轨迹批量上传
     */
    public static final String MSG_TYPE_TRACK_BATCH_UPLOAD = "TRACK_BATCH_UPLOAD";

    // ============================================
    // 消息优先级
    // ============================================

    /**
     * 高优先级（紧急通知、报警）
     */
    public static final int PRIORITY_HIGH = 10;

    /**
     * 普通优先级（订单状态变更）
     */
    public static final int PRIORITY_NORMAL = 5;

    /**
     * 低优先级（日志、统计）
     */
    public static final int PRIORITY_LOW = 1;

    // ============================================
    // 消息头部Key定义
    // ============================================

    /**
     * 消息类型头部Key
     */
    public static final String HEADER_MSG_TYPE = "X-Message-Type";

    /**
     * 消息版本头部Key
     */
    public static final String HEADER_MSG_VERSION = "X-Message-Version";

    /**
     * 消息优先级头部Key
     */
    public static final String HEADER_MSG_PRIORITY = "X-Message-Priority";

    /**
     * 消息发送时间头部Key
     */
    public static final String HEADER_MSG_TIMESTAMP = "X-Message-Timestamp";

    /**
     * 消息发送者头部Key
     */
    public static final String HEADER_MSG_SENDER = "X-Message-Sender";

    /**
     * 消息版本号
     */
    public static final String MESSAGE_VERSION = "1.0";
}
