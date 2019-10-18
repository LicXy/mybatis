package com.lic.ibatis.test;

import com.lic.ibatis.entity.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.Reader;

public class MybatisHelloWorld {
  public static void main(String[] args) {
    try {
      Reader reader = Resources.getResourceAsReader("Configuration.xml");
      SqlSessionFactory sqlMapper = new SqlSessionFactoryBuilder().build(reader);
      SqlSession session = sqlMapper.openSession();
      try {
        User user = (User) session.selectOne("com.lic.ibatis.dao.UserMapper.getUserById", 1);
        System.out.println(user.toString());
      } finally {
        session.close();
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
