/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    /**
     * new XPathParser(reader, true, props, new XMLMapperEntityResolver())的参数含义: Reader，是否进行DTD 校验，属性配置，XML实体节点解析器
     */
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    /**
     * mybatis配置文件解析的主流程:
     * 1. parser.evalNode("/configuration") --> 获取到根节点
     * 2. 根据根标签<configuration>开始解析
     */
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    //解析配置文件中根标签下的所有子标签
    try {
      //issue #117 read properties first
      /**
       * 1. 解析properties节点
       */
      propertiesElement(root.evalNode("properties"));
      /**
       * 2. 解析settings节点
       */
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      /**
       * 3. VFS主要用来加载容器内的各种资源，比如jar或者class文件
       */
      loadCustomVfs(settings);
      loadCustomLogImpl(settings);
      /**
       * 4. 解析类型别名typeAliasesElement
       */
      typeAliasesElement(root.evalNode("typeAliases"));
      /**
       * 5. 加载插件pluginElement
       *
       *  比如: 分页插件PageHelper，再比如druid连接池提供的各种监控、拦截、预发检查功能，
       *  在使用其它连接池比如dbcp的时候，在不修改连接池源码的情况下，就可以借助mybatis的插件体系实现
       */
      pluginElement(root.evalNode("plugins"));

      /**
       * 6. 加载对象工厂objectFactoryElement
       *
       * MyBatis 每次创建结果对象的新实例时，它都会使用一个对象工厂（ObjectFactory）实例来完成。
       * 默认的对象工厂DefaultObjectFactory做的仅仅是实例化目标类，要么通过默认构造方法，要么在参数映射存在的时候通过参数构造方法来实例化。
       */
      objectFactoryElement(root.evalNode("objectFactory"));
      /**
       * 7. 创建对象包装器工厂objectWrapperFactoryElement
       *
       * 对象包装器工厂主要用来包装返回result对象，比如说可以用来设置某些敏感字段脱敏或者加密等。
       * 默认对象包装器工厂是DefaultObjectWrapperFactory，也就是不使用包装器工厂。
       */
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      /**
       * 8. 加载反射工厂reflectorFactoryElement
       *
       * 因为加载配置文件中的各种插件类等等，为了提供更好的灵活性，
       * mybatis支持用户自定义反射工厂，不过总体来说，用的不多，
       * 要实现反射工厂，只要实现ReflectorFactory接口即可。默认的反射工厂是DefaultReflectorFactory。
       * 一般来说，使用默认的反射工厂就可以了。
       */
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      /**
       * 得到setting之后，调用settingsElement(Properties props)将各值赋值给configuration，
       * 同时在这里有重新设置了默认值，所有这一点很重要，configuration中的默认值不一定是真正的默认值。
       */
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      /**
       * 9. 加载环境配置environmentsElement
       *
       * 环境可以说是mybatis-config配置文件中最重要的部分，
       * 它类似于spring和maven里面的profile，允许给开发、
       * 生产环境同时配置不同的environment，根据不同的环境加载不同的配置，
       * 这也是常见的做法，如果在SqlSessionFactoryBuilder调用期间没有传递使用哪个环境的话，
       * 默认会使用一个名为default”的环境。找到对应的environment之后，就可以加载事务管理器和数据源了。
       * 事务管理器和数据源类型这里都用到了类型别名，JDBC/POOLED都是在mybatis内置提供的，
       * 在Configuration构造器执行期间注册到TypeAliasRegister。
       *
       * 　mybatis内置提供JDBC和MANAGED两种事务管理方式，前者主要用于简单JDBC模式，
       * 后者主要用于容器管理事务，一般使用JDBC事务管理方式。
       * mybatis内置提供JNDI、POOLED、UNPOOLED三种数据源工厂，一般情况下使用POOLED数据源。
       */
      environmentsElement(root.evalNode("environments"));
      /**
       * 10. 数据库厂商标识加载databaseIdProviderElement(了解即可)
       */
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      /**
       * 11. 加载类型处理器typeHandlerElement
       *
       * 无论是 MyBatis 在预处理语句（PreparedStatement）中设置一个参数时，
       * 还是从结果集中取出一个值时， 都会用类型处理器将获取的值以合适的方式转换成 Java 类型。
       * mybatis提供了两种方式注册类型处理器，package自动检索方式和显示定义方式。
       * 使用自动检索（autodiscovery）功能的时候，只能通过注解方式来指定 JDBC 的类型。
       */
      typeHandlerElement(root.evalNode("typeHandlers"));

      /**
       * 12. 加载mapper文件 或 mapperElement  --->  (重点)
       */
      mapperElement(root.evalNode("mappers"));

    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 检查所有从settings加载的设置,确保它们都在Configuration定义的范围内,而非未知的设置
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * <typeAliases>
   *   <typeAlias alias="Blog" type="domain.blog.Blog"/>
   *   < !-- 指定一个包名，MyBatis会在包名下面搜索需要的 Java Bean
   *   每一个在包 domain.blog 中的 Java Bean，在没有注解的情况下，
   *   会使用 Bean 的首字母小写的非限定类名来作为它的别名。 比如
   *   domain.blog.Author 的别名为 author；若有注解，则别名为其注解值 --!>
   *   <package name="domain.blog"/>
   * </typeAliases>
   * 注意:
   * 1.类型别名是为 Java 类型设置一个短的名字，存在的意义仅在于用来减少类完全限定名的冗余。
   * 2.在没有注解的情况下，会使用 Bean的首字母小写的非限定类名来作为它的别名。 比如 domain.blog.Author 的别名为author；
   *    若有注解，则别名为其注解值。所以所有的别名，无论是内置的还是自定义的，都一开始被保存在configuration.typeAliasRegistry中了，
   *    这样就可以确保任何时候使用别名和FQN的效果是一样的。
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        //包的别名设置
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          //类的别名设置
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              //如果没有自定义别名
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        //将interceptor指定的名称解析为Interceptor类型实例
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        interceptorInstance.setProperties(properties);
        //添加拦截器到configuration中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    /**
     * <properties resource="org/mybatis/example/config.properties">
     *   <property name="username" value="lic"/>
     *   <property name="password" value="m123"/>
     * </properties>
     */
    if (context != null) {
      //将<properties>节点的子节点转换为propertie对象返回
      Properties defaults = context.getChildrenAsProperties();
      //获取<properties>节点的resource属性值
      String resource = context.getStringAttribute("resource");
      //获取<properties>节点的url属性值
      String url = context.getStringAttribute("url");
      //如果resource和url属性值都不为空,mybatis无法确定使用哪一个配置文件, 则抛出异常
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        //如果resource属性不为空,而url属性为空, 则解析resource指定文件中的property属性值, 并添加到defaults集合中
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        //如果url属性不为空,而resource属性为空, 则解析url指定文件中的property属性值, 并添加到defaults集合中
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      //获取configuration中已添加的属性值, 添加到defaults集合中
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        //查找匹配的environment
        if (isSpecifiedEnvironment(id)) {
          // 事务配置并创建事务工厂
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          // 数据源配置加载并实例化数据源, 数据源是必备的
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          // 创建Environment.Builder
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * <mappers>
   *   <mapper resource="org/mybatis/builder/AuthorMapper.xml"/> -----> 引入类路径(编译后以classes为跟的路径)下的资源
   *   <mapper url="file:///var/mappers/BlogMapper.xml"/>        -----> 入网络或磁盘路径下的资源
   *   <mapper class="org.mybatis.builder.BlogMapper"/>          -----> 引用（注册）接口: 1.有sql映射文件，映射文件名必须和接口同名，并且放在与接口同一目录下；2.没有sql映射文件，所有的sql都是基于注解写在接口上。
   *   <package name="org.mybatis.builder"/>                     -----> 扫描包下所有的引用接口
   * </mappers>
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        /**
         *   如果要同时使用package自动扫描和通过mapper明确指定要加载的mapper，
         *   一定要确保package自动扫描的范围不包含明确指定的mapper，否则在通过
         *   package扫描的interface的时候，尝试加载对应xml文件的loadXmlResource()
         *   的逻辑中出现判重出错，报org.apache.ibatis.binding.BindingException异常，
         *   即使xml文件中包含的内容和mapper接口中包含的语句不重复也会出错，
         *   包括加载mapper接口时自动加载的xml mapper也一样会出错。
         */
        //如果配置包扫描
        if ("package".equals(child.getName())) {
          //获取需要扫描的包路径
          String mapperPackage = child.getStringAttribute("name");
          //解析包信息, 注册该包下的Mappers
          configuration.addMappers(mapperPackage);
        } else {
          //获取resource,url,mapperClass属性的值
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");

          //resource属性解析
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();

          } else if (resource == null && url != null && mapperClass == null) {
            //url属性解析
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();

          } else if (resource == null && url == null && mapperClass != null) {
            //mapperClass属性解析
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
