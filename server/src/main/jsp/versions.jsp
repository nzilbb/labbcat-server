<%@ page info="Version information" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.Versions" 
    import = "javax.json.Json" 
%><%@ include file="base.jsp" %><%{
    if ("GET".equals(request.getMethod())) { // GET only
      Versions handler = new Versions();
      initializeHandler(handler, request);
      handler.get(Json.createGenerator(out), (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
