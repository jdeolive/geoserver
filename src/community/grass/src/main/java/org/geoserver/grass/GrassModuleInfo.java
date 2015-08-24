package org.geoserver.grass;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Point;
import net.sf.json.JSONObject;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.Parameter;
import org.geotools.feature.FeatureCollection;
import org.geotools.util.SimpleInternationalString;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Info about a grass module.
 */
public class GrassModuleInfo {

    public enum Type {
        VECTOR, RASTER
    }

    /**
     * parameter metadata to maintain original grass type
     */
    static final String MD_GRASS_TYPE = "grass_type";

    /**
     * function to map json representation of input/output to a process parameter
     */
    static Function<Object,Parameter> TO_PARAM = new Function<Object, Parameter>() {
        @Nullable
        @Override
        public Parameter apply(Object input) {
            JSONObject p = (JSONObject) input;
            String name = p.getString("name");

            String type = p.getString("type");
            boolean required = p.getBoolean("required");

            // TODO: grass can have arguments that take many values, reflect that here
            return new Parameter(name, type(p), new SimpleInternationalString(name),
                new SimpleInternationalString(p.getString("description")), required, required ? 1 : 0, 1,
                p.get("default"), ImmutableMap.of(MD_GRASS_TYPE, type));
        }
    };

    static Class type(JSONObject p) {
        String type = p.getString("type");
        switch(type) {
            case "raster":
                return GridCoverage2D.class;
            case "vector":
                return FeatureCollection.class;
            case "coords":
                return Point.class;
            case "str":
                return String.class;
            case "int":
                return Integer.class;
            case "float":
                return Double.class;
            default:
                return String.class;
        }

    }
    /**
     * JSON representation of the module from the server
     */
    JSONObject obj;

    GrassModuleInfo(JSONObject obj) {
        this.obj = obj;
    }

    public String name() {
        return obj.getString("name");
    }

    public String description() {
        return obj.getString("description");
    }

    public Type type() {
        return Type.valueOf(obj.getString("type").toUpperCase());
    }

    @SuppressWarnings("unchecked")
    public List<Parameter<?>> inputs() {
        return Lists.transform(obj.getJSONArray("inputs"), TO_PARAM);
    }

    public List<Parameter<?>> outputs() {
        return Lists.transform(obj.getJSONArray("outputs"), TO_PARAM);
    }
}
