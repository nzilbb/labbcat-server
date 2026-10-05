<%@ page info="Regenerate transcript layers" isErrorPage="true"
    contentType = "application/json;charset=UTF-8"
    import = "nzilbb.labbcat.server.api.edit.transcripts.layers.Regenerate" 
    import = "java.util.Collection" 
    import = "java.util.HashSet" 
    import = "java.util.Vector" 
    import = "javax.json.Json" 
    import = "javax.json.JsonObject" 
    import = "javax.json.JsonWriter"
    import = "nz.ac.canterbury.ling.Labbcat"
    import = "nz.ac.canterbury.ling.GraphsLayerGenerator"
%><%@ include file="../../../base.jsp" %><%{
      if ("POST".equals(request.getMethod())) { // POST uploads files
        // load multipart request parameters - the implementation depends on the servlet container:
        // Server info something like "Apache Tomcat/9.0.58 (Ubuntu)" or "Apache Tomcat/10.1.36"
        boolean tomcat9 = application.getServerInfo().matches(".*Tomcat/9.*");
        if (tomcat9) {
          %><jsp:include page="../../../file-upload-tomcat9.jsp" /><%
        } else {
          %><jsp:include page="../../../file-upload-tomcat10.jsp" /><%
        } 
        try (RequestParameters parameters = (RequestParameters)
             request.getAttribute("multipart-parameters")) {
          Regenerate handler = new Regenerate();
          initializeHandler(handler, request, response);
          JsonObject json = handler.post(
            parameters, (status)->response.setStatus(status),
            (Collection<Integer> ag_ids, Integer layer_id)-> { // layer generator
              // TODO replace this legacy method for generating layers, once all layer managers are annotators
              GraphsLayerGenerator regenerationThread
                = new GraphsLayerGenerator(
                  new Vector<Integer>(ag_ids),
                  layer_id == null?null:new HashSet<Integer>() {{ add(layer_id); }},
                  (Labbcat)getServletContext().getAttribute("labbcat"));
              if (request.getRemoteUser() != null) {
                regenerationThread.setWho(request.getRemoteUser());
              } else {
                regenerationThread.setWho(request.getRemoteHost());
              }
              regenerationThread.start();
              return ""+regenerationThread.getId();
            });
          if (json != null) {
            JsonWriter writer = Json.createWriter(response.getWriter());
            writer.writeObject(json);
            writer.close();
          }
        } // delete any temporary files
      } else if ("OPTIONS".equals(request.getMethod())) {
        response.addHeader("Allow", "OPTIONS, POST");
      } else {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      }
}%>
