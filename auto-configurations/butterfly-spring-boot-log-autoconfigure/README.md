# butterfly-spring-boot-log-autoconfigure

统一的 Logback 日志配置。服务依赖本模块后**不需要再写 `logback.xml`**；需要定制的服务写自己的日志配置，通过
`<include>` 继承公共部分。

## 默认行为

`butterfly-spring-boot-starter` 已经依赖本模块，所以引入 starter 的服务开箱即用：

| 项目 | 默认值 | 怎么改 |
| --- | --- | --- |
| 控制台 | appender `STDOUT`，带颜色与 Spring Boot 默认格式 | `logging.pattern.console` |
| 文件 | appender `DefaultAppender`（异步）→ `DefaultAppender_inner`（滚动） | 见下方说明 |
| 日志路径 | `logs/<spring.application.name>/<spring.application.name>.log` | `logging.file.name`（完整路径）或 `logging.file.path`（目录） |
| 滚动 | 单文件 50MB、保留 30 天、总量上限 512MB、归档为 `.zip` | 见下方说明 |
| root 级别 | `INFO` | `logging.level.root` |
| 第三方降噪 | `io.netty` 为 `WARN` | `logging.level.io.netty` |

日志格式、`%clr` 颜色、异常格式都直接复用 Spring Boot 自带的 `defaults.xml`，因此 `logging.pattern.*`、
`logging.charset.*`、`logging.threshold.*` 等官方配置项的行为与不引入本模块时完全一致。

日志按应用名分目录，是为了同机部署多个服务时互不覆盖：`logs/order-service/order-service.log`、
`logs/user-service/user-service.log`。想改成 `logs/<应用名>/app.log` 这种固定文件名，配一行
`logging.file.name=logs/你的应用名/app.log` 即可；`logging.file.path` 也能只改目录。

滚动参数（50MB / 30 天 / 512MB）写死在公共 XML 里，需要调整时继承
`butterfly-logback-defaults.xml` 并重新声明 `DefaultAppender_inner` 即可。

## 覆盖与继承

Spring Boot 查找日志配置的顺序是：`logging.config` → `logback-spring.xml` → `logback.xml`。本模块提供的默认配置
命名成 **`logback.xml`** 而不是 `logback-spring.xml`，就是为了不盖住服务自己的配置。

### 1. 什么都不做

自动使用默认配置。

### 2. 完全替换

在服务的 `src/main/resources` 下放自己的 `logback-spring.xml`，或配置 `logging.config=classpath:xxx.xml`。
优先级高于本模块，本模块的配置不会生效。

### 3. 继承默认配置，再加自己的 logger / appender

服务的 `src/main/resources/logback.xml`（或 `logback-spring.xml`）：

```xml
<configuration>
    <include resource="butterfly/logback/butterfly-logback-base.xml"/>

    <logger name="com.example.order" level="DEBUG"/>
</configuration>
```

`butterfly-logback-base.xml` = 公共 appender + `root(INFO, STDOUT + DefaultAppender)`，等价于默认配置。

### 4. 只继承公共 appender，自己定义 root

```xml
<configuration>
    <include resource="butterfly/logback/butterfly-logback-defaults.xml"/>

    <root level="WARN">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

`butterfly-logback-defaults.xml` 只包含日志格式、`STDOUT` / `DefaultAppender`（内含
`DefaultAppender_inner`）和降噪级别，**不含 `<root>`**，所以你自己的 root 不会被公共配置干扰。

## 常见问题

- **为什么不把默认配置命名为 `logback-spring.xml`？** 因为 Spring Boot 先找 `logback-spring.xml`、再找
  `logback.xml`。如果默认文件叫 `logback-spring.xml`，服务里已有的 `logback.xml` 会被它挡住而失效。
- **服务里已有 `logback.xml` 会不会冲突？** 不会。可执行 jar 中 `BOOT-INF/classes/` 排在依赖 jar 之前，
  服务自己的同名文件优先。
- **异步 appender 会丢日志吗？** 用的是 logback 默认队列（256）与默认丢弃阈值，队列满时 `WARN` 及以上不会丢。
  需要调参时在自己的配置里重新声明 `DefaultAppender`（`queueSize`、`discardingThreshold` 等）。
- **服务用 Log4j2 呢？** 本模块只提供 Logback 的 XML，不会被 Log4j2 读取；没有 logback 依赖时该文件完全无副作用。
  这类服务应排除本模块（`<exclusions>`）并使用自己的 Log4j2 配置。
- **想彻底关掉文件日志？** 继承 `butterfly-logback-defaults.xml` 并只挂 `STDOUT`。
- **本模块没有 Java 代码**：日志配置在 Spring 上下文创建之前就要生效，靠的是 classpath 根目录的 XML，
  而不是 `@AutoConfiguration` 类，所以这里不存在 `META-INF/spring/...AutoConfiguration.imports`。

## 文件说明

| 文件 | 作用 |
| --- | --- |
| `logback.xml` | 默认配置入口，仅 include `butterfly-logback-base.xml` |
| `butterfly/logback/butterfly-logback-base.xml` | 公共 appender + 默认 root，供服务整体继承 |
| `butterfly/logback/butterfly-logback-defaults.xml` | 公共格式、appender、降噪级别，不含 root |
