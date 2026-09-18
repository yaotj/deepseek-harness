package com.chinasofti.huateng.account.service.impl;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.account.entity.UserPayChannel;
import com.chinasofti.huateng.account.mapper.TerminationTimeSummaryMapper;
import com.chinasofti.huateng.account.mapper.UserItpRegInfoMapper;
import com.chinasofti.huateng.account.mapper.UserPayChannelMapper;
import com.chinasofti.huateng.account.page.ItpPayChannelView;
import com.chinasofti.huateng.account.page.ItpUserSearchView;
import com.chinasofti.huateng.account.page.RegStatView;
import com.chinasofti.huateng.account.page.TerminationTimeSummary;
import com.chinasofti.huateng.account.service.ItpUserQueryService;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 运营后台非支付宝用户查询的实现，见 {@link ItpUserQueryService}。
 */
@Service
public class ItpUserQueryServiceImpl implements ItpUserQueryService {
    private final UserItpRegInfoMapper userItpRegInfoMapper;
    private final UserPayChannelMapper userPayChannelMapper;
    private final TerminationTimeSummaryMapper terminationTimeSummaryMapper;

    public ItpUserQueryServiceImpl(UserItpRegInfoMapper userItpRegInfoMapper,
                                   UserPayChannelMapper userPayChannelMapper,
                                   TerminationTimeSummaryMapper terminationTimeSummaryMapper) {
        this.userItpRegInfoMapper = userItpRegInfoMapper;
        this.userPayChannelMapper = userPayChannelMapper;
        this.terminationTimeSummaryMapper = terminationTimeSummaryMapper;
    }

    @Override
    public List<ItpUserSearchView> search(String queryType, String keyword) {
        String trimmed = keyword.trim();
        List<UserItpRegInfo> users = switch (queryType) {
            case "THIRD_USER_ID" -> userItpRegInfoMapper.selectListByThirdUserId(trimmed);
            case "MSISDN" -> userItpRegInfoMapper.selectListByMsisdn(trimmed);
            case "CARD_ID" -> userItpRegInfoMapper.selectListByCardId(trimmed);
            default -> null;
        };
        if (users == null) {
            return null;
        }
        Map<String, TerminationTimeSummary> terminations = loadTerminationTimes(users);
        return users.stream().map(user -> toView(user, terminations)).toList();
    }

    /**
     * 对命中的全部票卡做一次聚合查询取回解约时间，按 {@code thirdUserId + cardId} 建索引。
     */
    private Map<String, TerminationTimeSummary> loadTerminationTimes(List<UserItpRegInfo> users) {
        List<String> cardIds = users.stream()
                .map(UserItpRegInfo::getCardId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        return terminationTimeSummaryMapper.selectByCardIds(cardIds).stream()
                .collect(Collectors.toMap(
                        summary -> terminationKey(summary.getThirdUserId(), summary.getCardId()),
                        Function.identity(),
                        (a, b) -> a));
    }

    private String terminationKey(String thirdUserId, String cardId) {
        return thirdUserId + "" + cardId;
    }

    @Override
    public List<ItpPayChannelView> payChannels(String thirdUserId, String cardId, String cardType) {
        UserItpRegInfo registration = userItpRegInfoMapper.selectActiveByThirdUserIdAndCardIdAndCardType(
                thirdUserId.trim(), cardId.trim(), cardType.trim());
        if (registration == null) {
            return null;
        }

        List<UserPayChannel> channels = userPayChannelMapper.selectByThirdUserIdAndCardTypeAndCardId(
                registration.getThirdUserId(), registration.getCardType(), registration.getCardId());
        return channels.stream()
                .map(channel -> toPayChannelView(channel, registration))
                .toList();
    }

    private ItpPayChannelView toPayChannelView(UserPayChannel channel, UserItpRegInfo registration) {
        ItpPayChannelView view = new ItpPayChannelView();
        view.setThirdUserId(channel.getThirdUserId());
        view.setCardId(channel.getCardId());
        view.setCardType(channel.getCardType());
        view.setChannel(channel.getChannel());
        view.setThirdPayId(maskAccount(channel.getThirdPayId()));
        view.setReqContractNo(channel.getReqContractNo());
        view.setStatus(channel.getStatus());
        view.setCreateTms(channel.getCreateTms());
        view.setUpdateTms(channel.getUpdateTms());
        view.setDefaultChannel(channel.getChannel() != null && channel.getChannel().equals(registration.getChannel()));
        view.setTerminationReady(StringUtils.hasText(channel.getReqContractNo())
                && "ACTIVE".equalsIgnoreCase(channel.getStatus()));
        view.setPayAccountId(maskAccount(channel.getPayAccountId()));
        return view;
    }

    private String maskAccount(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.trim();
        if (normalized.length() <= 4) {
            return "****";
        }
        int visibleLength = Math.min(4, normalized.length() / 2);
        return normalized.substring(0, visibleLength) + "****"
                + normalized.substring(normalized.length() - visibleLength);
    }

    private ItpUserSearchView toView(UserItpRegInfo user, Map<String, TerminationTimeSummary> terminations) {
        ItpUserSearchView view = new ItpUserSearchView();
        view.setThirdUserId(user.getThirdUserId());
        view.setCardId(user.getCardId());
        view.setCardType(user.getCardType());
        view.setItpCardType(user.getItpCardType());
        view.setMsisdn(maskPhone(user.getMsisdn()));
        view.setUserName(maskName(user.getUserName()));
        view.setCardIssueCode(user.getCardIssueCode());
        view.setIssueOrgCode(user.getIssueOrgCode());
        view.setChannel(user.getChannel());
        view.setCompanionFlag(user.getCompanionFlag());
        view.setStatus(user.isActive() ? "有效" : user.isCanceled() ? "已注销" : "未知");
        view.setRegTms(user.getRegTms());
        TerminationTimeSummary termination = terminations.get(terminationKey(user.getThirdUserId(), user.getCardId()));
        if (termination != null) {
            view.setTerminationRequestTime(termination.getRequestTime());
            view.setTerminationCompleteTime(termination.getCompleteTime());
        }
        return view;
    }

    private String maskPhone(String value) {
        return value != null && value.length() >= 7
                ? value.substring(0, 3) + "****" + value.substring(value.length() - 4) : value;
    }

    @Override
    public List<ItpUserSearchView> batchSearch(List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = cardIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<UserItpRegInfo> users = userItpRegInfoMapper.selectListByCardIds(normalized);
        Map<String, TerminationTimeSummary> terminations = loadTerminationTimes(users);
        return users.stream().map(user -> toView(user, terminations)).toList();
    }

    @Override
    public List<RegStatView> regStats(String startDate, String endDate) {
        String start = StringUtils.hasText(startDate) ? startDate.trim() : null;
        String end = StringUtils.hasText(endDate) ? endDate.trim() : null;
        List<RegStatView> stats = userItpRegInfoMapper.countGroupByCardType(start, end);
        if (stats == null) {
            return List.of();
        }
        for (RegStatView stat : stats) {
            CardTypeCodeEnum cardType = CardTypeCodeEnum.fromCode(stat.getCardType());
            stat.setCardTypeName(cardType != null ? cardType.getDesc() : stat.getCardType());
        }
        return stats;
    }

    private String maskName(String value) {
        return value != null && value.length() > 1 ? value.substring(0, 1) + "*" : value;
    }
}
