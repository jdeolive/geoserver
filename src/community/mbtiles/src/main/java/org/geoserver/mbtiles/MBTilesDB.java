package org.geoserver.mbtiles;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

//TODO: cache prepared statements
public class MBTilesDB {

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    File file;
    Connection conn;

    public MBTilesDB() throws IOException, SQLException {
        this(File.createTempFile("mbtiles", "db"));
    }
    
    public MBTilesDB(File file) throws SQLException {
        this.file = file;
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    public File getFile() {
        return file;
    }

    public Connection getConnection() {
        return conn;
    }

    public void init() throws SQLException {
        Statement st = conn.createStatement();
        try {
            st.execute("CREATE TABLE metadata (name text, value text);");
            st.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, " +
                "tile_row integer, tile_data blob);");
        }
        finally {
            st.close();
        }
    }

    public void initForAndroid() throws SQLException {
        Statement st = conn.createStatement();
        try {
            st.execute("CREATE TABLE android_metadata (locale TEXT DEFAULT 'en_US');");
            st.execute("INSERT INTO android_metadata VALUES ('en_US');");
        }
        finally {
            st.close();
        }
        
    }
    
    public void addMetadata(Map<String,String> kvp) throws SQLException {
        PreparedStatement st = conn.prepareStatement("INSERT INTO metadata VALUES (?,?);");
        try {
            for (Map.Entry<String, String> kv : kvp.entrySet()) {
                st.setString(1, kv.getKey());
                st.setString(2, kv.getValue());
                st.execute();
            }
        }
        finally {
            st.close();
        }
    }

    public void addTile(long z, long x, long y, byte[] bytes) throws SQLException {
        PreparedStatement st = conn.prepareStatement("INSERT INTO tiles VALUES (?,?,?,?);");
        try {
            st.setLong(1, z);
            st.setLong(2, x);
            st.setLong(3, y);
            st.setBytes(4, bytes);
            st.execute();
        }
        finally {
            st.close();
        }
    }

    public TileIterator tiles() throws SQLException {
        Statement st = conn.createStatement();
        return new TileIterator(st.executeQuery("SELECT * FROM tiles;"));
    }

    public byte[] readTileData(long z, long x, long y) throws SQLException {
        PreparedStatement st = conn.prepareStatement(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? and tile_row = ?;");
        try {
            st.setLong(1, z);
            st.setLong(2, x);
            st.setLong(3, y);

            ResultSet rs = st.executeQuery();
            try {
                if (rs.next()) {
                    return rs.getBytes(1);
                }
            }
            finally {
                rs.close();
            }
        }
        finally {
            st.close();
        }

        return null;
    }

    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            MBTilesOutputFormat.LOGGER.log(Level.WARNING, "Error ocurred closing database connection", e);
        }
    }
}