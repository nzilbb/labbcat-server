<%@ page info="Upload transcript" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.edit.transcript.Upload" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../../base.jsp" %><%{
      if ("POST".equals(request.getMethod())) { // POST uploads files
        // load multipart request parameters - the implementation depends on the servlet container:
        // Server info something like "Apache Tomcat/9.0.58 (Ubuntu)" or "Apache Tomcat/10.1.36"
        boolean tomcat9 = application.getServerInfo().matches(".*Tomcat/9.*");
        if (tomcat9) {
          %><jsp:include page="../../file-upload-tomcat9.jsp" /><%
        } else {
          %><jsp:include page="../../file-upload-tomcat10.jsp" /><%
        } 
        RequestParameters parameters = (RequestParameters)
        request.getAttribute("multipart-parameters");
        Upload handler = new Upload();
        initializeHandler(handler, request);
        JsonObject json = handler.post(
          parameters, (status)->response.setStatus(status));
        if (json != null) {
          JsonWriter writer = Json.createWriter(response.getWriter());
          writer.writeObject(json);   
          writer.close();
        }
      } else if ("PUT".equals(request.getMethod())) { // PUT finishes processing files
        Upload handler = new Upload();
        initializeHandler(handler, request);
        JsonObject json = handler.put(
          request.getPathInfo(), parseParameters(request), (status)->response.setStatus(status));
        if (json != null) {
          JsonWriter writer = Json.createWriter(response.getWriter());
          writer.writeObject(json);   
          writer.close();
        }
      } else if ("DELETE".equals(request.getMethod())) { // DELETE abandons a POSTed upload
        Upload handler = new Upload();
        initializeHandler(handler, request);
        JsonObject json = handler.delete(
          request.getPathInfo(), (status)->response.setStatus(status));
        if (json != null) {
          JsonWriter writer = Json.createWriter(response.getWriter());
          writer.writeObject(json);   
          writer.close();
        }
      } else if ("OPTIONS".equals(request.getMethod())) {
        response.addHeader("Allow", "OPTIONS, POST, PUT, DELETE");
      } else {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      }
}%>
