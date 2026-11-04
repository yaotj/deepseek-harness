package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.page.KeyVersionView;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface MetroAgmKeyVersionMapper {
    Long selectMaxApprovedBatchNumber(String providerId);

    /**
     * 综管台密钥版本查看：按接入方取最新一条 AGM 密钥版本记录（只读元信息）。
     *
     * <p>NEVER 在本 mapper 新增返回 KEY_VALUE 的方法。</p>
     */
    List<KeyVersionView> selectLatestByProvider();

    /** 查某接入方某批次版本的当前审批状态码（20010/20020/20030），无记录返 null。 */
    String selectApprovalStatus(String providerId, Long batchNumber);

    /**
     * 审批状态 CAS 迁移：WHERE 带当前状态 {@code fromStatus}，前置状态写进 SQL 才是权威白名单。
     *
     * @return 受影响行数；0 行 = 状态已被并发改掉或记录不存在，调用方 MUST 回查后按冲突处理，
     *         NEVER 把 0 行当成功
     */
    int transitionApprovalStatus(String providerId, Long batchNumber, String fromStatus, String toStatus,
                                 String operator);
}
