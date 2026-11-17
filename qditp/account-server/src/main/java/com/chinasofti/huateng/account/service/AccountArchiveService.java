package com.chinasofti.huateng.account.service;


public interface AccountArchiveService {

    void archiveIfLastChannelRemoved(String thirdUserId);


    void tryArchiveAfterCancel(String thirdUserId);
}
