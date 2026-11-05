# Butterfly

**English** | [简体中文](README.md)

A set of reusable building blocks for **Spring Boot 4 / Java 21**: a BOM for version alignment, a group of
auto-configuration modules, a starter, plus runnable sandbox apps.

Two design rules drive everything here:

1. **If it can be auto-configured, nobody should have to write it** — add the dependency and it works, every
   setting has a default;
2. **Business code must stay in control** — every auto-configuration backs off through
   `@ConditionalOnMissingBean` / `@ConditionalOnProperty`, so a custom bean or a property flag overrides it.

```xml
<dependency>
    <groupId>io.github.2fishdouble</groupId>
    <artifactId>butterfly-spring-boot-starter</artifactId>
    <version>0.1.7</version>
</dependency>
```

- Version: `0.1.7`, driven by the `revision` property in `butterfly-parent`
- Java 21, Spring Boot 4.1.2
- Package root: `io.github.butterfly.*`

---

## Modules

| Module | What it gives you |
| --- | --- |
| `butterfly-parent` | Build parent POM: version properties, plugins, Error Prone, format/style/license checks |
| `butterfly-bom` | Single source of truth for dependency versions (`spring-boot-dependencies` + middleware clients) |
| `butterfly-core` | Core types: `R` response envelope, `BaseEnum`, `BusinessException`, `PatternConstant` |
| `butterfly-spring-boot-autoconfigure` | Fallback exception handler, Jackson customization, trace id, SpEL helper |
| `butterfly-spring-boot-log-autoconfigure` | Logback configuration that applies on its own, overridable per service |
| `butterfly-spring-boot-redis-autoconfigure` | Per-entity `RedisTemplate` beans, distributed-lock idempotency via `@Idempotent` |
| `butterfly-spring-boot-mybatis-plus-autoconfigure` | Pagination + optimistic locking, audit-field filling for `BaseEntity` |
| `butterfly-spring-boot-excel-autoconfigure` | FastExcel helper `ExcelUtil` (read / write / download) |
| `butterfly-spring-boot-mail-autoconfigure` | `MailTemplate` mail-sending facade |
| `butterfly-spring-boot-security-autoconfigure` | Permission enum collection and storage (in-memory or database) |
| `butterfly-spring-boot-rabbitmq-autoconfigure` | Per-entity topology (dead letter / delay / retry) + listener container factories |
| `butterfly-spring-boot-kafka-autoconfigure` | Per-entity `KafkaTemplate`, topic creation, non-blocking retry and DLT |
| `butterfly-spring-boot-netty-autoconfigure` | Embedded Netty TCP server (NIO / Epoll / KQueue) |
| `butterfly-spring-boot-canal-autoconfigure` | Canal binlog consumption, row mapping, `@CanalListener` dispatch |
| `starter/butterfly-spring-boot-starter` | One-stop dependency = `autoconfigure` + `log-autoconfigure` |
| `sandbox/*` | Runnable demos (basic, Kafka, RabbitMQ, RocketMQ, Canal, Curator), not published |

All auto-configuration modules register a
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file, so adding the jar is
enough. Two exceptions: `excel` only ships static helpers and `log` only ships a Logback file at the classpath
root — neither needs an auto-configuration class. Every auto-configuration class is guarded with
`@ConditionalOnClass` / `@ConditionalOnMissingBean`, so it degrades gracefully when a dependency is absent and
backs off when you define your own bean.

---

## Getting started

### 1. Import the BOM (recommended)

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.2fishdouble</groupId>
            <artifactId>butterfly-bom</artifactId>
            <version>0.1.7</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Every module dependency below can then drop its `<version>`.

### 2. Add dependencies

```xml
<!-- core capabilities + logging -->
<dependency>
    <groupId>io.github.2fishdouble</groupId>
    <artifactId>butterfly-spring-boot-starter</artifactId>
</dependency>

<!-- add the middleware you actually use -->
<dependency>
    <groupId>io.github.2fishdouble</groupId>
    <artifactId>butterfly-spring-boot-redis-autoconfigure</artifactId>
</dependency>
```

