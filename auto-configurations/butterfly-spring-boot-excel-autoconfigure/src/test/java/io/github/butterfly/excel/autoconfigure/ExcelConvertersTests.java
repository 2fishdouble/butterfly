package io.github.butterfly.excel.autoconfigure;

import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import io.github.butterfly.core.BaseEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 覆盖三个 FastExcel 转换器的读写规则与异常分支.
 */
class ExcelConvertersTests {

	private final BaseEnumConverter baseEnumConverter = new BaseEnumConverter();

	private final CollectionBaseEnumConverter collectionConverter = new CollectionBaseEnumConverter();

	private final BooleanStringGenericConverter booleanConverter = new BooleanStringGenericConverter();

	private final GlobalConfiguration globalConfiguration = new GlobalConfiguration();

	@Test
	void baseEnumConverterDeclaresSupportedTypes() {
		assertThat(this.baseEnumConverter.supportJavaTypeKey()).isEqualTo(BaseEnum.class);
		assertThat(this.baseEnumConverter.supportExcelTypeKey()).isEqualTo(CellDataTypeEnum.STRING);
	}

	@Test
	void baseEnumConverterWritesTitleAsCellText() {
		WriteCellData<?> cellData = this.baseEnumConverter.convertToExcelData(SampleEnum.FIRST, contentProperty("type"),
				this.globalConfiguration);

		assertThat(cellData.getStringValue()).isEqualTo("第一");
	}

	@Test
	void baseEnumConverterWritesEmptyCellForNullValue() {
		WriteCellData<?> cellData = this.baseEnumConverter.convertToExcelData(null, contentProperty("type"),
				this.globalConfiguration);

		assertThat(cellData.getStringValue()).isNull();
	}

	@Test
	void baseEnumConverterReadsEnumByTitle() {
		BaseEnum value = this.baseEnumConverter.convertToJavaData(new ReadCellData<>("第二"), contentProperty("type"),
				this.globalConfiguration);

		assertThat(value).isEqualTo(SampleEnum.SECOND);
	}

