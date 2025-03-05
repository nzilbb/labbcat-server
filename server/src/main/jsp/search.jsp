<%@ page info="Transcript search" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.Search" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="base.jsp" %><%{
    if (!"GET".equals(request.getMethod())) { // GET only
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else {
      Search handler = new Search();
      initializeHandler(handler, request);
      JsonObject json = handler.get(
        parseParameters(request), (status)->response.setStatus(status));
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    }
}%>
