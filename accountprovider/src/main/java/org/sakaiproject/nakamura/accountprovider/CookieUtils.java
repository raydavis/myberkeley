/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.accountprovider;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.codec.binary.Base64;
import org.sakaiproject.nakamura.api.servlet.HttpOnlyCookie;
import org.sakaiproject.nakamura.util.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.SignatureException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CookieUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(CookieUtils.class);
  public static final String SEPARATOR = "%";

  public static String encodeField(String field) throws UnsupportedEncodingException {
    final byte[] bytes = field.getBytes(CharEncoding.UTF_8);
    final String escapedField = new Base64(0, new byte[0], true).encodeToString(bytes);
    return escapedField;
  }

  public static String decodeField(String field) throws UnsupportedEncodingException {
    byte[] fieldUtf8 = new Base64(0, new byte[0], true).decode(field);
    String unescapedField = new String(fieldUtf8, CharEncoding.UTF_8);
    return unescapedField;
  }

  public static void addCookie(HttpServletResponse response, String name, String payload, String secret, long timeToLive) {
    final long expires = System.currentTimeMillis() + timeToLive;
    try {
      final String message = String.valueOf(expires) + SEPARATOR + encodeField(payload);
      final String hmac = Signature.calculateRFC2104HMAC(message, secret);
      final String token = hmac + SEPARATOR + message;
      Cookie cookie = new HttpOnlyCookie(name, token);
      cookie.setMaxAge(-1);
      cookie.setPath("/");
      response.addCookie(cookie);
      response.addHeader("Cache-Control", "no-cache=\"set-cookie\" ");
      response.addDateHeader("Expires", expires);
    } catch (SignatureException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      throw new Error(e);
    } catch (UnsupportedEncodingException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      throw new Error(e);
    }
  }

  public static void clearCookie(HttpServletResponse response, String name) {
    Cookie c = new HttpOnlyCookie(name, "");
    c.setMaxAge(0);
    c.setPath("/");
    response.addCookie(c);
  }

  public static String getPayload(HttpServletRequest request, HttpServletResponse response, String name, String secret) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookie != null) {
          String cookieName = cookie.getName();
          if (cookieName.equals(name)) {
            String cookieValue = cookie.getValue();
            LOGGER.info("Got cookie value = {}", cookieValue);
            String[] signedParts = cookieValue.split(SEPARATOR, 2);
            try {
              final String hmac = Signature.calculateRFC2104HMAC(signedParts[1], secret);
              if (hmac.equals(signedParts[0])) {
                String[] expirationAndPayload  = signedParts[1].split(SEPARATOR, 2);
                long expiration = Long.parseLong(expirationAndPayload[0]);
                if (System.currentTimeMillis() <= expiration) {
                  return decodeField(expirationAndPayload[1]);
                } else {
                  LOGGER.info("Clearing expired cookie");
                  clearCookie(response, cookieName);
                }
              } else {
                LOGGER.warn("Bad signature; clearing cookie");
                clearCookie(response, cookieName);
              }
            } catch (SignatureException e) {
              LOGGER.error(e.getLocalizedMessage(), e);
            } catch (UnsupportedEncodingException e) {
              LOGGER.error(e.getLocalizedMessage(), e);
            }
          }
        }
      }
    }
    return null;
  }
}
