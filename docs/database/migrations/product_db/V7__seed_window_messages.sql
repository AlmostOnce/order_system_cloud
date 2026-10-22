-- 仅补齐当前已有窗口的空描述，不覆盖后续商家编辑的内容。
USE product_db;

UPDATE dining_windows
SET description = '欢迎来一楼窗口，选一份喜欢的饭菜吧。\n口味偏好或忌口，请在下单时填写备注。\n用餐高峰请留意订单状态，祝你用餐愉快！'
WHERE window_id = 1 AND (description IS NULL OR TRIM(description) = '');
