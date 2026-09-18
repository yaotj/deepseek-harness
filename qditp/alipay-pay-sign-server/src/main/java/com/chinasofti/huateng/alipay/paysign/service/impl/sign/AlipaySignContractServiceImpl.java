package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipaySignContractService;
import com.chinasofti.huateng.alipay.paysign.service.impl.channelsync.ChannelSyncDeliverer;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 签约链路的编排层：只做「按顺序调协作者 + 按结果形状决定后续动作」，不含落库细节与冲突判定。
 *
 * <p>四个协作者、职责互不重叠：
 * <ul>
 *   <li>{@link SignCommand} —— 入参校验，过了就保证四个必填项非空；
 *   <li>{@link SignRepository} —— 三支落库与唯一约束竞态兜底，返 {@link SignOutcome} 三态；
 *   <li>{@link SignLogWriter} —— 签约成功流水（内部吞异常，记不上不该打断签约）；
 *   <li>{@link ChannelSyncDeliverer} —— 支付通道同步 outbox 的**唯一**出网 + 三态回写（首推与补偿共用，ADR-D132）。
 * </ul>
 *
 * <p><b>本类刻意不带 {@code @Transactional}，NEVER 加回</b>（ADR-D129）：链路里有两次出网
 * —— 查账户域拿卡号、签约后同步支付通道 —— 任一次被事务包住，行锁持有时长就等于对端响应时长，
 * 即 AGENTS.md §5.2 那条已发生过生产事故的形态。落库只有一条语句，自动提交足够。
 *
 * <p>通道同步失败**仍对上游返 {@code 0000}**：协议号已落库、签约已成立，而扣款链路是按
 * {@code ALIPAY_SIGN_INFO} 查协议号的、不依赖账户域那份通道信息；不一致由 {@code CHANNEL_SYNC_*}
 * 四列承载并留给补偿扫描（`/internal/alipay/channelSync/compensate`），
 * 而不是靠回滚把已成立的签约抹掉。
 */
@Service
public class AlipaySignContractServiceImpl implements AlipaySignContractService {

    private static final Logger log = LoggerFactory.getLogger(AlipaySignContractServiceImpl.class);

    @Autowired
    private SignRepository signRepository;

    /**
     * 只用它的**查询方向**：按 thirdUserId 取开户信息（卡号 / 卡类型）。
     *
     * <p>ADR-D131 只把「更新支付通道」那条出网收口进端口，因为只有它需要区分「业务拒绝」与「不可达」。
     * 查询这条的失败处置是「查不到就拒绝签约」，异常原样冒泡到全局处理器即正确行为，
     * 再包一层端口只会多一层壳。<b>NEVER 为了「看起来整齐」把查询也收成端口。</b>
     */
    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Autowired
    private ChannelSyncDeliverer channelSyncDeliverer;

    @Autowired
    private SignLogWriter signLogWriter;

    @Override
    public AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request) {
        log.info("接收到支付宝添加签约信息报文: {}", JSON.toJSONString(request));
        SignCommand command = SignCommand.from(request);

        AlipaySignInfo activeSign = signRepository.findActiveSign(command.thirdUserId());
        if (activeSign != null) {
            return onAlreadySigned(command, activeSign);
        }

        AlipayUserInfoDTO userInfo = alipayAccountClient.selectByThirdUserId(command.thirdUserId());
        if (userInfo == null || userInfo.getCardId() == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "用户未开户，无法签约");
        }

        SignOutcome outcome = signRepository.save(command.toSignRow(userInfo));
        return switch (outcome) {
            case SignOutcome.AlreadySigned(AlipaySignInfo existing) -> onAlreadySigned(command, existing);
            case SignOutcome.Reactivated(AlipaySignInfo signInfo) -> onSignEstablished(command, signInfo, request);
            case SignOutcome.Created(AlipaySignInfo signInfo) -> onSignEstablished(command, signInfo, request);
        };
    }

    /**
     * 命中一条**生效中**的签约（幂等重复请求，或并发竞态回查命中）时的处置（ADR-D135）。
     *
     * <p>协议号相同即幂等，原样返成功 + 库内号。<b>协议号不同一律拒绝，NEVER 改成覆盖更新</b>：
     * {@code CHANNEL_AGREEMENT_CODE} 是销卡通知发给支付中心的那个号，覆盖旧号等于让旧协议再也解不了约；
     * 正确顺序是先解约再重签。此前这里不比对协议号、直接返库里的旧号，于是**换号重签被静默吞掉**。
     *
     * <p>这一支**不记流水、不同步通道**：两件事在首次签约成立时已经做过。
     */
    private AlipayTripAddContractRespDTO onAlreadySigned(SignCommand command, AlipaySignInfo existing) {
        if (!command.agreementCode().equals(existing.getAgreementCode())) {
            log.error("该用户已有生效签约且协议号不一致，拒绝签约, thirdUserId={}, 库内协议号={}, 入参协议号={}",
                    command.thirdUserId(), existing.getAgreementCode(), command.agreementCode());
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该用户已存在生效中的签约，请先解约后再签约");
        }
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("该用户已签约");
        response.setAgreementCode(existing.getAgreementCode());
        return response;
    }

    /**
     * 签约行刚落库成立（INSERT 或 CAS 复活）后的收口：先返回码，再记流水，最后同步支付通道。
     *
     * <p>顺序是有意的：流水与通道同步都不影响本次应答的成败，因此**放在应答组装之后**；
     * 通道同步内部 NEVER 抛异常（失败留在 {@code CHANNEL_SYNC_STATUS != 'SUCCESS'} 等补偿）。
     * {@code request} 原样传给流水记录，是为了把渠道上送的整包报文留证。
     */
    private AlipayTripAddContractRespDTO onSignEstablished(SignCommand command, AlipaySignInfo signInfo,
                                                          AlipayTripAddContractReqDTO request) {
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        response.setAgreementCode(command.agreementCode());

        signLogWriter.recordSignSuccess(signInfo, request, response);
        channelSyncDeliverer.deliver(command.thirdUserId(), command.channelUserAccount(), command.agreementCode());

        log.info("支付宝签约成功, thirdUserId={}, agreementCode={}", command.thirdUserId(), command.agreementCode());
        return response;
    }

    @Override
    public AlipaySignInfoDTO selectSignInfo(String thirdUserId) {
        if (!StringUtils.hasText(thirdUserId)) {
            return null;
        }
        AlipaySignInfo signInfo = signRepository.findActiveSign(thirdUserId);
        if (signInfo == null) {
            return null;
        }
        AlipaySignInfoDTO dto = new AlipaySignInfoDTO();
        dto.setThirdUserId(signInfo.getThirdUserId());
        dto.setChannelAgreementCode(signInfo.getChannelAgreementCode());
        dto.setCardId(signInfo.getCardId());
        dto.setCardType(signInfo.getCardType());
        return dto;
    }
}
