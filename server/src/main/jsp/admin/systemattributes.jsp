<%@ page info="System Attributes" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.AdminSystemAttributes" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    if (!"PUT".equals(request.getMethod()) && !"GET".equals(request.getMethod())) { // PUT/GET
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else {
      AdminSystemAttributes handler = new AdminSystemAttributes();
      initializeHandler(handler, request);
      if ("PUT".equals(request.getMethod())) {
        JsonObject json = handler.put(
          request.getInputStream(), (status)->response.setStatus(status));
        if (json != null) {
          JsonWriter writer = Json.createWriter(response.getWriter());
          writer.writeObject(json);   
          writer.close();
        }
      } else { // GET
        handler.get(
          Json.createGenerator(out), (status)->response.setStatus(status));
      }
    }
}%>
