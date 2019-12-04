package com.shen.example1;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author shenjianeng
 * @date 2019/12/4
 */
@Configuration
public class DynamicDataSourceConfiguration {

    @Bean
    public DynamicDataSource dynamicDataSource(DataSourceProperties dataSourceProperties) {
        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
        return new DynamicDataSource(dataSource);

    }


}
