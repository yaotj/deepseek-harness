
UPDATE sys_job
   SET job_id = 240,
       job_name = '卡池数据导入',
       remark = '每5分钟触发card-pool-server POST /internal/card-pools/maintenance：回收超时预占+按阈值向ACC申请批次补货+推进批次(FTP下载卡号文件入库)；接口只受理，结果看服务端日志与/card-pools/summary。名称对甲方口径叫卡池数据导入，实际还含预占回收，NEVER 据名字以为只做导入'
 WHERE job_id = 107;

COMMIT;
