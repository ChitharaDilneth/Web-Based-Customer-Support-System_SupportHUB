package com.customersupport.SupportHUB;

import com.customersupport.SupportHUB.common.EarlyProductionConfigValidator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CustomerSupportApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CustomerSupportApplication.class);
        app.addInitializers(new EarlyProductionConfigValidator());
        app.run(args);
    }
}
