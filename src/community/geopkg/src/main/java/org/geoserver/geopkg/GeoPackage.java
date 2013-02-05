package org.geoserver.geopkg;

import static org.geoserver.geopkg.PreparedStatementBuilder.prepare;
import static java.lang.String.format;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
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

import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geoserver.geopkg.Entry.DataType;
import org.geoserver.geopkg.RasterEntry.Rectification;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
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
import org.geotools.factory.Hints;
import org.geotools.gce.image.WorldImageFormat;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.LoggerAdapter;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.grid.Format;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.coverage.grid.GridCoverageWriter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Geometry;

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

    static final String GEOMETRY_COLUMNS = "geometry_columns";
    
    static final String RASTER_COLUMNS = "raster_columns";
    
    static final String TILE_TABLE_METADATA = "tile_table_metadata";
    
    static final String TILE_MATRIX_METADATA = "tile_matrix_metadata";

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
    public void init() throws IOException {
        try {
            runSQL("SELECT InitSpatialMetaData();");
            runScript("geopackage_contents.sql");
            runScript("raster_columns.sql");
            runScript("tile_table_metadata.sql");
            runScript("tile_matrix_metadata.sql");
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
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
     * Returns list of contents of the geopackage. 
     */
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

    //
    // feature methods
    //

    /**
     * Lists all the feature entries in the geopackage. 
     *
     */
    public List<FeatureEntry> features() throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, b.f_geometry_column, b.type, b.coord_dimension" +
                 " FROM %s a, %s b" + 
                " WHERE a.table_name = b.f_table_name" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, GEOMETRY_COLUMNS);
                PreparedStatement ps = cx.prepareStatement(sql);
                ps.setString(1, DataType.Feature.value());

                ResultSet rs = ps.executeQuery();

                List<FeatureEntry> entries = new ArrayList();
                while(rs.next()) {
                    entries.add(createFeatureEntry(rs));
                }
                rs.close();
                ps.close();

                return entries;
            }
            finally {
                cx.close();
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Looks up a feature entry by name.
     * 
     * @param name THe name of the feature entry.
     * @return The entry, or <code>null</code> if no such entry exists.
     */
    public FeatureEntry feature(String name) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, b.f_geometry_column, b.type, b.coord_dimension" +
                 " FROM %s a, %s b" + 
                " WHERE a.table_name = ?" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, GEOMETRY_COLUMNS);
                PreparedStatement ps = cx.prepareStatement(sql);
                ps.setString(1, name);
                ps.setString(2, DataType.Feature.value());

                ResultSet rs = ps.executeQuery();

                try {
                    if(rs.next()) {
                        return createFeatureEntry(rs);
                    }
                }
                finally {
                    rs.close();
                    ps.close();
                }
            }
            finally {
                cx.close();
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
        return null;
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

        if (e.getGeometryType() == null) {
            e.setGeometryType(findGeometryType(schema));
        }
        //mark changed
        e.setLastChange(new Date());

        JDBCDataStore dataStore = dataStore();

        //create the feature table
        dataStore.createSchema(schema);

        //update the metadata tables
        addGeoPackageContentsEntry(e);

        //update the entry
        entry.init(e);
    }

    /**
     * Adds a new feature dataset to the geopackage.
     *
     * @param entry Contains metadata about the feature entry.
     * @param source The dataset to add to the geopackage.
     * @param filter Filter specifying what subset of feature dataset to include, may be 
     *  <code>null</code> to specify no filter. 
     * 
     * @throws IOException Any errors occurring while adding the new feature dataset.  
     */
    public void add(FeatureEntry entry, SimpleFeatureSource source, Filter filter) throws IOException {
        FeatureEntry e = new FeatureEntry();
        e.init(entry);

        if (e.getBounds() == null) {
            e.setBounds(source.getBounds());
        }

        create(e, source.getSchema());

        Transaction tx = new DefaultTransaction();
        SimpleFeatureWriter w = writer(e, true, null, tx);

        //copy over features
        //TODO: make this more robust, won't handle case issues going between datasources, etc...
        //TODO: for big datasets we need to break up the transaction
        if (filter == null) {
            filter = Filter.INCLUDE;
        }

        SimpleFeatureIterator it = source.getFeatures(filter).features();
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
            tx.close();
        }

        entry.init(e);
    }

    /**
     * Returns a writer used to modify or add to the contents of a feature dataset.
     *  
     * @param entry The feature entry. 
     * @param append Flag controlling whether to modify existing contents, or append to the dataset. 
     * @param filter Filter determining what subset of dataset to modify, only relevant when 
     *   <tt>append</tt> set to false. May be <code>null</code> to specify no filter.
     * @param tx Transaction object, may be <code>null</code> to specify auto commit transaction.
     *
     */
    public SimpleFeatureWriter writer(FeatureEntry entry, boolean append, Filter filter, 
        Transaction tx) throws IOException {

        FeatureWriter w = append ?  dataStore.getFeatureWriterAppend(entry.getTableName(), tx) 
            : dataStore.getFeatureWriter(entry.getTableName(), filter, tx);

        return Features.simple(w);
    }

    /**
     * Returns a reader for the contents of a feature dataset.
     * 
     * @param entry The feature entry.
     * @param filter Filter Filter determining what subset of dataset to return. May be 
     *   <code>null</code> to specify no filter.
     * @param tx Transaction object, may be <code>null</code> to specify auto commit transaction.
     */
    public SimpleFeatureReader reader(FeatureEntry entry, Filter filter, Transaction tx) throws IOException {
        Query q = new Query(entry.getTableName());
        q.setFilter(filter != null ? filter : Filter.INCLUDE);

        return Features.simple(dataStore().getFeatureReader(q, tx));
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

    Geometries findGeometryType(SimpleFeatureType schema) {
        GeometryDescriptor gd = findGeometryDescriptor(schema);
        return gd != null ? 
            Geometries.getForBinding((Class<? extends Geometry>) gd.getType().getBinding()) : null;
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
        return gd;
    }

    FeatureEntry createFeatureEntry(ResultSet rs) throws SQLException, IOException {
        FeatureEntry e = new FeatureEntry();
        initEntry(e, rs);

        e.setGeometryColumn(rs.getString("f_geometry_column"));
        e.setGeometryType(Geometries.getForName(rs.getString("type")));
        e.setCoordDimension(rs.getInt("coord_dimension"));

        return e;
    }

    void addGeoPackageContentsEntry(Entry e) throws IOException {
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
    }

    //
    // raster methods
    //

    /**
     * Lists all the raster entries in the geopackage. 
     */
    public List<RasterEntry> rasters() throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, b.r_raster_column, b.compr_qual_factor, b.georectification" +
                 " FROM %s a, %s b" + 
                " WHERE a.table_name = b.r_table_name" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, RASTER_COLUMNS);
                PreparedStatement ps = cx.prepareStatement(sql);
                ps.setString(1, DataType.Raster.value());

                ResultSet rs = ps.executeQuery();

                List<RasterEntry> entries = new ArrayList();
                while(rs.next()) {
                    entries.add(createRasterEntry(rs));
                }
                rs.close();
                ps.close();

                return entries;
            }
            finally {
                cx.close();
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Looks up a raster entry by name.
     * 
     * @param name THe name of the raster entry.
     * @return The entry, or <code>null</code> if no such entry exists.
     */
    public RasterEntry raster(String name) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, b.r_raster_column, b.compr_qual_factor, b.georectification" +
                 " FROM %s a, %s b" + 
                " WHERE a.table_name = ?" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, RASTER_COLUMNS);
                PreparedStatement ps = cx.prepareStatement(sql);
                ps.setString(1, name);
                ps.setString(2, DataType.Raster.value());

                ResultSet rs = ps.executeQuery();
                try {
                    if (rs.next()) {
                        return createRasterEntry(rs);
                    }
                }
                finally {
                    rs.close();
                    ps.close();
                }
            }
            finally {
                cx.close();
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }

        return null;
    }

    /**
     * Adds a new raster dataset to the geopackage.
     *
     * @param entry Contains metadata about the raster entry.
     * @param raster The raster dataset.
     * @param format The format in which to store the raster in the database.
     * 
     * @throws IOException Any errors occurring while adding the new feature dataset.
     */
    public void add(RasterEntry entry, GridCoverage2D raster, AbstractGridFormat format) 
        throws IOException {

        RasterEntry e = new RasterEntry();
        e.init(entry);

        if (e.getTableName() == null) {
            if (raster.getName() == null) {
                throw new IllegalArgumentException("No table name specified for raster");
            }
            e.setTableName(raster.getName().toString());
        }

        if (e.getRasterColumn() == null) {
            e.setRasterColumn("raster");
        }

        if (e.getSrid() == null) {
            try {
                e.setSrid(findSRID(raster));
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        if (e.getSrid() == null) {
            throw new IllegalArgumentException("Entry must have srid");
        }

        if (e.getBounds() == null) {
            e.setBounds(findBounds(raster));
        }
        if (e.getBounds() == null) {
            throw new IllegalArgumentException("Entry must have bounds");
        }

        if (e.getIdentifier() == null) {
            e.setIdentifier(raster.getName().toString());
        }
        if (e.getDescription() == null) {
            e.setDescription(e.getIdentifier());
        }

        //TODO: comperession quality and georectification
        e.setCompressionQualityFactor(1.0);
        e.setGeoRectification(Rectification.Geo);

        e.setLastChange(new Date());

        //write out raster to temp file
        File tmpFile = File.createTempFile(e.getTableName(), "raster");

        GridCoverageWriter writer = format.getWriter(tmpFile);
        writer.write(raster, null);
        writer.dispose();

        //create the raster table
        try {
            Connection cx = connPool.getConnection();
            try {
                Statement st = cx.createStatement();
                try {
                    String sql = format("CREATE TABLE %s (id INTEGER PRIMARY KEY AUTOINCREMENT, %s BLOB NOT NULL)", 
                        e.getTableName(), e.getRasterColumn());
                    LOGGER.fine(sql);

                    st.execute(sql);
                }
                finally {
                    close(st);
                }

                //TODO: ideally we would stream this in
                BufferedInputStream bin = new BufferedInputStream(new FileInputStream(tmpFile));
                byte[] blob = IOUtils.toByteArray(bin);

                try {
                    PreparedStatement ps = prepare(cx, 
                        format("INSERT INTO %s (%s) VALUES (?)",e.getTableName(), e.getRasterColumn()))
                    .set(blob).log(Level.FINE).statement();
                    ps.execute();
                    ps.close();
                }
                finally {
                    bin.close();
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException ex) {
            throw new IOException(ex);
        }

        tmpFile.delete();

        addGeoPackageContentsEntry(e);
        addRasterColumnsEntry(e);

        entry.init(e);
    }

    /**
     * Returns a reader for the contents of a raster dataset.
     * 
     * @param entry The raster entry.
     * @param format Format of the raster dataset.
     */
    public GridCoverageReader reader(RasterEntry entry, AbstractGridFormat format) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                Statement st = cx.createStatement();
                try {
                    ResultSet rs = st.executeQuery(
                        format("SELECT %s FROM %s;", entry.getRasterColumn(), entry.getTableName()));
                    if (rs.next()) {
                        byte[] blob = rs.getBytes(1);
                        Hints hints = new Hints();
                        if (format instanceof WorldImageFormat) {
                            hints.put(WorldImageFormat.ORIGINAL_ENVELOPE, toGeneralEnvelope(entry.getBounds()));
                        }
                        return format.getReader(blob, hints);
                    }
                }
                finally {
                    close(st);
                }
            }
            finally {
                close(cx);
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
        return null;
    }

    Integer findSRID(GridCoverage2D raster) throws Exception {
        return CRS.lookupEpsgCode(raster.getCoordinateReferenceSystem(), true);
    }

    ReferencedEnvelope findBounds(GridCoverage2D raster) {
        Envelope e = raster.getEnvelope();
        return new ReferencedEnvelope(e.getMinimum(0), e.getMaximum(0), e.getMinimum(1), 
            e.getMaximum(1), raster.getCoordinateReferenceSystem());
    }

    GeneralEnvelope toGeneralEnvelope(ReferencedEnvelope e) {
        GeneralEnvelope ge = new GeneralEnvelope(new double[]{e.getMinX(), e.getMinY()}, 
            new double[]{e.getMaxX(), e.getMaxY()});
        ge.setCoordinateReferenceSystem(e.getCoordinateReferenceSystem());
        return ge;
    }

    RasterEntry createRasterEntry(ResultSet rs) throws SQLException, IOException {
        RasterEntry e = new RasterEntry();
        initEntry(e, rs);

        e.setRasterColumn(rs.getString("r_raster_column"));
        e.setCompressionQualityFactor(rs.getDouble("compr_qual_factor"));
        e.setGeoRectification(Rectification.valueOf(rs.getInt("georectification")));
        return e;
    }

    void addRasterColumnsEntry(RasterEntry e) throws IOException {
        String sql = format(
                "INSERT INTO %s VALUES (?, ?, ?, ?, ?);", RASTER_COLUMNS);

        try {
            Connection cx = connPool.getConnection();
            try {
                PreparedStatement ps = prepare(cx, sql)
                    .set(e.getTableName())
                    .set(e.getRasterColumn())
                    .set(e.getCompressionQualityFactor())
                    .set(e.getGeoRectification().value())
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
    }


    //
    // tile methods
    //

    /**
     * Lists all the tile entries in the geopackage. 
     */
    public List<TileEntry> tiles() throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, b.is_times_two_zoom" +
                 " FROM %s a, %s b" + 
                " WHERE a.table_name = b.t_table_name" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, TILE_TABLE_METADATA);
                LOGGER.fine(sql);

                PreparedStatement ps = cx.prepareStatement(sql);
                ps.setString(1, DataType.Tile.value());

                ResultSet rs = ps.executeQuery();

                List<TileEntry> entries = new ArrayList();
                while(rs.next()) {
                    entries.add(createTileEntry(rs, cx));
                }
                rs.close();
                ps.close();

                return entries;
            }
            finally {
                cx.close();
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * Looks up a tile entry by name.
     * 
     * @param name THe name of the tile entry.
     * @return The entry, or <code>null</code> if no such entry exists.
     */
    public TileEntry tile(String name) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                String sql = format(
                "SELECT a.*, b.is_times_two_zoom" +
                 " FROM %s a, %s b" + 
                " WHERE a.table_name = ?" + 
                  " AND a.data_type = ?", GEOPACKAGE_CONTENTS, TILE_TABLE_METADATA);
                LOGGER.fine(sql);

                PreparedStatement ps = cx.prepareStatement(sql);
                ps.setString(1, name);
                ps.setString(2, DataType.Tile.value());

                ResultSet rs = ps.executeQuery();
                try {
                    if(rs.next()) {
                        return createTileEntry(rs, cx);
                    }
                }
                finally {
                    rs.close();
                    ps.close();
                }
            }
            finally {
                cx.close();
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
        return null;
    }

    /**
     * Creates a new tile entry in the geopackage.
     * 
     * @param entry The tile entry.
     */
    public void create(TileEntry entry) throws IOException {
        if (entry.getBounds() == null) {
            throw new IllegalArgumentException("Tile entry must specify bounds");
        }

        TileEntry e = new TileEntry();
        e.init(entry);

        if (e.getTableName() == null) {
            e.setTableName("tiles");
        }
        if (e.getIdentifier() == null) {
            e.setIdentifier(e.getTableName());
        }
        if (e.getDescription() == null) {
            e.setDescription(e.getIdentifier());
        }

        if (e.getSrid() == null) {
            try {
                e.setSrid(findSRID(entry.getBounds()));
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        if (e.isTimesTwoZoom() == null) {
            e.setTimesTwoZoom(true);
        }

        e.setLastChange(new Date());
        try {
            Connection cx = connPool.getConnection();
            //TODO: do all of this in a transaction
            try {
                //create the tile_table_metadata entry
                PreparedStatement st = 
                    prepare(cx, format("INSERT INTO %s VALUES (?,?)", TILE_TABLE_METADATA))
                    .set(e.getTableName()).set(e.isTimesTwoZoom()).log(Level.FINE).statement();
                st.execute();
                st.close();

                //create the tile_matrix_metadata entries
                st = prepare(cx, format("INSERT INTO %s VALUES (?,?,?,?,?,?,?,?)", TILE_MATRIX_METADATA))
                    .statement();
                for (TileMatrix m : e.getTileMatricies()) {
                    prepare(st).set(e.getTableName()).set(m.getZoomLevel()).set(m.getMatrixWidth())
                        .set(m.getMatrixHeight()).set(m.getTileWidth()).set(m.getTileHeight())
                        .set(m.getXPixelSize()).set(m.getYPixelSize())
                        .statement().execute();
                }
                st.close();

                //create the tile table itself
                st = cx.prepareStatement(format("CREATE TABLE %s (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "zoom_level INTEGER NOT NULL DEFAULT 0," +
                    "tile_column INTEGER NOT NULL DEFAULT 0," +
                    "tile_row INTEGER NOT NULL DEFAULT 0," +
                    "tile_data BLOB NOT NULL DEFAULT (zeroblob(4)))", e.getTableName()));
                st.execute();
                st.close();
            }
            finally {
                cx.close();
            }
        }
        catch(SQLException ex) {
            throw new IOException(ex);
        }

        //update the metadata tables
        addGeoPackageContentsEntry(e);

        entry.init(e);
    }


    /**
     * Adds a tile to the geopackage.
     * 
     * @param entry The tile metadata entry.
     * @param tile The tile.
     */
    public void add(TileEntry entry, Tile tile) throws IOException {
        try {
            Connection cx = connPool.getConnection();
            try {
                PreparedStatement ps = prepare(cx, format("INSERT INTO %s (zoom_level, tile_column,"
                    + " tile_row, tile_data) VALUES (?,?,?,?)", entry.getTableName()))
                    .set(tile.getZoom()).set(tile.getColumn()).set(tile.getRow()).set(tile.getData())
                    .log(Level.FINE).statement();
                ps.execute();
                ps.close();
            }
            finally {
                cx.close();
            }
        }
        catch(SQLException e) {
            throw new IOException(e);
        }
    }

    public TileReader reader(TileEntry entry, Integer lowZoom, Integer highZoom, 
        Integer lowCol, Integer highCol, Integer lowRow, Integer highRow) throws IOException  {

        try {
            List<String> q = new ArrayList();
            if (lowZoom != null) {
                q.add("zoom_level > " + lowZoom);
            }
            if (highZoom != null) {
                q.add("zoom_level < " + lowZoom);
            }
            if (lowCol != null) {
                q.add("tile_column < " + lowCol);
            }
            if (highCol != null) {
                q.add("tile_column < " + highCol);
            }
            if (lowRow != null) {
                q.add("tile_row < " + lowRow);
            }
            if (highRow != null) {
                q.add("tile_row < " + highRow);
            }

            StringBuffer sql = new StringBuffer("SELECT * FROM ").append(entry.getTableName());
            if (!q.isEmpty()) {
                sql.append(" WHERE ");
                for (String s : q) {
                    sql.append(s).append(" AND ");
                }
                sql.setLength(sql.length()-5);
            }

            Connection cx = connPool.getConnection();

            Statement st = cx.createStatement();
            ResultSet rs = st.executeQuery(sql.toString());

            return new TileReader(rs, cx);

        }
        catch(SQLException e) {
            throw new IOException(e);
        }
        
    }

    TileEntry createTileEntry(ResultSet rs, Connection cx) throws SQLException, IOException {
        TileEntry e = new TileEntry();
        initEntry(e, rs);

        e.setTimesTwoZoom(rs.getBoolean("is_times_two_zoom"));

        //load all the tile matrix entries
        PreparedStatement psm = cx.prepareStatement(format(
            "SELECT * FROM %s" + 
            " WHERE t_table_name = ?", TILE_MATRIX_METADATA));
        psm.setString(1, e.getTableName());

        ResultSet rsm = psm.executeQuery();
        while(rsm.next()) {
            TileMatrix m = new TileMatrix();
            m.setZoomLevel(rsm.getInt("zoom_level"));
            m.setMatrixWidth(rsm.getInt("matrix_width"));
            m.setMatrixHeight(rsm.getInt("matrix_height"));
            m.setTileWidth(rsm.getInt("tile_width"));
            m.setTileHeight(rsm.getInt("tile_height"));
            m.setXPixelSize(rsm.getDouble("pixel_x_size"));
            m.setYPixelSize(rsm.getDouble("pixel_y_size"));

            e.getTileMatricies().add(m);
        }

        rsm.close();
        psm.close();

        return e;
    }

    Integer findSRID(ReferencedEnvelope e) throws Exception {
        return CRS.lookupEpsgCode(e.getCoordinateReferenceSystem(), true);
    }

    //
    //sql utility methods
    //

    void initEntry(Entry e, ResultSet rs) throws SQLException, IOException {
        e.setIdentifier(rs.getString("identifier"));
        e.setDescription(rs.getString("description"));
        e.setTableName(rs.getString("table_name"));
        e.setLastChange(rs.getDate("last_change"));

        int srid = rs.getInt("srid"); 
        e.setSrid(srid);

        CoordinateReferenceSystem crs;
        try {
            crs = CRS.decode("EPSG:" + srid);
        } 
        catch(Exception ex) {
            throw new IOException(ex);
        }

        e.setBounds(new ReferencedEnvelope(rs.getDouble("min_x"), 
            rs.getDouble("max_x"), rs.getDouble("min_y"), rs.getDouble("max_y"), crs));
    }

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
