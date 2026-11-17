package com.chinasofti.huateng.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.chinasofti.huateng.common.core.domain.entity.SysDictData;


public interface SysDictDataMapper
{

    public List<SysDictData> selectDictDataList(SysDictData dictData);


    public List<SysDictData> selectDictDataByType(String dictType);


    public String selectDictLabel(@Param("dictType") String dictType, @Param("dictValue") String dictValue);


    public SysDictData selectDictDataById(Long dictCode);


    public int countDictDataByType(String dictType);


    public int deleteDictDataById(Long dictCode);


    public int deleteDictDataByIds(Long[] dictCodes);


    public int insertDictData(SysDictData dictData);


    public int updateDictData(SysDictData dictData);


    public int updateDictDataType(@Param("oldDictType") String oldDictType, @Param("newDictType") String newDictType);
}
