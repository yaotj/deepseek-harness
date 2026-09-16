package com.chinasofti.huateng.facepay.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 序列取号。SQL 写在 {@code mapper/F2fSequenceMapper.xml}，不用 MyBatis 注解
 * （AGENTS.md §5.1：统一走自研 mybatis-adaptor，SQL 不进 Java 注解）。
 *
 * <p>旧实现 {@code OrderSeqMapper} 用 {@code @Select} 把 SQL 写在注解里，本模块不沿用。</p>
 */
@Mapper
public interface F2fSequenceMapper {

    /**
     * 取订单号序列段。序列建为 {@code MAXVALUE 9999 CYCLE}，返回值恒在 1~9999。
     *
     * <p>与旧服务的 {@code ORDER_NO_SEQ} <b>不共用</b>：蓝绿并行期两套服务各自发号，
     * 旧服务回滚也不会因新服务消耗过序列而出现空洞。</p>
     */
    Long nextOrderNoSeq();
}
