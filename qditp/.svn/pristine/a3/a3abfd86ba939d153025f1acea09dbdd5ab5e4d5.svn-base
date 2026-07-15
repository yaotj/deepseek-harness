package com.chinasofti.huateng.alipay.account.mapper;

import com.chinasofti.huateng.alipay.account.entity.AlipayCardPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface AlipayCardPoolMapper {
    AlipayCardPool selectNextAvailableCard();

    int updateCardStatus(AlipayCardPool record);

    AlipayCardPool selectUnusedCard();

    long countUnusedCards();

    int updateAllocateCard(@Param("id") String cardId, @Param("thirdUserId") String thirdUserId, @Param("regTms") java.time.LocalDateTime regTms);

    int batchInsert(List<AlipayCardPool> ticketNoList);
}
