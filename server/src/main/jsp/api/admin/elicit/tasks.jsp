<%@ page info="Elicitation tasks" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.admin.elicit.Tasks" 
%><%@ include file="../../../base.jsp" %><%{
    Tasks handler = new Tasks();
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
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, POST, PUT, DELETE");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