### 3. Run a sandbox app

```bash
./mvnw -pl sandbox/butterfly-sandbox -am spring-boot:run
```

---

## Core: `butterfly-spring-boot-autoconfigure`

### Response envelope and exception fallback

`R<T>` from `butterfly-core` is the fixed response shape: success means `code=200`, `isSuccess=true` and the
payload in `data`; failure means `code=100000`, `isSuccess=false` and the reason in `msg`.

```java
return R.success(data);
throw new BusinessException("out of stock");
```

`HandleExceptionAdvice` is a catch-all `@ExceptionHandler(Throwable.class)` for servlet web apps: anything not
consumed by another handler is wrapped as `R.error(t.getMessage())` and logged at error level with the full
stack trace. Your own `@RestControllerAdvice` has higher precedence and keeps working.

### Jackson customization

Three modules are appended to Spring Boot's `JsonMapper`:

| Customization | Effect |
| --- | --- |
| `BaseEnum` | Enums implementing the interface are read/written by `code` instead of name/ordinal |
| `Long` / `long` | Serialized as JSON strings so JavaScript never loses precision (deserialization still accepts both) |
| `LocalDateTime` / `LocalDate` / `LocalTime` | Read and written with the formats in `PatternConstant` |

### Trace id and request logging

`TraceIdFilter` (highest precedence) creates or forwards a trace id per request: it writes it to the MDC key
`traceId`, echoes it in a response header, and logs request start/end with the elapsed time. Enabled by default.

```yaml
butterfly:
  web:
    trace-id:
      enabled: true          # set false to disable
      header: X-Trace-Id     # header used for propagation
```

To get the trace id into log lines, configure the correlation segment (the default logging setup does not
print it — see the logging section):

```yaml
logging:
  pattern:
    correlation: "[%X{traceId:-}] "
```

### Also in this module

`SpelSup` is a SpEL helper (it understands `@beanName` references) and is what backs `lockValue` in the Redis
idempotency annotation. It is only registered when hutool is on the classpath.

---

## Logging: `butterfly-spring-boot-log-autoconfigure`

### The problem

Every service keeps its own `logback.xml`, so formats, log paths, rolling policies and noise filters all drift
apart — one rule change means editing N repositories. The naive fix (drop one `logback.xml` into a shared jar)
breaks the opposite requirement: services can no longer customize anything.

So both goals must hold at once:

1. a service that writes nothing still gets a usable, consistent logging setup;
2. a service that wants to customize can replace everything **or** inherit only the shared part.

### How: one default entry point plus two includable fragments

This module is **resources only (no Java code)**; the jar contains three files:

| File (inside the jar) | Purpose | Who uses it |
| --- | --- | --- |
| `logback.xml` | Default entry point, body is a single `<include>` | Loaded by Spring Boot |
| `butterfly/logback/butterfly-logback-base.xml` | Shared appenders + default `<root>` | Services that want the whole default setup |
| `butterfly/logback/butterfly-logback-defaults.xml` | Format, appenders, noise levels, **no `<root>`** | Services that define their own root |

Why it applies with no configuration: during early startup Spring Boot scans the classpath for logging
configuration in a fixed order, and `classpath:logback.xml` is one of the candidates. **This happens before the
Spring context exists**, which is exactly why the module ships an XML file instead of an `@AutoConfiguration`
class — and why it has no `AutoConfiguration.imports` entry.

### Key design decision: `logback.xml`, not `logback-spring.xml`

Spring Boot resolves logging configuration in this order:

```
logging.config property → logback-spring.xml → logback.xml
```

`logback-spring.xml` wins over `logback.xml`. Had the shared module claimed that name, any existing
`logback.xml` in a service would have been shadowed and silently ignored — breaking goal 2. Naming it
`logback.xml` gives:

- no configuration in the service → the shared defaults apply;
- the service has its own `logback.xml` → `BOOT-INF/classes/` precedes dependency jars in an executable jar,
  so **the service file wins**;
- the service has its own `logback-spring.xml` or sets `logging.config` → higher precedence, again the service wins.

