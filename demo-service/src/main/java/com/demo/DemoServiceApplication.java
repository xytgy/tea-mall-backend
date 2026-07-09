package com.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoServiceApplication.class, args);
    }
}

@RestController
class ProductController {
    @GetMapping("/product/{id}")
    public String getProduct(@PathVariable Long id) {
        return "商品" + id + "：特级高山龙井茶，价格298元";
    }
}
