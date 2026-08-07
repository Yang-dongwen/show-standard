# 数据库脚本已迁至 Flyway

| 产品线 | 路径 |
|--------|------|
| 本地 SQLite | `src/main/resources/db/migration/sqlite/` |
| 云 MySQL | `src/main/resources/db/migration/mysql/` |

- 版本脚本命名：`V{n}__description.sql`
- 已发布脚本禁止修改；只追加新版本
- 配置见 `application.yml` / `application-cloud.yml` 中 `spring.flyway`
