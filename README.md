# Butterfly

**简体中文** | [English](README.en.md)

面向 **Spring Boot 4 / Java 21** 的一套通用能力封装：BOM 统一版本 + 一组自动配置模块 + starter，另有可运行的沙箱示例。

设计原则只有两条：

1. **能自动配置的就别让业务写** —— 引依赖即生效，配置全部有默认值；
2. **业务要接管时必须好接管** —— 统一用 `@ConditionalOnMissingBean` / `@ConditionalOnProperty` 让位，
   自定义 Bean 或开关配置都能覆盖默认行为。

```xml
<dependency>
    <groupId>io.github.2fishdouble</groupId>
    <artifactId>butterfly-spring-boot-starter</artifactId>
    <version>0.1.8</version>
</dependency>
```

- 当前版本：`0.1.8`（`butterfly-parent` 的 `revision` 属性统一控制）
- Java 21；Spring Boot 4.1.2
- 包名：`io.github.butterfly.*`

---

## 模块一览

| 模块 | 一句话 |
| --- | --- |
| `butterfly-parent` | 构建父 POM：版本属性、插件、Error Prone、格式/风格/许可校验 |
| `butterfly-bom` | 所有依赖版本的唯一来源（`spring-boot-dependencies` + 各中间件客户端） |
| `butterfly-core` | 基础类型：`R` 统一响应、`BaseEnum`、`BusinessException`、`PatternConstant` |
| `butterfly-spring-boot-autoconfigure` | 基础自动配置：异常兜底、Jackson 定制、TraceId、SpEL 辅助 |
| `butterfly-spring-boot-log-autoconfigure` | 统一日志：Logback 配置，引用即生效，也可按服务覆盖/继承 |
| `butterfly-spring-boot-redis-autoconfigure` | 按实体注册 `RedisTemplate`、分布式锁幂等 `@Idempotent` |
| `butterfly-spring-boot-mybatis-plus-autoconfigure` | 分页 + 乐观锁插件、`BaseEntity` 审计字段自动填充 |
| `butterfly-spring-boot-excel-autoconfigure` | Fesod 静态工具 `ExcelUtil`（读/写/下载） |
| `butterfly-spring-boot-file-autoconfigure` | MySQL 单表文件存储、自动建表与上传/下载接口 |
| `butterfly-spring-boot-mail-autoconfigure` | 邮件发送门面 `MailTemplate` |
| `butterfly-spring-boot-security-autoconfigure` | 权限枚举收集、JWT 认证与 `@RequirePermission` 方法级授权 |
| `butterfly-spring-boot-rabbitmq-autoconfigure` | 按实体声明拓扑（死信/延时/重试）+ 监听容器工厂 |
| `butterfly-spring-boot-kafka-autoconfigure` | 按实体注册 `KafkaTemplate`、建主题、非阻塞重试与死信 |
| `butterfly-spring-boot-netty-autoconfigure` | 内嵌 Netty TCP 服务端（NIO/Epoll/KQueue） |
| `butterfly-spring-boot-canal-autoconfigure` | Canal binlog 事件消费、映射与 `@CanalListener` 分发 |
| `starter/butterfly-spring-boot-starter` | 一站式依赖 = `autoconfigure` + `log-autoconfigure` |
| `sandbox/*` | 可启动示例（基础、Kafka、RabbitMQ、RocketMQ、Canal、Curator），不发布 |

各自动配置模块都按 Spring Boot 标准注册
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，引 jar 即生效。
两个例外：`excel` 只提供静态工具类、`log` 只提供 classpath 根目录的 Logback 配置，两者都不需要自动配置类。
每个自动配置类都带 `@ConditionalOnClass` / `@ConditionalOnMissingBean`，缺依赖时优雅跳过、业务自定义 Bean 时让位。

---

## 快速开始

### 1. 引入 BOM（推荐）

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.2fishdouble</groupId>
            <artifactId>butterfly-bom</artifactId>
            <version>0.1.8</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

之后引各模块都不用写版本号。

### 2. 引依赖

```xml
<!-- 基础能力 + 统一日志 -->
<dependency>
    <groupId>io.github.2fishdouble</groupId>
    <artifactId>butterfly-spring-boot-starter</artifactId>
</dependency>

<!-- 需要哪个中间件再加哪个 -->
<dependency>
    <groupId>io.github.2fishdouble</groupId>
    <artifactId>butterfly-spring-boot-redis-autoconfigure</artifactId>
</dependency>
```

