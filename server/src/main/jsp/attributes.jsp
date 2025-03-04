<%@ page info="User information" isErrorPage="true"
    contentType = "text/csv;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.Attributes" 
    import = "javax.json.Json" 
%><%@ include file="base.jsp" %><%{
    if (!"GET".equals(request.getMethod())) { // GET only
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else {
      Attributes handler = new Attributes();
      initializeHandler(handler, request);
      handler.get(parseParameters(request), response.getOutputStream(), (fileName)->{
          ResponseAttachmentName(
            request, response, fileName);
        }, (status)->response.setStatus(status));
    }
}%>
