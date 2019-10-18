package com.lic.ibatis.test;

import com.lic.ibatis.entity.User;
import com.lic.ibatis.service.UserServiceImpl;

public class MybatisTest {
  public static void main(String[] args) {
    UserServiceImpl userService = new UserServiceImpl();
    User user = userService.query(1);
    System.out.println(user.toString());
  }
}
