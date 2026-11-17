package com.chinasofti.huateng.acc.es.server.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;
import java.net.SocketException;


@Slf4j
public class FTP {


    public FTPClient getFTPClient(String ftpHost, int ftpPort,
                                  String ftpUserName, String ftpPassword) {

        FTPClient ftp = null;
        try {
            ftp = new FTPClient();
            ftp.connect(ftpHost, ftpPort);
            ftp.login(ftpUserName, ftpPassword);
            ftp.setConnectTimeout(50000);
            ftp.setControlEncoding("UTF-8");
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                log.info("未连接到FTP，用户名或密码错误");
                ftp.disconnect();
            } else {
                log.info("FTP连接成功");
            }

        } catch (SocketException e) {
            e.printStackTrace();
            log.info("FTP的IP地址可能错误，请正确配置");
        } catch (IOException e) {
            e.printStackTrace();
            log.info("FTP的端口错误,请正确配置");
        }
        return ftp;
    }


    public boolean closeFTP(FTPClient ftp) {

        try {
            ftp.logout();
        } catch (Exception e) {
            log.error("FTP关闭失败");
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ioe) {
                    log.error("FTP关闭失败");
                }
            }
        }

        return false;

    }



    public boolean downLoadFTP(FTPClient ftp, String filePath, String fileName,
                               String downPath) {
        boolean flag = false;

        try {
            ftp.changeWorkingDirectory(filePath);
            ftp.enterLocalPassiveMode();
            FTPFile[] files = ftp.listFiles();
            for (FTPFile file : files) {
                if (file.getName().equals(fileName)) {
                    File downFile = new File(downPath + File.separator
                            + file.getName());
                    OutputStream out = new FileOutputStream(downFile);
                    flag = ftp.retrieveFile(new String(file.getName().getBytes("UTF-8"), "ISO-8859-1"), out);
                    out.flush();
                    out.close();
                    if (flag) {
                        log.info("下载成功");
                    } else {
                        log.error("下载失败");
                    }
                }
            }

        } catch (Exception e) {
            log.error("下载失败");
        }

        return flag;
    }


    public boolean uploadFile(FTPClient ftp, String filePath, String ftpPath) {
        boolean flag = false;
        InputStream in = null;
        try {
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
            if (!ftp.changeWorkingDirectory(ftpPath)) {
                ftp.makeDirectory(ftpPath);
            }
            ftp.changeWorkingDirectory(ftpPath);

            File file = new File(filePath);
            in = new FileInputStream(file);
            String tempName = ftpPath+ file.getName();
            flag = ftp.storeFile(new String(tempName.getBytes("UTF-8"), "ISO-8859-1"), in);
            if (flag) {
                log.info("上传成功");
            } else {
                log.error("上传失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("上传失败");
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return flag;
    }


    public boolean copyFile(FTPClient ftp, String olePath, String newPath, String fileName) {
        boolean flag = false;

        try {
            ftp.changeWorkingDirectory(olePath);
            ftp.enterLocalPassiveMode();
            FTPFile[] files = ftp.listFiles();
            ByteArrayInputStream in = null;
            ByteArrayOutputStream out = null;
            for (FTPFile file : files) {
                if (file.getName().equals(fileName)) {

                    out = new ByteArrayOutputStream();
                    ftp.retrieveFile(new String(file.getName().getBytes("UTF-8"), "ISO-8859-1"), out);
                    in = new ByteArrayInputStream(out.toByteArray());
                    ftp.makeDirectory(newPath);
                    ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
                    flag = ftp.storeFile(newPath+ (new String(file.getName().getBytes("UTF-8"), "ISO-8859-1")), in);
                    out.flush();
                    out.close();
                    in.close();
                    if (flag) {
                        log.info("转存成功");
                    } else {
                        log.error("复制失败");
                    }


                }
            }
        } catch (Exception e) {
            log.error("复制失败");
        }
        return flag;
    }


    public boolean moveFile(FTPClient ftp, String oldPath, String newPath) {
        boolean flag = false;

        try {
            ftp.changeWorkingDirectory(oldPath);
            ftp.enterLocalPassiveMode();
            FTPFile[] files = ftp.listFiles();
            if (!ftp.changeWorkingDirectory(newPath)) {
                ftp.makeDirectory(newPath);
            }
            ftp.changeWorkingDirectory(oldPath);
            for (FTPFile file : files) {

                flag = ftp.rename(new String(file.getName().getBytes("UTF-8"), "UTF-8"), newPath+ new String(file.getName().getBytes("UTF-8"), "ISO-8859-1"));
                if (flag) {
                    log.info(file.getName() + "移动成功");
                } else {
                    log.error(file.getName() + "移动失败");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("移动文件失败");
        }
        return flag;
    }


    public boolean deleteByFolder(FTPClient ftp, String FtpFolder) {
        boolean flag = false;
        try {
            ftp.changeWorkingDirectory(new String(FtpFolder.getBytes("UTF-8"), "ISO-8859-1"));
            ftp.enterLocalPassiveMode();
            FTPFile[] files = ftp.listFiles();
            for (FTPFile file : files) {
                if (file.isFile()) {
                    ftp.deleteFile(new String(file.getName().getBytes("UTF-8"), "ISO-8859-1"));
                }
                if (file.isDirectory()) {
                    String childPath = FtpFolder+ file.getName();
                    deleteByFolder(ftp, childPath);
                }
            }
            flag = ftp.removeDirectory(new String(FtpFolder.getBytes("UTF-8"), "ISO-8859-1"));
            if (flag) {
                log.info(FtpFolder + "文件夹删除成功");
            } else {
                log.error(FtpFolder + "文件夹删除成功");
            }

        } catch (Exception e) {
            e.printStackTrace();
            log.error("删除失败");
        }
        return flag;

    }


    public boolean readFileByFolder(FTPClient ftp, String folderPath) {
        boolean flag = false;
        try {
            ftp.changeWorkingDirectory(new String(folderPath.getBytes("UTF-8"), "ISO-8859-1"));
            ftp.enterLocalPassiveMode();
            FTPFile files[] = ftp.listFiles();
            InputStream in = null;
            BufferedReader reader = null;
            for (FTPFile file : files) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    if (fileName.endsWith(".txt")) {
                        in = ftp.retrieveFileStream(new String(file.getName().getBytes("UTF-8"), "ISO-8859-1"));
                        reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                        String temp;
                        StringBuffer buffer = new StringBuffer();
                        while ((temp = reader.readLine()) != null) {
                            buffer.append(temp);
                        }
                        if (reader != null) {
                            reader.close();
                        }
                        if (in != null) {
                            in.close();
                        }
                        ftp.completePendingCommand();
                        System.out.println(buffer.toString());
                    }
                }
                if (file.isDirectory()) {
                    String path = folderPath+ file.getName();
                    readFileByFolder(ftp, path);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
            log.error("文件解析失败");
        }

        return flag;

    }

}
