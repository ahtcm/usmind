package com.usmind;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.usmind.mapper")
public class UsmindApplication {
    public static void main(String[] args) {
        SpringApplication.run(UsmindApplication.class, args);
    }
}
