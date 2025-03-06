<%@ page info="Process with Praat" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.Store" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="base.jsp" %><%{
    if ("GET".equals(request.getMethod())) { // GET only
      Store handler = new Store();
      initializeHandler(handler, request);
      JsonObject json = handler.get(
        request.getRequestURI(), request.getMethod(), request.getPathInfo(),
        request.getQueryString(), parseParameters(request),
        (status)->response.setStatus(status),
        (redirectUrl)->{
          try {
            response.sendRedirect(redirectUrl);
          } catch(IOException ex) {
            log("Could not redirect to " + redirectUrl + " : " + ex);
          }
        });
      if (json != null) {
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(json);   
        writer.close();
      }
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
