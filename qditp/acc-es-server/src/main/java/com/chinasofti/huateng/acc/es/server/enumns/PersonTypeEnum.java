package com.chinasofti.huateng.acc.es.server.enumns;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author rxwnc
 */
@AllArgsConstructor
@Getter
public enum PersonTypeEnum {
    /**
     * 普通乘客
     */
    PASSENGER("0","普通乘客"),
    /**
     * 地铁员工
     */
    EMPLOYEE("1","地铁员工");

    /**
     * 类型值
     */
    private final String typeValue;
    /**
     * 类型名称
     */
    private final String typeName;

    /**
     * 根据名称获取typeValue的值
     * @return
     */
    public static String getValue(String typeName) {
        for(PersonTypeEnum personType: PersonTypeEnum.values()) {
            if (typeName.equals(personType.typeName)) {
                return personType.getTypeValue();
            }
        }
        return null;
    }
}
