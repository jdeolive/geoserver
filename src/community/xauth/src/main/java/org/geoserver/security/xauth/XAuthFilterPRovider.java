package org.geoserver.security.xauth;

import org.geoserver.config.util.XStreamPersister;
import org.geoserver.security.config.PreAuthenticatedUserNameFilterConfig.PreAuthenticatedUserNameRoleSource;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.filter.AbstractFilterProvider;
import org.geoserver.security.filter.GeoServerSecurityFilter;

public class XAuthFilterProvider extends AbstractFilterProvider {

  @Override
  public void configure(XStreamPersister xp) {
    super.configure(xp);
    //xp.getXStream().alias("basicAuthentication", BasicAuthenticationFilterConfig.class);
  }

  @Override
  public Class<? extends GeoServerSecurityFilter> getFilterClass() {
    return XAuthFilter.class; 
  }

  @Override
  public GeoServerSecurityFilter createFilter(SecurityNamedServiceConfig config) {
    return new XAuthFilter();
  }
}
