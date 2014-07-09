package org.geoserver.catalog;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.styling.*;
import org.geotools.util.Version;
import org.opengis.filter.FilterFactory;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Justin Deoliveira, Boundless
 */
public class PropertyStyleHandler extends StyleHandler {

    public static final String FORMAT = "psl";

    StyleFactory styleFactory;
    FilterFactory filterFactory;

    public PropertyStyleHandler() {
        super(FORMAT, new Version("1.0.0"));
        styleFactory = CommonFactoryFinder.getStyleFactory();
        filterFactory = CommonFactoryFinder.getFilterFactory();
    }

    @Override
    public StyledLayerDescriptor parse(Object input, ResourceLocator resourceLocator) throws IOException {
        Properties p = new Properties();
        p.load(toReader(input));

        Color color = color(p.getProperty("color"), Color.BLACK);
        Symbolizer sym = null;

        String type = p.getProperty("type");
        if ("line".equalsIgnoreCase(type)) {
            LineSymbolizer ls = styleFactory.createLineSymbolizer();
            ls.setStroke(styleFactory.createStroke(filterFactory.literal(color), filterFactory.literal(2)));

            sym = ls;
        }
        else if ("polygon".equalsIgnoreCase(type)) {
            PolygonSymbolizer ps = styleFactory.createPolygonSymbolizer();
            ps.setFill(styleFactory.createFill(filterFactory.literal(color)));

            sym = ps;
        }
        else if ("raster".equalsIgnoreCase(type)) {
            RasterSymbolizer rs = styleFactory.createRasterSymbolizer();
            sym = rs;
        }
        else {
            Mark mark = styleFactory.createMark();
            mark.setFill(styleFactory.createFill(filterFactory.literal(color)));

            PointSymbolizer ps = styleFactory.createPointSymbolizer();
            ps.setGraphic(styleFactory.createDefaultGraphic());
            ps.getGraphic().graphicalSymbols().add(mark);

            sym = ps;
        }

        Rule r = styleFactory.createRule();
        r.symbolizers().add(sym);

        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle();
        fts.rules().add(r);

        Style s = styleFactory.createStyle();
        s.featureTypeStyles().add(fts);

        UserLayer l = styleFactory.createUserLayer();
        l.userStyles().add(s);

        StyledLayerDescriptor sld = styleFactory.createStyledLayerDescriptor();
        sld.layers().add(l);
        return sld;
    }

    Color color(String color, Color def) {
        if (color == null) {
            return def;
        }

        return new Color(Integer.valueOf(color.substring(0,2), 16),
            Integer.valueOf(color.substring(2,4), 16), Integer.valueOf(color.substring(4,6), 16));
    }

    @Override
    public void encode(StyledLayerDescriptor sld, boolean pretty, OutputStream output) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Exception> validate(Object input) throws IOException {
        return Collections.emptyList();
    }
}
