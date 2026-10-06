<%@ page info="Elicitation task definition" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.elicit.Verify" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter"
%><%@ include file="../../base.jsp" %><%{
      if ("GET".equals(request.getMethod())) {
        Verify handler = new Verify();
        initializeHandler(handler, request, response);
        JsonObject json = handler.get(
          parseParameters(request),
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
