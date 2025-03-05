<%@ page info="Serialize transcript fragments" isErrorPage="true"
    import = "nzilbb.labbcat.server.servlet.SerializeFragments" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    if (!"GET".equals(request.getMethod()) && !"POST".equals(request.getMethod())) { // GET/POST only
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else {
      SerializeFragments handler = new SerializeFragments();
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
    }
}%>
