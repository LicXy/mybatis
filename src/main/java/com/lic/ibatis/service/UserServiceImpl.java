package com.lic.ibatis.service;

import com.lic.ibatis.dao.UserMapper;
import com.lic.ibatis.dao.UserMapperImpl;
import com.lic.ibatis.entity.User;

public class UserServiceImpl implements UserService{

  UserMapper userMapper=new UserMapperImpl();

  @Override
  public User query(int id) {
    return userMapper.getUserById(id);
  }
}
