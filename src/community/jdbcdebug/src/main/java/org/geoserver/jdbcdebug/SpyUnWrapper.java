package org.geoserver.jdbcdebug;

import java.sql.Connection;
import java.sql.Statement;

import net.sf.log4jdbc.ConnectionSpy;
import net.sf.log4jdbc.StatementSpy;

import org.geotools.data.jdbc.datasource.UnWrapper;

public class SpyUnWrapper implements UnWrapper {

    @Override
    public boolean canUnwrap(Connection conn) {
        return conn instanceof ConnectionSpy;
    }
    
    @Override
    public boolean canUnwrap(Statement st) {
        return st instanceof StatementSpy;
    }
    
    @Override
    public Connection unwrap(Connection conn) {
        if (canUnwrap(conn)) {
            return ((ConnectionSpy)conn).getRealConnection();
        }
        throw new IllegalArgumentException("Can't unwrap connection: " + conn);
    }
    
    @Override
    public Statement unwrap(Statement statement) {
        if (canUnwrap(statement)) {
            return ((StatementSpy)statement).getRealStatement();
        }
        throw new IllegalArgumentException("Can't unwrap statement: " + statement);
    }

}
