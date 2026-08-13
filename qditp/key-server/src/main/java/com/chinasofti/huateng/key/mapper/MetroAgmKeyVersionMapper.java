package com.chinasofti.huateng.key.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

@Mapper
@Component
public interface MetroAgmKeyVersionMapper {
    Long selectMaxApprovedBatchNumber(String providerId);
}
