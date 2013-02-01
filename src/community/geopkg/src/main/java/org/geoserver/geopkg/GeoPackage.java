package org.geoserver.geopkg;

import static org.geoserver.geopkg.PreparedStatementBuilder.prepare;
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.IOUtils;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.data.spatialite.SpatiaLiteDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.LoggerAdapter;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Provides access to a GeoPackage database.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class GeoPackage {

    static final Logger LOGGER = Logging.getLogger("org.geotools.geopkg");

    static {
        //load the sqlite jdbc driver up front
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static final String GEOPACKAGE_CONTENTS = "geopackage_contents";

    /**
     * database file
     */
    final File file;

    /** 
     * connection pool
     */
    final BasicDataSource connPool;

    /** 
     * datastore for vector access, lazily created
     */
    volatile JDBCDataStore dataStore;

    /**
     * Creates a new empty GeoPackage, generating a new file.
     */
    public GeoPackage() throws IOException {
        this(File.createTempFile("geopkg", "db"));
    }

    /**
     * Creates a GeoPackage from an existing file.
     * <p>
     * This constructor assumes no credentials are required to connect to the database. 
     * </p>
     */
    public GeoPackage(File file) throws IOException {
        this(file, null, null);
    }

    /**
     * Creates a GeoPackage from an existing file specifying database credentials. 
     */
    public GeoPackage(File file, String user, String passwd) throws IOException {
        this.file = file;

        Properties props = new Properties();
        props.setProperty("enable_shared_cache", "true");
        props.setProperty("enable_load_extension", "true");
        props.setProperty("enable_spatialite", "true");

        connPool = new BasicDataSource();
        connPool.setConnectionProperties(
            "enable_shared_cache=true;enable_load_extension=true;enable_spatialite=true;");
        if (user != null) {
            connPool.setUsername(user);
        }
        if (passwd != null) {
            connPool.setPassword(passwd);
        }
        connPool.setUrl("jdbc:sqlite:" + file.getAbsolutePath());
    }

    /**
     * The underlying database file.
     */
    public File getFile() {
        return file;
    }

    /**
     * The database data source.
     */
    public DataSource getDataSource() {
        return connPool;
    }

    /**
     * Initializes the geopackage database.
     * <p>
     * This method creates all the necessary metadata tables.
     * </p> 
     */
    public void init() throws SQLException {
        runSQL("SELECT InitSpatialMetaData();");
        runScript("create_geopackage_contents.sql");
    }

    /**
     * Closes the geopackage database connection.
     * <p>
     * The application should always call this method when done with a geopackage to 
     * prevent connection leakage. 
     * </p>
     */
    public void close() {
        if (dataStore != null) {
            dataStore.dispose();
        }

        try {
            connPool.close();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error closing database connection", e);
        }
    }

    /**
     * Creates a new feature entry in the geopackage.
     * <p>
     * The resulting feature dataset will be empty. The 
     * {@link #writer(FeatureEntry, boolean, Transaction)} method returns a writer object that can 
     * be used to populate the dataset. 
     * </p>
     * @param entry Contains metadata about the feature entry.
     * @param schema The schema of the feature dataset.
     * 
     * @throws IOException Any errors occurring while creating the new feature entry.  
     */
    public void create(FeatureEntry entry, SimpleFeatureType schema) throws IOException {
        //clone entry so we can work on it
        FeatureEntry e = new FeatureEntry();
        e.init(entry);
        e.setTableName(schema.getTypeName());

        if (e.getGeometryColumn() != null) {
            //check it
            if (schema.getDescriptor(e.getGeometryColumn()) == null) {
                throw new IllegalArgumentException(
                    format("Geometry column %s does not exist in schema", e.getGeometryColumn()));
            }
        }
        else {
            e.setGeometryColumn(findGeometryColumn(schema));
        }

        if (e.getIdentifier() == null) {
            e.setIdentifier(schema.getTypeName());
        }
        if (e.getDescription() == null) {
            e.setDescription(e.getIdentifier());
        }

        //check for bounds
        if (e.getBounds() == null) {
            throw new IllegalArgumentException("Entry must have bounds");
        }

        //check for srid
        if (e.getSrid() == null) {
            try {
                e.setSrid(findSRID(schema));
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        if (e.getSrid() == null) {
            throw new IllegalArgumentException("Entry must have srid");
        }

        if (e.getCoordDimension() == null) {
            e.setCoordDimension(2);
        }

        //mark changed
        e.setLastChange(new Date());

        JDBCDataStore dataStore = dataStore();

        //create the feature table
        dataStore.createSchema(schema);

        String sql = format(
            "INSERT INTO %s VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);", GEOPACKAGE_CONTENTS);

        try {
            Connection cx = connPool.getConnection();
            try {
                PreparedStatement ps = prepare(cx, sql)
                    .set(e.getTableName())
                    .set(e.getDataType().value())
                    .set(e.getIdentifier())
                    .set(e.getDescription())
                    .set(e.getLastChange())
                    .set(e.getBounds().getMinX())
                    .set(e.getBounds().getMinY())
                    .set(e.getBounds().getMaxX())
                    .set(e.getBounds().getMaxY())
                    .set(e.getSrid())
                    .log(Level.FINE)
                    .statement();
                ps.execute();
                ps.close();
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException ex) {
            throw new IOException(ex);
        }

        //update the entry
        entry.init(e);
    }

    public void add(FeatureEntry entry, SimpleFeatureSource source) throws IOException {
        FeatureEntry e = new FeatureEntry();
        e.init(entry);

        if (e.getBounds() == null) {
            e.setBounds(source.getBounds());
        }

        create(e, source.getSchema());

        Transaction tx = new DefaultTransaction();
        SimpleFeatureWriter w = writer(e, true, tx);

        //copy over features
        //TODO: make this more robust, won't handle case issues going between datasources, etc...
        //TODO: for big datasets we need to break up the transaction
        SimpleFeatureIterator it = source.getFeatures().features();
        try {
            while(it.hasNext()) {
                SimpleFeature f = it.next(); 
                SimpleFeature g = w.next();
                for (PropertyDescriptor pd : source.getSchema().getDescriptors()) {
                    String name = pd.getName().getLocalPart();
                    g.setAttribute(name, f.getAttribute(name));
                }
                g.setDefaultGeometry(f.getDefaultGeometry());
                w.write();
            }
            tx.commit();
        }
        catch(Exception ex) {
            tx.rollback();
            throw new IOException(ex);
        }
        finally {
            it.close();
        }

        entry.init(e);
    }

    public SimpleFeatureWriter writer(FeatureEntry entry, boolean append, Transaction tx) 
            throws IOException {

        FeatureWriter w = append ?  dataStore.getFeatureWriterAppend(entry.getTableName(), tx) 
            : dataStore.getFeatureWriter(entry.getTableName(), tx);

        return Features.simple(w);
    }

    public SimpleFeatureReader reader(FeatureEntry entry, Filter filter, Transaction tx) throws IOException {
        Query q = new Query(entry.getTableName());
        q.setFilter(filter != null ? filter : Filter.INCLUDE);

        return Features.simple(dataStore.getFeatureReader(q, tx));
    }

    Integer findSRID(SimpleFeatureType schema) throws Exception {
        CoordinateReferenceSystem crs = schema.getCoordinateReferenceSystem();
        if (crs == null) {
            GeometryDescriptor gd = findGeometryDescriptor(schema);
            crs = gd.getCoordinateReferenceSystem();
        }

        return crs != null ? CRS.lookupEpsgCode(crs, true) : null;
    }

    String findGeometryColumn(SimpleFeatureType schema) {
        GeometryDescriptor gd = findGeometryDescriptor(schema);
        return gd != null ? gd.getLocalName() : null;
    }

    GeometryDescriptor findGeometryDescriptor(SimpleFeatureType schema) {
        GeometryDescriptor gd = schema.getGeometryDescriptor();
        if (gd == null) {
            for (PropertyDescriptor pd : schema.getDescriptors()) {
                if (pd instanceof GeometryDescriptor) {
                    return (GeometryDescriptor) pd;
                }
            }
        }
        return null;
    }

    //sql utility methods
    void runSQL(String sql) throws SQLException {
        Connection cx = connPool.getConnection();
        try {
            Statement st = cx.createStatement();
            try {
                st.execute(sql);
            }
            finally {
                st.close();
            }
        }
        finally {
            cx.close();
        }
    }

    void runScript(String filename) throws SQLException {
        String sql = null;

        try {
            InputStream in = getClass().getResourceAsStream(filename);
            try {
                sql = IOUtils.toString(in);
            }
            finally {
                in.close();
            }
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }

        runSQL(sql);
    }

    void close(Connection cx) {
        if (cx != null) {
            try {
                cx.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing connection", e);
            }
        }
    }

    void close(Statement  st) {
        if (st != null) {
            try {
                st.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing statement", e);
            }
        }
    }
    public List<Entry> contents() {
        List<Entry> contents = new ArrayList();
        try {
            Connection cx = connPool.getConnection();
            Statement st = cx.createStatement();
            try {
                ResultSet rs = st.executeQuery("SELECT * FROM geopackage_contents;");
                try {
                    while(rs.next()) {
                        
                    }
                }
                finally {
                    rs.close();
                }
            }
            finally {
                close(st);
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new RuntimeException(e);
        }
        return contents;
    }

    JDBCDataStore dataStore() throws IOException {
        if (dataStore == null) {
            synchronized (this) {
                if (dataStore == null) {
                    dataStore = createDataStore();
                }
            }
        }
        return dataStore;
    }

    JDBCDataStore createDataStore() throws IOException {
        Map params = new HashMap();
        params.put(SpatiaLiteDataStoreFactory.DATASOURCE.key, connPool);
        
        SpatiaLiteDataStoreFactory factory = new SpatiaLiteDataStoreFactory();
        return factory.createDataStore(params);
    }
}
