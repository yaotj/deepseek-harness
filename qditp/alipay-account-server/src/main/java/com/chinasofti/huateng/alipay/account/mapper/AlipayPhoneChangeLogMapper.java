package com.chinasofti.huateng.alipay.account.mapper;

import com.chinasofti.huateng.alipay.account.entity.AlipayPhoneChangeLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlipayPhoneChangeLogMapper {
    int insert(AlipayPhoneChangeLog record);
}
