package com.hotel.system.config;

import com.hotel.system.room.RoomPhotoStorage;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RoomImageWebConfiguration implements WebMvcConfigurer {
    private final RoomPhotoStorage storage;
    public RoomImageWebConfiguration(RoomPhotoStorage storage){this.storage=storage;}
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry){
        registry.addResourceHandler("/room-images/**")
            .addResourceLocations(storage.root().toUri().toString());
    }
}