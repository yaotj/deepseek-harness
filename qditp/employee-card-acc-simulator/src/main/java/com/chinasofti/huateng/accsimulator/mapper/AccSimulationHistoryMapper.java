package com.chinasofti.huateng.accsimulator.mapper;

import com.chinasofti.huateng.accsimulator.entity.AccSimulationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模拟 ACC 调用记录数据访问接口。
 *
 * <p>提供模拟通知历史记录的写入与查询能力，
 * 记录持久化到 ACC_SIMULATION_HISTORY 表。</p>
 */
@Mapper
public interface AccSimulationHistoryMapper {

    /**
     * 保存一次 ACC 模拟调用记录，便于审计和失败排查。
     *
     * @param history 调用记录
     */
    void insert(AccSimulationHistory history);

    /**
     * 按操作类型分页查询调用记录，按创建时间倒序。
     *
     * @param operation 操作类型，精确匹配，可为 null
     * @return 调用记录列表
     */
    List<AccSimulationHistory> selectPage(@Param("operation") String operation);

    /**
     * 清空所有调用记录。
     *
     * @return 删除行数
     */
    int deleteAll();
}
