package org.geoserver.security.xauth.web;

import org.apache.wicket.model.IModel;
import org.geoserver.security.web.auth.AuthenticationFilterPanel;
import org.geoserver.security.xauth.XAuthFilterConfig;

public class XAuthFilterPanel extends AuthenticationFilterPanel<XAuthFilterConfig> {

  public XAuthFilterPanel(String id, IModel<XAuthFilterConfig> model) {
    super(id, model);
  }
}
