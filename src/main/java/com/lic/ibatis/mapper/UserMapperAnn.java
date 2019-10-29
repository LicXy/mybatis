package com.lic.ibatis.mapper;

import com.lic.ibatis.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


public interface UserMapperAnn {

  @Select("select * from user where id = #{id}")
  User getUserById(@Param("id") int id);
}
