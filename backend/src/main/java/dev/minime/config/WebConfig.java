package dev.minime.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Local-only desktop client (Angular dev server + Electron file://).
        registry.addMapping("/**").allowedOriginPatterns("*").allowedMethods("*");
    }
}
