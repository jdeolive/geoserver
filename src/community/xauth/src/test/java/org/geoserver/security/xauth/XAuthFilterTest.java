package org.geoserver.security.xauth;
import org.geoserver.security.GeoServerRoleService;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.auth.AuthenticationCache;
import org.geoserver.security.impl.GeoServerRole;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class XAuthFilterTest {

  @Test
  public void sanity() throws Exception {
    GeoServerSecurityManager secMgr = createNiceMock(GeoServerSecurityManager.class);
    
    AuthenticationCache authCache = createNiceMock(AuthenticationCache.class);
    expect(secMgr.getAuthenticationCache()).andReturn(authCache).anyTimes();

    GeoServerRoleService roleService = createNiceMock(GeoServerRoleService.class);
    expect(roleService.getRolesForUser("bob")).andReturn(Collections.emptySortedSet()).anyTimes();

    expect(secMgr.getActiveRoleService()).andReturn(roleService).anyTimes();

    replay(authCache, roleService, secMgr);

    XAuthFilterConfig config = new XAuthFilterConfig();
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
}
