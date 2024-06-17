package com.heima.freemarker;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FreemarkerDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(FreemarkerDemoApplication.class,args);
    }
}
