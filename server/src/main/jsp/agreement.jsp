<%@ page info="Corpus access agreement" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.Agreement" 
%><%@ include file="base.jsp" %><%{
    Agreement handler = new Agreement();
    initializeHandler(handler, request);
    if ("GET".equals(request.getMethod())) {
      handler.get(        
        request.getPathInfo(),
        (path)->new File(getServletContext().getRealPath(path)),
        response.getOutputStream(),
        (expires)->response.setDateHeader("Expires", expires),
        (contentType)->response.setContentType(contentType),
        (encoding)->response.setCharacterEncoding(encoding),
        (status)->response.setStatus(status));
    } else if ("POST".equals(request.getMethod())) {
      // load multipart request parameters - the implementation depends on the servlet container:
      // Server info something like "Apache Tomcat/9.0.58 (Ubuntu)" or "Apache Tomcat/10.1.36"
      boolean tomcat9 = application.getServerInfo().matches(".*Tomcat/9.*");
      if (tomcat9) {
        %><jsp:include page="file-upload-tomcat9.jsp" /><%
      } else {
        %><jsp:include page="file-upload-tomcat10.jsp" /><%
      } 
      RequestParameters parameters = (RequestParameters)
        request.getAttribute("multipart-parameters");
      handler.post(
        request.getPathInfo(),
        (path)->new File(getServletContext().getRealPath(path)),
        parameters,
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (encoding)->response.setCharacterEncoding(encoding),
        (status)->response.setStatus(status));
    } else if ("PUT".equals(request.getMethod())) {
      handler.put(
        request.getPathInfo(),
        (path)->new File(getServletContext().getRealPath(path)),
        request.getInputStream(),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (encoding)->response.setCharacterEncoding(encoding),
        (status)->response.setStatus(status));
    } else if ("DELETE".equals(request.getMethod())) {
      handler.delete(
        request.getPathInfo(),
        (path)->new File(getServletContext().getRealPath(path)),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (encoding)->response.setCharacterEncoding(encoding),
        (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", handler.options());
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
