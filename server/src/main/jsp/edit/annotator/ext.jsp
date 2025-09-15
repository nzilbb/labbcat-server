<%@ page info="Annotator Extension" isErrorPage="true"
    import = "nzilbb.labbcat.server.api.edit.annotator.ExtWebApp" 
    import = "nzilbb.ag.automation.util.AnnotatorDescriptor"
    import = "java.util.HashMap"
    import = "java.util.Timer"
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%!
HashMap<String,AnnotatorDescriptor> activeAnnotators = new HashMap<String,AnnotatorDescriptor>();
// cached descriptors are removed after a while so they don't linger in memory
Timer annotatorDeactivator = new Timer("edit/annotator/ext");
%><%@ include file="../../base.jsp" %><%{
    if ("GET".equals(request.getMethod())
        || "POST".equals(request.getMethod())
        || "PUT".equals(request.getMethod())
        || "DELETE".equals(request.getMethod())) { // GET/POST/PUT/DELETE
      ExtWebApp handler = new ExtWebApp(activeAnnotators, annotatorDeactivator);
      initializeHandler(handler, request);
      handler.get(
        request.getMethod(),
        request.getRequestURI(),
        request.getPathInfo(),
        request.getQueryString(),
        (headerName)->request.getHeader(headerName),
        request.getInputStream(),
        response.getOutputStream(),
        (contentType)->response.setContentType(contentType),
        (encoding)->response.setCharacterEncoding(encoding),
        (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, POST, PUT, DELETE");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
