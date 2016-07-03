package org.geoserver.security.xauth;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.RandomStringUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.GeoServerRoleConverter;
import org.geoserver.security.GeoServerRoleService;
import org.geoserver.security.GeoServerRoleStore;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.GeoServerUserGroupService;
import org.geoserver.security.GeoServerUserGroupStore;
import org.geoserver.security.config.PasswordPolicyConfig;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.filter.GeoServerRequestHeaderAuthenticationFilter;
import org.geoserver.security.impl.GeoServerRole;
import org.geoserver.security.impl.GeoServerUser;
import org.geoserver.security.impl.GeoServerUserGroup;
import org.geoserver.security.impl.RoleCalculator;
import org.geoserver.security.password.PasswordValidator;
import org.geoserver.security.validation.PasswordPolicyException;
import org.geotools.util.logging.Logging;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class XAuthFilter extends GeoServerRequestHeaderAuthenticationFilter {

  static final Logger LOG = Logging.getLogger(XAuthFilter.class);

  List<String> newUserGroups;
  boolean autoProvisionUsers;

  @Override
  public void initializeFromConfig(SecurityNamedServiceConfig config) throws IOException {
    super.initializeFromConfig(config);

    XAuthFilterConfig xauthConfig = (XAuthFilterConfig) config;
    newUserGroups = xauthConfig.getNewUserGroups();
    autoProvisionUsers = xauthConfig.isAutoProvisionUsers();

    // set converter manually because even though we use role source == RoleService
    // we still need to pull roles out of the header to synchronize
    setConverter(GeoServerExtensions.bean(GeoServerRoleConverter.class));    
  }

  @Override
  protected String getPreAuthenticatedPrincipalName(HttpServletRequest request) {
    String username = super.getPreAuthenticatedPrincipalName(request);
    if (autoProvisionUsers && !Strings.isNullOrEmpty(username)) {
      // ensure the user exists
      try {
        GeoServerUserGroupService ugService = findUserGroupService();
        GeoServerUser user = ugService.getUserByUsername(username);
        if (user == null) {
          // add it
          if (ugService.canCreateStore()) {
            user = createNewUser(username, ugService);            
          }
          else {
            LOG.warning("Unable to synchronize read-only user group service");
          }
        }
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Error getting user group service, unable to synchronize", e);
      }
    }
    return username;
  }

  GeoServerUserGroupService findUserGroupService() throws IOException {
    GeoServerSecurityManager secMgr = getSecurityManager();
    String name = getUserGroupServiceName();
    if (name == null) {
      Set<String> names = secMgr.listUserGroupServices();
      if (names.size() == 1) {
        name = names.iterator().next();
      }
    }

    if (name != null) {
      return secMgr.loadUserGroupService(name);
    }

    
    LOG.warning("Unable to determine user group service, configure one on this filter");
    return null;
  }

  synchronized GeoServerUser createNewUser(String username, GeoServerUserGroupService service) throws IOException {
    GeoServerUserGroupStore store = service.createStore();
    try {
      GeoServerUser user = store.createUserObject(username, generatePassword(service), true);
      store.addUser(user);

      // groups
      for (String g : newUserGroups) {
        GeoServerUserGroup group = service.getGroupByGroupname(g);
        if (group == null) {
          group = service.createGroupObject(g, true);
        }

        store.associateUserToGroup(user, group);
      }

      store.store();
      return user;
    } catch (PasswordPolicyException | IllegalStateException e) {
      LOG.log(Level.SEVERE, "Unable to generate new user, password error", e);
    }
    return null;
  }

  String generatePassword(GeoServerUserGroupService ugService) throws IOException {
    GeoServerSecurityManager secMgr = getSecurityManager(); 
    PasswordValidator validator = secMgr.loadPasswordValidator(ugService.getPasswordValidatorName());
    //GeoServerPasswordEncoder encoder = secMgr.loadPasswordEncoder(GeoServerPBEPasswordEncoder.class, true, false);
    
    PasswordPolicyConfig policy = validator.getConfig();

    int length = policy.getMaxLength();
    if (length == -1) {
      length = policy.getMinLength()*2;
    }
    if (length == 0) {
      length = 8;
    }
    
    for (int i = 0; i < 100; i++) {
      String passwd = RandomStringUtils.random(length, true, policy.isDigitRequired());
      try {
        validator.validatePassword(passwd.toCharArray()); // TODO: don't use string
        return passwd;
        //return encoder.encodePassword(passwd, null);
      }
      catch(PasswordPolicyException e) {
      }
    }

    throw new IllegalStateException("Unable to machine generate password");
  }

  @Override
  protected Collection<GeoServerRole> getRolesFromRoleService(HttpServletRequest request, String principal) throws IOException {
    Collection<GeoServerRole> header = super.getRolesFromHttpAttribute(request, principal);
    GeoServerRoleService roleService = roleService();

    if (autoProvisionUsers) {
      Collection<GeoServerRole> service = super.getRolesFromRoleService(request, principal);

      RoleComparison compare = new RoleComparison(service, header);
      if (!compare.equal) {
        // synchronize the user group service
        if (roleService.canCreateStore()) {
          synchronized (this) {
            GeoServerRoleStore store = roleService.createStore();
            for (GeoServerRole r : compare.remove()) {
              store.disAssociateRoleFromUser(r, principal);
            }
            for (GeoServerRole r : compare.add()) {
              // TODO: should we assume roles always present in database?
              //if (store.getRoleByName(r.getAuthority()) == null) {
              //  store.addRole(r);
              //}
              store.associateRoleToUser(r, principal);
            }
            store.store();
          }
        } else {
          LOG.warning("Unable to synchronize read-only role service");
        }
      }
    }

    GeoServerUser user = new GeoServerUser(principal);
    user.setAuthorities(ImmutableSet.copyOf(header));

    return new RoleCalculator(roleService).calculateRoles(user);
  }

  GeoServerRoleService roleService() throws IOException {
    // TODO: copied from parent, factor out upstream into callablae method
    boolean useActiveService = getRoleServiceName()==null ||
        getRoleServiceName().trim().length()==0;

    return useActiveService ?
        getSecurityManager().getActiveRoleService() :
        getSecurityManager().loadRoleService(getRoleServiceName());
  }

  static class RoleComparison implements Comparator<GeoServerRole> {
    boolean equal;
    Set<GeoServerRole> from;
    Set<GeoServerRole> to;
    
    RoleComparison(Collection<GeoServerRole> r1, Collection<GeoServerRole> r2) {
      if (r1.size() != r2.size()) {
        equal = false;
      }

      if (r1.equals(r2)) {
        equal = true;
      }

      if (!equal) {
        from = new TreeSet<>(this);
        from.addAll(r1);

        to = new TreeSet<>(this);
        to.addAll(r2);
      }
    }

    List<GeoServerRole> add() {
      List<GeoServerRole> l = new ArrayList<>(to);
      l.removeAll(from);
      return l;
    }

    List<GeoServerRole> remove() {
      List<GeoServerRole> l = new ArrayList<>(from);
      l.removeAll(to);
      return l;
    }

    @Override
    public int compare(GeoServerRole o1, GeoServerRole o2) {
      if (o1 == o2) {
        return 0;
      }
      if (o1.getAuthority().equals(o2.getAuthority())) {
        return 0;
      }

      return o1.getAuthority().compareTo(o2.getAuthority());
    }
  }
}
