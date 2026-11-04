package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.entity.Graylist;
import com.chinasofti.huateng.blacklist.entity.GraylistOperateLog;
import com.chinasofti.huateng.blacklist.mapper.GraylistMapper;
import com.chinasofti.huateng.blacklist.mapper.GraylistOperateLogMapper;
import com.chinasofti.huateng.blacklist.service.GraylistService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 灰名单服务实现：当前在灰表 + 操作流水，写操作均以唯一索引 + DuplicateKeyException 兜底。 */
@Service
public class GraylistServiceImpl implements GraylistService {

    /** 发起方：01 系统自动，09 运维手工。运营页发起方在服务端定死 09，NEVER 让前台传值覆盖。 */
    private static final String SOURCE_OPS = "09";

    private final GraylistMapper graylistMapper;
    private final GraylistOperateLogMapper graylistOperateLogMapper;

    public GraylistServiceImpl(GraylistMapper graylistMapper,
                               GraylistOperateLogMapper graylistOperateLogMapper) {
        this.graylistMapper = graylistMapper;
        this.graylistOperateLogMapper = graylistOperateLogMapper;
    }

    @Override
    public ResultVO<PageInfo<Graylist>> page(String cardId, String thirdUserId,
                                              String createTimeBegin, String createTimeEnd,
                                              Integer pageNum, Integer pageSize) {
        PageInfo<Graylist> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> graylistMapper.selectPage(trimToNull(cardId), trimToNull(thirdUserId),
                        trimToNull(createTimeBegin), trimToNull(createTimeEnd)));
        return ResultMapper.ok(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> addGray(String cardId, String thirdUserId, String cardType,
                                  String bizNo, String reason, String operator) {
        String normalizedCardId = trimToNull(cardId);
        if (normalizedCardId == null) {
            return ResultMapper.illegalParams("卡号不能为空");
        }
        Graylist record = new Graylist();
        record.setCardId(normalizedCardId);
        record.setThirdUserId(trimToNull(thirdUserId));
        record.setCardType(trimToNull(cardType));
        record.setGraySource(SOURCE_OPS);
        record.setBizNo(trimToNull(bizNo));
        record.setReason(trimToNull(reason));
        record.setCreateBy(trimToNull(operator));
        try {
            graylistMapper.insert(record);
        } catch (DuplicateKeyException duplicate) {
            // 已在灰名单：幂等成功，不覆盖原记录（重灰以原进灰信息为准）。
            return ResultMapper.ok();
        }
        graylistOperateLogMapper.insert(buildLog(normalizedCardId, trimToNull(thirdUserId),
                "ADD", operator, reason));
        return ResultMapper.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> removeGray(String cardId, String reason, String operator) {
        String normalizedCardId = trimToNull(cardId);
        if (normalizedCardId == null) {
            return ResultMapper.illegalParams("卡号不能为空");
        }
        Graylist record = graylistMapper.selectByCardId(normalizedCardId);
        if (record == null) {
            return ResultMapper.error("该卡号不在灰名单中");
        }
        int deleted = graylistMapper.deleteByCardId(normalizedCardId);
        if (deleted == 0) {
            // 并发移出已被处理：幂等成功。
            return ResultMapper.ok();
        }
        graylistOperateLogMapper.insert(buildLog(normalizedCardId, record.getThirdUserId(),
                "REMOVE", operator, reason));
        return ResultMapper.ok();
    }

    @Override
    public ResultVO<PageInfo<GraylistOperateLog>> operateLogPage(String cardId, String operateType,
                                                                 Integer pageNum, Integer pageSize) {
        PageInfo<GraylistOperateLog> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> graylistOperateLogMapper.selectPage(
                        trimToNull(cardId), trimToNull(operateType)));
        return ResultMapper.ok(page);
    }

    private GraylistOperateLog buildLog(String cardId, String thirdUserId, String operateType,
                                        String operator, String reason) {
        GraylistOperateLog logRecord = new GraylistOperateLog();
        logRecord.setCardId(cardId);
        logRecord.setThirdUserId(thirdUserId);
        logRecord.setOperateType(operateType);
        logRecord.setOperator(trimToNull(operator));
        logRecord.setReason(trimToNull(reason));
        return logRecord;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