### 3. 跑一下沙箱

```bash
./mvnw -pl sandbox/butterfly-sandbox -am spring-boot:run
```

---

## 基础能力：`butterfly-spring-boot-autoconfigure`

### 统一响应与异常兜底

`butterfly-core` 的 `R<T>` 是固定响应结构：成功 `code=200`、`isSuccess=true`、数据在 `data`；
失败 `code=100000`、`isSuccess=false`、原因在 `msg`。

```java
return R.success(data);
throw new BusinessException("库存不足");
```

`HandleExceptionAdvice` 是 Servlet Web 应用里的兜底 `@ExceptionHandler(Throwable.class)`：
把没被其它处理器消费的异常包成 `R.error(t.getMessage())` 并以 error 级别打完整堆栈。业务自己的
`@RestControllerAdvice` 优先级更高，不会被它抢。

### Jackson 定制

自动给 Spring Boot 的 `JsonMapper` 追加三个模块：

| 定制 | 效果 |
| --- | --- |
| `BaseEnum` | 实现该接口的枚举按 `code` 收发，而不是 name/ordinal |
| `Long` / `long` | 序列化为 JSON 字符串，避免前端 JS 精度丢失（反序列化仍接受数字和字符串） |
| `LocalDateTime` / `LocalDate` / `LocalTime` | 按 `PatternConstant` 中的格式读写 |

### TraceId 与请求日志

`TraceIdFilter`（优先级最高）为每个请求生成或透传 traceId：写入 MDC 键 `traceId`，回写响应头，
并打印请求开始/结束与耗时。默认开启。

```yaml
butterfly:
  web:
    trace-id:
      enabled: true          # 置 false 可关闭
      header: X-Trace-Id     # 透传用的请求头名
```

想让 traceId 出现在日志里，配置 correlation 段即可（默认日志不打印，见「统一日志」一节）：

```yaml
logging:
  pattern:
    correlation: "[%X{traceId:-}] "
```

### 其它

`SpelSup` 是 SpEL 解析辅助（支持 `@beanName` 引用），Redis 幂等注解的 `lockValue` 就靠它；
它只在 classpath 上有 hutool 时注册。

---

## 统一日志：`butterfly-spring-boot-log-autoconfigure`

### 要解决的问题

多个服务各自维护 `logback.xml`，格式、路径、滚动策略、降噪规则全都不一致，改一条要改 N 个仓库；
反过来，如果直接把配置塞进公共 jar，业务又改不动。所以目标有两条且必须同时成立：

1. 服务什么都不写，也要有可用的统一日志；
2. 服务想定制时，能整体替换，也能只继承公共部分。

### 做法：一个默认入口 + 两个可 include 的片段

本模块是**纯资源模块（没有 Java 代码）**，jar 里三个文件：

| 文件（jar 内路径） | 作用 | 谁用 |
| --- | --- | --- |
| `logback.xml` | 默认入口，内容只有一行 `<include>` | Spring Boot 自动加载 |
| `butterfly/logback/butterfly-logback-base.xml` | 公共 appender + 默认 `<root>` | 想「整体继承默认配置」的服务 |
| `butterfly/logback/butterfly-logback-defaults.xml` | 格式、appender、降噪级别，**不含 `<root>`** | 想「自己定 root」的服务 |

能在「引用即生效」的原因是：Spring Boot 启动早期会按固定顺序在 classpath 上找日志配置，
`classpath:logback.xml` 就在其中，找到即加载。**这发生在 Spring 上下文创建之前**，
所以它必须是一份 XML，而不是 `@AutoConfiguration` 类——这也是本模块没有 `AutoConfiguration.imports` 的原因。

### 关键设计：为什么文件名是 `logback.xml` 而不是 `logback-spring.xml`

Spring Boot 的查找顺序是：

```
logging.config 属性 → logback-spring.xml → logback.xml
```

`logback-spring.xml` 优先级更高。如果公共模块占用这个名字，业务服务里已有的 `logback.xml`
会被它挡住、静默失效——正好破坏上面第 2 条目标。命名成 `logback.xml` 之后：

- 服务没有任何配置 → 用公共的；
- 服务有自己的 `logback.xml` → 可执行 jar 中 `BOOT-INF/classes/` 排在依赖 jar 之前，**服务的同名文件胜出**；
- 服务有自己的 `logback-spring.xml` 或配了 `logging.config` → 优先级更高，同样胜出。

