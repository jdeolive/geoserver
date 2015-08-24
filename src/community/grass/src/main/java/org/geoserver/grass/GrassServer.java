package org.geoserver.grass;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * Client for Python GRASS server.
 */
public class GrassServer {

    static Logger LOG = Logging.getLogger(GrassServer.class);

    String host;
    HttpClient http;

    public GrassServer() {
        this("http://localhost:8000");
    }

    public GrassServer(String host) {
        this.host = host;
        http = connect();
    }

    public boolean connected() {
        return http != null;
    }

    HttpClient connect() {
        http = new HttpClient();

        // try a connection
        try {
            if (http.executeMethod(new GetMethod(host)) != HttpStatus.SC_ACCEPTED) {
                return http;
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, format("Failed to connect to GRASS server at %s", host), e);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public List<String> list() throws IOException {
        GetMethod req = new GetMethod(format("%s/list", host));
        int rc = http.executeMethod(req);
        if (rc == HttpStatus.SC_OK) {
            JSONObject obj = JSONObject.fromObject(req.getResponseBodyAsString());


            return Lists.transform(obj.getJSONArray("modules"), new Function<Object, String>() {
                @Nullable
                @Override
                public String apply(Object input) {
                    JSONObject m = (JSONObject) input;
                    return m.getString("name");
                }
            });
        }
        else {
            String msg = format(
                "Error listing grass modules, server returned %d: %s", rc, req.getResponseBodyAsString());
            throw new IOException(msg);
        }
    }

    public GrassModuleInfo info(String name) throws IOException {
        GetMethod req = new GetMethod(format("%s/info/%s", host, name));
        int rc = http.executeMethod(req);
        switch(rc) {
            case HttpStatus.SC_OK:
                return new GrassModuleInfo(JSONObject.fromObject(req.getResponseBodyAsString()));

            case HttpStatus.SC_NOT_FOUND:
                return null;
            default:
                String msg = format("Error looking up grass module %s, server returned %d: %s",
                    name, rc, req.getResponseBodyAsString());
                throw new IOException(msg);
        }
    }

    public JSONObject run(String name, JSONObject input) throws IOException {
        PostMethod req = new PostMethod(format("%s/run/%s", host, name));
        req.setRequestEntity(new StringRequestEntity(input.toString(), "application/json", Charsets.UTF_8.name()));

        int rc = http.executeMethod(req);
        switch(rc) {
            case HttpStatus.SC_OK:
                return JSONObject.fromObject(req.getResponseBodyAsString());

            case HttpStatus.SC_NOT_FOUND:
                throw new IOException("No such grass module: " + name);

            default:
                String msg = format("Error running grass process %s, server returned %d: %s",
                    name, rc, req.getResponseBodyAsString());
                throw new IOException(msg);
        }
    }
}
