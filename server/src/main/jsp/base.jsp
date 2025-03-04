<%@ page isErrorPage="true"
    trimDirectiveWhitespaces="true"
    import = "java.io.File" 
    import = "java.io.FileInputStream"
    import = "java.io.IOException" 
    import = "java.io.UnsupportedEncodingException"
    import = "java.net.MalformedURLException"
    import = "java.net.URL"
    import = "java.net.URLEncoder"
    import = "java.sql.Connection"
    import = "java.sql.DriverManager"
    import = "java.sql.PreparedStatement"
    import = "java.sql.ResultSet"
    import = "java.sql.SQLException"
    import = "java.util.Enumeration"
    import = "java.util.Locale"
    import = "java.util.Optional"
    import = "java.util.ResourceBundle"
    import = "javax.xml.parsers.*"
    import = "javax.xml.xpath.*"
    import = "nzilbb.labbcat.server.db.*"
    import = "nzilbb.labbcat.server.servlet.APIRequestContext"
    import = "nzilbb.labbcat.server.servlet.APIRequestHandler"
    import = "nzilbb.labbcat.server.servlet.RequestParameters"
    import = "nzilbb.sql.ConnectionFactory"
    import = "nzilbb.sql.mysql.MySQLConnectionFactory"
    import = "nzilbb.util.IO"
    import = "nzilbb.util.SemanticVersionComparator"
    import = "org.w3c.dom.*"
    import = "org.xml.sax.*"
