package org.geoserver.jdbcdebug;

import java.io.IOException;
import java.util.Map;

import javax.sql.DataSource;

import org.geotools.jdbc.JDBCDataSourceFactory;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.jdbc.SQLDialect;

public class JDBCDebugDataSourceFactory extends JDBCDataSourceFactory {

    JDBCDataSourceFactory delegate;

    public JDBCDebugDataSourceFactory(JDBCDataSourceFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public DataSource createDataSource(Map params, String jdbcURL, String driverClassName, 
        JDBCDataStoreFactory dataStoreFactory, SQLDialect dialect) throws IOException {
        
        DataSource dataSource = 
            delegate.createDataSource(params, jdbcURL, driverClassName, dataStoreFactory, dialect);
        return new JDBCDebugDataSource(dataSource);
    }
}
