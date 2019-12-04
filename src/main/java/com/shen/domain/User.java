package com.shen.domain;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * @author shenjianeng
 * @date 2019/12/4
 */
@Data
@Entity
@Table(name = "user_profile")
public class User {

    @Id
    private Long id;
    private String nickname;
}
