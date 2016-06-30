package org.geoserver.security.xauth.web;

import org.geoserver.security.web.auth.AuthenticationFilterPanelInfo;
import org.geoserver.security.xauth.XAuthFilter;
import org.geoserver.security.xauth.XAuthFilterConfig;

public class XAuthFilterPanelInfo extends AuthenticationFilterPanelInfo<XAuthFilterConfig, XAuthFilterPanel> {

  public XAuthFilterPanelInfo() {
    setServiceClass(XAuthFilter.class);
    setServiceConfigClass(XAuthFilterConfig.class);
    setComponentClass(XAuthFilterPanel.class);
  }
}
