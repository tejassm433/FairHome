package com.fairhome;

import com.fairhome.config.FairHomeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FairHomeProperties.class)
public class FairhomeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FairhomeApplication.class, args);
    }
}
