package com.ddmo.app.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * 全局 lazy-initialization=true 时，确保 Flyway 仍在启动期执行迁移。
 */
@Configuration
@Lazy(false)
public class FlywayEagerConfig {

    @Bean
    static LazyInitializationExcludeFilter flywayLazyExclude() {
        return LazyInitializationExcludeFilter.forBeanTypes(
            Flyway.class,
            FlywayMigrationInitializer.class
        );
    }

    /**
     * 再保险：容器创建时显式 migrate（幂等，已与 history 对齐则空操作）。
     */
    @Bean
    @Lazy(false)
    FlywayMigrationInitializer forceFlywayInitializer(ObjectProvider<Flyway> flyway) {
        Flyway fw = flyway.getIfAvailable();
        if (fw == null) {
            throw new IllegalStateException("Flyway bean 未创建，请检查 spring.flyway 与数据源配置");
        }
        return new FlywayMigrationInitializer(fw);
    }
}
