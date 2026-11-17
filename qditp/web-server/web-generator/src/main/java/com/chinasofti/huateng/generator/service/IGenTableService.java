package com.chinasofti.huateng.generator.service;

import java.util.List;
import java.util.Map;
import com.chinasofti.huateng.generator.domain.GenTable;


public interface IGenTableService
{

    public List<GenTable> selectGenTableList(GenTable genTable);


    public List<GenTable> selectDbTableList(GenTable genTable);


    public List<GenTable> selectDbTableListByNames(String[] tableNames);


    public List<GenTable> selectGenTableAll();


    public GenTable selectGenTableById(Long id);


    public void updateGenTable(GenTable genTable);


    public void deleteGenTableByIds(Long[] tableIds);


    public boolean createTable(String sql);


    public void importGenTable(List<GenTable> tableList, String operName);


    public Map<String, String> previewCode(Long tableId);


    public byte[] downloadCode(String tableName);


    public void generatorCode(String tableName);


    public void synchDb(String tableName);


    public byte[] downloadCode(String[] tableNames);


    public void validateEdit(GenTable genTable);
}
