-- 本机测试目录：3 个食堂、9 个窗口，每个窗口新增 8 道菜。
-- 可重复执行；保留原菜品、价格、订单关联及现有库存，不创建虚假销量或订单。
-- 执行前备份 product_db 与 stock_db，执行后失效菜单缓存与布隆过滤器旧索引。
SET NAMES utf8mb4;
USE product_db;
CREATE TEMPORARY TABLE seed_windows (window_code VARCHAR(32) PRIMARY KEY, restaurant_code VARCHAR(32), window_name VARCHAR(64), location VARCHAR(255), description VARCHAR(500), sort_order INT);
CREATE TEMPORARY TABLE seed_dishes (window_code VARCHAR(32), product_code VARCHAR(64), product_name VARCHAR(128), description VARCHAR(500), price DECIMAL(10,2), sort_order INT, category_name VARCHAR(64), PRIMARY KEY(window_code,product_code));
CREATE TEMPORARY TABLE seed_guard (valid TINYINT NOT NULL CHECK(valid=1));
INSERT INTO seed_windows VALUES
('W-DEMO-01', 'R0001', '家常小炒', '一楼东侧', '现点现炒，少油少盐可以备注，米饭管饱。', 10),
('DEV-C1-W2', 'R0001', '麻辣香锅', '一楼西侧', '麻辣、微辣和酱香任选，食材当天准备。', 20),
('DEV-C1-W3', 'R0001', '手工面馆', '二楼东侧', '汤面现煮，面条筋道，加不加葱香菜请备注。', 30),
('DEV-C2-W1', 'DEV-R02', '营养蒸菜', '一楼东侧', '一人一份现蒸上桌，清淡口味也有好选择。', 10),
('DEV-C2-W2', 'DEV-R02', '铁板烧饭', '一楼西侧', '铁板现做趁热吃，酱汁浓淡可按喜好调整。', 20),
('DEV-C2-W3', 'DEV-R02', '特色米粉', '二楼南侧', '热汤鲜熬，米粉现烫，酸辣程度随你挑。', 30),
('DEV-C3-W1', 'DEV-R03', '招牌盖饭', '一楼东侧', '肉菜搭配一碗满足，忙碌的时候也要好好吃饭。', 10),
('DEV-C3-W2', 'DEV-R03', '饺子馄饨', '一楼西侧', '饺子馄饨现煮现盛，热汤暖胃，蘸料自取。', 20),
('DEV-C3-W3', 'DEV-R03', '轻食简餐', '二楼北侧', '谷物蔬菜搭配优质蛋白，酱汁单放，轻松吃一餐。', 30);
INSERT INTO seed_dishes VALUES
('W-DEMO-01', 'DEV-C1-W1-P01', '番茄炒蛋', '番茄炒蛋，现做热食，具体口味与配料可在下单时备注。', 10, 10, '招牌菜品'),
('W-DEMO-01', 'DEV-C1-W1-P02', '鱼香肉丝', '鱼香肉丝，现做热食，具体口味与配料可在下单时备注。', 14, 20, '招牌菜品'),
('W-DEMO-01', 'DEV-C1-W1-P03', '宫保鸡丁', '宫保鸡丁，现做热食，具体口味与配料可在下单时备注。', 14, 30, '招牌菜品'),
('W-DEMO-01', 'DEV-C1-W1-P04', '土豆烧牛肉', '土豆烧牛肉，现做热食，具体口味与配料可在下单时备注。', 18, 40, '招牌菜品'),
('W-DEMO-01', 'DEV-C1-W1-P05', '香菇滑鸡', '香菇滑鸡，现做热食，具体口味与配料可在下单时备注。', 15, 50, '人气推荐'),
('W-DEMO-01', 'DEV-C1-W1-P06', '农家小炒肉', '农家小炒肉，现做热食，具体口味与配料可在下单时备注。', 16, 60, '人气推荐'),
('W-DEMO-01', 'DEV-C1-W1-P07', '红烧茄子', '红烧茄子，现做热食，具体口味与配料可在下单时备注。', 10, 70, '人气推荐'),
('W-DEMO-01', 'DEV-C1-W1-P08', '手撕包菜', '手撕包菜，现做热食，具体口味与配料可在下单时备注。', 8, 80, '人气推荐'),
('DEV-C1-W2', 'DEV-C1-W2-P01', '招牌麻辣香锅', '招牌麻辣香锅，现做热食，具体口味与配料可在下单时备注。', 18, 10, '招牌菜品'),
('DEV-C1-W2', 'DEV-C1-W2-P02', '鲜蔬香锅', '鲜蔬香锅，现做热食，具体口味与配料可在下单时备注。', 14, 20, '招牌菜品'),
('DEV-C1-W2', 'DEV-C1-W2-P03', '鸡肉香锅', '鸡肉香锅，现做热食，具体口味与配料可在下单时备注。', 17, 30, '招牌菜品'),
('DEV-C1-W2', 'DEV-C1-W2-P04', '牛肉香锅', '牛肉香锅，现做热食，具体口味与配料可在下单时备注。', 22, 40, '招牌菜品'),
('DEV-C1-W2', 'DEV-C1-W2-P05', '丸子香锅', '丸子香锅，现做热食，具体口味与配料可在下单时备注。', 16, 50, '人气推荐'),
('DEV-C1-W2', 'DEV-C1-W2-P06', '肥牛香锅', '肥牛香锅，现做热食，具体口味与配料可在下单时备注。', 21, 60, '人气推荐'),
('DEV-C1-W2', 'DEV-C1-W2-P07', '鸡肉牛肉双拼香锅', '鸡肉牛肉双拼香锅，现做热食，具体口味与配料可在下单时备注。', 23, 70, '人气推荐'),
('DEV-C1-W2', 'DEV-C1-W2-P08', '酱香菌菇香锅', '酱香菌菇香锅，现做热食，具体口味与配料可在下单时备注。', 16, 80, '人气推荐'),
('DEV-C1-W3', 'DEV-C1-W3-P01', '红烧牛肉面', '红烧牛肉面，现做热食，具体口味与配料可在下单时备注。', 16, 10, '招牌菜品'),
('DEV-C1-W3', 'DEV-C1-W3-P02', '番茄鸡蛋面', '番茄鸡蛋面，现做热食，具体口味与配料可在下单时备注。', 11, 20, '招牌菜品'),
('DEV-C1-W3', 'DEV-C1-W3-P03', '香菇鸡汤面', '香菇鸡汤面，现做热食，具体口味与配料可在下单时备注。', 14, 30, '招牌菜品'),
('DEV-C1-W3', 'DEV-C1-W3-P04', '老北京炸酱面', '老北京炸酱面，现做热食，具体口味与配料可在下单时备注。', 13, 40, '招牌菜品'),
('DEV-C1-W3', 'DEV-C1-W3-P05', '重庆小面', '重庆小面，现做热食，具体口味与配料可在下单时备注。', 10, 50, '人气推荐'),
('DEV-C1-W3', 'DEV-C1-W3-P06', '酸菜肉丝面', '酸菜肉丝面，现做热食，具体口味与配料可在下单时备注。', 13, 60, '人气推荐'),
('DEV-C1-W3', 'DEV-C1-W3-P07', '葱油拌面', '葱油拌面，现做热食，具体口味与配料可在下单时备注。', 10, 70, '人气推荐'),
('DEV-C1-W3', 'DEV-C1-W3-P08', '雪菜肉丝面', '雪菜肉丝面，现做热食，具体口味与配料可在下单时备注。', 12, 80, '人气推荐'),
('DEV-C2-W1', 'DEV-C2-W1-P01', '粉蒸排骨饭', '粉蒸排骨饭，现做热食，具体口味与配料可在下单时备注。', 18, 10, '招牌菜品'),
('DEV-C2-W1', 'DEV-C2-W1-P02', '香菇蒸鸡饭', '香菇蒸鸡饭，现做热食，具体口味与配料可在下单时备注。', 16, 20, '招牌菜品'),
('DEV-C2-W1', 'DEV-C2-W1-P03', '梅菜扣肉饭', '梅菜扣肉饭，现做热食，具体口味与配料可在下单时备注。', 17, 30, '招牌菜品'),
('DEV-C2-W1', 'DEV-C2-W1-P04', '剁椒鱼块饭', '剁椒鱼块饭，现做热食，具体口味与配料可在下单时备注。', 18, 40, '招牌菜品'),
('DEV-C2-W1', 'DEV-C2-W1-P05', '清蒸肉丸饭', '清蒸肉丸饭，现做热食，具体口味与配料可在下单时备注。', 15, 50, '人气推荐'),
('DEV-C2-W1', 'DEV-C2-W1-P06', '豆豉蒸肉饭', '豆豉蒸肉饭，现做热食，具体口味与配料可在下单时备注。', 16, 60, '人气推荐'),
('DEV-C2-W1', 'DEV-C2-W1-P07', '南瓜蒸肉饭', '南瓜蒸肉饭，现做热食，具体口味与配料可在下单时备注。', 15, 70, '人气推荐'),
('DEV-C2-W1', 'DEV-C2-W1-P08', '蒸蛋鸡肉饭', '蒸蛋鸡肉饭，现做热食，具体口味与配料可在下单时备注。', 14, 80, '人气推荐'),
('DEV-C2-W2', 'DEV-C2-W2-P01', '黑椒牛肉铁板饭', '黑椒牛肉铁板饭，现做热食，具体口味与配料可在下单时备注。', 20, 10, '招牌菜品'),
('DEV-C2-W2', 'DEV-C2-W2-P02', '照烧鸡腿铁板饭', '照烧鸡腿铁板饭，现做热食，具体口味与配料可在下单时备注。', 17, 20, '招牌菜品'),
('DEV-C2-W2', 'DEV-C2-W2-P03', '孜然鸡肉铁板饭', '孜然鸡肉铁板饭，现做热食，具体口味与配料可在下单时备注。', 16, 30, '招牌菜品'),
('DEV-C2-W2', 'DEV-C2-W2-P04', '香煎猪排铁板饭', '香煎猪排铁板饭，现做热食，具体口味与配料可在下单时备注。', 18, 40, '招牌菜品'),
('DEV-C2-W2', 'DEV-C2-W2-P05', '金针菇肥牛铁板饭', '金针菇肥牛铁板饭，现做热食，具体口味与配料可在下单时备注。', 21, 50, '人气推荐'),
('DEV-C2-W2', 'DEV-C2-W2-P06', '双拼铁板饭', '双拼铁板饭，现做热食，具体口味与配料可在下单时备注。', 22, 60, '人气推荐'),
('DEV-C2-W2', 'DEV-C2-W2-P07', '黑椒肉片铁板饭', '黑椒肉片铁板饭，现做热食，具体口味与配料可在下单时备注。', 17, 70, '人气推荐'),
('DEV-C2-W2', 'DEV-C2-W2-P08', '菌菇豆腐铁板饭', '菌菇豆腐铁板饭，现做热食，具体口味与配料可在下单时备注。', 14, 80, '人气推荐'),
('DEV-C2-W3', 'DEV-C2-W3-P01', '招牌牛肉粉', '招牌牛肉粉，现做热食，具体口味与配料可在下单时备注。', 15, 10, '招牌菜品'),
('DEV-C2-W3', 'DEV-C2-W3-P02', '酸辣鸡杂粉', '酸辣鸡杂粉，现做热食，具体口味与配料可在下单时备注。', 14, 20, '招牌菜品'),
('DEV-C2-W3', 'DEV-C2-W3-P03', '香菇肉丝粉', '香菇肉丝粉，现做热食，具体口味与配料可在下单时备注。', 12, 30, '招牌菜品'),
('DEV-C2-W3', 'DEV-C2-W3-P04', '番茄肥牛粉', '番茄肥牛粉，现做热食，具体口味与配料可在下单时备注。', 17, 40, '招牌菜品'),
('DEV-C2-W3', 'DEV-C2-W3-P05', '卤肉米粉', '卤肉米粉，现做热食，具体口味与配料可在下单时备注。', 14, 50, '人气推荐'),
('DEV-C2-W3', 'DEV-C2-W3-P06', '酸豆角肉末粉', '酸豆角肉末粉，现做热食，具体口味与配料可在下单时备注。', 12, 60, '人气推荐'),
('DEV-C2-W3', 'DEV-C2-W3-P07', '三鲜汤粉', '三鲜汤粉，现做热食，具体口味与配料可在下单时备注。', 15, 70, '人气推荐'),
('DEV-C2-W3', 'DEV-C2-W3-P08', '麻辣牛杂粉', '麻辣牛杂粉，现做热食，具体口味与配料可在下单时备注。', 17, 80, '人气推荐'),
('DEV-C3-W1', 'DEV-C3-W1-P01', '黄焖鸡米饭', '黄焖鸡米饭，现做热食，具体口味与配料可在下单时备注。', 17, 10, '招牌菜品'),
('DEV-C3-W1', 'DEV-C3-W1-P02', '红烧肉盖饭', '红烧肉盖饭，现做热食，具体口味与配料可在下单时备注。', 18, 20, '招牌菜品'),
('DEV-C3-W1', 'DEV-C3-W1-P03', '咖喱鸡肉盖饭', '咖喱鸡肉盖饭，现做热食，具体口味与配料可在下单时备注。', 16, 30, '招牌菜品'),
('DEV-C3-W1', 'DEV-C3-W1-P04', '土豆牛腩盖饭', '土豆牛腩盖饭，现做热食，具体口味与配料可在下单时备注。', 20, 40, '招牌菜品'),
('DEV-C3-W1', 'DEV-C3-W1-P05', '青椒肉丝盖饭', '青椒肉丝盖饭，现做热食，具体口味与配料可在下单时备注。', 14, 50, '人气推荐'),
('DEV-C3-W1', 'DEV-C3-W1-P06', '香干回锅肉盖饭', '香干回锅肉盖饭，现做热食，具体口味与配料可在下单时备注。', 16, 60, '人气推荐'),
('DEV-C3-W1', 'DEV-C3-W1-P07', '麻婆豆腐盖饭', '麻婆豆腐盖饭，现做热食，具体口味与配料可在下单时备注。', 11, 70, '人气推荐'),
('DEV-C3-W1', 'DEV-C3-W1-P08', '豉汁鸡丁盖饭', '豉汁鸡丁盖饭，现做热食，具体口味与配料可在下单时备注。', 15, 80, '人气推荐'),
('DEV-C3-W2', 'DEV-C3-W2-P01', '猪肉白菜水饺', '猪肉白菜水饺，现做热食，具体口味与配料可在下单时备注。', 13, 10, '招牌菜品'),
('DEV-C3-W2', 'DEV-C3-W2-P02', '韭菜鸡蛋水饺', '韭菜鸡蛋水饺，现做热食，具体口味与配料可在下单时备注。', 12, 20, '招牌菜品'),
('DEV-C3-W2', 'DEV-C3-W2-P03', '香菇猪肉水饺', '香菇猪肉水饺，现做热食，具体口味与配料可在下单时备注。', 14, 30, '招牌菜品'),
('DEV-C3-W2', 'DEV-C3-W2-P04', '芹菜牛肉水饺', '芹菜牛肉水饺，现做热食，具体口味与配料可在下单时备注。', 17, 40, '招牌菜品'),
('DEV-C3-W2', 'DEV-C3-W2-P05', '鲜肉小馄饨', '鲜肉小馄饨，现做热食，具体口味与配料可在下单时备注。', 11, 50, '人气推荐'),
('DEV-C3-W2', 'DEV-C3-W2-P06', '鲜虾大馄饨', '鲜虾大馄饨，现做热食，具体口味与配料可在下单时备注。', 18, 60, '人气推荐'),
('DEV-C3-W2', 'DEV-C3-W2-P07', '红油抄手', '红油抄手，现做热食，具体口味与配料可在下单时备注。', 14, 70, '人气推荐'),
('DEV-C3-W2', 'DEV-C3-W2-P08', '酸汤水饺', '酸汤水饺，现做热食，具体口味与配料可在下单时备注。', 15, 80, '人气推荐'),
('DEV-C3-W3', 'DEV-C3-W3-P01', '香煎鸡胸杂粮饭', '香煎鸡胸杂粮饭，现做热食，具体口味与配料可在下单时备注。', 18, 10, '招牌菜品'),
('DEV-C3-W3', 'DEV-C3-W3-P02', '黑椒牛肉杂粮饭', '黑椒牛肉杂粮饭，现做热食，具体口味与配料可在下单时备注。', 22, 20, '招牌菜品'),
('DEV-C3-W3', 'DEV-C3-W3-P03', '照烧鸡腿蔬菜饭', '照烧鸡腿蔬菜饭，现做热食，具体口味与配料可在下单时备注。', 19, 30, '招牌菜品'),
('DEV-C3-W3', 'DEV-C3-W3-P04', '香煎豆腐藜麦饭', '香煎豆腐藜麦饭，现做热食，具体口味与配料可在下单时备注。', 16, 40, '招牌菜品'),
('DEV-C3-W3', 'DEV-C3-W3-P05', '虾仁玉米轻食碗', '虾仁玉米轻食碗，现做热食，具体口味与配料可在下单时备注。', 23, 50, '人气推荐'),
('DEV-C3-W3', 'DEV-C3-W3-P06', '鸡蛋金枪鱼饭团餐', '鸡蛋金枪鱼饭团餐，现做热食，具体口味与配料可在下单时备注。', 20, 60, '人气推荐'),
('DEV-C3-W3', 'DEV-C3-W3-P07', '菌菇鸡肉荞麦面', '菌菇鸡肉荞麦面，现做热食，具体口味与配料可在下单时备注。', 18, 70, '人气推荐'),
('DEV-C3-W3', 'DEV-C3-W3-P08', '时蔬鸡肉沙拉餐', '时蔬鸡肉沙拉餐，现做热食，具体口味与配料可在下单时备注。', 17, 80, '人气推荐');
START TRANSACTION;
INSERT INTO restaurants (restaurant_code,restaurant_name,location,notice,sort_order,status) VALUES
('R0001', '桂园食堂', '校园东区', '家常风味，热饭热菜，欢迎来吃饭。', 10, 1),
('DEV-R02', '桃园食堂', '校园中心区', '蒸菜、铁板和米粉，总有一口适合你。', 20, 1),
('DEV-R03', '梅园食堂', '校园西区', '盖饭、饺子和轻食，每天换个口味。', 30, 1)
ON DUPLICATE KEY UPDATE restaurant_name=VALUES(restaurant_name),location=VALUES(location),notice=VALUES(notice),sort_order=VALUES(sort_order),status=1;
INSERT INTO dining_windows (restaurant_id,window_code,window_name,location,description,sort_order,status,is_open)
SELECT r.restaurant_id,s.window_code,s.window_name,s.location,s.description,s.sort_order,1,1 FROM seed_windows s JOIN restaurants r ON r.restaurant_code=s.restaurant_code
ON DUPLICATE KEY UPDATE restaurant_id=VALUES(restaurant_id),window_name=VALUES(window_name),location=VALUES(location),description=VALUES(description),sort_order=VALUES(sort_order),status=1,is_open=1;
INSERT INTO product_categories (window_id,category_name,sort_order,status)
SELECT DISTINCT w.window_id,d.category_name,IF(d.category_name='招牌菜品',10,20),1 FROM seed_dishes d JOIN dining_windows w ON w.window_code=d.window_code
ON DUPLICATE KEY UPDATE category_id=product_categories.category_id;
INSERT INTO products (window_id,category_id,product_code,product_name,description,price,sale_status,sort_order)
SELECT w.window_id,c.category_id,d.product_code,d.product_name,d.description,d.price,1,d.sort_order FROM seed_dishes d
JOIN dining_windows w ON w.window_code=d.window_code JOIN product_categories c ON c.window_id=w.window_id AND c.category_name=d.category_name
ON DUPLICATE KEY UPDATE product_id=products.product_id;
INSERT INTO stock_db.stock (window_id,product_id,total_qty,available_qty,version,is_deleted)
SELECT p.window_id,p.product_id,80+MOD(CRC32(p.product_code),81),80+MOD(CRC32(p.product_code),81),0,0
FROM products p JOIN seed_dishes d ON d.product_code=p.product_code JOIN dining_windows w ON w.window_id=p.window_id AND w.window_code=d.window_code
LEFT JOIN stock_db.stock old ON old.window_id=p.window_id AND old.product_id=p.product_id WHERE old.stock_id IS NULL
ON DUPLICATE KEY UPDATE stock_id=stock_db.stock.stock_id;
INSERT INTO seed_guard SELECT IF(COUNT(*)=72,1,0) FROM seed_dishes d JOIN dining_windows w ON w.window_code=d.window_code
JOIN products p ON p.window_id=w.window_id AND p.product_code=d.product_code JOIN stock_db.stock s ON s.window_id=p.window_id AND s.product_id=p.product_id WHERE s.is_deleted=0;
COMMIT;
SELECT r.restaurant_name,w.window_id,w.window_name,w.location,COUNT(p.product_id) AS dish_count
FROM seed_windows sw JOIN dining_windows w ON w.window_code=sw.window_code JOIN restaurants r ON r.restaurant_id=w.restaurant_id
LEFT JOIN products p ON p.window_id=w.window_id GROUP BY r.restaurant_id,w.window_id ORDER BY r.sort_order,w.sort_order;
DROP TEMPORARY TABLE seed_guard;
DROP TEMPORARY TABLE seed_dishes;
DROP TEMPORARY TABLE seed_windows;
