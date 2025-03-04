<%@ page info="User information" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.servlet.User" 
    import = "javax.json.Json" 
%><%@ include file="base.jsp" %><%{
    String user = request.getRemoteUser();
    User handler = new User();
    initializeHandler(handler, request);
    handler.handleRequest(Json.createGenerator(out), user);
}%>
