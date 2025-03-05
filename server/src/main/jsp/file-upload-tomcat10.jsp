<%@ page info="Test upload"
    import = "java.io.File" 
    import = "java.util.Enumeration" 
    import = "java.util.HashMap" 
    import = "java.util.Iterator" 
    import = "java.util.List" 
    import = "java.util.Vector" 
    import = "org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletFileUpload"
    import = "org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletRequestContext"
    import = "org.apache.commons.fileupload2.core.DiskFileItemFactory"
    import = "org.apache.commons.fileupload2.core.FileItem"
    import = "org.apache.commons.fileupload2.core.FileUploadException"
    import = "nzilbb.labbcat.server.servlet.RequestParameters" 
%><%
{
  RequestParameters parameters = new RequestParameters();
  try {
    final DiskFileItemFactory fileItemfactory = DiskFileItemFactory.builder().get();
    final JakartaServletFileUpload fileUpload = new JakartaServletFileUpload(fileItemfactory);
    List items = fileUpload.parseRequest(request);
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
          File f = File.createTempFile("anycontainer-", "-"+item.getName());
          f.delete();
          f.deleteOnExit();
          f.mkdir();
          // ensure the server file's name is the same as the client file's name
          f = new File(f, item.getName());
          f.deleteOnExit();
          item.write(f.toPath());            
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
  } catch(FileUploadException exception) {
    // not a multipart request, just load regular parameters
    parameters = parseParameters(request);
  }
  request.setAttribute("multipart-parameters", parameters);
}
%>
