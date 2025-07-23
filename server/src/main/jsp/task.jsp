<%@ page info="Task information" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.Task" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="base.jsp" %><%{
    if ("GET".equals(request.getMethod())) {
      Task handler = new Task();
      initializeHandler(handler, request);
      JsonObject json = handler.get(
        request.getPathInfo(), parseParameters(request),
        (status)->response.setStatus(status));
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    } else if ("DELETE".equals(request.getMethod())) {
      Task handler = new Task();
      initializeHandler(handler, request);
      JsonObject json = handler.delete(
        request.getPathInfo(), parseParameters(request),
        (status)->response.setStatus(status));
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, DELETE");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
