package com.chinasofti.huateng.acc.es.server.enumns;

import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor
@Getter
public enum PersonTypeEnum {

    PASSENGER("0","普通乘客"),

    EMPLOYEE("1","地铁员工");


    private final String typeValue;

    private final String typeName;


    public static String getValue(String typeName) {
        for(PersonTypeEnum personType: PersonTypeEnum.values()) {
            if (typeName.equals(personType.typeName)) {
                return personType.getTypeValue();
            }
        }
        return null;
    }
}
