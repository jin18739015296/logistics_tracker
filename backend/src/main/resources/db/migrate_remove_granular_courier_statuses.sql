-- 可选：升级已有库，将已下线的订单主状态码合并回简化状态机。
-- 部署新后端前执行一次即可；全新库无需执行。

UPDATE orders
SET status = 'awaiting_pickup'
WHERE status IN ('courier_en_route_pickup', 'arrived_sender');

UPDATE orders
SET status = 'in_transit'
WHERE status = 'arrived_destination';

UPDATE delivery_tasks
SET status = 'awaiting_pickup'
WHERE status IN ('courier_en_route_pickup', 'arrived_sender');

UPDATE delivery_tasks
SET status = 'in_transit'
WHERE status = 'arrived_destination';
