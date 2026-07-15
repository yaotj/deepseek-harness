package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PaySignLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaySignLogMapper {
    int upsert(PaySignLog record);
}
