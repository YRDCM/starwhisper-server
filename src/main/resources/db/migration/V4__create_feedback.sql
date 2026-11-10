-- ============================================================
-- V4：用户反馈表
--
-- 背景：这个小程序此前没有任何用户反馈通道——昵称是系统自动生成的，
-- 联系不上用户；订阅消息模板为空，推不了；也没有客服入口。
-- 结果就是"看得到数字，问不到人"。
--
-- 这张表让游客也能留一句话（哪怕不登录），是建立触达通道的第一步。
-- ============================================================
CREATE TABLE IF NOT EXISTS feedback (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NULL COMMENT '登录用户 id；游客为 NULL',
  openid     VARCHAR(64)  NULL COMMENT '登录用户的 openid；游客为 NULL',
  ip         VARCHAR(64)  NULL COMMENT '提交来源 IP，用于限频与排查',
  page       VARCHAR(64)  NULL COMMENT '用户当时的页面（如 tarot / fortune）',
  content    VARCHAR(1000) NOT NULL COMMENT '反馈正文',
  contact    VARCHAR(128) NULL COMMENT '用户自留的联系方式（可选）',
  created_at DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_feedback_created (created_at),
  KEY idx_feedback_ip_created (ip, created_at),
  KEY idx_feedback_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户反馈';
