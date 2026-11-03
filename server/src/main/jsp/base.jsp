<%@ page isErrorPage="true"
    trimDirectiveWhitespaces="true"
    import = "java.io.File" 
    import = "java.io.FileInputStream"
    import = "java.io.PrintWriter" 
    import = "java.io.StringWriter" 
    import = "java.io.IOException" 
    import = "java.io.OutputStream" 
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
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonObjectBuilder" 
    import = "javax.json.JsonValue" 
    import = "javax.xml.parsers.*"
    import = "javax.xml.xpath.*"
    import = "nzilbb.labbcat.server.db.*"
    import = "nzilbb.labbcat.server.api.APIRequestContext"
    import = "nzilbb.labbcat.server.api.APIRequestHandler"
    import = "nzilbb.labbcat.server.api.RequestParameters"
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
        if (connectionURL == null) { // tomcat 10
          connectionURL = xpath.evaluate("//Resource/@url", doc);
          log("connectionURL not found in Realm, trying Resource...");
        }
        connectionName = xpath.evaluate("//Realm/@connectionName", doc);
        if (connectionName == null) { // tomcat 10
          connectionName = xpath.evaluate("//Resource/@username", doc);
        }
        connectionPassword = xpath.evaluate("//Realm/@connectionPassword", doc);
        if (connectionPassword == null) { // tomcat 10
          connectionPassword = xpath.evaluate("//Resource/@password", doc);
        }
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
   * @param response The HTTP response.
   * @return The handler.
   */
  APIRequestHandler initializeHandler(
    APIRequestHandler handler, HttpServletRequest request, HttpServletResponse response) {
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
         * Get the base parth for the servlet.
         * @return Get the base parth for the servlet, or null if it can't be determined.
         */
        public String getServletPath() {
          return request.getServletPath();
        }
        
        /**
         * The ID of the logged-in user.
         * @return The ID of the logged-in user, on null if no user is logged in.
         */
        public String getUser() {
          return request.getRemoteUser();
        }
        
        /**
         * The IP/host name of the user's connection.
         * @return The IP/host name of the user's connection, or null if not available.
         */
        public String getUserHost() {
          return request.getRemoteHost();
        }
        
        /**
         * Returns the path portion of the request URL.
         * @return The path portion of the request URL.
         */
        public String getPathInfo() {
          return request.getPathInfo();
        }
        
        /**
         * Provides access to a given header of the request.
         * @param name The name of the header.
         * @return The request header.
         */
        public String getRequestHeader(String name) {
          return request.getHeader(name);
        }
        
        /**
         * Add the fiven given header to the response.
         * @param name Header name.
         * @param value Header value.
         */
        public void addResponseHeader(String name, String value) {
          response.addHeader(name, value);
        }
        
        /**
         * Provides access to a given attribute of the request.
         * @param name The name of the attribute.
         * @return The request attribute.
         */
        public Object getRequestAttribute(String name) {
          return request.getAttribute(name);
        }
        
        /**
         * Sets the value of a given attribute of the request.
         * @param name The name of the attribute.
         * @param value The value for the attribute.
         */
        public void setRequestAttribute(String name, Object value) {
          request.setAttribute(name, value);
        }

        /**
         * Provides access to a given attribute of the user session.
         * @param name The name of the attribute.
         * @return The session attribute.
         */
        public Object getSessionAttribute(String name) {
          return request.getSession().getAttribute(name);
        }
        
        /**
         * Sets the value of a given attribute of the user session.
         * @param name The name of the attribute.
         * @param value The value for the attribute.
         */
        public void setSessionAttribute(String name, Object value) {
          request.getSession().setAttribute(name, value);
        }
        
        /**
         * Provides access to a given attribute of the serlvet context.
         * @param name The name of the attribute.
         * @return The context attribute.
         */
        public Object getServletContextAttribute(String name){
          return request.getServletContext().getAttribute(name);
        }

        /**
         * Sets the value of a given attribute of the serlvet context.
         * @param name The name of the attribute.
         * @param value The new value for the attribute.
         */
        public void setServletContextAttribute(String name, Object value) {
          request.getServletContext().setAttribute(name, value);
        }
        
        /**
         * Provides the local file corresponding to the given path within the servlet context.
         * @param path The path within the servlet context.
         * @return The local path corresponding to the given path.
         */
        public String getRealPath(String path) {
          return request.getServletContext().getRealPath(path);
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
              return isUserInRole(role, db);
            } finally {
              db.close();
            }
          } catch (Exception x) {
            log("isUserInRole: " + x);
            x.printStackTrace(System.err);
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

        /**
         * Log a message.
         * @param message
         */
        public void servletLog(String message) {
          log(message);
        }
        
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
   * Returns the root of the persistent file system.
   * @return The "files" directory.
   */
  public File getRootDir() {
    return new File(getServletContext().getRealPath("/"));
  } // end of getFilesDir()
  
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

  /**
   * Encode the given Throwable as a JSON failure response and write it as the request reponse.
   * @param t
   * @param response
   */
  public void jsonError(Throwable t, HttpServletResponse response) {
    OutputStream out = null;
    try {
      out = response.getOutputStream();
      String message = ""+t.getMessage();
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      JsonObjectBuilder exception = Json.createObjectBuilder()
        .add("type", t.getClass().getSimpleName())
        .add("message", message)
        .add("stackTrace", sw.toString());
      if (t.getCause() != null) {
        sw = new StringWriter();
        pw = new PrintWriter(sw);
        t.getCause().printStackTrace(pw);
        exception.add("cause", Json.createObjectBuilder()
                      .add("type", t.getCause().getClass().getSimpleName())
                      .add("message", ""+t.getCause().getMessage())
                      .add("stackTrace", sw.toString()));
      }
      
      JsonObjectBuilder result = Json.createObjectBuilder()
        .add("title", Optional.ofNullable(title).orElse(""))
        .add("version", Optional.ofNullable(version).orElse(""))
        .add("code", 1) // TODO deprecate?
        .add("errors", Json.createArrayBuilder().add(message))
        .add("exception", exception)
        .add("messages", Json.createArrayBuilder())
        .add("model", JsonValue.NULL);
      JsonObject json = result.build();
      try {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      } catch(Exception x2) {}
      out.write(json.toString().getBytes());
      out.flush();
    } catch(Exception x1) {
      try {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      if (out != null) {
        try {
          out.write(t.toString().getBytes());
          t.printStackTrace(new PrintWriter(out));
          out.flush();
        } catch(Exception exception) {
          System.err.println("base.jsp ("+title+"): failed to report exception: " + t);
          t.printStackTrace(System.err);
        }
      } else {
        System.err.println("base.jsp ("+title+"): could not report exception: " + t);
        t.printStackTrace(System.err);
      }
    }
  } // end of jsonError()

%>
