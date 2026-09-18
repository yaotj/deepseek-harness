package com.chinasofti.huateng.accsimulator.mapper;

import com.chinasofti.huateng.accsimulator.entity.AccEmployeeCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模拟 ACC 员工卡数据访问接口。
 *
 * <p>提供员工卡的增删查改能力，卡号为业务主键，
 * 新增与编辑通过 {@link #merge} 统一处理。</p>
 */
@Mapper
public interface AccEmployeeCardMapper {

    /**
     * 新增或更新员工卡；卡号已存在时按编辑处理，不存在时插入。
     *
     * @param card 员工卡信息
     */
    void merge(AccEmployeeCard card);

    /**
     * 按管理端筛选条件分页查询模拟 ACC 员工卡。
     *
     * @param cardNo       员工号，模糊匹配，可为 null
     * @param employeeName 员工姓名，模糊匹配，可为 null
     * @param cardStatus   电子卡状态，精确匹配，可为 null
     * @return 员工卡列表
     */
    List<AccEmployeeCard> selectPage(@Param("cardNo") String cardNo,
                                     @Param("employeeName") String employeeName,
                                     @Param("cardStatus") Integer cardStatus);

    /**
     * 按员工号查询员工卡。
     *
     * @param cardNo 员工号/实体卡号
     * @return 员工卡记录，不存在返回 null
     */
    AccEmployeeCard selectByCardNo(@Param("cardNo") String cardNo);

    /**
     * 更新指定员工号的电子卡状态。
     *
     * @param cardNo     员工号/实体卡号
     * @param cardStatus 目标状态：1 启用、2 禁用
     * @return 影响行数
     */
    int updateStatus(@Param("cardNo") String cardNo, @Param("cardStatus") Integer cardStatus);

    /**
     * 清空所有模拟员工卡记录。
     *
     * @return 删除行数
     */
    int deleteAll();
}
