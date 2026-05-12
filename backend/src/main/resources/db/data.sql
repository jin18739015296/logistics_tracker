-- ============================================
-- 初始化数据 - data.sql
-- 物流管理平台基础数据
-- 包含: 管理员用户、物品类型、价格规则
-- ============================================

USE logistics_db;

-- ============================================
-- 1. 管理员账户
-- 原始密码: Logistics@2024
-- MD5加密: 5f1d7a84d6e8f9c2b3a4d5e6f7a8b9c0
-- ============================================
INSERT INTO users (username, password, role, name, phone, email, status, create_time) 
VALUES (
    'admin', 
    '5f1d7a84d6e8f9c2b3a4d5e6f7a8b9c0', 
    'admin', 
    '系统管理员',
    '13800138000',
    'admin@logistics.com',
    1,
    NOW()
);

-- ============================================
-- 2. 物品类型 (9种常用类型)
-- ============================================
INSERT INTO goods_types (id, code, name, description, icon, sort_order, is_active, create_time) VALUES
(1, 'document', '文件资料', '合同、证件、发票等纸质文件', 'document-text', 1, 1, NOW()),
(2, 'electronics', '数码产品', '手机、电脑、平板等电子设备', 'phone-portrait', 2, 1, NOW()),
(3, 'clothing', '服装鞋帽', '衣服、鞋子、包包等服饰类', 'shirt', 3, 1, NOW()),
(4, 'food', '食品饮料', '零食、水果、饮料等食品', 'restaurant', 4, 1, NOW()),
(5, 'daily', '日用百货', '生活用品、洗护用品等', 'home', 5, 1, NOW()),
(6, 'furniture', '家具家电', '家具、电器等大件物品', 'tv', 6, 1, NOW()),
(7, 'books', '图书音像', '书籍、CD、DVD等', 'book', 7, 1, NOW()),
(8, 'medicine', '医药保健', '药品、保健品、医疗器械', 'medkit', 8, 1, NOW()),
(9, 'other', '其他物品', '不属于以上分类的物品', 'cube', 9, 1, NOW());

-- ============================================
-- 3. 按重量定价规则 (pricing_rules) - 简化版
-- 定价策略: 基础费 + 每公斤费用 (每个物品类型一条规则)
-- 数据转换说明: 取原各类型第一档价格作为新规则
-- ============================================

-- 3.1 文件资料 (轻便、易运输) - 原第一档: 0-0.5kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(1, 6.00, 4.00, 1, NOW());

-- 3.2 数码产品 (贵重、需小心) - 原第一档: 0-0.5kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(2, 10.00, 12.00, 1, NOW());

-- 3.3 服装鞋帽 (中等体积) - 原第一档: 0-1kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(3, 8.00, 3.50, 1, NOW());

-- 3.4 食品饮料 (需保鲜、快速) - 原第一档: 0-1kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(4, 9.00, 4.00, 1, NOW());

-- 3.5 日用百货 (普通物品) - 原第一档: 0-1kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(5, 7.00, 3.00, 1, NOW());

-- 3.6 家具家电 (大件、需搬运) - 原第一档: 0-5kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(6, 25.00, 5.00, 1, NOW());

-- 3.7 图书音像 (较重) - 原第一档: 0-1kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(7, 7.00, 2.50, 1, NOW());

-- 3.8 医药保健 (特殊处理) - 原第一档: 0-0.5kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(8, 12.00, 15.00, 1, NOW());

-- 3.9 其他物品 (通用) - 原第一档: 0-1kg
INSERT INTO pricing_rules (goods_type_id, base_fee, price_per_kg, is_active, create_time) VALUES
(9, 8.00, 3.50, 1, NOW());

-- ============================================
-- 4. 按距离定价规则 (distance_pricing_rules)
-- 定价策略: 阶梯式计价
-- 参考同城配送行业标准
-- ============================================
INSERT INTO distance_pricing_rules (distance_min, distance_max, base_fee, price_per_km, is_active, create_time) VALUES

-- 起步价区间 (0-3km): 城市核心区域配送
(0, 3, 12.00, 0, 1, NOW()),

-- 短距离 (3-5km): 同城近距离
(3, 5, 12.00, 2.50, 1, NOW()),

-- 中距离 (5-10km): 市区内跨区
(5, 10, 16.00, 2.00, 1, NOW()),

-- 中长距离 (10-15km): 近郊范围
(10, 15, 21.00, 1.70, 1, NOW()),

-- 较长距离 (15-20km): 远郊范围
(15, 20, 27.00, 1.50, 1, NOW()),

-- 长距离 (20-30km): 周边城市
(20, 30, 35.00, 1.30, 1, NOW()),

-- 超长距离 (30-50km): 跨市配送
(30, 50, 48.00, 1.10, 1, NOW()),

-- 特殊超远距离 (50km+): 需协商
(50, 999.99, 65.00, 0.95, 1, NOW());

-- ============================================
-- 数据插入完成
-- 统计信息:
--   管理员用户: 1 条
--   物品类型: 9 种
--   重量定价规则: 9 条 (简化版,每种类型1条规则)
--   距离定价规则: 8 个档位
-- ============================================
