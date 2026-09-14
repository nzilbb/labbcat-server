<%@ page info="Elicitation task elicited attributes" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.admin.elicit.Attributes" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../../base.jsp" %><%{
    Attributes handler = new Attributes();
    initializeHandler(handler, request, response);
    if ("GET".equals(request.getMethod())) {
      JsonObject json = handler.get(
        request.getPathInfo(),
        (status)->response.setStatus(status));
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
