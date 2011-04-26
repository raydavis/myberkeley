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

package edu.berkeley.myberkeley.util;

import org.apache.xmlbeans.impl.util.Base64;
import org.junit.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class EncodedMD5GeneratorTest {

  @Test
  public void generateMD5() throws NoSuchAlgorithmException, IOException {
    String password = "foo";
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] md5 = md.digest(password.getBytes());
    byte[] md5Base64 = Base64.encode(md5);
    System.out.println(new String(md5Base64));
  }

}
