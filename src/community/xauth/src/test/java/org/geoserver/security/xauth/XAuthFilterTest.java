package org.geoserver.security.xauth;
import org.geoserver.security.GeoServerRoleService;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.GeoServerUserGroupService;
import org.geoserver.security.GeoServerUserGroupStore;
import org.geoserver.security.auth.AuthenticationCache;
import org.geoserver.security.config.PasswordPolicyConfig;
import org.geoserver.security.impl.GeoServerRole;
import org.geoserver.security.impl.GeoServerUser;
import org.geoserver.security.validation.PasswordValidatorImpl;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.expect;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class XAuthFilterTest {

  @Before
  public void setup() {
    SecurityContextHolder.getContext().setAuthentication(null);
  }

  @Test
  public void sanity() throws Exception {
    GeoServerSecurityManager secMgr = createNiceMock(GeoServerSecurityManager.class);
    
    AuthenticationCache authCache = createNiceMock(AuthenticationCache.class);
    expect(secMgr.getAuthenticationCache()).andReturn(authCache).anyTimes();

    GeoServerRoleService roleService = createNiceMock(GeoServerRoleService.class);
    expect(roleService.getRolesForUser("bob")).andReturn(Collections.emptySortedSet()).anyTimes();

    GeoServerUser bob = createNiceMock(GeoServerUser.class);

    GeoServerUserGroupService ugService = createNiceMock(GeoServerUserGroupService.class);
    expect(ugService.getUserByUsername("bob")).andReturn(bob).anyTimes();

    expect(secMgr.loadUserGroupService("default")).andReturn(ugService).anyTimes();
    expect(secMgr.loadRoleService("default")).andReturn(roleService).anyTimes();

    replay(authCache, ugService, roleService, secMgr);

    XAuthFilterConfig config = new XAuthFilterConfig();
    config.setUserGroupServiceName("default");
    config.setRoleServiceName("default");
    config.setAutoProvisionUsers(false);

    XAuthFilter filter = new XAuthFilter();
    filter.setSecurityManager(secMgr);
    filter.initializeFromConfig(config);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("x-auth-user", "bob");
    req.addHeader("x-auth-roles", "ADMIN");
    
    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth);
    
    assertEquals("bob", auth.getPrincipal());
    assertEquals(2, auth.getAuthorities().size());

    assertTrue(auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter("ADMIN"::equals)
        .findAny()
        .isPresent());
    assertTrue(auth.getAuthorities().stream().filter(GeoServerRole.AUTHENTICATED_ROLE::equalsWithoutUserName).findAny().isPresent());
  }

  @Test
  public void testProvisionFlag() throws Exception {
    GeoServerSecurityManager secMgr = createNiceMock(GeoServerSecurityManager.class);

    PasswordValidatorImpl validator = new PasswordValidatorImpl(secMgr);
    validator.setConfig(new PasswordPolicyConfig());
    expect(secMgr.loadPasswordValidator(anyObject(String.class))).andReturn(validator).anyTimes();

    AuthenticationCache authCache = createNiceMock(AuthenticationCache.class);
    expect(secMgr.getAuthenticationCache()).andReturn(authCache).anyTimes();

    GeoServerRoleService roleService = createNiceMock(GeoServerRoleService.class);
    expect(roleService.getRolesForUser(anyObject(String.class))).andReturn(Collections.emptySortedSet()).anyTimes();

    GeoServerUser bob = new GeoServerUser("bob");

    GeoServerUserGroupService ugService = createNiceMock(GeoServerUserGroupService.class);

    GeoServerUserGroupStore ugStore = createNiceMock(GeoServerUserGroupStore.class);
    expect(ugStore.createUserObject(eq("bob"), anyObject(String.class), eq(true))).andReturn(bob).anyTimes();
    ugStore.addUser(eq(bob));
    expectLastCall().once();

    expect(ugService.canCreateStore()).andReturn(true).anyTimes();
    expect(ugService.createStore()).andReturn(ugStore).anyTimes();

    expect(secMgr.loadUserGroupService("default")).andReturn(ugService).anyTimes();
    expect(secMgr.loadRoleService("default")).andReturn(roleService).anyTimes();

    replay(authCache, ugService, ugStore, roleService, secMgr);

    XAuthFilterConfig config = new XAuthFilterConfig();
    config.setUserGroupServiceName("default");
    config.setRoleServiceName("default");
    config.setAutoProvisionUsers(true);

    XAuthFilter filter = new XAuthFilter();
    filter.setSecurityManager(secMgr);
    filter.initializeFromConfig(config);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("x-auth-user", "bob");
    req.addHeader("x-auth-roles", "ADMIN");

    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth);

    verify(ugStore);
  }
}
