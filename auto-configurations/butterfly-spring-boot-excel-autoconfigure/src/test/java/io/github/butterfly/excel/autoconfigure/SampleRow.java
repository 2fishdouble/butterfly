package io.github.butterfly.excel.autoconfigure;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.butterfly.core.BaseEnum;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 测试用行实体:每个字段显式注册本模块的转换器,用于覆盖枚举、枚举集合与布尔文案三类转换场景.
 * <p>
 * FastExcel 会通过反射读写该类型,因此类与构造器必须为 {@code public}.
 */
@Setter
@Getter
public class SampleRow {

	@ExcelProperty("姓名")
	private String name;

	@ExcelProperty("年龄")
	private Integer age;

	@ExcelProperty(value = "类型", converter = BaseEnumConverter.class)
	private SampleEnum type;

	@ExcelProperty(value = "类型列表", converter = CollectionBaseEnumConverter.class)
	private List<SampleEnum> types;

	@ExcelProperty(value = "空类型列表", converter = CollectionBaseEnumConverter.class)
	private List<EmptyEnum> emptyTypes;

	@ExcelProperty(value = "是否启用", converter = BooleanStringGenericConverter.class)
	@BooleanMapping(trueValue = "是", falseValue = "否")
	private Boolean enabled;

	@ExcelProperty(value = "普通布尔", converter = BooleanStringGenericConverter.class)
	private Boolean plain;

	public SampleRow() {
	}

	static SampleRow of(String name, int age) {
		SampleRow row = new SampleRow();
		row.setName(name);
		row.setAge(age);
		row.setType(SampleEnum.FIRST);
		row.setTypes(List.of(SampleEnum.FIRST, SampleEnum.SECOND));
		row.setEmptyTypes(List.of());
		row.setEnabled(Boolean.TRUE);
		row.setPlain(Boolean.FALSE);
		return row;
	}

	/**
	 * 不含任何常量的枚举,用于覆盖集合转换器的异常分支.
	 */
	public enum EmptyEnum implements BaseEnum {

		;

		@Override
		public int getCode() {
			return 0;
		}

		@Override
		public String getTitle() {
			return "";
		}

	}

}
