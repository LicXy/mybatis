package com.lic.ibatis.service;

import com.lic.ibatis.entity.User;

public interface UserService {
  User query(int id);
}
