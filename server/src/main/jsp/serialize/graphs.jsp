<%@ page info="Serialize transcripts" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.serialize.Graphs" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    if ("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())) { // GET/POST only
      Graphs handler = new Graphs();
      initializeHandler(handler, request);
      handler.get(
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
