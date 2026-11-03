<%@ page info="Layer Managers" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.layers.Managers" 
%><%@ include file="../../base.jsp" %><%{
    Managers handler = new Managers();
    initializeHandler(handler, request, response);
    if ("GET".equals(request.getMethod())) {
      handler.get(
        request.getPathInfo(),
        parseParameters(request),
        (headerName)->request.getHeader(headerName),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (fileName)->handler.getContext().responseAttachmentName(fileName),
        (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
