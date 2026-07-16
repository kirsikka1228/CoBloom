package com.cobloom;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.cobloom.mapper")
public class CoBloomApplication {
  public static void main(String[] args) {
    SpringApplication.run(CoBloomApplication.class, args);
  }
}