### 默认日志

| 项目 | 默认值 | 覆盖方式 |
| --- | --- | --- |
| 控制台 | appender `STDOUT`，Spring Boot 默认格式 + 颜色 | `logging.pattern.console` |
| 文件 | 异步 `DefaultAppender` 包滚动 `DefaultAppender_inner` | 重新声明 appender |
| 落盘路径 | `logs/<spring.application.name>/<spring.application.name>.log` | `logging.file.name` / `logging.file.path` |
| 滚动 | 单文件 50MB、保留 30 天、总量上限 512MB、归档 `.zip` | 重新声明 `DefaultAppender_inner` |
| root 级别 | `INFO` | `logging.level.root` |
| 降噪 | `io.netty` 为 `WARN` | `logging.level.io.netty` |

日志格式复用 Spring Boot 自带的 `defaults.xml`，所以 `logging.pattern.*`、`logging.charset.*`、
`logging.threshold.*` 的行为和不引本模块时一致。**注意**滚动策略是本模块自己声明的，不是 Boot 的
`file-appender.xml`，因此 `logging.logback.rollingpolicy.*` 对默认配置不生效。

按服务名分目录是为了同机部署多个服务时互不覆盖；想改成 `logs/<服务名>/app.log`，配一行
`logging.file.name` 即可。

### 服务定制

**A. 完全替换**——自己放 `logback-spring.xml`，或 `logging.config=classpath:logback-custom.xml`。

**B. 继承默认，再加自己的 logger**

```xml
<configuration>
    <include resource="butterfly/logback/butterfly-logback-base.xml"/>
    <logger name="com.example.order" level="DEBUG"/>
</configuration>
```

**C. 只用公共 appender，自己定 root**

```xml
<configuration>
    <include resource="butterfly/logback/butterfly-logback-defaults.xml"/>
    <root level="WARN">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

拆成 `base` / `defaults` 两份，是为了避免「include 进来把服务自己的 `<root>` 顶掉」——一个配置里出现多个
`<root>` 时最终生效的是哪个并不直观，所以 `defaults` 干脆不定义 root。

> 换成 Log4j2 的服务：本模块只提供 Logback 的 XML，Log4j2 不会读它，classpath 上没有 logback 时完全无副作用。
> 这类服务用 `<exclusions>` 排除本模块即可。更细的说明见
> [模块 README](auto-configurations/butterfly-spring-boot-log-autoconfigure/README.md)。

---

## Redis：`butterfly-spring-boot-redis-autoconfigure`

### 按实体注册类型安全的 RedisTemplate

```java
@Configuration
@EnableRedisTemplates({ User.class, Order.class })
class RedisConfig {
}
```

会注册 `userRedisTemplate`、`orderRedisTemplate`（`RedisTemplate<String, T>`）：key/hashKey 用 String
序列化，value/hashValue 用以该实体为目标的 Jackson 序列化器。同名 Bean 已存在时跳过。

### 方法级幂等

```java
@Idempotent(lockValue = "#orderNo")
public void createOrder(String orderNo) { ... }
```

切面以「类全名.方法名#SpEL 值」为锁 key，用 Redisson `tryLock(0, -1, SECONDS)` **立即**抢锁：
抢不到直接抛 `BusinessException("请稍后再试")`，方法结束或异常时由持锁线程释放。

依赖条件：classpath 有 `RedissonClient`，同时存在 AspectJ 与 hutool（`SpelSup` 依赖 hutool）；
缺少任一依赖时切面不注册，方法照常执行。

---

## MyBatis-Plus：`butterfly-spring-boot-mybatis-plus-autoconfigure`

classpath 有 `MybatisPlusInterceptor` 时自动装配：

- `MybatisPlusInterceptor`：分页插件 + 乐观锁插件（顺序即执行顺序）；
- `MetaObjectHandler`：写入 `create_time`（仅插入）与 `edit_time`（插入 + 更新）。

实体继承 `BaseEntity` 即获得 `create_time` / `creator_id` / `edit_time` / `editor_id` / `is_deleted`（逻辑删除）。
`creator_id` / `editor_id` **不会**被自动填充，需要业务用 `ThreadLocal` 或自定义 `MetaObjectHandler` 赋值。

---

## Excel：`butterfly-spring-boot-excel-autoconfigure`

纯工具模块，没有自动配置类，直接用静态方法：

```java
// 读:回调式,边解析边校验,最后拿整批
ExcelUtil.read(file, User.class,
        (row, context) -> { if (row.getName() == null) throw new BusinessException("名称必填"); },
        (rows) -> { /* 整批处理 */ });

