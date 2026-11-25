package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.port.AlipayBlacklistNotifyPort;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证渠道分流逻辑：地铁App（01）跳过支付宝通知直接解黑，支付宝（02）走正常通知流程。
 */
@ExtendWith(MockitoExtension.class)
class BlacklistChannelSyncServiceTest {

    @Mock
    private BlacklistMapper blacklistMapper;

    @Mock
    private AlipayBlacklistNotifyPort alipayBlacklistNotifyPort;

    @Mock
    private BlacklistReleaseFinalizer blacklistReleaseFinalizer;

    @InjectMocks
    private BlacklistChannelSyncService service;

    @Test
    void metroAppChannelSkipsAlipayNotification() {
        // 模拟地铁App渠道（01）的黑名单记录
        Blacklist row = new Blacklist();
        row.setId(1L);
        row.setCardId("0426090949000001");
        row.setChannelCode("01"); // 地铁App
        row.setStatus("RELEASING");
        row.setReleaseReason("欠费已结清");
        row.setThirdUserId("test_user");
        row.setCardType("0441");

        when(blacklistMapper.selectByCardIds(List.of("0426090949000001"))).thenReturn(List.of(row));
        when(blacklistReleaseFinalizer.completeRelease(row)).thenReturn(true);

        // 调用公共方法
        service.deliverReleaseByCardId("0426090949000001");

        // 验证：不应调用支付宝通知
        verify(alipayBlacklistNotifyPort, never()).notifyBlackListChange(any(), any(), any(), any(), any());
        // 验证：应直接完成解黑收口
        verify(blacklistReleaseFinalizer).completeRelease(row);
    }

    @Test
    void alipayChannelCallsAlipayNotification() {
        // 模拟支付宝渠道（02）的黑名单记录
        Blacklist row = new Blacklist();
        row.setId(2L);
        row.setCardId("0426090949000002");
        row.setChannelCode("02"); // 支付宝
        row.setStatus("RELEASING");
        row.setReleaseReason("欠费已结清");
        row.setThirdUserId("test_user");
        row.setCardType("0441");

        when(blacklistMapper.selectByCardIds(List.of("0426090949000002"))).thenReturn(List.of(row));
        when(alipayBlacklistNotifyPort.notifyBlackListChange(any(), any(), any(), eq("2"), any()))
                .thenReturn(new RpcOutcome.Ok());
        when(blacklistReleaseFinalizer.completeRelease(row)).thenReturn(true);

        // 调用公共方法
        service.deliverReleaseByCardId("0426090949000002");

        // 验证：应调用支付宝通知
        verify(alipayBlacklistNotifyPort).notifyBlackListChange(
                eq("0426090949000002"), eq("test_user"), eq("0441"), eq("2"), eq("欠费已结清"));
        // 验证：应完成解黑收口
        verify(blacklistReleaseFinalizer).completeRelease(row);
    }

    @Test
    void unknownChannelCallsAlipayNotification() {
        // 模拟未知渠道（99）的黑名单记录，也应走支付宝通知
        Blacklist row = new Blacklist();
        row.setId(3L);
        row.setCardId("0426090949000003");
        row.setChannelCode("99"); // 未知渠道
        row.setStatus("RELEASING");
        row.setReleaseReason("欠费已结清");
        row.setThirdUserId("test_user");
        row.setCardType("0441");

        when(blacklistMapper.selectByCardIds(List.of("0426090949000003"))).thenReturn(List.of(row));
        when(alipayBlacklistNotifyPort.notifyBlackListChange(any(), any(), any(), eq("2"), any()))
                .thenReturn(new RpcOutcome.Ok());
        when(blacklistReleaseFinalizer.completeRelease(row)).thenReturn(true);

        // 调用公共方法
        service.deliverReleaseByCardId("0426090949000003");

        // 验证：未知渠道也应调用支付宝通知
        verify(alipayBlacklistNotifyPort).notifyBlackListChange(any(), any(), any(), eq("2"), any());
    }
}
