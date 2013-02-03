package org.geoserver.geopkg;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Level;

/**
 * Builder class for creating prepared statements.
 * 
 * @author Justin Deoliveira, OpenGeo
 *
 */
public class PreparedStatementBuilder {

    Connection conn;
    PreparedStatement ps;
    int pos = 1;

    StringBuilder log = new StringBuilder();

    public static PreparedStatementBuilder prepare(Connection conn, String sql) throws SQLException {
        return new PreparedStatementBuilder(conn, sql); 
    }

    public PreparedStatementBuilder(Connection conn, String sql) throws SQLException {
        this.conn = conn;
        ps = conn.prepareStatement(sql);

        log.append(sql);
    }

    public PreparedStatementBuilder set(Long l) throws SQLException {
        log(l);
        ps.setLong(pos++, l);
        return this;
    }

    public PreparedStatementBuilder set(Integer i) throws SQLException {
        log(i);
        ps.setInt(pos++, i);
        return this;
    }

    public PreparedStatementBuilder set(Double d) throws SQLException {
        log(d);
        ps.setDouble(pos++, d);
        return this;
    }

    public PreparedStatementBuilder set(String s) throws SQLException {
        log(s);
        ps.setString(pos++, s);
        return this;
    }

    public PreparedStatementBuilder set(Date d) throws SQLException {
        log(d);
        ps.setDate(pos++, d != null ? new java.sql.Date(d.getTime()) : null);
        return this;
    }

    public PreparedStatementBuilder set(byte[] b) throws SQLException {
        log(b);
        ps.setBytes(pos++, b);
        //ps.setBinaryStream(pos++, is);
        return this;
    }

    public PreparedStatementBuilder log(Level l) {
        GeoPackage.LOGGER.log(l, log.toString());
        return this;
    }

    public PreparedStatement statement() {
        return ps;
    }

    void log(Object v) {
        log.append("\n").append(" ").append(pos).append(" = ").append(v);
    }
}