### Defaults

| Item | Default | How to change |
| --- | --- | --- |
| Console | Appender `STDOUT`, Spring Boot format with colors | `logging.pattern.console` |
| File | Async `DefaultAppender` wrapping rolling `DefaultAppender_inner` | Redeclare the appenders |
| Location | `logs/<spring.application.name>/<spring.application.name>.log` | `logging.file.name` / `logging.file.path` |
| Rolling | 50MB per file, 30 days, 512MB total, `.zip` archives | Redeclare `DefaultAppender_inner` |
| Root level | `INFO` | `logging.level.root` |
| Noise | `io.netty` at `WARN` | `logging.level.io.netty` |

The format reuses Spring Boot's own `defaults.xml`, so `logging.pattern.*`, `logging.charset.*` and
`logging.threshold.*` behave exactly as they do without this module. Note that the rolling policy is declared
here rather than in Boot's `file-appender.xml`, so **`logging.logback.rollingpolicy.*` does not affect the
defaults**.

The per-service directory keeps several services on one host from overwriting each other's logs. To get
`logs/<service>/app.log` instead, set `logging.file.name`.

### Customizing

**A. Replace everything** — add your own `logback-spring.xml`, or set
`logging.config=classpath:logback-custom.xml`.

**B. Inherit the defaults, then add your own loggers**

```xml
<configuration>
    <include resource="butterfly/logback/butterfly-logback-base.xml"/>
    <logger name="com.example.order" level="DEBUG"/>
</configuration>
```

**C. Use only the shared appenders and define your own root**

```xml
<configuration>
    <include resource="butterfly/logback/butterfly-logback-defaults.xml"/>
    <root level="WARN">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

Splitting `base` and `defaults` avoids the surprising case where an included file silently overrides your own
`<root>`: when a configuration contains several `<root>` elements it is not obvious which one wins, so
`defaults` simply does not declare one.

> Using Log4j2? This module ships Logback XML only; Log4j2 never reads it, and it is inert when logback is not
> on the classpath. Such services should exclude the module with `<exclusions>`. Details:
> [module README](auto-configurations/butterfly-spring-boot-log-autoconfigure/README.md) (Chinese).

---

## Redis: `butterfly-spring-boot-redis-autoconfigure`

### Type-safe `RedisTemplate` per entity

```java
@Configuration
@EnableRedisTemplates({ User.class, Order.class })
class RedisConfig {
}
```

Registers `userRedisTemplate` and `orderRedisTemplate` (`RedisTemplate<String, T>`): keys/hash keys use the
String serializer, values/hash values use a Jackson serializer targeting that entity. Existing beans with the
same name are skipped.

### Method-level idempotency

```java
@Idempotent(lockValue = "#orderNo")
public void createOrder(String orderNo) { ... }
```

The aspect builds a lock key from `fully.qualified.Class.method#<SpEL value>` and calls Redisson's
`tryLock(0, -1, SECONDS)` — **immediately**, no waiting. A failed acquisition throws
`BusinessException("please try again later")`; the lock is released by the owning thread on both normal return
and exception.

Requirements: `RedissonClient` on the classpath plus AspectJ and hutool (`SpelSup` needs hutool). If any is
missing the aspect is not registered and the method simply runs unprotected.

---

## MyBatis-Plus: `butterfly-spring-boot-mybatis-plus-autoconfigure`

Auto-configured when `MybatisPlusInterceptor` is on the classpath:

- `MybatisPlusInterceptor`: pagination + optimistic locking (inner-interceptor order is execution order);
- `MetaObjectHandler`: fills `create_time` (insert only) and `edit_time` (insert and update).

Extending `BaseEntity` gives you `create_time` / `creator_id` / `edit_time` / `editor_id` / `is_deleted`
(logical delete). `creator_id` and `editor_id` are **not** filled automatically — set them from a `ThreadLocal`
or supply your own `MetaObjectHandler`.

---

## Excel: `butterfly-spring-boot-excel-autoconfigure`

A utility module with no auto-configuration; call the static methods directly:

