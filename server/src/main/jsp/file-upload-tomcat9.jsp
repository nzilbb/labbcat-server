<%@ page info="Test upload"
    import = "java.io.File" 
    import = "java.util.Enumeration" 
    import = "java.util.HashMap" 
    import = "java.util.Iterator" 
    import = "java.util.List" 
    import = "java.util.Vector" 
    import = "org.apache.commons.fileupload.*"
    import = "org.apache.commons.fileupload.disk.*"
    import = "org.apache.commons.fileupload.servlet.*"
    import = "nzilbb.labbcat.server.api.RequestParameters" 
%><%
{
  RequestParameters parameters = new RequestParameters();
  try {
    ServletFileUpload fileupload = new ServletFileUpload(new DiskFileItemFactory());
    // this ensures that umlauts, etc. don't get ?ified
    fileupload.setHeaderEncoding("UTF-8");
    List items = fileupload.parseRequest(request);
    Iterator it = items.iterator();
    while (it.hasNext()) {
        FileItem item = (FileItem) it.next();
        if (item.isFormField()) {
          // interpret common form fields
          if (!parameters.containsKey(item.getFieldName())) {
            parameters.put(item.getFieldName(), item.getString());
          } else { // multiple values for same parameter
            // save a vector of strings
            if (parameters.get(item.getFieldName()) instanceof String) { // only one value so far
              Vector<String> values = new Vector<String>();
              values.add((String)parameters.get((String)item.getFieldName()));
              values.add(item.getString());
              parameters.put(item.getFieldName(), values);
            } else { // already have multiple values
              Vector<String> values = (Vector<String>)parameters.get(item.getFieldName());
              values.add(item.getString());
            }
          } // multiple values for same parameter
        } else { // it's a file
          String fileName = item.getName();
          // some browsers provide a full path, which must be truncated
          int lastSlash = fileName.lastIndexOf('/');
          if (lastSlash < 0) lastSlash = fileName.lastIndexOf('\\');
          if (lastSlash >= 0) fileName = fileName.substring(lastSlash + 1);
          // // '+' is misinterpreted as an HTML-encoded ' ' in some places
          // fileName = fileName.replaceAll("\\+","_");
          File f = File.createTempFile("file-upload-tomcat9-", "-"+fileName);
          f.delete();
          f.deleteOnExit();
          f.mkdir();
          // ensure the server file's name is the same as the client file's name
          f = new File(f, fileName);
          f.deleteOnExit();
          item.write(f);            
          if (!parameters.containsKey(item.getFieldName())) {
            parameters.put(item.getFieldName(), f);
          } else { // already got a file with this name - must be multiple files with the same name
            Vector<File> files = null;
            if (parameters.get(item.getFieldName()) instanceof Vector) {
              files = (Vector<File>)parameters.get(item.getFieldName());
            } else {
              files = new Vector<File>();
              parameters.put(item.getFieldName(), files);
            }
            files.add(f);
          } // multiple values for the same parameter
        } // it's a file
    } // next item
  } catch(Throwable exception) {
    // not a multipart request, just load regular parameters
    Enumeration enNames = request.getParameterNames();
    while (enNames.hasMoreElements()) {
      String name = (String)enNames.nextElement();
      String[] values = request.getParameterValues(name);
      if (values.length == 1) {
        parameters.put(name, values[0]);
      } else {
        parameters.put(name, values);
      }
    } // next element 
  }
  request.setAttribute("multipart-parameters", parameters);
}
%>
