package org.geoserver.security.xauth.web;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.GeoServerUserGroupService;
import org.geoserver.security.impl.GeoServerUser;
import org.geoserver.security.impl.GeoServerUserGroup;
import org.geoserver.security.web.auth.AuthenticationFilterPanel;
import org.geoserver.security.web.role.RoleServiceChoice;
import org.geoserver.security.web.user.UserGroupPaletteFormComponent;
import org.geoserver.security.web.usergroup.UserGroupServiceChoice;
import org.geoserver.security.xauth.XAuthFilterConfig;
import org.geoserver.web.GeoServerApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class XAuthFilterPanel extends AuthenticationFilterPanel<XAuthFilterConfig> {

  WebMarkupContainer autoProvisionContainer;
  WebMarkupContainer groupContainer;

  public XAuthFilterPanel(String id, final IModel<XAuthFilterConfig> model) {
    super(id, model);

    add(new TextField("principalHeaderAttribute").setRequired(true));
    add(new TextField("rolesHeaderAttribute").setRequired(true));
    add(new AjaxCheckBox("autoProvisionUsers") {
      @Override
      protected void onUpdate(AjaxRequestTarget target) {
        autoProvisionContainer.setVisible(getModelObject());
        target.add(autoProvisionContainer);
      }
    });

    autoProvisionContainer = new WebMarkupContainer("autoProvisionContainer");
    add(autoProvisionContainer);
    autoProvisionContainer.setOutputMarkupId(true);
    autoProvisionContainer.setOutputMarkupPlaceholderTag(true);
    autoProvisionContainer.setVisible(model.getObject().isAutoProvisionUsers());

    autoProvisionContainer.add(new UserGroupServiceChoice("userGroupServiceName").add(new AjaxFormComponentUpdatingBehavior("change") {
      @Override
      protected void onUpdate(AjaxRequestTarget target) {
        updateGroupList(getFormComponent().getDefaultModelObjectAsString(), model, target);
      }
    }));
    autoProvisionContainer.add(new RoleServiceChoice("roleServiceName"));

    groupContainer = new WebMarkupContainer("groupContainer");
    autoProvisionContainer.add(groupContainer);
    groupContainer.setOutputMarkupPlaceholderTag(true);
    groupContainer.setOutputMarkupId(true);

    updateGroupList(model.getObject().getUserGroupServiceName(), model, null);
  }

  void updateGroupList(String ugServiceName, IModel<XAuthFilterConfig> configModel, AjaxRequestTarget target) {
    List<String> groups = configModel.getObject().getNewUserGroups();
    
    groupContainer.addOrReplace(ugServiceName != null ? 
        new UserGroupPaletteFormComponent("groups", new SelectedGroupsModel(ugServiceName, groups), ugServiceName, GeoServerUser.createAnonymous()) : 
        new WebMarkupContainer("groups"));

    if (target != null) {
      target.add(groupContainer);
    }
  }

  @Override
  public void doSave(XAuthFilterConfig config) throws Exception {
    UserGroupPaletteFormComponent groups = (UserGroupPaletteFormComponent) groupContainer.get("groups");
    config.setNewUserGroups(
        groups.getSelectedGroups().stream().map(GeoServerUserGroup::getGroupname).collect(Collectors.toList())
    );
    super.doSave(config);
  }

  static class SelectedGroupsModel implements IModel<List<GeoServerUserGroup>> {

    List<GeoServerUserGroup> groups;

    SelectedGroupsModel(String ugServiceName, List<String> groupNames) {
      if (groupNames.isEmpty()) {
        setObject(new ArrayList<>());
        return;
      }

      try {
        GeoServerSecurityManager secMgr = GeoServerApplication.get().getSecurityManager();
        GeoServerUserGroupService service = secMgr.loadUserGroupService(ugServiceName);

        setObject(new ArrayList<>(groupNames.stream().map(g -> {
          try {
            return service.getGroupByGroupname(g);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }).collect(Collectors.toList())));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }      
    }

    @Override
    public List<GeoServerUserGroup> getObject() {
      return groups;
    }

    @Override
    public void setObject(List<GeoServerUserGroup> object) {
      this.groups = object;
    }

    @Override
    public void detach() {

    }
  } 
}