```java
// read: callback based, validate per row, receive the whole batch at the end
ExcelUtil.read(file, User.class,
        (row, context) -> { if (row.getName() == null) throw new BusinessException("name is required"); },
        (rows) -> { /* handle the batch */ });

// write: entity list -> byte array
byte[] xlsx = ExcelUtil.write(users, User.class);

// stream straight to the browser: field -> header mapping also defines column order
ExcelUtil.write(response, fieldsMap, "users", rows);
```

Reading from a byte array, writing through a template (`write(InputStream, ...)`), registering a
`WriteHandler`, and wrapping bytes into a download `ResponseEntity` with `getResponse` are all supported.
Reading and writing rely on FastExcel annotations; the `BaseEnumConverter` family maps enums by code and text.

---

## Mail: `butterfly-spring-boot-mail-autoconfigure`

When a `JavaMailSender` exists (that is, `spring.mail.*` or JNDI is configured), `MailTemplate` is registered:

```yaml
butterfly:
  mail:
    default-from: noreply@example.com
```

```java
mailTemplate.sendText("to@example.com", "subject", "body");

mailTemplate.sendAttachment(List.of("a@example.com"), "subject", "body",
        Map.of("report.xlsx", resource));
```

The sender address comes from `butterfly.mail.default-from`, falling back to the `JavaMailSender` username and
finally to the underlying SMTP/JNDI session. An empty recipient list throws `IllegalArgumentException`.

---

## Permissions: `butterfly-spring-boot-security-autoconfigure`

Define permission enums implementing `IPermission` (group enums implement `IPermissionGroup`); they are scanned
at startup and persisted.

```yaml
butterfly:
  security:
    permission:
      enabled: true
      db: false                  # true + a DataSource means database storage, otherwise in-memory
      scan-packages: [ com.example.permission ]   # empty means the main class package
```

With a `DataSource` and `db=true` you get `DbPermissionStorage` (auto-created table + upsert); otherwise
`InMemoryPermissionStorage`. Providing your own `PermissionStorage` bean takes over completely.

---

## RabbitMQ: `butterfly-spring-boot-rabbitmq-autoconfigure`

```java
@Configuration
@EnableRabbitMqTemplates({ Order.class, Stock.class })
class RabbitConfig {
}
```

For each entity class it declares the main exchange/queue/binding, a **dead-letter topology enabled by
default** (suffixes `.dlx` / `.dlq` / `.dead`), an optional delay topology (suffix `.delay`, off by default),
and a listener container factory with a retry advice chain. Once retries are exhausted the delivery is
rejected and the broker routes it to the dead-letter exchange via `x-dead-letter-*`.

Note: unlike Kafka and Redis, this module does **not** register one template per entity. Sending and receiving
use the single `RabbitTemplate` that Boot auto-configures (with a shared JSON message converter); pass the
exchange and routing key explicitly when sending.

Defaults (all overridable under `butterfly.rabbitmq.*`, or per entity as `entities.<name>.*`):

| Item | Default |
| --- | --- |
| Topology names | Uncapitalized simple class name, e.g. `Order` → `order` |
| Exchange type / durable / auto-delete | `TOPIC` / `true` / `false` |
| Retry | 3 total deliveries, 1s initial, multiplier 2.0, 30s cap |
| Delay queue | Off; 30s TTL when enabled |
| Listener container | 1 consumer, prefetch 1, `MANUAL` ack, no requeue on failure |

---

## Kafka: `butterfly-spring-boot-kafka-autoconfigure`

```java
@Configuration
@EnableKafkaTemplates({ Order.class })
class KafkaConfig {
}
```

Registers a type-safe `KafkaTemplate<String, T>` per entity (`orderKafkaTemplate`) and, alongside it:

- the main `NewTopic` (uncapitalized simple class name, 1 partition and 1 replica by default), created by
  `KafkaAdmin`;
- a `RetryTopicConfiguration` for that topic, so a `@KafkaListener` on it gets non-blocking retry + dead letter
  **without** annotating `@RetryableTopic`;
- the retry topics `<topic>-retry-<delay>` and the dead-letter topic `<topic>-dlt`.

