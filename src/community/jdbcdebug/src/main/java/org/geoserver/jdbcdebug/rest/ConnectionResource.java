package org.geoserver.jdbcdebug.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.jdbcdebug.JDBCDebugDataSource;
import org.geoserver.jdbcdebug.JDBCDebugDataSource.ConnectionInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.rest.AbstractResource;
import org.geoserver.rest.format.DataFormat;
import org.geoserver.rest.format.MediaTypes;
import org.geoserver.rest.format.StreamDataFormat;
import org.geotools.data.DataAccess;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.util.logging.Logging;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;

public class ConnectionResource extends AbstractResource {

    static Logger LOGGER = Logging.getLogger(ConnectionResource.class);
    static {
        MediaTypes.registerExtension( "txt", MediaType.TEXT_PLAIN );
    }

    @Override 
    public final void handleGet() {
        DataFormat format = getFormatGet();
        try {
            getResponse().setEntity(format.toRepresentation(null));
        } 
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    protected List<DataFormat> createSupportedFormats(Request request, Response response) {
        return (List) Arrays.asList(new TextFormat());
    }

    static class TextFormat extends StreamDataFormat {

        protected TextFormat() {
            super(MediaType.TEXT_PLAIN);
        }

        @Override
        protected Object read(InputStream in) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void write(Object object, OutputStream out) throws IOException {
            PrintWriter pw = new PrintWriter(out);

            Catalog cat = (Catalog) GeoServerExtensions.bean("catalog");
            ResourcePool pool = cat.getResourcePool();

            for (DataAccess da : pool.getDataStoreCache().values()) {
                if (!(da instanceof JDBCDataStore)) {
                    continue;
                }

                JDBCDataStore jdbc = (JDBCDataStore) da;
                DataSource ds = jdbc.getDataSource();

                BasicDataSource basicds = unwrap(ds, BasicDataSource.class);
                if (basicds != null) {
                    pw.println("Source: " + basicds.getUrl());
                    pw.println("Idle: " + basicds.getNumIdle());
                    pw.println("Active: " + basicds.getNumActive());
                    
                    JDBCDebugDataSource debugds = unwrap(ds, JDBCDebugDataSource.class);
                    if (debugds != null) {
                        for (ConnectionInfo cxInfo : debugds.activeConnections()) {
                            pw.println();
                            pw.println(cxInfo.getTimestamp());
                            cxInfo.getTrace().printStackTrace(pw);
                        }
                    }
                    pw.println();
                }

            }

            pw.flush();
        }
    
        <T extends DataSource> T unwrap(DataSource dataSource, Class<T> clazz) {
            while(dataSource != null) {
                if (clazz.isInstance(dataSource)) {
                    return (T) dataSource;
                }

                DataSource unwrapped;
                try {
                    unwrapped = dataSource.unwrap(DataSource.class);
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Error unwrapping datasource", e);
                    unwrapped = null;
                } 
                if (unwrapped != dataSource) {
                    dataSource = unwrapped;
                }
            }
            return null;
        }
    }
}
