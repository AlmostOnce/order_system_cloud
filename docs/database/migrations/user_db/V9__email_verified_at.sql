-- 历史邮箱保持未验证。执行前备份、更新全部认证服务与公共身份链。
-- 切换后旧账号必须完成密码校验及新邮箱OTP验证，不能直接填充历史验证时间。
ALTER TABLE users ADD COLUMN email_verified_at DATETIME NULL COMMENT '当前邮箱实际验证成功时间，历史账号待补验';
