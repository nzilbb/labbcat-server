<%@ page info="Annotation Graph Store" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.edit.Store" 
    import = "nzilbb.labbcat.server.api.RequestParameters" 
    import = "java.io.InputStream" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    RequestParameters parameters = new RequestParameters();
    if ("GET".equals(request.getMethod())) { // no request body, parse URL parameters
      parameters = parseParameters(request);
    } else if ("POST".equals(request.getMethod()) // request body will be parameters
               // unless it's a request that has a JSON-encoded body
               && !request.getPathInfo().toLowerCase().endsWith("savetranscript")) {
      // load multipart request parameters - the implementation depends on the servlet container:
      // Server info something like "Apache Tomcat/9.0.58 (Ubuntu)" or "Apache Tomcat/10.1.36"
      boolean tomcat9 = application.getServerInfo().matches(".*Tomcat/9.*");
      if (tomcat9) {
        %><jsp:include page="../file-upload-tomcat9.jsp" /><%
          } else {
        %><jsp:include page="../file-upload-tomcat10.jsp" /><%
      } 
      parameters = (RequestParameters) request.getAttribute("multipart-parameters");
    }
    if ("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())) { // GET/POST
      Store handler = new Store();
      initializeHandler(handler, request);
      JsonObject json = handler.get(
        request.getRequestURI(), request.getMethod(), request.getPathInfo(),
        request.getQueryString(), parameters, request.getInputStream(),
        (status)->response.setStatus(status),
        (redirectUrl)->{
          try {
            response.sendRedirect(redirectUrl);
          } catch(IOException ex) {
            log("Could not redirect to " + redirectUrl + " : " + ex);
          }
        });
      if (json != null) {
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
