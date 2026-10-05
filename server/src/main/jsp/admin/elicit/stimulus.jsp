<%@ page info="Elicitation task stimulus management" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.admin.elicit.Stimulus" 
    import = "nzilbb.labbcat.server.api.RequestParameters" 
    import = "java.io.File" 
    import = "java.nio.file.Files" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../../base.jsp" %><%{
  Stimulus handler = new Stimulus(new File(getRootDir(), "elicit"));
  initializeHandler(handler, request, response);
  if ("GET".equals(request.getMethod())) {
    JsonObject json = handler.get((status)->response.setStatus(status));
    if (json != null) {
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeObject(json);   
      writer.close();
    }
  } else if ("POST".equals(request.getMethod())) { // POST
    // load multipart request parameters - the implementation depends on the servlet container:
    // Server info something like "Apache Tomcat/9.0.58 (Ubuntu)" or "Apache Tomcat/10.1.36"
    boolean tomcat9 = application.getServerInfo().matches(".*Tomcat/9.*");
    if (tomcat9) {
      %><jsp:include page="../../file-upload-tomcat9.jsp" /><%
        } else {
      %><jsp:include page="../../file-upload-tomcat10.jsp" /><%
    }
    try (RequestParameters parameters =
         (RequestParameters) request.getAttribute("multipart-parameters")) {
      JsonObject json = handler.post(parameters, (status)->response.setStatus(status));
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    } // delete any temporary files
  } else if ("DELETE".equals(request.getMethod())) {
    JsonObject json = handler.delete(request.getPathInfo(), (status)->response.setStatus(status));
    if (json != null) {
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeObject(json);   
      writer.close();
    }
  } else if ("OPTIONS".equals(request.getMethod())) {
    response.addHeader("Allow", "OPTIONS, GET, POST, DELETE");
  } else {
    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }
}%>
