<%@ page info="Statistics dashboard items" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.dashboard.Statistics" 
    import = "javax.json.Json" 
%><%@ include file="../base.jsp" %><%{
    if ("GET".equals(request.getMethod())) { // GET only
      Statistics handler = new Statistics();
      initializeHandler(handler, request);
      handler.get(
        request.getPathInfo(),
        parseParameters(request),
        (headerName)->request.getHeader(headerName),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (fileName)->{
          ResponseAttachmentName(
            request, response, fileName);
        },
        (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
