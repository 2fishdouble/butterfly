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

package io.github.butterfly.sandbox.web;

import io.github.butterfly.core.R;
import io.github.butterfly.sandbox.permission.PermissionEnum;
import io.github.butterfly.sandbox.security.AsyncDemoService;
import io.github.butterfly.security.autoconfigure.authentication.AnonymousAccess;
import io.github.butterfly.security.autoconfigure.authentication.LoginUser;
import io.github.butterfly.security.autoconfigure.authentication.UserContext;
import io.github.butterfly.security.autoconfigure.authorization.RequirePermission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证/授权示例接口.
 * <ul>
 * <li>{@code GET /api/me}:登录即可访问,回显当前用户与它带的权限 id;</li>
 * <li>{@code GET /api/async/me}:在 {@code @Async} 线程里取当前用户,验证 ThreadLocal 跨线程传递;</li>
 * <li>{@code GET /api/products}:要求
 * {@link PermissionEnum#PRODUCT_PAGE_LIST}({@code @RequirePermission});</li>
 * <li>{@code POST /api/products}:要求
 * {@link PermissionEnum#PRODUCT_ADD}({@code @PreAuthorize})。</li>
 * </ul>
 * 这些接口都不需要额外配置:登录接口 {@code POST /login} 由框架提供,除登录与
 * {@code butterfly.security.authentication.permit-paths} 之外一律要求携带 JWT。
 */
@RestController
@RequestMapping("/api")
public class SecurityDemoController {

	private final AsyncDemoService asyncDemoService;

	/**
	 * 创建示例接口.
	 * @param asyncDemoService 异步示例服务
	 */
	public SecurityDemoController(AsyncDemoService asyncDemoService) {
		this.asyncDemoService = asyncDemoService;
	}

	/**
	 * 匿名可访问的接口:用 {@link AnonymousAccess} 标在方法上,不带 token 也能访问.
	 * <p>
	 * 与 {@code butterfly.security.authentication.permit-paths} 的区别:那个按路径放行,这个跟着方法走,
	 * 接口路径改了也不用同步改配置。
	 * @return 固定字符串
	 */
	@GetMapping("/public/hello")
	@AnonymousAccess
	public R<String> publicHello() {
		return R.success("anonymous ok");
	}

	/**
	 * 回显当前登录用户:直接从 {@link UserContext} 取,不需要在方法参数里注入 Authentication.
	 * @return 用户 id、登录名与权限 id
	 */
	@GetMapping("/me")
	public R<Map<String, Object>> me() {
		LoginUser current = UserContext.require();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("userId", current.userId());
		body.put("username", current.username());
		body.put("authorities", current.permissionIds());
		return R.success(body);
	}

	/**
	 * 异步线程里取当前用户:执行器挂了 {@code UserContextTaskDecorator},所以 {@code @Async} 方法里读得到.
	 * @return 异步方法返回的登录名
	 */
	@GetMapping("/async/me")
	public R<String> asyncMe() {
		return R.success(this.asyncDemoService.currentUsername().join());
	}

	/**
	 * 商品列表:需要商品列表权限.
	 * @return 模拟的商品列表
	 */
	@GetMapping("/products")
	@RequirePermission(1010100L) // PermissionEnum.PRODUCT_PAGE_LIST.getId()
	public R<List<String>> listProducts() {
		return R.success(List.of("阿莫西林胶囊", "布洛芬缓释胶囊"));
	}

	/**
	 * 新增商品:需要新增权限,用 Spring Security 原生表达式写法演示.
	 * @return 模拟的新增结果
	 */
	@PostMapping("/products")
	@PreAuthorize("hasAuthority('1010101')") // PermissionEnum.PRODUCT_ADD.getId()
	public R<String> createProduct() {
		return R.success("created by " + UserContext.username());
	}

}
