package org.geoserver.web.websphere;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.geoserver.filters.GeoServerFilter;
import org.geotools.util.logging.Logging;

/**
 * Filter to get around issue with WebSphere that not properly handling relative redirects.
 * <p>
 * This causes failures in wicket as it uses relative redirects in many places. This issue is 
 * commonly found in many other frameworks. For example 
 * {@link https://jira.sakaiproject.org/browse/SAK-14219}
 * </p>
 * @author Justin Deoliveira, OpenGeo
 */
public class WebSphereFilter implements GeoServerFilter {

    static Logger LOGGER = Logging.getLogger("org.geoserver.web.was");

    public void init(FilterConfig filterConfig) throws ServletException {
    }
    
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String servletPath = httpRequest.getServletPath();
            if (servletPath != null && servletPath.startsWith("/web")) {
                response = new ResponseWrapper((HttpServletResponse) response, httpRequest);
            }
        }
        chain.doFilter(request, response);
    }
    
    public void destroy() {
    }

    static class ResponseWrapper extends HttpServletResponseWrapper {
        HttpServletRequest request;

        public ResponseWrapper(HttpServletResponse response, HttpServletRequest request) {
            super(response);
            this.request = request;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            if (location != null && location.startsWith("?")) {
                String newLocation = request.getContextPath() + "/web" + location;
                LOGGER.fine(String.format("Changing redirect '%s' to '%s'", location, newLocation));
                location = newLocation;
            }
            super.sendRedirect(location);
        }
    }
}
