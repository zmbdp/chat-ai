-- # 1、初始化数据库：创建nacos外接数据库chat-ai_nacos_prd和脚手架业务数据库chat-ai_prd
-- # 2、创建用户，用户名：zmbdpprd 密码：Hf@173503494
-- # 3、授予zmbdpprd用户特定权限

CREATE database if NOT EXISTS `chat-ai_nacos_prd` default character set utf8mb4 collate utf8mb4_general_ci;
CREATE database if NOT EXISTS `chat-ai_prd` default character set utf8mb4 collate utf8mb4_general_ci;
CREATE database if NOT EXISTS `chat-ai_xxljob_prd` default character set utf8mb4 collate utf8mb4_general_ci;

CREATE USER 'zmbdpprd'@'%' IDENTIFIED BY 'Hf@173503494';
grant replication slave, replication client on *.* to 'zmbdpprd'@'%';

GRANT ALL PRIVILEGES ON chat-ai_nacos_prd.* TO  'zmbdpprd'@'%';
GRANT ALL PRIVILEGES ON chat-ai_prd.* TO  'zmbdpprd'@'%';
GRANT ALL PRIVILEGES ON chat-ai_xxljob_prd.* TO  'zmbdpprd'@'%';

FLUSH PRIVILEGES;