	@Test
	void baseEnumConverterFailsWhenTitleDoesNotMatch() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.baseEnumConverter.convertToJavaData(new ReadCellData<>("不存在"),
					contentProperty("type"), this.globalConfiguration))
			.withMessageContaining("不存在");
	}

	@Test
	void baseEnumConverterFailsWhenFieldIsNotABaseEnum() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.baseEnumConverter.convertToJavaData(new ReadCellData<>("第一"),
					contentProperty("name"), this.globalConfiguration))
			.withMessageContaining("第一");
	}

	@Test
	void collectionConverterDeclaresSupportedTypes() {
		assertThat(this.collectionConverter.supportJavaTypeKey()).isEqualTo(Collection.class);
		assertThat(this.collectionConverter.supportExcelTypeKey()).isEqualTo(CellDataTypeEnum.STRING);
	}

	@Test
	void collectionConverterJoinsTitlesByComma() {
		WriteCellData<?> cellData = this.collectionConverter.convertToExcelData(
				List.of(SampleEnum.FIRST, SampleEnum.SECOND), contentProperty("types"), this.globalConfiguration);

		assertThat(cellData.getStringValue()).isEqualTo("第一,第二");
	}

	@Test
	void collectionConverterWritesEmptyTextForNullOrEmptyCollection() {
		assertThat(this.collectionConverter.convertToExcelData(null, contentProperty("types"), this.globalConfiguration)
			.getStringValue()).isEmpty();
		assertThat(this.collectionConverter
			.convertToExcelData(List.of(), contentProperty("types"), this.globalConfiguration)
			.getStringValue()).isEmpty();
	}

	@Test
	void collectionConverterReadsTrimmedTitles() {
		Collection<BaseEnum> result = new ArrayList<>(this.collectionConverter
			.convertToJavaData(new ReadCellData<>(" 第一 , 第二 "), contentProperty("types"), this.globalConfiguration));

		assertThat(result).containsExactly(SampleEnum.FIRST, SampleEnum.SECOND);
	}

	@Test
	void collectionConverterReadsBlankTextAsEmptyCollection() {
		assertThat(this.collectionConverter.convertToJavaData(new ReadCellData<>("  "), contentProperty("types"),
				this.globalConfiguration))
			.isEmpty();
	}

	@Test
	void collectionConverterFailsWhenTitleDoesNotMatch() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.collectionConverter.convertToJavaData(new ReadCellData<>("第一,不存在"),
					contentProperty("types"), this.globalConfiguration))
			.withMessageContaining("不存在");
	}

	@Test
	void collectionConverterFailsWhenElementTypeHasNoConstants() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.collectionConverter.convertToJavaData(new ReadCellData<>("x"),
					contentProperty("emptyTypes"), this.globalConfiguration))
			.withMessageContaining("Can not find enum constants");
	}

	@Test
	void booleanConverterDeclaresSupportedTypes() {
		assertThat(this.booleanConverter.supportJavaTypeKey()).isEqualTo(Boolean.class);
		assertThat(this.booleanConverter.supportExcelTypeKey()).isEqualTo(CellDataTypeEnum.STRING);
	}

	@Test
	void booleanConverterWritesMappedTextWhenAnnotated() {
		assertThat(this.booleanConverter
			.convertToExcelData(Boolean.TRUE, contentProperty("enabled"), this.globalConfiguration)
			.getStringValue()).isEqualTo("是");
		assertThat(this.booleanConverter
			.convertToExcelData(Boolean.FALSE, contentProperty("enabled"), this.globalConfiguration)
			.getStringValue()).isEqualTo("否");
	}

	@Test
	void booleanConverterWritesDefaultTextWhenNotAnnotated() {
		assertThat(this.booleanConverter
			.convertToExcelData(Boolean.TRUE, contentProperty("plain"), this.globalConfiguration)
			.getStringValue()).isEqualTo("true");
		assertThat(this.booleanConverter
			.convertToExcelData(Boolean.FALSE, contentProperty("plain"), this.globalConfiguration)
			.getStringValue()).isEqualTo("false");
	}

	@Test
	void booleanConverterReadsMappedTextWhenAnnotated() {
		assertThat(this.booleanConverter.convertToJavaData(new ReadCellData<>("是"), contentProperty("enabled"),
				this.globalConfiguration))
			.isTrue();
		assertThat(this.booleanConverter.convertToJavaData(new ReadCellData<>("否"), contentProperty("enabled"),
				this.globalConfiguration))
			.isFalse();
	}

	@Test
	void booleanConverterFallsBackToBooleanValueOfWhenMappingMisses() {
		assertThat(this.booleanConverter.convertToJavaData(new ReadCellData<>("TRUE"), contentProperty("enabled"),
				this.globalConfiguration))
			.isTrue();
		assertThat(this.booleanConverter.convertToJavaData(new ReadCellData<>("1"), contentProperty("enabled"),
				this.globalConfiguration))
			.isFalse();
	}

	@Test
	void booleanConverterReadsDefaultTextWhenNotAnnotated() {
		assertThat(this.booleanConverter.convertToJavaData(new ReadCellData<>("true"), contentProperty("plain"),
				this.globalConfiguration))
			.isTrue();
		assertThat(this.booleanConverter.convertToJavaData(new ReadCellData<>("other"), contentProperty("plain"),
				this.globalConfiguration))
			.isFalse();
	}

	@Test
	void booleanConverterIgnoresMappingWhenContentPropertyHasNoField() {
		assertThat(this.booleanConverter.convertToExcelData(Boolean.TRUE, new ExcelContentProperty(),
				this.globalConfiguration))
			.extracting(WriteCellData::getStringValue)
			.isEqualTo("true");
	}

	@Test
	void booleanMappingKeepsEmptyDefaultForNullValue() throws Exception {
		BooleanMapping mapping = SampleRow.class.getDeclaredField("enabled").getAnnotation(BooleanMapping.class);

		assertThat(mapping.nullValue()).isEmpty();
	}

	private static ExcelContentProperty contentProperty(String fieldName) {
		ExcelContentProperty property = new ExcelContentProperty();
		property.setField(field(fieldName));
		return property;
	}

	private static Field field(String fieldName) {
		try {
			return SampleRow.class.getDeclaredField(fieldName);
		}
		catch (NoSuchFieldException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
