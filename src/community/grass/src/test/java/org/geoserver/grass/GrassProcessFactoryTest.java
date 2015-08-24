package org.geoserver.grass;

import com.google.common.collect.ImmutableMap;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.geoserver.data.test.TestData;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.Parameter;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.NameImpl;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opengis.feature.type.Name;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GrassProcessFactoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    GrassProcessFactory factory;

    @Before
    public void setUp() {
        factory = new GrassProcessFactory();
        Assume.assumeTrue(factory.isAvailable());
    }

    @Test
    public void testGetNames() {
        Set<Name> names = factory.getNames();
        assertFalse(names.isEmpty());
    }

    @Test
    public void testGetInfo() {
        Name buffer = new NameImpl(GrassProcessFactory.NS, "v.buffer");
        Name viewshed = new NameImpl(GrassProcessFactory.NS, "r.viewshed");

        assertEquals("v.buffer", factory.getTitle(buffer).toString());
        assertEquals("Creates a buffer around vector features of given type.",
            factory.getDescription(buffer).toString());

        Map<String,Parameter<?>> params = factory.getParameterInfo(buffer);
        assertTrue(params.containsKey("input"));
        assertEquals(FeatureCollection.class, params.get("input").type);
        assertTrue(params.containsKey("distance"));

        params = factory.getParameterInfo(viewshed);
        assertTrue(params.containsKey("input"));
        assertEquals(GridCoverage2D.class, params.get("input").type);
    }

    @Test
    public void testRun() throws Exception {
        File file = tmp.newFile("dem.diff");
        file.delete();

        Files.copy(TestData.class.getResourceAsStream("tazdem.tiff"), file.toPath());

        Name viewshed = new NameImpl(GrassProcessFactory.NS, "r.viewshed");

        Map<String,Object> in = ImmutableMap.of(
            "input", (Object) new GeoTiffReader(file).read(null),
            "coordinates", new GeometryFactory().createPoint(new Coordinate(145.5, -42.0))
        );

        Map<String,Object> out = factory.create(viewshed).execute(in, null);
        assertTrue(out.containsKey("output"));
        assertTrue(out.get("output") instanceof GridCoverage2D);
    }
}
