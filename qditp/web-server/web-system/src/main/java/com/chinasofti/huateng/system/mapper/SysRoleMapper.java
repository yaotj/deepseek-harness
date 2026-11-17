package com.chinasofti.huateng.system.mapper;

import java.util.List;
import com.chinasofti.huateng.common.core.domain.entity.SysRole;


public interface SysRoleMapper
{

    public List<SysRole> selectRoleList(SysRole role);


    public List<SysRole> selectRolePermissionByUserId(Long userId);


    public List<SysRole> selectRoleAll();


    public List<Long> selectRoleListByUserId(Long userId);


    public SysRole selectRoleById(Long roleId);


    public List<SysRole> selectRolesByUserName(String userName);


    public SysRole checkRoleNameUnique(String roleName);


    public SysRole checkRoleKeyUnique(String roleKey);


    public int updateRole(SysRole role);


    public int insertRole(SysRole role);


    public int deleteRoleById(Long roleId);


    public int deleteRoleByIds(Long[] roleIds);
}
