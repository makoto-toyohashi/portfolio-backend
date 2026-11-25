package com.portfolio.github;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
@SpringBootApplication(scanBasePackages = "com.portfolio")
@EnableCaching
public class PortfolioApplication {
    public static void main(String[] args) { SpringApplication.run(PortfolioApplication.class, args); }
}
