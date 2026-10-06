<%@ page info="Upload CSV annotations for time intervals" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.edit.annotations.Intervals" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter"
    import = "nz.ac.canterbury.ling.Labbcat"
%><%@ include file="../../../base.jsp" %><%{
      if ("POST".equals(request.getMethod())) { // POST uploads files
        // load multipart request parameters - the implementation depends on the servlet container:
        // Server info something like "Apache Tomcat/9.0.58 (Ubuntu)" or "Apache Tomcat/10.1.36"
        boolean tomcat9 = application.getServerInfo().matches(".*Tomcat/9.*");
        if (tomcat9) {
          %><jsp:include page="../../../file-upload-tomcat9.jsp" /><%
        } else {
          %><jsp:include page="../../../file-upload-tomcat10.jsp" /><%
        } 
        try (RequestParameters parameters = (RequestParameters)
             request.getAttribute("multipart-parameters")) {
          Intervals handler = new Intervals();
          initializeHandler(handler, request, response);
          JsonObject json = handler.post(
            parameters, (status)->response.setStatus(status));
          if (json != null) {
            JsonWriter writer = Json.createWriter(response.getWriter());
            writer.writeObject(json);   
            writer.close();
          }
        } // delete any temporary files
      } else if ("OPTIONS".equals(request.getMethod())) {
        response.addHeader("Allow", "OPTIONS, POST");
      } else {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      }
}%>
