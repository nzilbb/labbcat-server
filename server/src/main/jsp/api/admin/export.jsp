<%@ page info="Export LaBB-CAT" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.admin.Export" 
%><%@ include file="../../base.jsp" %><%{
    Export handler = new Export(
      getRootDir(), driverName, connectionURL, connectionName, connectionPassword);
    initializeHandler(handler, request, response);
    if ("GET".equals(request.getMethod())) {
      handler.get(
        parseParameters(request),
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
