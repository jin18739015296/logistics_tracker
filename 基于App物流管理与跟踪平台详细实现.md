# 基于App物流管理与跟踪平台详细实现

## 第一章 系统概述

### 1.1 项目背景

快运通物流平台是一个基于移动App的同城物流管理与实时跟踪系统，为用户提供便捷的寄件、查件、追踪等服务，为配送员提供高效的抢单、配送任务管理功能，为管理员提供全面的运营监控与数据统计分析。

### 1.2 系统角色

| 角色 | 数据库role值 | 说明 | 终端 |
|------|-------------|------|------|
| 用户 | user | 寄件人/收件人，可下单、支付、追踪、确认收货、评价、投诉 | React Native App |
| 配送员 | courier | 快递配送人员，可抢单、揽件、配送、上报位置 | React Native App |
| 管理员 | admin | 系统运营管理，可审批、派单、处理异常和投诉 | React Native App |

**角色切换机制：** 同一用户可在App内切换角色（通过 `AuthContext.switchRole`），AppNavigator根据当前角色动态渲染对应导航栈。同一手机号可分别注册user和courier两种角色。

### 1.3 技术架构

**后端架构：**
- 核心框架：Spring Boot 3.2.0（Java 17）
- 数据库：MySQL 8.0（端口13306，数据库名logistics_db）
- 缓存：Redis（端口16379，Lettuce连接池，8个max-active连接）
- 消息队列：RabbitMQ（端口5672，用户admin/admin，5个TopicExchange，并发消费者5-10个）
- 实时通信：WebSocket（STOMP协议，端点/stomp和/ws）
- 安全认证：JWT双令牌机制（HS256，Access Token 1小时 + Refresh Token 7天）
- ORM：MyBatis（驼峰映射，mapper XML文件16个）
- 文件存储：阿里云OSS（bucket: wubysj，endpoint: oss-cn-beijing.aliyuncs.com），降级为本地uploads/目录
- 构建工具：Maven

**前端架构：**
- 框架：React Native（Expo SDK）
- 导航：React Navigation（根据角色动态切换3套Tab导航器）
- 状态管理：React Context（AuthContext管理认证和角色，AppLocationContext管理定位）
- 地图：高德地图JS API 2.0（WebView内嵌，JS API Key: 155928fc79408d31539f6b70b89ca01b，Web服务Key: f2d8380599ed7d4473ac5b2cc0503a3e）
- 定位：expo-location（支持前台+后台位置权限，缓存3分钟内位置）
- WebSocket：@stomp/stompjs（STOMP over WebSocket，5秒自动重连，4秒心跳）
- HTTP客户端：Axios（请求拦截器自动附加Bearer Token，401自动刷新Token）

**后端服务端口：** 8082，Context Path: /api

---

## 第二章 后端代码架构

### 2.1 包结构

```
com.logistics.api/
├── common/          # 通用类：Result统一响应、BusinessException业务异常、ErrorCode错误码
├── config/          # 配置类：Redis、RabbitMQ、WebSocket、调度算法、OSS、定时任务
├── controller/      # 控制器层：20个Controller（含9个admin控制器）
│   └── admin/       # 管理端控制器
├── dto/             # 数据传输对象：13个DTO/Request类
├── enums/           # 枚举：OrderStatus(10种状态+状态机)、OrderExceptionType(7种异常)、PaymentStatus
├── exception/       # 全局异常处理器
├── job/             # 定时任务：3个Job
├── mapper/          # MyBatis Mapper接口：19个
├── messaging/       # RabbitMQ消息：5个Exchange、5个Consumer、2个Sender
├── model/           # 实体类：19个Model
├── security/        # 安全：JWT过滤器、MD5密码编码器、SecurityConfig
├── service/         # 服务接口：24个Service
│   ├── impl/        # 服务实现：25个ServiceImpl
│   └── support/     # 辅助类：OrderStatusValidator、OrderViewerPolicy、NotificationCopy
├── util/            # 工具类：JWT双令牌、验证码、卡尔曼滤波
└── websocket/       # WebSocket处理器：STOMP消息路由
```

### 2.2 安全认证实现

#### JWT双令牌机制

系统采用Access Token + Refresh Token双令牌机制，所有令牌存储在Redis中：

| 令牌类型 | Redis Key | 有效期 | 用途 |
|---------|-----------|-------|------|
| Access Token | `access_token:{token}` → username | 1小时 | API请求认证 |
| Refresh Token | `refresh_token:{token}` → username | 7天 | 刷新Access Token |
| 黑名单 | `blacklist:{token}` → "1" | 等于token剩余有效期 | 失效已泄露token |
| 用户封禁 | `user_blacklist:{username}` → JSON(reason,blacklistTime,operator) | 无过期 | 封禁违规用户 |

**JwtAuthenticationFilter过滤流程：**
1. 从`Authorization: Bearer xxx`头提取JWT
2. WebSocket路径（/ws、/stomp）跳过过滤
3. 携带token但解析失败 → 返回401 + "登录已过期或已在其他设备登录"
4. 检查Redis用户黑名单 → 封禁返回403
5. 双重验证：JWT签名校验 + Redis存在性检查
6. 未携带token直接放行（后续Spring Security按权限返回403）

**用户封禁机制：** 封禁用户时，遍历Redis中所有access/refresh token key，匹配username后加入黑名单并删除，实现即时踢下线。解封时删除黑名单key。

**密码编码：** 使用自定义MD5PasswordEncoder（MD5不加盐，仅用于兼容，非安全实践）。

