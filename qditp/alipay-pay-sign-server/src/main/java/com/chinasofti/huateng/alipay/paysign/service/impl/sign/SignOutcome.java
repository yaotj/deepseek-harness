package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;

/**
 * 一次签约落库的**结果形状**，三态、sealed。
 *
 * <p>为什么要它：原实现用「返回 response 对象 + 中途 return」表达这三种结局，于是
 * 「哪种结局要记签约流水、哪种要同步支付通道」这条判断散在方法体各处 —— 加一种结局时
 * 编译器不会提醒你补处置。收成 sealed 后编排层是穷尽 {@code switch}，
 * <b>少一个分支直接编译失败</b>（与 `rpc` 的 {@code RpcOutcome} 同一手法，ADR-D131）。
 *
 * <p><b>刻意只表达「成功」的三种形状</b>：换号拒绝、冲突后回查不到生效签约，
 * 两者在重构前后都是 {@code BusinessException} 直接抛给全局处理器 —— 那是**请求失败**、
 * 不是另一种结局。硬塞成第四个 case 会让调用方以为「拒绝也算一种正常返回」。
 */
sealed interface SignOutcome {

    /**
     * 库里已有一条生效签约：幂等重复请求，或并发竞态后回查到兄弟请求刚写成的那行。
     *
     * <p>**这一支既不记签约流水、也不同步支付通道** —— 那两件事在首次签约成立时已经做过，
     * 重复请求再做一遍等于多一条流水与一次无意义出网。
     */
    record AlreadySigned(AlipaySignInfo existing) implements SignOutcome {
    }

    /** 历史行（已解约）就地 CAS 复活成 {@code SIGNED}：本表主键是 {@code THIRD_USER_ID} 单列，只能这么签回来（ADR-D135）。 */
    record Reactivated(AlipaySignInfo signInfo) implements SignOutcome {
    }

    /** 该用户全表无行，INSERT 建立首次签约。 */
    record Created(AlipaySignInfo signInfo) implements SignOutcome {
    }
}
