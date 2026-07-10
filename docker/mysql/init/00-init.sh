#!/usr/bin/env bash
set -euo pipefail

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS cloud_tea_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS nacos_config DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'nacos'@'%' IDENTIFIED WITH mysql_native_password BY '${NACOS_MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON nacos_config.* TO 'nacos'@'%';
FLUSH PRIVILEGES;
SQL

table_count="$(mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nacos_config' AND table_name='config_info';")"
if [[ "${table_count}" == "0" ]]; then
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" nacos_config < /opt/nacos/mysql-schema.sql
fi

