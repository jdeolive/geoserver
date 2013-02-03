package org.geoserver.geopkg;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;

import it.geosolutions.imageio.stream.input.ImageInputStreamAdapter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io.FileUtils;
import org.geoserver.geopkg.Entry.DataType;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.factory.Hints;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.image.WorldImageFormat;
import org.geotools.gce.image.WorldImageReader;
import org.geotools.geometry.jts.Geometries;
import org.geotools.TestData;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;

import com.vividsolutions.jts.geom.Geometry;

public class GeoPackageTest {

    GeoPackage geopkg;

    @BeforeClass
    public static void setUpOnce() {
        Hints.putSystemDefault(Hints.COMPARISON_TOLERANCE, 1e-9);
    }

    @Before
    public void setUp() throws Exception {
        geopkg = new GeoPackage(File.createTempFile("geopkg", "db", new File("target")));
        geopkg.init();
    }

    @After
    public void tearDown() throws Exception {
        geopkg.close();

        //for debugging, copy the current geopackage file to a well known file
        File f = new File("target", "geopkg.db");
        if (f.exists()) {
            f.delete();
        }

        FileUtils.copyFile(geopkg.getFile(), f);
    }

    @Test
    public void testInit() throws Exception {
        assertTableExists("geopackage_contents");
        assertTableExists("geometry_columns");
        assertTableExists("spatial_ref_sys");
    }

    void assertTableExists(String table) throws Exception {
        Connection cx = geopkg.getDataSource().getConnection();
        Statement st = cx.createStatement();
        try {
            st.execute(String.format("SELECT count(*) FROM %s;", table));
        }
        catch(Exception e) {
            fail(e.getMessage());
        }
        finally {
            st.close();
            cx.close();
        }
    }

    @Test
    public void testCreateFeatureEntry() throws Exception {
        ShapefileDataStore shp = new ShapefileDataStore(setUpShapefile());

        FeatureEntry entry = new FeatureEntry();
        geopkg.add(entry, shp.getFeatureSource());

        assertTableExists("bugsites");

        //check metadata contents
        assertFeatureEntry(entry);
        
        SimpleFeatureReader re = Features.simple(shp.getFeatureReader());
        SimpleFeatureReader ra = geopkg.reader(entry, null, null);

        while(re.hasNext()) {
            assertTrue(ra.hasNext());
            assertSimilar(re.next(), ra.next());
        }

        re.close();
        ra.close();
    }

    @Test
    public void testCreateRasterEntry() throws Exception {
        GeoTiffFormat format = new GeoTiffFormat();
        GeoTiffReader reader = format.getReader(setUpGeoTiff());
        GridCoverage2D cov = reader.read(null);

        RasterEntry entry = new RasterEntry();
        entry.setTableName("world");

        geopkg.add(entry, cov, format);

        assertTableExists("world");
        assertRasterEntry(entry);

        GridCoverageReader r = geopkg.reader(entry, format);
        GridCoverage2D c = (GridCoverage2D) r.read(null);
        assertNotNull(c);
    }

    @Test
    public void testCreateRasterEntryPNG() throws Exception {
        WorldImageFormat format = new WorldImageFormat();
        WorldImageReader reader = format.getReader(setUpPNG());
        GridCoverage2D cov = reader.read(null);

        RasterEntry entry = new RasterEntry();
        entry.setTableName("Pk50095");

        geopkg.add(entry, cov, format);
        assertTableExists("Pk50095");
        assertRasterEntry(entry);

        GridCoverageReader r = geopkg.reader(entry, format);
        GridCoverage2D c = (GridCoverage2D) r.read(null);
        assertNotNull(c);
    }

