package org.geoserver.grass;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.geotools.data.Parameter;
import org.geotools.feature.NameImpl;
import org.geotools.process.ProcessFactory;
import org.geotools.util.SimpleInternationalString;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;
import org.opengis.util.InternationalString;

import javax.annotation.Nullable;
import java.awt.RenderingHints.Key;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class GrassProcessFactory implements ProcessFactory {

    static Logger LOG = Logging.getLogger(GrassProcessFactory.class);

    static final String NS = "grass";

    static final Function<Parameter<?>,String> KEY = new Function<Parameter<?>, String>() {
        @Nullable
        @Override
        public String apply(@Nullable Parameter<?> p) {
            return p.key;
        }
    };

    GrassServer server;

    LoadingCache<Name,GrassModuleInfo> modules = CacheBuilder.newBuilder().build(new CacheLoader<Name, GrassModuleInfo>() {
        @Override
        public GrassModuleInfo load(Name key) throws Exception {
            return server.info(key.getLocalPart());
        }
    });

    public GrassProcessFactory() {
        server = new GrassServer();  // TODO: pass in url to server
    }

    @Override
    public InternationalString getTitle() {
        return new SimpleInternationalString("GRASS GIS");
    }

    @Override
    public boolean isAvailable() {
        return server.connected();
    }

    @Override
    public Set<Name> getNames() {
        try {
            return ImmutableSet.copyOf(Iterables.transform(server.list(), new Function<String, Name>() {
                @Nullable
                @Override
                public Name apply(@Nullable String input) {
                    return new NameImpl(NS, input);
                }
            }));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InternationalString getTitle(Name name) {
        return new SimpleInternationalString(info(name).name());
    }

    @Override
    public InternationalString getDescription(Name name) {
        return new SimpleInternationalString(info(name).description());
    }

    @Override
    public String getVersion(Name name) {
        return "1.0.0";
    }

    @Override
    public Map<String, Parameter<?>> getParameterInfo(Name name) {
        return Maps.uniqueIndex(info(name).inputs(), KEY);
    }

    @Override
    public Map<String, Parameter<?>> getResultInfo(Name name, Map<String, Object> parameters) throws IllegalArgumentException {
        return Maps.uniqueIndex(info(name).outputs(), KEY);
    }

    @Override
    public org.geotools.process.Process create(Name name) {
        return new GrassProcess(name, this);
    }

    @Override
    public boolean supportsProgress(Name name) {
        return false;
    }

    @Override
    public Map<Key, ?> getImplementationHints() {
        return null;
    }

    GrassModuleInfo info(Name name) {
        try {
            return modules.get(name);
        } catch (ExecutionException e) {
            throw Throwables.propagate(e);
        }
    }
}
