<%@ page info="Set user password" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.AdminPassword" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    if (!"PUT".equals(request.getMethod())) { // PUT only
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else {
      AdminPassword handler = new AdminPassword();
      initializeHandler(handler, request);
      JsonObject json = handler.put(
        request.getInputStream(), (status)->response.setStatus(status));
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    }
}%>
