package org.geoserver.catalog;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.List;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.styling.StyleFactory;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.util.Version;

/**
 * Extension point for handling a style of a particular language/version.
 * <p>
 * </p>
 * @author Justin Deoliveira, OpenGeo
 *
 */
public abstract class StyleHandler {

    protected static StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory(null);

    String format;
    Version version;

    protected StyleHandler(String format, Version version) {
        this.format = format;
        this.version = version;
    }

    public final String getFormat() {
        return format;
    }

    public Version getVersion() {
        return version;
    }

    public abstract StyledLayerDescriptor parse(Object input) throws IOException;

    public abstract void encode(StyledLayerDescriptor sld, boolean pretty, OutputStream output) 
        throws IOException;

    public abstract List<Exception> validate(Object input) throws IOException;

    protected Reader toReader(Object input) throws IOException {
        if (input instanceof Reader) {
            return (Reader) input;
        }
        
        if (input instanceof InputStream) {
            return new InputStreamReader((InputStream)input);
        }
        
        if (input instanceof File) {
            return new FileReader((File)input);
        }
        
        throw new IllegalArgumentException("Unable to turn " + input + " into reader");
    }
}
