package com.jhz.movielens.web;

import com.jhz.movielens.web.config.PipelineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PipelineProperties.class)
public class MovieLensAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(MovieLensAgentApplication.class, args);
    }
}
