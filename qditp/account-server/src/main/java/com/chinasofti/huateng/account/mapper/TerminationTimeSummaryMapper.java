package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.page.TerminationTimeSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;


@Mapper
@Component
public interface TerminationTimeSummaryMapper {

    List<TerminationTimeSummary> selectByCardIds(@Param("cardIds") List<String> cardIds);
}
