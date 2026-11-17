package com.chinasofti.huateng.system.service;

import java.util.List;
import com.chinasofti.huateng.common.core.domain.entity.SysDictData;


public interface ISysDictDataService
{

    public List<SysDictData> selectDictDataList(SysDictData dictData);


    public String selectDictLabel(String dictType, String dictValue);


    public SysDictData selectDictDataById(Long dictCode);


    public void deleteDictDataByIds(Long[] dictCodes);


    public int insertDictData(SysDictData dictData);


    public int updateDictData(SysDictData dictData);
}
