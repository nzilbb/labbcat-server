<%@ page info="Transcript search" isErrorPage="true"
    contentType = "text/plain;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.results.Dictionary" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="../base.jsp" %><%{
    if ("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())) { // GET/POST only
      Dictionary handler = new Dictionary();
      initializeHandler(handler, request);
      handler.get(
        parseParameters(request),
        response.getOutputStream(),
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