Default retry: 3 attempts, 1s initial, multiplier 2.0, 30s cap. The DLT handler defaults to the framework's
logging handler; override it with `butterfly.kafka.topic.retry.dlt-handler=beanName#methodName`.

Brokers, security and acks remain the job of `spring.kafka.*`; the registrar reuses the existing
`ProducerFactory` and only swaps the key/value serializers. To keep the `NewTopic` definitions without creating
topics, set `spring.kafka.admin.auto-create=false`.

---

## Netty: `butterfly-spring-boot-netty-autoconfigure`

With Netty on the classpath a TCP server starts according to configuration and follows the container lifecycle:

```yaml
butterfly:
  netty:
    enabled: true
    host: 0.0.0.0
    port: 9000
    transport: nio        # nio / epoll / kqueue, falls back to nio when the native transport is unavailable
    max-frame-length: 8192
    reader-idle-seconds: 0
```

The default pipeline is a line-delimited text protocol (`TextLineChannelInitializer`). Provide a
`NettyChannelInitializer` bean to use your own protocol, or a `NettyServer` bean to take over the server
entirely.

---

## Canal: `butterfly-spring-boot-canal-autoconfigure`

Connects to a single canal server and turns binlog row changes into events.

```yaml
butterfly:
  canal:
    destination: example        # empty means spring.application.name
    host: 127.0.0.1
    port: 11111
    filter: ".*\\..*"           # subscription expression
    consumer-type: sync         # sync / async
    batch-size: 1000
```

Two ways to consume:

```java
// 1. annotation: register by table name + event type
@CanalListener(table = "computer", events = CanalEventType.INSERT)
public void onInsert(Computer computer) { ... }

// 2. interface: implement CanalRowHandler / CanalEventHandler, registered automatically
```

A handler method takes at most two parameters: the row object (or `CanalEvent`, or a `Map`), and optionally the
previous values for UPDATE events. A failure in synchronous mode aborts and rolls back the batch; in
asynchronous mode the whole batch is redelivered.

---

## Sandbox: `sandbox/*`

| Module | Contents |
| --- | --- |
| `butterfly-sandbox-model` | Shared sample entities |
| `butterfly-sandbox` | Basic web app (port 8080) |
| `butterfly-sandbox-kafka` / `-rabbitmq` / `-rocketmq` | Send/receive demos per messaging system |
| `butterfly-sandbox-canal` | Canal consumption demo |
| `butterfly-sandbox-curator` | ZooKeeper/Curator demo |

Sandbox apps are runnable applications that skip `deploy` and are never published; their
`src/main/resources/application.yml` files double as configuration samples.

---

## Build and quality gates

```bash
./mvnw clean verify            # compile + test + checks
./mvnw -pl <module> -am test   # a single module (-am pulls in its parent POMs)
```

Three checks run in the `validate` phase and fail the build:

| Gate | What it does |
| --- | --- |
| `spring-javaformat:validate` | Formatting; run `./mvnw spring-javaformat:apply` to fix |
| `checkstyle:check` | Spring rule set, exclusions in `src/checkstyle/checkstyle.xml` |
| `license:check` | Every `.java` file must carry the Apache 2.0 header from `src/license/HEADER` |

**Error Prone** runs during `compile` and can be tuned per build:

```bash
./mvnw package -Derror-prone.flags=-XepAllErrorsAsWarnings   # downgrade all to warnings (hotfix)
./mvnw compile -Derror-prone.flags=-Xep:ReferenceEquality:ERROR
./mvnw compile "-Derror-prone.flags=-Xep:UnusedVariable:OFF"
```

JaCoCo writes a coverage report to `target/site/jacoco` during the test phase.

## Versioning and release

- The version lives in `revision` in `butterfly-parent`; every module references `${revision}`, so a release
  touches one place.
- `-Prelease` enables Central publishing (`central-publishing-maven-plugin`) and GPG signing;
  `autoPublish=false` means the upload still needs manual confirmation in the Central portal.
- `sandbox/*` is never published.

## License

Apache License 2.0.
