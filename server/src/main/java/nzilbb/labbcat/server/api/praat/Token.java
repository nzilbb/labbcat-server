//
// Copyright 2025 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
//    (at your option) any later version.
//
//    LaBB-CAT is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with LaBB-CAT; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//

package nzilbb.labbcat.server.api.praat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.util.IO;
import nzilbb.labbcat.server.api.APIRequestHandler;

/**
 * <tt>/a</tt> : Token for Praat browser integration.
 * @author Robert Fromont
 */
public class Token extends APIRequestHandler {
  
  /**
   * Constructor
   */
  public Token() {
  } // end of constructor
  
  /**
   * Returns a token for Praat browser integration.
   * @param requestHeaders Access to HTTP request headers.
   * @param realPath Access to local paths within the servlet context.
   * @param cookie Access to the value of a given cookie.
   * @return JSON-encoded object representing the response.
   */
  public JsonObject get(
    UnaryOperator<String> requestHeaders, Function<String,File> realPath,
    Function<String,String> cookie) {
    
    // Praat integration uses an external program that does not have
    // access to the browser's Authorization header or session cookies.
    // The only way to give that program the access to URLs that the client
    // has is to pass an Authorization token to it.
    
    // We only reveal the token if the client knows the nonce,
    // which is derived from the server's user-interface/en/main-....js
    // script file name - i.e. the UI was compiled for this server -
    // and the referring page is also from this server
    
    String token = null;
    // load UI index.html
    File index = realPath.apply("/user-interface/en/index.html");
    try {
      String html = IO.InputStreamToString(new FileInputStream(index));
      
      // get the '<script src="main-....js" type="module">' script tag in it
      Pattern scriptPattern = Pattern.compile("<script src=\"main([^>]*)\\.js\"");
      Matcher scriptMatcher = scriptPattern.matcher(html);
      if (scriptMatcher.find()) {
        String nonce = scriptMatcher.group(1);
        String referer = requestHeaders.apply("Referer");
        String ifMatch = requestHeaders.apply("If-Match");
        if (referer != null // but only if referred from our own pages
            && referer.startsWith(context.getBaseUrl())
            && ifMatch != null // and only if nonce is present ...
            && ifMatch.equals(nonce) // ... and correct
          ) {
          String jsessionid = cookie.apply("JSESSIONID");
          if (jsessionid != null) { // the cookie is present
            token = "Cookie JSESSIONID="+jsessionid;
          }
        } // planets are aligned
      } // can parse UI
    } catch (IOException x) {
      context.servletLog(x.toString());
    }
    return successResult(token, null);  
  }
} // end of class Token
