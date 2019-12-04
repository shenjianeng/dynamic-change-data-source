package com.shen;

import com.shen.example1.DataSourceRefresher;
import com.shen.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;


@SpringBootApplication
public class AppApplication {


    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }


    @Bean
    public CommandLineRunner dataSourceRefresherRunner(UserRepository userRepository,
                                                       DataSourceRefresher dataSourceRefresher) {
        return args -> {
            System.out.println(userRepository.findById(1L).get());

            DataSourceProperties properties = new DataSourceProperties();
            //修改属性
            properties.setUrl("jdbc:mysql:///ssms");
            properties.setUsername("root");
            properties.setPassword("admin");
            HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                    .type(HikariDataSource.class).build();

            dataSourceRefresher.refreshDataSource(dataSource);

            System.out.println(userRepository.findById(1L).get());
        };
    }


}




