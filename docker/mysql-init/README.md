# MySQL 初始化说明

`docker-compose.yml` 中的 `mysql:8.0` 会通过环境变量自动创建库 `show` 与用户 `show`。

若在本机直接装 MySQL（不用 Docker），可手动执行：

```sql
CREATE DATABASE IF NOT EXISTS show DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'show'@'%' IDENTIFIED BY 'show';
GRANT ALL PRIVILEGES ON show.* TO 'show'@'%';
FLUSH PRIVILEGES;
```

应用 cloud profile 启动后由 **Flyway** 执行 `db/migration/mysql/V*.sql` 建表。
