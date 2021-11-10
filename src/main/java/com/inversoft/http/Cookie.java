/*
 * Copyright (c) 2021, Inversoft Inc., All Rights Reserved
 */
package com.inversoft.http;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.inversoft.rest.DateTools;

/**
 * Models an HTTP cookie.
 *
 * @author Brian Pontarelli
 */
public class Cookie implements Buildable<Cookie> {
  public String domain;

  public ZonedDateTime expires;

  public boolean httpOnly;

  public Long maxAge;

  public String name;

  public String path;

  public SameSite sameSite;

  public boolean secure;

  public String value;

  public Cookie() {
  }

  public Cookie(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public static List<Cookie> fromRequestHeader(String header) {
    List<Cookie> cookies = new ArrayList<>();
    boolean inName = false, inValue = false;
    char[] chars = header.toCharArray();
    int start = 0;
    String name = null;
    String value = null;
    for (int i = 0; i < header.length(); i++) {
      char c = chars[i];
      if (!inName && !inValue && (Character.isWhitespace(c) || c == ';')) {
        start++;
        continue;
      }

      if (c == '=') {
        name = header.substring(start, i);
        value = "";
        inValue = true;
        inName = false;
        start = i + 1;
      } else if (c == ';') {
        value = header.substring(start, i);
        if (name != null && name.trim().length() > 0 && value.trim().length() > 0) {
          cookies.add(new Cookie(name, value));
        }

        inName = false;
        inValue = false;
        name = null;
        value = null;
        start = 0;
      } else {
        if (!inName && !inValue) {
          inName = true;
          start = i;
        }
      }
    }

    // Handle what's left
    if (inName && start > 0) {
      name = header.substring(start);
    }

    if (inValue && start > 0) {
      value = header.substring(start);
    }

    if (name != null && value != null && name.trim().length() > 0 && value.trim().length() > 0) {
      cookies.add(new Cookie(name, value));
    }

    return cookies;
  }

  public static Cookie fromResponseHeader(String header) {
    Cookie cookie = new Cookie();
    boolean inName = false, inValue = false, inAttributes = false;
    char[] chars = header.toCharArray();
    int start = 0;
    String name = null;
    String value = null;
    for (int i = 0; i < header.length(); i++) {
      char c = chars[i];
      if (!inName && !inValue && (Character.isWhitespace(c) || c == ';')) {
        start++;
        continue;
      }

      if (c == '=') {
        name = header.substring(start, i);
        if (!inAttributes && name.trim().length() == 0) {
          return null;
        }

        value = "";
        inValue = true;
        inName = false;
        start = i + 1;
      } else if (c == ';') {
        if (inName) {
          if (!inAttributes) {
            return null;
          }

          name = header.substring(start, i);
          value = null;
        } else {
          value = header.substring(start, i);
          if (!inAttributes && value.trim().length() == 0) {
            return null;
          }
        }

        if (inAttributes) {
          cookie.addAttribute(name, value);
        } else {
          cookie.name = name;
          cookie.value = value;
        }

        inName = false;
        inValue = false;
        inAttributes = true;
        name = null;
        value = null;
      } else {
        if (!inName && !inValue) {
          inName = true;
          start = i;
        }
      }
    }

    // Handle what's left
    if (inName && start > 0) {
      name = header.substring(start);
    }

    if (inValue && start > 0) {
      value = header.substring(start);
    }

    if (inAttributes) {
      cookie.addAttribute(name, value);
    } else {
      if (name == null || value == null || name.trim().length() == 0 || value.trim().length() == 0) {
        return null;
      }

      cookie.name = name;
      cookie.value = value;
    }

    return cookie;
  }

  public void addAttribute(String name, String value) {
    switch (name.toLowerCase()) {
      case HTTPStrings.CookieAttributes.DomainLower:
        domain = value;
        break;
      case HTTPStrings.CookieAttributes.ExpiresLower:
        expires = DateTools.parse(value);
        break;
      case HTTPStrings.CookieAttributes.HttpOnlyLower:
        httpOnly = true;
        break;
      case HTTPStrings.CookieAttributes.MaxAgeLower:
        try {
          maxAge = Long.parseLong(value);
        } catch (Exception e) {
          // Ignore
        }
        break;
      case HTTPStrings.CookieAttributes.PathLower:
        path = value;
        break;
      case HTTPStrings.CookieAttributes.SameSiteLower:
        try {
          sameSite = SameSite.valueOf(value);
        } catch (Exception e) {
          // Ignore
        }
        break;
      case HTTPStrings.CookieAttributes.SecureLower:
        secure = true;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Cookie)) {
      return false;
    }
    Cookie cookie = (Cookie) o;
    return Objects.equals(domain, cookie.domain) &&
        Objects.equals(expires, cookie.expires) &&
        Objects.equals(httpOnly, cookie.httpOnly) &&
        Objects.equals(maxAge, cookie.maxAge) &&
        Objects.equals(name, cookie.name) &&
        Objects.equals(path, cookie.path) &&
        Objects.equals(secure, cookie.secure) &&
        Objects.equals(value, cookie.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(domain, expires, httpOnly, maxAge, name, path, secure, value);
  }

  public String toRequestHeader() {
    return name + "=" + value;
  }

  public enum SameSite {
    Lax,
    None,
    Strict
  }
}
