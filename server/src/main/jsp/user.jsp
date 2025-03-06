<%@ page info="User information" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.User" 
    import = "javax.json.Json" 
%><%@ include file="base.jsp" %><%{
    if ("GET".equals(request.getMethod())) { // GET only
      User handler = new User();
      initializeHandler(handler, request);
      handler.get(Json.createGenerator(out));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
