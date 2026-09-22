package io.github.butterfly.excel.autoconfigure;

import io.github.butterfly.core.BaseEnum;

/**
 * 测试用枚举,实现 {@link BaseEnum} 以便被 Excel 转换器识别.
 */
public enum SampleEnum implements BaseEnum {

	FIRST(1, "第一"),

	SECOND(2, "第二");

	private final int code;

	private final String title;

	SampleEnum(int code, String title) {
		this.code = code;
		this.title = title;
	}

	@Override
	public int getCode() {
		return this.code;
	}

	@Override
	public String getTitle() {
		return this.title;
	}

}
