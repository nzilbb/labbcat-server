<%@ page info="Transcript search" isErrorPage="true"
    contentType = "text/plain;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.ResultsDictionary" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    if (!"GET".equals(request.getMethod()) && !"POST".equals(request.getMethod())) { // GET/POST only
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else {
      ResultsDictionary handler = new ResultsDictionary();
      initializeHandler(handler, request);
      handler.get(
        parseParameters(request),
        response.getOutputStream(),
        (fileName)->{
          ResponseAttachmentName(
            request, response, fileName);
        },
        (status)->response.setStatus(status));
    }
}%>
