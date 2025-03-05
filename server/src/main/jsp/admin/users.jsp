<%@ page info="Users" isErrorPage="true"
    import = "nzilbb.labbcat.server.servlet.AdminUsers" 
%><%@ include file="../base.jsp" %><%{
    AdminUsers handler = new AdminUsers();
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
    } else if ("POST".equals(request.getMethod())) {
      handler.post(
        request.getInputStream(),
        (headerName)->request.getHeader(headerName),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (status)->response.setStatus(status));
    } else if ("PUT".equals(request.getMethod())) {
      handler.put(
        request.getInputStream(),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (status)->response.setStatus(status));
    } else if ("DELETE".equals(request.getMethod())) {
      handler.delete(
        request.getPathInfo(),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (status)->response.setStatus(status));
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
