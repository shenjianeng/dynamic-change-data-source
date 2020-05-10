## 抛出疑问 ❓

通过配置中心，应用可以实时的接收到配置的变更，但是，**应用中一些 Bean 是通过 Spring 容器来管理的，配置变更之后，怎么来修改 Spring 容器中对应 Bean 的状态呢？**

举个例子：如果在运行时修改了 JDBC 的参数配置，此时是重启应用呢？还是修改对应 DataSource Bean 的属性呢？如果是修改 Bean 的属性，直接修改有用吗？要怎么做呢？

本文将以运行时修改 JDBC 参数为例，来讨论尝试运行时修改配置，最后提出普遍的解决方案。

_画外音：思路比结果更重要。_

## 难点分析 👨🏻‍💻

**难点一：动态修改 JDBC 参数，假设修改的是 URL 和 password，那旧连接使用的还是旧的配置，这个时候怎么办呢？旧连接立刻失效还是一段时间后在失效？在使用旧连接的线程要怎么处理呢？**

在动态切换的过程中，必然会有一个过渡过程，从旧连接过渡到新连接，这个过渡的过程应该是尽可能的平滑。比如可以通过运维层面来做到：在就修改完 URL 和 password 之后，还是会有一段时间可以支持旧的连接的正常访问，以保证程序的平稳过渡。

**难点二：DataSource Bean 到底在哪里被引用了？能不能替换干净？旧连接如何放弃使用，并关闭？**

在修改完 JDBC 参数之后，下一步要做的就是查找 DataSource Bean 的使用方，将使用方使用的 DataSource Bean 换成新的配置。然后，将旧的连接关闭，让使用方使用使用新的连接。

## 尝试解决 🤔

文本将以 HikariCP 连接池为例来尝试解决这个问题。HikariCP 是 SpringBoot2.0 之后的默认数据库连接池，号称是当前 Java 领域最快的数据库连接池。

### 方案一：HikariCP 自带动态修改配置 API

HikariCP 自带了一些 API 来支持动态的修改数据库的相关配置。

> 引用 1：https://github.com/brettwooldridge/HikariCP/wiki/FAQ#q9

