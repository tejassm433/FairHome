package com.fairhome;

import com.fairhome.config.FairHomeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FairHomeProperties.class)
public class FairhomeApplication {

    private static final Logger log = LoggerFactory.getLogger(FairhomeApplication.class);

    public static void main(String[] args) {
        log.debug("FairHome : FairhomeApplication : in method main : START");
        log.info("FairHome : FairhomeApplication : in method main : starting Spring Boot application");
        SpringApplication.run(FairhomeApplication.class, args);
        log.debug("FairHome : FairhomeApplication : in method main : END");
    }
}