**公开端点：** /auth/**、/sms/**、/captcha/**、/file/upload、/uploads/**、/courier-application/status、/ws/**、/stomp/**

#### 验证码服务

图形验证码通过CaptchaController提供，验证码存储在Redis中（key=captcha:{captchaId}），有过期时间。前端通过CaptchaInput组件展示验证码图片、输入验证码、点击刷新。

### 2.3 认证服务实现

**注册流程（AuthServiceImpl.register）：**
1. 检查用户名唯一性
2. 检查手机号+角色唯一性（同一手机号可分别注册user和courier）
3. MD5加密密码
4. 默认状态设为1（正常）
5. 返回Token

**配送员注册流程（AuthServiceImpl.registerCourier，事务）：**
1. 注册用户，角色=courier，状态=2（审核中，不可登录）
2. 创建CourierApplication记录，包含：身份证信息（号码、正反面、手持照）、车辆信息（类型、车牌、照片）、驾驶证信息、紧急联系人、工作城市/区域
3. 申请状态=pending，等待管理员审批

**登录流程（AuthServiceImpl.login）：**
1. 查找用户
2. MD5验证密码
3. 检查Redis用户封禁
4. 检查用户状态：0=禁用拒绝、2=审核中拒绝
5. courier角色额外检查申请是否被拒绝
6. 生成双令牌（accessToken + refreshToken），存入Redis
7. 返回令牌 + 用户信息（userId, role, username, name, avatar）

**Token刷新（AuthServiceImpl.refreshToken）：**
1. 验证refresh token有效性
2. 检查用户封禁和状态
3. 使旧refresh token失效（加入Redis黑名单）
4. 生成新的双令牌对

---

## 第三章 订单状态机

### 3.1 状态定义（OrderStatus枚举）

| 状态码 | 枚举名 | 中文描述 | 说明 |
|--------|--------|---------|------|
| pending | PENDING | 待支付 | 订单刚创建，等待用户支付 |
| paid | PAID | 已支付 | 用户完成支付，等待系统派单 |
| awaiting_courier_confirm | AWAITING_COURIER_CONFIRM | 配送员待确认 | 系统自动派单后，等待配送员确认接单 |
| awaiting_pickup | AWAITING_PICKUP | 待揽件 | 配送员已接单（抢单直接到此状态），等待取件 |
| picked_up | PICKED_UP | 已揽件 | 配送员已从寄件人处取到包裹 |
| in_transit | IN_TRANSIT | 运输中 | 配送员正在配送途中 |
| delivered | DELIVERED | 已送达 | 配送员确认送达收件人 |
| completed | COMPLETED | 已收货 | 收件人确认收货（终态） |
| cancelled | CANCELLED | 已取消 | 订单被取消（终态） |
| exception | EXCEPTION | 异常待处理 | 配送过程出现异常 |

### 3.2 状态流转规则（OrderStatus.canTransitionTo方法实现）

```
PENDING ──→ PAID（支付完成）
PENDING ──→ CANCELLED（用户取消未支付订单）

PAID ──→ AWAITING_COURIER_CONFIRM（系统自动派单成功）
PAID ──→ AWAITING_PICKUP（订单进入抢单池，配送员直接抢单跳过确认）
PAID ──→ CANCELLED（用户取消已支付订单，自动退款）

AWAITING_COURIER_CONFIRM ──→ AWAITING_PICKUP（配送员确认接单）
AWAITING_COURIER_CONFIRM ──→ PAID（配送员拒绝接单，订单回到待派状态）
AWAITING_COURIER_CONFIRM ──→ EXCEPTION（超时未确认，自动标记异常）

AWAITING_PICKUP ──→ PICKED_UP（配送员确认揽件）
AWAITING_PICKUP ──→ EXCEPTION（揽件异常）

PICKED_UP ──→ IN_TRANSIT（配送员开始运输）
PICKED_UP ──→ EXCEPTION（揽件后异常）

IN_TRANSIT ──→ DELIVERED（配送员确认送达）
IN_TRANSIT ──→ EXCEPTION（运输异常）

DELIVERED ──→ COMPLETED（收件人确认收货）

EXCEPTION ──→ CANCELLED（管理员处理后取消，自动退款）
EXCEPTION ──→ PAID（管理员改派，清除原配送员重新分配）
EXCEPTION ──→ PICKED_UP（管理员恢复继续配送）
```

**关键业务规则：**
- 只有PENDING和PAID状态用户可取消（`canCancel`方法）
- COMPLETED和CANCELLED为终态，不可再流转
- EXCEPTION可恢复到3种状态，取决于异常类型和管理员操作

### 3.3 异常类型与恢复策略（OrderExceptionType枚举）

| 异常类型 | 编码 | 物品位置 | 可恢复 | 允许的管理员动作 |
|---------|------|---------|--------|----------------|
| 配送员未响应 | courier_not_responding | 寄件人处 | 是 | reassign(改派)、cancel(取消退款) |
| 揽件受阻 | pickup_pending_issue | 寄件人处 | 是 | reassign(改派)、cancel(取消退款) |
| 揽件异常 | pickup_issue | 配送员手上 | 是 | continue(继续配送)、cancel(取消退款) |
| 运输异常 | transit_issue | 配送员手上 | 否 | cancel(取消退款) |
| 快件灭失 | goods_lost | 不可恢复 | 否 | cancel(取消退款) |
| 货品损毁 | goods_damaged | 不可恢复 | 否 | cancel(取消退款) |
| 不可抗力 | force_majeure | 不可恢复 | 否 | cancel(取消退款) |

**自动异常推断：** 配送员上报异常时未指定类型，系统根据当前状态自动推断：
- awaiting_courier_confirm / awaiting_pickup → courier_not_responding
- picked_up → pickup_issue
- 其他 → transit_issue

**自动异常扫描：** OrderAutoExceptionJob每5分钟扫描，将awaiting_courier_confirm状态超过60分钟的订单自动标记为courier_not_responding异常。

---

## 第四章 数据库设计

### 4.1 数据库概览

**数据库名：** logistics_db
**字符集：** utf8mb4
**引擎：** InnoDB

### 4.2 完整表结构（19张表）

| 序号 | 表名 | 说明 | 核心字段 |
|------|------|------|---------|
| 1 | users | 用户表 | id, username, password(MD5), role(user/courier/admin), status(0禁用/1启用) |
| 2 | addresses | 用户地址表 | user_id, 省/市/区/详细地址, 经纬度, is_default, tag(家/公司/学校) |
| 3 | goods_types | 物品类型表 | code, name, description, icon, sort_order, is_active |
| 4 | orders | 订单表 | order_no, user_id, courier_id, status(10种), order_type, 金额, dispatch_type(auto/grab), is_exception |
| 5 | order_addresses | 订单地址表 | order_id, type(sender/receiver), 联系人/电话/省市区/详细地址/经纬度 |
| 6 | delivery_tasks | 配送任务表 | order_id, courier_id, status, accept_time, pickup_time, delivery_time, 预估距离/时长 |
| 7 | courier_applications | 配送员申请表 | user_id, status(pending/approved/rejected), 身份证/车辆/驾驶证/紧急联系人/工作区域, reviewer_id |
| 8 | courier_status | 配送员状态表 | courier_id, status(idle/busy/offline), 当前经纬度, location_updated_at, 今日订单数/收入 |
| 9 | order_tracks | 订单轨迹表 | order_id, courier_id, 经纬度, accuracy(精度), create_time |
| 10 | logistics_events | 物流事件表 | order_id, status(节点状态), description, location, 经纬度, operator_id |
| 11 | notification | 消息通知表 | user_id, user_type, type(order/system/complaint), title, content, related_id, is_read, is_deleted |
| 12 | wallets | 钱包表 | user_id(唯一), balance, frozen_amount, total_income, total_withdraw |
| 13 | wallet_transaction | 钱包流水表 | user_id, order_id, order_no, type(income/withdraw/refund/payment/recharge), amount, balance_after |
| 14 | pricing_rules | 价格规则表 | goods_type_id(唯一), base_fee, price_per_kg, is_active |
| 15 | distance_pricing_rules | 距离定价规则表 | distance_min, distance_max, base_fee, price_per_km, is_active |
| 16 | courier_reviews | 配送员评价表 | courier_id, order_id, user_id, rating(1-5), content, tags |
| 17 | payment_records | 支付记录表 | payment_no, order_id, pay_method(mock/wechat/alipay), amount, status(pending/success/failed) |
| 18 | complaint | 投诉表 | complaint_no, order_id, user_id, courier_id, type(service/delay/damage/lost/other), status(pending/processing/resolved/rejected), handler_id, result |
| 19 | message_records | 本地消息表 | message_type, business_id, exchange, routing_key, queue_name, payload(JSON), status(0待发送/1已发送/2失败), retry_count |

### 4.3 初始化数据

**管理员账户：** username=admin, password=5f1d7a84d6e8f9c2b3a4d5e6f7a8b9c0（原始密码: Logistics@2024）

**物品类型（9种）：**

| ID | code | 名称 | 基础费 | 每公斤价 |
|----|------|------|-------|---------|
| 1 | document | 文件资料 | 6.00 | 4.00 |
| 2 | electronics | 数码产品 | 10.00 | 12.00 |
| 3 | clothing | 服装鞋帽 | 8.00 | 3.50 |
| 4 | food | 食品饮料 | 9.00 | 4.00 |
| 5 | daily | 日用百货 | 7.00 | 3.00 |
| 6 | furniture | 家具家电 | 25.00 | 5.00 |
| 7 | books | 图书音像 | 7.00 | 2.50 |
| 8 | medicine | 医药保健 | 12.00 | 15.00 |
| 9 | other | 其他物品 | 8.00 | 3.50 |

**距离定价规则（8个档位）：**

| 距离范围 | 基础费 | 每公里价 | 说明 |
|---------|-------|---------|------|
| 0-3km | 12.00 | 0 | 起步价，城市核心区域 |
| 3-5km | 12.00 | 2.50 | 同城近距离 |
| 5-10km | 16.00 | 2.00 | 市区内跨区 |
| 10-15km | 21.00 | 1.70 | 近郊范围 |
| 15-20km | 27.00 | 1.50 | 远郊范围 |
| 20-30km | 35.00 | 1.30 | 周边城市 |
| 30-50km | 48.00 | 1.10 | 跨市配送 |
| 50-999.99km | 65.00 | 0.95 | 特殊超远距离 |

---

## 第五章 核心业务流程详解

### 5.1 下单-支付-调度完整流程

#### 5.1.1 创建订单（OrderServiceImpl.createOrder，事务）

**代码实际执行步骤：**
1. 根据当前登录用户的username查找用户记录
2. 生成订单号：`LG` + yyyyMMddHHmmss + 4位随机数（如LG202405121430251234）
3. 调用PricingService.calculateOrderPrice计算订单金额（详见5.1.2定价算法）
4. 默认orderType=standard，payType=prepay（预付）
5. 插入Order记录，状态=PENDING
6. 插入寄件人地址（type=sender）和收件人地址（type=receiver）到order_addresses表
7. 记录物流事件到logistics_events表："订单已创建，等待支付"
8. 发送站内通知给用户

#### 5.1.2 定价算法（PricingServiceImpl）

**定价公式：** `总价 = (重量费用 + 距离费用) / 2 × 订单类型系数`

**重量费用计算：**
- 根据goods_type_id查找pricing_rules表获取base_fee和price_per_kg
- `重量费用 = base_fee + weight × price_per_kg`
- 例如：3kg数码产品 = 10.00 + 3 × 12.00 = 46.00元

**距离费用计算：**
- 使用Haversine公式计算寄件地址和收件地址之间的直线距离
- 根据距离查找distance_pricing_rules表匹配档位
- `距离费用 = base_fee + (distance - distance_min) × price_per_km`
- 例如：8km = 16.00 + (8-5) × 2.00 = 22.00元

**订单类型系数：**
- standard（标准配送）：1.0x
- express（加急配送）：1.5x
- same_day（当日达）：2.0x

**示例：** 3kg数码产品配送8km，标准配送 = (46.00 + 22.00) / 2 × 1.0 = 34.00元

#### 5.1.3 支付流程（PaymentServiceImpl.mockPayment，事务）

**当前为模拟支付实现，代码实际执行步骤：**
1. 校验订单状态必须为PENDING
2. 创建支付记录：paymentNo = `PAY` + yyyyMMddHHmmss + 6位随机数，pay_method=mock，status=pending
3. 模拟500ms处理延迟
4. 更新支付记录status=success
5. **从用户钱包扣款**（调用WalletService.deductPayment），余额不足时扣款失败但不影响支付状态（模拟支付场景）
6. 调用handlePaymentSuccess

**handlePaymentSuccess方法（事务）：**
1. 更新订单状态 PENDING → PAID
2. 记录物流事件："支付成功"
3. 发送站内通知给用户"支付成功"
4. **发送RabbitMQ消息**（mqMessageSender.sendOrderPaidMessage），触发自动调度

**钱包扣款（WalletServiceImpl.deductPayment）：**
- 检查余额是否充足，不足抛BusinessException
- 减少余额，记录wallet_transaction（type=payment，金额为负数）
- 余额支付时description="订单支付-订单号XXX"

#### 5.1.4 自动调度流程（OrderPaidConsumer → DispatchServiceImpl）

**RabbitMQ消费链路：**
```
PaymentService.handlePaymentSuccess
  → MQMessageSender.sendOrderPaidMessage（高优先级消息）
    → RabbitMQ Exchange: logistics.order, Routing Key: order.paid.*
      → Queue: order_dispatch_queue
        → OrderPaidConsumer.consume
          → DispatchService.autoAssignCourier（尝试自动分配）
          或 → GrabOrderService.addToGrabPool（分配失败，加入抢单池）
```

**autoAssignCourier方法（事务）代码实际执行步骤：**
1. 获取订单寄件地址（从order_addresses表查type=sender）
2. 查找可用配送员（三级查找策略，详见5.2节）
3. 过滤已满单配送员（maxOrdersPerCourier=5）
4. 对候选配送员执行智能评分算法（详见5.2节）
5. 选择得分最低（最优）的配送员执行分配
6. 发送MQ通知配送员"有新订单"

**doAssignCourier方法（事务）：**
1. 校验订单状态（PAID或PENDING）
2. 校验配送员角色必须为courier
3. 检查订单是否已有非rejected的配送任务
4. 创建DeliveryTask记录（status=awaiting_courier_confirm）
5. 更新订单状态为awaiting_courier_confirm，记录courierId和dispatchType=auto
6. 从抢单池移除（如果存在）
7. 记录物流事件

**分配失败时：** 订单加入抢单池，等待配送员手动抢单。

### 5.2 智能派单算法详解（DispatchServiceImpl）

#### 候选配送员查找（三级策略）

1. **第一级：Redis Geo按城市+半径搜索**
   - 使用Redis GEO数据结构`courier:geo:{城市}`
   - 默认搜索半径10km（searchRadiusKm配置）
   - `opsForGeo().radius(geoKey, circle, args)` 按距离升序返回

2. **第二级：自动扩圈**
   - 10km内无结果时，扩大到20km（expandRadiusKm配置，autoExpand=true时生效）

3. **第三级：按城市全量查找**
   - 无位置信息的配送员，从courier_status表按城市查找
   - 兜底策略，确保不遗漏

**可用性四重检查（isAvailableForDispatch方法）：**
1. 状态为idle或full
2. 当前订单数 < 5
3. 位置信息存在
4. 在可分配列表`couriers:available`中

#### 评分算法（selectBestCourier方法）

**评分公式：**
```
score = routePenaltyWeight × detourKm + directDistanceWeight × directKm + loadPenaltyKmPerOrder × orderCount
```

**默认权重（来自application.yml配置）：**
- routePenaltyWeight = 10.0（顺路性权重最大，优先选择最顺路的配送员）
- directDistanceWeight = 1.0（直线距离次要）
- loadPenaltyKmPerOrder = 1.5（每多一单折合1.5公里惩罚，避免过载）

**Top-K优化：** 先按直线距离取前32人（maxCandidatesForDetourScoring=32），再对这32人执行顺路精算，避免全员计算性能问题。

**顺路性计算（computeRouteDetourKmCached方法）：**
- 对配送员每个在送订单，取"下一关键节点"：未揽件前=寄件坐标，揽件后=收件坐标
- 额外里程 = max(0, dist(配送员当前位置, 新寄件地址) + dist(新寄件地址, 关键节点) - dist(配送员当前位置, 关键节点))
- 取所有在送订单中最小的额外里程（最顺路的一条路径）
- 批量查询配送任务和地址数据，避免N+1查询问题

**评分示例：**
- 配送员A：距新单寄件点2km，顺路额外0.5km，当前2单 → score = 10×0.5 + 1×2 + 1.5×2 = 10.0
- 配送员B：距新单寄件点1km，顺路额外3km，当前1单 → score = 10×3 + 1×1 + 1.5×1 = 32.5
- 结果：配送员A得分更低，优先选择（更顺路）

### 5.3 抢单机制详解（GrabOrderServiceImpl）

#### Redis抢单池数据结构

| Key | 类型 | 值 | 用途 |
|-----|------|---|------|
| `orders:grab_pool:{城市}:{区域}` | Set | orderId集合 | 按城市区域组织的抢单池 |
| `orders:grab_lock:{orderId}` | String | courierId | 抢单分布式锁（30秒TTL） |

**城市名标准化：** 去掉"市"、"省"、"自治区"、"特别行政区"后缀，避免"上海"和"上海市"不匹配。

#### 抢单池操作

**addToGrabPool（加入抢单池）：**
1. 检查订单是否已分配配送员
2. 检查是否已有非rejected的配送任务
3. 根据寄件地址区域构建Redis Key，加入Set

**getGrabableOrders（获取可抢订单）：**
1. 按城市查找所有区域的抢单池（支持原始城市名和标准化城市名两种模式匹配）
2. 遍历池中订单，过滤无效订单（已分配、状态异常、已有配送任务）
3. 计算配送员到寄件地址的距离
4. 按距离升序排序返回

#### 抢单核心逻辑（tryGrabOrder，事务）

1. **Redis分布式锁**：`setIfAbsent(lockKey, courierId, 30s)` 防止并发抢单
2. 校验订单状态（PENDING或PAID）
3. 校验配送员角色
4. 校验无已有配送任务
5. 创建DeliveryTask（**状态直接为awaiting_pickup**，跳过配送员确认环节）
6. 更新订单状态为awaiting_pickup，dispatchType=grab
7. 通知下单用户"配送员已接单"
8. 增加配送员订单计数（courierStatusService.addOrderToCourier）
9. 从抢单池移除
10. finally释放锁

**关键区别：** 自动派单创建的任务状态为awaiting_courier_confirm（需配送员确认），抢单创建的任务直接为awaiting_pickup（无需确认）。

### 5.4 配送流程详解

#### 配送员确认接单（CourierOrderServiceImpl.confirmOrder，事务）

- 状态：awaiting_courier_confirm → awaiting_pickup
- 记录acceptTime
- 同步Redis：syncActiveOrderKeysFromDb

#### 配送员拒绝接单（DispatchServiceImpl.rejectOrder，事务）

- 配送任务标记rejected
- 订单回到paid状态
- 清除配送员
- 订单加入抢单池（等待其他配送员抢单）

#### 确认揽件（CourierOrderServiceImpl.pickupOrder，事务）

- 校验状态=awaiting_pickup
- 更新订单和配送任务状态为picked_up
- 记录pickupTime
- 通知用户"配送员已取件"

#### 开始运输（CourierOrderServiceImpl.startDelivery，事务）

- 校验状态=picked_up
- 更新状态为in_transit
- **同步Redis**：courierStatusService.startDelivery（设为busy，从可分配列表移除）

#### 确认送达（CourierOrderServiceImpl.confirmDelivery，事务）

**代码实际执行步骤：**
1. 校验状态=in_transit
2. **距离校验**（test-mode=false时）：配送员当前位置与收货地址距离必须在5公里以内
   - 优先使用接口传入的位置，否则从轨迹系统取最新位置
   - 使用Haversine公式计算距离
   - 超过5km拒绝确认送达（防止虚假送达）
3. 更新状态为delivered，记录deliveryTime
4. **配送员收入结算**：`actualAmount × 0.90`（配送员得90%，平台抽成10%）
   - 调用walletService.addIncome增加配送员余额
   - 记录wallet_transaction（type=income）
5. **送达通知**：通过收件人手机号查找注册用户，发送通知
6. courierStatusService.finishDelivery更新配送员状态

#### 批量开始配送（CourierOrderServiceImpl.startBatchDelivery，事务）

- 查找配送员所有picked_up状态的订单
- 批量更新为in_transit
- 设配送员为busy状态
- 适用于配送员同时取了多个包裹后一起出发的场景

### 5.5 用户订单操作

#### 取消订单（UserOrderServiceImpl.cancelOrder，事务）

- 校验是下单人本人
- 只有PENDING和PAID状态可取消
- 已支付订单自动全额退款到钱包（幂等：actualAmount - 已退款金额，通过wallet_transaction表汇总已退款金额）
- 退款成功通知用户

#### 确认收货（UserOrderServiceImpl.confirmReceived，事务）

- **仅收件人可确认**（手机号匹配校验）
- 状态 delivered → completed

#### 评价订单（UserOrderServiceImpl.reviewOrder，事务）

- **仅收件人可评价**（手机号匹配校验）
- 订单状态需为delivered或completed
- 防重复评价
- 通知配送员收到评价

#### 查看物流事件（UserOrderServiceImpl.getOrderEvents）

- **过滤内部信息**：过滤掉awaiting_courier_confirm状态的事件（用户端不展示内部派单信息）
- 取消和异常事件保留description，其他事件清除description（避免透出内部信息）

#### 订单查看权限（OrderViewerPolicy）

- **手机号匹配**：去掉首尾空白和常见分隔符后比较
- **收件人可见性**：揽件完成前不可见（PENDING/PAID/AWAITING_COURIER_CONFIRM/AWAITING_PICKUP状态对收件人隐藏）
- **配送员信息脱敏**：在"已送达"之前不展示配送员身份信息

### 5.6 异常处理流程

#### 配送员上报异常（CourierOrderServiceImpl.reportException，事务）

1. 校验状态可流转到EXCEPTION
2. 自动推断异常类型（未指定时根据当前状态推断）
3. 更新订单状态为exception，标记异常类型
4. 同步Redis：finishDelivery（配送员从可分配列表移除）

#### 管理员恢复异常订单（OrderServiceImpl.adminRecoverException，事务）

**三种恢复动作：**

1. **continue（继续配送）**：
   - 回到异常前状态（优先从logistics_events表获取最后一个非异常状态）
   - 配送任务继续

2. **reassign（改派）**：
   - 清除原配送员，订单回到paid重新分配
   - 原配送任务标记rejected
   - 调用courierStatusService.finishDelivery

3. **cancel（取消退款）**：
   - 关闭订单，自动退款到用户钱包
   - 退款金额 = actualAmount - 已退款金额（幂等）
   - 通知用户退款成功

#### 管理员退款（OrderServiceImpl.processRefund，事务）

- 支持全额退款（full）和部分退款（partial）
- 退款金额 = actualAmount - 已退款金额（通过钱包流水表汇总，幂等）
- 清理配送任务、清除配送员
- 退款到钱包、订单状态改为CANCELLED
- 通知用户退款成功

### 5.7 实时轨迹追踪

#### 轨迹上报流程（TrackingServiceImpl.uploadRealTimeLocation）

**代码实际执行步骤：**
1. 校验订单存在且配送员是本单负责人
2. **卡尔曼滤波降噪**：从Redis获取上一次KalmanState，执行滤波计算平滑坐标
   - Q=0.00001（过程噪声协方差，越小越信任预测模型）
   - R=0.001（测量噪声协方差，越大越不信任GPS测量值）
3. **距离阈值过滤**：与上一次位置比较，距离变化 < 5米则丢弃（静止噪点）
4. 构建RealTimeLocationDTO
5. 存入Redis：
   - `track:latest:{orderId}` → JSON(RealTimeLocationDTO)，TTL 2小时
   - `track:order:{orderId}` → List<"lat,lng,timestamp">，TTL 2小时
6. **WebSocket推送**：`/topic/tracking/{orderId}` 实时广播给订阅的用户

#### 轨迹持久化（TrackPersistJob，每5分钟）

1. 扫描Redis中`track:order:*`的所有key
2. 解析每个key中的轨迹点（lat,lng,timestamp）
3. 批量插入MySQL order_tracks表（每500条一批）
4. 插入后删除Redis列表

#### WebSocket消息路由（TrackingWebSocketHandler）

**STOMP端点：**
- `@MessageMapping("/tracking/location")` → `/topic/tracking/{orderId}`：位置更新
- `@MessageMapping("/tracking/iot")` → `/topic/tracking/{orderId}`：IoT设备数据

**推送通道：**
- `/topic/tracking/{orderId}` - 实时位置更新
- `/topic/order/{orderId}` - 订单状态更新、物流事件
- `/topic/user/{userId}` - 用户通知
- `/topic/courier/{courierId}` - 配送员新订单通知

### 5.8 配送员状态管理（CourierStatusServiceImpl）

#### Redis数据结构

| Key | 值 | TTL | 用途 |
|-----|---|-----|------|
| `courier:status:{id}` | offline/idle/busy/full | 240分钟 | 配送员当前状态 |
| `courier:location:{id}` | "lat,lng" | 240分钟 | 当前位置 |
| `courier:city:{id}` | 城市名 | 240分钟 | 所在城市 |
| `courier:update:{id}` | ISO时间 | 240分钟 | 最后更新时间 |
| `courier:geo:{城市}` | Redis Geo | 240分钟 | 按城市组织的地理位置索引 |
| `couriers:available` | Set\<courierId\> | - | 可分配配送员列表 |
| `courier:order_count:{id}` | 订单数 | 4小时 | 当前在送订单数 |
| `courier:orders:{id}` | Set\<orderId\> | 4小时 | 当前在送订单集合 |

**状态值说明：** offline(离线)、idle(空闲可接单)、busy(配送中)、full(订单已满，前端显示idle)

#### 上线流程（goOnline方法）

1. 查询数据库真实状态
2. 查询进行中订单数（从delivery_tasks表恢复）
3. 确定初始状态（上线强制设为idle）
4. 写入Redis（状态、位置、城市、更新时间）
5. idle且未满单则加入可分配列表
6. 添加到Redis Geo（按城市）
7. 恢复订单计数和订单集合到Redis
8. **同步落库**（直接写MySQL，不依赖MQ时序）
9. **发送MQ消息**（异步补偿，CourierStatusSyncConsumer消费）

#### 下线流程（goOffline方法）

1. 检查是否有进行中订单（有则不允许下线）
2. 更新Redis状态为offline
3. 从可分配列表和Geo中移除
4. 同步更新数据库
5. 发送MQ消息

#### 位置更新（updateLocation方法）

- **只更新idle状态配送员的位置**（busy状态不更新，避免干扰）
- 更新Redis位置和Geo
- 发送MQ消息（type=UPDATE_LOCATION，仅更新坐标，不覆盖status/orderCount）

#### 完成配送（finishDelivery方法）

- 从订单集合移除
- **以数据库为准**重新查询在送订单，同步Redis订单集合
- 如果busy且无剩余订单 → 恢复idle，加入可分配列表
- 如果busy且有剩余订单 → 保持busy，更新订单数

#### 清理机制（cleanupInactiveCouriers）

- 清理超过60分钟未更新的配送员，自动设为offline

### 5.9 钱包与支付

#### 钱包操作（WalletServiceImpl）

| 操作 | 方法 | 逻辑 |
|------|------|------|
| 懒创建 | getOrCreateWallet | 首次访问时创建，balance=0 |
| 充值 | recharge | 增加余额 + 记录交易(type=recharge) |
| 支付扣款 | deductPayment | 检查余额不足抛异常，减少余额 + 记录交易(type=payment，金额为负) |
| 收入入账 | addIncome | 增加余额 + 记录交易(type=income) |
| 退款 | refund | 增加余额 + 记录交易(type=refund)，描述包含原因 |
| 已退款查询 | getRefundedAmount | 通过wallet_transaction表汇总退款金额，用于幂等退款计算 |

**交易类型：** income(配送收入)、payment(订单支付)、refund(退款)、recharge(充值)

**收入分配：** 配送员确认送达时，配送员获得actualAmount × 90%，平台抽成10%。

### 5.10 投诉流程

#### 提交投诉（ComplaintServiceImpl.submitComplaint）

1. 查找订单（支持orderId或orderNo）
2. **权限校验**：下单人或收件人手机号匹配
3. 防重复投诉
4. 订单状态校验：只有delivered或completed可投诉
5. 投诉对象校验：courierId必须与订单配送员一致
6. 生成投诉编号：`CP` + yyyyMMddHHmmss + 4位随机数
7. 通知投诉人"已受理"
8. 通知管理员"新投诉待处理"
9. **受理阶段不通知配送员**

#### 处理投诉（ComplaintServiceImpl.handleComplaint）

1. 更新投诉状态和结果
2. 通知投诉人结论文案（成立/未成立 + 处理结果 + 详细说明）
3. 通知关联配送员结论文案

#### 撤销投诉（ComplaintServiceImpl.cancelComplaint）

- 只有pending状态且是投诉人本人可撤销

### 5.11 通知服务（NotificationServiceImpl + NotificationCopy）

**通知文案集中管理（NotificationCopy类）：**

| 场景 | 接收人 | 通知内容 |
|------|--------|---------|
| 下单成功待支付 | 下单用户 | "您的订单已创建，请尽快完成支付" |
| 支付成功 | 下单用户 | "支付成功！正在为您安排配送员" |
| 配送员接单 | 下单用户 | "配送员已接单，即将前来取件"（对用户只说"已接单"，不提抢单池等内部词） |
| 系统派单 | 配送员 | 走MQ/WebSocket实时通道，不写App消息中心 |
| 揽件成功 | 下单用户 | "配送员已取件，包裹正在路上" |
| 送达成功 | 收件人 | "您的包裹已送达，请确认收货" |
| 客户评价 | 被评价配送员 | "客户对您的服务进行了评价" |
| 投诉提交 | 投诉人 + 管理员 | 投诉人收到"已受理"，管理员收到"新投诉待处理" |
| 投诉结案 | 投诉人 + 配送员 | 各自收到结论文案 |

### 5.12 配送员申请审核

#### 提交申请（CourierApplicationServiceImpl.submitApplication）

- 创建CourierApplication记录，包含身份证信息（号码、正反面、手持照）、车辆信息（类型、车牌、照片）、驾驶证信息、紧急联系人、工作城市/区域
- 申请状态=pending

#### 管理员审核

**通过（approveApplication）：**
- 更新申请状态为approved
- 更新关联用户角色为courier
- 更新用户状态为1（启用）

**拒绝（rejectApplication）：**
- 更新申请状态为rejected
- 记录拒绝原因
- 更新用户状态为0（禁用）

### 5.13 RabbitMQ消息系统

#### Exchange和Queue定义（MQConstants + RabbitMQConfig）

| Exchange | 类型 | Queue | Routing Key | 用途 |
|----------|------|-------|-------------|------|
| logistics.order | Topic | payment_success_queue | order.paid.* | 支付成功回调 |
| logistics.order | Topic | order_dispatch_queue | delivery.order.dispatch.* | 订单分配 |
| logistics.tracking | Topic | track_queue | tracking.upload.* | 轨迹上传 |
| logistics.order | Topic | logistics_event_queue | order.event.* | 物流事件 |
| logistics.data.sync | Topic | courier_status_sync_queue | courier.status.* | 配送员状态同步 |

#### 消息消费者

| 消费者 | 消费Queue | 处理逻辑 |
|--------|----------|---------|
| OrderPaidConsumer | order_dispatch_queue | 调用dispatchService.autoAssignCourier，失败则addToGrabPool |
| CourierStatusSyncConsumer | courier_status_sync_queue | 根据消息类型同步Redis状态到MySQL（UPDATE_LOCATION仅更新坐标，OFFLINE仅改状态） |
| TrackMessageConsumer | track_queue | 将轨迹写入order_tracks表 |
| PaymentSuccessConsumer | payment_success_queue | 旧版消费者，默认关闭 |

#### 可靠消息机制（本地消息表模式）

**MessageSender发送流程：**
1. 序列化消息为JSON
2. 保存到message_records表（status=0待发送）
3. 发送到MQ
4. 更新消息状态（1已发送或2失败）

**MessageCompensateJob补偿定时任务：**
- 每30秒处理status=0的消息（重新发送）
- 每60秒处理status=2且retry_count < 5的消息（重试失败消息）

### 5.14 定时任务汇总

| 任务 | 执行频率 | 功能 |
|------|---------|------|
| OrderAutoExceptionJob | 每5分钟 | 扫描awaiting_courier_confirm超时60分钟的订单，标记为courier_not_responding异常 |
| TrackPersistJob | 每5分钟 | 将Redis中缓存的GPS轨迹点批量写入MySQL（每500条一批） |
| MessageCompensateJob(补偿) | 每30秒 | 处理待发送的本地消息 |
| MessageCompensateJob(重试) | 每60秒 | 重试失败消息（最多5次） |

---

## 第六章 前端代码架构

### 6.1 项目结构

```
mobileApp/
├── App.js                    # 应用入口
├── index.js                  # Expo注册入口
└── src/
    ├── api/                  # API服务层（14个文件）
    │   ├── axiosConfig.js    # Axios实例（自动附加Token，401自动刷新）
    │   ├── authApi.js        # 认证API（登录/注册/刷新/登出/切换角色/重置密码）
    │   ├── userApi.js        # 用户API（获取/更新信息/修改密码）
    │   ├── orderApi.js       # 订单API（创建/列表/详情/取消/支付/确认收货/评价/物流事件）
    │   ├── courierApi.js     # 配送员API（可抢订单/抢单/当前任务/确认取件送达/统计/历史/评价）
    │   ├── adminApi.js       # 管理API（仪表盘/订单/用户/投诉/定价规则）
    │   ├── addressApi.js     # 地址API（列表/新增/更新/删除）
    │   ├── paymentApi.js     # 支付API（创建支付/记录）
    │   ├── trackingApi.js    # 追踪API（历史/最新位置/轨迹/上传）
    │   ├── notificationApi.js # 通知API（列表/详情/标记已读）
    │   ├── complaintApi.js   # 投诉API（提交/我的列表/详情/撤销）
    │   ├── courierReviewApi.js # 评价API（配送员评价列表/统计/订单评价）
    │   ├── courierApplicationApi.js # 配送员申请API（申请/状态查询）
    │   ├── walletApi.js      # 配送员钱包API（余额/提现/交易记录）
    │   ├── userWalletApi.js  # 用户钱包API（余额/充值/交易记录）
    │   ├── captchaApi.js     # 验证码API（获取/刷新/验证）
    │   ├── fileApi.js        # 文件上传API
    │   └── goodsTypeApi.js   # 物品类型API（列表/创建/更新/删除）
    ├── components/           # 可复用组件
    │   ├── AMapView.js       # 高德地图WebView组件（标记点/折线/车辆平滑移动）
    │   ├── AMapPicker.js     # 高德地图选点组件（搜索/逆地理编码/定位）
    │   ├── OrderMap.js       # 订单地图组件（封装AMapView，自动构建标记和轨迹）
    │   ├── CompactLocationChip.js # 定位胶囊（刷新/地图选点/地址簿选择）
    │   ├── OrderReviewCard.js # 订单评价卡片
    │   ├── CaptchaInput.js   # 图形验证码输入组件
    │   ├── ImagePicker.js    # 图片选择上传组件
    │   ├── BrandMark.js      # 品牌矢量图标
    │   ├── AuthScreenDecor.js # 认证页背景装饰
    │   ├── SplashScreen.js   # 启动闪屏（动画+进度条）
    │   └── admin/AdminPageBackdrop.js # 管理员页面装饰底
    ├── config/env.js         # 环境配置（API地址/WS地址）
    ├── constants/            # 常量
    │   ├── orderStatus.js    # 订单状态定义+用户端展示规则+预计送达计算
    │   ├── mapMarkers.js     # 地图标记常量（配送车辆SVG图标）
    │   └── mapDefaults.js    # 地图默认中心点
    ├── context/              # React Context
    │   ├── AuthContext.js    # 认证上下文（登录/登出/角色切换/Token管理）
    │   └── AppLocationContext.js # 定位上下文（GPS/地图选点/地址簿选择）
    ├── data/                 # 静态数据（省市区三级联动）
    ├── hooks/                # 自定义Hooks
    │   ├── useOrderTracking.js  # 用户端订单追踪（REST+WebSocket双数据源）
    │   └── useCourierTracking.js # 配送员位置追踪（GPS监听+低频上传）
    ├── navigation/           # 导航配置
    │   ├── AppNavigator.js   # 根导航器（根据角色动态切换）
    │   ├── BottomTabNavigator.js # 用户端Tab导航
    │   ├── CourierTabNavigator.js # 配送员端Tab导航
    │   ├── AdminTabNavigator.js # 管理员端Tab导航
    │   └── AdminOrderStackNavigator.js # 管理员订单Stack导航
    ├── screens/              # 页面
    │   ├── LoginScreen.js    # 登录
    │   ├── RegisterScreen.js # 注册
    │   ├── ResetPasswordScreen.js # 重置密码
    │   ├── HomeScreen.js     # 用户首页
    │   ├── ShipmentScreen.js # 寄件下单
    │   ├── OrderListScreen.js # 订单列表
    │   ├── OrderTrackingScreen.js # 订单详情/物流追踪（核心页面）
    │   ├── TrackScreen.js    # 物流查询入口
    │   ├── PaymentScreen.js  # 支付
    │   ├── ProfileScreen.js  # 用户"我的"
    │   ├── MessageScreen.js  # 消息列表
    │   ├── AddressListScreen.js # 地址管理
    │   ├── AddressMapEditScreen.js # 地图选点地址编辑
    │   ├── EditProfileScreen.js # 编辑个人信息
    │   ├── ChangePasswordScreen.js # 修改密码
    │   ├── UserWalletScreen.js # 用户钱包
    │   ├── PaymentRecordListScreen.js # 支付记录
    │   ├── AboutScreen.js    # 关于我们
    │   ├── HelpCenterScreen.js # 帮助中心
    │   ├── CourierApplicationStatusScreen.js # 配送员申请状态查询
    │   ├── complaint/        # 投诉相关
    │   │   ├── ComplaintListScreen.js
    │   │   ├── ComplaintDetailScreen.js
    │   │   └── SubmitComplaintScreen.js
    │   ├── review/           # 评价相关
    │   │   ├── SubmitReviewScreen.js
    │   │   └── CourierReviewListScreen.js
    │   ├── courier/          # 配送员端
    │   │   ├── CourierHomeScreen.js
    │   │   ├── GrabOrderScreen.js
    │   │   ├── DeliveryTaskScreen.js
    │   │   ├── DeliveryDetailScreen.js
    │   │   ├── PendingConfirmTaskScreen.js
    │   │   ├── VerificationScreen.js
    │   │   ├── CourierProfileScreen.js
    │   │   ├── CourierWalletScreen.js
    │   │   ├── WalletTransactionScreen.js
    │   │   ├── DeliveryHistoryScreen.js
    │   │   ├── CourierReviewsScreen.js
    │   │   ├── CourierSettingsScreen.js
    │   │   └── WorkHoursScreen.js
    │   └── admin/            # 管理员端
    │       ├── AdminDashboardScreen.js
    │       ├── AdminOrderMainScreen.js
    │       ├── AdminOrderSearchScreen.js
    │       ├── AdminOrderListScreen.js
    │       ├── AdminOrderDetailScreen.js
    │       ├── AdminOrderHubScreen.js
    │       ├── AdminUserListScreen.js
    │       ├── AdminCourierApplicationScreen.js
    │       ├── AdminComplaintListScreen.js
    │       ├── AdminComplaintDetailScreen.js
    │       ├── AdminReportsScreen.js
    │       ├── AdminProfileScreen.js
    │       ├── AdminBlacklistScreen.js
    │       ├── GoodsTypeListScreen.js
    │       └── PricingRuleListScreen.js
    ├── services/             # 服务层
    │   ├── webSocketService.js # STOMP WebSocket服务（自动重连/心跳/断线重订阅）
    │   └── locationService.js # GPS定位服务（缓存/持续监听/前后台权限）
    ├── theme.js              # 主题配置
    └── utils/                # 工具函数
```

### 6.2 导航结构

**AppNavigator根据角色动态切换：**
- 未登录 → AuthStack（Login / Register / ResetPassword / CourierApplicationStatus）
- role=user → BottomTabNavigator（首页/订单/消息/我的）
- role=courier → CourierTabNavigator（工作台/抢单/任务/我的）
- role=admin → AdminTabNavigator（仪表盘/订单/管理/我的）

### 6.3 前端核心机制

#### 订单状态用户端展示规则（orderStatus.js）

**用户端不暴露内部调度状态，统一展示友好文案：**

| 实际状态 | 用户端展示 | 说明 |
|---------|-----------|------|
| pending | 待支付 | 等待用户支付 |
| paid | 已支付 | 对用户统一展示"已支付"，不暴露"待派单" |
| awaiting_courier_confirm | 已支付 | 对用户统一展示"已支付"，不暴露"配送员待确认" |
| awaiting_pickup | 已接单 | 配送员已接单，即将取件 |
| picked_up | 已揽件 | 配送员已取件 |
| in_transit | 配送中 | 运输中，地图显示配送车辆 |
| delivered | 已送达 | 等待收件人确认 |
| completed | 已完成 | 收货确认 |
| cancelled | 已取消 | - |
| exception | 异常 | - |

**地图轨迹显示规则：** 只有in_transit状态才在地图上显示配送车辆图标和实时轨迹。

**预计送达时间计算（getCoarseEstimatedDeliveryHint）：** 基于Haversine距离和配送速度模型，考虑红绿灯/休息/找楼等因素：
- 0-3km：30-50分钟
- 3-5km：50-70分钟
- 5-10km：1-1.5小时
- 10km以上：每10km加1小时

#### WebSocket实时追踪（useOrderTracking Hook）

**双数据源策略：**
1. **WebSocket推送**：订阅`/topic/tracking/{orderId}`（实时位置）和`/topic/order/{orderId}`（物流节点）
2. **定时轮询兜底**：WebSocket断线时自动降级为REST API定时刷新

**配送车辆平滑移动：** AMapView组件使用高德JS API的`moveTo`动画实现车辆标记平滑移动，`setAngle`实现方向旋转。

#### 配送员位置上报（useCourierTracking Hook）

- 使用locationService持续获取GPS位置
- **低频上传**：最小间隔3秒上传一次轨迹到后端
- 关键节点（揽件、送达）时手动上传精确位置

#### GPS定位优化（locationService）

- **缓存策略**：3分钟内、800m精度内的位置优先使用缓存，避免室内BestForNavigation长等待
- **权限管理**：自动请求前台+后台位置权限
- **双模式**：fast模式优先缓存，accurate模式用BestForNavigation

---

## 第七章 完整功能模块清单

### 7.1 用户端功能模块

| 序号 | 功能模块 | 前端页面 | 后端接口 | 代码实际做了什么 |
|------|----------|---------|---------|----------------|
| 1 | 用户注册 | RegisterScreen | POST /auth/register | 检查用户名+手机号唯一性，MD5加密密码，创建用户 |
| 2 | 用户登录 | LoginScreen | POST /auth/login | MD5验证密码，检查封禁/状态，生成JWT双令牌存Redis |
| 3 | 找回密码 | ResetPasswordScreen | POST /auth/reset-password | 验证用户名后重置密码 |
| 4 | 修改密码 | ChangePasswordScreen | PUT /users/me/password | 验证旧密码后更新为新MD5密码 |
| 5 | 编辑个人资料 | EditProfileScreen | PUT /users/me | 更新姓名/手机号/邮箱 |
| 6 | 寄件下单 | ShipmentScreen | POST /orders | 生成订单号，Haversine算距离，查定价规则算金额，插入订单+地址+物流事件 |
| 7 | 地址选择 | AddressMapEditScreen | POST /addresses | 高德地图选点+逆地理编码，保存省市区+经纬度 |
| 8 | 地址管理 | AddressListScreen | GET/POST/PUT/DELETE /addresses | CRUD地址，设置默认地址 |
| 9 | 订单列表 | OrderListScreen | GET /orders | 合并查询下单人+收件人订单，配送员信息脱敏，设置viewerRole标志 |
| 10 | 订单搜索 | TrackScreen | GET /orders/{orderNo} | 按订单号查询 |
| 11 | 订单追踪 | OrderTrackingScreen | GET /orders/{id}/track | WebSocket实时位置+REST轨迹+卡尔曼滤波降噪+物流时间线 |
| 12 | 取消订单 | OrderTrackingScreen | POST /orders/{id}/cancel | 校验PENDING/PAID状态，已支付自动退款到钱包 |
| 13 | 确认收货 | OrderTrackingScreen | POST /orders/{id}/confirm-received | 校验收件人手机号，delivered→completed |
| 14 | 发起支付 | PaymentScreen | POST /payments/create | 创建支付记录，模拟支付，扣钱包余额，发MQ触发调度 |
| 15 | 支付记录 | PaymentRecordListScreen | GET /payments/my-records | 查询当前用户支付记录 |
| 16 | 钱包充值 | UserWalletScreen | POST /user-wallet/recharge | 增加余额+记录交易 |
| 17 | 钱包余额 | UserWalletScreen | GET /user-wallet/balance | 懒创建钱包，返回余额 |
| 18 | 流水记录 | UserWalletScreen | GET /user-wallet/transactions | 按type过滤+分页，批量enrichOrderNos |
| 19 | 提交投诉 | SubmitComplaintScreen | POST /complaints | 校验权限+防重复+生成投诉编号+通知投诉人和管理员 |
| 20 | 投诉列表 | ComplaintListScreen | GET /complaints/my | 查询当前用户投诉 |
| 21 | 投诉详情 | ComplaintDetailScreen | GET /complaints/{id} | 展示投诉信息+处理结果，支持撤销 |
| 22 | 评价配送员 | SubmitReviewScreen | POST /orders/{id}/review | 校验收件人+防重复+通知配送员 |
| 23 | 查看评价 | CourierReviewListScreen | GET /courier-reviews/courier/{id} | 评分概览+评价列表 |
| 24 | 申请配送员 | CourierApplicationStatusScreen | POST /courier-applications | 注册courier角色(状态2审核中)+创建申请记录 |
| 25 | 消息通知 | MessageScreen | GET /notifications | 分页查询+标记已读 |
| 26 | 帮助中心 | HelpCenterScreen | - | 6个FAQ+客服联系方式 |
| 27 | 关于我们 | AboutScreen | - | 品牌信息+功能介绍 |
| 28 | 退出登录 | ProfileScreen | POST /auth/logout | 清除AsyncStorage token和用户信息 |

### 7.2 配送员端功能模块

| 序号 | 功能模块 | 前端页面 | 后端接口 | 代码实际做了什么 |
|------|----------|---------|---------|----------------|
| 1 | 配送员登录 | LoginScreen | POST /auth/login | 同用户登录，额外检查申请是否被拒绝 |
| 2 | 工作台首页 | CourierHomeScreen | GET /courier/stats + GET /courier/orders/current | 今日统计(完成单数/收入)+当前任务列表 |
| 3 | 抢单大厅 | GrabOrderScreen | GET /courier/orders/available | Redis Set查抢单池+过滤无效+计算距离+按距离排序 |
| 4 | 订单抢单 | GrabOrderScreen | POST /courier/orders/{id}/grab | Redis分布式锁+创建任务(直接awaiting_pickup)+通知用户+增加订单计数 |
| 5 | 我的任务 | DeliveryTaskScreen | GET /courier/orders/current | 按状态分组(待取件/配送中/已完成) |
| 6 | 任务详情 | DeliveryDetailScreen | GET /courier/orders/{id} | 订单信息+地图+操作按钮 |
| 7 | 待确认任务 | PendingConfirmTaskScreen | POST /courier/orders/{id}/confirm | awaiting_courier_confirm→awaiting_pickup |
| 8 | 确认揽件 | DeliveryDetailScreen | POST /courier/orders/{id}/pickup | awaiting_pickup→picked_up，通知用户"已取件" |
| 9 | 开始运输 | DeliveryDetailScreen | POST /courier/orders/{id}/start-delivery | picked_up→in_transit，Redis设busy |
| 10 | 确认送达 | DeliveryDetailScreen | POST /courier/orders/{id}/deliver | 距离校验5km内+in_transit→delivered+配送员收入90%+通知收件人 |
| 11 | 位置上报 | useCourierTracking Hook | POST /tracking/upload | 卡尔曼滤波+5m阈值过滤+Redis缓存+WebSocket推送 |
| 12 | 地图导航 | DeliveryDetailScreen | - | 调用高德地图导航 |
| 13 | 历史配送 | DeliveryHistoryScreen | GET /courier/delivery-history | 统计卡片+历史记录列表 |
| 14 | 我的评价 | CourierReviewsScreen | GET /courier/reviews | 评分概览+评价列表 |
| 15 | 配送员钱包 | CourierWalletScreen | GET /wallet/balance | 余额+提现+收支明细 |
| 16 | 流水记录 | WalletTransactionScreen | GET /wallet/transactions | 按type过滤+分页 |
| 17 | 工作时长 | WorkHoursScreen | GET /courier/stats | 本月预估工作时长+配送单数 |
| 18 | 配送员认证 | VerificationScreen | POST /courier-applications | 身份证+车辆+驾驶证信息上传 |
| 19 | 配送员资料 | CourierProfileScreen | GET /users/me | 个人信息+统计+功能菜单 |
| 20 | 上线/下线 | CourierHomeScreen | POST /courier/online / POST /courier/offline | 上线：写Redis+同步MySQL+发MQ；下线：有进行中订单则拒绝 |

### 7.3 管理端功能模块

| 序号 | 功能模块 | 前端页面 | 后端接口 | 代码实际做了什么 |
|------|----------|---------|---------|----------------|
| 1 | 仪表盘 | AdminDashboardScreen | GET /admin/dashboard/stats | 今日订单/收入/活跃配送员/待处理投诉 |
| 2 | 待派订单 | AdminOrderHubScreen | GET /admin/orders/pending-dispatch | 查询已支付未分配的订单 |
| 3 | 自动派单 | AdminOrderHubScreen | POST /admin/orders/{id}/auto-dispatch | 调用DispatchService智能派单算法 |
| 4 | 加入抢单池 | AdminOrderHubScreen | POST /admin/orders/{id}/add-to-grab-pool | 加入Redis抢单池 |
| 5 | 异常订单 | AdminOrderListScreen | GET /admin/orders/exceptions | 按异常类型筛选 |
| 6 | 订单搜索 | AdminOrderSearchScreen | GET /admin/orders/search | 按订单号/用户信息搜索 |
| 7 | 订单详情 | AdminOrderDetailScreen | GET /admin/orders/{id} | 完整订单信息+异常订单返回preExceptionStatus |
| 8 | 恢复异常 | AdminOrderDetailScreen | POST /admin/orders/{id}/recover-exception | continue/reassign/cancel三种动作 |
| 9 | 退款处理 | AdminOrderDetailScreen | POST /admin/orders/{id}/refund | 幂等退款(actualAmount-已退款)+清理任务+通知用户 |
| 10 | 用户管理 | AdminUserListScreen | GET /admin/users | 搜索/筛选/分页 |
| 11 | 启用/禁用 | AdminUserListScreen | PUT /admin/users/{id}/status | 更新用户状态+Redis封禁/解封(遍历所有token踢下线) |
| 12 | 黑名单管理 | AdminBlacklistScreen | GET/POST/DELETE /admin/blacklist | 查看黑名单+拉黑/解封 |
| 13 | 配送员申请审核 | AdminCourierApplicationScreen | GET /admin/courier-applications | 按状态筛选申请列表 |
| 14 | 通过申请 | AdminCourierApplicationScreen | PUT /admin/courier-applications/{id}/approve | 更新申请状态+用户角色改courier+状态改1 |
| 15 | 拒绝申请 | AdminCourierApplicationScreen | PUT /admin/courier-applications/{id}/reject | 更新申请状态+记录原因+用户状态改0 |
| 16 | 投诉列表 | AdminComplaintListScreen | GET /admin/complaints | 按状态筛选 |
| 17 | 处理投诉 | AdminComplaintDetailScreen | POST /admin/complaints/{id}/handle | 更新结果+通知投诉人和配送员 |
| 18 | 物品类型管理 | GoodsTypeListScreen | GET/POST/PUT/DELETE /admin/goods-types | CRUD物品类型+关联价格规则 |
| 19 | 价格规则管理 | PricingRuleListScreen | GET/POST/PUT/DELETE /admin/pricing/rules | CRUD定价规则 |
| 20 | 数据报表 | AdminReportsScreen | GET /admin/reports | 订单量趋势/收入统计/配送员绩效 |
| 21 | 管理员资料 | AdminProfileScreen | GET /users/me | 个人信息+功能菜单 |

---

## 第八章 API接口总览

### 8.1 认证接口

| 接口 | 方法 | 说明 | 请求体 | 返回 |
|------|------|------|--------|------|
| /api/auth/login | POST | 用户登录 | {username, password, captchaId, captchaCode} | {accessToken, refreshToken, userId, role, username, name, avatar} |
| /api/auth/register | POST | 用户注册 | {username, password, phone, name, email} | Token |
| /api/auth/refresh | POST | 刷新Token | {refreshToken} | {accessToken, refreshToken} |
| /api/auth/logout | POST | 退出登录 | - | - |
| /api/auth/switch-role | POST | 切换角色 | {targetRole} | {accessToken, refreshToken, role} |
| /api/auth/reset-password | POST | 重置密码 | {username, newPassword} | - |

### 8.2 用户接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/users/me | GET | 获取当前用户信息 |
| /api/users/me | PUT | 更新当前用户信息 |
| /api/users/me/password | PUT | 修改密码 |

### 8.3 地址接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/addresses | GET | 地址列表 |
| /api/addresses | POST | 新增地址（含省市区+经纬度） |
| /api/addresses/{id} | PUT | 编辑地址 |
| /api/addresses/{id} | DELETE | 删除地址 |
| /api/addresses/{id}/default | POST | 设为默认 |

### 8.4 订单接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/orders | POST | 创建订单（自动计算价格） |
| /api/orders | GET | 订单列表（支持status筛选+分页） |
| /api/orders/{id} | GET | 订单详情（含地址+配送员信息） |
| /api/orders/{id}/cancel | POST | 取消订单（已支付自动退款） |
| /api/orders/{id}/pay | POST | 支付订单 |
| /api/orders/{id}/confirm-received | POST | 确认收货（仅收件人） |
| /api/orders/{id}/review | POST | 评价订单（仅收件人，防重复） |
| /api/orders/{id}/events | GET | 物流事件（过滤内部状态） |
| /api/orders/{id}/track | GET | 订单追踪（实时位置+轨迹+物流时间线） |

### 8.5 支付接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/payments/create | POST | 创建支付（模拟支付） |
| /api/payments/records | GET | 支付记录 |
| /api/payments/my-records | GET | 我的支付记录 |

### 8.6 钱包接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/user-wallet/balance | GET | 用户钱包余额 |
| /api/user-wallet/recharge | POST | 充值 |
| /api/user-wallet/transactions | GET | 交易记录 |
| /api/wallet/balance | GET | 配送员钱包余额 |
| /api/wallet/withdraw | POST | 提现 |
| /api/wallet/transactions | GET | 配送员交易记录 |

### 8.7 投诉接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/complaints | POST | 提交投诉（防重复） |
| /api/complaints/my | GET | 我的投诉列表 |
| /api/complaints/{id} | GET | 投诉详情 |
| /api/complaints/{id}/cancel | POST | 撤销投诉（仅pending） |

### 8.8 配送员申请接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/courier-applications | POST | 提交申请（含身份证/车辆/驾驶证信息） |
| /api/courier-applications/status | GET | 查询申请状态 |

### 8.9 配送员接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/courier/orders/available | GET | 可抢订单（按距离排序） |
| /api/courier/orders/{id}/grab | POST | 抢单（Redis分布式锁） |
| /api/courier/orders/current | GET | 当前配送任务 |
| /api/courier/orders/{id} | GET | 配送详情 |
| /api/courier/orders/{id}/confirm | POST | 确认接单 |
| /api/courier/orders/{id}/pickup | POST | 确认揽件 |
| /api/courier/orders/{id}/start-delivery | POST | 开始运输 |
| /api/courier/orders/{id}/deliver | POST | 确认送达（5km距离校验） |
| /api/courier/stats | GET | 配送统计 |
| /api/courier/delivery-history | GET | 配送历史 |
| /api/courier/reviews | GET | 我的评价 |
| /api/courier/online | POST | 上线（写Redis+MySQL+MQ） |
| /api/courier/offline | POST | 下线（有进行中订单则拒绝） |
| /api/courier/location | POST | 上报位置 |

### 8.10 追踪接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/tracking/{orderId}/history | GET | 追踪历史 |
| /api/tracking/{orderId}/latest | GET | 最新位置 |
| /api/tracking/{orderId}/tracks | GET | 订单轨迹（Redis+MySQL合并去重） |
| /api/tracking/upload | POST | 上传轨迹点（卡尔曼滤波+5m阈值） |

### 8.11 管理端接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/admin/dashboard/stats | GET | 仪表盘统计 |
| /api/admin/orders | GET | 订单列表（支持筛选） |
| /api/admin/orders/search | GET | 订单搜索 |
| /api/admin/orders/{id} | GET | 订单详情（含preExceptionStatus） |
| /api/admin/orders/{id}/assign | PUT | 手动分配配送员 |
| /api/admin/orders/{id}/auto-dispatch | POST | 自动派单 |
| /api/admin/orders/{id}/add-to-grab-pool | POST | 加入抢单池 |
| /api/admin/orders/{id}/recover-exception | POST | 恢复异常（continue/reassign/cancel） |
| /api/admin/orders/{id}/refund | POST | 退款（幂等） |
| /api/admin/orders/exceptions | GET | 异常订单列表 |
| /api/admin/orders/pending-dispatch | GET | 待派订单 |
| /api/admin/users | GET | 用户列表（搜索/筛选/分页） |
| /api/admin/users/{id}/status | PUT | 修改用户状态（封禁时踢下线） |
| /api/admin/blacklist | GET | 黑名单列表 |
| /api/admin/blacklist | POST | 拉黑用户 |
| /api/admin/blacklist/{userId} | DELETE | 解封用户 |
| /api/admin/courier-applications | GET | 配送员申请列表 |
| /api/admin/courier-applications/{id}/approve | PUT | 通过申请 |
| /api/admin/courier-applications/{id}/reject | PUT | 拒绝申请 |
| /api/admin/complaints | GET | 投诉列表 |
| /api/admin/complaints/{id} | GET | 投诉详情 |
| /api/admin/complaints/{id}/handle | POST | 处理投诉 |
| /api/admin/goods-types | GET | 物品类型列表（含价格规则） |
| /api/admin/goods-types | POST | 创建物品类型+价格规则 |
| /api/admin/goods-types/{id} | PUT | 更新物品类型+价格规则 |
| /api/admin/goods-types/{id} | DELETE | 删除物品类型+价格规则 |
| /api/admin/pricing/rules | GET | 价格规则列表 |
| /api/admin/pricing/rules | POST | 创建价格规则 |
| /api/admin/pricing/rules/{id} | PUT | 更新价格规则 |
| /api/admin/pricing/rules/{id} | DELETE | 删除价格规则 |
| /api/admin/reports | GET | 数据报表 |

---

## 第九章 Redis数据结构总览

| Key Pattern | 类型 | TTL | 用途 |
|-------------|------|-----|------|
| `access_token:{token}` | String | 1小时 | JWT Access Token |
| `refresh_token:{token}` | String | 7天 | JWT Refresh Token |
| `blacklist:{token}` | String | token剩余有效期 | 失效Token黑名单 |
| `user_blacklist:{username}` | String | 无过期 | 用户封禁信息 |
| `captcha:{captchaId}` | String | - | 图形验证码 |
| `courier:status:{id}` | String | 240分钟 | 配送员状态 |
| `courier:location:{id}` | String | 240分钟 | 配送员位置 |
| `courier:city:{id}` | String | 240分钟 | 配送员城市 |
| `courier:update:{id}` | String | 240分钟 | 最后更新时间 |
| `courier:geo:{城市}` | Geo | 240分钟 | 配送员地理位置索引 |
| `couriers:available` | Set | - | 可分配配送员列表 |
| `courier:order_count:{id}` | String | 4小时 | 配送员在送订单数 |
| `courier:orders:{id}` | Set | 4小时 | 配送员在送订单集合 |
| `orders:grab_pool:{城市}:{区域}` | Set | - | 抢单池 |
| `orders:grab_lock:{orderId}` | String | 30秒 | 抢单分布式锁 |
| `track:order:{id}` | List | 2小时 | 订单轨迹点列表 |
| `track:latest:{id}` | String | 2小时 | 订单最新位置 |
| `track:kalman:{id}` | String | 2小时 | 卡尔曼滤波状态 |

---

## 附录

### 附录A 订单状态码对照表

| 状态码 | 中文描述 | 用户端展示 | 可执行操作 |
|--------|----------|-----------|-----------|
| pending | 待支付 | 待支付 | 支付、取消 |
| paid | 已支付 | 已支付 | 取消（自动退款） |
| awaiting_courier_confirm | 配送员待确认 | 已支付（不暴露内部状态） | - |
| awaiting_pickup | 待揽件 | 已接单 | - |
| picked_up | 已揽件 | 已揽件 | - |
| in_transit | 运输中 | 配送中（地图显示车辆） | - |
| delivered | 已送达 | 已送达 | 确认收货 |
| completed | 已完成 | 已完成 | 评价、投诉 |
| cancelled | 已取消 | 已取消 | - |
| exception | 异常 | 异常 | 管理员处理 |

### 附录B 投诉类型对照表

| type | 中文描述 |
|------|---------|
| service | 服务态度问题 |
| delay | 配送延误 |
| damage | 物品损坏 |
| lost | 物品丢失 |
| other | 其他问题 |

### 附录C 配送员状态对照表

| status | 中文描述 | 说明 |
|--------|---------|------|
| idle | 空闲 | 可接新单，在可分配列表中 |
| busy | 忙碌 | 正在配送，不在可分配列表 |
| full | 订单已满 | 前端显示idle，达到5单上限 |
| offline | 离线 | 不在工作 |

### 附录D 配送员申请状态

| status | 中文描述 | 说明 |
|--------|---------|------|
| pending | 待审核 | 等待管理员审核 |
| approved | 已通过 | 用户角色改为courier，状态改为1 |
| rejected | 已拒绝 | 记录拒绝原因，用户状态改为0 |

### 附录E 订单类型

| order_type | 中文描述 | 价格系数 |
|------------|---------|---------|
| standard | 标准配送 | 1.0x |
| express | 加急配送 | 1.5x |
| same_day | 当日达 | 2.0x |

### 附录F 支付方式

| pay_method | 中文描述 | 说明 |
|------------|---------|------|
| mock | 模拟支付 | 当前实现，500ms延迟模拟 |
| wechat | 微信支付 | 预留接口 |
| alipay | 支付宝 | 预留接口 |

### 附录G 流水类型

| type | 中文描述 | 金额方向 | 说明 |
|------|---------|---------|------|
| income | 配送收入 | 正 | 配送员确认送达时入账，金额=actualAmount×90% |
| payment | 订单支付 | 负 | 用户支付订单时扣款 |
| refund | 退款 | 正 | 取消订单或管理员退款时入账 |
| recharge | 充值 | 正 | 用户充值钱包 |

### 附录H 异常类型

| exception_type | 中文描述 | 物品位置 | 允许动作 |
|----------------|---------|---------|---------|
| courier_not_responding | 配送员未响应 | 寄件人处 | reassign, cancel |
| pickup_pending_issue | 揽件受阻 | 寄件人处 | reassign, cancel |
| pickup_issue | 揽件异常 | 配送员手上 | continue, cancel |
| transit_issue | 运输异常 | 配送员手上 | cancel |
| goods_lost | 快件灭失 | 不可恢复 | cancel |
| goods_damaged | 货品损毁 | 不可恢复 | cancel |
| force_majeure | 不可抗力 | 不可恢复 | cancel |

### 附录I 收入分配

| 角色 | 分配比例 | 计算方式 |
|------|---------|---------|
| 配送员 | 90% | actualAmount × 0.90 |
| 平台 | 10% | actualAmount × 0.10 |