// 写:实体列表 -> 字节数组
byte[] xlsx = ExcelUtil.write(users, User.class);

// 直接下载:字段名 -> 表头名 的映射同时决定列顺序,行数据是 Map
ExcelUtil.write(response, fieldsMap, "用户列表", rows);
```

还支持从字节数组读、按模板写出（`write(InputStream, ...)`）、注册 `WriteHandler`，
以及用 `getResponse` 把字节数组包成带下载响应头的 `ResponseEntity`。读写都走 Fesod 注解，
`BaseEnumConverter` 系列让枚举列按 code 与文本互转。

模块内置三个可叠加的写处理器，直接作为 `write` 的可变参数传入：

```java
byte[] report = ExcelUtil.write(users, User.class,
        new ExcelStyleWriteHandler(),                                  // 表头深蓝底白字 + 细边框，数据行细边框
        new ExcelRowHeightWriteHandler(30f, 20f),                      // 表头 30 磅、数据行 20 磅
        new ExcelMergeWriteHandler(new ExcelMergeRange(1, 2, 0, 0)));  // 合并第 2-3 行第 1 列
```

`ExcelMergeRange` 的行列号从 0 开始、结束值包含在内；模块测试会把生成的示例工作簿写到项目根目录的
`test-files/`，可直接用 Excel 打开核对样式、行高与合并效果。

---

## 文件：`butterfly-spring-boot-file-autoconfigure`

文件内容与元信息都存在 **MySQL 单表**（默认表名 `file`），建表由模块自动完成，并直接提供上传/下载接口。
存储走纯 JDBC，不绑定 MyBatis / JPA。

### 依赖与最小配置

只要容器里有 `DataSource`（例如引 `spring-boot-starter-jdbc` 加 MySQL 驱动）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo
    username: root
    password: secret
```

第一次上传或下载时才执行 `CREATE TABLE IF NOT EXISTS`，所以应用启动不要求数据库已经就绪；
表由 DBA 预先建好、或当前账号没有 DDL 权限时，设 `butterfly.file.create-table=false`。

表结构（单表）：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `char(32)` | 主键，32 位十六进制 UUID |
| `original_name` | `varchar(255)` | 原始文件名（已去路径与控制字符） |
| `content_type` | `varchar(255)` | 上传方给的内容类型，可为空 |
| `file_size` | `bigint` | 字节数 |
| `sha256` | `char(64)` | 内容摘要，带索引 |
| `content` | `longblob` | 文件内容 |
| `create_time` | `datetime(3)` | 入库时间 |

### 接口

```bash
# 上传：multipart/form-data，表单字段名固定为 file
curl -F "file=@/tmp/report.pdf" http://localhost:8080/file/upload
# {"code":200,"isSuccess":true,"data":{"id":"0f3c...","originalName":"report.pdf","size":"12345",...}}

# 下载：直接返回二进制，带 Content-Disposition: attachment（中文名按 RFC 5987 编码）
curl -OJ http://localhost:8080/file/0f3c...9a
```

业务代码也可以直接用 `FileService`：`store(InputStream | byte[], name, contentType)`、`load(id)`、`delete(id)`。
校验只在 `FileService` 做一次，所以 HTTP 接口和业务调用行为一致：

- 文件名去掉路径和控制字符，长度上限 255；
- 大小先按声明的 `size` 判断，读完再按实际字节数复核，声明值不可信也不会写超限内容；
- `allowed-extensions` 非空时按扩展名过滤（大小写不敏感）。

### 配置

| 项 | 默认 | 说明 |
| --- | --- | --- |
| `butterfly.file.enabled` | `true` | 总开关，关掉后存储、服务与接口都不注册 |
| `table-name` | `file` | 只允许字母、数字与下划线 |
| `create-table` | `true` | 首次读写时自动建表 |
| `max-size` | `10MB` | 单文件上限；需不大于 `spring.servlet.multipart.max-file-size` 与 MySQL `max_allowed_packet` |
| `allowed-extensions` | 空 | 为空表示不限制 |
| `upload-path` | `/file/upload` | 上传路径 |
| `download-path` | `/file/{id}` | 下载路径，必须保留 `{id}` 占位符 |

