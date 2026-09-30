/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.butterfly.redis.autoconfigure;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Version;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用真实 Docker 上的 Redis 容器验证 Testcontainers 的可达性,并跑通最基础的 SET/GET.
 * <p>
 * 前置条件是本机 Docker Desktop 已启动;Docker 不可用时整个测试类会被跳过
 * ({@code disabledWithoutDocker = true}),而不是失败,这样没有 Docker 的机器仍能跑完其余测试。
 * <p>
 * 这里显式抑制 {@code resource} 检查:两个类型都实现了 {@code AutoCloseable},但生命周期都由 Testcontainers
 * 托管,手工关闭反而会破坏后续用例与资源回收。
 * <p>
 * 容器由 JUnit 扩展启停(Ryuk 兜底),{@code DockerClient} 则由 {@code DockerClientFactory} 缓存复用。
 */
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("resource")
class RedisTestcontainersTests {

	private static final Logger log = LoggerFactory.getLogger(RedisTestcontainersTests.class);

	private static final String REDIS_IMAGE = "redis:7.4-alpine";

	private static final int REDIS_PORT = 6379;

	private static final String VALUE_KEY = "butterfly:testcontainers:greeting";

	private static final String ENTITY_KEY = "butterfly:testcontainers:entity";

	// 生命周期由 Testcontainers 的 JUnit 扩展托管(启动 + 停止),不能放进 try-with-resources
	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
		.withExposedPorts(REDIS_PORT)
		.waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

	@Test
	void dockerEngineIsReachable() {
		DockerClientFactory dockerClientFactory = DockerClientFactory.instance();
		// 这个 client 由 DockerClientFactory 缓存并跨用例复用,关掉它会连带拖垮后续用例与 Ryuk
		DockerClient dockerClient = dockerClientFactory.client();

		Version version = dockerClient.versionCmd().exec();
		Info info = dockerClient.infoCmd().exec();

		assertThat(dockerClientFactory.isDockerAvailable()).as("Testcontainers 应能连上本机 Docker 引擎").isTrue();
		assertThat(version.getVersion()).as("Docker 引擎版本").isNotBlank();
		assertThat(version.getApiVersion()).as("Docker API 版本").isNotBlank();
		assertThat(info.getOsType()).as("redis 官方镜像只能跑在 Linux 容器模式下").isEqualTo("linux");

		assertThat(REDIS.isRunning()).as("redis 容器应已启动").isTrue();
		assertThat(REDIS.getContainerId()).isNotBlank();
		assertThat(REDIS.getDockerImageName()).contains("redis");
		assertThat(REDIS.getHost()).isNotBlank();
		assertThat(REDIS.getMappedPort(REDIS_PORT)).isPositive();

		log.info("Testcontainers 可达: engine={} api={} osType={},redis {} 映射到 {}:{}", version.getVersion(),
				version.getApiVersion(), info.getOsType(), REDIS.getDockerImageName(), REDIS.getHost(),
				REDIS.getMappedPort(REDIS_PORT));
	}

	@Test
	void setsAndGetsValueWithStringRedisTemplate() throws Exception {
		LettuceConnectionFactory connectionFactory = connectionFactory();
		try {
			StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
			template.afterPropertiesSet();

			// SET key value EX 300
			template.opsForValue().set(VALUE_KEY, "hello butterfly", Duration.ofMinutes(5));

			// GET key
			assertThat(template.opsForValue().get(VALUE_KEY)).isEqualTo("hello butterfly");
			assertThat(template.hasKey(VALUE_KEY)).isTrue();
			assertThat(template.getExpire(VALUE_KEY)).isPositive();

			// 再从容器内部用 redis-cli 读一次,证明值确实落在容器里的 Redis 上,而不只是本地缓存
			ExecResult result = REDIS.execInContainer("redis-cli", "GET", VALUE_KEY);
			assertThat(result.getExitCode()).isZero();
			assertThat(result.getStdout().trim()).isEqualTo("hello butterfly");

			// DEL key
			assertThat(template.delete(VALUE_KEY)).isTrue();
			assertThat(template.opsForValue().get(VALUE_KEY)).isNull();
		}
		finally {
			connectionFactory.destroy();
		}
	}

	/**
	 * 按 Bean 名取出注册器注册的模板只能拿到裸类型 {@code RedisTemplate},因此这里显式收敛泛型。
	 */
	@Test
	@SuppressWarnings("unchecked")
	void roundTripsEntityWithJsonRedisTemplate() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.registerBean(RedisConnectionFactory.class, RedisTestcontainersTests::connectionFactory);
			context.register(TemplateConfiguration.class);
			context.refresh();

			RedisTemplate<String, SampleEntity> template = context.getBean("sampleEntityRedisTemplate",
					RedisTemplate.class);
			SampleEntity entity = new SampleEntity("butterfly");

			template.opsForValue().set(ENTITY_KEY, entity);

			assertThat(template.opsForValue().get(ENTITY_KEY)).isEqualTo(entity);
		}
	}

	private static LettuceConnectionFactory connectionFactory() {
		LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(REDIS.getHost(),
				REDIS.getMappedPort(REDIS_PORT));
		connectionFactory.afterPropertiesSet();
		return connectionFactory;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisTemplates(SampleEntity.class)
	static class TemplateConfiguration {

	}

	record SampleEntity(String name) {
	}

}
