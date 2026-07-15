package com.chinasofti.huateng.micro.datasource;

import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.filter.FilterEventAdapter;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Matcher;


public class SqlAudit extends FilterEventAdapter {
    public static Logger log = LoggerFactory.getLogger(SqlAudit.class);
    private ThreadLocal<String[]> sqlAndStartTimeData = new ThreadLocal<>();

    @Value("${spring.datasource.druid.filter.stat.slow-sql-millis:5000}")
    private long slowSqlMillis;

    @Value("${other.sql.rows.warn:1000}")
    private long rowsWarn;

    @Value("${other.sql.print.pre:false}")
    private boolean print_pre;

    @Value("${other.sql.trace:false}")
    private boolean trace;

    public long getRowsWarn() {
        return rowsWarn;
    }

    public void setRowsWarn(long rowsWarn) {
        this.rowsWarn = rowsWarn;
    }

    public long getSlowSqlMillis() {
        return slowSqlMillis;
    }

    public void setSlowSqlMillis(long slowSqlMillis) {
        this.slowSqlMillis = slowSqlMillis;
    }

    @Override
    public void init(DataSourceProxy dataSource) {
        super.init(dataSource);
    }

    @Override
    protected void statementExecuteBefore(StatementProxy statement, String sql) {
        String runSql = getRunSql(statement);
        if (print_pre) {
            log.info("Pre execution preview:{}", runSql);
        }
        String[] sqlAndStartTime = new String[]{runSql, String.valueOf(System.currentTimeMillis())};
        sqlAndStartTimeData.set(sqlAndStartTime);
        super.statementExecuteBefore(statement, sql);
    }

    @Autowired
    ObservationRegistry observationRegistry;

    private void audit(long fetchRowCount) {
        String[] datas = sqlAndStartTimeData.get();
        if (datas == null) {
            return;
        }
        String runSql = "";
        long startTime = 0L;
        if (datas != null && datas.length == 2) {
            runSql = datas[0];
            startTime = Long.parseLong(datas[1]);
        } else {
            startTime = System.currentTimeMillis();
        }

        try {
            String runSqlF = "" + runSql;
            long take = System.currentTimeMillis() - startTime;
            String cnt_str = String.valueOf(fetchRowCount);

            if (trace) {
                Observation observation = Observation.createNotStarted("custom.sql", this.observationRegistry);
                observation.lowCardinalityKeyValue("custom.slow.sql", getSlowSqlMillis() + "ms");
                observation.lowCardinalityKeyValue("custom.run.sql", runSqlF);
                observation.lowCardinalityKeyValue("custom.fetchRowCount", cnt_str);
                observation.lowCardinalityKeyValue("custom.take.times", take + "ms");
                observation.observe(() -> {
                    String log_msg = MessageFormat.format("fetchRowCount:{0} taketimes:{1}ms sql:{2}", cnt_str, take, runSqlF);
                    if (getSlowSqlMillis() <= take || Long.parseLong(cnt_str) >= getRowsWarn()) {
                        log.error(log_msg);
                    } else {
                        log.info(log_msg);
                    }
                });
            } else {
                String log_msg = MessageFormat.format("fetchRowCount:{0} taketimes:{1}ms sql:{2}", cnt_str, take, runSqlF);
                if (getSlowSqlMillis() <= take || Long.parseLong(cnt_str) >= getRowsWarn()) {
                    log.error(log_msg);
                } else {
                    log.info(log_msg);
                }
            }

        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        } finally {
            sqlAndStartTimeData.remove();
        }
    }

    @Override
    protected void statementExecuteAfter(StatementProxy statement, String sql, boolean result) {
        if (!result) {
            try {
                audit(statement.getUpdateCount());
            } catch (SQLException e) {
                log.error("{}", e.getMessage(), e);
            }
        }
        super.statementExecuteAfter(statement, sql, result);
    }

    @Override
    public void resultSet_close(FilterChain chain, ResultSetProxy resultSet) throws SQLException {
        audit(resultSet.getFetchRowCount());
        chain.resultSet_close(resultSet);
    }

    @Deprecated
    private long getRowsCnt(StatementProxy statement) {
        try {
            ResultSet resultSet0 = statement.getRawObject().getResultSet();
            String resultSetName = resultSet0.getClass().getName();
            if (resultSetName.equals("com.mysql.cj.protocol.a.result.NativeResultset")) {
                Field rows_field = resultSet0.getClass().getDeclaredField("updateCount");
                rows_field.setAccessible(true);
                long cnt = (Long) rows_field.get(resultSet0);
                cnt = cnt == -1 ? 1 : cnt;
                return cnt;
            } else if (resultSetName.equals("dm.jdbc.driver.DmdbResultSet")) {
                Field rows_field = resultSet0.getClass().getDeclaredField("totalRowCount");
                return (Long) rows_field.get(resultSet0);
            } else if (resultSetName.equals("com.huawei.gaussdb.jdbc.jdbc.PgResultSet")) {
                Field rows_field = resultSet0.getClass().getDeclaredField("rows");
                rows_field.setAccessible(true);
                List ob = (List) rows_field.get(resultSet0);
                return ob.size();
            }
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }
        return 0;
    }


    private String getRunSql(StatementProxy statement) {
        String runSql = statement.getRawObject().toString();

        boolean dm = false;
        if (runSql.contains("dm.jdbc.driver")) {
            dm = true;
        }

        runSql = statement.getLastExecuteSql();
        int parametersSize = statement.getParametersSize();
        int maxi = parametersSize > 128 ? 128 : parametersSize;
        for (int i = 0; i < maxi; i++) {
            Object v = statement.getParameter(i).getValue();
            if (v == null) {
                runSql = runSql.replaceFirst("\\?", "null");
            } else {
                runSql = runSql.replaceFirst("\\?", "'" + Matcher.quoteReplacement(v.toString()) + "'");
            }
        }

        if (runSql.contains("@")) {
            runSql = statement.getLastExecuteSql();
        }

        if (!dm) {
            runSql = runSql.substring(runSql.indexOf(": ") + 1);
        }
        runSql = runSql.replaceAll("\\r?\\n", " ");
        runSql = runSql.replaceAll(" {2,}", " ");
        return runSql;
    }


}
