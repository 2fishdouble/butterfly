package io.github.butterfly.excel.autoconfigure;

import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import org.jspecify.annotations.Nullable;

public class BooleanStringGenericConverter implements Converter<Boolean> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return Boolean.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public Boolean convertToJavaData(ReadCellData<?> cellData,
                                     ExcelContentProperty contentProperty,
                                     GlobalConfiguration globalConfiguration) {
        String str = cellData.getStringValue();
        BooleanMapping mapping = getMapping(contentProperty);
        if (mapping != null) {
            if (mapping.trueValue().equals(str)) {
                return true;
            }
            if (mapping.falseValue().equals(str)) {
                return false;
            }
        }
        return Boolean.valueOf(str);
    }

    @Override
    public WriteCellData<?> convertToExcelData(Boolean value,
                                               ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        BooleanMapping mapping = getMapping(contentProperty);
        String stringValue;
        if (mapping != null) {
            stringValue = value ? mapping.trueValue() : mapping.falseValue();
        } else {
            stringValue = value.toString();
        }
        return new WriteCellData<>(stringValue);
    }

    private @Nullable BooleanMapping getMapping(ExcelContentProperty contentProperty) {
        if (contentProperty.getField() == null) {
            return null;
        }
        return contentProperty.getField().getAnnotation(BooleanMapping.class);
    }
}
