package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.constant.BlacklistErrorCodeEnum;
import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.entity.BlacklistOperateLog;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.mapper.BlacklistOperateLogMapper;
import com.chinasofti.huateng.blacklist.service.BlacklistService;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.DeleteBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑名单业务服务实现。
 */
@Service
public class BlacklistServiceImpl implements BlacklistService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BlacklistServiceImpl.class);
    @Autowired
    private BlacklistMapper blacklistMapper;

    @Autowired
    private BlacklistOperateLogMapper blacklistOperateLogMapper;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${app.notify.blacklist-url:}")
    private String appNotifyBlacklistUrl;

    /**
     * 查询卡号是否命中黑名单。
     *
     * @param request 查询黑名单请求参数
     * @return 查询黑名单结果
     */
    @Override
    public QueryBlackListResult queryBlackList(QueryBlackListReqDTO request) {
        QueryBlackListResult result = new QueryBlackListResult();
        result.setFailedCount(0);

        if (request == null || !StringUtils.hasText(request.getCardId())) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            result.setInBlack("0");
            return result;
        }

        List<String> cardIds = parseCardIds(request.getCardId());
        if (cardIds.isEmpty()) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            result.setInBlack("0");
            return result;
        }

        int blackCount = blacklistMapper.countByCardIds(cardIds);
        result.setRetCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");
        result.setInBlack(blackCount > 0 ? "1" : "0");
        return result;
    }

    /**
     * 新增黑名单。
     *
     * @param request 新增黑名单请求参数
     * @return 黑名单操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlackListOperateResult addBlackList(AddBlackListReqDTO request) {
        BlackListOperateResult result = new BlackListOperateResult();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }

        String cardId = request.getCardId().trim();
        List<String> cardIds = new ArrayList<>();
        cardIds.add(cardId);
        if (blacklistMapper.countByCardIds(cardIds) <= 0) {
            Blacklist blacklist = new Blacklist();
            blacklist.setCardId(cardId);
            blacklist.setThirdUserId(StringUtils.hasText(request.getThirdUserId()) ? request.getThirdUserId().trim() : null);
            blacklist.setReason(request.getReason());
            blacklistMapper.insert(blacklist);
        }
        insertOperateLog(cardId, request.getThirdUserId(), "ADD", request.getReason());

        result.setRetCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");

        notifyAppBlacklistAsync(cardId, request.getThirdUserId(), request.getCardType(), "1", request.getReason());

        return result;
    }

    /**
     * 物理删除黑名单。
     *
     * @param request 删除黑名单请求参数
     * @return 黑名单操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlackListOperateResult deleteBlackList(DeleteBlackListReqDTO request) {
        BlackListOperateResult result = new BlackListOperateResult();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }

        List<String> cardIds = parseCardIds(request.getCardId());
        if (cardIds.isEmpty()) {
            result.setRetCode(BlacklistErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }

        List<Blacklist> existsRecords = blacklistMapper.selectByCardIds(cardIds);
        blacklistMapper.deleteByCardIds(cardIds);
        for (Blacklist record : existsRecords) {
            insertOperateLog(record.getCardId(), record.getThirdUserId(), "DELETE", record.getReason());
        }
        result.setRetCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg("成功");
        return result;
    }

    /**
     * 将逗号分隔的卡号字符串解析为卡号列表。
     *
     * @param cardId 卡号字符串
     * @return 卡号列表
     */
    private List<String> parseCardIds(String cardId) {
        String[] items = cardId.split(",");
        List<String> cardIds = new ArrayList<>();
        for (String item : items) {
            if (StringUtils.hasText(item)) {
                cardIds.add(item.trim());
            }
        }
        return cardIds;
    }

    /**
     * 写入黑名单操作记录。
     *
     * @param cardId 卡ID
     * @param thirdUserId 三方用户ID
     * @param operateType 操作类型
     * @param reason 操作原因
     */
    private void insertOperateLog(String cardId, String thirdUserId, String operateType, String reason) {
        BlacklistOperateLog operateLog = new BlacklistOperateLog();
        operateLog.setCardId(cardId);
        operateLog.setThirdUserId(StringUtils.hasText(thirdUserId) ? thirdUserId.trim() : null);
        operateLog.setOperateType(operateType);
        operateLog.setReason(reason);
        blacklistOperateLogMapper.insert(operateLog);
    }

    /**
     * 异步通知地铁APP黑名单状态变更。
     *
     * @param cardId 卡ID
     * @param thirdUserId 三方用户ID
     * @param cardType 卡类型编码
     * @param blackListType 黑名单类型，1：加入黑名单，2：移除黑名单
     * @param reason 变更原因
     */
    private void notifyAppBlacklistAsync(String cardId, String thirdUserId, String cardType, String blackListType, String reason) {
        if (StringUtils.hasText(appNotifyBlacklistUrl)) {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("thirdUserId", thirdUserId);
            requestBody.put("cardId", cardId);
            requestBody.put("cardType", cardType);
            requestBody.put("blackListType", blackListType);
            requestBody.put("optionDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            if ("1".equals(blackListType)) {
                requestBody.put("expireTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            }
            requestBody.put("signType", "00");
            requestBody.put("sign", "");

            Mono<String> mono = webClientBuilder.build()
                    .post()
                    .uri(appNotifyBlacklistUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> log.error("异步通知地铁APP黑名单变更异常, cardId={}", cardId, e))
                    .doOnNext(response -> log.info("异步通知地铁APP黑名单变更完成, cardId={}, response={}", cardId, response))
                    .onErrorResume(e -> {
                        log.error("异步通知地铁APP黑名单变更失败, cardId={}", cardId, e);
                        return Mono.empty();
                    });

            mono.subscribe();
        } else {
            log.warn("地铁APP通知服务地址未配置，跳过黑名单变更通知, cardId={}", cardId);
        }
    }
}
