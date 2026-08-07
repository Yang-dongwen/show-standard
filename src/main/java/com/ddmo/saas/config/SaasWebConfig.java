package com.ddmo.saas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SaaS 静态资源挂载在 /saas/，与 C 端根路径静态资源隔离。
 */
@Configuration
public class SaasWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/saas", "/saas/");
        registry.addViewController("/saas/").setViewName("forward:/saas/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/saas/**")
            .addResourceLocations("classpath:/static-saas/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    Resource requested = location.createRelative(resourcePath);
                    if (requested.exists() && requested.isReadable()) {
                        return requested;
                    }
                    // SPA hash 路由：未知路径回退 index.html
                    return new ClassPathResource("/static-saas/index.html");
                }
            });
    }
}
