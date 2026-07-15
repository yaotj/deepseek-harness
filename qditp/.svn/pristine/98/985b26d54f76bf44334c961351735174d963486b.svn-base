package com.chinasofti.huateng.micro.web.validation;

import cn.hutool.core.collection.CollectionUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;

import java.util.Set;

public class Checker {


    public static void check(@Valid Object o) throws Exception {
        Set<ConstraintViolation<@Valid Object>> validateSet = Validation.buildDefaultValidatorFactory().
                getValidator().validate(o);
        if (!CollectionUtil.isEmpty(validateSet)) {
            String msg = validateSet.stream().map(ConstraintViolation::getMessage)
                    .reduce((m1, m2) -> m1 + " | " + m2)
                    .orElse("Parameter error");
            throw new Exception(msg);
        }
    }
}
