package com.lic.ibatis.dao;

import com.lic.ibatis.entity.User;

import java.sql.*;
import java.util.Date;

public class UserMapperImpl implements UserMapper {
  @Override
  public User getUserById(int id) {
    Connection connection=null;
    PreparedStatement preparedStatement=null;
    ResultSet resultSet=null;
    try {
      //注册驱动
      Class.forName("com.mysql.jdbc.Driver");
      //获取数据连接
      connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/mybatis" , "root" , "m123" ) ;
      //sql语句
      String sql= "select * from user where id = ?";
      //加载预执行对象
      preparedStatement = connection.prepareStatement(sql);
      //添加参数
      preparedStatement.setInt(1,id);
      //执行查询, 获取结果集
      resultSet = preparedStatement.executeQuery();
      User user=null;
      //解析结果集,封装结果
      while(resultSet.next()){
        int userId = resultSet.getInt("id");
        String username = resultSet.getString("username") ;
        Date birthday = resultSet.getDate("birthday");
        String sex = resultSet.getString("sex");
        String address = resultSet.getString("address");
        user = new User(String.valueOf(userId), username, String.valueOf(birthday), sex, address);
      }
      return user;
    } catch (Exception e) {
      e.printStackTrace();
    }finally {
      //关闭相关资源
      try {
        if (resultSet !=null){
            resultSet.close();
        }
        if (preparedStatement !=null){
          preparedStatement.close();
        }
        if (connection !=null){
          connection.close();
        }

      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return null;
  }
}
