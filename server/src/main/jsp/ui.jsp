<%@ page trimDirectiveWhitespaces="true" 
%><%@ page import = "java.net.URL" 
%><%@ page import = "java.util.regex.*" 
%><%@ page import = "nzilbb.util.IO" 
%><%
// Include the stylesheets and javascripts required for the UI app
ServletContext context = request.getSession().getServletContext();
String userInterfacePath = "en/";
URL userInterface = getClass().getResource("/META-INF/resources/ui/en/index.html");

// load index.html
String html = IO.InputStreamToString(userInterface.openStream());
html = html.replace("<base href=\"/en/\">",
                    "<base href=\""+session.getAttribute("baseUrl")+"/ui/en/\">");
%><%=html%>
