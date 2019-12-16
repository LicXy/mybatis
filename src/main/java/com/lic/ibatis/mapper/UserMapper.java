package com.lic.ibatis.mapper;

import com.lic.ibatis.entity.User;

public interface UserMapper {

  User getUserById(int id);

  User getUserById2(int id);
}
