<%@ page info="Annotator Configuration" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.admin.Annotators" 
    import = "nzilbb.labbcat.server.api.RequestParameters" 
    import = "java.io.File" 
    import = "java.nio.file.Files" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%!
    File tempDir;
%><%{
    if (tempDir == null || !tempDir.exists()) {
      try {
        tempDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
      } catch (IOException x) {
        log("tempDir: " + x);
      }
    }
    Annotators handler = new Annotators(tempDir);
    initializeHandler(handler, request);
    if ("GET".equals(request.getMethod())) { // GET
      handler.get(
        (contentType)->response.setContentType(contentType),
        response.getOutputStream());
    } else if ("POST".equals(request.getMethod())) { // POST
      // load multipart request parameters - the implementation depends on the servlet container:
      // Server info something like "Apache Tomcat/9.0.58 (Ubuntu)" or "Apache Tomcat/10.1.36"
      boolean tomcat9 = application.getServerInfo().matches(".*Tomcat/9.*");
      if (tomcat9) {
        %><jsp:include page="../file-upload-tomcat9.jsp" /><%
          } else {
        %><jsp:include page="../file-upload-tomcat10.jsp" /><%
      } 
      RequestParameters parameters =
        (RequestParameters) request.getAttribute("multipart-parameters");
      JsonObject json = handler.post(
        parameters,
        (status)->response.setStatus(status),
        getAnnotatorDir());
      if (json != null) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, POST");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
