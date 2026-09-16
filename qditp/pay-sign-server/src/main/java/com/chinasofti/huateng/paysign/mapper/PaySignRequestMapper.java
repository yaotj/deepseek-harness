package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaySignRequestMapper {
    int insert(PaySignRequest record);

    // 通过 requestSignSeq 查询（取最新一条，用于补充 thirdUserId）
    PaySignRequest selectByRequestSignSeq(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 取指定流水号下最新一条**签约结果**流水（OPERATION_TYPE='RECEIVE_SIGN_RESULT'）。
     *
     * <p>供单条通知重发 /internal/paySign/resendNotify 使用。这里 MUST 带 OPERATION_TYPE 过滤，
     * NEVER 复用 {@link #selectByRequestSignSeq(String)}：同一流水号在本表还有 OPERATION_TYPE='SIGN'
     * （请求签约信息时写的）等记录，按 CREATE_TMS 取最新可能拿到 SIGN 那条——它的 SIGN_STATUS 为空、
     * 也不是通知队列里的行，拿它去重发会发出一条 signResult 为空的报文。</p>
     */
    PaySignRequest selectLatestSignResultBySeq(@Param("requestSignSeq") String requestSignSeq);

    /**
     * 取一批需要补偿通知的签约流水，按 CREATE_TMS 升序（最久没成功的先补），并过滤已超过最大重试次数的记录。
     * <p>命中两类记录：
     * <ul>
     *   <li>{@code NOTIFY_STATUS='FAILED'} —— 通知发出去失败了，正常重试；</li>
     *   <li>{@code NOTIFY_STATUS='PENDING'} 且滞留超过 {@code staleMinutes} 分钟 —— 流水插入时写的就是
     *       PENDING（{@code PaySignWorkflow} 签约结果分支），若随后的状态回写自身失败（如进程被杀、
     *       DB 抖动、拆箱 NPE），这行会永久停在 PENDING。只扫 FAILED 时它对补偿完全不可见。</li>
     * </ul>
     * 供签约通知补偿接口扫表使用。
     */
    List<PaySignRequest> selectCompensableNotify(@Param("maxRetry") int maxRetry,
                                                 @Param("staleMinutes") int staleMinutes,
                                                 @Param("limit") int limit);

    // 更新通知状态
    int updateNotifyStatus(PaySignRequest record);

    // 增加重试次数
    int increaseRetryCount(@Param("id") Long id);
}
