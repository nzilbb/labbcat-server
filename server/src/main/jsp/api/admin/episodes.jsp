<%@ page info="Transcript episodes" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.admin.Episodes" 
    import = "java.io.File" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
    %><%@ include file="../../base.jsp" %><%{
  Episodes handler = new Episodes(new File(getRootDir(), "files"));
  initializeHandler(handler, request, response);
  JsonObject json = null;
  if ("GET".equals(request.getMethod())) {
    json = handler.get(
      request.getPathInfo(),
      (status)->response.setStatus(status));
  } else if ("PUT".equals(request.getMethod())) {
    json = handler.put(
      request.getInputStream(),
      (status)->response.setStatus(status));
  } else if ("OPTIONS".equals(request.getMethod())) {
    response.addHeader("Allow", "OPTIONS, GET, PUT");
  } else {
    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }
  if (json != null) {
    JsonWriter writer = Json.createWriter(response.getWriter());
    writer.writeObject(json);   
    writer.close();
  }
}%>
