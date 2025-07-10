<%@ page info="Participant attributes" isErrorPage="true"
    contentType = "text/csv;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.participant.Attributes" 
    import = "javax.json.Json" 
%><%@ include file="../base.jsp" %><%{
    if ("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())) { // GET/POST only
      Attributes handler = new Attributes();
      initializeHandler(handler, request);
      handler.get(parseParameters(request), response.getOutputStream(), (fileName)->{
          ResponseAttachmentName(
            request, response, fileName);
        }, (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, POST");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
