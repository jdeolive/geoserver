package org.geoserver.mbtiles;

import static java.lang.String.format;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.geoserver.gwc.GWC;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.geoserver.wms.MapProducerCapabilities;
import org.geoserver.wms.RasterCleaner;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.WebMap;
import org.geoserver.wms.WebMapService;
import org.geoserver.wms.map.AbstractMapOutputFormat;
import org.geoserver.wms.map.JPEGMapResponse;
import org.geoserver.wms.map.PNGMapResponse;
import org.geoserver.wms.map.RawMap;
import org.geoserver.wms.map.RenderedImageMap;
import org.geoserver.wms.map.RenderedImageMapResponse;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.renderer.lite.RendererUtilities;
import org.geotools.util.logging.Logging;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Envelope;

public class MBTilesOutputFormat extends AbstractMapOutputFormat {

    static Logger LOGGER = Logging.getLogger("org.geoserver.mbtiles");

    static final String MIME_TYPE = "application/x-sqlite3";

    static final String PNG_MIME_TYPE = "image/png";

    static final String JPEG_MIME_TYPE = "image/jpeg";

    static final Set<String> NAMES = Sets.newHashSet("mbtiles");

    static final int TILE_CLEANUP_INTERVAL;
    static {
        //calculate the number of tiles we can generate before having to cleanup, value is
        //  25% of total memory / approximte size of single tile
        TILE_CLEANUP_INTERVAL = (int) (Runtime.getRuntime().maxMemory() * 0.25 / (256.0*256*4)); 
    }
    WebMapService webMapService;
    GWC gwc;
    WMS wms;

    public MBTilesOutputFormat(WebMapService webMapService, WMS wms, GWC gwc) {
        super(MIME_TYPE, NAMES);
        this.webMapService = webMapService;
        this.wms = wms;
        this.gwc = gwc;
    }

    @Override
    public WebMap produceMap(WMSMapContent map) throws ServiceException, IOException {
        //find the dimensions we want to grab files for
        GridSubset gridSubset = findBestGridSubset(map);
        int[] minmax = findMinMaxZoom(gridSubset, map);

        Map formatOpts = map.getRequest().getFormatOptions();

        //figure out what format we need
        String format = formatOpts.containsKey("format") ? 
            parseFormatFromOpts(formatOpts) : findBestFormat(map);

        //prep the database
        MBTilesDB db = null;
        try {
            db = new MBTilesDB();
            db.init();

            if (Boolean.valueOf((String)formatOpts.get("android"))) {
                db.initForAndroid();
            }
        } catch (SQLException e) {
            throw new ServiceException("Could not create SQLite database", e);
        }

        //push in the metadata
        //TODO: fill this in better
        Map<String,String> metadata = new LinkedHashMap<String, String>();
        metadata.put("name", map.getTitle());
        metadata.put("type", "baselayer");
        metadata.put("version", "1.0");
        metadata.put("description", map.getAbstract());
        metadata.put("format", formatName(format));

        BoundingBox bbox = bbox(map);
        metadata.put("bounds", String.format("%f,%f,%f,%f", 
            bbox.getMinX(), bbox.getMinY(), bbox.getMaxX(), bbox.getMaxY()));
        metadata.put("srs", map.getRequest().getSRS());
        try {
            db.addMetadata(metadata);
        } catch (SQLException e) {
            throw new ServiceException("Error adding MBTiles metadata", e);
        }

        //create a prototype getmap request
        GetMapRequest req = new GetMapRequest();
        OwsUtils.copy(map.getRequest(), req, GetMapRequest.class);
        req.setFormat(format);
        req.setWidth(gridSubset.getTileWidth());
        req.setHeight(gridSubset.getTileHeight());

        //count tiles as we generate them
        int ntiles = 0;

        //flag determining if tile row indexes we store in database should be inverted 
        boolean flipy = Boolean.valueOf((String)formatOpts.get("flipy"));
        for (int z = minmax[0]; z <= minmax[1]; z++) {
            long[] intersect = gridSubset.getCoverageIntersection(z, bbox);
            for (long x = intersect[0]; x <= intersect[2]; x++) {
                for (long y = intersect[1]; y <= intersect[3]; y++) {
                    BoundingBox box = gridSubset.boundsFromIndex(new long[]{x,y,z});
                    req.setBbox(
                        new Envelope(box.getMinX(),box.getMaxX(),box.getMinY(),box.getMaxY()));

                    WebMap result = webMapService.getMap(req);
                    try {
                        db.addTile(z, x, flipy?gridSubset.getNumTilesHigh(z)-(y+1):y, toBytes(result));
                    } catch (SQLException e) {
                        throw new ServiceException("Tile generation failed", e);
                    }

                    //images we encode are actually kept around, we need to clean them up
                    if (ntiles++ == TILE_CLEANUP_INTERVAL) {
                        cleanUpImages();
                        ntiles = 0;
                    }
                }
            }
        }

        db.close();

        final File dbFile = db.getFile();
        final BufferedInputStream bin = new BufferedInputStream(new FileInputStream(dbFile));

        RawMap result = new RawMap(map, bin, MIME_TYPE) {
            @Override
            public void writeTo(OutputStream out) throws IOException {
                super.writeTo(out);
                bin.close();
                try {
                    dbFile.delete();
                }
                catch(Exception e) {
                    LOGGER.log(Level.WARNING, "Error deleting file: " + dbFile.getAbsolutePath(), e);
                }
            }
        };
        result.setContentDispositionHeader(map, ".db", true);
        return result;
    }

