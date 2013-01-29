package org.geoserver.mbtiles;

import java.io.Closeable;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;

public class TileIterator implements Iterator<Tile>, Closeable {

    ResultSet rs;
    Boolean next = null;

    TileIterator(ResultSet rs) {
        this.rs = rs;
    }

    @Override
    public boolean hasNext() {
        if (next == null) {
            try {
                next = rs.next();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return next;
    }

    @Override
    public Tile next() {
        try {
            return new Tile(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getBytes(4));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            next = null;
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        try {
            Statement st = rs.getStatement();
            rs.close();
            st.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }


}
