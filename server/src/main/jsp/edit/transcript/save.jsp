<%@ page info="Save uploaded transcript" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.edit.transcript.Save" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../../base.jsp" %><%{
  try {
    if ("POST".equals(request.getMethod()) // POST only
        || "GET".equals(request.getMethod())) { // but secretly GET too
      Save handler = new Save();
      initializeHandler(handler, request);
      JsonObject json = handler.post(
        parseParameters(request), (status)->response.setStatus(status));
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, POST");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
  } catch (Throwable t) {
    jsonError(t, response);
  }
}%>