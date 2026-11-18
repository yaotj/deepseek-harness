package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.GraylistRisk;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GraylistRiskMapper {
    
    @Select("SELECT * FROM GRAYLIST_RISK WHERE 1=1")
    @Results({
        @Result(property = "id", column = "ID"),
        @Result(property = "cardId", column = "CARD_ID"),
        @Result(property = "ruleName", column = "RULE_NAME"),
        @Result(property = "riskTimes", column = "RISK_TIMES"),
        @Result(property = "riskLastTime", column = "RISK_LAST_TIME"),
        @Result(property = "managerId", column = "MANAGER_ID"),
        @Result(property = "updateTime", column = "UPDATE_TIME"),
        @Result(property = "regTime", column = "REG_TIME")
    })
    List<GraylistRisk> selectAll();
    
    @Select("SELECT * FROM GRAYLIST_RISK WHERE CARD_ID = #{cardId}")
    List<GraylistRisk> selectByCardId(@Param("cardId") String cardId);
    
    @Select("SELECT * FROM GRAYLIST_RISK WHERE RULE_NAME = #{ruleName}")
    List<GraylistRisk> selectByRuleName(@Param("ruleName") String ruleName);
    
    @Select("SELECT * FROM GRAYLIST_RISK WHERE CARD_ID = #{cardId} AND RULE_NAME = #{ruleName}")
    GraylistRisk selectByCardIdAndRuleName(@Param("cardId") String cardId, @Param("ruleName") String ruleName);
    
    @Insert("INSERT INTO GRAYLIST_RISK (CARD_ID, RULE_NAME, RISK_TIMES, RISK_LAST_TIME, MANAGER_ID, UPDATE_TIME, REG_TIME, MIGRATE_BATCH) " +
            "VALUES (#{cardId}, #{ruleName}, #{riskTimes}, #{riskLastTime}, #{managerId}, #{updateTime}, #{regTime}, #{migrateBatch})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GraylistRisk record);
    
    @Delete("DELETE FROM GRAYLIST_RISK WHERE CARD_ID = #{cardId} AND RULE_NAME = #{ruleName}")
    int deleteByCardIdAndRuleName(@Param("cardId") String cardId, @Param("ruleName") String ruleName);
}
