package io.github.butterfly.excel.autoconfigure;


import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import io.github.butterfly.core.BaseEnum;


public class BaseEnumConverter implements Converter<BaseEnum> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return BaseEnum.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public WriteCellData<?> convertToExcelData(BaseEnum value, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
        return new WriteCellData<>(value.getTitle());
    }

    @Override
    public BaseEnum convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
        String text = cellData.getStringValue();
        Class<?> clazz = contentProperty.getField().getType();
        if (clazz.isEnum() && BaseEnum.class.isAssignableFrom(clazz)) {
            for (Object e : clazz.getEnumConstants()) {
                BaseEnum titleEnum = (BaseEnum) e;
                if (titleEnum.getTitle().equals(text)) {
                    return titleEnum;
                }
            }
        }
        throw new IllegalArgumentException("No matching enum: " + text);
    }
}
