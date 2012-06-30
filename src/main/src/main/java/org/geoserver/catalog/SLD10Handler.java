package org.geoserver.catalog;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.geotools.styling.NamedLayer;
import org.geotools.styling.SLDParser;
import org.geotools.styling.SLDTransformer;
import org.geotools.styling.Style;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.util.Version;
import org.vfny.geoserver.util.SLDValidator;
import org.xml.sax.InputSource;

public class SLD10Handler extends SLDHandler {

    public static final Version VERSION = new Version("1.0.0");

    public SLD10Handler() {
        super(VERSION);
    }

    @Override
    public StyledLayerDescriptor parse(Object input) throws IOException {
        SLDParser p = parser(input);
        StyledLayerDescriptor sld = p.parseSLD();
        if (sld.getStyledLayers().length == 0) {
            //most likely a style that is not a valid sld, try to actually parse out a 
            // style and then wrap it in an sld
            Style[] style = p.readDOM();
            if (style.length > 0) {
                NamedLayer l = styleFactory.createNamedLayer();
                l.addStyle(style[0]);
                sld.addStyledLayer(l);
            }
        }
        
        return sld;
    }
    
    @Override
    public List<Exception> validate(Object input) throws IOException {
        return new SLDValidator().validateSLD(new InputSource(toReader(input)));
    }
    
    @Override
    public void encode(StyledLayerDescriptor sld, boolean format, OutputStream output) throws IOException {
        SLDTransformer tx = new SLDTransformer();
        if (format) {
            tx.setIndentation(2);
        }
        try {
            tx.transform( sld, output );
        } 
        catch (TransformerException e) {
            throw (IOException) new IOException("Error writing style").initCause(e);
        }
    }
    
    
    SLDParser parser(Object input) throws IOException {
        if (input instanceof File) {
            return new SLDParser(styleFactory, (File) input);
        }
        else {
            return new SLDParser(styleFactory, toReader(input));
        }
    }
}
