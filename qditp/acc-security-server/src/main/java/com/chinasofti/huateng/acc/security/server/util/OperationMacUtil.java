package com.chinasofti.huateng.acc.security.server.util;

import com.chinasofti.huateng.acc.security.feign.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

public class OperationMacUtil {

    private static final Logger log = LoggerFactory.getLogger(OperationMacUtil.class);


    public static final <T> String getFieldOrderStr(T t) {
        StringBuffer result = new StringBuffer();

        Class<?> clazz = t.getClass();
        Field[] fields = clazz.getDeclaredFields();
        fields = sort(fields);

        String getMethodName = null;
        Method getMethod = null;
        Object fieldValue = null;

        if (fields != null && fields.length != 0) {
            for (Field field : fields) {
                getMethodName = "get" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
                try {
                    getMethod = clazz.getMethod(getMethodName, new Class[]{});
                    if (getMethod == null)
                        continue;
                    fieldValue = getMethod.invoke(t, new Object[]{});
                    if (fieldValue != null)
                        result.append(fieldValue);
                } catch (Exception e) {
                }
            }
        }
        return result.toString();
    }

    public static final Field[] sort(Field[] arr) {
        try {
            Arrays.sort(arr, new Comparator<Field>() {
                @Override
                public int compare(Field o1, Field o2) {
                    if (o1.getAnnotation(Order.class) != null && o2.getAnnotation(Order.class) != null) {
                        if (o1.getAnnotation(Order.class).value() > o2.getAnnotation(Order.class).value()) {
                            return 1;
                        } else if (o1.getAnnotation(Order.class).value() == o2.getAnnotation(Order.class).value()) {
                            return 0;
                        } else {
                            return -1;
                        }
                    } else {
                        return -1;
                    }
                }
            });
        } catch (Exception e) {
            return null;
        }
        return arr;
    }
}
