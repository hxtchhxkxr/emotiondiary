-- emotiondiary 초기 스키마 + 시드 데이터
-- MariaDB 컨테이너 최초 기동 시 자동 실행됨 (/docker-entrypoint-initdb.d)
-- MARIADB_DATABASE 환경변수로 이미 springstudy 가 생성된 상태에서 실행됨

/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;

USE `springstudy`;

-- ----------------------------------------------------------------
-- users
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id`         bigint(20)   NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6)  DEFAULT NULL,
  `email`      varchar(100) NOT NULL,
  `nickname`   varchar(50)  NOT NULL,
  `password`   varchar(100) NOT NULL,
  `role`       enum('ADMIN','USER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

INSERT IGNORE INTO `users` (`id`, `created_at`, `email`, `nickname`, `password`, `role`) VALUES
  (1, '2026-04-14 11:19:23.854645', 'test@example.com', '홍길동',
   '$2a$10$MSRMnUh/pv6vFv6nlUXhSu4dbZz7hwY4DkPXCzdd5HF6P3jwowa4i', 'USER');

-- ----------------------------------------------------------------
-- diary
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `diary` (
  `id`         varchar(36)   NOT NULL,
  `content`    varchar(2000) NOT NULL,
  `created_at` datetime(6)   DEFAULT NULL,
  `date`       bigint(20)    NOT NULL,
  `emotion_id` int(11)       NOT NULL,
  `updated_at` datetime(6)   DEFAULT NULL,
  `user_id`    bigint(20)    NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX_diary_user_id` (`user_id`),
  CONSTRAINT `FK_diary_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

INSERT IGNORE INTO `diary` (`id`, `content`, `created_at`, `date`, `emotion_id`, `updated_at`, `user_id`) VALUES
  ('e2c0a553-4b9a-4f5d-9dac-de7fa9cf098e', '오늘은 즐거운 하루였다',
   '2026-04-10 16:37:01.642892', 20260101, 5, '2026-04-10 16:37:01.642892', 1);

-- ----------------------------------------------------------------
-- refresh_token
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `refresh_token` (
  `id`         bigint(20)   NOT NULL AUTO_INCREMENT,
  `expires_at` datetime(6)  NOT NULL,
  `token`      varchar(512) NOT NULL,
  `user_id`    bigint(20)   NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_refresh_token_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
