<%@ page info="System Attributes" isErrorPage="true"
    import = "nzilbb.labbcat.server.servlet.SystemAttributes" 
%><%@ include file="base.jsp" %><%{
    SystemAttributes handler = new SystemAttributes();
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
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
