package com.chinasofti.huateng.system.service;

import java.util.List;
import com.chinasofti.huateng.system.domain.SysNotice;


public interface ISysNoticeService
{

    public SysNotice selectNoticeById(Long noticeId);


    public List<SysNotice> selectNoticeList(SysNotice notice);


    public int insertNotice(SysNotice notice);


    public int updateNotice(SysNotice notice);


    public int deleteNoticeById(Long noticeId);
    

    public int deleteNoticeByIds(Long[] noticeIds);
}
