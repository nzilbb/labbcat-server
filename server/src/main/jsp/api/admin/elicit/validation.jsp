<%@ page info="Elicitation task elicited attribute validation scripts" isErrorPage="true"
    contentType = "text/javascript;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.admin.elicit.Validation" 
%><%@ include file="../../../base.jsp" %><%{
    Validation handler = new Validation();
    initializeHandler(handler, request, response);
    if ("GET".equals(request.getMethod())) {
      String js = handler.get(
        request.getPathInfo(),
        (status)->response.setStatus(status));
      response.getWriter().write(js);
    } else if ("PUT".equals(request.getMethod())) {
      handler.put(
        request.getPathInfo(),
        request.getInputStream(),
        (status)->response.setStatus(status));
    } else if ("OPTIONS".equals(request.getMethod())) {
      response.addHeader("Allow", "OPTIONS, GET, PUT");
    } else {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}%>
