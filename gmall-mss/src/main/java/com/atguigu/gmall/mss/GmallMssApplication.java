package com.atguigu.gmall.mss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"com.atguigu.gmall"})
public class GmallMssApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmallMssApplication.class, args);
    }

}
