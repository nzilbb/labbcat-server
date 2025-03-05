<%@ page info="Layer Managers" isErrorPage="true"
    import = "nzilbb.labbcat.server.servlet.LayerManagers" 
%><%@ include file="../base.jsp" %><%{
    LayerManagers handler = new LayerManagers();
    initializeHandler(handler, request);
    if ("GET".equals(request.getMethod())) {
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
