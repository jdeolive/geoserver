package org.geoserver.grass;

import com.google.common.collect.Maps;
import com.google.common.collect.Maps.EntryTransformer;
import com.vividsolutions.jts.geom.Point;
import net.sf.json.JSONObject;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.Parameter;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.process.Process;
import org.geotools.process.ProcessException;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;
import org.opengis.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

public class GrassProcess implements Process {

    static Logger LOG = Logging.getLogger(GrassProcess.class);

    Name name;
    GrassProcessFactory factory;

    public GrassProcess(Name name, GrassProcessFactory factory) {
        this.name = name;
        this.factory = factory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, ProgressListener monitor) throws ProcessException {
        // request payload
        JSONObject req = new JSONObject();

        try {
            // map the input values back to grass
            Map<String, Parameter<?>> inputs = factory.getParameterInfo(name);
            for (String key : input.keySet()) {
                Parameter p = inputs.get(key);
                if (p == null) {
                    LOG.log(Level.WARNING, "Unrecognized input parameter: {0}, ignoring.", key);
                    continue;
                }

                Object val = input.get(key);
                String grassType = (String) p.metadata.get(GrassModuleInfo.MD_GRASS_TYPE);

                req.put(key, unmapValue(val, grassType));
            }
        }
        catch(IOException e) {
            throw new ProcessException("Unable to prepare process inputs", e);
        }

        try {
            // run it
            final JSONObject rsp = factory.server.run(name.getLocalPart(), req);

            LOG.log(Level.INFO,
                "Process {0} finished with result: {1}", new Object[]{name.getLocalPart(), rsp.toString()});

            // map response values
            final Map<String,Parameter<?>> outputs = factory.getResultInfo(name, null);

            return Maps.transformEntries(rsp, new EntryTransformer<String,Object,Object>() {
                @Override
                public Object transformEntry(String key, Object value) {
                    Parameter<?> p = outputs.get(key);
                    if (p == null) {
                        LOG.log(Level.WARNING, "Unknown result from process: {1}", key);
                        return value;
                    }

                    try {
                        return mapValue(value, p.type);
                    } catch (IOException e) {
                        throw new RuntimeException(format("Unable to map %s from process result", key));
                    }
                }
            });
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                "Process {0} failed with input {1}", new Object[]{name.getLocalPart(), req.toString()});

            throw new ProcessException(format("Process %s failed", name.getLocalPart()), e);
        }
    }

    /**
     * Maps a process input value into what we send to the grass server.
     */
    Object unmapValue(Object val, String grassType) throws IOException {
        switch(grassType) {
            case "raster":
                // TODO: find a way to use the source of the layer directly
                return convertToGeoTIFF((GridCoverage2D)val).toFile().getAbsolutePath();
            case "coords":
                if (val instanceof Point) {
                    Point p = (Point) val;
                    return format("%f, %f", p.getX(), p.getY());
                }
        }
        return val;
    }

    /**
     * Maps a process result from the grass server to what we send back from the process.
     */
    Object mapValue(Object val, Class type) throws IOException {
        if (GridCoverage2D.class.isAssignableFrom(type)) {
            // should be a path to a geotiff raster file
            return readGeoTIFF((String)val);
        }
        return val;
    }

    Path convertToGeoTIFF(GridCoverage2D raster) throws IOException {
        // write the raster out into a temporary geotiff
        Path file = Files.createTempDirectory("wps").resolve("in.tif");

        GeoTiffWriter gtw = new GeoTiffWriter(file.toFile());
        gtw.write(raster, null);

        LOG.info("Staging input: " + file.toAbsolutePath());
        return file;
    }

    GridCoverage2D readGeoTIFF(String path) throws IOException {
        GeoTiffReader gtr = new GeoTiffReader(new File(path));
        return gtr.read(null);
    }
}
