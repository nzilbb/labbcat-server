<%@ page info="System Attributes" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.AdminSystemAttributes" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
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
    } else if ("GET".equals(request.getMethod())) { // GET
      handler.get(
        Json.createGenerator(out), (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, PUT");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
