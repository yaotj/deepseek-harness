package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayCallbackLogMapper {
    int insert(PayCallbackLog record);
}