### 替换与边界

- 自定义 `FileStorage` Bean：换存储实现（对象存储、本地磁盘等），`FileService` 的校验仍然生效；
- 自定义 `FileService` 或 `FileController` Bean：整体接管；
- 没有 `DataSource` → 存储与接口都不注册；没有 Web 依赖 → 只注册存储与服务，接口跳过。

`longblob` 适合中小文件（默认 10MB）。大文件或高并发下载建议实现 `FileStorage` 存到对象存储，
数据库只保留元信息。

---

## 邮件：`butterfly-spring-boot-mail-autoconfigure`

存在 `JavaMailSender`（即配了 `spring.mail.*` 或 JNDI）时注册 `MailTemplate`：

```yaml
butterfly:
  mail:
    default-from: noreply@example.com
```

`MailTemplate` 提供纯文本发送与带附件的发送：

```java
mailTemplate.sendText("to@example.com", "主题", "正文");

mailTemplate.sendAttachment(List.of("a@example.com"), "主题", "正文",
        Map.of("报表.xlsx", resource));
```

发件人取 `butterfly.mail.default-from`，没配则回退到 `JavaMailSender` 的 username，
两者都没有时交给底层 SMTP/JNDI 会话。收件人为空会抛 `IllegalArgumentException`。

---

## 安全：`butterfly-spring-boot-security-autoconfigure`

本模块做三件事，各自有独立开关，可以只取其中一件：

| 能力 | 开关前缀 | 说明 |
| --- | --- | --- |
| 权限字典收集 | `butterfly.security.permission` | 启动时扫描权限枚举，入库或入内存 |
| 认证 | `butterfly.security.authentication` | 登录接口、JWT 签发与校验、无状态过滤链 |
| 授权 | `butterfly.security.authorization` | 用户→权限 id 查询、`@PreAuthorize`、`@RequirePermission` |

**边界**：框架只管机制，不管业务数据。`user`、角色、用户-角色关系这些表不建、不写，
字段怎么设计、数据存在哪（自己的库、LDAP、外部单点登录）都交给业务，框架只要求两个接口。

### 依赖

Spring Security 相关依赖全部是 `optional`：只要权限收集就什么都不用加；要认证授权，自己引：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>
```

缺这些依赖时，认证/授权相关自动配置整体静默跳过，权限收集照常工作。

### 一、权限字典

业务定义权限枚举实现 `IPermission`（分组枚举实现 `IPermissionGroup`），启动时自动扫描并入库：

```yaml
butterfly:
  security:
    permission:
      enabled: true
      db: false                  # true 且有 DataSource 时建表入库，否则内存存储
      scan-packages: [ com.example.permission ]   # 为空则扫描主类所在包
```

有 `DataSource` 且 `db=true` → `DbPermissionStorage`（自动建 `authority` / `authority_group` 表 + Upsert）；
否则 `InMemoryPermissionStorage`。自定义 `PermissionStorage` Bean 可直接接管。

### 二、认证：账号从哪来

实现 `UserAccountRepository` 即可，框架只要求「按登录名查到一个能校验密码的账号」：

```java
@Component
public class MyUserAccountRepository implements UserAccountRepository {

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        // SELECT id, username, password, nickname, enabled FROM sys_user WHERE username = ?
        return Optional.ofNullable(...);
    }
}
```

`UserAccount.password()` 必须是 **BCrypt 散列**（框架注册的 `PasswordEncoder` 就是 BCrypt）。
没有提供实现时退回内存实现，并在启动时创建 `admin/admin`、打印告警——只为本地跑通，生产别用。

登录：

```bash
curl -X POST http://localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'

# {"code":200,"isSuccess":true,"data":{"token":"eyJ...","tokenType":"Bearer","expiresIn":7200,"username":"admin"}}

curl http://localhost:8080/api/profile -H 'Authorization: Bearer eyJ...'
```

除登录路径与 `permit-paths` 外，其余请求都要求合法 JWT；未登录返回 401、权限不足返回 403，
响应体仍是统一结构 `R`（`{"code":100000,"isSuccess":false,"msg":"未登录或登录已过期"}`）。

### 匿名访问：路径放行还是方法放行

| 方式 | 适用 |
| --- | --- |
| `butterfly.security.authentication.permit-paths` | 按**路径**放行，适合 `/error`、静态资源这类没有 Controller 方法的位置 |
| `@AnonymousAccess` | 按**方法**放行，标在 Controller 方法或类上；路径改了注解跟着方法走，不用同步改配置 |

```java
@RestController
@AnonymousAccess                       // 整个 Controller 允许匿名
public class HealthController { ... }

