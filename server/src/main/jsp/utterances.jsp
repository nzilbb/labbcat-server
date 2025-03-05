<%@ page info="Process with Praat" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.Utterances" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="base.jsp" %><%{
    if (!"GET".equals(request.getMethod()) && !"POST".equals(request.getMethod())) { // GET/POST only
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    } else {
      Utterances handler = new Utterances();
      initializeHandler(handler, request);
      JsonObject json = handler.post(
        parseParameters(request),
        (fileName)->{
          ResponseAttachmentName(
            request, response, fileName);
        },
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
    }
}%>
