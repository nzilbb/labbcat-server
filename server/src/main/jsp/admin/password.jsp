<%@ page info="Set user password" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.admin.Password" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    if ("PUT".equals(request.getMethod())) { // PUT only
      Password handler = new Password();
      initializeHandler(handler, request);
      JsonObject json = handler.put(
        request.getInputStream(), (status)->response.setStatus(status));
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, PUT");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
