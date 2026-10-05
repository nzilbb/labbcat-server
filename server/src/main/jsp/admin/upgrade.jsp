<%@ page info="Upgrade LaBB-CAT" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.admin.Upgrade" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    Upgrade handler = new Upgrade(
      getRootDir(), driverName, connectionURL, connectionName, connectionPassword);
    initializeHandler(handler, request, response);
    JsonObject json = null;
    if ("GET".equals(request.getMethod())) {
      json = handler.get((status)->response.setStatus(status));
    } else if ("POST".equals(request.getMethod())) {
      // load multipart request parameters - the implementation depends on the servlet container:
      // Server info something like "Apache Tomcat/9.0.58 (Ubuntu)" or "Apache Tomcat/10.1.36"
      boolean tomcat9 = application.getServerInfo().matches(".*Tomcat/9.*");
      if (tomcat9) {
      log("tomcat9");
        %><jsp:include page="/api/file-upload-tomcat9.jsp" /><%
      } else {
        %><jsp:include page="/api/file-upload-tomcat10.jsp" /><%
      } 
      try (RequestParameters parameters = (RequestParameters)
           request.getAttribute("multipart-parameters")) {
        log("parameters: " + parameters);
        json = handler.post(parameters, (status)->response.setStatus(status));
      } // delete any temporary files
    } else if ("PUT".equals(request.getMethod())) {
      json = handler.put(
        request.getPathInfo(),
        parseParameters(request),
        (status)->response.setStatus(status));
    } else if ("DELETE".equals(request.getMethod())) {
      json = handler.delete(
        request.getPathInfo(),
        (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, POST, PUT, DELETE");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
    if (json != null) {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeObject(json);   
      writer.close();
    }
}%>
