package com.chinasofti.huateng.web.controller.importer;

import java.util.List;


public interface IImportExportHandler<T>
{

    Class<T> getEntityClass();


    String getEntityLabel(String biz);


    List<T> getExportList(String biz, String queryJson) throws Exception;


    String handle(String biz, List<T> list, boolean updateSupport, String operator);
}
