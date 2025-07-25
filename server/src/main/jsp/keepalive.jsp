<%@ page info="Keep session/task alive" isErrorPage="true"
    contentType = "text/plain;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.KeepAlive" 
%><%@ include file="base.jsp" %><%{
    if ("GET".equals(request.getMethod())) {
      KeepAlive handler = new KeepAlive();
      initializeHandler(handler, request);
      handler.get(
        parseParameters(request),
        (refreshSeconds)->response.addHeader("Refresh", refreshSeconds.toString()));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
