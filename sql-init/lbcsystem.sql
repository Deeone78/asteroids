SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";

START TRANSACTION;

SET time_zone = "+00:00";

CREATE TABLE IF NOT EXISTS `users` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `username` varchar(50) NOT NULL,
    `password` varchar(255) NOT NULL,
    `role` enum('ADMIN', 'USER', 'MANAGER') NOT NULL,
    `status` tinyint(1) DEFAULT 1,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `files` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `filename` varchar(255) NOT NULL,
    `filesize` bigint(20) NOT NULL,
    `upload_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `user_id` int(11) NOT NULL,
    `is_locked` int(11) DEFAULT 0,
    `checksum` bigint(20) DEFAULT 0,
    PRIMARY KEY (`id`),
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `file_permissions` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `file_id` int(11) NOT NULL,
    `shared_with_user_id` int(11) NOT NULL,
    `permission_type` VARCHAR(50) NOT NULL,
    `granted_by_id` int(11) NOT NULL,
    `granted_at` timestamp DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    FOREIGN KEY (`file_id`) REFERENCES `files` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`shared_with_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`granted_by_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    UNIQUE KEY `unique_grant` (
        `file_id`,
        `shared_with_user_id`,
        `permission_type`
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `logs` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `user_id` int(11) NOT NULL,
    `event_type` varchar(50) NOT NULL,
    `details` text,
    `event_timestamp` timestamp DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO
    `users` (
        `id`,
        `username`,
        `password`,
        `role`
    )
VALUES (
        1,
        'admin',
        '/vHYvfJFrIVs1mCucDXJOw==',
        'ADMIN'
    ),
    (
        2,
        'user1',
        '9Ofyud03J8GI6I1sbCd0Bg==',
        'USER'
    );

INSERT IGNORE INTO
    `files` (
        `id`,
        `filename`,
        `filesize`,
        `user_id`,
        `checksum`
    )
VALUES (
        1,
        'Project_Proposal.pdf',
        1048576,
        2,
        123456789
    ),
    (
        2,
        'Budget_Sheet.xlsx',
        512000,
        2,
        987654321
    );

COMMIT;