![FQA](https://upload-images.jianshu.io/upload_images/14270210-52a1c19aac5fd1ea.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

> 引用 2：https://github.com/brettwooldridge/HikariCP/wiki/MBean-(JMX)-Monitoring-and-Management

![使用jxm](https://upload-images.jianshu.io/upload_images/14270210-b3eb38066894a518.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

```java
...
HikariDataSource dataSource = ....
HikariConfigMXBean hikariConfigMXBean = dataSource.getHikariConfigMXBean();
hikariConfigMXBean.setPassword("...");
...
```

笔者没有仔细去尝试这个方案（为什么呢？当然是下面有更好的方案啦~），不过其 Github 的文档是这样描述的，官方第一手资料，出错的可能性比较小，如果有问题也可以去 Github 提相关的 issue。

**该方案的优点：使用原生 API 来动态修改配置，简单、可靠。缺点：能修改的参数有限，同时强绑定了 DataSource 的实现，假设以后改用别的数据库连接池，不一定有提供这些原生 API 来修改参数。**

### 方案二：动态修改 DataSource

废话不多说，直接上代码：

![DynamicDataSource](https://upload-images.jianshu.io/upload_images/14270210-67eae02b46b12333.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

方案二的思路来自于`org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource`。这个类是用来解决多数据源问题的，可以根据不同的 key 找到不同的 DataSource，然后再获取相应的 Connection。

同理，`DynamicDataSource`实现了`DataSource`接口，同时在其内部有一个成员变量`AtomicReference<DataSource> dataSourceReference`，由`dataSourceReference`来提供`DataSource`。当程序在运行时修改了 JDBC 参数时，可以通过创建一个新的`DataSource`对象来替换`dataSourceReference`的值，而对外暴露的是`DynamicDataSource`对象，这对使用方来说是无感知的。

那么是不是这样做就够了呢？思考一分钟。

还记得之前提的难点吗？**使用这种方法，底层可以悄悄的把 DataSource 的实例对象替换掉，那被替换下来的旧 DataSource 的连接怎么关闭呢？**

HikariCP 提供了相应的方法来关闭连接。如果使用别的数据库连接池也应该可以找到类似的方法。

![ShutdownDataSource](https://upload-images.jianshu.io/upload_images/14270210-8a055b0582e02092.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

**小结：该方法通过`DynamicDataSource`来包装真实的`DataSource`提供者，允许在运行时动态的替换底层的 DataSource 实例对象。同时，替换之后，要记得将旧的 DataSource 关闭。相比于方案一，该方案可以支持修改任意的 JDBC 属性，同时也没有强依赖`DataSource`实现者的 API，更加通用、灵活。**

相关代码地址：https://github.com/shenjianeng/dynamic-change-data-source

## 任意 Bean 属性的动态修改 🚀

### 完善方案二

解决完 DataSource 个例之后，能不能对上述方案进一步抽象，以支持动态修改任意的 Bean 的属性呢？

![DynamicRefreshProxy](https://upload-images.jianshu.io/upload_images/14270210-c0be3ebee6f1564d.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

其实事情远没有想着中的那么简单，即使抽象出了`DynamicRefreshProxy`，还是会有以下几个难点：

1. 需要动态修改配置的 Bean 要通过`DynamicRefreshProxy`来创建代理对象
2. 动态修改配置之后，需要修改`AtomicReference<Object> atomicReference`的引用值
3. 需要提供关闭旧对象相关资源的方法，在替换完旧对象之后，调用该方法

这里笔者可以提供一个思路来解决这些问题：

**通过自定义注解，例如`@DynamicRefreshable`，然后提供一个 BeanPostProcessor 来创建代理对象替换原对象，同时保存对应的`DynamicRefreshProxy`对象，监听到对应属性发生变化之后，替换`DynamicRefreshProxy`对象中的`atomicReference`，然后调用原始对象的相关方法来关闭资源。**

### Spring Cloud Refresh Scope

Spring Cloud 中提供了一种新的 Scope：Refresh Scope

> Refresh Scope 相关文档：
>
> https://cloud.spring.io/spring-cloud-static/spring-cloud.html#_refresh_scope

![Refresh Scope](https://upload-images.jianshu.io/upload_images/14270210-1ec214cae466713b.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

标记为@RefreshScope 的 Bean 在配置更改时，重新初始化，不过这需要调用`ContextRefresher#refresh`或者`RefreshScope#refreshAll`。不同的是，`ContextRefresher#refresh`方法内部不仅调用了`RefreshScope#refreshAll`，还调用了`ContextRefresher#refreshEnvironment`。

一个简单的 DEMO 程序如下：

![demo](https://upload-images.jianshu.io/upload_images/14270210-239a5569a07cefaf.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

## 重启大法好？✌️

还记得开篇提出的问题和难点吗？

> 难点二：DataSource Bean 到底在哪里被引用了？能不能替换干净？旧连接如何放弃使用，并关闭？

**是否已经正常的关闭资源是一个很难验证的事情，它会和当前项目的具体运行状态相关联。**在上文中，虽然在替换 DataSource 实例之后，调用了相关的 API 来关闭连接，但是，**`doShutdownDataSource`方法只会尝试几次，超过一定次数之后，就会直接调用 close 方法来关闭数据库。如果在尝试`MAX_RETRY_TIMES`之后，连接还是没有关闭呢？close 方法能保证关闭所有相关资源吗？还是重启大法好？！**

小调查：你们的做法是热更新 Bean 呢？还是选择重启呢？

---

欢迎关注个人公众号：

![Coder小黑](https://upload-images.jianshu.io/upload_images/14270210-81b49194fd825e8c.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
