package org.geoserver.geopkg;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.factory.Hints;
import org.geotools.TestData;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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

        SimpleFeatureReader re = Features.simple(shp.getFeatureReader());
        SimpleFeatureReader ra = geopkg.reader(entry, null, null);

        while(re.hasNext()) {
            assertTrue(ra.hasNext());

            assertSimilar(re.next(), ra.next());
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
}
