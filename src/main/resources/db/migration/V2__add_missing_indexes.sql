-- ============================================================
-- V2：补齐缺失索引
--
-- 背景：这些表的数据量还小的时候完全无感，一旦有了几万行用户行为就全是全表扫描。
-- 每条 DDL 都先查 information_schema 判断索引是否已存在，重复执行也不会报错
-- （方便手工在别的环境补跑）。
-- ============================================================

-- ---------- user_history：主路径是按用户查最近记录 ----------
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'user_history'
                  AND index_name = 'idx_history_user_created');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_history_user_created ON user_history (user_id, created_at)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 按类型过滤的历史列表（TAROT / BAGUA）
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'user_history'
                  AND index_name = 'idx_history_user_type_created');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_history_user_type_created ON user_history (user_id, type, created_at)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- usage_event：后台大盘全部按 module + 时间聚合 ----------
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'usage_event'
                  AND index_name = 'idx_usage_module_created');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_usage_module_created ON usage_event (module, created_at)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'usage_event'
                  AND index_name = 'idx_usage_ip_created');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_usage_ip_created ON usage_event (ip, created_at)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'usage_event'
                  AND index_name = 'idx_usage_user_created');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_usage_user_created ON usage_event (user_id, created_at)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- user_session：后台统计按 user_id 聚合，清理任务按 expires_at 扫 ----------
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'user_session'
                  AND index_name = 'idx_session_user');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_session_user ON user_session (user_id)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'user_session'
                  AND index_name = 'idx_session_expires');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_session_expires ON user_session (expires_at)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- fortune_daily：按日期查全部星座（后台日报 / 预热） ----------
-- 现有的唯一键是 (sign_id, fortune_date)，前导列是 sign_id，
-- 所以「where fortune_date = ?」用不上它，需要单独一个索引。
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'fortune_daily'
                  AND index_name = 'idx_fortune_date');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_fortune_date ON fortune_daily (fortune_date)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- signs：按英文名/中文名查找（只有 12 行，但索引零成本） ----------
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'signs'
                  AND index_name = 'idx_signs_name_en');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_signs_name_en ON signs (name_en)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'signs'
                  AND index_name = 'idx_signs_name');
SET @ddl := IF(@exists = 0,
  'CREATE INDEX idx_signs_name ON signs (name)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- app_user.nickname 唯一约束 ----------
-- 实体上标了 @Column(unique = true)，但线上这个唯一索引**并不存在**
-- （很可能是当初 Hibernate 尝试添加时撞上了重复昵称，静默失败后继续启动）。
-- 结果就是"昵称全站唯一"只靠应用层的先查后写保证，并发注册能撞出同名。
-- 这里先把历史重复数据改名，再加唯一索引。
UPDATE app_user u
JOIN (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY nickname ORDER BY id) AS rn
  FROM app_user
  WHERE nickname IS NOT NULL AND nickname <> ''
) d ON d.id = u.id
SET u.nickname = CONCAT(LEFT(u.nickname, 55), '·', LPAD(u.id, 4, '0'))
WHERE d.rn > 1;

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'app_user'
                  AND index_name = 'uk_app_user_nickname');
SET @ddl := IF(@exists = 0,
  'ALTER TABLE app_user ADD UNIQUE KEY uk_app_user_nickname (nickname)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
