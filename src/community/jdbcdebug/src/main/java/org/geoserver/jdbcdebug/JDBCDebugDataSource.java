package org.geoserver.jdbcdebug;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import net.sf.log4jdbc.ConnectionSpy;

import org.apache.commons.dbcp.DelegatingConnection;
import org.geotools.data.jdbc.datasource.AbstractManageableDataSource;
import org.geotools.data.jdbc.datasource.ManageableDataSource;

public class JDBCDebugDataSource extends AbstractManageableDataSource {

    ConcurrentHashMap<Integer, ConnectionInfo> activeConnections;

    public JDBCDebugDataSource(DataSource wrapped) {
        super(wrapped);
        activeConnections = new ConcurrentHashMap<Integer, ConnectionInfo>();
    }

    @Override
    public boolean isWrapperFor(Class c) throws SQLException {
        if (DataSource.class.isAssignableFrom(c)) {
            return true;
        }
        return false;
    }

    @Override
    public Object unwrap(Class c) throws SQLException {
        if (isWrapperFor(c)) {
            return wrapped;
        }
        return null;
    }

    public List<ConnectionInfo> activeConnections() {
        return new ArrayList(activeConnections.values());
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection cx = super.getConnection();
        if (cx != null) {
            return newConnection(cx);
        }
        return null;
    }

    Connection newConnection(Connection cx) {
        DebugConnection debugCx = new DebugConnection(cx);
        activeConnections.put(debugCx.getId(), new ConnectionInfo(debugCx));
        return debugCx;
    }

    @Override
    public void close() throws SQLException {
        if (activeConnections != null) {
            activeConnections.clear();
        }
        activeConnections = null;

        if (wrapped != null && wrapped instanceof ManageableDataSource) {
            ((ManageableDataSource)wrapped).close();
        }
        wrapped = null;
    }

    public static class ConnectionInfo {
        Date timestamp;
        Exception trace;

        public ConnectionInfo(DebugConnection cx) {
            this.trace = new Exception();
            this.timestamp = new Date();
        }

        public Exception getTrace() {
            return trace;
        }

        public Date getTimestamp() {
            return timestamp;
        }
    }

    class DebugConnection extends ConnectionSpy {

        public DebugConnection(Connection c) {
            super(c);
        }

        public Integer getId() {
            return getConnectionNumber();
        }

        @Override
        public void close() throws SQLException {
            super.close();
            activeConnections.remove(getId());
        }
    }
}

