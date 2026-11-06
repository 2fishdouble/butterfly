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
import io.github.butterfly.security.autoconfigure.authorization.RequirePermission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
 * <li>{@code GET /api/products}:要求
 * {@link PermissionEnum#PRODUCT_PAGE_LIST}({@code @RequirePermission});</li>
 * <li>{@code POST /api/products}:要求
 * {@link PermissionEnum#PRODUCT_ADD}({@code @PreAuthorize})。</li>
 * </ul>
 * 三个接口都不需要额外配置:登录接口 {@code POST /login} 由框架提供,除登录与
 * {@code butterfly.security.authentication.permit-paths} 之外一律要求携带 JWT。
 */
@RestController
@RequestMapping("/api")
public class SecurityDemoController {

	/**
	 * 回显当前登录用户与其权限 id.
	 * @param authentication 当前认证信息,由 Spring Security 注入
	 * @return 用户名与权限 id 列表
	 */
	@GetMapping("/me")
	public R<Map<String, Object>> me(Authentication authentication) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("username", authentication.getName());
		body.put("authorities", authorityNames(authentication));
		return R.success(body);
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
		return R.success("created");
	}

	/**
	 * 把当前用户的权限转成字符串列表.
	 * <p>
	 * {@link GrantedAuthority#getAuthority()} 可以返回 {@code null},这里统一转成字符串,避免把可空元素 收进
	 * {@code List<String>}。
	 * @param authentication 当前认证信息
	 * @return 权限字符串列表
	 */
	private static List<String> authorityNames(Authentication authentication) {
		return authentication.getAuthorities()
			.stream()
			.map((authority) -> String.valueOf(authority.getAuthority()))
			.toList();
	}

}
