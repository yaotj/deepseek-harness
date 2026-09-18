package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 签约方向对 {@code ALIPAY_SIGN_INFO} 的**唯一写入口**：三支落库 + 唯一约束竞态兜底，收在一个类里。
 *
 * <p>抽它的理由：这三支不是「代码风格」问题，而是被表结构逼出来的语义
 * —— 主键是 {@code THIRD_USER_ID} 单列、解约只改状态不删行，因此「一个用户全表最多一行」（ADR-D135）。
 * 谁再往编排层写第二处 INSERT / UPDATE，就会重新踩「已解约用户永远签不回来」那个缺陷。
 * <b>NEVER 绕过本类直接注 {@link AlipaySignInfoMapper} 写签约行。</b>
 * （解约侧的状态写入是另一条 CAS，在 {@code TerminationNotifier}，两边刻意不共用方法。）
 *
 * <p>本类**不返回 boolean、不吞异常**：结果用 {@link SignOutcome} 三态表达，
 * 「回查不到生效签约」与「非冲突类异常」都原样抛出去。
 */
@Component
class SignRepository {

    private static final Logger log = LoggerFactory.getLogger(SignRepository.class);

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    /** 读**生效中**（{@code SIGN_STATUS='SIGNED'}）的那行；查不到返 null。谓词在 mapper XML 里，ADR-D135 加的状态条件 NEVER 去掉。 */
    AlipaySignInfo findActiveSign(String thirdUserId) {
        return alipaySignInfoMapper.selectByThirdUserIdAndChannel(thirdUserId, SignCommand.CHANNEL_ALIPAY);
    }

    /**
     * 落一行签约：**有历史行就 CAS 复活、没有才 INSERT**，两条路径撞上并发都收口到 {@link #onWriteConflict}。
     *
     * <p>「先 select 再写」挡不住并发 —— 两条请求会同时判定「无生效签约」，真正兜住的是主键与
     * {@code reactivateSign} 的状态白名单。竞态本身不是错误：结果与幂等重复请求等价。
     */
    SignOutcome save(AlipaySignInfo signRow) {
        String thirdUserId = signRow.getThirdUserId();
        if (alipaySignInfoMapper.selectAnyByThirdUserIdAndChannel(thirdUserId, SignCommand.CHANNEL_ALIPAY) != null) {
            if (alipaySignInfoMapper.reactivateSign(signRow) == 0) {
                return onWriteConflict(signRow);
            }
            return new SignOutcome.Reactivated(signRow);
        }
        try {
            alipaySignInfoMapper.insert(signRow);
        } catch (RuntimeException e) {
            if (!isUniqueConflict(e)) {
                throw e;
            }
            return onWriteConflict(signRow);
        }
        return new SignOutcome.Created(signRow);
    }

    /**
     * 撞唯一约束或 CAS 影响 0 行 —— 判为「另一条并发请求刚把同一用户签上」，回查后按幂等处理。
     *
     * <p>回查为空说明既没有生效签约、写又没成功，此时**MUST 对上游报错**：静默返成功会让渠道
     * 以为签约已成立，而库里一行都没有。
     */
    private SignOutcome onWriteConflict(AlipaySignInfo signRow) {
        log.warn("签约落库与并发请求冲突，判为重复请求, thirdUserId={}, agreementCode={}",
                signRow.getThirdUserId(), signRow.getAgreementCode());
        AlipaySignInfo committed = findActiveSign(signRow.getThirdUserId());
        if (committed == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "签约冲突且回查不到生效签约，请重试");
        }
        return new SignOutcome.AlreadySigned(committed);
    }

    /**
     * 沿 {@code getCause()} 链判「唯一约束冲突」，<b>NEVER 只看最外层异常类型</b>。
     *
     * <p>本模块开着 tracing，{@code resource/micro/web} 的观测切面会把异常重新包一层，
     * 只 {@code catch (DuplicateKeyException)} 会静默落空（AGENTS.md §5.2 那条已发生过的坑）。
     */
    private boolean isUniqueConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DuplicateKeyException || t instanceof DataIntegrityViolationException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