%><%!
    
  /**
   * Load http paramaters into a map.
   * @param request
   * @return A map of parameter names to the String values.
   */
  public RequestParameters parseParameters(HttpServletRequest request) {
    RequestParameters parameters = new RequestParameters();
    Enumeration<String> names = request.getParameterNames();
    while(names.hasMoreElements()) {
      String name = names.nextElement();
      parameters.put(name, request.getParameterValues(name));
    }
    return parameters;
  } // end of parseParameters()
  
  protected String driverName;
  protected String connectionURL;
  protected String connectionName;
  protected String connectionPassword;
  protected ConnectionFactory connectionFactory;
  protected String title;
  protected String version;
  private String lastLanguage = "en";
  private Locale lastLocale = Locale.UK;
  private ResourceBundle lastBundle;
    
  /** 
   * Initialise the servlet by loading the database connection settings.
   */
  public void init() {
    try {
      log(getClass().getSimpleName()+".init()");

      title = getServletInfo();

      // get version info
      File versionTxt = new File(getServletContext().getRealPath("/version.txt"));
      if (versionTxt.exists()) {
        try {
          version = IO.InputStreamToString(new FileInputStream(versionTxt));
        } catch(IOException exception) {
          log("Can't read version.txt: " + exception);
        }
      }

      // get database connection info
      File contextXml = new File(getServletContext().getRealPath("/META-INF/context.xml"));
      if (contextXml.exists()) { // get database connection configuration from context.xml
        Document doc = DocumentBuilderFactory.newInstance()
          .newDocumentBuilder().parse(new InputSource(new FileInputStream(contextXml)));
            
        // locate the node(s)
        XPath xpath = XPathFactory.newInstance().newXPath();
        driverName = "com.mysql.cj.jdbc.Driver";
        connectionURL = xpath.evaluate("//Realm/@connectionURL", doc);
        connectionName = xpath.evaluate("//Realm/@connectionName", doc);
        connectionPassword = xpath.evaluate("//Realm/@connectionPassword", doc);
        connectionFactory = new MySQLConnectionFactory(
          connectionURL, connectionName, connectionPassword);

        // ensure it's registered with the driver manager
        Class.forName(driverName).getConstructor().newInstance();
      } else {
        log("Configuration file not found: " + contextXml.getPath());
      }
    } catch (Exception x) {
      log("failed", x);
    } 
  }

  /**
   * Initialize the given request handler.
   * @param handler The API request handler.
   * @param request The HTTP request, from which the locale might be inferred.
   * @return The handler.
   */
  APIRequestHandler initializeHandler(APIRequestHandler handler, HttpServletRequest request) {
    handler.init(new APIRequestContext() {
        
        /**
         * Access the title of the request endpoint.
         * @return The title of the endpoint.
         */
        public String getTitle() { return title; }
        
        /**
         * Determine the version of the server software.
         * @return The server version.
         */
        public String getVersion() { return version; }

        /**
         * Get the base URL for the server.
         * @return The base URL for the server, or null if it can't be determined.
         */
        public String getBaseUrl() {
          return inferBaseUrl(request);
        }
  
        /**
         * The ID of the logged-in user.
         * @return The ID of the logged-in user, on null if no user is logged in.
         */
        public String getUser() {
          return request.getRemoteUser();
        }
        
        /**
         * Determines whether the logged-in user is in the given role.
         * @param role The desired role.
         * @return true if the user is in the given role, false otherwise.
         */
        public boolean isUserInRole(String role) {
          try {
            Connection db = connectionFactory.newConnection();
            try {
              // load user groups
              if (request.getSession().getAttribute("security") == null) {
                String user = request.getRemoteUser();
                if (user == null) { // not using authentication
                  request.getSession().setAttribute("security", "none");
                  request.getSession().setAttribute("group_view", Boolean.TRUE);
                  request.getSession().setAttribute("group_edit", Boolean.TRUE);
                  request.getSession().setAttribute("group_admin", Boolean.TRUE);
                } else { // using authentication
                  PreparedStatement sqlUserGroups = db.prepareStatement(
                    "SELECT role_id FROM role WHERE user_id = ?");
                  sqlUserGroups.setString(1, user);
                  ResultSet rstUserGroups = sqlUserGroups.executeQuery();
                  while (rstUserGroups.next()) {
                    request.getSession().setAttribute(
                      "group_" + rstUserGroups.getString("role_id"), Boolean.TRUE);
                  } // next group
                  rstUserGroups.close();
                  sqlUserGroups.close();
                  
                  // check what kind of security we're using
                  PreparedStatement sqlUser = db.prepareStatement(
                    "SELECT reset_password FROM miner_user WHERE user_id = ?");
                  sqlUser.setString(1, user);
                  ResultSet rstUser = sqlUser.executeQuery();
                  if (rstUser.next()) {
                    // this user id is in the user table - this means we're
                    // using JDBCRealm security to connect to our own DB
                    request.getSession().setAttribute("security", "JDBCRealm");
                    if (rstUser.getInt("reset_password") == 1) {
                      request.setAttribute("reset_password", Boolean.TRUE);
                    }
                  } else {
                    // this user id is not in the user table - this means
                    // we're using some other security mechanism - probably
                    // LDAP via JNDI
                    request.getSession().setAttribute("security", "JNDIRealm");
                  } // user is in user table
                  rstUser.close();
                  sqlUser.close();
                } // using authentication
              } // security not set yet, must be logging on
              
              return request.getSession().getAttribute("group_" + role) != null;
            } finally {
              db.close();
            }
          } catch (Exception x) {
            log("isUserInRole: " + x);
            return false;
          }
        }
  
        /**
         * Access the value of an instance-wide named parameter.
         * @param name The name of the parameter.
         * @return The value of the named parameter.
         */
        public String getInitParameter(String name) {
          return getServletContext().getInitParameter(name);
        }
        
        /**
         * Generates an instance-wide notification that an underlying object has been updated,
         * and cached versions of that object should be flushed. 
         * @param name Name of the object that has been updated.
         */
        public void cacheNotification(String name) {
          // servlet context attribute
          // use a timestamp so servlets can know if the notification is old
          getServletContext().setAttribute(name+" dirty", new java.util.Date());
        }
  
        /**
         * Access the localization resources for the correct locale.
         * @return The localization resources for the correct local.
         */
        public ResourceBundle getResourceBundle() {
          String language = request.getHeader("Accept-Language");
          if (language == null) language = lastLanguage;
          if (language == null) language = "en";
          language = language
            // if multiple are specified, use the first one TODO process all possibilities?
            .replaceAll(",.*","")
            // and ignore q-factor weighting
            .replaceAll(";.*","");
          // fall back to English if they don't care
          if (language.equals("*")) language = "en";
          Locale locale = lastLocale; // keep a local locale for thread safety
          ResourceBundle resources = lastBundle;
          if (!language.equals(lastLanguage)) {
            // is it just a language code ("en")? or does it include th country ("en-NZ")?
            int dash = language.indexOf('-');
            if (dash < 0) {
              locale = new Locale(language);
            } else {
              locale = new Locale(language.substring(0, dash), language.substring(dash+1));
            }
            resources = ResourceBundle.getBundle(
              "nzilbb.labbcat.server.locale.Resources", locale);
          }
          lastLanguage = language;
          lastLocale = locale;
          lastBundle = resources;
          return resources;
        }
  
        /**
         * Accesses a database connection factory.
         * @return An object that can provide database connections.
         */
        public ConnectionFactory getConnectionFactory() { return connectionFactory; }
      });
    return handler;
  }

  String baseUrl;
  /**
   * Determine the baseUrl for the server.
   * @param request The request.
   * @return The baseUrl.
   */
  protected String inferBaseUrl(HttpServletRequest request) {
    if (baseUrl == null) {
      if (Optional.ofNullable(System.getenv("LABBCAT_BASE_URL")).orElse("").length() > 0) {
        // get it from the environment variable
        baseUrl = System.getenv("LABBCAT_BASE_URL");
      } else if (request.getSession() != null
                 && request.getSession().getAttribute("baseUrl") != null) {
        // get it from the session
        baseUrl = request.getSession().getAttribute("baseUrl").toString();
      } else if (Optional.ofNullable(getServletContext().getInitParameter("baseUrl"))
                 .orElse("").length() > 0) {
        // get it from the webapp configuration
        baseUrl = getServletContext().getInitParameter("baseUrl");
      } else { // infer it from the request itself
        try {
          URL url = new URL(request.getRequestURL().toString());
          baseUrl = url.getProtocol() + "://"
            + url.getHost() + (url.getPort() < 0?"":":"+url.getPort())
            + ("/".equals(
                 getServletContext().getContextPath())?""
               :getServletContext().getContextPath());
        } catch(MalformedURLException exception) {
          baseUrl = request.getRequestURI().replaceAll("/api/store/.*","");
        }
      }
    }
    return baseUrl;
  } // end of baseUrl()

  /**
   * Sets the Content-Disposition header of the given Response correctly for saving a file
   * to the given name. 
   * <p> This should handle special characters/spaces in the file name correctly.
   * @param response The response to set the header of.
   * @param fileName The file name to save the response body as.
   */
  public void ResponseAttachmentName(
    HttpServletRequest request, HttpServletResponse response, String fileName) {
    if (fileName == null) return;
    fileName = IO.SafeFileNameUrl(fileName);
    String onlyASCIIFileName = IO.OnlyASCII(fileName)
      .replace(",","-"); // Chrome/Edge don't like commas
    String onlyASCIIFileNameNoQuotes = onlyASCIIFileName
      .replace(" ","_").replace(";","_");
    
    if (fileName.indexOf(' ') >= 0 // contains spaces
        || fileName.indexOf(';') >= 0 // contains semicolon
        || !fileName.equals(onlyASCIIFileName)) { // contains non-ascii
      try {
        fileName = "\""+URLEncoder.encode(fileName, "UTF-8").replace('+',' ')+"\"";
      } catch(UnsupportedEncodingException exception) {
        fileName = "\""+fileName+"\"";
      }
    } else {
      try {
        fileName = URLEncoder.encode(fileName, "UTF-8").replace('+',' ');
      } catch(UnsupportedEncodingException exception) {
      }
    }
    if (onlyASCIIFileName.indexOf(' ') >= 0 || onlyASCIIFileName.indexOf(';') >= 0){
      onlyASCIIFileName = "\""+onlyASCIIFileName+"\"";
    }
    
    // are we being called by the nzilbb.labbcat R package or by java (jsendpraat)?
    String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("");
    String labbcatRVersion = null;
    if (userAgent.startsWith("labbcat-R")) {
      String[] parts = userAgent.split("/");
      if (parts.length > 1) {
        labbcatRVersion = parts[1];
      }
    }
    try {
      if (userAgent != null
          && (userAgent.startsWith("Java/") // plain Java connection (probably jsendpraat)
              || (labbcatRVersion != null // nzilbb.labbcat R package
                  // and version <= 1.3-0
                  && new SemanticVersionComparator().compare(labbcatRVersion, "1.3-0") <= 0))) {
        // specify only 'filename' without quotes
        response.addHeader(
          "Content-Disposition", "attachment; filename="+onlyASCIIFileNameNoQuotes);
      } else if ("jsendpraat 20240927.1325".equals(userAgent)) {
        response.addHeader( // this jsendpraat version prefers filename* second...
          "Content-Disposition", "attachment; filename="+onlyASCIIFileName+"; filename*="+fileName);
      } else {
        // MDN recommends not to use URLEncoder.encode
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition#as_a_response_header_for_the_main_body
        // TODO we can use User-Agent to send un-URL-encoded responses to Safari only
        response.addHeader(
          "Content-Disposition", "attachment; filename*="+fileName+"; filename="+onlyASCIIFileName);
      }
      // send headers immediately, so that the browser shows the 'save' prompt
      response.getOutputStream().flush();
    }
    catch(IOException exception) {
      log("base.jsp:ResponseAttachmentName - " + exception);
    }
  } // end of ResponseAttachmentName()
  
  /**
   * Returns the root of the persistent file system.
   * @return The "files" directory.
   */
  public File getFilesDir() {
    return new File(getServletContext().getRealPath("/files"));
  } // end of getFilesDir()
  
  /**
   * Returns the location of the annotators directory.
   * @return The annotator installation directory.
   */
  public File getAnnotatorDir() {
    File dir = new File(getFilesDir(), "annotators");
    if (!dir.exists()) dir.mkdir();
    return dir;
  } // end of getAnnotatorDir()   

  /**
   * Returns the location of the transcribers directory.
   * @return The transcriber installation directory.
   */
  public File getTranscriberDir() {
    File dir = new File(getFilesDir(), "transcribers");
    if (!dir.exists()) dir.mkdir();
    return dir;
  } // end of getTranscriberDir()   

%>