    void assertContentEntry(Entry entry) throws Exception {
        Connection cx = geopkg.getDataSource().getConnection();
        try {
            PreparedStatement ps = 
                cx.prepareStatement("SELECT * FROM geopackage_contents WHERE table_name = ?");
            ps.setString(1, entry.getTableName());

            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());

            assertEquals(entry.getIdentifier(), rs.getString("identifier"));
            assertEquals(entry.getDescription(), rs.getString("description"));
            assertEquals(entry.getSrid().intValue(), rs.getInt("srid"));

            assertEquals(entry.getBounds().getMinX(), rs.getDouble("min_x"), 0.1);
            assertEquals(entry.getBounds().getMinY(), rs.getDouble("min_y"), 0.1);
            assertEquals(entry.getBounds().getMaxX(), rs.getDouble("max_x"), 0.1);
            assertEquals(entry.getBounds().getMaxY(), rs.getDouble("max_y"), 0.1);

            rs.close();
            ps.close();
        }
        finally {
            cx.close();
        }
    }

    void assertFeatureEntry(FeatureEntry entry) throws Exception {
        assertContentEntry(entry);
        
        Connection cx = geopkg.getDataSource().getConnection();
        try {
            PreparedStatement ps = 
                cx.prepareStatement("SELECT * FROM geometry_columns WHERE f_table_name = ?");
            ps.setString(1, entry.getTableName());

            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());

            assertEquals(entry.getGeometryColumn(), rs.getString("f_geometry_column"));
            assertEquals(entry.getGeometryType(), Geometries.getForName(rs.getString("type")));
            assertEquals(entry.getSrid().intValue(), rs.getInt("srid"));
            assertEquals(entry.getCoordDimension().intValue(), rs.getInt("coord_dimension"));

            rs.close();
            ps.close();
        }
        finally {
            cx.close();
        }
    }

    void assertRasterEntry(RasterEntry entry) throws Exception {
        assertContentEntry(entry);
        
        Connection cx = geopkg.getDataSource().getConnection();
        try {
            PreparedStatement ps = 
                cx.prepareStatement("SELECT * FROM raster_columns WHERE r_table_name = ?");
            ps.setString(1, entry.getTableName());

            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());

            assertEquals(entry.getRasterColumn(), rs.getString("r_raster_column"));
            assertEquals(entry.getSrid().intValue(), rs.getInt("srid"));
            assertEquals(entry.getGeoRectification().value(), rs.getInt("georectification"));
            assertEquals(entry.getCompressionQualityFactor(), rs.getDouble("compr_qual_factor"), 0.1);

            rs.close();
            ps.close();
        }
        finally {
            cx.close();
        }
    }

    void assertSimilar(SimpleFeature expected, SimpleFeature actual) {
        assertNotNull(actual);

        assertTrue(((Geometry)expected.getDefaultGeometry()).equals(
            ((Geometry)actual.getDefaultGeometry())));
        for (AttributeDescriptor d : expected.getType().getAttributeDescriptors()) {
            Object e = expected.getAttribute(d.getLocalName());
            Object a = actual.getAttribute(d.getLocalName());

            if (e instanceof Number) {
                assertEquals(((Number) e).intValue(), ((Number)a).intValue());
            }
            else {
                assertEquals(e, a);
            }
        }
    }

    URL setUpShapefile() throws IOException {
        File d = File.createTempFile("bugsites", "shp", new File("target"));
        d.delete();
        d.mkdirs();

        String[] exts = new String[]{"shp", "shx", "dbf", "prj"};
        for (String ext : exts) {
            FileUtils.copyURLToFile(TestData.url("shapes/bugsites." + ext), 
                new File(d, "bugsites." + ext));
        }
        
        return DataUtilities.fileToURL(new File(d, "bugsites.shp"));
    }

    URL setUpGeoTiff() throws IOException {
        File d = File.createTempFile("world", "tiff", new File("target"));
        d.delete();
        d.mkdirs();

        FileUtils.copyURLToFile(TestData.url("geotiff/world.tiff"), new File(d, "world.tiff"));
        return DataUtilities.fileToURL(new File(d, "world.tiff")); 
    }

    URL setUpPNG() throws IOException {
        File d = File.createTempFile("Pk50095", "png", new File("target"));
        d.delete();
        d.mkdirs();

        FileUtils.copyURLToFile(TestData.url(this, "Pk50095.png"), new File(d, "Pk50095.png"));
        FileUtils.copyURLToFile(TestData.url(this, "Pk50095.pgw"), new File(d, "Pk50095.pgw"));
        return DataUtilities.fileToURL(new File(d, "Pk50095.png")); 
    }
}
