package com.chinasofti.huateng.system.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chinasofti.huateng.common.annotation.DataScope;
import com.chinasofti.huateng.common.constant.UserConstants;
import com.chinasofti.huateng.common.core.domain.TreeSelect;
import com.chinasofti.huateng.common.core.domain.entity.SysDept;
import com.chinasofti.huateng.common.core.domain.entity.SysRole;
import com.chinasofti.huateng.common.core.domain.entity.SysUser;
import com.chinasofti.huateng.common.core.text.Convert;
import com.chinasofti.huateng.common.exception.ServiceException;
import com.chinasofti.huateng.common.utils.SecurityUtils;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.spring.SpringUtils;
import com.chinasofti.huateng.system.mapper.SysDeptMapper;
import com.chinasofti.huateng.system.mapper.SysRoleMapper;
import com.chinasofti.huateng.system.mapper.SysUserMapper;
import com.chinasofti.huateng.system.service.ISysDeptService;


@Service
public class SysDeptServiceImpl implements ISysDeptService
{
    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysUserMapper userMapper;


    @Override
    @DataScope(deptAlias = "d")
    public List<SysDept> selectDeptList(SysDept dept)
    {
        return deptMapper.selectDeptList(dept);
    }


    @Override
    public List<TreeSelect> selectDeptTreeList(SysDept dept)
    {
        List<SysDept> depts = SpringUtils.getAopProxy(this).selectDeptList(dept);
        return buildDeptTreeSelect(depts);
    }


    @Override
    public List<SysDept> buildDeptTree(List<SysDept> depts)
    {
        List<SysDept> returnList = new ArrayList<SysDept>();
        Set<Long> deptIds = depts.stream().map(SysDept::getDeptId).collect(Collectors.toSet());
        Map<Long, List<SysDept>> childMap = groupByParentId(depts);
        for (SysDept dept : depts)
        {
            if (!deptIds.contains(dept.getParentId()))
            {
                recursionFn(childMap, dept);
                returnList.add(dept);
            }
        }
        if (returnList.isEmpty())
        {
            returnList = depts;
        }
        return returnList;
    }


    @Override
    public List<TreeSelect> buildDeptTreeSelect(List<SysDept> depts)
    {
        List<SysDept> deptTrees = buildDeptTree(depts);
        return deptTrees.stream().map(TreeSelect::new).collect(Collectors.toList());
    }


    @Override
    public List<Long> selectDeptListByRoleId(Long roleId)
    {
        SysRole role = roleMapper.selectRoleById(roleId);
        return deptMapper.selectDeptListByRoleId(roleId, role.isDeptCheckStrictly());
    }


    @Override
    public SysDept selectDeptById(Long deptId)
    {
        return deptMapper.selectDeptById(deptId);
    }


    @Override
    public int selectNormalChildrenDeptById(Long deptId)
    {
        return deptMapper.selectNormalChildrenDeptById(deptId);
    }


    @Override
    public boolean hasChildByDeptId(Long deptId)
    {
        int result = deptMapper.hasChildByDeptId(deptId);
        return result > 0;
    }


    @Override
    public boolean checkDeptExistUser(Long deptId)
    {
        int result = deptMapper.checkDeptExistUser(deptId);
        return result > 0;
    }


    @Override
    public boolean checkDeptNameUnique(SysDept dept)
    {
        Long deptId = StringUtils.isNull(dept.getDeptId()) ? -1L : dept.getDeptId();
        SysDept info = deptMapper.checkDeptNameUnique(dept.getDeptName(), dept.getParentId());
        if (StringUtils.isNotNull(info) && info.getDeptId().longValue() != deptId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }


    @Override
    public void checkDeptDataScope(Long deptId)
    {
        if (!SysUser.isAdmin(SecurityUtils.getUserId()) && StringUtils.isNotNull(deptId))
        {
            SysDept dept = new SysDept();
            dept.setDeptId(deptId);
            List<SysDept> depts = SpringUtils.getAopProxy(this).selectDeptList(dept);
            if (StringUtils.isEmpty(depts))
            {
                throw new ServiceException("没有权限访问部门数据！");
            }
        }
    }


    @Override
    public int insertDept(SysDept dept)
    {
        SysDept info = deptMapper.selectDeptById(dept.getParentId());
        if (!UserConstants.DEPT_NORMAL.equals(info.getStatus()))
        {
            throw new ServiceException("部门停用，不允许新增");
        }
        dept.setAncestors(info.getAncestors() + "," + dept.getParentId());
        return deptMapper.insertDept(dept);
    }


    @Override
    public int updateDept(SysDept dept)
    {
        SysDept newParentDept = deptMapper.selectDeptById(dept.getParentId());
        SysDept oldDept = deptMapper.selectDeptById(dept.getDeptId());
        if (StringUtils.isNotNull(newParentDept) && StringUtils.isNotNull(oldDept))
        {
            String newAncestors = newParentDept.getAncestors() + "," + newParentDept.getDeptId();
            String oldAncestors = oldDept.getAncestors();
            dept.setAncestors(newAncestors);
            updateDeptChildren(dept.getDeptId(), newAncestors, oldAncestors);
        }
        int result = deptMapper.updateDept(dept);
        if (UserConstants.DEPT_NORMAL.equals(dept.getStatus()) && StringUtils.isNotEmpty(dept.getAncestors())
                && !StringUtils.equals("0", dept.getAncestors()))
        {
            updateParentDeptStatusNormal(dept);
        }
        return result;
    }


    private void updateParentDeptStatusNormal(SysDept dept)
    {
        String ancestors = dept.getAncestors();
        Long[] deptIds = Convert.toLongArray(ancestors);
        deptMapper.updateDeptStatusNormal(deptIds);
    }


    public void updateDeptChildren(Long deptId, String newAncestors, String oldAncestors)
    {
        List<SysDept> children = deptMapper.selectChildrenDeptById(deptId);
        for (SysDept child : children)
        {
            child.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
        }
        if (children.size() > 0)
        {
            deptMapper.updateDeptChildren(children);
        }
    }


    @Override
    public int deleteDeptById(Long deptId)
    {
        return deptMapper.deleteDeptById(deptId);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDeptCascade(Long deptId)
    {
        List<Long> deptIds = deptMapper.selectDeptAndDescendantIds(deptId);
        if (deptIds == null || deptIds.isEmpty())
        {
            return 0;
        }
        userMapper.clearDeptIdByDeptIds(deptIds);
        return deptMapper.deleteDeptByIds(deptIds);
    }


    private Map<Long, List<SysDept>> groupByParentId(List<SysDept> depts)
    {
        Map<Long, List<SysDept>> childMap = new HashMap<>();
        for (SysDept dept : depts)
        {
            if (dept.getParentId() != null)
            {
                childMap.computeIfAbsent(dept.getParentId(), k -> new ArrayList<SysDept>()).add(dept);
            }
        }
        return childMap;
    }


    private void recursionFn(Map<Long, List<SysDept>> childMap, SysDept t)
    {
        List<SysDept> childList = getChildList(childMap, t);
        t.setChildren(childList);
        for (SysDept tChild : childList)
        {
            if (hasChild(childMap, tChild))
            {
                recursionFn(childMap, tChild);
            }
        }
    }


    private List<SysDept> getChildList(Map<Long, List<SysDept>> childMap, SysDept t)
    {
        List<SysDept> childList = childMap.get(t.getDeptId());
        return childList != null ? childList : new ArrayList<SysDept>();
    }


    private boolean hasChild(Map<Long, List<SysDept>> childMap, SysDept t)
    {
        return childMap.containsKey(t.getDeptId());
    }
}
