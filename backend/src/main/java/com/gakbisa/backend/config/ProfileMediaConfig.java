package com.gakbisa.backend.config;

import com.gakbisa.backend.service.ProfileMediaService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProfileMediaConfig implements WebMvcConfigurer {

    private final ProfileMediaService profileMedia;

    public ProfileMediaConfig(ProfileMediaService profileMedia) {
        this.profileMedia = profileMedia;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/profile/**")
            .addResourceLocations(profileMedia.root().toUri().toString() + "/");
    }
}
