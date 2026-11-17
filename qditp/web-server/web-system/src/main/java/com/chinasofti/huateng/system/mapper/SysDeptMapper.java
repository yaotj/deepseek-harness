package com.chinasofti.huateng.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.chinasofti.huateng.common.core.domain.entity.SysDept;


public interface SysDeptMapper
{

    public List<SysDept> selectDeptList(SysDept dept);


    public List<Long> selectDeptListByRoleId(@Param("roleId") Long roleId, @Param("deptCheckStrictly") boolean deptCheckStrictly);


    public SysDept selectDeptById(Long deptId);


    public List<SysDept> selectChildrenDeptById(Long deptId);


    public int selectNormalChildrenDeptById(Long deptId);


    public int hasChildByDeptId(Long deptId);


    public int checkDeptExistUser(Long deptId);


    public SysDept checkDeptNameUnique(@Param("deptName") String deptName, @Param("parentId") Long parentId);


    public int insertDept(SysDept dept);


    public int updateDept(SysDept dept);


    public void updateDeptStatusNormal(Long[] deptIds);


    public int updateDeptChildren(@Param("depts") List<SysDept> depts);


    public int deleteDeptById(Long deptId);


    public List<Long> selectDeptAndDescendantIds(Long deptId);


    public int deleteDeptByIds(@Param("deptIds") List<Long> deptIds);
}