@GetMapping("/api/public/hello")
@AnonymousAccess                       // 只放行这一个方法
public R<String> hello() { return R.success("ok"); }
```

⚠️ `@AnonymousAccess` 只免除「必须登录」，**不会**绕过 `@PreAuthorize` / `@RequirePermission`;
需要匿名的方法不要再标权限注解。

> 实现方式：过滤链的 `anyRequest()` 换成了一个 `AuthorizationManager`——先看是否已认证（带 token 的请求
> 不碰 MVC），只有匿名请求才会让 `RequestMappingHandlerMapping` 解析出目标方法，再看方法/类上有没有
> `@AnonymousAccess`。所以它比 `permitAll()` 多一层 MVC 解析，但只发生在未登录请求上。

### 在任意位置取当前用户

登录成功后，框架把 JWT 里的用户信息解析成 `LoginUser` 放进 ThreadLocal，Service、拦截器里都能直接取，
不用一路传 `Authentication` 参数：

```java
LoginUser user = UserContext.require();        // 没有登录用户时抛 IllegalStateException
long userId = user.userId();
String username = user.username();
Set<Long> permissionIds = user.permissionIds();

// 便捷写法（内部就是 require()）
long id = UserContext.userId();
String name = UserContext.username();

// 不确定是否登录（比如匿名接口、定时任务）时先判断
if (UserContext.isPresent()) { ... }
LoginUser maybe = UserContext.get();           // 可能为 null
```

内容来自 token 声明：`uid` → `userId`、`sub` → `username`、`authorities` → `permissionIds`。

底层是 Alibaba 的 `TransmittableThreadLocal`（已进 BOM 与模块依赖）。同线程读写和 `ThreadLocal` 一样；
跨线程需要显式打开，三种方式任选：

| 方式 | 说明 |
| --- | --- |
| `UserContextTaskDecorator` | **推荐**。挂到 `ThreadPoolTaskExecutor` 上即可，内部用 TTL 的 `capture/replay/restore`，不需要给 JVM 加参数 |
| `TtlRunnable` / `TtlExecutors` | 提交任务时自己包一层 |
| `-javaagent:transmittable-thread-local.jar` | 全局透明传播，`ThreadPoolExecutor` 自动包装 |

```java
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean
    ThreadPoolTaskExecutor applicationTaskExecutor(UserContextTaskDecorator userContextTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(userContextTaskDecorator);
        executor.initialize();
        return executor;
    }
}
// 之后 @Async 方法里 UserContext.username() 拿到的就是「提交任务的那个用户」
```

两个注意点：
- `TransmittableThreadLocal` 继承自 `InheritableThreadLocal`，**请求期间新建**的线程会继承当前用户。
  所以线程池要随容器启动创建（那时没有请求上下文），别在处理请求里临时 `new Thread`；
- 请求结束（含抛异常）由过滤器 `finally` 清理，装饰器执行完会 `restore` 线程原状态，线程池复用不会串号。

### 三、授权：权限从哪来

实现 `PermissionRepository`，返回该用户拥有的权限 id：

```java
@Component
public class MyPermissionRepository implements PermissionRepository {

    @Override
    public List<Long> findPermissionIdsByUserId(long userId) {
        // SELECT ra.authority_id FROM sys_role_authority ra
        //   JOIN sys_user_role ur ON ur.role_id = ra.role_id WHERE ur.user_id = ?
        return ...;
    }
}
```

权限 id 会原样变成 `GrantedAuthority`，所以两种写法都可用：

```java
@RequirePermission(101L)                     // 需要 101，全部满足
@RequirePermission({ 101L, 102L })           // 两个都要有
@PreAuthorize("hasAuthority('101')")         // 直接写表达式
```

权限在**登录时**查出来写进 JWT 的 `authorities` 声明（默认 2 小时），所以给用户改权限后需要重新登录才生效。
没有提供实现时退回内存实现，只有默认管理员拥有全部已收集的权限。

### 在沙箱里跑一遍

沙箱已经接好了这套能力（`sandbox/butterfly-sandbox`），两个演示账号：`admin/admin` 拥有全部权限，
`guest/guest` 只有商品列表权限。

```bash
./mvnw -pl sandbox/butterfly-sandbox -am spring-boot:run
# 或者：java -jar sandbox/butterfly-sandbox/target/butterfly-sandbox-0.1.7.jar
```

```bash
# 1) 未登录访问受保护接口：401 + 统一响应
curl -i http://localhost:8080/api/products

