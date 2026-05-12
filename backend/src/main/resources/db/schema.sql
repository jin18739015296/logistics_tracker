-- ============================================
-- 物流管理平台数据库 Schema (最终版)
-- 说明：此版本为系统最终数据库结构
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. 用户表 users
-- ============================================
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    role VARCHAR(20) NOT NULL COMMENT '角色: user/courier/admin',
    
    name VARCHAR(50) COMMENT '真实姓名',
    phone VARCHAR(20) UNIQUE COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    avatar VARCHAR(255) COMMENT '头像URL',
    
    status TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_username (username),
    INDEX idx_role (role),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 2. 地址表 addresses
-- ============================================
CREATE TABLE addresses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '地址ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    
    contact_name VARCHAR(50) COMMENT '联系人姓名',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    
    province VARCHAR(50) COMMENT '省',
    city VARCHAR(50) COMMENT '市',
    district VARCHAR(50) COMMENT '区/县',
    detail_address VARCHAR(255) COMMENT '详细地址',
    
    latitude DECIMAL(10,8) COMMENT '纬度',
    longitude DECIMAL(11,8) COMMENT '经度',
    
    is_default TINYINT DEFAULT 0 COMMENT '是否默认地址',
    tag VARCHAR(20) COMMENT '地址标签: 家/公司/学校',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user (user_id),
    INDEX idx_geo (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户地址表';

-- ============================================
-- 3. 物品类型表 goods_types
-- ============================================
CREATE TABLE goods_types (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '类型ID',
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '类型编码',
    name VARCHAR(100) NOT NULL COMMENT '类型名称',
    description VARCHAR(255) COMMENT '类型描述',
    icon VARCHAR(100) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用: 1启用 0禁用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品类型表';

-- ============================================
-- 4. 订单表 orders
-- ============================================
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    order_no VARCHAR(32) UNIQUE NOT NULL COMMENT '订单号',
    
    user_id BIGINT NOT NULL COMMENT '下单用户ID',
    courier_id BIGINT COMMENT '配送员ID',
    
    goods_type_id INT COMMENT '物品类型ID',
    goods_description VARCHAR(255) COMMENT '物品描述',
    goods_weight DECIMAL(8,2) DEFAULT 1.00 COMMENT '物品重量(kg)',
    
    status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '订单状态(OrderStatus.code，最长如 awaiting_courier_confirm)',
    order_type VARCHAR(20) DEFAULT 'standard' COMMENT '订单类型: standard/express/same_day',
    
    total_amount DECIMAL(10,2) COMMENT '订单金额',
    actual_amount DECIMAL(10,2) COMMENT '实付金额',
    pay_type VARCHAR(20) DEFAULT 'prepay' COMMENT '支付方式: prepay/cod',
    
    dispatch_type VARCHAR(20) COMMENT '分配方式: auto/grab',
    dispatch_time TIMESTAMP NULL COMMENT '分配时间',
    is_exception TINYINT DEFAULT 0 COMMENT '是否异常订单',
    exception_type VARCHAR(30) COMMENT '异常类型',
    
    cancel_reason VARCHAR(255) COMMENT '取消原因',
    cancel_time TIMESTAMP NULL COMMENT '取消时间',
    
    remark VARCHAR(500) COMMENT '订单备注',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user (user_id),
    INDEX idx_courier (courier_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    INDEX idx_user_status (user_id, status),
    INDEX idx_exception (is_exception)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================
-- 5. 订单地址表 order_addresses
-- ============================================
CREATE TABLE order_addresses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '地址ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    type VARCHAR(10) NOT NULL COMMENT '类型: sender/receiver',
    
    contact_name VARCHAR(50) COMMENT '联系人姓名',
    contact_phone VARCHAR(20) COMMENT '联系人电话',
    
    province VARCHAR(50) COMMENT '省',
    city VARCHAR(50) COMMENT '市',
    district VARCHAR(50) COMMENT '区/县',
    detail_address VARCHAR(255) COMMENT '详细地址',
    
    latitude DECIMAL(10,8) COMMENT '纬度',
    longitude DECIMAL(11,8) COMMENT '经度',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_order (order_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单地址表';

-- ============================================
-- 6. 配送任务表 delivery_tasks
-- ============================================
CREATE TABLE delivery_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    courier_id BIGINT NOT NULL COMMENT '配送员ID',
    
    status VARCHAR(32) COMMENT '任务状态，与 orders.status / OrderStatus.code 一致',
    
    accept_time TIMESTAMP NULL COMMENT '接单时间',
    pickup_time TIMESTAMP NULL COMMENT '取件时间',
    delivery_time TIMESTAMP NULL COMMENT '送达时间',
    
    estimated_distance DECIMAL(8,2) COMMENT '预估距离(km)',
    estimated_duration INT COMMENT '预估时长(分钟)',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_order (order_id),
    INDEX idx_courier (courier_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送任务表';

-- ============================================
-- 7. 配送员申请表 courier_applications
-- ============================================
CREATE TABLE courier_applications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID',
    user_id BIGINT NOT NULL COMMENT '关联用户ID',
    
    status VARCHAR(20) DEFAULT 'pending' COMMENT '审核状态: pending-待审核, approved-已通过, rejected-已拒绝',
    
    -- 身份认证信息
    id_card_no VARCHAR(18) NOT NULL COMMENT '身份证号',
    id_card_front VARCHAR(500) COMMENT '身份证正面照URL',
    id_card_back VARCHAR(500) COMMENT '身份证背面照URL',
    id_card_hold VARCHAR(500) COMMENT '手持身份证照片URL',
    
    -- 车辆信息
    vehicle_type VARCHAR(20) NOT NULL COMMENT '车辆类型: electric_bike/motorcycle/small_truck/medium_truck/large_truck',
    vehicle_plate VARCHAR(20) COMMENT '车牌号（汽车/摩托车必填）',
    vehicle_photo VARCHAR(500) COMMENT '车辆照片URL',
    
    -- 驾驶证信息（驾驶汽车/摩托车必填）
    driver_license_no VARCHAR(50) COMMENT '驾驶证号',
    driver_license_photo VARCHAR(500) COMMENT '驾驶证照片URL',
    
    -- 紧急联系人信息
    emergency_contact_name VARCHAR(50) COMMENT '紧急联系人姓名',
    emergency_contact_phone VARCHAR(20) COMMENT '紧急联系人电话',
    emergency_contact_relation VARCHAR(20) COMMENT '与紧急联系人关系',
    
    -- 工作相关
    work_city VARCHAR(50) COMMENT '工作城市',
    work_district VARCHAR(100) COMMENT '工作区域',
    
    -- 审核信息
    review_time TIMESTAMP NULL COMMENT '审核时间',
    reviewer_id BIGINT COMMENT '审核人ID',
    reject_reason VARCHAR(500) COMMENT '拒绝原因',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送员申请表';

-- ============================================
-- 8. 配送员状态表 courier_status
-- ============================================
CREATE TABLE courier_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    
    courier_id BIGINT NOT NULL COMMENT '配送员ID',
    status VARCHAR(20) COMMENT '状态: idle-空闲/busy-忙碌/offline-离线',
    
    current_lat DECIMAL(10,8) COMMENT '当前纬度',
    current_lng DECIMAL(11,8) COMMENT '当前经度',
    location_updated_at TIMESTAMP NULL COMMENT '位置更新时间',
    
    current_order_count INT DEFAULT 0 COMMENT '当前订单数',
    today_order_count INT DEFAULT 0 COMMENT '今日接单数',
    today_income DECIMAL(10,2) DEFAULT 0 COMMENT '今日收入',
    
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE INDEX uk_courier (courier_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送员状态表';

-- ============================================
-- 9. 订单轨迹表 order_tracks
-- ============================================
CREATE TABLE order_tracks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    order_id BIGINT NOT NULL COMMENT '订单ID',
    courier_id BIGINT COMMENT '配送员ID',
    
    latitude DECIMAL(10,8) COMMENT '纬度',
    longitude DECIMAL(11,8) COMMENT '经度',
    accuracy DECIMAL(10,2) COMMENT '精度(米)',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_order (order_id),
    INDEX idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单轨迹表';

-- ============================================
-- 10. 物流事件表 logistics_events
-- ============================================
CREATE TABLE logistics_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '事件ID',
    
    order_id BIGINT NOT NULL COMMENT '订单ID',
    
    status VARCHAR(30) COMMENT '节点状态',
    description VARCHAR(255) COMMENT '节点描述',
    
    location VARCHAR(100) COMMENT '地点描述',
    latitude DECIMAL(10,8) COMMENT '纬度',
    longitude DECIMAL(11,8) COMMENT '经度',
    
    operator_id BIGINT COMMENT '操作人ID',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_order (order_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流事件表';

-- ============================================
-- 11. 通知表 notification
-- ============================================
CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    user_type VARCHAR(20) NOT NULL COMMENT '接收用户类型: user/courier/admin',
    type VARCHAR(50) NOT NULL COMMENT '消息类型: order/system/activity',
    title VARCHAR(200) NOT NULL COMMENT '消息标题',
    content TEXT COMMENT '消息内容',
    related_id BIGINT COMMENT '关联业务ID（如订单ID）',
    related_type VARCHAR(50) COMMENT '关联业务类型',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读: 0-未读, 1-已读',
    read_time DATETIME COMMENT '阅读时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除: 0-未删除, 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_user_type (user_type),
    INDEX idx_type (type),
    INDEX idx_is_read (is_read),
    INDEX idx_is_deleted (is_deleted),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- ============================================
-- 12. 钱包表 wallets（用户/配送员共用，user_id 区分身份）
-- ============================================
CREATE TABLE wallets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '钱包ID',
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID（用户或配送员）',

    balance DECIMAL(10,2) DEFAULT 0 COMMENT '余额',
    frozen_amount DECIMAL(10,2) DEFAULT 0 COMMENT '冻结金额',

    total_income DECIMAL(10,2) DEFAULT 0 COMMENT '累计收入',
    total_withdraw DECIMAL(10,2) DEFAULT 0 COMMENT '累计提现',

    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包表';

-- ============================================
-- 13. 钱包流水表 wallet_transaction
-- ============================================
CREATE TABLE wallet_transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    user_id BIGINT NOT NULL COMMENT '用户ID（用户或配送员）',
    order_id BIGINT COMMENT '关联订单ID',
    order_no VARCHAR(32) COMMENT '关联订单号（冗余，便于展示）',

    type VARCHAR(20) COMMENT '类型: income-收入/withdraw-提现/refund-退款/payment-支付',
    amount DECIMAL(10,2) COMMENT '金额',
    balance_after DECIMAL(10,2) COMMENT '变动后余额',

    description VARCHAR(255) COMMENT '描述',

    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user (user_id),
    INDEX idx_order (order_id),
    INDEX idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水表';

-- ============================================
-- 14. 价格规则表 pricing_rules
-- ============================================
CREATE TABLE pricing_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则ID',
    
    goods_type_id INT NOT NULL COMMENT '物品类型ID',
    
    base_fee DECIMAL(10,2) NOT NULL COMMENT '基础费用(元)',
    price_per_kg DECIMAL(10,2) NOT NULL COMMENT '每公斤价格(元)',
    
    is_active TINYINT DEFAULT 1 COMMENT '是否启用: 1启用 0禁用',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_goods_type (goods_type_id),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格规则表';

-- ============================================
-- 15. 距离定价规则表 distance_pricing_rules
-- ============================================
CREATE TABLE distance_pricing_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则ID',
    
    distance_min DECIMAL(8,2) NOT NULL COMMENT '最小距离(km)',
    distance_max DECIMAL(8,2) NOT NULL COMMENT '最大距离(km)',
    
    base_fee DECIMAL(10,2) NOT NULL COMMENT '基础费用(元)',
    price_per_km DECIMAL(10,2) NOT NULL COMMENT '每公里价格(元)',
    
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_distance (distance_min, distance_max)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='距离定价规则表';

-- ============================================
-- 16. 配送员评价表 courier_reviews
-- ============================================
CREATE TABLE courier_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评价ID',
    
    courier_id BIGINT NOT NULL COMMENT '配送员ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    user_id BIGINT NOT NULL COMMENT '评价用户ID',
    
    rating INT NOT NULL COMMENT '评分1-5',
    content VARCHAR(500) COMMENT '评价内容',
    tags VARCHAR(255) COMMENT '评价标签',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_courier (courier_id),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送员评价表';

-- ============================================
-- 17. 支付记录表 payment_records
-- ============================================
CREATE TABLE payment_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '支付记录ID',
    payment_no VARCHAR(32) UNIQUE NOT NULL COMMENT '支付单号',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    
    pay_method VARCHAR(20) COMMENT '支付方式: mock/wechat/alipay',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    status VARCHAR(20) NOT NULL COMMENT '支付状态: pending/success/failed',
    
    pay_channel VARCHAR(50) COMMENT '支付渠道',
    transaction_id VARCHAR(100) COMMENT '第三方交易号',
    
    pay_time TIMESTAMP NULL COMMENT '支付时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_order (order_id),
    INDEX idx_payment_no (payment_no),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- ============================================
-- 18. 投诉表 complaint
-- ============================================
CREATE TABLE complaint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '投诉ID',
    complaint_no VARCHAR(32) UNIQUE NOT NULL COMMENT '投诉单号',
    
    order_id BIGINT COMMENT '关联订单ID',
    user_id BIGINT NOT NULL COMMENT '投诉用户ID',
    courier_id BIGINT COMMENT '被投诉配送员ID',
    
    type VARCHAR(30) NOT NULL COMMENT '投诉类型: service/delay/damage/lost/other',
    title VARCHAR(200) NOT NULL COMMENT '投诉标题',
    content TEXT NOT NULL COMMENT '投诉内容',
    images VARCHAR(1000) COMMENT '图片证据(JSON数组)',
    
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/processing/resolved/rejected',
    
    handler_id BIGINT COMMENT '处理人ID',
    handler_name VARCHAR(50) COMMENT '处理人姓名',
    handle_time TIMESTAMP NULL COMMENT '处理时间',
    result VARCHAR(255) COMMENT '处理结果',
    result_content TEXT COMMENT '处理说明',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user (user_id),
    INDEX idx_order (order_id),
    INDEX idx_courier (courier_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉表';

-- ============================================
-- 19. 本地消息表 message_records
-- ============================================
CREATE TABLE message_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    
    message_type VARCHAR(50) NOT NULL COMMENT '消息类型',
    business_id BIGINT NOT NULL COMMENT '业务ID',
    
    exchange VARCHAR(100) COMMENT 'RabbitMQ交换机',
    routing_key VARCHAR(100) COMMENT 'RabbitMQ路由键',
    queue_name VARCHAR(100) COMMENT 'RabbitMQ队列名',

    payload TEXT NOT NULL COMMENT '消息内容(JSON)',
    
    status TINYINT DEFAULT 0 COMMENT '状态: 0待发送 1已发送 2失败',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    error_msg VARCHAR(500) COMMENT '错误信息',
    
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_status (status),
    INDEX idx_business (business_id, message_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表(RabbitMQ可靠消息)';

SET FOREIGN_KEY_CHECKS = 1;
