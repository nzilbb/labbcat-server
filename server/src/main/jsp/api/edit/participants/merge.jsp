<%@ page info="Merge participants" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.edit.participants.Merge" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter"
%><%@ include file="../../../base.jsp" %><%{
  Merge handler = new Merge();
  initializeHandler(handler, request, response);
  if ("POST".equals(request.getMethod())) {
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
}%>
