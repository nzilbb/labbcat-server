<%@ page info="Serialize transcripts" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.annotation.Data" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    if ("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())) { // GET/POST only
      Data handler = new Data();
      initializeHandler(handler, request);
      handler.post(
        parseParameters(request),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (fileName)->{
          ResponseAttachmentName(
            request, response, fileName);
        },
        (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, POST");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
