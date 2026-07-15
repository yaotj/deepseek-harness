package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserAccTicketNo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Component
public interface UserAccTicketNoMapper {
    UserAccTicketNo selectUnusedCard();

    long countUnusedCards();

    int insert(UserAccTicketNo record);

    int batchInsert(@Param("list") List<UserAccTicketNo> list);

    int updateAllocateCard(@Param("id") Integer id,
                           @Param("thirdUserId") String thirdUserId,
                           @Param("regTms") LocalDateTime regTms);
}
