package com.shen.refreshscope;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author shenjianeng
 * @date 2019/12/5
 */
@RestController
public class UserController {

    private UserBean userBean;

    @Autowired
    private ContextRefresher contextRefresher;

    @Autowired
    public void setUserBean(UserBean userBean) {
        this.userBean = userBean;
        String name = userBean.getName();
    }

    @GetMapping("/user")
    public String refresh() {
        contextRefresher.refresh();
        return userBean.toString();
    }


    @RefreshScope
    @Component
    @Data
    public static class UserBean {
        private volatile Integer id;
        private String name;

        public UserBean() {
            id = ThreadLocalRandom.current().nextInt(100);
            name = "微信公众号:Coder小黑";
        }

        @PostConstruct
        public void init() {
            System.out.println("init:" + this);
        }

        @PreDestroy
        public void destroy() {
            System.out.println("destroy:" + this);
        }


    }


}
