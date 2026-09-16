INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '进行中', '2', 'sys_common_status', NULL, 'warning', 'N', '0', 'admin', SYSDATE, '任务开始时先落该状态，收口后回写为成功或失败'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'sys_common_status' AND dict_value = '2'
);

COMMIT;
