package org.geoserver.security.xauth;

import org.geoserver.security.config.RequestHeaderAuthenticationFilterConfig;

import java.util.ArrayList;
import java.util.List;

public class XAuthFilterConfig extends RequestHeaderAuthenticationFilterConfig {

  static final String USER_HEADER_DEFAULT = "x-auth-user";
  static final String ROLE_HEADER_DEFAULT = "x-auth-roles";
  
  Boolean autoProvisionUsers = true;

  /**
   * groups to add new users to when created on-demand 
   */
  List<String> newUserGroups = new ArrayList<>();

  public XAuthFilterConfig() {
    setPrincipalHeaderAttribute(USER_HEADER_DEFAULT);
    setRolesHeaderAttribute(ROLE_HEADER_DEFAULT);
    setRoleSource(PreAuthenticatedUserNameRoleSource.RoleService); 
  }

  private Object readResolve() {
    if (getPrincipalHeaderAttribute() == null) {
      setPrincipalHeaderAttribute(USER_HEADER_DEFAULT);
    }
    if (getRolesHeaderAttribute() == null) {
      setRolesHeaderAttribute(ROLE_HEADER_DEFAULT);
    }
    if (getRoleSource() == null) {
      setRoleSource(PreAuthenticatedUserNameRoleSource.RoleService);
    }
    if (newUserGroups == null) {
      newUserGroups = new ArrayList<>();
    }
    if (autoProvisionUsers == null) {
      autoProvisionUsers = true;
    }
    return this;
  }

  public Boolean isAutoProvisionUsers() {
    return autoProvisionUsers;
  }

  public void setAutoProvisionUsers(Boolean autoProvisionUsers) {
    this.autoProvisionUsers = autoProvisionUsers;
  }

  public List<String> getNewUserGroups() {
    return newUserGroups;
  }

  public void setNewUserGroups(List<String> groups) {
    newUserGroups = new ArrayList<>(groups);
  }
}
