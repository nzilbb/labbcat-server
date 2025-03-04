<%@ page info="User information" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.User" 
    import = "javax.json.Json" 
%><%@ include file="base.jsp" %><%{
    if (!"GET".equals(request.getMethod())) { // GET only
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else {
      String user = request.getRemoteUser();
      User handler = new User();
      initializeHandler(handler, request);
      handler.get(Json.createGenerator(out));
    }
}%>
