package com.oj.config;

import com.oj.common.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AppProperties appProperties;

    public WebConfig(JwtInterceptor jwtInterceptor, AppProperties appProperties) {
        this.jwtInterceptor = jwtInterceptor;
        this.appProperties = appProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // All /api/** require auth EXCEPT the public ones listed.
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/submissions/meta/languages",
                        "/api/health"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origin = appProperties.getCors().getOrigin();
        // "*" cannot be used with allowCredentials; use a permissive pattern instead.
        String[] origins = "*".equals(origin) ? new String[]{"*"} : new String[]{origin};
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
