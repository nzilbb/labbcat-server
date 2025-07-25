<%@ page info="Praat integration token" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.praat.Token" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter" 
%><%@ include file="base.jsp" %><%{
    if ("GET".equals(request.getMethod())) { // GET only
      Token handler = new Token();
      initializeHandler(handler, request);
      final Cookie[] cookies = request.getCookies();
      JsonObject json = handler.get(
        (headerName)->request.getHeader(headerName),
        (path)->new File(getServletContext().getRealPath(path)),
        (name)->{
          if (cookies != null) {
            for (Cookie cookie : cookies) {
              if (name.equals(cookie.getName())) {
                return cookie.getValue();
              }
            } // next cookie
          }
          return null; // not found
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
