package org.sakaiproject.nakamura.api.accountprovider;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface ForeignPrincipalService {

  public void addForeignPrincipal(HttpServletResponse response, String userId);

  public String getForeignPrincipal(HttpServletRequest request, HttpServletResponse response);

  public void dropForeignPrincipal(HttpServletResponse response);

}