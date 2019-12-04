package com.shen.example1;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class DataSourceRefresher {

    private static final int MAX_RETRY_TIMES = 10;

    @Autowired
    private DynamicDataSource dynamicDataSource;

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public synchronized void refreshDataSource(DataSource newDataSource) {
        try {
            log.info("refresh data source....");
            DataSource oldDataSource = dynamicDataSource.getAndSetDataSource(newDataSource);
            shutdownDataSourceAsync(oldDataSource);
        } catch (Throwable ex) {
            log.error("refresh data source error", ex);
        }
    }


    private void shutdownDataSourceAsync(DataSource dataSource) {
        scheduledExecutorService.execute(() -> doShutdownDataSource(dataSource));
    }


    /**
     * https://github.com/brettwooldridge/HikariCP/issues/742
     */
    private void doShutdownDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            int retryTimes = 0;
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            while (poolBean.getActiveConnections() > 0 && retryTimes <= MAX_RETRY_TIMES) {
                try {
                    poolBean.softEvictConnections();
                    sleep1Second();
                } catch (Exception e) {
                    log.warn("doShutdownDataSource error ", e);
                } finally {
                    retryTimes++;
                }
            }
            hikariDataSource.close();
            log.info("shutdown data source success");
        }

        // TODO other  DataSource

    }


    private void sleep1Second() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            //ignore
        }
    }
}
