<%@ page info="Media files" isErrorPage="true"
    import = "nzilbb.labbcat.server.content.Files" 
%><%@ include file="base.jsp" %><%{
  Files handler = new Files(new File(getRootDir(), "files"));
  initializeHandler(handler, request, response);
  if ("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())) {
    handler.get(
      request.getPathInfo(),
      parseParameters(request),
      (path)->new File(getServletContext().getRealPath(path)),
      (fileName)->handler.getContext().responseAttachmentName(fileName),
      (contentType)->response.setContentType(contentType),
      (status)->response.setStatus(status),
      (forwardTo)->{
        try {
          getServletContext().getNamedDispatcher(forwardTo).forward(request, response);
        } catch(Exception ex) {
          log("Could not forward to " + forwardTo + " : " + ex);
        }
      });
  } else if ("OPTIONS".equals(request.getMethod())) {
    response.addHeader("Allow", "OPTIONS, GET, POST");
  } else {
    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }
}%>