# 2) 登录拿 token
curl -s -X POST http://localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"username":"guest","password":"guest"}'

# 3) 有权限的接口：200（guest 有商品列表权限）
curl -i http://localhost:8080/api/products -H "Authorization: Bearer $TOKEN"

# 4) 权限不足的接口：403（guest 没有新增权限）
curl -i -X POST http://localhost:8080/api/products -H "Authorization: Bearer $TOKEN"

# 5) 免登录接口照旧可用（配置在 permit-paths 里）
curl http://localhost:8080/time
```

对应的实现只有三个类：`DemoUserAccountRepository`（账号）、`DemoPermissionRepository`（用户→权限 id）、
`SecurityDemoController`（`@RequirePermission` 与 `@PreAuthorize` 两种写法），都在
`sandbox/butterfly-sandbox` 里，可以直接照抄到业务工程。

### 配置

```yaml
butterfly:
  security:
    authentication:
      enabled: true
      login-path: /login          # 自动放行，只接受 POST + JSON
      permit-paths: [ /error ]    # 其它免认证路径
      init-default-admin: false   # 账号都由业务提供时关掉
      default-admin-username: admin
      default-admin-password: admin
      jwt:
        secret: "至少 32 字节的随机密钥"   # 不配则随机生成：重启失效、多实例互不认可
        issuer: butterfly
        expiration: 2h
    authorization:
      enabled: true
      method-security-enabled: true   # 已自己标 @EnableMethodSecurity 时可设为 false
```

注意：自己声明 `SecurityFilterChain` 时，框架那条不生效（`@ConditionalOnMissingBean`），
登录接口、JWT 组件与授权组件仍然可用。

---

## RabbitMQ：`butterfly-spring-boot-rabbitmq-autoconfigure`

```java
@Configuration
@EnableRabbitMqTemplates({ Order.class, Stock.class })
class RabbitConfig {
}
```

为每个实体类声明：主交换机/队列/绑定、**默认开启**的死信交换机/队列/绑定（后缀 `.dlx` / `.dlq` / `.dead`）、
可选的延时拓扑（后缀 `.delay`，默认关闭）、以及带重试通知链的监听容器工厂。重试耗尽后拒绝投递，
由 Broker 按 `x-dead-letter-*` 转入死信队列。

注意：与 Kafka/Redis 不同，本模块**不**按实体注册多个模板，收发统一用 Boot 自动配置的那一个 `RabbitTemplate`
（共享一个 JSON 消息转换器），发送时显式传交换机与路由键。

默认值（都可在 `butterfly.rabbitmq.*` 覆盖，也可按实体名 `entities.<name>.*` 覆盖）：

| 项 | 默认 |
| --- | --- |
| 拓扑名 | 实体类简单名首字母小写，如 `Order` → `order` |
| 交换机类型 / 持久化 / 自动删除 | `TOPIC` / `true` / `false` |
| 重试 | 共 3 次投递、起始 1s、倍率 2.0、上限 30s |
| 延时队列 | 关闭；开启后 TTL 30s |
| 监听容器 | 单并发、预取 1、`MANUAL` 确认、失败不重回队列 |

---

## Kafka：`butterfly-spring-boot-kafka-autoconfigure`

```java
@Configuration
@EnableKafkaTemplates({ Order.class })
class KafkaConfig {
}
```

为每个实体注册类型安全的 `KafkaTemplate<String, T>`（`orderKafkaTemplate`），并一并注册：

- 主主题 `NewTopic`（名称为实体简单名小写，默认 1 分区 1 副本），由 `KafkaAdmin` 创建；
- 该主题专属 `RetryTopicConfiguration`：落在该主题上的 `@KafkaListener` **无需**再标 `@RetryableTopic`
  即获得非阻塞重试 + 死信；
- 重试主题 `<主题>-retry-<延迟>` 与死信主题 `<主题>-dlt`。

默认重试 3 次、起始 1s、倍率 2.0、上限 30s；死信处理器默认用框架自带的日志处理器，
可用 `butterfly.kafka.topic.retry.dlt-handler=beanName#methodName` 换成自己的。

