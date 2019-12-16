package com.lic.ibatis.test;

import com.lic.ibatis.dao.UserMapper;
import com.lic.ibatis.entity.User;
import org.apache.ibatis.binding.MapperProxy;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.Reader;

public class MybatisHelloWorld2 {
  public static void main(String[] args) {
    try {
      Reader reader = Resources.getResourceAsReader("Configuration.xml");
      SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
      SqlSession session = sqlSessionFactory.openSession();
      try {
        UserMapper userMapper = session.getMapper(UserMapper.class);
        /**
         * {@link MapperProxy#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])}
         */
        User user = userMapper.getUserById(1);
        System.out.println(user.toString());
      } finally {
        session.close();
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
