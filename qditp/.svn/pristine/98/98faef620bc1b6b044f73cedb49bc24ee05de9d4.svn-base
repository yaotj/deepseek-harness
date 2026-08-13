package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.MetroAgmKeyPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface MetroAgmKeyPoolMapper {
    List<MetroAgmKeyPool> selectByProviderIdAndBatchNumber(@Param("providerId") String providerId,
                                                           @Param("keyBathNumber") Long keyBathNumber);
}