Broker 地址、安全认证、acks 等仍由 `spring.kafka.*` 负责，注册器复用容器里已有的 `ProducerFactory`，
只替换 key/value 序列化器。想让 `KafkaAdmin` 保留定义但不建主题，设 `spring.kafka.admin.auto-create=false`。

---

## Netty：`butterfly-spring-boot-netty-autoconfigure`

classpath 有 Netty 就按配置启动一个 TCP 服务端，随容器生命周期启停：

```yaml
butterfly:
  netty:
    enabled: true
    host: 0.0.0.0
    port: 9000
    transport: nio        # nio / epoll / kqueue,原生实现不可用时自动回退 nio
    max-frame-length: 8192
    reader-idle-seconds: 0
```

默认 pipeline 是「按换行符分帧的文本协议」（`TextLineChannelInitializer`）。要换成自定义协议，
提供一个 `NettyChannelInitializer` Bean 即可覆盖；整个服务端也可以用自定义 `NettyServer` Bean 接管。

---

## Canal：`butterfly-spring-boot-canal-autoconfigure`

直连单个 canal server，把 binlog 行变更转成事件并分发。

```yaml
butterfly:
  canal:
    destination: example        # 为空取 spring.application.name
    host: 127.0.0.1
    port: 11111
    filter: ".*\\..*"           # 订阅表达式
    consumer-type: sync         # sync / async
    batch-size: 1000
```

两种写法：

```java
// 1. 注解:按表名 + 事件类型注册
@CanalListener(table = "computer", events = CanalEventType.INSERT)
public void onInsert(Computer computer) { ... }

// 2. 接口:实现 CanalRowHandler / CanalEventHandler,由框架自动注册
```

方法参数最多两个：第一个是行对象（或 `CanalEvent`、`Map`），第二个接收 UPDATE 的旧值。
同步消费失败会中断本批并回滚，异步消费会整批重投。

---

## 沙箱：`sandbox/*`

| 模块 | 内容 |
| --- | --- |
| `butterfly-sandbox-model` | 示例实体，供各沙箱共用 |
| `butterfly-sandbox` | 基础 Web 示例（8080），含 JWT 认证与 `@RequirePermission` 授权示例 |
| `butterfly-sandbox-kafka` / `-rabbitmq` / `-rocketmq` | 各 MQ 的收发示例 |
| `butterfly-sandbox-canal` | Canal 消费示例 |
| `butterfly-sandbox-curator` | ZooKeeper/Curator 示例 |

沙箱是可直接启动的应用，跳过 `deploy`，不发布到仓库；对应的
`src/main/resources/application.yml` 也是现成的配置样例。

---

## 构建与质量门禁

```bash
./mvnw clean verify            # 编译 + 测试 + 校验
./mvnw -pl <module> -am test   # 只跑某个模块(-am 会带上父 POM)
```

`validate` 阶段三道门禁，任一不过即失败：

| 门禁 | 说明 |
| --- | --- |
| `spring-javaformat:validate` | 格式校验；写完执行 `./mvnw spring-javaformat:apply` 自动修复 |
| `checkstyle:check` | Spring 规则集，排除项见 `src/checkstyle/checkstyle.xml` |
| `license:check` | 每个 `.java` 必须带 `src/license/HEADER` 的 Apache 2.0 头 |

`compile` 阶段接入 **Error Prone**，可按需调整：

```bash
./mvnw package -Derror-prone.flags=-XepAllErrorsAsWarnings   # 全部降级为警告(救急发版)
./mvnw compile -Derror-prone.flags=-Xep:ReferenceEquality:ERROR
./mvnw compile "-Derror-prone.flags=-Xep:UnusedVariable:OFF"
```

测试阶段由 JaCoCo 生成覆盖率报告（`target/site/jacoco`）。

## 版本与发布

- 版本号集中在 `butterfly-parent` 的 `revision`，子模块统一引用 `${revision}`，发版只改一处。
- `-Prelease` 激活 Central 发布（`central-publishing-maven-plugin`）+ GPG 签名，
  `autoPublish=false` 意味着上传后仍需在中央仓库后台手动确认。
- `sandbox/*` 不发布。

## 许可

Apache License 2.0。
