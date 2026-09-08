package io.github.butterfly.excel.autoconfigure;

import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import io.github.butterfly.core.BaseEnum;
import jakarta.annotation.Nullable;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionBaseEnumConverter implements Converter<Collection<? extends BaseEnum>> {

    @Override
    @SuppressWarnings("unchecked")
    public Class<Collection<? extends BaseEnum>> supportJavaTypeKey() {
        return (Class) Collection.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public WriteCellData<?> convertToExcelData(@Nullable Collection<? extends BaseEnum> value, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
        if (value == null || value.isEmpty()) {
            return new WriteCellData<>("");
        }
        String combinedTitle = value.stream()
                .map(BaseEnum::getTitle)
                .collect(Collectors.joining(","));
        return new WriteCellData<>(combinedTitle);
    }


    @Override
    public Collection<? extends BaseEnum> convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
        String cellString = cellData.getStringValue();
        if (cellString == null || cellString.trim().isEmpty()) {
            return new ArrayList<>();
        }

        ParameterizedType parameterizedType = (ParameterizedType) contentProperty.getField().getGenericType();
        @SuppressWarnings("unchecked")
        Class<BaseEnum> enumClass = (Class<BaseEnum>) parameterizedType.getActualTypeArguments()[0];

        BaseEnum[] allEnums = enumClass.getEnumConstants();
        if (allEnums == null || allEnums.length == 0) {
            throw new IllegalArgumentException("Can not find enum constants for " + enumClass.getName() + ".");
        }

        return Stream.of(cellString.split(","))
                .map(String::trim)
                .map(desc -> findEnumByDesc(allEnums, desc))
                .toList();
    }

    private BaseEnum findEnumByDesc(BaseEnum[] enums, String desc) {
        for (BaseEnum e : enums) {
            if (e.getTitle().equals(desc)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Can not convert description '" + desc + "' to enum type.");
    }
}