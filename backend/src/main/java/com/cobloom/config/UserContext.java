package com.cobloom.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserContext {
  public static Long userId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      throw new IllegalStateException("Unauthorized");
    }
    return Long.valueOf(auth.getPrincipal().toString());
  }
}
