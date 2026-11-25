<%@ page info="Elicitation task steps" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.admin.elicit.Steps" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../../../base.jsp" %><%{
    Steps handler = new Steps();
    initializeHandler(handler, request, response);
    if ("PUT".equals(request.getMethod())) {
      JsonObject json = handler.put(
        request.getPathInfo(),
        request.getInputStream(),
        (status)->response.setStatus(status));
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