    ReferencedEnvelope bounds(WMSMapContent map) {
        return new ReferencedEnvelope(map.getRequest().getBbox(), map.getCoordinateReferenceSystem());
    }

    BoundingBox bbox(WMSMapContent map) {
        Envelope bnds = bounds(map);
        return new BoundingBox(bnds.getMinX(), bnds.getMinY(), bnds.getMaxX(), bnds.getMaxY());
    }

    byte[] toBytes(WebMap map) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        if (map instanceof RenderedImageMap) {
            RenderedImageMapResponse response = JPEG_MIME_TYPE.equals(map.getMimeType()) ?
                new JPEGMapResponse(wms) : new PNGMapResponse(wms);
            response.write(map, bout, null);
        }
        else if (map instanceof RawMap) {
            ((RawMap) map).writeTo(bout);
        }
        bout.flush();
        return bout.toByteArray();
    }

    void cleanUpImages() {
        RasterCleaner cleaner = GeoServerExtensions.bean(RasterCleaner.class);
        cleaner.finished(null);
    }

    @Override
    public MapProducerCapabilities getCapabilities(String format) {
        return new MapProducerCapabilities(false, false, false, true, null);
    }


    GridSubset findBestGridSubset(WMSMapContent map) {
        GetMapRequest req = map.getRequest();
        Map formatOpts = req.getFormatOptions();

        GridSetBroker gridSetBroker = gwc.getGridSetBroker();
        GridSet gridSet = null;
        //first check format options to see if explicitly specified
        if (formatOpts.containsKey("gridset")) {
            gridSet = gridSetBroker.get(formatOpts.get("gridset").toString());
        }

        //next check srs
        if (gridSet == null) {
            gridSet = gridSetBroker.get(req.getSRS().toUpperCase());
        }

        if (gridSet != null) {
            return GridSubsetFactory.createGridSubSet(gridSet);
        }

        CoordinateReferenceSystem crs = map.getCoordinateReferenceSystem();

        //look up epsg code
        Integer epsgCode = null;
        try {
            epsgCode = CRS.lookupEpsgCode(crs, false);
        } catch (Exception e) {
            throw new ServiceException("Unable to determine epsg code for " + crs, e);
        }
        if (epsgCode == null) {
            throw new ServiceException("Unable to determine epsg code for " + crs);
        }

        SRS srs = SRS.getSRS(epsgCode);

        //figure out the appropriate grid sub set
        Set<GridSubset> gridSubsets = new LinkedHashSet<GridSubset>();
        for (MapLayerInfo l : req.getLayers()) {
            TileLayer tl = gwc.getTileLayerByName(l.getName());
            if (tl == null) {
                throw new ServiceException("No tile layer for " + l.getName());
            }

            List<GridSubset> theseGridSubsets = tl.getGridSubsetsForSRS(srs);
            if (gridSubsets.isEmpty()) {
                gridSubsets.addAll(theseGridSubsets);
            }
            else {
                gridSubsets.retainAll(theseGridSubsets);
            }

            if (gridSubsets.isEmpty()) {
                throw new ServiceException(
                    "No suitable " + epsgCode + " grid subset for " + req.getLayers());
            }
        }

        if (gridSubsets.size() > 1) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                StringBuilder msg = new StringBuilder("Found multiple grid subsets: ");
                for (GridSubset gs : gridSubsets) {
                    msg.append(gs.getName()).append(", ");
                }
                msg.setLength(msg.length()-2);
                msg.append(". Choosing first.");
                LOGGER.warning(msg.toString());
            }
        }

        return gridSubsets.iterator().next();
    }
    

    int[] findMinMaxZoom(GridSubset gridSubset, WMSMapContent map) {
        GridSet gridSet = gridSubset.getGridSet();
        Map formatOpts = map.getRequest().getFormatOptions();

        Integer minZoom = null;
        if (formatOpts.containsKey("minZoom")) {
            minZoom = Integer.parseInt(formatOpts.get("minZoom").toString());
        }
        if (minZoom == null) {
            minZoom = findClosestZoom(gridSet, map);
        }

        Integer maxZoom = null;
        if (formatOpts.containsKey("maxZoom")) {
            maxZoom = Integer.parseInt(formatOpts.get("maxZoom").toString());
        }
        else if (formatOpts.containsKey("numZooms")) {
            maxZoom = minZoom + Integer.parseInt(formatOpts.get("numZooms").toString());
        }

        if (maxZoom == null) {
            //walk down until we hit too many tiles
            maxZoom = findMaxZoomAuto(gridSubset, minZoom, map); 
        }

        if (maxZoom < minZoom) {
            throw new ServiceException(
                format("maxZoom (%d) can not be less than minZoom (%d)", maxZoom, minZoom));
        }

        //end index
        if (maxZoom > gridSet.getNumLevels()) {
            LOGGER.warning(format("Max zoom (%d) can't be greater than number of zoom levels (%d)", 
                maxZoom, gridSet.getNumLevels()));
            maxZoom = gridSet.getNumLevels();
        }

        return new int[]{minZoom, maxZoom};
    }

    
    Integer findClosestZoom(GridSet gridSet, WMSMapContent map) {
        double reqScale = 
            RendererUtilities.calculateOGCScale(bounds(map), gridSet.getTileWidth(), null);

        int i = 0; 
        double error = Math.abs(gridSet.getGrid(i).getScaleDenominator() - reqScale);
        while (i < gridSet.getNumLevels()-1) {
            Grid g = gridSet.getGrid(i+1);
            double e = Math.abs(g.getScaleDenominator() - reqScale);

            if (e > error) {
                break;
            }
            else {
                error = e;
            }
            i++;
        }

        return Math.max(i, 0);
    }
    

    Integer findMaxZoomAuto(GridSubset gridSubset, Integer minZoom, WMSMapContent map) {
        BoundingBox bbox = bbox(map);

        int zoom = minZoom;
        int ntiles = 0;

        while(ntiles < 256 && zoom < gridSubset.getGridSet().getNumLevels()) {
            long[] intersect = gridSubset.getCoverageIntersection(zoom, bbox);
            ntiles += (intersect[2]-intersect[0])*(intersect[3]-intersect[1]);
            zoom++;
        }
        return zoom;
    }

    
    String parseFormatFromOpts(Map formatOpts) {
        String format = (String) formatOpts.get("format");
        return format.contains("/") ? format : "image/" + format;
    }

    String findBestFormat(WMSMapContent map) {
        //if request is a single coverage layer return jpeg, otherwise use just png
        List<MapLayerInfo> layers = map.getRequest().getLayers();
        if (layers.size() == 1 && layers.get(0).getType() == MapLayerInfo.TYPE_RASTER) {
            return JPEG_MIME_TYPE;
        }
        return PNG_MIME_TYPE;
    }

    String formatName(String format) {
        return format.split("/")[1];
        
    }

    public static void main(String[] args) throws Exception {
        MBTilesDB db = new MBTilesDB(new File("/Users/jdeolive/osm.db"));
        File dir = new File("/Users/jdeolive/tiles");
        dir.mkdirs();

        TileIterator it = db.tiles();
        try {
            while(it.hasNext()) {
                Tile t = it.next();
                String filename = 
                    String.format("%d-%d-%d.png", t.getZoom(),t.getColumn(),t.getRow());
                FileUtils.writeByteArrayToFile(new File(dir, filename), t.getData());
            }
        }
        finally {
            it.close();
        }
        db.close();
    }
}
