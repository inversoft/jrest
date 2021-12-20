/*
 * Copyright (c) 2021, Inversoft Inc., All Rights Reserved
 */
package com.inversoft.net;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author Daniel DeGroff
 */
public class EncodingTools {
  private static final byte[] ATTR_CHAR = {'!', '#', '$', '&', '+', '-', '.', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '^', '_', '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '|', '~'};

  private static final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

  private EncodingTools() {
  }

  /**
   * Basic escape of double quotes and back slash.
   *
   * @param s the string to escape
   * @return an escaped string.
   *
   * @see <a href="https://tools.ietf.org/html/rfc2616#section-2.2">https://tools.ietf.org/html/rfc2616#section-2.2</a>
   */
  public static String escapedQuotedString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * Encode a string using <a href="http://tools.ietf.org/html/rfc5987">RFC 5987</a> standard.
   * <p>
   *
   * @param s the input string
   * @return an encoded string
   *
   * @see <a href="https://stackoverflow.com/a/11307864">https://stackoverflow.com/a/11307864</a>
   */
  public static String rfc5987_encode(String s) {
    final byte[] s_bytes = s.getBytes(StandardCharsets.UTF_8);
    final int len = s_bytes.length;
    final StringBuilder sb = new StringBuilder(len << 1);

    for (int i = 0; i < len; ++i) {
      final byte b = s_bytes[i];
      if (Arrays.binarySearch(ATTR_CHAR, b) >= 0) {
        sb.append((char) b);
      } else {
        sb.append('%');
        sb.append(DIGITS[0x0f & (b >>> 4)]);
        sb.append(DIGITS[b & 0x0f]);
      }
    }

    return sb.toString();
  }
}
