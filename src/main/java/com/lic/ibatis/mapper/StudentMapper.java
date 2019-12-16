package com.lic.ibatis.mapper;

import com.lic.ibatis.entity.User;

public interface StudentMapper {

  User getStudentrById(int id);

  User getStudentrById2(int id);
}
